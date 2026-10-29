import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) FileInputStream(keystorePropertiesFile).use { load(it) }
}
// Récupère un paramètre de signature depuis keystore.properties, puis les
// variables d'environnement (CI). Une valeur vide (secret CI non défini →
// chaîne vide) est traitée comme absente et retombe sur `default`.
fun signingProp(name: String, default: String? = null): String? =
    (keystoreProperties.getProperty(name) ?: System.getenv(name))?.takeIf { it.isNotBlank() } ?: default

// Keystore de signature STABLE : versionné dans le dépôt (application personnelle)
// pour que chaque build (local et CI) soit signé avec la MÊME clé. C'est ce qui
// permet d'installer une mise à jour par-dessus la version précédente sans
// désinstaller (et donc sans perdre les données). Surchargé par
// keystore.properties en local ou par des secrets CI (RELEASE_STORE_FILE, …).
val committedKeystore = rootProject.file("keystore/boursosignal.jks")
val releaseStoreFile: String? = signingProp(
    "RELEASE_STORE_FILE",
    if (committedKeystore.exists()) committedKeystore.absolutePath else null,
)
val releaseStorePassword: String = signingProp("RELEASE_STORE_PASSWORD", "boursosignal")!!
val releaseKeyAlias: String = signingProp("RELEASE_KEY_ALIAS", "boursosignal")!!
val releaseKeyPassword: String = signingProp("RELEASE_KEY_PASSWORD", "boursosignal")!!

// versionCode monotone (numéro de run CI) : chaque build est une vraie mise à
// jour incrémentale ; 1 en build local.
val ciBuild: Int = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toIntOrNull() ?: 1

android {
    namespace = "bzh.dargio.boursosignal"
    compileSdk = 35

    defaultConfig {
        applicationId = "bzh.dargio.boursosignal"
        minSdk = 29
        targetSdk = 35
        versionCode = ciBuild
        versionName = "0.1.$ciBuild"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null) {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        // Debug ET release signés avec la même clé stable : quel que soit l'APK
        // installé, les mises à jour s'installent sans désinstallation.
        debug {
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
