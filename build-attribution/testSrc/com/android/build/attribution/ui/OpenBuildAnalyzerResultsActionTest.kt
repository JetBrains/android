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
package com.android.build.attribution.ui

import com.android.build.attribution.BuildAnalyzerStorageManager
import com.android.build.attribution.BuildDescriptor
import com.android.build.attribution.analyzers.BuildEventsAnalyzersProxy
import com.android.build.attribution.data.BuildRequestHolder
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.TaskContainer
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.idea.Projects
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.UUID

@RunsInEdt
class OpenBuildAnalyzerResultsActionTest {

  private val projectRule = AndroidProjectRule.onDisk()
  private val jbPopupRule = JBPopupRule()
  private val openBuildAnalyzerResultsAction = OpenBuildAnalyzerResultsAction()
  private lateinit var event: AnActionEvent

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule()).around(jbPopupRule)!!

  @Before
  fun setup() {
    event = TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(projectRule.project))
    StudioFlags.BUILD_ANALYZER_HISTORY.override(true)
  }

  @After
  fun clearOverrideFlag() {
    StudioFlags.BUILD_ANALYZER_HISTORY.clearOverride()
  }

  @Test
  fun testActionIsRegistered() {
    val action = ActionManager.getInstance()
      .getAction("Android.OpenBuildAnalyzerResultsAction")
    Truth.assertThat(action).isNotNull()
  }

  @Test
  fun testUpdateNoData() {
    openBuildAnalyzerResultsAction.update(event)
    Truth.assertThat(event.presentation.isVisible).isTrue()
    Truth.assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun testUpdateNoProject() {
    event = TestActionEvent.createTestEvent(EMPTY_CONTEXT)
    openBuildAnalyzerResultsAction.update(event)
    Truth.assertThat(event.presentation.isVisible).isFalse()
    Truth.assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun testUpdateDataAndProject() {
    val buildSessionID = UUID.randomUUID().toString()
    storeDefaultData(buildSessionID)
    openBuildAnalyzerResultsAction.update(event)
    Truth.assertThat(event.presentation.isVisible).isTrue()
    Truth.assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun testActionPerformed() {
    val buildSessionIDs = List(5) {
      val buildSessionID = UUID.randomUUID().toString()
      storeDefaultData(buildSessionID)
      buildSessionID
    }
    openBuildAnalyzerResultsAction.update(event)
    Truth.assertThat(event.presentation.isVisible).isTrue()
    Truth.assertThat(event.presentation.isEnabled).isTrue()
    openBuildAnalyzerResultsAction.actionPerformed(event)
    val popup = jbPopupRule.fakePopupFactory.getPopup<BuildDescriptor>(0)
    Truth.assertThat(popup.title).isEqualTo("Build Analysis Results")
    Truth.assertThat(popup.items.map {it.buildSessionID}).containsExactlyElementsIn(buildSessionIDs)
  }

  private fun storeDefaultData(buildSessionID: String) {
    BuildAnalyzerStorageManager.getInstance(projectRule.project).let { storage ->
      storage.storeNewBuildResults(
        BuildEventsAnalyzersProxy(TaskContainer(), PluginContainer(), storage),
        buildSessionID,
        BuildRequestHolder(GradleBuildInvoker.Request
                             .builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()))
    }
  }
}