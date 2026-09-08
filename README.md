# Rest Stop Countdown

A Kotlin + Jetpack Compose Android navigation app: plan a route once, optionally layer on rest
areas / fast food / coffee / gas / EV charging along it, and get real turn-by-turn guidance with
an ETA - all tracked purely from on-device GPS after that one setup fetch, no repeated calls to
Google's APIs while driving.

## Architecture

```
app/src/main/java/com/reststop/countdown/
├── data/
│   ├── model/         Plain domain models (GeoPoint, RouteInfo, RouteStep, PointOfInterest, PoiCategory, PlaceSuggestion)
│   ├── remote/         Retrofit services + DTOs for Directions API and Places API (New)
│   └── repository/     TripRepositoryImpl - the only place that makes network calls
├── domain/
│   ├── repository/     TripRepository interface (what the ViewModel depends on)
│   └── util/            PolylineDecoder, RoutePolylineIndex, RouteSampler, HtmlText - all pure, local
├── location/            LocationTracker - FusedLocationProviderClient wrapper (one-shot + live Flow)
├── di/                  AppContainer - minimal hand-rolled DI (no Hilt, fewer moving build parts)
└── ui/
    ├── MainViewModel.kt  Owns TripUiState, drives the whole trip lifecycle
    ├── TripUiState.kt
    ├── theme/
    ├── screens/MapScreen.kt
    └── components/       DestinationInputBar, TurnByTurnBanner, TripSummaryBar, PoiPanel,
                           CategoryChip, SecondaryDestinationBanner, RouteOptionsCard, ...
```

This is MVVM with a Clean-Architecture-style split: `ui` never talks to Retrofit directly, only
to `TripRepository` (an interface) via the `MainViewModel`; `domain/util` has zero Android
framework or network dependencies (aside from `android.location.Location.distanceBetween`,
used purely as a geodesic-distance helper) so the route-math is easy to unit test.

Rest areas are **not** a special case - `PoiCategory.REST_AREA` sits alongside fast food, coffee,
gas, and EV charging as just another checkbox category, using the exact same fetch-once /
nearest-not-yet-passed machinery as the rest. It's kept to a tight 1.5 mi lateral cutoff from the
route (`PoiCategory.maxLateralMeters`) so a rest area several miles off the highway never counts
as "on the way"; other categories allow a bit more since a gas station just off an exit is still
a reasonable stop.

## Cost-optimization design (read this first)

The whole point of the app is: **plan once, track locally, forever** - with two narrow,
driver-initiated exceptions.

1. **Directions API** is called once at `MainViewModel.startTrip()` (route + turn-by-turn steps
   in one response), and again only if the driver deliberately sets or clears a **secondary
   destination** (routing through a chosen POI as a waypoint) - a one-time reroute for a one-time
   decision, not a background poll.
2. **Places API (New) Nearby Search** is called once per category the driver checks (rest areas /
   fast food / coffee / gas / EV charging). Toggling a category off and back on does **not**
   refetch - results are cached in `TripUiState.poiByCategory` for the life of the trip, and are
   locally re-projected (no network call) onto a new route if one is set via a reroute.
   - Because a single Nearby Search circle only covers a limited radius, `RouteSampler` picks a
     handful of evenly spaced points along the route (capped, e.g. 15) and a Nearby Search is
     issued per point, all in parallel, all within that one setup sweep.
3. **Places API (New) Text Search** powers destination search as the user types (debounced) -
   used instead of Autocomplete specifically because it returns each result's `location` directly,
   so results can be pinned on the map without a separate Place Details call per suggestion.
4. **Everything else** - the turn-by-turn banner, ETA, auto-advance, and "upcoming X" hints - is
   computed with `RoutePolylineIndex` (a local projection of GPS points onto the cached polyline)
   and `Location.distanceTo()`/`bearingTo()` on every `FusedLocationProviderClient` update. Zero
   network calls happen in this loop.

## Live tracking, ETA, and turn-by-turn

- `LocationTracker.observeLocationUpdates()` exposes a `Flow<Location>` from
  `FusedLocationProviderClient` (high-accuracy priority, ~3s interval / 15m min displacement -
  tune these in `LocationTracker` for your battery/precision tradeoff).
