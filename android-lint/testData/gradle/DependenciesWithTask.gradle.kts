plugins {
    id("com.android.application")
}

android {
    //noinspection GradleDependency
    compileSdkVersion(27)

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

dependencies {
    compile("com.android.support:appcompat-v7:+")
    freeCompile(<warning descr="A newer version of com.google.guava:guava than 11.0.2 is available: 21.0">"com.google.guava:guava:11.0.2"</warning>)
    compile(<warning descr="A newer version of com.android.support:appcompat-v7 than 13.0.0 is available: 28.0.0">"com.android.support:appcompat-v7:13.0.0"</warning>)
}


tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
