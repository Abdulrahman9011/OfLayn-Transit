plugins { alias(libs.plugins.kotlin.jvm); alias(libs.plugins.kotlin.serialization) }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:model"))
    implementation(libs.serialization.json)
    testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
