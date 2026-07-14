# Contributing

Thanks for helping improve CBU Find.

## Before you start

1. Search existing issues before opening a new one.
2. For a substantial feature or behavior change, open an issue first so the approach can be discussed.
3. Never include credentials, student data, or other private information in issues, screenshots, tests, or commits.

## Development workflow

1. Fork the repository and create a focused branch from the default branch.
2. Configure your own Firebase and Cloudinary development projects using the guides in `docs/`.
3. Keep changes small and use clear commit messages, such as `feat: add claim request flow` or `fix: preserve report filters`.
4. Build the debug application before submitting your change:

   ```powershell
   ./gradlew.bat :app:assembleDebug
   ```

5. Open a pull request that explains the problem, the solution, how it was tested, and any user-facing changes. Include screenshots for interface changes.

## Code style

- Follow the existing Kotlin and Jetpack Compose conventions.
- Prefer clear names and small, testable functions.
- Keep UI state in view models and data access in the repository layer.
- Update the README or setup guides when configuration or behavior changes.

