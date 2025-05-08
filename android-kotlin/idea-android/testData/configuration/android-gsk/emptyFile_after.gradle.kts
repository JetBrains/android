plugins {
    id("org.jetbrains.kotlin.android") version "$KOTLIN_VERSION_FOR_TESTS"
}
android {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
