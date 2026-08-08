# Default Behavior

## Task Organization and Commits

Before making changes, divide the requested work into logical, independently verifiable categories.

For each category:

1. Complete only that category.
2. Run the smallest relevant verification or test.
3. Review the changed files and exclude unrelated user changes.
4. Commit automatically after verification succeeds.

Commit rules:

- Create one atomic commit per completed category.
- Use Conventional Commits with a concise message, such as `feat:`, `fix:`, `refactor:`, `test:`, or `docs:`.
- Never commit unrelated, pre-existing, or user-owned changes.
- Do not commit incomplete or failing work.
- Do not amend, squash, rebase, or push unless explicitly requested.
- If changes cannot be separated safely, stop and ask before committing.

## Default Skills

Always use:

- Ponytail
- Caveman

These should be applied automatically unless I explicitly disable them.

Be concise, but never omit important reasoning, trade-offs, risks, or implementation details when they are necessary for making a correct decision.

## Superpowers

Do NOT use the Superpowers workflow automatically.

Before using Superpowers, ask for my permission.

Only suggest Superpowers when you genuinely believe the task would significantly benefit from it (for example: large architecture changes, major refactoring, or complex multi-step implementations).

Wait for my approval before invoking any Superpowers workflow.