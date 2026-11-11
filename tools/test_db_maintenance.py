"""Run: python -m unittest discover -s tools -p test_db_maintenance.py -v."""
import argparse
import json
import os
from pathlib import Path
import tempfile
import unittest
import uuid

from db_maintenance import DatabaseTools, MaintenanceError, backup, dump_inventory, restore, sha256, verify


class BackupIntegrityTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        self.sql = self.directory / "database.sql"
        self.sql.write_bytes(b"CREATE TABLE `sample` (\n  `id` bigint NOT NULL,\n  PRIMARY KEY (`id`)\n) ENGINE=InnoDB;\nINSERT INTO `sample` (`id`) VALUES (1);\n")
        self.manifest = {"formatVersion": 1, "sourceDatabase": "personnel_system", "sqlFile": "database.sql",
                         "sqlBytes": self.sql.stat().st_size, "sqlSha256": sha256(self.sql), "tables": dump_inventory(self.sql)}
        self.write_manifest()

    def write_manifest(self):
        (self.directory / "manifest.json").write_text(json.dumps(self.manifest), encoding="utf-8")

    def test_valid_inventory_covers_rows_and_schema(self):
        self.assertEqual(verify(self.directory), self.manifest)
        original = dump_inventory(self.sql)
        self.assertEqual(original["sample"]["rows"], 1)
        self.sql.write_bytes(self.sql.read_bytes().replace(b"bigint", b"int"))
        self.assertNotEqual(dump_inventory(self.sql)["sample"]["schemaSqlSha256"], original["sample"]["schemaSqlSha256"])

    def test_tampered_sql_rejected(self):
        self.sql.write_bytes(self.sql.read_bytes().replace(b"VALUES (1)", b"VALUES (2)"))
        with self.assertRaisesRegex(MaintenanceError, "hash mismatch"):
            verify(self.directory)

    def test_manifest_cannot_select_other_sql_file(self):
        self.manifest["sqlFile"] = "../outside.sql"
        self.write_manifest()
        with self.assertRaisesRegex(MaintenanceError, "filename"):
            verify(self.directory)

    def test_manifest_inventory_must_match_dump(self):
        self.manifest["tables"]["sample"]["rows"] = 0
        self.write_manifest()
        with self.assertRaisesRegex(MaintenanceError, "inventory mismatch"):
            verify(self.directory)

    def test_live_target_and_missing_confirmation_rejected_before_connection(self):
        class NoConnection:
            def query(self, sql):
                raise AssertionError("No database query allowed")
        for target, confirmation in [("personnel_system", "personnel_system"), ("personnel_restore_test", "wrong"), ("personnel_restore_x;DROP", "")]:
            with self.subTest(target=target), self.assertRaises(MaintenanceError):
                restore(NoConnection(), self.directory, target, confirmation)

    def test_existing_recovery_database_is_never_modified(self):
        class Existing:
            def query(self, sql):
                if not sql.startswith("SELECT SCHEMA_NAME"):
                    raise AssertionError("Existing target must not be modified")
                return [["personnel_restore_existing"]]
        with self.assertRaisesRegex(MaintenanceError, "already exists"):
            restore(Existing(), self.directory, "personnel_restore_existing", "personnel_restore_existing")

    def test_row_hash_detects_same_count_content_change(self):
        original = dump_inventory(self.sql)
        self.sql.write_bytes(self.sql.read_bytes().replace(b"VALUES (1)", b"VALUES (2)"))
        changed = dump_inventory(self.sql)
        self.assertEqual(changed["sample"]["rows"], original["sample"]["rows"])
        self.assertNotEqual(changed["sample"]["rowSqlSha256"], original["sample"]["rowSqlSha256"])

    def test_unsupported_manifest_format_rejected(self):
        self.manifest["formatVersion"] = 999
        self.write_manifest()
        with self.assertRaisesRegex(MaintenanceError, "Unsupported"):
            verify(self.directory)


