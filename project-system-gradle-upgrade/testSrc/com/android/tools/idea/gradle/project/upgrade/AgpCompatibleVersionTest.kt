package com.android.tools.idea.gradle.project.upgrade

import com.android.SdkConstants
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.upgrade.CompatibleGradleVersion.Companion.getCompatibleGradleVersion
import com.google.common.truth.Expect
import com.intellij.testFramework.LightPlatformTestCase
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
      "3.1" to GradleVersion.parse("4.10"),
      "3.2" to GradleVersion.parse("4.10"),
      "3.3" to GradleVersion.parse("4.10.1"),
      "3.4" to GradleVersion.parse("5.1.1"),
      "3.5" to GradleVersion.parse("5.4.1"),
      "3.6" to GradleVersion.parse("5.6.4"),
      "4.0" to GradleVersion.parse("6.1.1"),
      "4.1" to GradleVersion.parse("6.5"),
      "4.2" to GradleVersion.parse("6.7.1"),
      "7.0" to GradleVersion.parse("7.0.2"),
      "7.1" to GradleVersion.parse("7.2"),
      "7.2" to GradleVersion.parse("7.3.3"),
      "7.3" to GradleVersion.parse("7.4"),
      "7.4" to GradleVersion.parse(SdkConstants.GRADLE_LATEST_VERSION)
    )
    data.forEach { (agpBase, expected) ->
      expect.that(getCompatibleGradleVersion(GradleVersion.parse(agpBase)).version).isEqualTo(expected)
      expect.that(getCompatibleGradleVersion(GradleVersion.parse("$agpBase.0")).version).isEqualTo(expected)
      expect.that(getCompatibleGradleVersion(GradleVersion.parse("$agpBase.9")).version).isEqualTo(expected)
      expect.that(getCompatibleGradleVersion(GradleVersion.parse("$agpBase-alpha01")).version).isEqualTo(expected)
      expect.that(getCompatibleGradleVersion(GradleVersion.parse("$agpBase-beta02")).version).isEqualTo(expected)
      expect.that(getCompatibleGradleVersion(GradleVersion.parse("$agpBase-rc03")).version).isEqualTo(expected)
    }
  }
}