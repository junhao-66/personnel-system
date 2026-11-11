# Database setup

The application runs `src/main/resources/schema.sql` at startup. The script only creates missing tables and upserts the three built-in role definitions; it does not delete or truncate existing records.

Tables currently established:

- `sys_user`: account, role, registration review, department and position references.
- `sys_role`: administrator, department manager and employee role definitions.
- `sys_department`: department and responsible manager references.
- `sys_position`: position definitions and optional department association.
- `employee_profile`: hire date and active/employment status, created when a registration is approved.

To bootstrap the first administrator, register an account through the application, then execute `src/main/resources/bootstrap-admin.sql` after replacing its username predicate with the account you own. The script is intentionally not auto-run and contains no default password or default administrator account.

Database credentials belong in the local profile and must not be committed to a shared repository. Production should use environment-based secrets and managed schema migrations.
