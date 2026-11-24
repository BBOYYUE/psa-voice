plugins { id("com.android.application") }
android {
 namespace = "com.sollado.psaprobe"
 compileSdk = 34
 defaultConfig { applicationId = "com.sollado.psaprobe"; minSdk = 28; targetSdk = 34; versionCode = 1; versionName = "0.1" }
 testOptions { unitTests.isIncludeAndroidResources = true; unitTests.all { it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2") } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies { testImplementation("junit:junit:4.13.2"); testImplementation("org.robolectric:robolectric:4.14.1") }
