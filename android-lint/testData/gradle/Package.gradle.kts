plugins {
    id("com.android.application")
}

android {
    defaultConfig {
        <warning descr="Deprecated: Replace 'packageName' with 'applicationId'">packageName</warning> = "my.pkg"
    }
    buildTypes {
        findByName("debug") {
            <warning descr="Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix'">packageNameSuffix</warning> = ".debug"
        }
    }
}
