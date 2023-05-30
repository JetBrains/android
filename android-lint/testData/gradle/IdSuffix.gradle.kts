plugins {
    id("com.android.application")
}

android {
    buildTypes {
        getByName("debug") {
            <warning>applicationIdSuffix = "debug"</warning>
        }
    }
}
