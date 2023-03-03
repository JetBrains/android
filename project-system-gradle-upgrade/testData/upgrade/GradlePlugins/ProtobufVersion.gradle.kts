buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        val protobuf_version = "0.8.13"
        classpath("com.android.tools.build:gradle:4.2.0")
        classpath("com.google.protobuf:protobuf-gradle-plugin:${protobuf_version}")
    }
}

allprojects {
    repositories {
        jcenter()
    }
}
