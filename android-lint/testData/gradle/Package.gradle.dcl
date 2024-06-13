androidApplication {
    defaultConfig {
        <warning descr="Deprecated: Replace 'packageName' with 'applicationId'">packageName</warning> = "my.pkg"
    }
    buildTypes {
        debug {
            <warning descr="Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix'">packageNameSuffix</warning> = ".debug"
        }
    }
}
