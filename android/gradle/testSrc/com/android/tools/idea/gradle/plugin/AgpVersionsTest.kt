package com.android.tools.idea.gradle.plugin

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
  }

  @Test
  fun `check override behaviour` () {
      StudioFlags.AGP_VERSION_TO_USE.override("7.4.0")
      assertThat(AgpVersions.studioFlagOverride).isEqualTo(AgpVersion.parse("7.4.0"))
  }

  @Test
  fun `check override stable` () {
    StudioFlags.AGP_VERSION_TO_USE.override("stable")
    assertThat(AgpVersions.studioFlagOverride!!.isPreview).isFalse()
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
  }
  @Test
  fun `check invalid version string` () {
    StudioFlags.AGP_VERSION_TO_USE.override("canary")
    val failure = assertFailsWith<IllegalStateException> { AgpVersions.studioFlagOverride }
    assertThat(failure).hasMessageThat().isEqualTo("Invalid value 'canary' for Studio flag gradle.ide.agp.version.to.use. Expected Android Gradle plugin version (e.g. '8.0.2') or 'stable'")
  }
}