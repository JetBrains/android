package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import junit.framework.TestCase.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)

class AndroidGradlePluginVersionCompatibilityInIDEATest(val info: TestInfo) {
  
  @Test
  fun assertAGPVersionsAreCompatible() {
    assertEquals(
      "AGP compatibility check failed. projectAGP: ${info.projectVersion} IDEA AGP:${info.ideaVersion}",
      info.expectedCompatibility, checkAgpCompatibilityForIdeaAndroidProject(info.projectVersion, info.ideaVersion)
    )
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun data() = listOf(
      // Special case 1: versions equal
      TestInfo("4.2.0-alpha01", "4.2.0-alpha01", true),

      // Special case 2: current version earlier than minimum supported
      TestInfo("3.1.0", "4.2.0-alpha01", false),
      TestInfo("3.1.0-alpha01", "4.2.0-alpha01", false),
      TestInfo("3.1.0-beta2", "4.2.0-alpha01", false),
      TestInfo("3.1.0-rc03", "4.2.0-alpha01", false),

      // RC/release of the same major/minor cycle
      TestInfo("7.1.0-rc01", "7.1.0", true),
      TestInfo("7.1.0", "7.1.0-rc01", true),
      TestInfo("7.1.0", "7.1.1", true),
      TestInfo("7.1.1", "7.1.0", true),

      // project's version is a snapshot, and IDEA is a RC/release of the same major/minor version
      TestInfo("7.1.0-dev", "7.1.0-rc01", true),
      TestInfo("7.2.0-dev", "7.1.0-rc01", true),

      // project's version is a snapshot, and IDEA is an alpha/beta of the same major/minor version
      TestInfo("7.1.0-dev", "7.1.0-alpha01", true),

      // project's version is later than IDEA's latest
      TestInfo("7.1.0-dev", "7.0.0-rc01", true),
      TestInfo("7.1.0-dev", "7.0.0-dev", true),
      TestInfo("7.1.0-dev", "7.0.0-alpha01", true),
      TestInfo("7.1.0-alpha01", "7.0.0-rc01", true),
      TestInfo("7.1.0-alpha01", "7.0.0-dev", true),
      TestInfo("7.1.0-alpha01", "7.0.0-alpha01",true),
      TestInfo("7.1.0-rc01", "7.0.0-rc01", true),
      TestInfo("7.1.0-rc01", "7.0.0-dev", true),
      TestInfo("7.1.0-rc01", "7.0.0-alpha01", true),
      TestInfo("7.1.0-rc01", "7.1.0-alpha01", true),
      TestInfo("7.1.0-alpha02", "7.1.0-alpha01", true),
      TestInfo("7.2.0-dev", "7.0.0-rc01", false),
      TestInfo("7.2.0-dev", "7.0.0-dev", false),
      TestInfo("7.2.0-dev", "7.0.0-alpha01", false),
      TestInfo("7.2.0-alpha01", "7.0.0-rc01", false),
      TestInfo("7.2.0-alpha01", "7.0.0-dev", false),
      TestInfo("7.2.0-alpha01", "7.0.0-alpha01",false),
      TestInfo("7.2.0-rc01", "7.0.0-rc01", false),
      TestInfo("7.2.0-rc01", "7.0.0-dev", false),
      TestInfo("7.2.0-rc01", "7.0.0-alpha01", false),
      TestInfo("7.2.0-rc01", "7.0.0-alpha01", false),
      TestInfo("7.2.0-alpha02", "7.0.0-alpha01", false),

      // project's version is an alpha/beta and IDEA's version is not a snapshot
      TestInfo("7.1.0-alpha01", "7.1.0-rc01", true),
      TestInfo("7.1.0-alpha01", "7.1.0-alpha02", true),
      TestInfo("7.1.0-alpha01", "7.2.0-alpha01", true),
      TestInfo("7.1.0-alpha01", "7.2.0-rc01", true),

      // project's version is a snapshot of an earlier series
      TestInfo("7.1.0-dev", "7.2.0-dev", true),
      TestInfo("7.1.0-dev", "7.2.0-alpha01", true),
      TestInfo("7.1.0-dev", "7.2.0-rc01", true),

      // otherwise
      TestInfo("7.1.0-rc01", "7.2.0-alpha01", true),
      TestInfo("7.1.0-rc01", "7.2.0-rc01", true),
      TestInfo("7.1.0-rc01", "7.2.0-dev", true),
      TestInfo("7.1.0-rc01", "7.1.0-dev", true),
      TestInfo("7.1.0-alpha01", "7.1.0-dev", true),
      TestInfo("7.1.0-alpha01", "7.2.0-dev", true),
    )
  }
  
  data class TestInfo(
    val projectVersion: AgpVersion,
    val ideaVersion: AgpVersion,
    val expectedCompatibility: Boolean
  ) {
    constructor(projectVersion: String, ideaVersion: String, expectedCompatibility: Boolean):
      this(AgpVersion.parse(projectVersion), AgpVersion.parse(ideaVersion), expectedCompatibility)
  }
}