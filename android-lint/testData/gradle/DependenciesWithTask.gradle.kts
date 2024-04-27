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
    freeCompile("<warning>com.google.guava:guava:11.0.2</warning>")
    compile("<warning>com.android.support:appcompat-v7:13.0.0</warning>")
}


tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
