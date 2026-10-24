import com.android.build.api.artifact.SingleArtifact

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "io.github.arpit92k.fortuna"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.arpit92k.fortuna"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        // Device tests (androidTest) run on a phone or emulator: see .github/workflows/device-tests.yml.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
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
