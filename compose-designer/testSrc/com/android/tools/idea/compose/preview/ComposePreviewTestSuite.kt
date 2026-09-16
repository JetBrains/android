/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.compose.preview

import com.android.testutils.JarTestSuiteRunner
import com.android.tools.tests.IdeaTestSuiteBase
import java.io.File
import org.junit.runner.RunWith

private const val ANDROID_KOTLIN_MULTIPLATFORM_MULTI_PREVIEW_DEPS = "tools/adt/idea/compose-designer/testData/android_kotlin_multiplatform_multi_preview_deps.manifest"

@RunWith(JarTestSuiteRunner::class)
class ComposePreviewTestSuite : IdeaTestSuiteBase() {
  companion object {
    init {
      if (File(ANDROID_KOTLIN_MULTIPLATFORM_MULTI_PREVIEW_DEPS).exists()) {
        linkIntoOfflineMavenRepo(ANDROID_KOTLIN_MULTIPLATFORM_MULTI_PREVIEW_DEPS)
        linkIntoOfflineMavenRepo("tools/base/build-system/previous-versions/8.13.0.manifest")
      }
      linkIntoOfflineMavenRepo("tools/adt/idea/compose-designer/testData/simple_compose_application_test_deps.manifest")
      linkIntoOfflineMavenRepo("tools/adt/idea/compose-designer/testData/onboarding_auth_ibm_project_dep.manifest")
      unzipIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin.zip")
      linkIntoOfflineMavenRepo("tools/base/build-system/android_gradle_plugin_runtime_dependencies.manifest")
      linkIntoOfflineMavenRepo("tools/base/build-system/integration-test/kotlin_gradle_plugin_prebuilts.manifest")
      linkIntoOfflineMavenRepo("tools/base/build-system/integration-test/kotlin_gradle_plugin_for_compose_prebuilts.manifest")
      linkIntoOfflineMavenRepo("tools/base/third_party/kotlin/kotlin-m2repository.manifest")
    }
  }
}
