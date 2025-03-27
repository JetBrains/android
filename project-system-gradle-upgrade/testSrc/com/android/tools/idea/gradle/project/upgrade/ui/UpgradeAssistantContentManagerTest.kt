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
package com.android.tools.idea.gradle.project.upgrade.ui

import com.android.ide.common.repository.AgpVersion
import com.android.testutils.ignore.IgnoreTestRule
import com.android.tools.adtui.HtmlLabel
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.idea.gradle.plugin.AgpVersions
import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.MANDATORY_INDEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_CODEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpUpgradeComponentNecessity.OPTIONAL_INDEPENDENT
import com.android.tools.idea.gradle.project.upgrade.AgpVersionRefactoringProcessor
import com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction
import com.android.tools.idea.gradle.project.upgrade.R8FullModeDefaultRefactoringProcessor.NoPropertyPresentAction
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.Severity
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.StatusMessage
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState.AllDone
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState.Blocked
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState.CaughtException
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState.InvalidVersionError
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState.Loading
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState.NoAGP
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState.NoStepsSelected
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState.ProjectFilesNotCleanWarning
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState.ReadyToRun
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState.RunningBuild
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState.RunningSync
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState.RunningUpgrade
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState.RunningUpgradeSync
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState.UpgradeSyncFailed
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState.UpgradeSyncSucceeded
import com.android.tools.idea.gradle.project.upgrade.ui.UpgradeAssistantWindowModel.UIState.VersionSelectionInProgress
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.gradle.util.CompatibleGradleVersion
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.Jdks
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IdeComponents
import com.android.tools.idea.testing.JdkConstants
import com.android.tools.idea.testing.JdkConstants.JDK_11_PATH
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ui.configuration.SdkListItem
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.components.JBLabel
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

@RunsInEdt
class ContentManagerImplTest {
  val currentAgpVersion = AgpVersion.parse("4.1.0")
  val latestAgpVersion = AgpVersions.latestKnown

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

  private fun addMinimalBuildGradleToProject(): PsiFile {
    return projectRule.fixture.addFileToProject(
      "build.gradle",
      """
        buildscript {
          dependencies {
            classpath 'com.android.tools.build:gradle:$currentAgpVersion'
          }
        }
      """.trimIndent()
    )
  }

  private fun addMinimalGradlePropertiesToProject(): PsiFile {
    return projectRule.fixture.addFileToProject(
      "gradle.properties",
      """
        android.enableR8.fullMode=true
        android.nonTransitiveRClass=true
        android.nonFinalResIds=true
        android.defaults.buildfeatures.buildconfig=false
      """.trimIndent()
    )
  }

  private fun UpgradeAssistantWindowModel.listeningStatesChanges() = apply { uiState.addListener { uiStates.add(uiState.get()) } }

  @Test
  fun testContentManagerConstructable() {
    val contentManager = ContentManagerImpl(project)
  }

  @Test
  fun testContentManagerShowContent() {
    val contentManager = ContentManagerImpl(project)
    contentManager.showContent()
  }

  @Test
  fun testToolWindowModelConstructable() {
    val toolWindowModel = UpgradeAssistantWindowModel(project, { currentAgpVersion })
  }

  @Test
  fun testToolWindowModelStartsWithLatestAgpVersionSelected() {
    val toolWindowModel = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    assertThat(toolWindowModel.selectedVersion).isEqualTo(latestAgpVersion)
  }

  @Test
  fun testToolWindowModelStartsWithValidProcessor() {
    val toolWindowModel = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    assertThat(toolWindowModel.processor?.current).isEqualTo(currentAgpVersion)
    assertThat(toolWindowModel.processor?.new).isEqualTo(latestAgpVersion)
  }

  @Test
  fun testToolWindowModelStartsEnabledWithBuildGradle() {
    addMinimalBuildGradleToProject()
    val toolWindowModel = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    assertThat(toolWindowModel.uiState.get()).isEqualTo(ReadyToRun)
  }

  @Test
  fun testToolWindowModelStartsBlockedWithNoFiles() {
    val toolWindowModel = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    assertThat(toolWindowModel.uiState.get()).isEqualTo(Blocked)
  }

  @Test
  fun testToolWindowModelStartsNoAGPWithNoAGP() {
    val toolWindowModel = UpgradeAssistantWindowModel(project, { null })
    assertThat(toolWindowModel.uiState.get()).isEqualTo(NoAGP)
  }

  @Test
  fun testToolWindowDisplaysUpgradeWithNoFiles() {
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    assertThat(treeString(view.tree)).isEqualTo(
      """
        Upgrade
          Accept the new R8 default of full mode
          Enable buildConfig build feature
          Preserve transitive R classes
          Preserve constant R class values
          Upgrade AGP dependency from $currentAgpVersion to $latestAgpVersion
      """.trimIndent()
    )
  }

  @Test
  fun testToolWindowModelStartsBlockedWithUnsupportedDependency() {
    projectRule.fixture.addFileToProject(
      "build.gradle",
      """
        buildscript {
          dependencies {
            classpath deps.ANDROID_GRADLE_PLUGIN
          }
        }
      """.trimIndent()
    )
    val toolWindowModel = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    assertThat(toolWindowModel.uiState.get()).isEqualTo(Blocked)
  }

  @Test
  fun testToolWindowDisplaysUpgradeWithUnsupportedDependency() {
    val contentManager = ContentManagerImpl(project)
    projectRule.fixture.addFileToProject(
      "build.gradle",
      """
        buildscript {
          dependencies {
            classpath deps.ANDROID_GRADLE_PLUGIN
          }
        }
      """.trimIndent()
    )
    addMinimalGradlePropertiesToProject()
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    assertThat(treeString(view.tree)).isEqualTo(
      """
        Upgrade
          Upgrade AGP dependency from $currentAgpVersion to $latestAgpVersion
      """.trimIndent()
    )
  }

  @Test
  fun testToolWindowModelStartsInAllDoneWithNoFilesForNullUpgrade() {
    val toolWindowModel = UpgradeAssistantWindowModel(project, { latestAgpVersion })
    assertThat(toolWindowModel.uiState.get()).isEqualTo(AllDone)
  }

