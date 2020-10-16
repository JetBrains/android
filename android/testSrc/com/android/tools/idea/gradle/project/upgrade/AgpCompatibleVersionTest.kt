package com.android.tools.idea.gradle.project.upgrade

import com.android.SdkConstants
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.upgrade.AgpGradleVersionRefactoringProcessor.Companion.getCompatibleGradleVersion
import com.google.common.truth.Expect
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AgpCompatibleVersionTest {
  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun testCompatibleVersions() {
    val data = mapOf(
      "3.1" to GradleVersion.parse("4.4"),
      "3.2" to GradleVersion.parse("4.6"),
      "3.3" to GradleVersion.parse("4.10.1"),
      "3.4" to GradleVersion.parse("5.1.1"),
      "3.5" to GradleVersion.parse("5.4.1"),
      "3.6" to GradleVersion.parse("5.6.4"),
      "4.0" to GradleVersion.parse("6.1.1"),
      "4.1" to GradleVersion.parse("6.5"),
      "4.2" to GradleVersion.parse(SdkConstants.GRADLE_LATEST_VERSION),
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