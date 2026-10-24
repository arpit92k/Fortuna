plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)

    // All code lives in the common source set. The three targets exist so that
    // every change is compiled for Android, for the JVM (where the tests run)
    // and for one native platform (which proves nothing JVM-only has crept in).
    android {
        namespace = "io.github.arpit92k.fortuna.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        // The common tests also run on a phone or emulator, so the cryptography
        // is proved on the lowest supported Android version. They run from the
        // Device tests workflow, started by hand, not on every pull request.
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    jvm()
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: dates are part of the core's public types.
            api(libs.kotlinx.datetime)
            // api as well: the store exposes the dataset as a StateFlow, and
            // the Private files port is implemented on an Okio FileSystem.
            api(libs.kotlinx.coroutines.core)
            api(libs.okio)
            // Only the crypto package may use these; a test checks that.
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.okio.fakefilesystem)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.kotlin.test.junit)
        }
    }
}
