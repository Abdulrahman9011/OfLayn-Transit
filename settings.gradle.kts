rootProject.name = "OfLayn"
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
include(":core:model", ":domain:fare", ":domain:sources", ":domain:transit", ":domain:ai", ":domain:voice", ":app")
