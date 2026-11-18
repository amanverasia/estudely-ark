import java.util.Properties

plugins { id("com.android.application") }

// Release signing: a properties file kept by the maintainer, never in the repo.
//   storeFile=/path/to/ark-release.jks  storePassword=...  keyAlias=...  keyPassword=...
// Path: -ParkSigning=FILE, or $ARK_SIGNING, or ~/.estudely/ark-release.properties.
// Without it, assembleRelease produces unsigned APKs.
val signingFile = file(
    (findProperty("arkSigning") as String?) ?: System.getenv("ARK_SIGNING")
        ?: "${System.getProperty("user.home")}/.estudely/ark-release.properties"
)
val signingProps = Properties().apply { if (signingFile.isFile) signingFile.inputStream().use { load(it) } }
val building = gradle.startParameter.taskNames.joinToString(" ")
val releaseBuild = building.contains("Release", ignoreCase = true)

// Version codes: base * 10 + ABI (1 armeabi-v7a, 2 arm64-v8a, 3 x86_64; 0 universal and debug).
val baseVersionCode = 211
val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86_64" to 3)

android {
    namespace = "cc.esdy.ark"
    compileSdk = 36
    defaultConfig {
        applicationId = "cc.esdy.ark"
        minSdk = 26
        targetSdk = 36
        versionCode = baseVersionCode * 10
        versionName = "2.1.1"
    }
    signingConfigs {
        if (signingProps.getProperty("storeFile") != null) create("release") {
            storeFile = file(signingProps.getProperty("storeFile"))
            storePassword = signingProps.getProperty("storePassword")
            keyAlias = signingProps.getProperty("keyAlias")
            keyPassword = signingProps.getProperty("keyPassword")
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") } // phones and the x86_64 emulator; quicker builds
        }
        release {
            isMinifyEnabled = false // libkiwix and libtorrent4j call into Java over JNI; no R8 keep rules yet
            signingConfig = signingConfigs.findByName("release")
        }
    }
    splits {
        abi {
            isEnable = releaseBuild
            reset()
            include(*abiCodes.keys.toTypedArray())
            isUniversalApk = true // one APK for every phone, to pass around offline
        }
    }
    // libkiwix also ships 32-bit x86; libtorrent4j doesn't, so that ABI could never run. Leave it out.
    packaging { jniLibs { excludes += "lib/x86/**" } }
    // Calls newer than minSdk crashed 2.0/2.0.1 at launch on Android 8-12. Make that a release-build error.
    lint { fatal += "NewApi"; checkReleaseBuilds = true }
    buildFeatures { buildConfig = true }
    androidResources { noCompress += listOf("torrent") }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { out ->
            val abi = out.filters.find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }?.identifier
            out.versionCode.set(baseVersionCode * 10 + (abiCodes[abi] ?: 0))
        }
    }
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("org.kiwix:libkiwix:2.7.0")
    implementation("org.libtorrent4j:libtorrent4j:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:2.1.0-39")
    implementation("org.libtorrent4j:libtorrent4j-android-x86_64:2.1.0-39")
    testImplementation("junit:junit:4.13.2")
}
