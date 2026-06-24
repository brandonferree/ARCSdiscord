# hrf-engine — vendored HRF Arcs rules engine

These are **vendored, point-in-time sources** from the upstream HRF
(`haunt-roll-fail`) project, version `0.8.140`, used to run the **Arcs: The
Blighted Reach** rules engine headless on the JVM. They are **not** original to
this project.

- **License:** MIT — see [`LICENSE`](LICENSE) (Copyright (c) 2024
  haunt-roll-fail). The MIT notice is retained here as required.
- **Contents:** the `hrf` framework core (top-level `*.scala`) + the `arcs/`
  game package. Browser/Scala.js sources are present but excluded from the JVM
  build (see `../build.sbt` `hrfEngine` `excludeFilter`).
- **Do not edit the rules logic.** Treat this as an external dependency: only the
  build/packaging around it (in `../build.sbt`) is ours. Keeping these files
  pristine makes re-syncing upstream fixes trivial.

See [`../docs/M1.md`](../docs/M1.md) for how this compiles on the JVM and the
Milestone 1 steps.
