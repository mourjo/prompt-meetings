# Plan: Run GitHub Action on All Branches

This document outlines the plan to update the GitHub Actions CI workflow to run on push and pull request events for all branches.

## Proposed Changes

Currently, the workflow in [.github/workflows/ci.yml](file:///Users/mourjo/repos/prompt-meetings/.github/workflows/ci.yml) is configured to only run when changes are pushed or target the `main` branch:

```yaml
on:
  push:
    branches: [ "main" ]
  pull_request:
    branches: [ "main" ]
```

We will update the workflow configuration to run on all branches. To achieve this, we will use the `**` glob pattern, which matches all branches (including hierarchical ones containing slashes, e.g., `feature/branch-name`).

### Target Configuration

```yaml
on:
  push:
    branches:
      - '**'
  pull_request:
    branches:
      - '**'
```

## Steps

1. Update [.github/workflows/ci.yml](file:///Users/mourjo/repos/prompt-meetings/.github/workflows/ci.yml) with the new branch configuration.
2. Run local validation tests to ensure Maven builds and compiles cleanly.
3. Commit the changes following the repository guidelines.

## Impact

- All branches will now benefit from automated builds, unit testing, and coverage reports on push.
- Pull requests targeting any branch will trigger the CI pipeline.
