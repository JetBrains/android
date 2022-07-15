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
package com.android.build.attribution

import com.android.build.attribution.analyzers.BuildEventsAnalyzersProxy
import com.android.build.attribution.data.BuildRequestHolder
import com.google.common.truth.Truth
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.TaskContainer
import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.Rule
import org.junit.Test

class BuildAnalyzerStorageManagerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @Test(expected = IllegalStateException::class)
  fun testNullBuildResultsResponse(){
    BuildAnalyzerStorageManager.getInstance(projectRule.project).getLatestBuildAnalysisResults()
  }
  @Test
  fun testBuildResultsAreStored(){
    val taskContainer = TaskContainer()
    val pluginContainer = PluginContainer()
    val analyzersProxy = BuildEventsAnalyzersProxy(taskContainer, pluginContainer)
    val request = GradleBuildInvoker.Request
      .builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()

    BuildAnalyzerStorageManager.getInstance(projectRule.project)
      .storeNewBuildResults(analyzersProxy, "some buildID", BuildRequestHolder(request))

    Truth.assertThat(BuildAnalyzerStorageManager.getInstance(projectRule.project).getLatestBuildAnalysisResults()).isNotNull()
  }
}