"""MySQL logical backups and isolated recovery drills; standard library only."""
from __future__ import annotations

import argparse
import datetime as dt
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

FORMAT_VERSION = 1
IDENTIFIER = re.compile(r"[A-Za-z0-9_]{1,64}\Z")
CREATE = re.compile(rb"CREATE TABLE `([A-Za-z0-9_]+)` \(")
INSERT = re.compile(rb"INSERT INTO `([A-Za-z0-9_]+)` \(")


class MaintenanceError(Exception):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise MaintenanceError(message)


def identifier(value: str) -> str:
    require(bool(IDENTIFIER.fullmatch(value)), "Database/table name must contain only letters, digits and underscores (1-64).")
    return value


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def dump_inventory(path: Path) -> dict:
    """Hash the ordered SQL representation of every row, including JSON and binary fields."""
    counts: dict[str, int] = {}
    hashes = {}
    schema_hashes = {}
    schema_table = None
    with path.open("rb") as stream:
        for line in stream:
            match = CREATE.match(line)
            if match:
                name = match[1].decode("ascii")
                require(name not in counts, "Duplicate table definition in dump.")
                counts[name] = 0
                hashes[name] = hashlib.sha256()
                schema_hashes[name] = hashlib.sha256()
                schema_table = name
            if schema_table is not None:
                schema_hashes[schema_table].update(line.rstrip(b"\r\n") + b"\n")
                if line.startswith(b") ") and line.rstrip().endswith(b";"):
                    schema_table = None
            match = INSERT.match(line)
            if match:
                name = match[1].decode("ascii")
                require(name in counts, "Row appears before its table definition.")
                counts[name] += 1
                hashes[name].update(line.rstrip(b"\r\n") + b"\n")
    require(bool(counts), "Dump contains no supported tables.")
    require(schema_table is None, "Incomplete table definition in dump.")
    return {name: {"rows": counts[name], "rowSqlSha256": hashes[name].hexdigest(),
                   "schemaSqlSha256": schema_hashes[name].hexdigest()} for name in sorted(counts)}


class DatabaseTools:
    def __init__(self, args: argparse.Namespace):
        self.args = args
        self.password = os.environ.get("PERSONNEL_DB_PASSWORD")
        if self.password is None:
            self.password = getpass.getpass("MySQL password (not stored in backup): ")
        self.bin_dir = Path(args.mysql_bin) if args.mysql_bin else None

    def executable(self, name: str) -> str:
        if self.bin_dir:
            candidate = self.bin_dir / (name + (".exe" if os.name == "nt" else ""))
            require(candidate.is_file(), f"MySQL executable missing: {name}")
            return str(candidate.resolve())
        candidate = shutil.which(name)
        require(candidate is not None, f"Cannot find {name}; provide --mysql-bin.")
        return candidate

    def command(self, name: str) -> list[str]:
        # No shell; password is scoped to the child, never exposed in argv or manifests.
        command = [self.executable(name), "--no-defaults", "--protocol=TCP",
                f"--host={self.args.host}", f"--port={self.args.port}",
                f"--user={self.args.user}", "--default-character-set=utf8mb4"]
        if name == "mysql":
            command.append("--connect-timeout=15")
        return command

    def run(self, name: str, arguments: list[str], *, stdin=None, stdout=None) -> bytes:
        environment = os.environ.copy()
        environment["MYSQL_PWD"] = self.password
        try:
            result = subprocess.run(self.command(name) + arguments, env=environment, stdin=stdin,
                                    stdout=stdout if stdout is not None else subprocess.PIPE,
                                    stderr=subprocess.PIPE, check=False)
        except OSError:
            raise MaintenanceError(f"Unable to launch {name}.") from None
        # Do not print raw tool diagnostics: SQL errors can include personal data or credentials.
        require(result.returncode == 0, f"{name} failed (exit {result.returncode}); check connection, permissions and MySQL compatibility.")
        return result.stdout or b""

    def query(self, sql: str) -> list[list[str]]:
        raw = self.run("mysql", ["--batch", "--skip-column-names", f"--execute={sql}"])
        return [line.split("\t") for line in raw.decode("utf-8").splitlines() if line]

    def table_metadata(self, database: str) -> list[list[str]]:
        identifier(database)
        return self.query("SELECT TABLE_NAME, TABLE_TYPE, COALESCE(ENGINE, '') FROM information_schema.TABLES "
                          f"WHERE TABLE_SCHEMA='{database}' ORDER BY TABLE_NAME")

    def check_schema(self, database: str) -> list[str]:
        tables = self.table_metadata(database)
        require(bool(tables), "Source database is missing or has no tables.")
        require(all(row[1] == "BASE TABLE" and row[2] == "InnoDB" for row in tables),
                "Consistent backup supports InnoDB base tables only; views/nontransactional tables require a separate plan.")
        for row in tables:
            identifier(row[0])
        # This application uses tables only. Do not silently omit executable objects.
        for catalog in ("TRIGGERS", "ROUTINES", "EVENTS"):
            column = "TRIGGER_SCHEMA" if catalog == "TRIGGERS" else ("ROUTINE_SCHEMA" if catalog == "ROUTINES" else "EVENT_SCHEMA")
            rows = self.query(f"SELECT COUNT(*) FROM information_schema.{catalog} WHERE {column}='{database}'")
            require(rows == [["0"]], f"Source has {catalog.lower()}; this table-only backup cannot preserve them.")
        primary = self.query("SELECT DISTINCT TABLE_NAME FROM information_schema.STATISTICS "
                             f"WHERE TABLE_SCHEMA='{database}' AND INDEX_NAME='PRIMARY'")
        require({row[0] for row in primary} == {row[0] for row in tables},
                "All tables need a primary key for deterministic recovery verification.")
        return [row[0] for row in tables]

    def dump(self, database: str, path: Path) -> None:
        identifier(database)
        with path.open("xb") as output:
            self.run("mysqldump", ["--single-transaction", "--skip-lock-tables", "--quick", "--hex-blob",
                     "--order-by-primary", "--complete-insert", "--skip-extended-insert", "--skip-comments",
                     "--skip-add-drop-table", "--skip-add-locks", "--skip-triggers", "--skip-routines",
                     "--skip-events", "--set-gtid-purged=OFF", "--no-tablespaces", database], stdout=output)


