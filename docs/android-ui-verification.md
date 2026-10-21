# Android UI verification

## Optimization follow-up — 2026-10-21

All ten audit candidates were addressed in separate commits:

- Programmatic search bindings no longer dispatch input events. Library autocomplete
  tokens are normalized/indexed on Default and reused for an unchanged library.
  Query/profile guards discard stale results. The complete Hitomi search still
  requires submission; gallery filtering retains its debounce.
- The platform editor is created on first expansion and reused until the search
  screen leaves composition. Collapsed editors are invisible, unfocused and excluded
  from accessibility. Focus waits for a frame instead of a fixed 50 ms delay.
  Toolbar animation reads occur in layout; rounded corners remain rounded.
- Tag text measurement keys contain labels, not click callbacks. Actions and
  accessibility still update, and font/width changes still trigger measurement.
- Reconnect requests share a successful in-flight library refresh. Server-change
  requests remain stronger and survive backgrounding; manual refresh and failed-scan
  retries remain available. Profile generation guards remain in place.
- Presentation SQLite queries and list rebuilding run off Main. Publication checks
  revision/profile identity and retries against changed browsing collections.
- Individual reading pages use scalar preference updates over the legacy JSON map.
  Bulk replacements clear those deltas atomically. History uses bounded individual
  records and reuses unchanged encodings; the first save migrates legacy history.
  Original media and info.txt files are not modified.
- Archive indexes persist in a 32 MiB private cache keyed by URI, modification time
  and size. Sidecars and identities are still resolved on each scan. Unknown source
  versions, missing covers and corrupt cache entries cause a rescan. Extracted pages
  use direct name indexes, atomic writes and source-version validation; complete
  valid caches can skip ZIP traversal. Natural-sort regexes are reused.
- Tab previews record a graphics layer only for an explicit capture request, with
  a bounded wait for drawing. Ordinary frames draw directly. The capture test checks
  updated pixels and verifies that ordinary content changes do not save previews.
- History cards share a device-preferred book index. Series cards remember combined
  artists/tags. Matching identity precedence and list order are covered by tests.
- Removed the unused FilterMenu and legacy title formatter. Tests now cover the
  actual localized toolbar formatter. No dependencies were added.

Verification: full unit suite 217 tests, zero failures/errors, two opt-in live-service
tests skipped; debug/uitest APK builds and Debug Lint passed with no Error/Fatal issues.
The combined device regression run passed 42 tests. The final background-request
follow-up separately passed refresh/profile-switch/real-activity lock checks (4 tests).
One earlier combined lock test timed out during the second unlock; its isolated
rerun, repeated seven-test reader suite, combined run and final follow-up passed.
The initial UI run with the system keyguard showing was invalid and is excluded.

SM-N986N, debug builds, real-clock synthetic cached-content workloads:

| Search expansion including IME, five open/close pairs | Before editor reuse | Final quiet run |
| --- | ---: | ---: |
| Frames | 242 | 338 |
| Median frame | 10.69 ms | 10.97 ms |
| 95th percentile | 139.28 ms | 36.80 ms |
| Frames above 16.67 ms | 61 (25.2%) | 73 (21.6%) |

The search fixture includes cached browsing cards and a history panel, with no
network. The baseline already includes the autocomplete/tag fixes; it isolates
the subsequent editor/toolbar change. No sampling profiler was active. A separate
post-build run used the existing 600-book local-image scroll fixture: 578 frames,
median 10.24 ms, 95th percentile 23.54 ms, 48 frames above 16.67 ms (8.3%) and nine
scroll-state writes. An integration run overlapped host build work and is excluded
from that final scroll figure. These are diagnostic debug measurements, not a
release FPS guarantee. Network/SAF latency and fresh-image decoding can still stall.

All instrumentation uses `.uitest`; the actual `.debug` app is updated in place.
Production installation, actual debug credentials and source media are preserved.

## Earlier scroll investigation — 2026-10-19

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

## Additional implementation verification — 2026-10-20

- Android: 208 unit tests, zero failures (two opt-in live-service tests skipped).
  Debug Lint reports zero errors; debug and release APK assembly passed.
- Isolated SM-N986N device suite: 43 tests passed, including real biometric prompt
  cancellation/rotation, reader/preview restoration, scalable History labels,
  shared search editor/caret/history, debounced gallery queries, common view sizes,
  transactional series editing/aggregate previews, failed-image recovery,
  offline download destinations/settings shortcut and delayed-PC profile switching.