- On every fix, `MainViewModel.handleLocationUpdate()`:
  1. Projects the fix onto the cached route polyline (`RoutePolylineIndex.project`) to get both
     **progress along the route** and **lateral distance from the route**.
  2. Recomputes the 2 nearest not-yet-passed places per checked category (rest areas included),
     surfaced as floating `CategoryChip`s over the map.
  3. Finds the current position within the selected route's cached turn-by-turn `steps` (parsed
     once from the same Directions API response) and computes distance to the next maneuver -
     this drives the `TurnByTurnBanner`.
  4. Computes remaining distance (`routeTotalDistanceMeters - progress`) and a proportional ETA
     (remaining distance's share of the original Directions duration estimate, added to wall-clock
     time) for the `TripSummaryBar` - an estimate, not live traffic-aware routing.
  5. Resolves a heading for the camera: the GPS fix's own bearing when moving fast enough to
     trust it, otherwise the bearing between the last two fixes, otherwise holds steady.
  6. If a secondary destination is active and the driver's progress has passed it, clears it -
     the route already continues on to the real destination through the same cached steps, this
     just drops the "via" banner.

## Secondary destinations (waypoints)

Tapping a result inside a `CategoryChip`'s expanded list ("Set as stop") calls
`MainViewModel.setSecondaryDestination`, which re-fetches Directions with that place as a
waypoint - Google returns a multi-leg route (leg 0: here → stop, leg 1: stop → real destination),
which `TripRepositoryImpl.fetchRoute` concatenates into one continuous `steps` list and records
where the stop leg ends (`RouteInfo.waypointArrivalDistanceMeters`). The `SecondaryDestinationBanner`
shows while it's active and can be cancelled (rerouting straight back to the original
destination); either way it's a single deliberate Directions call, not a repeating one.

## Driving mode

Once a route is selected, `MapScreen` switches to a tilted (60°), zoomed-in camera that follows
the driver's position and bearing - `MainViewModel` doesn't touch the camera directly, it just
publishes `currentLocation` / `currentBearingDegrees` / `drivingMode`, and the screen's
`LaunchedEffect` re-animates the `CameraPositionState` on each update. A small FAB toggles back to
a flat, north-up overview. If the driver manually drags the map, a gesture-reason check on the
camera state turns off auto-follow until they tap the main "recenter" FAB, which snaps back to
whichever mode (driving or overview) was active.

The `TurnByTurnBanner` above the map shows the upcoming maneuver's icon (mapped from Directions'
`maneuver` field), instruction text (HTML-stripped), and live distance - all recomputed locally
against the steps already fetched in the one Directions call, never a new network request.

## Setup

This app needs **two separate API keys**, because Google's "Android apps" key restriction only
authenticates calls made through Google's own client libraries (the Maps SDK for Android embeds
your app's identity when it fetches tiles). It does **not** authenticate a raw HTTPS call your
own code makes - like this app's Retrofit calls to the Directions and Places REST endpoints,
which arrive at Google looking like an anonymous request with no `Referer` header and get
rejected ("not authorized ... empty referer"). So:

