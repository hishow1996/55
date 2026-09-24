# Repo 55 native Chromium feature port

This directory is the native integration boundary for repo 55.

Rules:
- Chromium/Kiwi is the browser engine and Android browser runtime.
- Chromium's native Extensions runtime is the only extension/plugin runtime.
- Android System WebView and the deleted custom JS extension runtime are not allowed.
- The original repo-55 Compose screens remain reference material during the port, but they are not the browser engine.
- Build locally with `scripts/build_local_chromium.sh`; GitHub Actions is intentionally not part of the workflow.

The first native settings surface ports repo-55 state into Chromium-owned Android code:
- bottom toolbar preference
- desktop mode preference
- night mode preference
- global PiP preference
- AI feature preference

Subsequent native UI/service ports should consume these preferences through Chromium tab/profile/activity services rather than recreating WebView bridges.
