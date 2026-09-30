# PdfBox-Android (for TASK-084)

Retrieved 2026-09-30 with curl:

- `maven-metadata.xml`: https://repo1.maven.org/maven2/com/tom-roush/pdfbox-android/maven-metadata.xml
  — latest `com.tom-roush:pdfbox-android` is 2.0.27.0.
- `pdfbox-android-2.0.27.0.pom` — depends (compile scope) on `org.bouncycastle:bcprov-jdk15to18`,
  `bcpkix-jdk15to18` and `bcutil-jdk15to18`, all 1.72. The AAR is 3.25 MB.
- `github-repo.json`: https://api.github.com/repos/TomRoush/PdfBox-Android — Apache-2.0, last push
  2024-03-18, 126 open issues, not archived.
- `github-issues-recent.json` (the releases API) — last release v2.0.27.0 on 2023-01-02.
- `osv-*.json`: https://api.osv.dev/v1/query — no advisories for pdfbox-android 2.0.27.0 or
  Apache PDFBox 2.0.27; bcprov-jdk15to18 1.72 has 10 (DoS, excessive allocation, timing
  channels including Marvin, LDAP injection, name-constraint bypass) and bcpkix 2. Latest
  bcprov-jdk15to18 is 1.86.

BouncyCastle is used by PDFBox for encrypted PDFs (public-key security handlers).
