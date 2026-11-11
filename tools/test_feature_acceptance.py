"""Fast, offline tests for the acceptance runner's false-positive protections."""
import json
import contextlib
import io
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import Mock, patch

from db_maintenance import MaintenanceError
import run_feature_acceptance as runner


class FeatureAcceptanceTest(unittest.TestCase):
    def test_browser_count_must_match_nonempty_checks(self):
        self.assertEqual(runner.parse_browser_output('{"passed":1,"checks":["检查"]}')["passed"], 1)
        for value in ('[]', 'null', '{"passed":0,"checks":[]}', '{"passed":2,"checks":["a"]}', '{"passed":true,"checks":["a"]}', '{"passed":1,"checks":[""]}'):
            with self.subTest(value=value), self.assertRaises(MaintenanceError):
                runner.parse_browser_output(value)

    def test_stale_and_missing_junit_cannot_be_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(MaintenanceError):
                runner.junit_summary(root, ["ExampleTest"], time.time())
            (root / "TEST-ExampleTest.xml").write_text('<testsuite tests="2" failures="0" errors="0" skipped="0"/>')
            self.assertEqual(runner.junit_summary(root, ["ExampleTest"], time.time())["tests"], 2)
            with self.assertRaises(MaintenanceError):
                runner.junit_summary(root, ["ExampleTest"], time.time() + 10)

    def test_failures_skips_and_empty_suites_are_not_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for attributes in ('tests="1" failures="1"', 'tests="1" errors="1"', 'tests="1" skipped="1"', 'tests="0"'):
                (root / "TEST-ExampleTest.xml").write_text(f'<testsuite {attributes}/>')
                with self.subTest(attributes=attributes), self.assertRaises(MaintenanceError):
                    runner.junit_summary(root, ["ExampleTest"], time.time())

    def test_output_must_be_new_and_below_acceptance_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for target in (root, root / "output", root / "output/acceptance", root / "outside"):
                with self.subTest(target=target), self.assertRaises(MaintenanceError):
                    runner.safe_output_directory(root, target)
            valid = root / "output/acceptance/new-run"
            self.assertEqual(runner.safe_output_directory(root, valid), valid.resolve())
            valid.mkdir(parents=True)
            with self.assertRaises(MaintenanceError):
                runner.safe_output_directory(root, valid)

    def test_concurrency_suite_is_excluded(self):
        classes = runner.discover_classes(runner.REPO)
        self.assertIn("com.personnel.AiAssistantDatabaseIT", classes)
        self.assertIn("com.personnel.StaticCacheUnavailableIT", classes)
        self.assertNotIn("com.personnel.AssessmentConcurrencyIT", classes)

    def test_command_timeout_only_terminates_its_owned_child_tree(self):
        process = Mock(pid=12345)
        process.poll.return_value = None
        process.communicate.side_effect = [subprocess.TimeoutExpired(["owned-command"], 1), ("", None)]
        with patch.object(runner.subprocess, "Popen", return_value=process), \
             patch.object(runner.subprocess, "run") as terminate, \
             patch.object(runner.os, "killpg", create=True) as kill_group:
            with self.assertRaises(subprocess.TimeoutExpired):
                runner.run_command(["owned-command"], Path.cwd(), {}, 1)
        self.assertEqual(process.communicate.call_count, 2)
        if os.name == "nt":
            self.assertEqual(terminate.call_args.args[0][1:], ["/PID", "12345", "/T", "/F"])
            kill_group.assert_not_called()
        else:
            terminate.assert_not_called()
            kill_group.assert_called_once_with(12345, runner.signal.SIGKILL)

    def test_reports_distinguish_mock_browser_and_actual_services(self):
        with tempfile.TemporaryDirectory() as directory:
            report = {"status": "FAILED", "startedAt": "test", "steps": [], "cleanup": {"status": "FAILED"}}
            runner.write_reports(Path(directory), report)
            self.assertEqual(json.loads((Path(directory) / "result.json").read_text(encoding="utf-8"))["status"], "FAILED")
            text = (Path(directory) / "RESULT.md").read_text(encoding="utf-8")
            self.assertIn("夹具API", text)
            self.assertIn("不等于真实模型联调", text)
            self.assertIn("隔离测试库清理：FAILED", text)

    def test_owned_database_is_cleaned_and_failures_never_become_passes(self):
        for scenario in ("backend_failure", "cleanup_failure", "success"):
            with self.subTest(scenario=scenario), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                java = root / "jdk/bin" / ("java.exe" if os.name == "nt" else "java")
                node = root / "node" / ("node.exe" if os.name == "nt" else "node")
                maven = root / "maven.cmd"
                npm = node.with_name("npm.cmd" if os.name == "nt" else "npm")
                vite = root / "frontend/node_modules/vite/bin/vite.js"
                for path in (java, node, maven, npm, vite):
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.touch()
                output = root / "output/acceptance/test-run"
                database = Mock(password="test-secret-not-for-report")
                if scenario == "cleanup_failure":
                    database.query.side_effect = [[], MaintenanceError("simulated cleanup failure")]
                command_result = subprocess.CompletedProcess([], 1 if scenario == "backend_failure" else 0,
                                                               "RAW-OUTPUT-MUST-NOT-LEAK")
                arguments = ["acceptance", "--maven", str(maven), "--node", str(node),
                             "--java-home", str(java.parent.parent), "--playwright-module", str(root),
                             "--report-dir", str(output)]
                with patch.object(runner, "REPO", root), patch.object(runner, "BROWSER_CHECKS", ()), \
                     patch.object(sys, "argv", arguments), patch.dict(os.environ, {"PERSONNEL_DB_PASSWORD": "test-only"}), \
                     patch.object(runner, "DatabaseTools", return_value=database), \
                     patch.object(runner.subprocess, "run", return_value=subprocess.CompletedProcess([], 0, b'openjdk version "17.0.1"')), \
                     patch.object(runner, "run_command", return_value=command_result) as commands, \
                     patch.object(runner, "discover_classes", return_value=["ExampleTest"]), \
                     patch.object(runner, "junit_summary", return_value={"tests": 1}), contextlib.redirect_stdout(io.StringIO()):
                    result = runner.main()
                self.assertEqual(result, 0 if scenario == "success" else 1)
                queries = [call.args[0] for call in database.query.call_args_list]
                self.assertEqual(len(queries), 2)
                owned_name = queries[0].split("`")[1]
                self.assertRegex(owned_name, r"^personnel_acceptance_[a-f0-9]{16}$")
                self.assertEqual(queries[1], f"DROP DATABASE `{owned_name}`")
                report_text = (output / "result.json").read_text(encoding="utf-8")
                report = json.loads(report_text)
                self.assertEqual(report["status"], "PASSED" if scenario == "success" else "FAILED")
                if scenario == "backend_failure":
                    self.assertEqual([step["status"] for step in report["steps"]], ["FAILED", "NOT_RUN"])
                self.assertEqual(report["cleanup"]["databaseDropped"], scenario != "cleanup_failure")
                self.assertEqual(commands.call_count, 1 if scenario == "backend_failure" else 2)
                java_arguments = commands.call_args_list[0].args[0]
                jdbc_argument = next(value for value in java_arguments if value.startswith("-Dspring.datasource.url="))
                self.assertNotIn("&", jdbc_argument)
                self.assertIn(owned_name, jdbc_argument)
                self.assertNotIn("RAW-OUTPUT-MUST-NOT-LEAK", report_text)
                self.assertNotIn("test-secret-not-for-report", report_text)


if __name__ == "__main__":
    unittest.main()
