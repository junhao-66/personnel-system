# Frontend build optimization verification

## Result

Sass legacy-JS-API warnings and Vite's default 500 kB chunk warning are resolved. No warnings were silenced and the chunk threshold was not increased.

The previous Element Plus installation registered every component and loaded the complete stylesheet at startup. Components and their CSS are now resolved on demand by `unplugin-vue-components` (locked to 0.27.5). Existing lazy routes automatically separate table/date-picker/dialog code. Programmatic message and message-box styles remain explicitly imported. `ElConfigProvider` retains Chinese locale, and application CSS specificity preserves frosted styles when component CSS loads later.

Sass now uses Vite's `api: 'modern'` interface. No Sass/Vite major-version upgrade was required.

## Size evidence

Sizes below are actual on-disk bytes before HTTP compression. The entry is also the largest JavaScript chunk.

| Asset | Before | After |
| --- | ---: | ---: |
| Entry JavaScript | 1,125,128 | 244,531 |
| Entry CSS | 363,170 | 50,941 |

Entry JS decreased by approximately 78%; entry CSS by approximately 86%. These are entry reductions, not the total bytes of every route. Final assets total 719,445 bytes JavaScript and 195,528 bytes CSS across all chunks.

## Verification

- `npm run build`: passed, including application Vue/TypeScript check; no Sass deprecation or oversized chunk warnings.
- `node node_modules/typescript/bin/tsc --noEmit -p tsconfig.node.json`: passed for Vite configuration.
- Existing `frontend-smoke.cjs`: 23 browser checks passed with mocked APIs.
- Existing `attendance-smoke.cjs`: 14 browser checks passed with mocked APIs.
- Temporary Vite production-preview checks: 6 groups passed, covering lazy routes/table, Chinese pagination/calendar, frosted card styles, dialog/date-picker styles, programmatic confirmation services and login validation. No browser page exceptions. The temporary preview process was closed afterwards.
- The new generated component declarations exposed existing table-slot type boundaries. Callback rows now explicitly use the corresponding domain VO and attendance row keys are strings. No runtime business rules were changed.
- Source/configuration/package diffs reviewed against the pre-change backup; no backend/database changes. The dependency lockfile is updated and generated component declarations are kept for reproducible template checking.

## Remaining limits

Browser coverage is Chrome with mocked APIs; this does not replace full server integration or physical-device checks. No lint task exists.

`npm audit` still reports two pre-existing development-tool entries: Vite (high) and esbuild (moderate). Their locked versions remain Vite 5.4.21 and esbuild 0.21.5, identical to the pre-change lockfile. The audit proposes a breaking Vite upgrade; it was not applied as part of the Sass/chunk optimization. This result does not claim a clean dependency security audit.

This report supersedes the historical Sass/large-chunk warning notes in earlier employee/attendance/frontend validation reports.
