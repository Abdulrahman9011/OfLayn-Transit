# Source Matrix (research date 2026-10-03, via web search/fetch only; nothing downloaded or executed)

| Feature | Source | Official? | Available? | Live? | API key? | Backend? | License/Terms | Confidence | Notes |
|---|---|---|---|---|---|---|---|---|---|
| Static schedule (GTFS) | BURULAŞ / BursaKart / BBB | — | **Not found** | No | — | — | — | Medium | Searched BURULAŞ, Açık Yeşil (acikyesil.bursa.bel.tr), GitHub. One forum post says BURULAŞ has no Google Transit feed. Not proof of absence. |
| Stops & routes | OpenStreetMap via Overpass | No (community) | Yes (at runtime) | No | No | No | ODbL, attribution required | Medium | Used as DERIVED / UNOFFICIAL. Overpass endpoint usage policy NOT verified here. |
| Vehicle positions | `ulasimapi.burulas.com.tr/api/NetworkInfo/VehiclesPosition?code=93` | Claimed by a third-party GitHub repo as BURULAŞ "Open Data Portal"; **not verified** | Unknown | Unknown | Unknown | Unknown | Unknown | Low | Status: Requires Further Verification. Not wired. Response shape unknown; run `tools/probe_ulasimapi.sh`. |
| Service alerts | none found | — | No | — | — | — | — | — | Alerts tool returns UNAVAILABLE. |
| BursaKart balance/transactions | none found | — | No | — | — | — | — | — | Manual balance only, labelled MANUAL. No NFC/DESFire reading. |
| Fares | Signed fare manifest in app | See manifest `source` | Yes | — | — | — | per manifest | per manifest | Existing module. |
| Basemap | MapLibre + configurable style URL (default OpenFreeMap) | No | Unverified | — | No | No | OSM attribution shown | Low | Default style URL is unverified; change in Settings. |
| Offline LLM | Gemma 4 E2B `.litertlm` (litert-community on Hugging Face), LiteRT-LM Kotlin API | Google | Yes per model card/docs | — | No | No | Apache-2.0 (Gemma 4) | Medium-High | Download is user-initiated. Exact Maven version not pinned (`latest.release`). |
| Online LLM | Gemini via own proxy (`server/gemini-proxy-worker.js`) | Google | Model id `gemini-3.7-flash` NOT verified | — | Yes (server-side only) | Yes | Google terms | Low | Model id comes from config. |
