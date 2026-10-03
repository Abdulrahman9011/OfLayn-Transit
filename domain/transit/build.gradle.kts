plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:model"))
    implementation(libs.serialization.json)
    testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
