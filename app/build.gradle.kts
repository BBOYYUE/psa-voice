plugins { id("com.android.application") }
android {
    namespace = "com.sollado.psavoice"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.sollado.psavoice"
        minSdk = 28
        targetSdk = 34
        versionCode = 13
        versionName = "0.6.0"
    }
    buildFeatures { buildConfig = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            it.systemProperty("psa.preview.file", project.layout.buildDirectory.file("reports/conversation-preview.png").get().asFile.absolutePath)
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
