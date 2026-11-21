plugins { id("com.android.application") }
android {
    namespace = "com.genee.psavoice"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.genee.psavoice"
        minSdk = 28
        targetSdk = 34
        versionCode = 9
        versionName = "0.3.0"
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
