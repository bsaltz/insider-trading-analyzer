# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot application built with Kotlin that analyzes insider trading data. The application integrates:
- **Spring Shell**: Interactive command-line interface for the application
- **Spring Boot Web**: Web framework capabilities
- **Spring Data JDBC**: Database access layer with repository pattern
- **PostgreSQL**: Database for storing trading data
- **Google Cloud Platform**: Cloud integration via spring-cloud-gcp-starter
- **Google Cloud Vision**: OCR processing for PDF documents
- **Google Cloud Storage**: File storage and processing
- **Flyway**: Database migration management

## Architecture

The application follows a Spring Boot application structure with:

### Core Components
- **Main application entry point**: `src/main/kotlin/com/github/bsaltz/insider/InsiderTradingAnalyzerApplication.kt`
- **Spring Shell Commands**: `CongressHouseCommands.kt` for CLI interaction
- **HTTP Clients**: `CongressHouseHttpClient.kt` for external API integration
- **Parsers**: TSV and JSON parsing services for different data formats
- **OCR Processing**: `OcrProcessorService.kt` for PDF text extraction using Google Cloud Vision
- **Repository Layer**: Spring Data JDBC repositories for all entities

### Data Models
- **Congress House Filing**: Filing lists and individual filings
- **Periodic Transaction Reports**: Trading transaction data with detailed classifications
- **OCR Parse Results**: Stored OCR processing results
- **Congress House Members**: Member information and details

### Database Structure
- **Flyway Migrations**: 4 migration files (V1-V4) managing schema evolution
  - V1: Periodic transaction reports tables
  - V2: Congress House filing tables
  - V3: Congress House member table
  - V4: OCR parse result table
- **Relationships**: Foreign key constraints between filing lists, filings, and transactions

## Development Commands

### Build and Run
```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Run tests
./gradlew test

# Run a specific test class
./gradlew test --tests "com.github.bsaltz.insider.InsiderTradingAnalyzerApplicationTests"

# Run repository tests
./gradlew test --tests "*Repository*"

# Run service tests
./gradlew test --tests "*Service*"
```

### Database Setup
```bash
# Start PostgreSQL database
docker-compose up -d

# Stop PostgreSQL database
docker-compose down

# View database logs
docker-compose logs postgres

# Connect to database
docker exec -it insider-trading-postgres psql -U insider_user -d insider_trading
```

### Testing Framework

The project has comprehensive test coverage with:
- **Repository Tests**: Integration tests using Zonky embedded PostgreSQL
- **Service Tests**: Unit tests using Mockito Kotlin extensions
- **Flyway Integration**: Database schema management in tests
- **Test Isolation**: Each test uses fresh embedded database instances

#### Test Configuration
- **Embedded Database**: Uses Zonky for PostgreSQL integration tests instead of H2
- **Mocking**: Mockito Kotlin extensions for clean mock syntax
- **Test Profile**: Separate `application-test.yml` configuration

## Technology Stack

- **Language**: Kotlin 2.1.21
- **Framework**: Spring Boot 3.5.6 with Spring Framework
- **Build Tool**: Gradle with Kotlin DSL
- **Java Version**: 21
- **Database**: PostgreSQL with Flyway migrations
- **Testing**: JUnit 5 with Spring Boot Test, Zonky embedded PostgreSQL, Mockito Kotlin
- **Shell Interface**: Spring Shell 3.4.1
- **Cloud**: Google Cloud Platform (Vision API, Storage, Spring Cloud GCP)
- **Data Processing**: OCR via Google Cloud Vision API

## Key Dependencies

### Production Dependencies
- **Spring Boot**: Web, Data JDBC, DevTools
- **Spring Cloud GCP**: Starter, Vision API, Storage integration
- **Spring Shell**: Interactive command-line interface
- **Database**: PostgreSQL driver, Flyway migration
- **JSON Processing**: Jackson Kotlin module
- **Kotlin**: Reflection and standard library

### Test Dependencies
- **JUnit 5**: Core testing framework with Kotlin test support
- **Spring Boot Test**: Integration testing support
- **Zonky Embedded Database**: PostgreSQL embedded testing (2.5.1)
- **Mockito Kotlin**: Clean mocking syntax for Kotlin (5.4.0)
- **Spring Shell Test**: Command-line interface testing

## Domain Models and Enums

The application defines comprehensive domain models for insider trading analysis:

### Enums
- **FilerStatus**: MEMBER, OFFICER_OR_EMPLOYEE, CANDIDATE
- **FilingStatus**: NEW, AMENDED
- **TradeType**: PURCHASE, SALE, PARTIAL_SALE, EXCHANGE
- **Ownership**: SP (Spouse), DC (Dependent Child), JT (Joint)
- **AmountRange**: A_1001_15000 through J_OVER_50000000 (11 ranges)

## Git Workflow

This project uses a structured Git workflow with branch protection and automated merging.

### Creating a New Feature or Fix

**Always start from the latest main branch:**

```bash
# 1. Switch to main and pull latest changes
git checkout main
git pull

# 2. Create a new branch with a descriptive name
git checkout -b feat/your-feature-name
# or
git checkout -b fix/your-bug-fix
# or
git checkout -b docs/your-documentation-change
```

### Branch Naming Convention

Use descriptive branch names with prefixes:
- `feat/` - New features
- `fix/` - Bug fixes
- `docs/` - Documentation changes
- `refactor/` - Code refactoring
- `test/` - Test additions or modifications
- `chore/` - Maintenance tasks

### Making Changes

```bash
# 1. Make your code changes

# 2. Compile and test your changes
./gradlew compileKotlin
./gradlew test --tests "*ServiceTest" --tests "*CommandsTest"

# 3. Stage and commit with conventional commit format
git add -A
git commit -m "$(cat <<'EOF'
feat: your commit title

Detailed description of what changed and why.
Multiple lines are fine.

- Bullet point 1
- Bullet point 2

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
)"
```

### Commit Message Format

Use conventional commits with the Claude signature:
- **Type**: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`
- **Title**: Brief summary (imperative mood)
- **Body**: Detailed explanation with bullet points
- **Signature**: Always include Claude Code attribution

Example:
```
feat: add CSV export for transactions

Export PTR transactions to CSV format with proper escaping
for special characters (commas, quotes, newlines).

- Add getTransactionsForYear method to HousePtrService
- Add export command with --output and --year options
- Implement CSV escaping helper method

🤖 Generated with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
```

### Creating and Merging Pull Requests

```bash
# 1. Push your branch to GitHub
git push -u origin your-branch-name

# 2. Create a pull request with default title and body from commit
gh pr create --fill

# 3. Enable auto-merge with squash
gh pr merge <PR_NUMBER> --auto --squash

# 4. View PR status
gh pr view <PR_NUMBER>
```

### After PR is Merged

```bash
# 1. Switch back to main
git checkout main

# 2. Pull the merged changes
git pull

# 3. Clean up local branches (optional)
git fetch --prune
git branch -d your-branch-name
```

### Notes

- **Branch Protection**: Main branch requires passing CI checks before merge
- **Auto-merge**: PRs will automatically merge once CI passes
- **Squash Merge**: All PRs are squashed into a single commit on main
- **CI Requirements**: All tests must pass, including unit and integration tests
- **Test Failures**: Repository tests may fail due to Docker/Zonky connectivity issues; service and command tests are the primary validation