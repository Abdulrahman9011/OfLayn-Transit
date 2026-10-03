plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:model"))
    api(project(":domain:fare"))
    api(project(":domain:transit"))
    api(project(":domain:sources"))
    api(libs.coroutines.core)
    implementation(libs.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
}
tasks.test { useJUnitPlatform() }
