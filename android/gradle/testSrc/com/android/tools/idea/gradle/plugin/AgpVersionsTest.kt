package com.android.tools.idea.gradle.plugin

import com.android.Version
import com.android.flags.junit.FlagRule
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFailsWith

class AgpVersionsTest {

  @get:Rule
  val flagRule = FlagRule(StudioFlags.AGP_VERSION_TO_USE)

  @Test
  fun `check no override behaviour` () {
    StudioFlags.AGP_VERSION_TO_USE.clearOverride()
    assertThat(AgpVersions.studioFlagOverride).isNull()
    assertThat(AgpVersions.newProject).isEqualTo(AgpVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION))
    assertThat(AgpVersions.latestKnown).isEqualTo(AgpVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION))
  }

  @Test
  fun `check override behaviour` () {
    StudioFlags.AGP_VERSION_TO_USE.override("7.4.0")
    assertThat(AgpVersions.studioFlagOverride).isEqualTo(AgpVersion.parse("7.4.0"))
    assertThat(AgpVersions.newProject).isEqualTo(AgpVersion.parse("7.4.0"))
    assertThat(AgpVersions.latestKnown).isEqualTo(AgpVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION))
  }

  @Test
  fun `check override stable` () {
    StudioFlags.AGP_VERSION_TO_USE.override("stable")
    assertThat(AgpVersions.studioFlagOverride!!.isPreview).isFalse()
    assertThat(AgpVersions.newProject).isEqualTo(AgpVersions.studioFlagOverride)
    assertThat(AgpVersions.latestKnown).isEqualTo(AgpVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION))
  }

  @Test
  fun `check override stable case insensitivity` () {
    StudioFlags.AGP_VERSION_TO_USE.override("staBLE")
    assertThat(AgpVersions.studioFlagOverride!!.isPreview).isFalse()
  }

  @Test
  fun `check invalid version` () {
    StudioFlags.AGP_VERSION_TO_USE.override("7.4.0.3")
    val failure = assertFailsWith<IllegalStateException> { AgpVersions.studioFlagOverride }
    assertThat(failure).hasMessageThat().isEqualTo("Invalid value '7.4.0.3' for Studio flag gradle.ide.agp.version.to.use. Expected Android Gradle plugin version (e.g. '8.0.2') or 'stable'")
    assertFailsWith<IllegalStateException> { AgpVersions.newProject }
    assertFailsWith<IllegalStateException> { AgpVersions.latestKnown }
  }

  @Test
  fun `check invalid version string` () {
    StudioFlags.AGP_VERSION_TO_USE.override("canary")
    val failure = assertFailsWith<IllegalStateException> { AgpVersions.studioFlagOverride }
    assertThat(failure).hasMessageThat().isEqualTo("Invalid value 'canary' for Studio flag gradle.ide.agp.version.to.use. Expected Android Gradle plugin version (e.g. '8.0.2') or 'stable'")
    assertFailsWith<IllegalStateException> { AgpVersions.newProject }
    assertFailsWith<IllegalStateException> { AgpVersions.latestKnown }
  }

  @Test
  fun `check override with newer version`() {
    val current = AgpVersion.parse(Version.ANDROID_GRADLE_PLUGIN_VERSION)
    val higherThanCurrent = AgpVersion(current.major + 1, 0)
    assertThat(higherThanCurrent).isGreaterThan(current)
    StudioFlags.AGP_VERSION_TO_USE.override(higherThanCurrent.toString())
    assertThat(AgpVersions.studioFlagOverride).isEqualTo(higherThanCurrent)
    assertThat(AgpVersions.newProject).isEqualTo(higherThanCurrent)
    assertThat(AgpVersions.latestKnown).isEqualTo(higherThanCurrent)
  }

}