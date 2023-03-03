package com.android.tools.idea.lint

import com.android.testutils.JarTestSuiteRunner
import com.android.tools.tests.IdeaTestSuiteBase
import org.junit.runner.RunWith

@RunWith(JarTestSuiteRunner::class)
@JarTestSuiteRunner.ExcludeClasses(AndroidLintTestSuite::class) // a suite mustn't contain itself
class AndroidLintTestSuite : IdeaTestSuiteBase() {
  companion object {
    init {
      linkIntoOfflineMavenRepo("tools/adt/idea/android-lint/test_deps.manifest")
      unzipIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin.zip")
      linkIntoOfflineMavenRepo(
        "tools/base/build-system/android_gradle_plugin_runtime_dependencies.manifest"
      )
      linkIntoOfflineMavenRepo(
        "tools/base/build-system/integration-test/kotlin_gradle_plugin_prebuilts.manifest"
      )
    }
  }
}
