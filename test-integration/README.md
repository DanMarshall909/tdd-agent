# Integration Tests

This folder contains integration tests for the TDD Agent that require:
- Running OpenCode CLI
- Code coverage analysis
- End-to-end workflow testing

These tests are **not committed** to the repository and are for local development only.

## Setup

```bash
cd test-integration
```

## Manual Testing

To manually test OpenCode integration:

```bash
# Test that opencode can be called
opencode run --format json "Write a simple function that adds two numbers"

# View the response
# Should see JSON events like:
# {"type":"step_start",...}
# {"type":"text","part":{"text":"...generated code..."}}
# {"type":"step_finish",...}
```

## Unit Tests

The main project (`../`) has unit tests with mocked dependencies:

```bash
cd ..
gradle test  # Run all unit tests
```

## Notes

- Integration tests require opencode CLI to be installed and in PATH
- `test-integration/` folder is gitignored to avoid committing test artifacts
- See `../src/test/kotlin` for unit tests that don't require external dependencies
