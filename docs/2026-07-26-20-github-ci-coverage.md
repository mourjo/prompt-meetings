# Plan: Run Test Coverage on Github CI

We need to execute the coverage tool on every commit in GitHub Actions CI, format the summary, and make the full coverage reports available.

## Proposed Changes

### 1. Update GitHub Actions Workflow (`.github/workflows/ci.yml`)
- Add a step after building and running tests to calculate and log a markdown coverage summary to `$GITHUB_STEP_SUMMARY`.
- Add a step to upload the full JaCoCo HTML report as an artifact using `actions/upload-artifact@v4`.

## Impact & Verification
- Test coverage will automatically run with every commit pushed or pull request submitted to the `main` branch.
- The build job summary will display a neat Markdown table with instruction and branch coverage.
- The full interactive HTML report will be accessible via GitHub Actions artifacts.
