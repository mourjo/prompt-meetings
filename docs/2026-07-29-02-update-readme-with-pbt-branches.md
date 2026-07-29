# Plan: Update README with Property-Based Testing Branches

This plan outlines the steps to update `README.md` to document the six active development branches containing property-based tests (PBT) and their respective features.

## Proposed Changes

We will add a new section to [README.md](file:///Users/mourjo/repos/prompt-meetings/README.md) under a header `## Development Branches`. This section will list the six branches with their descriptions and direct links to their GitHub pages.

### Branches and Summaries

1. **[basic-features](https://github.com/mourjo/prompt-meetings/tree/basic-features)**: Contains core application features before adding property-based tests.
2. **[pbt-10-invariant-no-user-can-be-in-two-meetings](https://github.com/mourjo/prompt-meetings/tree/pbt-10-invariant-no-user-can-be-in-two-meetings)**: Introduces property-based tests verifying users are not in overlapping accepted meetings.
3. **[pbt-20-disallow-rejection-when-accepted](https://github.com/mourjo/prompt-meetings/tree/pbt-20-disallow-rejection-when-accepted)**: Restricts accepting invitations to only those currently in a pending state.
4. **[pbt-30-accept-and-auto-reject](https://github.com/mourjo/prompt-meetings/tree/pbt-30-accept-and-auto-reject)**: Implements calendar priority-based conflict resolution when users accept invitations.
5. **[pbt-40-invariant-auto-rejections-should-be-justified](https://github.com/mourjo/prompt-meetings/tree/pbt-40-invariant-auto-rejections-should-be-justified)**: Distinguishes system auto-rejections and verifies they overlap with higher-priority meetings.
6. **[pbt-50-rejection-allowed-only-for-pending-invites](https://github.com/mourjo/prompt-meetings/tree/pbt-50-rejection-allowed-only-for-pending-invites)**: Prevents users from rejecting invitations that are not in a pending state.

## Steps

1. **Record**: Recorded the prompt in `prompts/2026-07-29-02-update-readme-with-pbt-branches.md`. (Done)
2. **Explore**: Explored the codebase, branch logs, and docs to determine branch functions. (Done)
3. **Plan**: Write the plan in `docs/2026-07-29-02-update-readme-with-pbt-branches.md`. (This file)
4. **Implement**: Modify [README.md](file:///Users/mourjo/repos/prompt-meetings/README.md) to append the list.
5. **Testing**: Run standard tests to ensure formatting and documentation structure are intact.
6. **Commit**: Commit the changes.
