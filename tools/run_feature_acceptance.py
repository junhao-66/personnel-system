"""Run local feature acceptance against an owned, disposable database (no deployment)."""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import shutil
import signal
import socket
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

from db_maintenance import DatabaseTools, MaintenanceError, require

REPO = Path(__file__).resolve().parent.parent
BROWSER_CHECKS = (("pdf-export-smoke.cjs", 5218), ("analysis-enhancement-smoke.cjs", 5219),
                  ("graph-smoke.cjs", 5205), ("cache-ai-smoke.cjs", 5220), ("account-settings-smoke.cjs", 5221),
                  ("assessment-smoke.cjs", 5204), ("scale-editor-smoke.cjs", 5222), ("leave-smoke.cjs", 5203))


def parse_browser_output(output: str) -> dict:
    """Require actual, nonempty assertions; successful exit alone is not acceptance."""
    data = json.loads(output.strip())
    require(isinstance(data, dict), "Browser report must be a JSON object.")
    checks = data.get("checks")
    require(isinstance(checks, list) and bool(checks), "Browser report has no checks.")
    require(all(isinstance(item, str) and item.strip() for item in checks), "Invalid browser checks.")
    require(type(data.get("passed")) is int and data["passed"] == len(checks), "Browser check count mismatch.")
    return {"passed": len(checks), "checks": checks}


def junit_summary(directory: Path, classes: list[str], started: float) -> dict:
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    suites = []
    for class_name in classes:
        path = directory / f"TEST-{class_name}.xml"
        require(path.is_file() and path.stat().st_mtime >= started - 1,
                f"Missing or stale test report: {class_name}")
        root = ET.parse(path).getroot()
        counts = {key: int(root.attrib.get(key, "0")) for key in totals}
        require(counts["tests"] > 0, f"Empty test suite: {class_name}")
        for key in totals:
            totals[key] += counts[key]
        suites.append({"name": class_name, **counts})
    require(bool(classes) and totals["failures"] == totals["errors"] == totals["skipped"] == 0,
            "Java tests contain failures, errors or skipped cases.")
    return {**totals, "suites": suites}


def safe_output_directory(repo: Path, requested: Path | None) -> Path:
    parent = (repo / "output" / "acceptance").resolve()
    candidate = (requested or parent / (dt.datetime.now().strftime("%Y%m%d-%H%M%S-") + uuid.uuid4().hex[:8])).resolve()
    require(candidate != parent and parent in candidate.parents, "Reports must be in a new output/acceptance subdirectory.")
    require(not candidate.exists(), "Report directory already exists; use a new directory.")
    return candidate


def discover_classes(repo: Path) -> list[str]:
    root = repo / "backend" / "src" / "test" / "java"
    result = []
    for path in sorted(root.rglob("*.java")):
        # Concurrency acceptance has its own owning database runner; never invoke it here.
        if path.stem == "AssessmentConcurrencyIT":
            continue
        if path.stem.endswith("Test") or path.stem.endswith("DatabaseIT") or path.stem == "StaticCacheUnavailableIT":
            result.append(".".join(path.relative_to(root).with_suffix("").parts))
    return result


def run_command(command: list[str], cwd: Path, environment: dict, timeout: int) -> subprocess.CompletedProcess:
    # Output is kept in memory only. Raw Java XML/system properties and config are never copied to reports.
    options = {"creationflags": subprocess.CREATE_NEW_PROCESS_GROUP} if os.name == "nt" else {"start_new_session": True}
    process = subprocess.Popen(command, cwd=cwd, env=environment, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, encoding="utf-8", errors="replace", **options)
    try:
        output, _ = process.communicate(timeout=timeout)
        return subprocess.CompletedProcess(command, process.returncode, output)
    except (subprocess.TimeoutExpired, KeyboardInterrupt) as error:
        # Kill only the child tree we launched, including Maven JVM/Vite/browser children.
        # Never use process names or occupied ports to stop a user's existing service.
        if process.poll() is None:
            if os.name == "nt":
                subprocess.run([str(Path(os.environ.get("SystemRoot", "C:/Windows")) / "System32/taskkill.exe"),
                                "/PID", str(process.pid), "/T", "/F"], stdout=subprocess.DEVNULL,
                               stderr=subprocess.DEVNULL, timeout=15, check=False)
            else:
                os.killpg(process.pid, signal.SIGKILL)
        output, _ = process.communicate(timeout=15)
        if isinstance(error, subprocess.TimeoutExpired):
            error.output = output
        raise


