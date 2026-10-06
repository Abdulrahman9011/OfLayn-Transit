# Keep the offline core (parsed by reflection-free JSON, but keep names readable in crash reports).
-keep class com.oflayn.domain.trips.** { *; }
-dontwarn org.json.**
