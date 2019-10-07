// Top-level build file where you can add configuration options common to all sub-projects/modules.
extra["someVar"] = "Present"
// TODO(xof): (was scriptBool) this clearly indicates to me that I do
//  not understand the extra model.  scriptBool is only defined
//  "later", and yet Gradle understand this (and the Groovy analogue).
extra["rootBool"] = extra["scriptBool"]

buildscript {
    extra["scriptVar1"] = 1
    var scriptVar2 by extra("2")
    var scriptBool by extra(true)
    var agpVersionX by extra("3.4.x")
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

var rootBool3 by extra(extra["rootBool"])
var rootBool2 by extra(rootBool3)
var rootFloat by extra(3.14)