  @Test
  fun testToolWindowTreeIsEmptyWithNoFilesForNullUpgrade() {
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { latestAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    assertThat(treeString(view.tree)).isEmpty()
  }

  @Test
  fun testToolWindowModelStartsInAllDoneWithUnrecognizedDependencyForNullUpgrade() {
    projectRule.fixture.addFileToProject(
      "build.gradle",
      """
        buildscript {
          dependencies {
            classpath deps.ANDROID_GRADLE_PLUGIN
          }
        }
      """.trimIndent()
    )
    val toolWindowModel = UpgradeAssistantWindowModel(project, { latestAgpVersion })
    assertThat(toolWindowModel.uiState.get()).isEqualTo(AllDone)
  }

  @Test
  fun testToolWindowTreeIsEmptyWithUnrecognizedDependencyForNullUpgrade() {
    val contentManager = ContentManagerImpl(project)
    projectRule.fixture.addFileToProject(
      "build.gradle",
      """
        buildscript {
          dependencies {
            classpath deps.ANDROID_GRADLE_PLUGIN
          }
        }
      """.trimIndent()
    )
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { latestAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    assertThat(treeString(view.tree)).isEmpty()
  }

  @Test
  fun testVersionUpdatedFromComboBox() {
    addMinimalBuildGradleToProject()
    val toolWindowModel = UpgradeAssistantWindowModel(project, { currentAgpVersion }).listeningStatesChanges()
    toolWindowModel.versionComboTextChanged()
    toolWindowModel.newVersionCommit(latestAgpVersion.toString())
    assertThat(uiStates).containsExactly(VersionSelectionInProgress, Loading, ReadyToRun).inOrder()
  }

  @Test
  fun testToolWindowModelUIStateOnFailedValidation() {
    val toolWindowModel = UpgradeAssistantWindowModel(project, { currentAgpVersion }).listeningStatesChanges()
    toolWindowModel.versionComboTextChanged()
    toolWindowModel.newVersionCommit("")
    // The following order might be not obvious but in fact it is correct:
    // Firstly version parser validates new version and sets the error,
    // Then UI is cleared showing Loading state,
    // Finally refresh logic results back in InvalidVersionError state.
    assertThat(uiStates).containsExactly(
      VersionSelectionInProgress,
      InvalidVersionError(StatusMessage(Severity.ERROR, "Invalid AGP version format.")),
      Loading,
      InvalidVersionError(StatusMessage(Severity.ERROR, "Invalid AGP version format."))
    ).inOrder()
  }

  @Test
  fun testTreeModelInitialState() {
    addMinimalBuildGradleToProject()
    addMinimalGradlePropertiesToProject()
    val toolWindowModel = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    val treeModel = toolWindowModel.treeModel
    val root = treeModel.root as? CheckedTreeNode
    assertThat(root).isInstanceOf(CheckedTreeNode::class.java)
    assertThat(root!!.childCount).isEqualTo(1)
    val mandatoryCodependent = root.firstChild as CheckedTreeNode
    assertThat(mandatoryCodependent.userObject).isEqualTo(MANDATORY_CODEPENDENT)
    assertThat(mandatoryCodependent.isEnabled).isTrue()
    assertThat(mandatoryCodependent.isChecked).isTrue()
    assertThat(mandatoryCodependent.childCount).isEqualTo(1)
    val step = mandatoryCodependent.lastChild as CheckedTreeNode
    assertThat(step.isEnabled).isFalse()
    assertThat(step.isChecked).isTrue()
    val stepPresentation = step.userObject as UpgradeAssistantWindowModel.DefaultStepPresentation
    assertThat(stepPresentation.processor).isInstanceOf(AgpVersionRefactoringProcessor::class.java)
    assertThat(stepPresentation.treeText).contains("Upgrade AGP dependency from $currentAgpVersion to $latestAgpVersion")
  }

  @Test
  fun testToolWindowView() {
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
  }

  @Test
  fun testToolWindowViewHasExpandedTree() {
    addMinimalBuildGradleToProject()
    addMinimalGradlePropertiesToProject()
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    assertThat(treeString(view.tree)).isEqualTo("""
      Upgrade
        Upgrade AGP dependency from 4.1.0 to $latestAgpVersion
    """.trimIndent())
  }

  @Test
  fun testToolWindowViewDisablingNodeDisablesChild() {
    addMinimalBuildGradleToProject()
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    val mandatoryCodependentNode = view.tree.getPathForRow(0).lastPathComponent as CheckedTreeNode
    assertThat(mandatoryCodependentNode.isChecked).isTrue()
    view.tree.setNodeState(mandatoryCodependentNode, false)
    assertThat(mandatoryCodependentNode.isChecked).isFalse()
    val agpVersionRefactoringProcessorNode = mandatoryCodependentNode.firstChild as CheckedTreeNode
    assertThat(agpVersionRefactoringProcessorNode.isChecked).isFalse()
    assertThat(agpVersionRefactoringProcessorNode.isEnabled).isFalse()
  }

  @Test
  fun testToolWindowViewAllDoneDetailsPanel() {
    addMinimalBuildGradleToProject()
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion }, currentAgpVersion)
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    val detailsPanelContent = TreeWalker(view.detailsPanel).descendants().first { it.name == "content" } as HtmlLabel
    assertThat(detailsPanelContent.text).contains("<b>Up-to-date for Android Gradle Plugin version $currentAgpVersion</b>")
    assertThat(detailsPanelContent.text).contains("Upgrades to newer versions")
  }

