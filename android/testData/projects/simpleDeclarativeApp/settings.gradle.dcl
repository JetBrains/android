pluginManagement {
    repositories {
    }
}
plugins {
    id("com.android.ecosystem").version("dcl_plugin_version")
}
dependencyResolutionManagement {
    repositories {
    }
}


defaults {
    androidApp {
        compileSdk = 34
    }
}

include(":app")