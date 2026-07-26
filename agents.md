# Agent Instructions & Guidelines

This document outlines the rules and methodologies that AI agents must follow when working on the `prompt-meetings` codebase.

## 1. Documentation & Plans
- **Location:** Always write plans and specifications in the [docs/](file:///Users/mourjo/repos/prompt-meetings/docs) directory.
- Always keep the readme file updated with what the application does, no need to write any endpoints here, explain the goal of the project and the capabilities and its entities

## 2. Development Methodology
Always follow the **Record**, **Explore, Plan, Implement** method:
1. **Record:** Save the prompt the user asked verbatim in the `prompts/` directory - follow the file name as `YYYY-MM-DD-XX-slug.md` where XX is an increasing sequence (01, 02. 03 and so on) and slug is the short description of the ask.
2. **Explore:** Research the codebase, run diagnostic searches, and understand the existing structure and requirements before writing any code.
3. **Plan:** Outline the proposed solution, steps, and impact in a document under the `docs/` directory - follow the file name as `YYYY-MM-DD-XX-slug.md` where XX is an increasing sequence (01, 02. 03 and so on) and slug is the short description of the ask
4. **Implement:** Implement the proposed changes according to the approved plan.

## 3. Testing
- **Test Cases:** Always add test cases for any changes, features, or bug fixes.
- Do not run any property based test (skip them) in the interest of quick feedback

## 4. Commit
- **Commit** your changes once tests pass
- Add the summary of changes in the body of the commit