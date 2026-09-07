# Rest Stop Countdown

A Kotlin + Jetpack Compose Android app that plans a route once, finds rest areas (and
optionally fast food / coffee / gas / EV charging) along it, and then tracks progress toward
the next rest stop purely from on-device GPS - no repeated calls to Google's APIs while driving.

## Architecture

```
app/src/main/java/com/reststop/countdown/
├── data/
│   ├── model/         Plain domain models (GeoPoint, RouteInfo, RestStop, PointOfInterest, PoiCategory)
│   ├── remote/         Retrofit services + DTOs for Directions API and Places API (New)
│   └── repository/     TripRepositoryImpl - the only place that makes network calls
├── domain/
│   ├── repository/     TripRepository interface (what the ViewModel depends on)
│   └── util/            PolylineDecoder, RoutePolylineIndex, RouteSampler - all pure, local math
├── location/            LocationTracker - FusedLocationProviderClient wrapper (one-shot + live Flow)
├── di/                  AppContainer - minimal hand-rolled DI (no Hilt, fewer moving build parts)
└── ui/
    ├── MainViewModel.kt  Owns TripUiState, drives the whole trip lifecycle
    ├── TripUiState.kt
    ├── theme/
    ├── screens/MapScreen.kt
    └── components/       DestinationInputBar, NextRestStopCard, PoiPanel, PermissionRequestScreen
```

This is MVVM with a Clean-Architecture-style split: `ui` never talks to Retrofit directly, only
to `TripRepository` (an interface) via the `MainViewModel`; `domain/util` has zero Android
framework or network dependencies (aside from `android.location.Location.distanceBetween`,
used purely as a geodesic-distance helper) so the route-math is easy to unit test.

## Cost-optimization design (read this first)

The whole point of the app is: **plan once, track locally, forever.**

1. **Directions API** is called exactly once, at `MainViewModel.startTrip()`, to get the route
   polyline from the user's current GPS fix to the destination.
2. **Places API (New) Nearby Search** is called once per "sweep": once for rest stops right
   after the route arrives, and once more *only if* the user checks a new POI category
   (fast food / coffee / gas / EV charging). Toggling a category off and back on does **not**
   refetch - results are cached in `TripUiState.poiByCategory` for the life of the trip.
   - Because a single Nearby Search circle only covers a limited radius, `RouteSampler` picks a
     handful of evenly spaced points along the route (capped, e.g. 15) and a Nearby Search is
     issued per point, all in parallel, all within that one setup sweep. This is still "one
     fetch at trip start," just spread across a few parallel requests instead of one - the
     alternative (a single call) isn't possible with a fixed-radius Places search.
3. **Everything after that** - the live countdown, auto-advance, and "upcoming X" hints - is
   computed with `RoutePolylineIndex` (a local projection of GPS points onto the cached
   polyline) and `Location.distanceTo()` on every `FusedLocationProviderClient` update. Zero
   network calls happen in this loop.

## Live tracking & auto-advance

- `LocationTracker.observeLocationUpdates()` exposes a `Flow<Location>` from
  `FusedLocationProviderClient` (high-accuracy priority, ~3s interval / 15m min displacement -
  tune these in `LocationTracker` for your battery/precision tradeoff).
- On every fix, `MainViewModel.handleLocationUpdate()`:
  1. Projects the fix onto the cached route polyline (`RoutePolylineIndex.project`) to get
     both **progress along the route** and **lateral distance from the route**.
  2. Computes straight-line distance to the head of the rest-stop queue with
     `Location.distanceTo()`.
  3. Auto-advances (drops the head of the queue) if the driver is within ~250m of it **or**
     their route progress has passed the stop's position - whichever happens first. This
     covers both "pulled into the rest stop" and "blew past it without stopping."
  4. Recomputes the nearest *upcoming* place per checked POI category the same way, so e.g. a
     "Next Fast Food: McDonald's - 4.2 mi" hint can show alongside the rest stop countdown -
     a loose proximity hint, not turn-by-turn guidance, and it never touches the map's camera
     or route.

## Setup

1. Copy `local.properties.template` to `local.properties` and set:
   - `sdk.dir` (Android Studio fills this in automatically on first open)
   - `MAPS_API_KEY` - a Google Cloud API key with **Maps SDK for Android**, **Directions API**,
     and **Places API (New)** enabled. Restrict it (package name + SHA-1, and API restrictions)
     before shipping - see the Google Cloud Console credentials page.
2. Open the project in Android Studio (Koala+ recommended). It will offer to generate the
   Gradle wrapper jar automatically; alternatively run `gradle wrapper --gradle-version 8.7`
   yourself once you have network access to `services.gradle.org`.
3. Run on a device or emulator with Google Play services and location enabled.

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
