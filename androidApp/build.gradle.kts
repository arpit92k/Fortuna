import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
}

// The upload key's location and password, kept out of the repository. Without this
// file a release build is left unsigned, and Play will not accept it.
val uploadKey = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

android {
    namespace = "io.github.arpit92k.fortuna"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.arpits.fortuna"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // The Release workflow sets both for each build Play receives; Play refuses a version code it has seen.
        versionCode = (findProperty("fortuna.versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("fortuna.versionName") as String?) ?: "0.1.0"

        // Device tests (androidTest) run on a phone or emulator: see .github/workflows/device-tests.yml.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (uploadKey != null) {
            create("upload") {
                storeFile = file(uploadKey.getProperty("storeFile"))
                storePassword = uploadKey.getProperty("storePassword")
                keyAlias = uploadKey.getProperty("keyAlias")
                keyPassword = uploadKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // A separate app, so a debug build and the Play build can share a phone.
            applicationIdSuffix = ".debug"
        }
        release {
            // Shrunk and obfuscated. The mapping file, kept with each build in CI,
            // turns a crash record's code locations back into names.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The upload key from keystore.properties; without it the build is unsigned.
            // CI's release check names the debug key there instead, so its build installs.
            signingConfig = signingConfigs.findByName("upload")
        }
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG: debug builds allow screenshots; release builds never do.
        buildConfig = true
    }

    testOptions {
        // View model tests run on the JVM; any Android call they reach returns a default.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okio.fakefilesystem)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

/**
 * Fails the build if the app, or any library it pulls in, asks for network access.
 * Fortuna keeps all data on the device, so the manifest must never request it.
 */
abstract class VerifyNoInternetPermission : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile.readText()
        val request = Regex("""<uses-permission[^>]*"android\.permission\.INTERNET"""")
        if (request.containsMatchIn(manifest)) {
            throw GradleException(
                "The merged manifest requests android.permission.INTERNET. " +
                    "Fortuna must not have network access: find the dependency that adds it."
            )
        }
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        tasks.register<VerifyNoInternetPermission>("verify${variantName}NoInternetPermission") {
            group = "verification"
            description = "Checks that the ${variant.name} manifest does not request network access."
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        }
    }
}
