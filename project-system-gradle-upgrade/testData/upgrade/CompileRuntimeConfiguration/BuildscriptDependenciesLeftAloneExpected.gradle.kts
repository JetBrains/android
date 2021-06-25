// This build file is nonsensical, and is here purely to test logic
// within the refactoring processor to avoid changing parts of the
// project configuration that it does not understand.
plugins {
    id("com.android.application")
}

buildscript {
    dependencies {
        "preCompile"("junit:junit:4.11")
    }
}

dependencies {
    testImplementation("junit:junit:4.13")
}
