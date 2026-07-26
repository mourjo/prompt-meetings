# Plan: Add GitHub Actions CI Workflow

Add a GitHub Actions CI workflow to run tests automatically on `push` or `pull_request` to the `main` branch.

## Proposed Changes

### 1. Create CI Workflow File
Create the GitHub Actions workflow file at `.github/workflows/ci.yml` that:
- Runs on push or pull requests to the `main` branch.
- Sets up Java 25 using the Temurin distribution.
- Caches Maven dependencies for faster subsequent runs.
- Makes `./mvnw` executable and runs `./mvnw -B clean test`.
