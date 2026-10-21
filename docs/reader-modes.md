# Reader modes

The app offers Auto, Page and Webtoon in reader settings. A choice made inside a
reader is saved for that work and takes priority over the app setting. “Use app
reading mode” removes that override. Online and saved copies share the override
when their gallery ID matches.

Auto samples up to five body images: the first three body pages, middle and last.
The cover is excluded. Each probe reads at most 64 KiB of an image header; it never
decodes a full bitmap. At least three valid dimensions are required. Webtoon is
selected when the median height/width is at least 2.5 and at least 75% of samples
have height/width at least 2.2. Missing or inconclusive information selects Page.
These are conservative image geometry heuristics, not genre classification.

Webtoon fits images to the screen width and scrolls continuously. Visible image
regions are decoded in tiles, bounded to 1600 by 2048 decoded pixels. Image files
are streamed into private app cache, capped at 256 MiB per file and 512 MiB total.
Requests keep the original reader authentication headers; Authorization is removed
on a redirect to another origin. HTTPS cannot redirect to HTTP.

Tab state stores a page index and, in Webtoon, a source-image vertical pixel offset.
Using source coordinates keeps the position stable when the viewport width changes.
The existing PC progress contract continues to synchronize the page index.
Opening a fresh reader from recent history or Continue also resumes at the saved
page; the internal image offset currently belongs to an existing tab's reader entry.

Webtoon has a light scrollbar on the dark reader background, independent of the
app theme. Drag the thumb directly, or long-press the track before dragging.
Scrollbar movement uses the same page/offset saving as touch scrolling.

Continuing to the next episode replaces the current reader history entry and
starts at page one. When reading from a series, Back returns to its detail view
without revisiting earlier episode readers or intermediate episode details.
