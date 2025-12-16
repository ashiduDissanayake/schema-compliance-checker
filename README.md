# Schema Compliance Checker

A zero-risk migration readiness tool that validates database schema integrity by comparing user database dumps against golden standard schemas in isolated Docker containers.

## Features

- **Non-Destructive**:  Works entirely with database dumps - no connection to production databases required
- **Multi-Database Support**: MySQL, Oracle, MSSQL, PostgreSQL
- **Deep Inspection**: Compares tables, columns, indexes, constraints, stored procedures, triggers, sequences, and views
- **Intelligent Diffing**: Normalizes data types across databases for accurate comparison
- **Detailed Reports**: CLI output + JSON reports with severity-based categorization
- **Docker Isolation**: Each comparison runs in fresh containers for complete isolation

## Prerequisites

- **Java 21** or later
- **Docker** running locally
- **Maven 3.8+** for building

## Quick Start

### 1. Build the Project

```bash
mvn clean package -DskipTests
```

### 2. Prepare Your Schema Files

Place your golden standard SQL file in the `standards/` directory:

```
standards/
├── mysql/
│   └── golden_schema.sql
├── oracle/
│   └── golden_schema.sql
└── mssql/
    └── golden_schema.sql
```

### 3. Run the Checker

```bash
# Basic usage
java -jar target/schema-compliance-checker-1.0.0.jar mysql /path/to/user_dump.sql

# With custom standard schema
java -jar target/schema-compliance-checker-1.0.0.jar oracle dump.sql --standard /path/to/golden.sql

# With custom output directory
java -jar target/schema-compliance-checker-1.0.0.jar mssql backup.sql --output ./my-reports
```

## Command Line Options

| Option | Description |
|--------|-------------|
| `<db-type>` | Database type:  `mysql`, `oracle`, `mssql`, `postgresql` |
| `<user-dump-path>` | Path to the user's SQL dump file |
| `--standard <path>` | Path to standard schema SQL file |
| `--output <dir>` | Output directory for reports (default: `reports/`) |
| `--no-views` | Skip view comparison |
| `--no-triggers` | Skip trigger comparison |
| `--no-sequences` | Skip sequence comparison |

## Sample Output

```
╔══════════════════════════════════════════════════════════════════╗
║           SCHEMA COMPLIANCE REPORT                               ║
╠══════════════════════════════════════════════════════════════════╣
║  Report ID:          a1b2c3d4...                                   ║
║  Generated:         2025-12-16T10:30:45                          ║
║  Compliance Score:  87.5%                                        ║
╠══════════════════════════════════════════════════════════════════╣
║  Migration Status:  ❌ NOT READY - ISSUES FOUND                  ║
╠══════════════════════════════════════════════════════════════════╣
║  DRIFT SUMMARY                                                   ║
╠══════════════════════════════════════════════════════════════════╣
║  🔴 CRITICAL     :   2                                            ║
║  🟠 HIGH         :  5                                            ║
║  🟡 MEDIUM       :   12                                           ║
║  🟢 LOW          :  8                                            ║
╚══════════════════════════════════════════════════════════════════╝

🔴 CRITICAL ISSUES (Must Fix):
──────────────────────────────────────────────────────────────────────
   • [Table] AM_APPLICATION
     └─ Table 'AM_APPLICATION' exists in standard but missing in user schema
   • [Column] AM_API. API_UUID
     └─ Column 'AM_API.API_UUID' exists in standard but missing in user schema
```

## JSON Report Structure

```json
{
  "reportId": "a1b2c3d4-.. .",
  "generatedAt": "2025-12-16T10:30:45Z",
  "summary": {
    "complianceScore": 87.5,
    "totalDrifts": 27,
    "criticalDrifts": 2,
    "migrationReady": false
  },
  "drifts": {
    "Tables": [... ],
    "Columns": [... ],
    "Routines": [...]
  },
  "recommendations": [...]
}
```

## Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Schema is migration-ready |
| 1 | Schema has issues (not migration-ready) |
| 2 | Configuration/usage error |
| 3 | Unexpected error |

## Architecture

```
┌─────────────────┐     ┌─────────────────┐
│  Standard SQL   │     │   User Dump     │
│  (Golden)       │     │   (. sql file)   │
└────────┬────────┘     └────────┬────────┘
         │                       │
         ▼                       ▼
┌─────────────────┐     ┌─────────────────┐
│  Docker         │     │  Docker         │
│  Container A    │     │  Container B    │
│  (Standard DB)  │     │  (User DB)      │
└────────┬────────┘     └────────┬────────┘
         │                       │
         ▼                       ▼
┌─────────────────┐     ┌─────────────────┐
│  Schema         │     │  Schema         │
│  Snapshot A     │     │  Snapshot B     │
└────────┬────────┘     └────────┬────────┘
         │                       │
         └───────────┬───────────┘
                     ▼
            ┌─────────────────┐
            │   Diff Engine   │
            │   (Comparison)  │
            └────────┬────────┘
                     │
                     ▼
            ┌─────────────────┐
            │  Compliance     │
            │  Report         │
            │  (CLI + JSON)   │
            └─────────────────┘
```

## License

Apache License 2.0