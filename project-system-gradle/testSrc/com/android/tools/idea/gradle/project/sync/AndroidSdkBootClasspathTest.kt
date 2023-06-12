package com.android.tools.idea.gradle.project.sync

import com.google.common.truth.Truth
import org.junit.Test

class AndroidSdkBootClasspathTest {
  @Test
  fun testOnlyAndroidJar() {
    val libs = getUsefulBootClasspathLibraries(listOf(ANDROID_JAR))
    Truth.assertThat(libs).isEmpty()
  }

  @Test
  fun testWithUnrelatedLibs() {
    val libs = getUsefulBootClasspathLibraries(listOf("/tmp/b.jar", ANDROID_JAR, "/tmp/a.jar"))
    Truth.assertThat(libs).isEmpty()
  }

  @Test
  fun testWithOptionalLibs() {
    val libs = getUsefulBootClasspathLibraries(listOf(ANDROID_JAR, OPTIONAL_JAR)).map { it.path }
    Truth.assertThat(libs).containsExactly(OPTIONAL_JAR)
  }

  @Test
  fun testWithSdkAddOn() {
    val libs = getUsefulBootClasspathLibraries(listOf(ANDROID_JAR, SDK_ADDON_JAR)).map { it.path }
    Truth.assertThat(libs).containsExactly(SDK_ADDON_JAR)
  }

  @Test
  fun testNoAndroidJar() {
    val libs = getUsefulBootClasspathLibraries(listOf(SDK_ADDON_JAR, OPTIONAL_JAR)).map { it.path }
    Truth.assertThat(libs).isEmpty()
  }
}

private const val ANDROID_JAR = "/Android/Sdk/platforms/android-33/android.jar"
private const val OPTIONAL_JAR = "/Android/Sdk/platforms/android-33/optional/a.jar"
private const val SDK_ADDON_JAR = "/Android/Sdk/add-ons/custom-custom/libs/a.jar"