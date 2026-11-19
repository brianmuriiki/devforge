# Releasing DevForge

Releases are created by pushing a version tag such as `v0.1.0`. The release workflow builds a Debian package on Linux, a
signed and notarized DMG on macOS, and an Authenticode-signed EXE on Windows. Each artifact set includes an OS-specific
SHA-256 manifest.

Configure these repository secrets before pushing a release tag:

| Platform | Required secrets |
|---|---|
| macOS | `MACOS_CERTIFICATE` (base64-encoded Developer ID Application `.p12`), `MACOS_CERTIFICATE_PASSWORD`, `MACOS_KEYCHAIN_PASSWORD`, `MACOS_SIGNING_IDENTITY`, `MACOS_PACKAGE_IDENTIFIER`, `APPLE_ID`, `APPLE_APP_SPECIFIC_PASSWORD`, `APPLE_TEAM_ID` |
| Windows | `WINDOWS_SIGNING_CERTIFICATE` (base64-encoded code-signing `.pfx`), `WINDOWS_SIGNING_CERTIFICATE_PASSWORD`, `WINDOWS_TIMESTAMP_URL` |

The workflow validates every required secret before packaging. It signs and verifies the Windows installer, submits the
macOS DMG to Apple notary service, staples its ticket, and validates the stapled result. A missing credential or failed
verification fails the release instead of creating an unsigned artifact.

Before tagging, run:

```bash
./mvnw clean verify
```

After downloading a release, verify the artifact with its matching checksum manifest before installing it.
