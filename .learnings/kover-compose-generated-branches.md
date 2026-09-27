# Kover counts Compose-compiler branches; composables are excluded from the gate

Established: 2026-09-27.

Kover's branch counter includes branches the Compose compiler generates on a
`@Composable` function's declaration line (recomposition skip and
default-argument checks). For `BruceTheme`, 6 of 14 branches were generated;
the one source-level `if` was fully covered, yet branch coverage read 57%.

Decision (owner, 2026-09-27): exclude `@Composable`-annotated functions from
Kover. Branching logic must live in plain functions, which the gate counts.
Composables are still tested for behaviour with Robolectric and on-device
Compose tests.

Evidence: Kover XML report, `Theme.kt` line 11 `mb=6 cb=6`, line 13 `mb=0 cb=2`.
