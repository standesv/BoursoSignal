// Projet Gradle JVM autonome, uniquement destiné à COMPILER et EXÉCUTER le moteur
// de signaux (Kotlin pur de l'app) et ses tests, sans le SDK Android.
// Il réutilise directement les sources du module app : aucune duplication de code.
plugins {
    kotlin("jvm") version "2.0.21"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

sourceSets {
    main {
        kotlin.srcDir("../app/src/main/java/bzh/dargio/boursosignal/domain/signals")
    }
    test {
        kotlin.srcDir("../app/src/test/java/bzh/dargio/boursosignal/domain/signals")
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
