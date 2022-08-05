plugins {
    id("com.android.application") version "4.2.0" apply false
    id("com.example.android")
}

android {
    compileSdkVersion(30)
    aidlPackagedList += listOf("one.aidl", "two.aidl")
}

