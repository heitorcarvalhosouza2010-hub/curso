plugins {
    id("com.android.application")
}

android {
    namespace = "br.com.fitfood"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.fitfood"
        minSdk = 23
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0"
    }
}

dependencies {
    implementation("androidx.core:core:1.15.0")
    testImplementation("junit:junit:4.13.2")
}
