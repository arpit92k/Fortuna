plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
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
    }
    jvm()
    linuxX64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