@unittest.skipUnless(os.environ.get("PERSONNEL_MAINTENANCE_IT") == "1", "Set PERSONNEL_MAINTENANCE_IT=1 for MySQL recovery drill")
class MysqlRecoveryTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)
        args = argparse.Namespace(mysql_bin=os.environ.get("PERSONNEL_MYSQL_BIN"),
                                  host=os.environ.get("PERSONNEL_DB_HOST", "127.0.0.1"),
                                  port=int(os.environ.get("PERSONNEL_DB_PORT", "3306")),
                                  user=os.environ.get("PERSONNEL_DB_USER", "root"))
        self.tools = DatabaseTools(args)
        suffix = uuid.uuid4().hex[:16]
        self.source = "personnel_restore_fixture_" + suffix
        self.target = "personnel_restore_result_" + suffix
        self.created = []
        self.addCleanup(self.cleanup_databases)
        self.tools.query(f"CREATE DATABASE `{self.source}` CHARACTER SET utf8mb4")
        self.created.append(self.source)
        self.tools.query(f"CREATE TABLE `{self.source}`.parent (id BIGINT PRIMARY KEY AUTO_INCREMENT, label VARCHAR(100), report JSON, raw BLOB, created_at DATETIME(6)) ENGINE=InnoDB")
        self.tools.query(f"CREATE TABLE `{self.source}`.child (id BIGINT PRIMARY KEY, parent_id BIGINT, CONSTRAINT fk_parent FOREIGN KEY (parent_id) REFERENCES parent(id)) ENGINE=InnoDB")
        self.tools.query(f"CREATE TABLE `{self.source}`.empty_table (id BIGINT PRIMARY KEY AUTO_INCREMENT) ENGINE=InnoDB AUTO_INCREMENT=42")
        self.tools.query(f"INSERT INTO `{self.source}`.parent(label,report,raw,created_at) VALUES ('签名\\n中文与引号\\\'测试',JSON_OBJECT('version','GRAPH_GROWTH_PLAN_V3','snapshot',JSON_OBJECT('original',true)),X'000102FF','2026-10-29 12:34:56.123456'),(NULL,NULL,NULL,NULL)")
        self.tools.query(f"INSERT INTO `{self.source}`.child VALUES (1,1)")

    def cleanup_databases(self):
        # Only UUID-named fixture schemas created by this test are eligible for cleanup.
        for name in reversed(self.created):
            if name in (self.source, self.target) and name.startswith("personnel_restore_"):
                self.tools.query(f"DROP DATABASE `{name}`")

    def test_round_trip_schema_json_binary_unicode_null_and_auto_increment(self):
        directory = self.directory / "backup"
        manifest = backup(self.tools, self.source, directory)
        self.assertEqual(len(manifest["tables"]), 3)
        self.assertEqual(manifest["tables"]["parent"]["rows"], 2)
        self.assertEqual(manifest["tables"]["empty_table"]["rows"], 0)
        # The name is owned by this test; cleanup also handles an interrupted import.
        self.created.append(self.target)
        report = restore(self.tools, directory, self.target, self.target)
        self.assertEqual(report["result"], "VERIFIED")
        self.assertEqual(report["tables"], manifest["tables"])
        self.assertEqual(self.tools.query(f"SELECT AUTO_INCREMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA='{self.target}' AND TABLE_NAME='empty_table'"), [["42"]])
        with self.assertRaisesRegex(MaintenanceError, "already exists"):
            restore(self.tools, directory, self.target, self.target)
        with self.assertRaisesRegex(MaintenanceError, "source database"):
            restore(self.tools, directory, self.source, self.source)
        with self.assertRaisesRegex(MaintenanceError, "already exists"):
            backup(self.tools, self.source, directory)

    def test_backup_rejects_nontransactional_table(self):
        self.tools.query(f"CREATE TABLE `{self.source}`.legacy (id BIGINT PRIMARY KEY) ENGINE=MyISAM")
        with self.assertRaisesRegex(MaintenanceError, "InnoDB"):
            backup(self.tools, self.source, self.directory / "unsupported")
        self.assertFalse((self.directory / "unsupported").exists())

    def test_current_application_database_round_trip_in_isolation(self):
        database = os.environ.get("PERSONNEL_DB_NAME", "personnel_system")
        directory = self.directory / "application-backup"
        manifest = backup(self.tools, database, directory)
        self.assertTrue({"sys_user", "assessment_session", "knowledge_graph_version", "growth_template", "operation_audit"}.issubset(manifest["tables"]))
        self.created.append(self.target)
        report = restore(self.tools, directory, self.target, self.target)
        self.assertEqual(report["tables"], manifest["tables"])
        print(f"Application recovery drill verified {len(report['tables'])} tables and {sum(t['rows'] for t in report['tables'].values())} rows (no live writes).")


if __name__ == "__main__":
    unittest.main()
