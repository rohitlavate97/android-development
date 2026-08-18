plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.expensetracker.core.database"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))

    implementation(libs.bundles.room)
    // In full KSP projects, ksp(libs.androidx.room.compiler) is used
    
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.bundles.testing.unit)
}
