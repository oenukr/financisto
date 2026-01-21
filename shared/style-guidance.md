# financisto app Style Guide

# Introduction
This style guide outlines the coding conventions for Kotlin code.
It's based on canonical Kotlin guidelines, but with some modifications to address specific needs and
preferences.

# Key Principles
- **Readability:** Code should be easy to understand for all team members.
- **Maintainability:** Code should be easy to modify and extend.
- **Consistency:** Adhering to a consistent style across all projects improves
  collaboration and reduces errors.
- **Performance:** While readability is paramount, code should be efficient.

## Development Conventions
- **Language:** Use Kotlin for all new code.
- **Dependency Injection:** Define new dependencies in `JavaAppKoin.kt`.
- **Coding Style:** Try to follow the style of other code parts and ensure `detekt` passes before committing. Prefer the composition over inheritance whenever it's possible. Use `runCatching` in Kotlin code instead of the `try` and `catch`. Avoid using not-null assertion operator `!!` in Kotlin. 
- **Database:** Database schema and migrations are located in `app/src/main/assets/database`.

## Comments
- **Write clear and concise comments:** Explain the "why" behind the code, not just the "what".
- **Comment sparingly:** Well-written code should be self-documenting where possible.
- **Use complete sentences:** Start comments with a capital letter and use proper punctuation.

## Logging
- **Logging:** Always use `Timber` via the `Logger` interface or directly.
- **Log at appropriate levels:** DEBUG, INFO, WARNING, ERROR, CRITICAL
- **Provide context:** Include relevant information in log messages to aid debugging.

## Error Handling
- **Use specific exceptions:** Avoid using broad exceptions like `Exception`.
- **Handle exceptions gracefully:** Provide informative error messages and avoid crashing the program.