  @Test
  fun testToolWindowViewUpgradeToStableDetailsPanel() {
    addMinimalBuildGradleToProject()
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion }, AgpVersion.parse("4.1.1"))
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    val detailsPanelContent = TreeWalker(view.detailsPanel).descendants().first { it.name == "content" } as HtmlLabel
    assertThat(detailsPanelContent.text).contains("<b>Updates available</b>")
    assertThat(detailsPanelContent.text).contains("To take advantage of the latest features, improvements and fixes")
    val linkContent = TreeWalker(view.detailsPanel).descendants().first { it.name == "browse release notes link" } as BrowserLink
    assertThat(linkContent.url).isEqualTo("https://developer.android.com/studio/releases/gradle-plugin")
    assertThat(linkContent.text).isEqualTo("release notes")
  }

  @Test
  fun testToolWindowViewUpgradeToPreviewDetailsPanel() {
    addMinimalBuildGradleToProject()
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion }, AgpVersion.parse("4.2.0-alpha01"))
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    val detailsPanelContent = TreeWalker(view.detailsPanel).descendants().first { it.name == "content" } as HtmlLabel
    assertThat(detailsPanelContent.text).contains("<b>Updates available</b>")
    assertThat(detailsPanelContent.text).contains("To take advantage of the latest features, improvements and fixes")
    val linkContent = TreeWalker(view.detailsPanel).descendants().first { it.name == "browse release notes link" } as BrowserLink
    assertThat(linkContent.url).isEqualTo("https://developer.android.com/studio/preview/features")
    assertThat(linkContent.text).isEqualTo("preview release notes")
  }

  @Test
  fun testToolWindowViewMandatoryCodependentDetailsPanel() {
    addMinimalBuildGradleToProject()
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    view.tree.selectionPath = view.tree.getPathForRow(0)
    val detailsPanelContent = TreeWalker(view.detailsPanel).descendants().first { it.name == "content" } as HtmlLabel
    assertThat(detailsPanelContent.text).contains("<b>Upgrade</b>")
    assertThat(detailsPanelContent.text).contains("at the same time")
  }

  @Test
  fun testToolWindowViewClasspathProcessorDetailsPanel() {
    addMinimalBuildGradleToProject()
    addMinimalGradlePropertiesToProject()
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    view.tree.selectionPath = view.tree.getPathForRow(1)
    val detailsPanelContent = TreeWalker(view.detailsPanel).descendants().first { it.name == "content" } as HtmlLabel
    assertThat(detailsPanelContent.text).contains("<b>Upgrade AGP dependency from $currentAgpVersion to $latestAgpVersion</b>")
    assertThat(detailsPanelContent.text).doesNotContain("This step is blocked")
  }

  @Test
  fun testToolWindowViewClasspathProcessorBlockedDetailsPanel() {
    addMinimalGradlePropertiesToProject()
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    view.tree.selectionPath = view.tree.getPathForRow(1)
    val detailsPanelContent = TreeWalker(view.detailsPanel).descendants().first { it.name == "content" } as HtmlLabel
    assertThat(detailsPanelContent.text).contains("<b>Upgrade AGP dependency from $currentAgpVersion to $latestAgpVersion</b>")
    assertThat(detailsPanelContent.text).contains("This step is blocked")
    val textNoNewLines = detailsPanelContent.text.replace("<br>", "\n").replace("\\s+".toRegex(), " ")
    assertThat(textNoNewLines).contains("The upgrade assistant is unable to upgrade this project." +
                                        " You can upgrade AGP by manually completing the list of required upgrade steps.")
    assertThat(detailsPanelContent.text).contains("https://developer.android.com/r/tools/upgrade-assistant/agp-version-not-found")
  }

  @Test
  fun testToolWindowViewDetailsPanelWithJava8() {
    // Note that this isn't actually a well-formed build.gradle file, but is constructed to activate both the classpath
    // and the Java8 refactoring processors without needing a full project.
    projectRule.fixture.addFileToProject(
      "build.gradle",
      """
        plugins {
          id 'com.android.application'
        }
        buildscript {
          dependencies {
            classpath 'com.android.tools.build:gradle:4.1.0'
          }
        }
        android {
        }
      """.trimIndent()
    )
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    val java8ProcessorPath = view.tree.getPathForRow(1)
    view.tree.selectionPath = java8ProcessorPath
    val stepPresentation = (java8ProcessorPath.lastPathComponent as CheckedTreeNode).userObject as UpgradeAssistantWindowModel.StepUiPresentation
    assertThat(stepPresentation.treeText).isEqualTo("Accept the new default of Java 8")
    val detailsPanelContent = TreeWalker(view.detailsPanel).descendants().first { it.name == "content" } as HtmlLabel
    assertThat(detailsPanelContent.text).contains("<b>Update default Java language level</b>")
    assertThat(detailsPanelContent.text).contains("explicit Language Level directives")
    val label = TreeWalker(view.detailsPanel).descendants().first { it.name == "label" } as JBLabel
    val comboBox = TreeWalker(view.detailsPanel).descendants().first { it.name == "selection" } as ComboBox<*>
    assertThat(label.text).contains("Action on no explicit Java language level")
    assertThat(comboBox.selectedItem).isEqualTo(NoLanguageLevelAction.ACCEPT_NEW_DEFAULT)
    comboBox.selectedItem = NoLanguageLevelAction.INSERT_OLD_DEFAULT
    assertThat(stepPresentation.treeText).isEqualTo("Insert directives to continue using Java 7")
  }

  @Test
  fun testToolWindowViewDetailsPanelWithR8FullMode() {
    projectRule.fixture.addFileToProject(
      "build.gradle",
      """
        buildscript {
          dependencies {
            classpath 'com.android.tools.build:gradle:7.3.0'
          }
        }
      """.trimIndent()
    )
    projectRule.fixture.addFileToProject(
      "gradle.properties", ""
    )
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { AgpVersion.parse("7.3.0") }, AgpVersion.parse("8.0.0"))
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    val r8FullModeProcessorPath = view.tree.getPathForRow(1)
    view.tree.selectionPath = r8FullModeProcessorPath
    val stepPresentation = (r8FullModeProcessorPath.lastPathComponent as CheckedTreeNode).userObject as UpgradeAssistantWindowModel.StepUiPresentation
    assertThat(stepPresentation.treeText).isEqualTo("Accept the new R8 default of full mode")
    val detailsPanelContent = TreeWalker(view.detailsPanel).descendants().first { it.name == "content" } as HtmlLabel
    assertThat(detailsPanelContent.text).contains("<b>Update default R8 processing mode</b>")
    assertThat(detailsPanelContent.text).contains("set explicitly in the gradle.properties file.")
    val label = TreeWalker(view.detailsPanel).descendants().first { it.name == "label" } as JBLabel
    val comboBox = TreeWalker(view.detailsPanel).descendants().first { it.name == "selection" } as ComboBox<*>
    assertThat(label.text).contains("Action on no android.enableR8.fullMode property")
    assertThat(comboBox.selectedItem).isEqualTo(NoPropertyPresentAction.ACCEPT_NEW_DEFAULT)
    comboBox.selectedItem = NoPropertyPresentAction.INSERT_OLD_DEFAULT
    assertThat(stepPresentation.treeText).isEqualTo("Insert property to continue using R8 in compat mode")
  }

  @Test
  fun testToolWindowViewDetailsPanelWithOldKotlinPlugin() {
    projectRule.fixture.addFileToProject(
      "build.gradle",
      """
        buildscript {
          dependencies {
            classpath 'com.android.tools.build:gradle:4.1.0'
            classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.11'
          }
        }
      """.trimIndent()
    )
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    val gradleVersionProcessorPath = view.tree.getPathForRow(1)
    view.tree.selectionPath = gradleVersionProcessorPath
    val stepPresentation = (gradleVersionProcessorPath.lastPathComponent as CheckedTreeNode)
      .userObject as UpgradeAssistantWindowModel.StepUiPresentation
    assertThat(stepPresentation.treeText).contains("Upgrade Gradle plugins")
    val detailsPanelContent = TreeWalker(view.detailsPanel).descendants().first { it.name == "content" } as HtmlLabel
    assertThat(detailsPanelContent.text).contains("<b>Upgrade Gradle plugins")
    assertThat(detailsPanelContent.text).contains("The following Gradle plugin versions will be updated:")
    assertThat(detailsPanelContent.text).contains("Update version of org.jetbrains.kotlin:kotlin-gradle-plugin to")
  }

  @Test
  fun testToolWindowViewDetailsPanelWithNewishKotlinPlugin() {
    projectRule.fixture.addFileToProject(
      "build.gradle",
      """
        buildscript {
          dependencies {
            classpath 'com.android.tools.build:gradle:4.1.0'
            classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.21'
          }
        }
      """.trimIndent()
    )
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    val gradleVersionProcessorPath = view.tree.getPathForRow(1)
    view.tree.selectionPath = gradleVersionProcessorPath
    val stepPresentation = (gradleVersionProcessorPath.lastPathComponent as CheckedTreeNode)
      .userObject as UpgradeAssistantWindowModel.StepUiPresentation
    assertThat(stepPresentation.treeText).doesNotContain("Upgrade Gradle plugins")
  }

  @Test
  fun testToolWindowViewDetailsPanelWithOldJdk() {
    projectRule.fixture.addFileToProject(
      "build.gradle",
      """
        buildscript {
          dependencies {
            classpath 'com.android.tools.build:gradle:7.3.0'
          }
        }
      """.trimIndent()
    )
    mock<GradleInstallationManager>().let {
      whenever(it.getGradleJvmPath(any(), any())).thenReturn(JDK_11_PATH)
      projectRule.projectRule.replaceService(GradleInstallationManager::class.java, it)
    }
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!

    // Make sure JDK 17 is available even if the test is run with a different version so refactorings are not blocked
    val jdk17 = Jdks.getInstance().createAndAddJdk(JdkConstants.JDK_17_PATH)
    assertThat(jdk17).isNotNull()
    try {
      val model = UpgradeAssistantWindowModel(project, { AgpVersion.parse("7.3.0") }, AgpVersion.parse("8.0.0"))
      val view = UpgradeAssistantView(model, toolWindow.contentManager)

      assertThat(treeString(view.tree)).isEqualTo(
        """
          Upgrade
            Upgrade project JDK for running Gradle
            Accept the new R8 default of full mode
            Enable buildConfig build feature
            Preserve transitive R classes
            Preserve constant R class values
            Upgrade AGP dependency from 7.3.0 to 8.0.0
      """.trimIndent()
      )

      val projectJdkProcessorPath = view.tree.getPathForRow(1)
      view.tree.selectionPath = projectJdkProcessorPath
      val stepPresentation = (projectJdkProcessorPath.lastPathComponent as CheckedTreeNode).userObject as UpgradeAssistantWindowModel.StepUiPresentation
      assertThat(stepPresentation.treeText).isEqualTo("Upgrade project JDK for running Gradle")
      assertThat(stepPresentation.isBlocked).isFalse()
      val detailsPanelContent = TreeWalker(view.detailsPanel).descendants().first { it.name == "content" } as HtmlLabel
      assertThat(detailsPanelContent.text).contains("<b>Upgrade project JDK for running Gradle</b>")
      assertThat(detailsPanelContent.text).contains("JDK version 17")
      val comboBox = TreeWalker(view.detailsPanel).descendants().first { it.name == "selection" } as ComboBox<*>
      assertThat((comboBox.selectedItem as SdkListItem.SdkItem).sdk.name).isEqualTo("jbr-17")
      comboBox.selectedItem = comboBox.getItemAt(0) // "app_jdk" which is, oddly, Java 1.5
      assertThat(stepPresentation.treeText).isEqualTo("Upgrade project JDK for running Gradle")
      assertThat(stepPresentation.isBlocked).isTrue()
    }
    finally {
      if (jdk17 != null) {
        runWriteAction {
          ProjectJdkTable.getInstance().removeJdk(jdk17)
        }
      }
    }
  }

  @Test
  fun testToolWindowViewWithGradleAndPluginUpgrades() {
    projectRule.fixture.addFileToProject(
      "build.gradle",
      """
        buildscript {
          dependencies {
            classpath 'com.android.tools.build:gradle:3.6.0'
            classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.10'
          }
        }
      """.trimIndent()
    )
    projectRule.fixture.addFileToProject(
      "gradle/wrapper/gradle-wrapper.properties",
      """
        distributionBase=GRADLE_USER_HOME
        distributionPath=wrapper/dists
        zipStoreBase=GRADLE_USER_HOME
        zipStorePath=wrapper/dists
        distributionUrl=https\://services.gradle.org/distributions/gradle-6.1.1-bin.zip
      """.trimIndent()
    )
    addMinimalGradlePropertiesToProject()
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    assertThat(treeString(view.tree)).isEqualTo("""
      Upgrade
        Upgrade Gradle version to ${CompatibleGradleVersion.getCompatibleGradleVersion(latestAgpVersion).version.version}
        Upgrade Gradle plugins
        Upgrade AGP dependency from 4.1.0 to $latestAgpVersion
    """.trimIndent())
  }

  @Test
  fun testToolWindowViewHasEnabledButtons() {
    addMinimalBuildGradleToProject()
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    assertThat(view.okButton.isEnabled).isTrue()
    assertThat(view.okButton.text).isEqualTo("Run selected steps")
    assertThat(view.previewButton.isEnabled).isTrue()
    assertThat(view.previewButton.text).isEqualTo("Show Usages")
    assertThat(view.refreshButton.isEnabled).isTrue()
    assertThat(view.refreshButton.text).isEqualTo("Refresh")
  }

  @Test
  fun testToolWindowOKButtonsAreDisabledWithNoFiles() {
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    assertThat(view.okButton.isEnabled).isFalse()
    assertThat(view.okButton.text).isEqualTo("Run selected steps")
    assertThat(view.previewButton.isEnabled).isTrue()
    assertThat(view.previewButton.text).isEqualTo("Show Usages")
    assertThat(view.refreshButton.isEnabled).isTrue()
    assertThat(view.refreshButton.text).isEqualTo("Refresh")
  }

  @Test
  fun testToolWindowOKButtonsAreDisabledWithNothingSelected() {
    addMinimalBuildGradleToProject()
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    val mandatoryCodependentNode = view.tree.getPathForRow(0).lastPathComponent as CheckedTreeNode
    assertThat(mandatoryCodependentNode.userObject).isEqualTo(MANDATORY_CODEPENDENT)
    assertThat(mandatoryCodependentNode.isChecked).isTrue()
    assertThat(view.okButton.isEnabled).isTrue()
    assertThat(view.previewButton.isEnabled).isTrue()
    assertThat(view.refreshButton.isEnabled).isTrue()
    assertThat(view.versionTextField.isEnabled).isTrue()
    view.tree.setNodeState(mandatoryCodependentNode, false)
    assertThat(view.okButton.isEnabled).isFalse()
    assertThat(view.previewButton.isEnabled).isFalse()
    assertThat(view.refreshButton.isEnabled).isTrue()
    assertThat(view.versionTextField.isEnabled).isTrue()
  }

  @Test
  fun testToolWindowInitialStateForOptionalSteps() {
    addMinimalBuildGradleToProject()
    projectRule.fixture.addFileToProject("settings.gradle", "include ':app'")
    projectRule.fixture.addFileToProject("app/build.gradle", """
      plugins {
        id 'com.android.application'
      }

      dependencies {
        compile 'com.example:foo:1.0'
      }
    """.trimIndent())
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion }, AgpVersion.parse("4.2.0-alpha07"))
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    val mandatoryCodependentNode = view.tree.getPathForRow(0).lastPathComponent as CheckedTreeNode
    assertThat(mandatoryCodependentNode.userObject).isEqualTo(MANDATORY_CODEPENDENT)
    assertThat(mandatoryCodependentNode.isChecked).isTrue()
    val optionalIndependentNode = view.tree.getPathForRow(3).lastPathComponent as CheckedTreeNode
    assertThat(optionalIndependentNode.userObject).isEqualTo(OPTIONAL_INDEPENDENT)
    val deprecatedConfigurationsNode = view.tree.getPathForRow(4).lastPathComponent as CheckedTreeNode
    val deprecatedConfigurationsUIPresentation = deprecatedConfigurationsNode.userObject as UpgradeAssistantWindowModel.StepUiPresentation
    assertThat(deprecatedConfigurationsNode.isChecked).isFalse()
    assertThat(deprecatedConfigurationsUIPresentation.treeText).isEqualTo("Replace deprecated configurations")
    assertThat(view.okButton.isEnabled).isTrue()
    assertThat(view.previewButton.isEnabled).isTrue()
    assertThat(view.refreshButton.isEnabled).isTrue()
    assertThat(view.versionTextField.isEnabled).isTrue()
    view.tree.setNodeState(mandatoryCodependentNode, false)
    assertThat(view.okButton.isEnabled).isFalse()
    assertThat(view.previewButton.isEnabled).isFalse()
    assertThat(view.refreshButton.isEnabled).isTrue()
    assertThat(view.versionTextField.isEnabled).isTrue()
  }

  @Test
  fun testToolWindowInitialStateForSameVersionUpgrade() {
    addMinimalBuildGradleToProject()
    projectRule.fixture.addFileToProject("settings.gradle", "include ':app'")
    projectRule.fixture.addFileToProject("app/build.gradle", """
      plugins {
        id 'com.android.application'
      }

      dependencies {
        compile 'com.example:foo:1.0'
      }
    """.trimIndent())
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion }, currentAgpVersion)
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    val optionalIndependentNode = view.tree.getPathForRow(0).lastPathComponent as CheckedTreeNode
    assertThat(optionalIndependentNode.userObject).isEqualTo(OPTIONAL_INDEPENDENT)
    assertThat(optionalIndependentNode.isChecked).isTrue()
    assertThat(view.okButton.isEnabled).isTrue()
    assertThat(view.previewButton.isEnabled).isTrue()
    assertThat(view.refreshButton.isEnabled).isTrue()
    assertThat(view.versionTextField.isEnabled).isTrue()
    view.tree.setNodeState(optionalIndependentNode, false)
    assertThat(view.okButton.isEnabled).isFalse()
    assertThat(view.previewButton.isEnabled).isFalse()
    assertThat(view.refreshButton.isEnabled).isTrue()
    assertThat(view.versionTextField.isEnabled).isTrue()
  }

  @Test
  fun testToolWindowDropdownInitializedWithCurrentAndLatest() {
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion }, newProjectVersion = currentAgpVersion) { setOf() }
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    assertThat(view.versionTextField.model.selectedItem).isEqualTo(latestAgpVersion)
    assertThat(view.versionTextField.model.size).isEqualTo(1)
    assertThat(view.versionTextField.model.getElementAt(0)).isEqualTo(currentAgpVersion)
  }

  @Test
  fun testSuccessfulSyncWithoutRunningProcessor() {
    val psiFile = addMinimalBuildGradleToProject()
    var changingCurrentAgpVersion = currentAgpVersion
    val toolWindowModel = UpgradeAssistantWindowModel(project, { changingCurrentAgpVersion }).listeningStatesChanges()

    toolWindowModel.syncStarted(project)
    toolWindowModel.syncSucceeded(project)
    assertThat(psiFile.text).contains("classpath 'com.android.tools.build:gradle:$currentAgpVersion")
    assertThat(uiStates).containsExactly(RunningSync, Loading, ReadyToRun).inOrder()
  }

  @Test
  fun testFailingSyncWithoutRunningProcessor() {
    val psiFile = addMinimalBuildGradleToProject()
    var changingCurrentAgpVersion = currentAgpVersion
    val toolWindowModel = UpgradeAssistantWindowModel(project, { changingCurrentAgpVersion }).listeningStatesChanges()

    toolWindowModel.syncStarted(project)
    toolWindowModel.syncFailed(project, "External Sync Failure")
    assertThat(psiFile.text).contains("classpath 'com.android.tools.build:gradle:$currentAgpVersion")
    assertThat(uiStates).containsExactly(RunningSync, Loading, ReadyToRun).inOrder()
  }

  @Test
  fun testRunProcessor() {
    val psiFile = addMinimalBuildGradleToProject()
    var changingCurrentAgpVersion = currentAgpVersion
    val toolWindowModel = UpgradeAssistantWindowModel(project, { changingCurrentAgpVersion }).listeningStatesChanges()

    toolWindowModel.runUpgrade(false)
    toolWindowModel.syncStarted(project)
    changingCurrentAgpVersion = latestAgpVersion
    toolWindowModel.syncSucceeded(project)
    assertThat(psiFile.text).contains("classpath 'com.android.tools.build:gradle:$latestAgpVersion")
    assertThat(uiStates).containsExactly(RunningUpgrade, RunningUpgradeSync, UpgradeSyncSucceeded).inOrder()
  }

  @Test
  fun testRunProcessorWithoutWritePermission() {
    val psiFile = addMinimalBuildGradleToProject()
    var changingCurrentAgpVersion = currentAgpVersion
    val toolWindowModel = UpgradeAssistantWindowModel(project, { changingCurrentAgpVersion }).listeningStatesChanges()

    runWriteAction { psiFile.virtualFile.isWritable = false }

    toolWindowModel.runUpgrade(false)
    changingCurrentAgpVersion = latestAgpVersion
    assertThat(psiFile.text).contains("classpath 'com.android.tools.build:gradle:$currentAgpVersion")
    assertThat(uiStates).hasSize(2)
    assertThat(uiStates[0]).isEqualTo(RunningUpgrade)
    assertThat(uiStates[1]).isInstanceOf(CaughtException::class.java)
  }

  @Test
  fun testRunProcessorSyncFailure() {
    val psiFile = addMinimalBuildGradleToProject()
    var changingCurrentAgpVersion = currentAgpVersion
    val toolWindowModel = UpgradeAssistantWindowModel(project, { changingCurrentAgpVersion }).listeningStatesChanges()

    toolWindowModel.runUpgrade(false)
    toolWindowModel.syncStarted(project)
    changingCurrentAgpVersion = latestAgpVersion
    toolWindowModel.syncFailed(project, "ContentManagerImplTest failure")
    assertThat(psiFile.text).contains("classpath 'com.android.tools.build:gradle:$latestAgpVersion")
    assertThat(uiStates).containsExactly(RunningUpgrade, RunningUpgradeSync, UpgradeSyncFailed("ContentManagerImplTest failure")).inOrder()
  }

  @Test
  fun testRefreshAfterRunProcessor() {
    val psiFile = addMinimalBuildGradleToProject()
    var changingCurrentAgpVersion = currentAgpVersion
    val toolWindowModel = UpgradeAssistantWindowModel(project, { changingCurrentAgpVersion }).listeningStatesChanges()

    toolWindowModel.runUpgrade(false)
    toolWindowModel.syncStarted(project)
    changingCurrentAgpVersion = latestAgpVersion
    toolWindowModel.syncSucceeded(project)
    assertThat(psiFile.text).contains("classpath 'com.android.tools.build:gradle:$latestAgpVersion")
    toolWindowModel.refresh(true)
    assertThat(uiStates)
      .containsExactly(RunningUpgrade, RunningUpgradeSync, UpgradeSyncSucceeded, Loading, AllDone).inOrder()
  }

  @Test
  fun testManualSyncAfterRunProcessor() {
    val psiFile = addMinimalBuildGradleToProject()
    var changingCurrentAgpVersion = currentAgpVersion
    val toolWindowModel = UpgradeAssistantWindowModel(project, { changingCurrentAgpVersion }).listeningStatesChanges()

    toolWindowModel.runUpgrade(false)
    toolWindowModel.syncStarted(project)
    changingCurrentAgpVersion = latestAgpVersion
    toolWindowModel.syncSucceeded(project)
    assertThat(psiFile.text).contains("classpath 'com.android.tools.build:gradle:$latestAgpVersion")
    toolWindowModel.syncStarted(project)
    toolWindowModel.syncSucceeded(project)
    assertThat(uiStates)
      .containsExactly(RunningUpgrade, RunningUpgradeSync, UpgradeSyncSucceeded, RunningSync, Loading, AllDone).inOrder()
  }

  @Test
  fun testRevertAfterRunProcessor() {
    val psiFile = addMinimalBuildGradleToProject()
    var changingCurrentAgpVersion = currentAgpVersion
    val toolWindowModel = UpgradeAssistantWindowModel(project, { changingCurrentAgpVersion }).listeningStatesChanges()

    toolWindowModel.runUpgrade(false)
    assertThat(syncRequest).isNotNull()
    assertThat(syncRequest!!.trigger).isEqualTo(GradleSyncStats.Trigger.TRIGGER_AGP_VERSION_UPDATED)

    toolWindowModel.syncStarted(project)
    changingCurrentAgpVersion = latestAgpVersion
    toolWindowModel.syncSucceeded(project)

    syncRequest = null
    toolWindowModel.runRevert()
    // Need to commit so that pending changes from Vfs are propagated to Psi.
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    assertThat(psiFile.text).contains("classpath 'com.android.tools.build:gradle:$currentAgpVersion")
    assertThat(syncRequest).isNotNull()
    assertThat(syncRequest!!.trigger).isEqualTo(GradleSyncStats.Trigger.TRIGGER_AGP_VERSION_UPDATE_ROLLED_BACK)

    toolWindowModel.syncStarted(project)
    changingCurrentAgpVersion = currentAgpVersion
    toolWindowModel.syncSucceeded(project)

    assertThat(uiStates).containsExactly(RunningUpgrade, RunningUpgradeSync, UpgradeSyncSucceeded, RunningSync, Loading,
                                         ReadyToRun).inOrder()
  }

  @Test
  fun testNecessityTreeText() {
    assertThat(MANDATORY_INDEPENDENT.treeText()).isEqualTo("Upgrade prerequisites")
    assertThat(MANDATORY_CODEPENDENT.treeText()).isEqualTo("Upgrade")
    assertThat(OPTIONAL_CODEPENDENT.treeText()).isEqualTo("Recommended post-upgrade steps")
    assertThat(OPTIONAL_INDEPENDENT.treeText()).isEqualTo("Recommended steps")
  }

  @Test
  fun testNecessityDescription() {
    fun AgpUpgradeComponentNecessity.descriptionString(): String = this.description().replace("\n", " ")
    assertThat(MANDATORY_INDEPENDENT.descriptionString()).contains("are required")
    assertThat(MANDATORY_INDEPENDENT.descriptionString()).contains("separate steps")
    assertThat(MANDATORY_CODEPENDENT.descriptionString()).contains("are required")
    assertThat(MANDATORY_CODEPENDENT.descriptionString()).contains("must all happen together")
    assertThat(OPTIONAL_CODEPENDENT.descriptionString()).contains("are not required")
    assertThat(OPTIONAL_CODEPENDENT.descriptionString()).contains("only if")
    assertThat(OPTIONAL_INDEPENDENT.descriptionString()).contains("are not required")
    assertThat(OPTIONAL_INDEPENDENT.descriptionString()).contains("with or without")
  }

  @Test
  fun testCheckboxTooltipText() {
    assertThat(MANDATORY_INDEPENDENT.checkboxToolTipText(true, false)).isNull()
    assertThat(MANDATORY_CODEPENDENT.checkboxToolTipText(true, false)).isNull()
    assertThat(OPTIONAL_INDEPENDENT.checkboxToolTipText(true, false)).isNull()
    assertThat(OPTIONAL_CODEPENDENT.checkboxToolTipText(true, false)).isNull()
    assertThat(MANDATORY_INDEPENDENT.checkboxToolTipText(false, true)).isEqualTo("Cannot be deselected while Upgrade is selected")
    assertThat(MANDATORY_CODEPENDENT.checkboxToolTipText(false, false)).isEqualTo(
      "Cannot be selected while Upgrade prerequisites is unselected")
    assertThat(MANDATORY_CODEPENDENT.checkboxToolTipText(false, true)).isEqualTo(
      "Cannot be deselected while Recommended post-upgrade steps is selected")
    assertThat(OPTIONAL_CODEPENDENT.checkboxToolTipText(false, false)).isEqualTo("Cannot be selected while Upgrade is unselected")
  }

  @Test
  fun testUpgradeLabelText() {
    assertThat((null as AgpVersion?).upgradeLabelText()).contains("unknown version")
    assertThat(AgpVersion.parse("4.1.0").upgradeLabelText()).contains("version 4.1.0")
  }

  @Test
  fun testContentDisplayName() {
    assertThat((null as AgpVersion?).contentDisplayName()).contains("unknown AGP")
    assertThat(AgpVersion.parse("4.1.0").contentDisplayName()).contains("AGP 4.1.0")
  }

  @Test
  fun testSuggestedVersions() {
    val toolWindowModel = UpgradeAssistantWindowModel(project, { currentAgpVersion }, newProjectVersion = currentAgpVersion)
    val knownVersions = listOf("4.1.0", "20000.1.0").map { AgpVersion.parse(it) }.toSet()
    val suggestedVersions = toolWindowModel.suggestedVersionsList(knownVersions)
    assertThat(suggestedVersions).isEqualTo(listOf(currentAgpVersion))
  }

  @Test
  fun testSuggestedVersionsLatestExplicitlyKnown() {
    val toolWindowModel = UpgradeAssistantWindowModel(project, { currentAgpVersion }, newProjectVersion = currentAgpVersion)
    val knownVersions = listOf("4.1.0", "20000.1.0").map { AgpVersion.parse(it) }.toSet().union(setOf(latestAgpVersion))
    val suggestedVersions = toolWindowModel.suggestedVersionsList(knownVersions)
    assertThat(suggestedVersions).isEqualTo(listOf(latestAgpVersion, currentAgpVersion))
  }

  @Test
  fun testSuggestedVersionsAlreadyAtLatestVersionExplicitlyKnown() {
    val toolWindowModel = UpgradeAssistantWindowModel(project, { latestAgpVersion })
    val knownVersions = listOf("4.1.0", "20000.1.0").map { AgpVersion.parse(it) }.toSet().union(setOf(latestAgpVersion))
    val suggestedVersions = toolWindowModel.suggestedVersionsList(knownVersions)
    assertThat(suggestedVersions).isEqualTo(listOf(latestAgpVersion))
  }

  @Test
  fun testSuggestedVersionsAlreadyAtLatestVersionExplicitlyUnknown() {
    val toolWindowModel = UpgradeAssistantWindowModel(project, { latestAgpVersion })
    val knownVersions = listOf("4.1.0", "20000.1.0").map { AgpVersion.parse(it) }.toSet()
    val suggestedVersions = toolWindowModel.suggestedVersionsList(knownVersions)
    assertThat(suggestedVersions).isEqualTo(listOf(latestAgpVersion))
  }

  @Test
  fun testSuggestedVersionsEmptyWhenCurrentUnknown() {
    val toolWindowModel = UpgradeAssistantWindowModel(project, { null })
    val knownVersions = listOf("4.1.0", "20000.1.0").map { AgpVersion.parse(it) }.toSet().union(setOf(latestAgpVersion))
    val suggestedVersions = toolWindowModel.suggestedVersionsList(knownVersions)
    assertThat(suggestedVersions).isEqualTo(listOf<AgpVersion>())
  }

  @Test
  fun testSuggestedVersionsDoesNotIncludeForcedUpgrades() {
    val toolWindowModel = UpgradeAssistantWindowModel(project, { currentAgpVersion }, newProjectVersion = AgpVersion.parse("4.0.0"))
    val knownVersions = listOf("4.1.0", "4.2.0-dev", "4.2.0").map { AgpVersion.parse(it) }.toSet()
    val suggestedVersions = toolWindowModel.suggestedVersionsList(knownVersions)
    assertThat(suggestedVersions)
      .isEqualTo(setOf(currentAgpVersion, AgpVersion.parse("4.2.0")).toList().sortedDescending())
  }

  @Test
  fun `test suggested versions does not include current alpha if not published`() {
    val latestKnownNotPublished = AgpVersion.parse("8.2.0-alpha11")
    val knownVersions = listOf(
      "8.2.0-alpha10", "8.2.0-alpha09", "8.2.0-alpha08", "8.2.0-alpha07", "8.2.0-alpha06", "8.2.0-alpha05", "8.2.0-alpha04", "8.2.0-alpha03", "8.2.0-alpha02", "8.2.0-alpha01",
      "8.1.0-rc01",
      "8.1.0-beta05", "8.1.0-beta04", "8.1.0-beta03", "8.1.0-beta02", "8.1.0-beta01",
      "8.1.0-alpha11", "8.1.0-alpha10", "8.1.0-alpha09", "8.1.0-alpha08", "8.1.0-alpha07", "8.1.0-alpha06", "8.1.0-alpha05", "8.1.0-alpha04", "8.1.0-alpha03", "8.1.0-alpha02", "8.1.0-alpha01",
      "8.0.2",
      "8.0.1",
      "8.0.0",
      "8.0.0-rc01",
      "8.0.0-beta05", "8.0.0-beta04", "8.0.0-beta03", "8.0.0-beta02", "8.0.0-beta01",
      "8.0.0-alpha11", "8.0.0-alpha10", "8.0.0-alpha09", "8.0.0-alpha08", "8.0.0-alpha07", "8.0.0-alpha06", "8.0.0-alpha05", "8.0.0-alpha04", "8.0.0-alpha03", "8.0.0-alpha02", "8.0.0-alpha01",
      "7.4.2",
      "7.4.1",
      ).map { AgpVersion.parse(it) }.toSet()
    val toolWindowModel = UpgradeAssistantWindowModel(project, { AgpVersion.parse("7.4.2") }, null, latestKnownNotPublished, newProjectVersion = AgpVersion.parse("8.0.2")) { knownVersions }
    val suggestedVersions = toolWindowModel.suggestedVersionsList(knownVersions)
    assertThat(suggestedVersions.map { it.toString() })
      .containsExactly("8.1.0-rc01", "8.0.2", "8.0.1", "8.0.0", "8.0.0-rc01", "7.4.2").inOrder()
  }

  @Test
  fun `test suggested versions does include current alpha if that version will be used for new projects`() {
    val latestKnownNotPublished = AgpVersion.parse("8.2.0-alpha11")
    val knownVersions = listOf(
      "8.2.0-alpha10", "8.2.0-alpha09", "8.2.0-alpha08", "8.2.0-alpha07", "8.2.0-alpha06", "8.2.0-alpha05", "8.2.0-alpha04", "8.2.0-alpha03", "8.2.0-alpha02", "8.2.0-alpha01",
      "8.1.0-rc01",
      "8.1.0-beta05", "8.1.0-beta04", "8.1.0-beta03", "8.1.0-beta02", "8.1.0-beta01",
      "8.1.0-alpha11", "8.1.0-alpha10", "8.1.0-alpha09", "8.1.0-alpha08", "8.1.0-alpha07", "8.1.0-alpha06", "8.1.0-alpha05", "8.1.0-alpha04", "8.1.0-alpha03", "8.1.0-alpha02", "8.1.0-alpha01",
      "8.0.2",
      "8.0.1",
      "8.0.0",
    ).map { AgpVersion.parse(it) }.toSet()
    val toolWindowModel = UpgradeAssistantWindowModel(project, { AgpVersion.parse("8.0.1") }, null, latestKnownNotPublished, newProjectVersion = latestKnownNotPublished) { knownVersions }
    val suggestedVersions = toolWindowModel.suggestedVersionsList(knownVersions)
    assertThat(suggestedVersions.map { it.toString() })
      .containsExactly("8.2.0-alpha11", "8.1.0-rc01", "8.0.2", "8.0.1").inOrder()
  }

  @Test
  fun testAgpVersionEditingValidation() {
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    assertThat(model.editingValidation("").first).isEqualTo(EditingErrorCategory.ERROR)
    assertThat(model.editingValidation("").second).isEqualTo("Invalid AGP version format.")
    assertThat(model.editingValidation("2.0.0").first).isEqualTo(EditingErrorCategory.ERROR)
    assertThat(model.editingValidation("2.0.0").second).isEqualTo("Cannot downgrade AGP version.")
    assertThat(model.editingValidation(currentAgpVersion.toString()).first).isEqualTo(EditingErrorCategory.NONE)
    assertThat(model.editingValidation(latestAgpVersion.toString()).first).isEqualTo(EditingErrorCategory.NONE)
    latestAgpVersion.run {
      val newMajorVersion = AgpVersion(major + 1, minor, micro)
      assertThat(model.editingValidation(newMajorVersion.toString()).first).isEqualTo(EditingErrorCategory.ERROR)
      assertThat(model.editingValidation(newMajorVersion.toString()).second).isEqualTo("Target AGP version is unsupported.")
    }
    latestAgpVersion.run {
      val newMinorVersion = AgpVersion(major, minor + 1, micro)
      assertThat(model.editingValidation(newMinorVersion.toString()).first).isEqualTo(EditingErrorCategory.WARNING)
      assertThat(model.editingValidation(newMinorVersion.toString()).second).isEqualTo("Upgrade to target AGP version is unverified.")
    }
    latestAgpVersion.run {
      val newPointVersion = AgpVersion(major, minor, micro + 1)
      assertThat(model.editingValidation(newPointVersion.toString()).first).isEqualTo(EditingErrorCategory.WARNING)
      assertThat(model.editingValidation(newPointVersion.toString()).second).isEqualTo("Upgrade to target AGP version is unverified.")
    }
  }

  @Test
  fun testTreePanelVisibility() {
    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)
    assertThat(view.treePanel.isVisible).isTrue()
    model.uiState.set(ReadyToRun)
    assertThat(view.treePanel.isVisible).isTrue()
    model.uiState.set(UpgradeSyncFailed("oops"))
    assertThat(view.treePanel.isVisible).isFalse()
    model.uiState.set(ReadyToRun)
    assertThat(view.treePanel.isVisible).isTrue()
  }

  @Test
  fun testRevertPanelVisibility() {
    fun UpgradeAssistantView.revertButtonVisible() =
      TreeWalker(detailsPanel).descendants().singleOrNull { it.name == "revert project button" }?.isVisible ?: false

    fun UpgradeAssistantView.localHistoryLinkVisible() =
      TreeWalker(detailsPanel).descendants().singleOrNull { it.name == "open local history link" }?.isVisible ?: false

    val contentManager = ContentManagerImpl(project)
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Upgrade Assistant")!!
    val model = UpgradeAssistantWindowModel(project, { currentAgpVersion })
    val view = UpgradeAssistantView(model, toolWindow.contentManager)

    assertThat(view.revertButtonVisible()).isFalse()
    assertThat(view.localHistoryLinkVisible()).isFalse()
    model.uiState.set(ReadyToRun)
    assertThat(view.revertButtonVisible()).isFalse()
    assertThat(view.localHistoryLinkVisible()).isFalse()
    model.uiState.set(UpgradeSyncFailed("oops"))
    assertThat(view.revertButtonVisible()).isTrue()
    assertThat(view.localHistoryLinkVisible()).isTrue()
    model.uiState.set(ReadyToRun)
    assertThat(view.revertButtonVisible()).isFalse()
    assertThat(view.localHistoryLinkVisible()).isFalse()
    model.uiState.set(UpgradeSyncSucceeded)
    assertThat(view.revertButtonVisible()).isTrue()
    assertThat(view.localHistoryLinkVisible()).isTrue()
    model.uiState.set(ReadyToRun)
    assertThat(view.revertButtonVisible()).isFalse()
    assertThat(view.localHistoryLinkVisible()).isFalse()
    model.uiState.set(CaughtException("argh"))
    assertThat(view.revertButtonVisible()).isFalse()
    assertThat(view.localHistoryLinkVisible()).isTrue()
  }

  @Test
  fun testUIStateEquality() {
    fun UIState.hash(): Int = when (this) {
      // This is written out so that it fails to compile if a new UIState is added without updating this test.
      AllDone, Blocked, NoAGP,
      is CaughtException,
      is InvalidVersionError,
      VersionSelectionInProgress,
      Loading, NoStepsSelected, ProjectFilesNotCleanWarning,
      ReadyToRun, RunningSync, RunningUpgrade, RunningUpgradeSync, RunningBuild,
      is UpgradeSyncFailed,
      UpgradeSyncSucceeded ->
        this.hashCode()
    }

    val stateList = listOf(
      AllDone, Blocked, NoAGP,
      CaughtException("one"), CaughtException("two"),
      InvalidVersionError(StatusMessage(Severity.ERROR, "one")), InvalidVersionError(StatusMessage(Severity.ERROR, "two")),
      Loading, ProjectFilesNotCleanWarning, ReadyToRun, RunningSync, RunningUpgrade, RunningUpgradeSync, RunningBuild,
      UpgradeSyncFailed("one"), UpgradeSyncFailed("two"),
      UpgradeSyncSucceeded
    )

    val unexpectedlyEqualHashes = mutableListOf<Pair<UIState, UIState>>()
    stateList.forEachIndexed { i, statei ->
      stateList.forEachIndexed { j, statej ->
        if (i == j) {
          expect.that(statei == statej)
          expect.that(statei.hash() == statej.hash())
        }
        else {
          expect.that(statei != statej)
          if (statei.hash() == statej.hash()) unexpectedlyEqualHashes.add(statei to statej)
        }
      }
    }
    // We have some functionally identical UIStates, which we can distinguish for .equals() on the basis of their class identity, but
    // have the same behaviour (given the same input error messages) and hence the same hash code.
    expect.that(unexpectedlyEqualHashes).hasSize(6)
  }

  @Test
  fun testBuildFinishedWithoutRunningProcessor() {
    val psiFile = addMinimalBuildGradleToProject()
    var changingCurrentAgpVersion = currentAgpVersion
    val toolWindowModel = UpgradeAssistantWindowModel(project, { changingCurrentAgpVersion }).listeningStatesChanges()
    val projectPath = File(project.basePath)
    val request = GradleBuildInvoker.Request.builder(project, projectPath, listOf("clean"))
      .setMode(BuildMode.CLEAN)
      .build()
    val buildContext = BuildContext(request)

    toolWindowModel.buildStarted(buildContext)
    toolWindowModel.buildFinished(BuildStatus.SUCCESS, buildContext)
    assertThat(psiFile.text).contains("classpath 'com.android.tools.build:gradle:$currentAgpVersion")
    assertThat(uiStates).containsExactly(RunningBuild, ReadyToRun, Loading, ReadyToRun).inOrder()
  }

  fun treeString(tree: CheckboxTree): String {
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