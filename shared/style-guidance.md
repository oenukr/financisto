# financisto app Style Guide

# Introduction
This style guide outlines the coding conventions for Kotlin code.
It's based on canonical Kotlin guidelines, but with some modifications to address specific needs and
preferences.

# Key Principles
* **Readability:** Code should be easy to understand for all team members.
* **Maintainability:** Code should be easy to modify and extend.
* **Consistency:** Adhering to a consistent style across all projects improves
  collaboration and reduces errors.
* **Performance:** While readability is paramount, code should be efficient.

## Comments
* **Write clear and concise comments:** Explain the "why" behind the code, not just the "what".
* **Comment sparingly:** Well-written code should be self-documenting where possible.
* **Use complete sentences:** Start comments with a capital letter and use proper punctuation.

## Logging
* **Use Timber logging library:**  Use the Timber library for logging.
* **Log at appropriate levels:** DEBUG, INFO, WARNING, ERROR, CRITICAL
* **Provide context:** Include relevant information in log messages to aid debugging.

## Error Handling
* **Use specific exceptions:** Avoid using broad exceptions like `Exception`.
* **Handle exceptions gracefully:** Provide informative error messages and avoid crashing the program.

# Tooling
* **Code formatter:**  [detekt] - Enforces consistent formatting automatically.
* **Linter:**  [detekt] - Identifies potential issues and style violations.
