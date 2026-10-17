plugins { kotlin("jvm") version "2.2.20"; application }
repositories { mavenCentral() }
kotlin { jvmToolchain(21) }
dependencies {
    implementation("com.google.firebase:firebase-admin:9.4.2")
    testImplementation(kotlin("test"))
}
application { mainClass.set("RucServerKt") }
tasks.test { useJUnitPlatform() }