def write_reports(output: Path, report: dict) -> None:
    (output / "result.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lines = ["# 本地功能验收结果", "", f"- 状态：{report['status']}", f"- 开始：{report['startedAt']}",
             f"- 结束：{report.get('finishedAt', '运行中')}", "- 公网部署：未执行；运行中的IDE服务：未停止或重启。",
             "- 后端：真实隔离MySQL；缓存测试：真实本地Redis及故障端口；浏览器：夹具API。",
             "- AI：仅Mock/客户端契约；没有调用外部付费API，不等于真实模型联调验收。", "",
             "| 验收项 | 状态 | 检查数量 |", "| --- | --- | --- |"]
    for step in report["steps"]:
        counts = step.get("details", {})
        lines.append(f"| {step['name']} | {step['status']} | {counts.get('tests', counts.get('passed', '—'))} |")
    lines.extend(["", f"隔离测试库清理：{report.get('cleanup', {}).get('status', 'PENDING')}", "",
                  "## 浏览器检查项", ""])
    for step in report["steps"]:
        for check in step.get("details", {}).get("checks", []):
            lines.append(f"- {check}")
    if report.get("error"):
        lines.extend(["", "验收未完成：" + report["error"]])
    (output / "RESULT.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mysql-bin", default=os.environ.get("PERSONNEL_MYSQL_BIN"))
    parser.add_argument("--host", default=os.environ.get("PERSONNEL_DB_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("PERSONNEL_DB_PORT", "3306")))
    parser.add_argument("--user", default=os.environ.get("PERSONNEL_DB_USER", "root"))
    parser.add_argument("--maven", default=shutil.which("mvn") or "D:/maven/apache-maven-3.8.2/bin/mvn.cmd")
    parser.add_argument("--java-home", default="D:/jdk/jdk17" if Path("D:/jdk/jdk17").is_dir() else os.environ.get("JAVA_HOME", ""))
    parser.add_argument("--node", default=shutil.which("node") or "D:/node/node.exe")
    parser.add_argument("--playwright-module", default=os.environ.get("PLAYWRIGHT_MODULE", ""))
    parser.add_argument("--report-dir", type=Path)
    args = parser.parse_args()
    report = {"status": "RUNNING", "startedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
              "steps": [{"name": name, "status": "NOT_RUN"} for name in
                        ["后端单元与数据库集成", "前端类型检查与构建", *[script for script, _ in BROWSER_CHECKS]]],
              "deploymentPerformed": False, "paidApiCalled": False, "businessDatabaseTouched": False}
    output = None
    database = "personnel_acceptance_" + uuid.uuid4().hex[:16]
    created = False
    db = None
    try:
        require(bool(re.fullmatch(r"[A-Za-z0-9.-]+", args.host)), "Invalid MySQL host.")
        require(bool(re.fullmatch(r"[A-Za-z0-9_.@-]+", args.user)), "Invalid MySQL user.")
        require(1 <= args.port <= 65535, "Invalid MySQL port.")
        require("PERSONNEL_DB_PASSWORD" in os.environ, "Set PERSONNEL_DB_PASSWORD for this process; no password prompt or config export is used.")
        for value, name in ((args.maven, "Maven"), (args.node, "Node")):
            require(Path(value).is_file(), f"{name} executable not found; provide its CLI option.")
        java = Path(args.java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")
        require(java.is_file(), "JDK not found; set --java-home.")
        version = subprocess.run([str(java), "-version"], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=10)
        match = re.search(rb'version "(\d+)', version.stdout)
        require(version.returncode == 0 and match is not None and int(match[1]) >= 17, "Java 17 or later is required.")
        require((REPO / "frontend/node_modules/vite/bin/vite.js").is_file(), "Frontend dependencies are not installed.")
        require(bool(args.playwright_module) and Path(args.playwright_module).exists(), "Set PLAYWRIGHT_MODULE or --playwright-module to the installed Playwright module.")
        for _, port in BROWSER_CHECKS:
            with socket.socket() as probe:
                require(probe.connect_ex(("127.0.0.1", port)) != 0, f"Browser test port {port} is occupied; existing process was not stopped.")
        output = safe_output_directory(REPO, args.report_dir)
        output.mkdir(parents=True)
        write_reports(output, report)
        db = DatabaseTools(args)
        db.query(f"CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
        created = True
        environment = {key.upper() if os.name == "nt" else key: value for key, value in os.environ.items()}
        environment.update({"JAVA_HOME": str(Path(args.java_home).resolve()),
                            # MySQL 8/Connector-J negotiates utf8mb4. Keep the command-line URL free
                            # of '&': Windows mvn.cmd would interpret it as a command separator.
                            "SPRING_DATASOURCE_URL": f"jdbc:mysql://{args.host}:{args.port}/{database}?serverTimezone=Asia/Shanghai",
                            "SPRING_DATASOURCE_USERNAME": args.user, "SPRING_DATASOURCE_PASSWORD": db.password,
                            "PERSONNEL_AI_MODE": "MOCK", "PERSONNEL_AI_EXTERNAL_ENABLED": "false", "PERSONNEL_AI_API_KEY": "",
                            "PERSONNEL_STATIC_CACHE_NAMESPACE": "personnel:acceptance:" + uuid.uuid4().hex + ":",
                            "PERSONNEL_JWT_SECRET": uuid.uuid4().hex + uuid.uuid4().hex,
                            "PLAYWRIGHT_MODULE": args.playwright_module})
        # Explicit JVM URL overrides environment/profile collisions; no password in argv.
        classes = discover_classes(REPO)
        started = time.time()
        total_steps = 2 + len(BROWSER_CHECKS)
        print(f"[1/{total_steps}] Running Java unit/integration tests in a new disposable database...", flush=True)
        report["steps"][0]["status"] = "RUNNING"
        write_reports(output, report)
        java_result = run_command([str(Path(args.maven).resolve()), "-o", "-Dtest=" + ",".join(classes),
                                  "-Dspring.datasource.url=" + environment["SPRING_DATASOURCE_URL"],
                                  "-Dspring.datasource.username=" + args.user, "test", "-q"], REPO / "backend", environment, 300)
        require(java_result.returncode == 0, "Java test command failed; inspect current local backend/target/surefire-reports (not copied into shareable reports).")
        summary = junit_summary(REPO / "backend/target/surefire-reports", classes, started)
        report["steps"][0].update({"status": "PASSED", "details": summary})
        write_reports(output, report)
        print(f"[2/{total_steps}] Checking TypeScript and building frontend...", flush=True)
        report["steps"][1]["status"] = "RUNNING"
        write_reports(output, report)
        npm = Path(args.node).with_name("npm.cmd" if os.name == "nt" else "npm")
        require(npm.is_file(), "npm not found next to Node; install project tooling first.")
        build = run_command([str(npm), "run", "build"], REPO / "frontend", environment, 120)
        require(build.returncode == 0, "Frontend type check/build failed; run npm run build locally for diagnostics.")
        report["steps"][1]["status"] = "PASSED"
        write_reports(output, report)
        for index, (script, _) in enumerate(BROWSER_CHECKS, 3):
            print(f"[{index}/{total_steps}] Checking {script}...", flush=True)
            report["steps"][index - 1]["status"] = "RUNNING"
            write_reports(output, report)
            result = run_command([str(Path(args.node).resolve()), script], REPO / "frontend", environment, 90)
            require(result.returncode == 0, f"Browser acceptance failed: {script}; rerun this script for diagnostics.")
            report["steps"][index - 1].update({"status": "PASSED", "details": parse_browser_output(result.stdout)})
            write_reports(output, report)
        report["status"] = "PASSED"
    except (MaintenanceError, OSError, ValueError, ET.ParseError, subprocess.TimeoutExpired) as error:
        report["status"] = "FAILED"
        # Error strings originate only from bounded internal validation, not command output or credentials.
        report["error"] = "A command timed out." if isinstance(error, subprocess.TimeoutExpired) else str(error)
        for step in report["steps"]:
            if step["status"] == "RUNNING":
                step["status"] = "FAILED"
    except KeyboardInterrupt:
        report["status"] = "INTERRUPTED"
        report["error"] = "Interrupted; incomplete checks are not accepted."
        for step in report["steps"]:
            if step["status"] == "RUNNING":
                step["status"] = "INTERRUPTED"
    finally:
        if created and db is not None:
            try:
                # Exact newly generated identifier only; no caller-supplied DB and no business-data cleanup.
                db.query(f"DROP DATABASE `{database}`")
                report["cleanup"] = {"status": "PASSED", "databaseDropped": True}
            except (MaintenanceError, OSError):
                report["status"] = "FAILED"
                report["cleanup"] = {"status": "FAILED", "databaseDropped": False, "ownedDatabase": database}
        else:
            report["cleanup"] = {"status": "NOT_CREATED", "databaseDropped": False}
        report["finishedAt"] = dt.datetime.now(dt.timezone.utc).isoformat()
        if output is not None:
            write_reports(output, report)
            print(f"{report['status']}: {output / 'RESULT.md'}", flush=True)
        elif report.get("error"):
            print(report["error"], file=sys.stderr)
    return 0 if report["status"] == "PASSED" else 1


if __name__ == "__main__":
    raise SystemExit(main())
