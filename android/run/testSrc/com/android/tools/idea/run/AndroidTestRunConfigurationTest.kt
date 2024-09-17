/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testartifacts.TestConfigurationTesting
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.switchVariant
import com.google.common.truth.Truth
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import junit.framework.TestCase.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.util.stream.Collectors

class AndroidTestRunConfigurationTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testCannotRunLibTestsInReleaseBuild() {
    projectRule.prepareTestProject(AndroidCoreTestProject.PROJECT_WITH_APP_AND_LIB_DEPENDENCY).open {
      val androidTestRunConfiguration = invokeAndWaitIfNeeded {
        TestConfigurationTesting.createAndroidTestConfigurationFromClass(
          it,
          "com.example.projectwithappandlib.lib.ExampleInstrumentedTest"
        )
      }!!
      assertNotNull(androidTestRunConfiguration)
      var errors = invokeAndWaitIfNeeded { androidTestRunConfiguration!!.validate(null) }
      Truth.assertThat(errors).hasSize(0)
      switchVariant(it, ":app", "basicRelease")
      errors = invokeAndWaitIfNeeded { androidTestRunConfiguration.validate(null) }
      Truth.assertThat(errors).isNotEmpty()
      Truth.assertThat(errors.stream().map { obj: ValidationError? -> obj!!.message }
                         .collect(Collectors.toList()))
        .contains("Run configuration ExampleInstrumentedTest is not supported in the current project. Cannot obtain the package.")
    }
  }

  @Test
  fun testCanRunLibTestsInDebugBuildWithNoAndroidManifest() {
    projectRule.prepareTestProject(AndroidCoreTestProject.PROJECT_WITH_APP_AND_LIB_DEPENDENCY_NO_LIB_MANIFEST).open {
      val androidTestRunConfiguration = invokeAndWaitIfNeeded {
        TestConfigurationTesting.createAndroidTestConfigurationFromClass(
          it,
          "com.example.projectwithappandlib.lib.ExampleInstrumentedTest"
        )
      }
      assertNotNull(androidTestRunConfiguration)
      val errors = invokeAndWaitIfNeeded { androidTestRunConfiguration!!.validate(null) }
      Truth.assertThat(errors).hasSize(0)
    }
  }
}
