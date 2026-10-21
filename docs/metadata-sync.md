# Metadata and cover synchronization

The desktop Companion service owns SQLite access. Android uses its paired Bearer
credential over the existing Companion connection; database files are not exposed.
Both applications need a build containing the presentation API below. Older
desktops leave phone edits pending instead of discarding them.

## Stored data

- A presentation uses `hitomi:<id>` or `sync:<UUID>` as its stable resource key.
- Series overrides use the reserved logical key `sync:series_<SHA-256 of exact
  series name>`, within the same desktop profile scope. The existing API stores
  this document without changing any member Book row. Metadata title is a display
  override; source-series fields and user episode membership remain separate.
  Rename copies overrides to the new name without overwriting existing destination
  edits. Names remain logical identities, so a reused series name retains its
  previous override. An unresolved metadata conflict must be resolved before a
  rename/merge. The original-name snapshot is retained for recovery.
- Cover-only series changes use `sync:series_cover_<same hash>` separately. A
  cover change therefore keeps aggregate episode metadata live, including newly
  added members, and does not create a metadata override. Each document has its
  own version/conflict; cover conflicts have explicit keep-phone/use-PC actions.
  The read-only aggregate has no 300-entry truncation. Metadata editing respects
  the existing 300 entries/200 characters per entry API limits and explains invalid
  input instead of constructing an invalid presentation for a large aggregate.
- Series settings reuse the work metadata editor, conflict handling and cover
  import. The internal picker offers member artwork covers by episode title;
  external images use the same system document picker and permanent cover copy.
- Editable fields are title, artists, groups, source series/parodies, characters,
  tags, type, language and cover. Source series are separate from user episode
  groups. Hitomi upload dates are retained; filesystem times are not upload dates.
- Android stores overrides and pending mutations in `library_identity.db`, scoped
  to the selected desktop. Internal pages and external images become permanent,
  content-addressed covers under `files/presentation-covers/`.
- Android downsamples imported covers to at most 512×1024 and applies EXIF
  orientation. Covers are validated images with a SHA-256 identity, bounded bytes
  and pixel count. They are user data, separate from evictable thumbnail caches.
- Original media, archives and `info.txt` remain unchanged. Desktop rescans and
  thumbnail jobs preserve edited metadata and selected covers.
- Webtoon classification is a separate flag. Its localized display tag does not
  replace or rewrite user tags.

## Companion API

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/v1/library/presentations?cursor=0&limit=200` | Ordered changes after a revision |
| POST | `/v1/library/presentations` | `{mutationId, baseVersion, value}` |
| PUT / GET | `/v1/library/presentation-covers/<sha256>` | Verified binary cover |

POST returns `applied`, `already_applied` or `conflict`, with the authoritative
presentation. The desktop stores mutation receipts per paired device. JSON is
limited to 2 MiB on this endpoint; binary covers are limited to 5 MiB. Other JSON
routes retain their smaller limits. Desktop status advertises `presentationSync`.

Android retries pending mutations before pulling changes, so losing a successful
acknowledgement does not turn its own update into a false conflict. Late responses
cannot erase newer phone edits. A real version conflict keeps both snapshots and
offers an explicit keep-phone/use-PC choice. Legacy status/title updates wait for
dirty metadata to resolve rather than bypassing its conflict check.

Changing or removing a desktop cancels its active scan, clears its displayed
snapshot and reloads the selected scope. Late list and reader-preparation results
cannot repopulate the previous scope. Download destination is independent:
device requests never enter the PC queue; offline desktop requests stay durable.
