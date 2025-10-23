/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade.ui

import com.android.ide.common.repository.AgpVersion
import com.android.testutils.ignore.IgnoreTestRule
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState.Blocked
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState.ReadyToRun
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class UpgradeAssistant90ContentManagerTest {
  val supportedAgpVersion = AgpVersion.parse("8.0.0")
  val latestAgpVersion = AgpVersion.parse("9.0.0")

  @get:Rule
  val projectRule = AndroidProjectRule.withSdk().onEdt()

  @get:Rule
  val ignoreTests = IgnoreTestRule()

  @get:Rule
  val expect = Expect.createAndEnableStackTrace()

  val project by lazy { projectRule.project }

  private val uiStates: MutableList<UIState> = ArrayList()

  private var syncRequest: GradleSyncInvoker.Request? = null

  @Before
  fun replaceSyncInvoker() {
    syncRequest = null
    val ideComponents = IdeComponents(projectRule.fixture)
    val fakeSyncInvoker = object : GradleSyncInvoker.FakeInvoker() {
      override fun requestProjectSync(project: Project, request: GradleSyncInvoker.Request, listener: GradleSyncListener?) {
        syncRequest = request
        super.requestProjectSync(project, request, listener)
      }
    }
    ideComponents.replaceApplicationService(GradleSyncInvoker::class.java, fakeSyncInvoker)

    IdeSdks.getInstance().jdk?.let {
      Disposer.register(projectRule.testRootDisposable) {
        SdkConfigurationUtil.removeSdk(it)
      }
    }
  }

  private fun addMinimalBuildGradleKtsToProject(version: AgpVersion = supportedAgpVersion): PsiFile {
    return projectRule.fixture.addFileToProject(
      "build.gradle.kts",
      """
        plugins {
          id("com.android.application") version "$version" apply false
        }
      """.trimIndent()
    )
  }

  private fun addMinimalGradlePropertiesToProject(): PsiFile {
    return projectRule.fixture.addFileToProject(
      "gradle.properties",
      """
        android.useAndroidX=true
        android.defaults.buildfeatures.shaders=false
        android.defaults.buildfeatures.resvalues=false
        android.sdk.defaultTargetSdkToCompileSdkIfUnset=true
        android.enableAppCompileTimeRClass=true
        android.usesSdkInManifest.disallowed=true
        android.uniquePackageNames=true
        android.dependency.useConstraints=false
        android.r8.strictFullModeForKeepRules=true
        android.r8.optimizedResourceShrinking=true
        android.builtInKotlin=true
        android.newDsl=true
      """.trimIndent()
    )
  }

  private fun UpgradeAssistantWindowModel.listeningStatesChanges() = apply { uiState.addListener { uiStates.add(uiState.get()) } }

  @Test
  fun testToolWindowModelStartsWithLatestAgpVersionSelected() {
    val toolWindowModel = UpgradeAssistantWindowModel(project, { supportedAgpVersion }, latestKnownVersion = latestAgpVersion)
    assertThat(toolWindowModel.selectedVersion).isEqualTo(latestAgpVersion)
  }

  @Test
  fun testToolWindowModelStartsWithValidProcessor() {
    val toolWindowModel = UpgradeAssistantWindowModel(project, { supportedAgpVersion }, latestKnownVersion = latestAgpVersion)
    assertThat(toolWindowModel.processor?.current).isEqualTo(supportedAgpVersion)
    assertThat(toolWindowModel.processor?.new).isEqualTo(latestAgpVersion)
  }

  @Test
  fun testToolWindowModelStartsEnabledWithBuildGradle() {
    addMinimalBuildGradleKtsToProject()
    val toolWindowModel = UpgradeAssistantWindowModel(project, { supportedAgpVersion }, latestKnownVersion = latestAgpVersion)
    assertThat(toolWindowModel.uiState.get()).isEqualTo(ReadyToRun)
  }

  @Test
  fun testToolWindowModelStartsBlockedWithNoFiles() {
    val toolWindowModel = UpgradeAssistantWindowModel(project, { supportedAgpVersion }, latestKnownVersion = latestAgpVersion)
    assertThat(toolWindowModel.uiState.get()).isEqualTo(Blocked)
  }

  @Test
  fun testToolWindowDisplaysUpgradeWithNoFiles() {
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { supportedAgpVersion }, latestKnownVersion = latestAgpVersion)
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    assertThat(treeString(view.tree)).isEqualTo(
      """
        Upgrade
          Disable Usage of AndroidX Libraries
          Enable resValues build feature
          Disable targetSdk defaults to compileSdk
          Disable App Compile-Time R Class
          Continue to allow <uses-sdk> in the main manifest
          Allow non-unique package names
          Enable Dependency Constraints
          Disable R8 Strict Mode for Keep Rules
          Disable R8 Optimized Resource Shrinking
          Disable built-in Kotlin support
          Preserve the old (internal) AGP Dsl APIs
          Upgrade AGP dependency from $supportedAgpVersion to $latestAgpVersion
      """.trimIndent()
    )
  }

  @Test
  fun testToolWindowDisplaysMinimalUpgradeWithMinimalGradleProperties() {
    val contentManager = ContentManagerImpl(project)
    addMinimalGradlePropertiesToProject()
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { supportedAgpVersion }, latestKnownVersion = latestAgpVersion)
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    assertThat(treeString(view.tree)).isEqualTo(
      """
        Upgrade
          Upgrade AGP dependency from $supportedAgpVersion to $latestAgpVersion
      """.trimIndent()
    )
  }

  // TODO(xof): this is a direct copy of the one in UpgradeAssistantContentManagerTest
  private fun treeString(tree: CheckboxTree): String {
    fun addRowText(n: Int, sb: StringBuilder) {
      val path = tree.getPathForRow(n)
      val userObject = (path.lastPathComponent as CheckedTreeNode).userObject
      sb.append("  ".repeat(path.pathCount - 2))
      when (userObject) {
        is UpgradeAssistantWindowModel.DefaultStepPresentation -> sb.append(userObject.treeText)
        is AgpUpgradeComponentNecessity -> sb.append(userObject.treeText())
        else -> sb.append("Unknown entry in tree: $userObject")
      }
    }

    val sb = StringBuilder()
    for (i in 0 until tree.rowCount) {
      addRowText(i, sb)
      if (i < tree.rowCount - 1) sb.append("\n")
    }
    return sb.toString()
  }
}