# AI / Agent Instructions — CleanSweep

This file contains rules and context specifically for AI coding assistants (Grok, Claude, Cursor, etc.) working in this repository.

**ALWAYS read this file at the start of any non-trivial task if it exists.**

## Critical Rule: Git Commits and Conventional Commits

> **If the user asks you to create git commits** (for example: "commit the changes", "use git to commit", "make a commit", "git commit this", or similar instructions to produce commit messages), you **MUST** follow the [Conventional Commits](https://www.conventionalcommits.org/) specification exactly.

### Requirements
- Use the standard format:
  ```
  <type>(<optional scope>): <description>

  [optional body]

  [optional footer(s)]
  ```
- Common types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`, `build`, `ci`
- Always use a clear, lowercase, present-tense description (no period at end of subject).
- Include a scope when it helps (e.g. `feat(swiper)`, `fix(ui)`, `docs(contributing)`).
- For breaking changes use `!` or `BREAKING CHANGE:` footer.

### Examples of Good Commit Messages
- `feat(swiper): move next/clear labels inline with icons`
- `fix(layout): reduce padding around image card to match tighter reference`
- `docs: add Conventional Commits requirement for AI agents`
- `refactor(cardstack): simplify maxCardHeight calculation`

### Why This Matters
- Enables automatic changelog generation.
- Supports semantic versioning.
- Keeps git history consistent and machine-readable.

**This rule must be visible in documentation.** Therefore the same note is annotated (as a comment or section) in:
- CONTRIBUTING.md
- README.md
- docs/*.md

This ensures that whenever these files are read (via tools or otherwise), the requirement is encountered and remembered across conversations.

---

## Other Notes
- Prefer editing existing files over creating new ones when possible.
- For Android/Kotlin changes, run `./gradlew :app:compileDebugKotlin` (or full assemble) to verify before committing.
- Follow the architecture and coding standards documented in CONTRIBUTING.md.
- When making UI changes, consider both phone (OrganizePhoneLayout) and expanded layouts.

If you are unsure about a commit message format, ask the user or default to a `chore` type with clear description.
