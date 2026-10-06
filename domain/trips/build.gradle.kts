plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.coroutines-core)
    testImplementation(libs.coroutines-test)
}

kotlin { jvmToolchain(17) }

tasks.test { useJUnitPlatform() }
