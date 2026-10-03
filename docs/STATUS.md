# OfLayn — honest status (static review only)

NOT verified: Gradle sync, compile, unit tests, device run. The sandbox has no Gradle, Android SDK or app network access.
Verified only by reading: brace balance of all 44 Kotlin files, no leftover references, web research for sources/APIs.

## What is wired end to end (written, unexecuted)
- Data: Overpass/OSM import (user-initiated) -> validate -> Room (atomic replace) -> Repository -> UI/Map/AI tools. Labelled DERIVED / UNOFFICIAL with source, URL, fetchedAt, freshness (RECENT/CACHED/STALE).
- Screens: Home, Map (MapLibre), Transit (Stops, Routes, Plan, Fare), Card, AI, Settings; real navigation (Home/Stops/Routes -> Map; Stops -> Planner).
- AI: Router (Gemini proxy -> Gemma E2B -> Local Data Assistant), 17 tools, grounded LLMs (tools first, LLM only rephrases), rate limit, no-crash fallbacks.
- Gemma 4 E2B: LiteRT-LM engine, device check (RAM/storage/ABI/SDK), user-initiated download with progress/cancel/delete, benchmark that can disable it, unload when leaving AI screen. E4B is NOT wired anywhere.
- Gemini: client + `server/gemini-proxy-worker.js` (key server-side).
- Voice: STT/TTS adapter + tested state machine; Localization: AR, TR, EN, DE, RU for UI strings in screens.
- Room also stores favorites and recent trips.

## Not implemented / unavailable (reasons)
| Item | Reason |
|---|---|
| Official or unofficial GTFS | None found (see docs/SOURCE_MATRIX.md). No GTFS parser written (no feed to parse; add when one exists). |
| Timetables / ETA | OSM has no schedules. Planner gives ESTIMATED moving time; waiting time shown as UNKNOWN. |
| Live vehicles | Candidate endpoint is unverified; response shape unknown. LIVE VEHICLES = UNAVAILABLE. |
| Service alerts | No source found. |
| BursaKart balance/transactions | No official API. Manual balance (MANUAL) only. |
| Offline basemap download | Not implemented; MapLibre only caches viewed tiles. Stops/routes are offline from Room. |
| Local RAG / EmbeddingGemma | Not implemented; search is normalized substring over Room data. |
| Native Gemma function calling | Not used; tools run via the app's orchestrator, LLM rephrases tool output. |
| AICore provider | Not implemented. |
| Turn-by-turn Navigation screen | Not implemented. |
| E4B | Disabled by design (needs proven benchmark). |

## Unverified assumptions (check on first build)
- Dependency versions (Kotlin 2.0.21, AGP 8.7.2, KSP 2.0.21-1.0.28, Room 2.6.1, MapLibre 11.11.0, play-services-location 21.3.0) and `litertlm-android:latest.release` — pin after first sync. minSdk 26 may be too low for LiteRT-LM.
- LiteRT-LM API calls follow the official Kotlin guide (Engine/EngineConfig/Backend.CPU/createConversation/sendMessage) but were not compiled.
- MapLibre/GeoJSON API usage written from memory + docs snippets.
- `gradlew` is a custom launcher that downloads gradle-wrapper.jar from github.com/gradle/gradle (tag v8.9.0) — URL unverified. Android Studio works from gradle-wrapper.properties.
- Default map style URL, Overpass endpoint policy, Hugging Face download access (no login) are unverified.
- Gemini model id `gemini-3.7-flash` unverified.
- Infinix X6833B specs unknown to me: thresholds in GemmaManager are design values; the on-device benchmark is the real gate.
