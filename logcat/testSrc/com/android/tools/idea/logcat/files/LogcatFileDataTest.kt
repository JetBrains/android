package com.android.tools.idea.logcat.files

import com.android.tools.idea.logcat.devices.Device
import com.android.tools.idea.logcat.files.LogcatFileData.Metadata
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Tests for [LogcatFileData] */
class LogcatFileDataTest {
  private val device = Device.createPhysical("device", true, "11", 30, "Google", "Pixel")

  @Test
  fun safeGetFilter_noMine() {
    val packages = setOf("package1")
    val data = LogcatFileData(Metadata(device, "package:foo tag:Foo", packages), emptyList())
    assertThat(data.safeGetFilter()).isEqualTo("package:foo tag:Foo")
  }

  @Test
  fun safeGetFilter_withMineAndSingleProjectPackage() {
    val packages = setOf("package1")
    val data = LogcatFileData(Metadata(device, "package:mine", packages), emptyList())
    assertThat(data.safeGetFilter()).isEqualTo("package:package1")
  }

  @Test
  fun safeGetFilter_withMineAndSingleProjectPackageAndMoreFilters() {
    val packages = setOf("package1")
    val data = LogcatFileData(Metadata(device, "foo package:mine tag:Bar", packages), emptyList())
    assertThat(data.safeGetFilter()).isEqualTo("foo package:package1 tag:Bar")
  }

  @Test
  fun safeGetFilter_withMineAndMultipleProjectPackages() {
    val packages = setOf("package1", "package2")
    val data = LogcatFileData(Metadata(device, "package:mine", packages), emptyList())
    assertThat(data.safeGetFilter()).isEqualTo("package:package1 package:package2")
  }

  @Test
  fun safeGetFilter_withMineAndMultipleProjectPackagesAndMoreFilters() {
    val packages = setOf("package1", "package2")
    val data = LogcatFileData(Metadata(device, "foo package:mine tag:Bar", packages), emptyList())
    assertThat(data.safeGetFilter()).isEqualTo("foo (package:package1 | package:package2) tag:Bar")
  }

  @Test
  fun safeGetFilter_withMineWithoutProjectPackages() {
    val packages = emptySet<String>()
    val data = LogcatFileData(Metadata(device, "foo package:mine tag:Bar", packages), emptyList())
    assertThat(data.safeGetFilter()).isEqualTo("foo  tag:Bar")
  }
}