- Desktop: 674 tests passed during implementation; final tsc/vue-tsc and actual
  main/renderer compilation passed. No installed desktop database was used.
- The separate 600-book cached-image scroll run recorded 551 frames, median
  10.81ms, p95 30.80ms, 69 frames above 16.67ms (12.5%) and 10 state writes.
  The full device suite recorded 571 frames, median 10.77ms, p95 27.72ms,
  65 frames above 16.67ms (11.4%) and 11 writes. These are diagnostic debug-build
  measurements, not a guarantee for every real library or network connection.
- Track swipes now remain content gestures; track jumping requires a long press.
  Thumb dragging is immediate. Fixed haptic uses 12ms and amplitude 26/255 when
  amplitude control is supported, with a weak system fallback and touch-feedback
  disable respected. Hardware amplitude is not perceived-strength calibration.
- A synthetic HTTP fixture failed four times, exposed manual retry after three
  automatic retries, then returned a valid image. The original user's individual
  network-image failure was not captured, so no specific CDN status is claimed.
- APK labels were inspected in English/Korean/Japanese/Chinese: Doujin Menu debug,
  package com.doujinmenu.android.debug, version 1.0.0-debug (12).
- v0.10.0 notes are preserved verbatim; new notes are under v1.0.0.
- The final `.debug` APK was installed on SM-N986N and launched successfully.
  The device had only the isolated test packages before this install. No debug
  or release app was uninstalled or cleared during this verification. The live
  fullscreen search editor and keyboard were inspected after installation.
- A final regression test confirms that an older metadata snapshot without an
  upload date preserves the date newly fetched from the source gallery.

Metadata/cover protocol and storage are documented in [metadata-sync.md](metadata-sync.md).
Real successful biometric scans and transfer against the user's installed PC build
were not automated. The PC requires the updated Companion API build.
# Search expansion and jank follow-up (2026-10-20)

- Search expands only through its rules/history/suggestions panel. The panel wraps
  short content, caps at 260 dp and at half the usable space below the input, and
  leaves the browsing results and main navigation visible with the keyboard open.
  Touching or scrolling the results collapses it without submitting the draft.
- Startup sampling identified library refresh work on the main thread, including
  repeated whole-collection copies for individual remote book states. Refresh and
  state-sync merging now mutate private working copies once; refresh preparation
  runs on Default. Preference snapshots and the presentation/profile identity are
  checked before publication so concurrent edits and PC changes cannot be replaced
  by an older result.
- Window/security/system-bar effects run only when their inputs change. Root
  appearance reads use derived state, and theme colors are remembered. App locking
  still removes the protected app content; it does not keep an interactive hidden
  screen behind the lock screen.
- Search's downloaded-book index is built in one pass instead of scanning the
  library once per gallery. Local-copy preference and hidden-copy rules remain.
- Steady scrolling sampling identified packed card tags as the largest app-level
  UI cost. Packed tags reuse measured text and draw pills directly instead of
  creating a Surface/Text subcomposition for each tag during measurement. Each
  visible tag retains its own accessibility text and optional click action.

Measurements on SM-N986N, debug builds:

| Controlled local-image scroll workload | Before | After, two runs |
| --- | --- | --- |
| Frames | 539 | 574 / 592 |
| Median frame, ms | 10.91 | 10.77 / 10.07 |
| 95th percentile, ms | 34.77 | 31.15 / 22.98 |
| Frames above 16.67 ms | 76 (14.1%) | 64 (11.1%) / 49 (8.3%) |
| Scroll-state writes | 9 | 9 / 9 |

The scroll workload uses 600 synthetic books, cached local images, two columns,
light mode, 16 sp, native timed input and no network. Long frames remain; these
debug measurements do not establish consistently smooth release-frame timing.
Separate method-sampling captures on the installed debug app showed aggregate
main-thread CPU under `refreshLibrary` fall from about 1783 ms to 143 ms. Sampling
and ART warm-up affect those captures; they are not app-launch-duration figures.

Device regression coverage includes real-activity PIN unlock/relock, FLAG_SECURE,
reader position, profile-switch races, presentation sync, draft preservation,
search expansion, tag clicks, overflow, selection and fonts through 28 sp. Lock
tests use the isolated `.uitest` package and synthetic credentials; the installed
debug application's credentials are unchanged.
