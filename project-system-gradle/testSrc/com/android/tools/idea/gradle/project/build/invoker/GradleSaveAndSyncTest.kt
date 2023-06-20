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
package com.android.tools.idea.gradle.project.build.invoker

import com.android.tools.idea.gradle.project.sync.snapshots.TestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.buildAndWait
import com.google.common.truth.Truth
import com.intellij.ide.SaveAndSyncHandler
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify

class GradleSaveAndSyncTest {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testSaveAndSyncIsCalledOnBuild() {
    val simpleApplication = projectRule.prepareTestProject(TestProject.SIMPLE_APPLICATION, "project")
    val saveAndSyncHandler = spy(SaveAndSyncHandler.getInstance())
    projectRule.replaceService(SaveAndSyncHandler::class.java, saveAndSyncHandler)
    simpleApplication.open { project ->
      val result = project.buildAndWait {buildInvoker ->
        buildInvoker.assemble(TestCompileType.ALL)
      }
      verify(saveAndSyncHandler).scheduleRefresh()
      Truth.assertThat(result.isBuildSuccessful).isTrue()
    }
  }
}