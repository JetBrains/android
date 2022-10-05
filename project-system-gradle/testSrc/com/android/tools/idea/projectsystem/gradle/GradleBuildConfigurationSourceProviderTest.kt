/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Expect
import com.intellij.openapi.project.guessProjectDir
import org.junit.Rule
import org.junit.Test

class GradleBuildConfigurationSourceProviderTest {
  @get:Rule
  val projectRule: EdtAndroidProjectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun `does not crash in unsynced state`() {
    val preparedProject = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION_SYNC_FAILED)
    preparedProject.open { project ->
      val provider = GradleBuildConfigurationSourceProvider(project)
      // Make sure that these methods do not crash when models are not available.
      expect.that(provider.getBuildConfigurationFiles()).isNotEmpty()
      expect.that(provider.contains(project.guessProjectDir()!!)).isFalse()
    }
  }
}