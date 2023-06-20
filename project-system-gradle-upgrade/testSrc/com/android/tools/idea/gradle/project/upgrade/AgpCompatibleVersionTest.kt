package com.android.tools.idea.gradle.project.upgrade

import com.android.SdkConstants
import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.util.CompatibleGradleVersion.Companion.getCompatibleGradleVersion
import com.google.common.truth.Expect
import com.intellij.testFramework.LightPlatformTestCase
import org.gradle.util.GradleVersion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AgpCompatibleVersionTest : LightPlatformTestCase() {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun testCompatibleVersions() {
    val data = mapOf(
      /**
       * This test intentionally does not use the symbolic constant SdkConstants.GRADLE_MINIMUM_VERSION, because any change to that should
       * involve a careful look at the compatibility tables used by [getCompatibleGradleVersion] (which failing this test and reading
       * this comment should encourage the brave maintainer to do.  Changes to GRADLE_LATEST_VERSION are both less likely to be disruptive
       * and more likely to be noticed quickly.
       */
      "3.1" to GradleVersion.version("4.8.1"),
      "3.2" to GradleVersion.version("4.8.1"),
      "3.3" to GradleVersion.version("4.10.1"),
      "3.4" to GradleVersion.version("5.1.1"),
      "3.5" to GradleVersion.version("5.4.1"),
      "3.6" to GradleVersion.version("5.6.4"),
      "4.0" to GradleVersion.version("6.1.1"),
      "4.1" to GradleVersion.version("6.5"),
      "4.2" to GradleVersion.version("6.7.1"),
      "7.0" to GradleVersion.version("7.0.2"),
      "7.1" to GradleVersion.version("7.2"),
      "7.2" to GradleVersion.version("7.3.3"),
      "7.3" to GradleVersion.version("7.4"),
      "7.4" to GradleVersion.version("7.5"),
      "8.0" to GradleVersion.version(SdkConstants.GRADLE_LATEST_VERSION)
    )
    fun String.toBetaVersionString() = when (this) {
      "3.1" -> "$this.0-beta2"
      else -> "$this.0-beta02"
    }
    data.forEach { (agpBase, expected) ->
      expect.that(getCompatibleGradleVersion(AgpVersion.parse("$agpBase.0")).version).isEqualTo(expected)
      expect.that(getCompatibleGradleVersion(AgpVersion.parse("$agpBase.9")).version).isEqualTo(expected)
      expect.that(getCompatibleGradleVersion(AgpVersion.parse("$agpBase.0-alpha01")).version).isEqualTo(expected)
      expect.that(getCompatibleGradleVersion(AgpVersion.parse(agpBase.toBetaVersionString())).version).isEqualTo(expected)
      expect.that(getCompatibleGradleVersion(AgpVersion.parse("$agpBase.0-rc03")).version).isEqualTo(expected)
    }
  }
}