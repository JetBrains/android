// Top-level build file where you can add configuration options common to all sub-projects/modules.
extra["someVar"] = "Present"
// TODO(xof): (was scriptBool) this clearly indicates to me that I do
//  not understand the extra model.  scriptBool is only defined
//  "later", and yet Gradle understand this (and the Groovy analogue).
extra["rootBool"] = extra["scriptBool"]

buildscript {
    extra["scriptVar1"] = 1
    extra["scriptVar2"] = "2"
    extra["scriptBool"] = true
    extra["agpVersionX"] = "3.4.x"
    repositories {
        // This will be populated by AndroidGradleTestCase
    }
    dependencies {
        classpath("com.android.tools.build:gradle:0.14.4")
    }
}

allprojects {
    repositories {
        // This will be populated by AndroidGradleTestCase
    }
}

extra["rootBool3"] = extra["rootBool"]
extra["rootBool2"] = extra["rootBool3"]
extra["rootFloat"] = 3.14
extra["listProp"] = listOf(15, 16, 45)
extra["mapProp"] = mapOf("key1" to "val1", "key2" to "val2")
extra["boolRoot"] = true
extra["dependencyVersion"] = "28.0.0"