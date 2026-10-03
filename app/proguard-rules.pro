# LiteRT-LM and MapLibre use JNI; keep their classes.
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
