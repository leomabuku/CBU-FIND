# Security Policy

## Reporting a vulnerability

Please do not disclose security vulnerabilities in a public issue. Use GitHub's private vulnerability reporting feature for this repository when available, or contact the repository owner privately through their GitHub profile.

Include a concise description, reproduction steps, affected versions, and the potential impact. Do not include real student data or active credentials.

## Credential safety

The following files and values must remain local and are excluded from version control:

- `local.properties`
- `app/google-services.json`
- Android signing keys
- Firebase Admin service-account files
- Cloudinary API secrets

If a privileged credential is exposed, revoke or rotate it immediately before removing it from the repository history.
