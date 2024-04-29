plugins {
    id("com.android.application")
    id("com.android.library")
    id("java")
    id(<warning descr="'android' is deprecated; use 'com.android.application' instead">"android"</warning>)
    id(<warning descr="'android-library' is deprecated; use 'com.android.library' instead">"android-library"</warning>)
}

android {
}