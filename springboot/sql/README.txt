BangBangAgro database SQL layout
================================

Recommended first import
------------------------
Use this file for a clean MySQL 8.0+ database:

  springboot/sql/init_all.sql

Example:

  mysql -u root -p < springboot/sql/init_all.sql

The script creates database `smart-agriculture`, switches into it, creates the base
tables, then applies the required project migrations.

Folder layout
-------------
00_schema
  Base schema and module table creation scripts.

01_migrations
  Upgrade scripts for an existing database, such as new user fields, AI config
  fields, ZET6 sensor fields, friend UID fields, and score attribution fields.

02_seed
  Optional demo data. Do not import this automatically unless demo records are
  wanted.

03_maintenance
  One-off repair or cleanup scripts. Do not run these during a normal first
  install.

Notes
-----
000_base_schema.sql was generated as a schema-only file from an existing full
dump found under .claude/worktrees. Data rows were intentionally excluded so the
project does not ship old runtime data, accounts, passwords, or chat/history
records.

init_all.sql intentionally does not include:
  02_seed/demo_seed_data.sql
  03_maintenance/cleanup_corrupted_ai_reports.sql
  03_maintenance/update_role_menu_to_report.sql

If you need demo data after importing init_all.sql, run:

  mysql -u root -p smart-agriculture < springboot/sql/02_seed/demo_seed_data.sql
