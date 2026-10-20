# Android UI verification — 2026-10-19

## Scroll investigation

Code paths showed three avoidable costs:

- Gallery scroll offsets updated the full UI/workspace state on every movement.
  Search and gallery now save settled positions and save again when leaving.
- The scrollbar waited for a long press even on its thumb and repeatedly replaced
  scroll jobs. The thumb now starts immediately; a conflated target channel keeps
  one scroll worker. Track long press remains available.
- Cards composed tags that could never fit. The shared card now measures only
  enough tags to fill its fixed rows, reserving an ellipsis for overflow.

Opening a PC book also replaced its cached cover URL with a full page URL. It now
keeps the cover URL, avoiding accidental original-image requests during browsing.

### Controlled device comparison

Device: Samsung SM-N986N, Android 13. Both apps used debug builds, light mode,
16sp base text, two columns, 600 synthetic books with 24 tags each and the same
local 512×712 PNG cover. One warm-up swipe preceded eight 400ms swipes. Native
touch events used 16ms wall-clock intervals and the real Choreographer, without
the Compose test clock. No network or archive extraction was involved.

The baseline used source commit `8ea14ab` in a separate `.perfbaseline` package;
the updated app used `1852fba` production code in `.uitest`. The fixture callback
copied workspace state in both versions, without simulating preference-file I/O.

| Measurement | Before | After |
| --- | ---: | ---: |
| Recorded frames | 445 | 555 |
| Frame duration, median | 24.93ms | 11.10ms |
| Frame duration, 95th percentile | 48.73ms | 33.75ms |
| Frames above 16.67ms | 369 (82.9%) | 76 (13.7%) |
| Scroll-position state callbacks | 395 | 10 |

This isolates a UI/state contribution to stutter even with cached local images.
It is one diagnostic run per version, not a release FPS guarantee. Initial remote
loading, real archive extraction and PC latency were not separately benchmarked;
slow frames still exist. The shared card and scrollbar changes also apply to
search, but this frame comparison specifically exercised the library screen.

`LibraryScrollBenchmarkTest` reproduces the updated workload and writes
`files/library-scroll-benchmark.json` in the isolated target app.

## Functional checks

- Unit suite: 197 tests, zero failures; two opt-in live-service tests skipped.
- Debug Lint and APK assembly passed.
- SM-N986N instrumentation checks cover immediate list/grid thumb dragging,
  track long press, reverse scrolling, toolbar collapse/reappearance/edit pinning,
  narrow toolbar layout, 2/3/4-column cards at 16/18/20/28sp, uniform list heights,
  hidden-tag composition and extreme custom card/text contrast.
- Reader checks cover entry at page 4, reading to page 50, removal/recreation of
  the reader during lock, preview anchor restoration after a header-height change,
  bounded long-image decoding and webtoon internal-offset restoration.
- Image transport checks cover bounded Range probes, dropping Authorization on
  cross-origin redirects, and persistent per-work reading-mode overrides.
- Lock checks cover persisted attempt limits, cancellation and stale callbacks,
  background invalidation, successful mocked biometric callbacks and credential
  authorization for changing settings. The real OS prompt also survived activity
  recreation; cancelling returned to app credential authentication without an
  automatic prompt loop. No repeated failed scans were forced on the device.

Real successful fingerprint/face recognition was not automated. Temporary and
permanent OS lockout errors are simulated, with app credential fallback tested.

## Test isolation

An initial Gradle device-test run removed the installed debug target and its app
settings. The debug app was reinstalled, but its private data was not recovered.
Subsequent device tests use dedicated `.uitest` / `.perfbaseline` application IDs.
See [testing.md](testing.md) for commands that avoid targeting installed user apps.
