# SpeakMate — Project Guidelines

## 1. Project Overview

SpeakMate is an AI-powered English learning application with two core features:
- AI speaking practice with level-based conversations, grammar and vocabulary feedback.
- Vocabulary flashcards that users can create manually or generate with AI.

Prioritize a reliable, easy-to-use learning experience over unnecessary features.

## 2. Technology Stack

- **Android:** Java, XML, Retrofit
- **Backend:** Java, Spring Boot, REST API
- **Database:** MySQL, Spring Data JPA
- **AI:** Gemini API, accessed exclusively through the backend

Inspect the existing repository before assuming specific library versions, project structure, build tools, or implementation details. Follow the technologies and conventions already established in the project.

## 3. Coding Principles

- Write simple, readable code that is easy to understand, explain, debug, and rewrite manually.
- Follow existing naming, formatting, and coding conventions.
- Keep responsibilities clear between Controller, Service, Repository, Entity, DTO, and Android components.
- Prefer straightforward solutions over unnecessary abstractions, design patterns, layers, or dependencies.
- Keep methods focused and avoid deeply nested or overly complicated logic.
- Avoid duplicated business logic and unnecessary code.
- Use comments only when they explain non-obvious logic, business rules, or important decisions.
- Do not optimize prematurely or introduce complexity without a clear benefit.

## 4. Working with the Existing Codebase

- Inspect relevant files and understand their dependencies before making changes.
- Identify existing implementations that can be reused before creating new classes or methods.
- Make the smallest set of changes necessary to complete the requested task.
- Preserve working functionality, existing API contracts, naming conventions, and database relationships unless a change is necessary.
- Do not rewrite entire files, refactor unrelated code, or add unrequested features.
- Do not introduce placeholder implementations when functional code is required.
- If an important requirement is unclear, inspect the codebase and available project documentation first. State any necessary assumptions rather than inventing requirements.

## 5. Backend and REST API Rules

- Use consistent REST resource naming, HTTP methods, request/response formats, and appropriate HTTP status codes.
- Keep Controllers focused on handling HTTP requests and responses; put business rules in Services and database operations in Repositories.
- Use DTOs for API input and output where appropriate. Avoid exposing internal Entity structures unnecessarily.
- Validate client input and return clear, consistent error responses.
- Handle missing resources, invalid input, unauthorized access, and business rule violations explicitly.
- Enforce authentication, authorization, and resource ownership on the backend. Never trust a user ID supplied by the client as proof of identity or permission.
- Do not change an API contract without checking its impact on Android and other callers.
- Keep database schema changes deliberate and update related Entity mappings, migrations or schema files, and affected queries when necessary.

## 6. Gemini API and AI Responses

- Call Gemini exclusively from the Spring Boot backend. Never expose the Gemini API key in Android code, client responses, source control, or logs.
- Keep AI integration logic in a dedicated service, such as `AIService`, following the existing project structure.
- Use structured JSON output when a feature requires predictable fields, such as corrected sentences, explanations, or flashcard content.
- Validate and parse AI responses before returning or persisting them. Do not assume generated content is always complete, valid, or correct.
- Handle timeouts, rate limits, API errors, malformed responses, and empty results gracefully.
- Preserve conversation order and associate messages and flashcards with the correct user and session.
- Do not unnecessarily resend the entire conversation history when a smaller, sufficient context is available.
- Do not claim that AI-generated grammar corrections, vocabulary, or explanations have been independently verified unless they have.

## 7. Security and Data Integrity

- Never hardcode API keys, passwords, tokens, database credentials, or other secrets.
- Use environment variables or the project's established secure configuration mechanism.
- Never store plaintext passwords.
- Ensure users can access or modify only resources they are authorized to access.
- Validate important business rules on the backend, not only in the Android interface.
- Avoid logging credentials, tokens, or sensitive user content.
- Keep database updates consistent, especially when multiple related records must be saved together.
- Use transactions or concurrency controls when required to prevent inconsistent data.

## 8. Android Rules

- Use Java and XML, following the existing application structure.
- Use Retrofit for communication with the Spring Boot REST API.
- Keep network and data-handling logic separate from UI code according to the existing architecture.
- Handle loading, success, empty, and error states clearly.
- Avoid blocking the main thread with network requests or long-running operations.
- Handle microphone permissions and speech recognition errors explicitly.
- Do not expose secrets or implement critical authorization rules in the Android client.
- Do not introduce additional Android frameworks or dependencies without a clear need.

## 9. Implementation Workflow

For each requested feature:

1. Inspect the relevant code and identify the smallest complete implementation path.
2. Briefly outline the required changes when the task involves multiple files or significant logic.
3. Implement one feature or complete workflow at a time.
4. Reuse existing components and conventions instead of creating parallel implementations.
5. Build and test the affected components before moving on.
6. Review the final diff for unrelated changes, duplicated code, security issues, and accidental behavior changes.

For small, straightforward changes, proceed directly without unnecessary planning or repeated explanations.

## 10. Verification and Reporting

- Run relevant build, test, and lint commands when available.
- Use the actual build system and commands detected in the repository.
- Report the commands executed and their actual results.
- If a command fails, investigate whether the failure was caused by the changes or by an existing environment or configuration issue.
- Never claim that code builds, tests pass, or a feature works unless the relevant checks were actually performed.
- Summarize changed files, important logic, test results, and any remaining risks.
- Keep explanations concise and focused on what the developer needs to understand or verify.

## 11. Git and Change Safety

- Check the current Git status before making changes.
- Preserve existing uncommitted work and avoid overwriting changes made by other contributors.
- Do not run destructive Git commands or rewrite Git history.
- Do not commit, push, force-push, merge branches, or modify remote repository settings unless explicitly requested.
- Keep changes small and reviewable so they can be inspected and reverted safely.

## 12. Primary Goal

Build SpeakMate as a functional, maintainable application while keeping the implementation understandable to the developer.

Correctness, clarity, security, and reliable end-to-end behavior take priority over code volume, unnecessary sophistication, and the number of features implemented.