buildscript {
  apply(from="../applied.gradle.kts")
  repositories {
    maven { url = uri("/Volumes/android/studio-master-dev/out/repo/") }
    google()
    jcenter()
  }
  dependencies {
    classpath("com.android.tools.build:gradle:3.4.0-dev")
    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${extra["kotlin_version"]}")

    // NOTE: Do not place your application dependencies here; they belong
    // in the individual module build.gradle files
  }
}
apply(plugin="com.android.application")

apply(plugin="kotlin-android")

apply(plugin="kotlin-android-extensions")

extra["compositeProjectProperty"] = true

allprojects {
  repositories {
    google()
    jcenter()
  }
}

android {
    compileSdkVersion(26)
    defaultConfig {
        applicationId = "${extra["appName"]}"
        minSdkVersion = 15
        targetSdkVersion = 26
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":composite1"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jre7:${parent.extra["kotlin_version"]}")
    implementation("com.android.support:appcompat-v7:26.1.0")
    implementation("com.android.support.constraint:constraint-layout:1.0.2")
    testImplementation("junit:junit:4.12")
    androidTestImplementation("com.android.support.test:runner:1.0.1")
    androidTestImplementation("com.android.support.test.espresso:espresso-core:3.0.1")
    implementation("com.test.composite:composite3:1.0")
}
