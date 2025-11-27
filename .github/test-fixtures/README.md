# Test Fixtures

This directory contains test data for end-to-end runtime tests.

## Usage

Place pre-configured files here that should be used during E2E tests:

- `test-stats.db` - Pre-seeded SQLite database with test player data
- `test-config.yml` - Alternative plugin configuration for testing

## Example: Adding Test Data

To create a seeded database for tests:

1. Run a local server with SMPStats
2. Add test data (join with test accounts, perform actions)
3. Copy the `stats.db` file here as `test-stats.db`
4. Update the E2E workflow to copy this file before server start

## Current Fixtures

_No fixtures configured yet. Add files as needed for specific test scenarios._