def backup(tools: DatabaseTools, database: str, destination: Path) -> dict:
    identifier(database)
    expected = tools.check_schema(database)
    destination = destination.resolve()
    require(not destination.exists(), "Backup destination already exists; use a new directory.")
    destination.parent.mkdir(parents=True, exist_ok=True)
    # Rename only a complete backup. Failed partial files never look like a completed backup.
    with tempfile.TemporaryDirectory(prefix=".personnel-backup-", dir=destination.parent) as temporary:
        directory = Path(temporary)
        sql = directory / "database.sql"
        tools.dump(database, sql)
        tables = dump_inventory(sql)
        require(sorted(tables) == expected, "Schema changed during backup; retry without concurrent DDL.")
        manifest = {"formatVersion": FORMAT_VERSION, "createdAt": dt.datetime.now(dt.timezone.utc).isoformat(),
                    "sourceDatabase": database, "sqlFile": "database.sql", "sqlBytes": sql.stat().st_size,
                    "sqlSha256": sha256(sql), "tables": tables,
                    "scope": "InnoDB tables, schema, rows, primary keys, constraints and AUTO_INCREMENT; no server users or secrets"}
        (directory / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        # mkdir then move each file avoids replacing an existing destination on any platform.
        destination.mkdir()
        for item in directory.iterdir():
            item.rename(destination / item.name)
    return manifest


def verify(directory: Path) -> dict:
    require(directory.is_dir(), "Backup directory does not exist.")
    try:
        manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
        require(isinstance(manifest, dict) and manifest.get("formatVersion") == FORMAT_VERSION, "Unsupported backup format.")
        identifier(manifest["sourceDatabase"])
        require(manifest["sqlFile"] == "database.sql", "Unexpected SQL filename.")
        sql = directory / "database.sql"
        require(sql.is_file() and not sql.is_symlink(), "Backup SQL file missing or invalid.")
        require(sql.stat().st_size == manifest["sqlBytes"] and sha256(sql) == manifest["sqlSha256"], "Backup size/hash mismatch; recovery cancelled.")
        require(dump_inventory(sql) == manifest["tables"], "Backup table inventory mismatch; recovery cancelled.")
        return manifest
    except (OSError, ValueError, KeyError, TypeError):
        raise MaintenanceError("Invalid or unreadable backup manifest/SQL.") from None


def restore(tools: DatabaseTools, directory: Path, target: str, confirmation: str) -> dict:
    manifest = verify(directory)
    identifier(target)
    require(target.startswith("personnel_restore_") and len(target) > len("personnel_restore_"),
            "Recovery target must start with personnel_restore_ and include a suffix.")
    require(target.lower() != manifest["sourceDatabase"].lower(), "Cannot restore over the source database.")
    require(confirmation == target, "--confirm-target must exactly match the recovery target.")
    exists = tools.query(f"SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='{target}'")
    require(not exists, "Target database already exists; nothing was changed. Choose a new recovery database.")
    # No IF NOT EXISTS: racing creation fails rather than touching someone else's database.
    tools.query(f"CREATE DATABASE `{target}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci")
    try:
        with (directory / "database.sql").open("rb") as stream:
            tools.run("mysql", ["--binary-mode", f"--database={target}"], stdin=stream)
        require(tools.check_schema(target) == sorted(manifest["tables"]), "Recovered table set differs from backup.")
        # Re-dump and compare deterministic row hashes: counts alone cannot detect changed JSON/report snapshots.
        with tempfile.TemporaryDirectory(prefix="personnel-recovery-check-") as temporary:
            sql = Path(temporary) / "restored.sql"
            tools.dump(target, sql)
            require(dump_inventory(sql) == manifest["tables"], "Recovered row counts/content differ from backup.")
    except (MaintenanceError, OSError):
        raise MaintenanceError(f"Recovery failed; isolated database {target} was retained for diagnosis. Live database was not replaced.") from None
    report = {"verifiedAt": dt.datetime.now(dt.timezone.utc).isoformat(), "sourceDatabase": manifest["sourceDatabase"],
              "targetDatabase": target, "sqlSha256": manifest["sqlSha256"], "tables": manifest["tables"], "result": "VERIFIED"}
    return report


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    commands = result.add_subparsers(dest="operation", required=True)
    for name in ("backup", "verify", "restore"):
        command = commands.add_parser(name)
        command.add_argument("--backup-dir", type=Path, required=True)
        if name == "verify":
            continue
        command.add_argument("--mysql-bin", default=os.environ.get("PERSONNEL_MYSQL_BIN"))
        command.add_argument("--host", default=os.environ.get("PERSONNEL_DB_HOST", "127.0.0.1"))
        command.add_argument("--port", type=int, default=int(os.environ.get("PERSONNEL_DB_PORT", "3306")))
        command.add_argument("--user", default=os.environ.get("PERSONNEL_DB_USER", "root"))
        if name == "backup":
            command.add_argument("--database", default=os.environ.get("PERSONNEL_DB_NAME", "personnel_system"))
        else:
            command.add_argument("--target-database", required=True)
            command.add_argument("--confirm-target", required=True)
            command.add_argument("--report", type=Path, required=True)
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        if args.operation == "verify":
            manifest = verify(args.backup_dir)
            print(f"VERIFIED: {len(manifest['tables'])} tables; SQL SHA256 {manifest['sqlSha256']}")
        elif args.operation == "backup":
            manifest = backup(DatabaseTools(args), args.database, args.backup_dir)
            print(f"BACKUP COMPLETE: {len(manifest['tables'])} tables; {args.backup_dir.resolve()}")
        else:
            # Reserve output before DB mutations and refuse to overwrite an existing report.
            args.report.parent.mkdir(parents=True, exist_ok=True)
            with args.report.open("x", encoding="utf-8") as output:
                report = restore(DatabaseTools(args), args.backup_dir, args.target_database, args.confirm_target)
                json.dump(report, output, ensure_ascii=False, indent=2)
                output.write("\n")
            print(f"RECOVERY VERIFIED: {args.target_database}; {len(report['tables'])} tables; report {args.report.resolve()}")
        return 0
    except (MaintenanceError, OSError) as error:
        # OS errors can expose only tool/file paths; database tool stderr is always suppressed.
        print(f"ERROR: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