1. Copy `local.properties.template` to `local.properties` and set:
   - `sdk.dir` (Android Studio fills this in automatically on first open)
   - `MAPS_API_KEY` - used only for the Maps SDK for Android (the `<meta-data>` in
     `AndroidManifest.xml`). In the Cloud Console, restrict it:
     - **Application restrictions → Android apps** → package name `com.reststop.countdown` +
       the SHA-1 below.
     - **API restrictions** → Maps SDK for Android only.

     `app/debug.keystore` is committed to the repo (it's a non-secret, dev-only key - never used
     for release signing) and wired into `app/build.gradle.kts` as the signing config for every
     debug build, local or CI, so its SHA-1 never changes:

     ```
     SHA1: E9:4E:92:97:FF:16:3C:DC:28:37:C1:9C:45:C4:7A:95:CC:FF:F8:4B
     ```

     Verify it yourself anytime with:
     ```
     keytool -list -v -keystore app/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
     A release build needs its own separate, private keystore and a different SHA-1 registered
     against the key restriction - never reuse the debug key for release signing.

   - `PLACES_API_KEY` - a **second** key used only for the Directions API, Places API (New), and
     Places Autocomplete (New) calls made directly via Retrofit. Restrict it:
     - **Application restrictions → None** (an Android restriction can't authenticate these raw
       REST calls - see above; IP restriction doesn't work either, since mobile client IPs are
       unpredictable).
     - **API restrictions** → Directions API + Places API (New) only.
     - To bound the risk of this key being extracted from the APK (any client-embedded key can
       be), set a daily quota cap under **APIs & Services → Quotas** for those two APIs.
     - Falls back to `MAPS_API_KEY` if left unset, purely so older single-key setups still
       build - but that fails at runtime if `MAPS_API_KEY` is Android-restricted, so set this
       explicitly.
2. Open the project in Android Studio (Koala+ recommended). It will offer to generate the
   Gradle wrapper jar automatically; alternatively run `gradle wrapper --gradle-version 8.7`
   yourself once you have network access to `services.gradle.org`.
3. Run on a device or emulator with Google Play services and location enabled.

## Continuous Integration (downloadable APK without a local SDK)

`.github/workflows/android-build.yml` builds a debug APK on every push/PR using GitHub-hosted
runners, which have unrestricted network access to Google's Maven repos (unlike this sandbox).
It:

1. Installs JDK 17 and the Android SDK (`android-actions/setup-android`).
2. Writes `local.properties` with `sdk.dir`, `MAPS_API_KEY`, and `PLACES_API_KEY` - each pulled
   from a same-named repository secret if you add one under **Settings → Secrets and variables →
   Actions**; otherwise they fall back to a placeholder, which builds fine but won't actually
   load maps or fetch routes.
3. Runs `gradle assembleDebug` and `gradle testDebugUnitTest` via `gradle/actions/setup-gradle`
   (pinned to Gradle 8.7) - no committed wrapper jar required.
4. Uploads `app-debug.apk` as a workflow artifact.

To get the file: push this branch (or trigger the workflow manually via **Actions → Android
Build → Run workflow**), open the finished run, and download `rest-stop-countdown-debug-apk`
from the **Artifacts** section at the bottom of the run summary page.

## Known limitations / things to verify before shipping

- **This project was authored and reviewed for correctness, but not compiled**, in an
  environment with no Android SDK and no network access to Google's Maven repositories
  (`dl.google.com` was blocked by the sandbox's egress policy). Please run a full
  `./gradlew assembleDebug` locally before relying on it - review the "Setup" and "Places API
  type names" sections if you hit resolution errors.
- **Places API type names** (`rest_stop`, `fast_food_restaurant`, `coffee_shop`, `gas_station`,
  `electric_vehicle_charging_station` in `PoiCategory.kt`) come from Google's Places API (New)
  "Table A" type list, which Google updates periodically - double check against the current
  [Place Types reference](https://developers.google.com/maps/documentation/places/web-service/place-types)
  if a category returns no results.
- **Rest area coverage**: Nearby Search relies on Google's own place data for `rest_stop`,
  which is inconsistent in some regions/countries. If results look sparse, consider adding a
  fallback text-search query (e.g. `"rest area" OR "service plaza"`) via Places Text Search
  (New) for the same sample points.
- **No persistence**: everything is in-memory per the cost/complexity tradeoff in the spec.
  Killing the app mid-trip loses the cached route/rest-stop list and needs a fresh
  `startTrip()` call (one more Directions + Places sweep).
- **No background tracking**: location updates only run while the app is foregrounded (via
  `viewModelScope`). Turning this into a true background/foreground-service tracker would need
  a `ForegroundService` + a notification, which was left out to keep the sample focused on the
  MVVM/data-flow architecture requested.
- Unit tests only cover `PolylineDecoder` (pure math). `RoutePolylineIndex`/`RouteSampler` use
  `android.location.Location.distanceBetween`, which needs Robolectric or an instrumented test
  to exercise outside of a real device/emulator - not included here to keep the dependency
  surface minimal.
