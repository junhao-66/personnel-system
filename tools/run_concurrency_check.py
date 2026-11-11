"""Run the 50-user HTTP acceptance against an owned disposable MySQL database."""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import uuid

from db_maintenance import DatabaseTools, MaintenanceError, require


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mysql-bin", default=os.environ.get("PERSONNEL_MYSQL_BIN"))
    parser.add_argument("--host", default=os.environ.get("PERSONNEL_DB_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("PERSONNEL_DB_PORT", "3306")))
    parser.add_argument("--user", default=os.environ.get("PERSONNEL_DB_USER", "root"))
    parser.add_argument("--maven", default=shutil.which("mvn") or "D:/maven/apache-maven-3.8.2/bin/mvn.cmd")
    parser.add_argument("--java-home", default="D:/jdk/jdk17" if Path("D:/jdk/jdk17").is_dir() else os.environ.get("JAVA_HOME", ""))
    parser.add_argument("--report-dir", type=Path, required=True)
    parser.add_argument("--p95-ms", type=int, default=5000)
    args = parser.parse_args()
    created = False
    tools = None
    name = "personnel_loadtest_" + uuid.uuid4().hex[:16]
    output = args.report_dir.resolve()
    repo = Path(__file__).resolve().parent.parent
    try:
        require(bool(re.fullmatch(r"[A-Za-z0-9.-]+", args.host)), "Host must be a hostname or IPv4 address.")
        require(1 <= args.port <= 65535, "Invalid database port.")
        require(100 <= args.p95_ms <= 60000, "P95 target must be between 100 and 60000ms.")
        require(Path(args.maven).is_file(), "Maven not found; set --maven to its executable path.")
        require((Path(args.java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")).is_file(), "JDK not found; set --java-home.")
        version = subprocess.run([str(Path(args.java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")), "-version"],
                                 stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)
        match = re.search(rb'version "(\d+)', version.stdout)
        require(version.returncode == 0 and match is not None and int(match[1]) >= 17, "Java 17 or later is required; set --java-home to the project JDK.")
        require(not output.exists(), "Report directory already exists; use a new directory.")
        output.mkdir(parents=True)
        tools = DatabaseTools(args)
        # Never accept a caller-supplied database name, clone live data or use IF NOT EXISTS.
        tools.query(f"CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
        created = True
        # Windows can inherit differently cased duplicate keys; normalize before selecting the JDK.
        environment = {key.upper() if os.name == "nt" else key: value for key, value in os.environ.items()}
        environment.update({
            "JAVA_HOME": str(Path(args.java_home).resolve()),
            "PERSONNEL_LOADTEST_DB": name,
            "PERSONNEL_LOADTEST_JDBC_URL": f"jdbc:mysql://{args.host}:{args.port}/{name}?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai",
            "PERSONNEL_LOADTEST_DB_USER": args.user,
            "PERSONNEL_LOADTEST_DB_PASSWORD": tools.password,
            "PERSONNEL_LOADTEST_REPORT": str(output / "metrics.json"),
            "PERSONNEL_LOADTEST_P95_MS": str(args.p95_ms),
            # A test-only ephemeral secret; do not inherit the running application's signing key.
            "PERSONNEL_JWT_SECRET": uuid.uuid4().hex + uuid.uuid4().hex,
        })
        with (output / "maven.log").open("wb") as log:
            result = subprocess.run([str(Path(args.maven).resolve()), "-o", "-Dtest=AssessmentConcurrencyIT", "test", "-q"],
                                    cwd=repo / "backend", env=environment, stdout=log, stderr=subprocess.STDOUT,
                                    timeout=300, check=False)
        require(result.returncode == 0, f"Concurrency test failed; inspect {output / 'maven.log'} and metrics.json if present.")
        metrics = json.loads((output / "metrics.json").read_text(encoding="utf-8"))
        require(metrics.get("result") == "PASSED" and metrics.get("users") == 50 and metrics.get("requests") == 651,
                "Acceptance report missing or incomplete; test not accepted.")
        print(f"PASSED: 50 users, {metrics['requests']} HTTP requests, elapsed {metrics['elapsedSeconds']:.2f}s; metrics {output / 'metrics.json'}")
        return 0
    except (MaintenanceError, OSError, ValueError, subprocess.TimeoutExpired) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    finally:
        if created and tools is not None:
            # Exact freshly generated target only; no recursive filesystem or business-data cleanup.
            try:
                tools.query(f"DROP DATABASE `{name}`")
                (output / "cleanup.json").write_text(json.dumps({"database": name, "dropped": True,
                    "at": dt.datetime.now(dt.timezone.utc).isoformat(), "businessDatabaseTouched": False}, indent=2) + "\n", encoding="utf-8")
                print("Cleaned up the database created by this run; business database unchanged.")
            except (MaintenanceError, OSError):
                print(f"Cleanup failed; owned test database retained: {name}", file=sys.stderr)
                return 1


if __name__ == "__main__":
    raise SystemExit(main())
