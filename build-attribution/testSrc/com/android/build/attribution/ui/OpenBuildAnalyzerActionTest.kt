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
import com.android.build.attribution.analyzers.BuildEventsAnalyzersProxy
import com.android.build.attribution.data.BuildRequestHolder
import com.android.build.attribution.data.PluginContainer
import com.android.build.attribution.data.TaskContainer
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.testFramework.MapDataContext
import com.google.common.truth.Truth.assertThat
import com.intellij.build.BuildContentManager
import com.intellij.build.BuildContentManagerImpl
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.content.impl.ContentImpl
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.UUID
import javax.swing.JPanel
import com.android.tools.idea.Projects
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker

@RunsInEdt
class OpenBuildAnalyzerActionTest {

  private val projectRule = AndroidProjectRule.inMemory()
  private val openBuildAnalyzerAction = OpenBuildAnalyzerAction()
  private val mapDataContext = MapDataContext()
  private lateinit var event : AnActionEvent
  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @Before
  fun setup() {
    event = AnActionEvent(null, mapDataContext, "place", Presentation(), ActionManager.getInstance(), 0)
  }

  @Test
  fun testActionIsRegistered() {
    val action = ActionManager.getInstance()
      .getAction("com.android.build.attribution.ui.OpenBuildAnalyzerAction")
    assertThat(action).isNotNull()
  }

  @Test
  fun testUpdateNoData() {
    mapDataContext.put(CommonDataKeys.PROJECT, projectRule.project)
    openBuildAnalyzerAction.update(event)
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun testUpdateNoProject() {
    mapDataContext.put(CommonDataKeys.PROJECT, null)
    openBuildAnalyzerAction.update(event)
    assertThat(event.presentation.isVisible).isFalse()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun testUpdateDataAndProject() {
    mapDataContext.put(CommonDataKeys.PROJECT, projectRule.project)
    val buildSessionID = UUID.randomUUID().toString()
    storeDefaultData(buildSessionID)
    openBuildAnalyzerAction.update(event)
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  fun testActionPerformed() {
    mapDataContext.put(CommonDataKeys.PROJECT, projectRule.project)
    val windowManager = ToolWindowHeadlessManagerImpl(projectRule.project)
    projectRule.replaceProjectService(ToolWindowManager::class.java, windowManager)
    projectRule.replaceProjectService(BuildContentManager::class.java, BuildContentManagerImpl(projectRule.project))
    projectRule.project.getService(BuildContentManager::class.java).addContent(
      ContentImpl(JPanel(), BuildContentManagerImpl.BUILD_TAB_TITLE_SUPPLIER.get(), true)
    )
    val buildSessionID = UUID.randomUUID().toString()
    storeDefaultData(buildSessionID)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    val contentManager = windowManager.getToolWindow(BuildContentManagerImpl.BUILD_TAB_TITLE_SUPPLIER.get())!!.contentManager
    contentManager.removeContent(contentManager.findContent("Build Analyzer"), true)
    assertThat(contentManager.findContent("Build Analyzer")).isNull()
    openBuildAnalyzerAction.actionPerformed(event)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertThat(contentManager.findContent("Build Analyzer")).isNotNull()
  }

  private fun storeDefaultData(buildSessionID : String) {
    BuildAnalyzerStorageManager.getInstance(projectRule.project).storeNewBuildResults(
      BuildEventsAnalyzersProxy(TaskContainer(), PluginContainer()),
      buildSessionID,
      BuildRequestHolder(GradleBuildInvoker.Request
                           .builder(projectRule.project, Projects.getBaseDirPath(projectRule.project), "assembleDebug").build()))
  }
}