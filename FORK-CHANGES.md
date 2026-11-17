# Nuvio Arc+ — changes on top of Nuvio Arc

Nuvio Arc+ is a personal build of [Nuvio Arc](https://github.com/ysosrs123/NuvioTV-Fork) by ysosrs123
(itself built on Nuvio by Tapframe and friends). It follows each Arc release and replays the changes below
on top. They live on three branches, each building on the one before:

| Branch | What it holds |
|---|---|
| `my-main` | Features that are ready, plus this build's own identity (name, app id, install script) |
| `my-extras` | Player and details-page changes (OSD, badges, focus, menus) |
| `try-perf` | Performance changes, aimed at weak boxes |

Everything was tested on three TVs: a Box R 4K Plus (armv7, 3 GB, the weak one), a 2017 Shield and a Shield
tube. Unit tests are included where the logic is testable; the commit messages explain each change.

---

## Home and Continue Watching

- **Continue Watching split into Series and Movies rows** (Modern layout), so a film doesn't push the next
  episode of a show out of view.
- **Sort Continue Watching by last aired date**: shows whose newest episode aired most recently come first.
- **Recently Watched row**: the titles you watched recently, newest first, with a window setting (days).
  It follows the Watch Progress source: Nuvio Sync, Trakt (`sync/history`), Simkl, MDBList or WeTrakr.
  Cards get poster, backdrop and logo (so the hero shows), saved between launches.
- **Upcoming row opens the details page** instead of starting playback.
- **Episodes left counts an episode from the day after it airs** when the add-on gives only a date (a plain
  date, or a stamp at midnight UTC or local midnight). Read as a time, it counted tonight's episode from the
  evening before. A real air time still counts from that moment.
- **Drop show** in the Continue Watching long-press menu (shows only): marks the show Dropped on every
  connected tracker — Simkl (moves to Dropped), WeTrakr (Dropped list), Trakt (`POST users/hidden/dropped`)
  and MDBList (`POST /sync/dropped`) — then clears the card as Remove does. History, ratings and the Nuvio
  library are left alone. A note says which trackers took it.

## Details page

- **TMDB reviews** as an alternative to Trakt reviews (setting), with the TMDB logo in the review popup.
- **Resume button shows progress**: a thin bar inside the button in the theme colour; the track goes dark
  while the V2 button is focused so it stays readable.
- **Reset progress** in the long-press menus of the Resume button and of an episode: removes the saved
  position without marking anything watched or unwatched. The player's episode panel shares the menu but
  doesn't opt in.

## Player

- **OSD media line**: a pill at the top left with resolution, HDR and audio (codec, Atmos, channels and the
  route: Passthrough / PCM), using the user's Fusion badge pack. Ready the moment the OSD opens. Arc's own
  player badge row is hidden in favour of it. Setting toggle under Playback → Player interface.
- **Badges from the stream itself, never the release name**: HDR10+ from the decoder's HDR10+ metadata;
  TrueHD Atmos from the major sync; DTS-HD / MA / DTS:X from the DTS extension syncs (Matroska labels all
  DTS "DTS"). Release names list several tracks, so they gave wrong badges.
- **Atmos badge before the codec badge**; mono audio looks up the pack's 1.0 / Mono badge.
- **Badge artwork no longer clipped** to rounded corners (it cut the S off Dolby Digital Plus).
- **Clock pills**: the time (and "ends at") on Home, the details page and the OSD, with setting toggles.
- **Play/Pause focus**: the controls always come back on Play/Pause, also on custom layouts where it
  alternated with More.
- **Hybrid player button style** for custom control layouts, next to Icons and Labels: the left and centre
  groups show labelled buttons, the right group icons. The layout editor's style button cycles through all
  three; saved as style id `hybrid`.

## Settings

- **Settings search finds this build's settings**: Recently Watched (and its window), the clock, the OSD
  line and TMDB reviews.

## Trackers and data

- **Trakt sign-in with a client id alone**: PKCE apps get no client secret, so the secret is optional.
- **MDBList ratings cached for 7 days** (titles with no ratings for 2) instead of 12 hours, which kept a
  free-tier key under its 1,000 requests a day across three TVs. Failed requests (a hit limit) are never
  cached.

## Performance (`try-perf`)

- **Display-size artwork copies**: card art from non-TMDB sources, and TMDB `original` backdrops, are
  decoded once at the size they are shown and kept (JPEG for opaque art). Big decodes on the Box R went from
  450–930 ms to 140–360 ms.
- **Scrapers take turns while a video plays**: a source search keeps going after a stream is picked, and
  slow scrapers ground for minutes during playback on the Box R. While playing, at most 2 run at once; the
  rest wait. Opening the sources panel or leaving the player lets them all go.
- **One next-episode lookahead, 4 minutes out**: the binge lookahead fired at 6 minutes, so its 5-minute
  cache entry expired near the end and it scraped twice. At 4 minutes it scrapes once and is still fresh on
  the next-episode card. It also skips the playback scraper queue above.

## Fixed here first, since taken over by Arc

These were dropped from this build when Arc shipped its own fix: the Calendar's followed-titles read off the
main thread (it froze the remote on the Box R), TBA episodes counting as "episodes left", and the Shuffle
button style.

## This build only (not meant for Arc)

- App name **Nuvio Arc+**, a **+** on the launcher icons, TV banners, in-app wordmark and intro lettering.
- App id `com.nuvio.tv.test.jack` (so it installs next to Arc).
- `scripts/install-tvs.ps1`: builds, installs on the TVs over adb and compiles the app fully.
- **Its own updates, no issue reports**: Arc+ checks this repository's releases, never Arc's (each release
  body carries `<!-- nuvio-fork-version-code: N -->`, N = Arc's code × 10 + the Arc+ release number). The
  report endpoint is left blank, so report buttons and prompts stay off.
