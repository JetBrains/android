plugins {
    id("com.android.application")
}

android {
    <warning descr="Deprecated: Replace 'packageName' with 'applicationId'">defaultConfig.packageName</warning> = "my.pkg"
    buildTypes {
        <warning descr="Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix'">findByName("debug").packageNameSuffix</warning> = ".debug"
    }
}
