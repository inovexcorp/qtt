# Web upgrade log

Results of the checks in [web-upgrade-plan.md](../web-upgrade-plan.md), one entry per step. Builds and tests ran on the
development Mac (arm64, Chrome 158). Bundle sizes come from `ng build` (production): "initial" is raw / estimated transfer.

## Summary

| Step | Node | Angular | TS | Specs | Coverage S / B / F / L | Initial bundle | Output | Build warnings | Gate |
|---|---|---|---|---|---|---|---|---|---|
| Baseline (`3bd270b`) | 20.18.1 | 18.2.14 | 5.5.4 | 225 | 46.8 / 31.5 / 47.3 / 46.3 | 5.51 MB / 1.08 MB | 21 MB | 99 CommonJS + 1 budget | see below |

## Baseline (Phase 0, before any change)

- `ng test`: 225 specs, all pass. Six `OntologyService` specs have no expectations.
- `ng build`: `main` 5.41 MB, `styles` 56 kB, `polyfills` 35 kB. 99 "CommonJS or AMD dependencies" warnings (Monaco CSS through
  `style-loader`) and one budget warning (`config-route.component.scss` 19.39 kB against a 15.36 kB warning budget).
  Output 21 MB, of which `assets/monaco` is 14 MB.
- Java: 1210 unit and app tests pass.
