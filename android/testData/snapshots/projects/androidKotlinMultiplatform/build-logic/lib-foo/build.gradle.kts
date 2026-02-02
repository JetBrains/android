plugins {
    id("kotlin-multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    androidLibrary {
        namespace = "com.buildlogic.lib.foo"
        compileSdk = 36
        minSdk = 24

        withHostTest {  }
        withDeviceTest {  }
    }
}
