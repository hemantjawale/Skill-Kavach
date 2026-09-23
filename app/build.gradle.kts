import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    id("com.google.devtools.ksp") version "2.3.12"
}

android {
    namespace = "com.example.skilkavach"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.skilkavach"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val apiUrl = providers.gradleProperty("API_BASE_URL").orElse("https://skill-kavach.onrender.com/").get()
        require(apiUrl.startsWith("https://") || apiUrl == "http://10.0.2.2:8080/")
        val apiUri = URI(apiUrl)
        require(apiUri.host != null && apiUri.userInfo == null && apiUri.query == null && apiUri.fragment == null && apiUri.path == "/") {
            "API_BASE_URL must be an origin ending with /, without credentials, query parameters or /api."
        }
        buildConfigField("String", "API_BASE_URL", "\"$apiUrl\"")
        listOf("FIREBASE_APP_ID", "FIREBASE_API_KEY", "FIREBASE_PROJECT_ID", "FIREBASE_SENDER_ID").forEach { key ->
            val value = providers.gradleProperty(key).orElse("").get()
            require(value.matches(Regex("[A-Za-z0-9_:.\\-]*")))
            buildConfigField("String", key, "\"$value\"")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("androidx.work:work-runtime-ktx:2.11.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.ar:core:1.54.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
val validateProductionEndpoint by tasks.registering {
    val configuredUrl = providers.gradleProperty("API_BASE_URL").orElse("https://skill-kavach.onrender.com/")
    doLast {
        val endpoint = configuredUrl.orNull.orEmpty()
        require(endpoint.startsWith("https://") && !endpoint.contains("localhost")) {
            "Release builds require -PAPI_BASE_URL=https://your-production-api/"
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(validateProductionEndpoint) }
