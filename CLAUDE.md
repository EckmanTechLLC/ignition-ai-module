# Claude Development Guidelines

**Last Updated:** 2026-01-30

---

## Quick Reference

**Project:** Ignition AI Module
**Tech Stack:** Java 17 + Ignition SDK 8.1.16 + JavaScript (Perspective Component)
**Key Docs:**
- `/README.md` - Installation and usage
- `/TODO_TOOLS.md` - Tools to implement
- `/TODO_ARCHITECTURE.md` - Architecture decisions needed

---

## User Preferences (Apply Always)

- **Never be verbose** - clear, concise responses
- **Small, incremental changes** - don't build everything at once
- **Ask before expanding scope** - stay within defined task
- **Document as you go** - update docs and TODOs
- **Show, don't just talk about it**
- **Always check the codebase** - read files, never rely on memory
- **Research before suggesting** - check docs/libraries, don't guess
- **Plan before implementing** - get approval on approach first

---

## What NOT to Do

- Don't implement without explicit plan approval
- Don't guess at solutions - research first
- Don't use memory - always read files
- Don't add features not requested
- Don't be verbose in responses or documentation
- **Don't start/stop/restart services** (only user does this)
- **Don't run tests** (only user runs tests)
- Don't check if services are running

---

## Development Workflow

**Build System:**
- Implementation sessions CAN run: `./gradlew clean build -x test`
- This is the ONLY build command allowed
- Implementation sessions can write code and prepare changes

**What Implementation Sessions CAN Do:**
- ✅ Build module: `./gradlew clean build -x test` (ONLY THIS COMMAND)
- ✅ Write code changes
- ✅ Write tests
- ✅ Document how user should test
- ✅ Provide commands for user to run
- ✅ Read files and analyze code

**What Implementation Sessions CANNOT Do:**
- ❌ Run tests: `./gradlew test`
- ❌ Start Ignition Gateway
- ❌ Install/uninstall modules
- ❌ Execute tests (user runs tests)
- ❌ Check if Gateway is running
- ❌ Run any gradle command except `./gradlew clean build -x test`

**Testing:**
- Implementation sessions write tests
- **USER RUNS ALL TESTS** - Implementation sessions never execute tests
- Provide clear test commands for user to run
- Document expected test results

---

## Implementation Session Checklist

**For EVERY code change, follow this checklist:**

### 1. READ FIRST
- [ ] Read CLAUDE.md (this file) to understand context
- [ ] Read relevant TODO files for feature area
- [ ] Read existing code files you'll modify
- [ ] Verify your understanding of current state

### 2. VERIFY ASSUMPTIONS
- [ ] Check if files/functions exist where you expect
- [ ] Verify API endpoints/interfaces match your assumptions
- [ ] Confirm dependencies are available
- [ ] List what you'll change BEFORE making changes

### 3. MAKE CHANGES
- [ ] Make ONE logical change at a time
- [ ] Keep changes small and focused
- [ ] Follow existing code patterns and style
- [ ] Add/update tests for changed behavior
- [ ] Update relevant documentation

### 4. BUILD & VERIFY (NO TESTING)
- [ ] Run build: `./gradlew clean build -x test`
- [ ] Verify compilation succeeds
- [ ] Write test cases (but don't run them)
- [ ] Document expected test results
- [ ] **NEVER run tests** - user runs all tests
- [ ] **NEVER start Gateway** - user controls services

### 5. DOCUMENT
- [ ] Update TODO files if task completed
- [ ] Note any issues encountered and how resolved
- [ ] List files created/modified
- [ ] Update CLAUDE.md if status changed
- [ ] **Provide commands for user to run** (build, test, install)

### 6. REPORT
- [ ] Provide concise summary to user
- [ ] List commands user should run to test
- [ ] Report any blockers or questions
- [ ] Confirm scope was not exceeded
- [ ] List next steps if applicable

**If you skip any step, you're doing it wrong.**
**If you run builds or tests, you're doing it VERY wrong.**

---

## Session Start Protocol

1. **User provides task**: Specific, bounded scope
2. **You confirm**: Restate task, list files you'll touch, ask for approval
3. **You work**: Follow checklist above
4. **You report**: Summary + files changed + commands for user to run
5. **User verifies**: Builds, tests, and confirms correctness
6. **Next task**: User provides prompt for next session

**CRITICAL REMINDER:**
- Implementation sessions write code and tests
- Implementation sessions CAN build: `./gradlew clean build -x test`
- **User runs tests** - never run `./gradlew test`
- Never execute test commands
- Never start Gateway or install modules

---

## Code Quality Standards

**Java:**
- Follow existing patterns in the codebase
- Use try-catch for error handling
- Follow Ignition SDK conventions
- Test with Ignition 8.1.8+
- Use LoggerEx for logging (not System.out)

**JavaScript (Perspective Component):**
- Plain JavaScript (no TypeScript/build tools)
- Use React from `window.React`
- Follow PerspectiveClient component patterns
- Use `emit()` for Perspective layout integration
- No dangerouslySetInnerHTML

**General:**
- No TODOs in code (track in TODO_*.md files)
- No commented-out code
- Clear variable/function names
- Keep functions small (<100 lines)
- Follow Ignition SDK best practices

---

## Known Issues

- Conversation token limits cause hallucination after ~50 messages with heavy tool use
- System prompt struggles with balancing exploration vs tool usage
- Missing tools: ListTagProvidersTool, ListGatewayModulesTool, ListProjectsTool
