buildscript {
    val agpVersion by extra("1.2.3.4")
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:$agpVersion")
    }
}

allprojects {
    repositories {
        jcenter()
    }
}
