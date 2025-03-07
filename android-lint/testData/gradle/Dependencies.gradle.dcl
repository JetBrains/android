androidApplication {
    <warning>compileSdkVersion(27)</warning>

    defaultConfig {
        minSdkVersion(7)
        targetSdkVersion(17)
        versionCode = 1
        versionName = "1.0"
    }

    productFlavors {
        create("free") {
        }
        create("pro") {
        }
    }
}

declarativeDependencies {
    compile("com.android.support:appcompat-v7:+")
    freeCompile(<warning>"androidx.appcompat:appcompat:1.6.0"</warning>)
    compile(<warning>"com.android.support:appcompat-v7:13.0.0"</warning>)
}
