/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.treeview

import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.base.BlazeIntegrationTestCase
import com.google.idea.blaze.base.workspace.WorkspaceSwitchManager
import com.google.idea.blaze.base.workspace.WorkspaceSwitchManager.setWorkspaceTarget
import com.google.idea.common.experiments.ExperimentService
import com.google.idea.common.experiments.MockExperimentService
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import java.nio.file.Path
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class WorkspaceSwitcherProjectViewNodeDecoratorTest : BlazeIntegrationTestCase() {

  private val experimentService = MockExperimentService()

  @Before
  fun initExperiments() {
    registerApplicationService(ExperimentService::class.java, experimentService)
  }

  private class TestProjectViewNode(
    project: Project,
    private val vf: VirtualFile,
  ) : ProjectViewNode<VirtualFile>(project, vf, ViewSettings.DEFAULT) {
    override fun contains(file: VirtualFile): Boolean = false

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

    override fun update(presentation: PresentationData) {}

    override fun getVirtualFile(): VirtualFile = vf
  }

  @Test
  fun experimentDisabled_doesNotModifyPresentation() {
    experimentService.setExperiment(WorkspaceSwitchManager.WORKSPACE_SWITCHER_ENABLED, false)
    val physicalPath = Path.of(FileUtil.getTempDirectory(), "physical_ws")
    val virtualRoot = project.setWorkspaceTarget(physicalPath)

    val homeRelative = FileUtil.getLocationRelativeToUserHome(virtualRoot.toString())
    val text = "$homeRelative/tools/adt/idea"

    val data = PresentationData()
    data.presentableText = text
    data.addText(text, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    data.locationString = " ($text)"

    val dummyDir = fileSystem.createDirectory("dummy_dir")
    val node = TestProjectViewNode(project, dummyDir)
    WorkspaceSwitcherProjectViewNodeDecorator().decorate(node, data)

    assertThat(data.presentableText).isEqualTo(text)
    assertThat(data.coloredText.single().text).isEqualTo(text)
    assertThat(data.locationString).isEqualTo(" ($text)")
  }

  @Test
  fun experimentEnabled_noWorkspaceTarget_doesNotModifyPresentation() {
    experimentService.setExperiment(WorkspaceSwitchManager.WORKSPACE_SWITCHER_ENABLED, true)

    val text = "~/ide_configs/as-repo/system/ws/somehash/tools/adt/idea"
    val data = PresentationData()
    data.presentableText = text
    data.addText(text, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    data.locationString = " ($text)"

    val dummyDir = fileSystem.createDirectory("dummy_dir")
    val node = TestProjectViewNode(project, dummyDir)
    WorkspaceSwitcherProjectViewNodeDecorator().decorate(node, data)

    assertThat(data.presentableText).isEqualTo(text)
    assertThat(data.coloredText.single().text).isEqualTo(text)
    assertThat(data.locationString).isEqualTo(" ($text)")
  }

  @Test
  fun homeRelativePrefixReplacedWithProject() {
    experimentService.setExperiment(WorkspaceSwitchManager.WORKSPACE_SWITCHER_ENABLED, true)
    val physicalPath = Path.of(FileUtil.getTempDirectory(), "physical_ws")
    val virtualRoot = project.setWorkspaceTarget(physicalPath)

    val homeRelative = FileUtil.toSystemIndependentName(FileUtil.getLocationRelativeToUserHome(virtualRoot.toString()))
    val text = "$homeRelative/tools/adt/idea"

    val data = PresentationData()
    data.presentableText = text
    data.addText(text, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)

    val dummyDir = fileSystem.createDirectory("dummy_dir")
    val node = TestProjectViewNode(project, dummyDir)
    WorkspaceSwitcherProjectViewNodeDecorator().decorate(node, data)

    assertThat(data.presentableText).isEqualTo("<project>/tools/adt/idea")
    assertThat(data.coloredText.single().text).isEqualTo("<project>/tools/adt/idea")
    assertThat(data.coloredText.single().attributes).isEqualTo(SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
  }

  @Test
  fun absolutePrefixReplacedWithProject() {
    experimentService.setExperiment(WorkspaceSwitchManager.WORKSPACE_SWITCHER_ENABLED, true)
    val physicalPath = Path.of(FileUtil.getTempDirectory(), "physical_ws")
    val virtualRoot = project.setWorkspaceTarget(physicalPath)

    val absolute = FileUtil.toSystemIndependentName(virtualRoot.toString())
    val text = "$absolute/tools/base"

    val data = PresentationData()
    data.presentableText = text
    data.addText(text, SimpleTextAttributes.REGULAR_ATTRIBUTES)

    val dummyDir = fileSystem.createDirectory("dummy_dir")
    val node = TestProjectViewNode(project, dummyDir)
    WorkspaceSwitcherProjectViewNodeDecorator().decorate(node, data)

    assertThat(data.presentableText).isEqualTo("<project>/tools/base")
    assertThat(data.coloredText.single().text).isEqualTo("<project>/tools/base")
  }

  @Test
  fun exactWorkspaceRootReplacedWithProject() {
    experimentService.setExperiment(WorkspaceSwitchManager.WORKSPACE_SWITCHER_ENABLED, true)
    val physicalPath = Path.of(FileUtil.getTempDirectory(), "physical_ws")
    val virtualRoot = project.setWorkspaceTarget(physicalPath)

    val homeRelative = FileUtil.toSystemIndependentName(FileUtil.getLocationRelativeToUserHome(virtualRoot.toString()))

    val data = PresentationData()
    data.presentableText = homeRelative
    data.addText(homeRelative, SimpleTextAttributes.REGULAR_ATTRIBUTES)

    val dummyDir = fileSystem.createDirectory("dummy_dir")
    val node = TestProjectViewNode(project, dummyDir)
    WorkspaceSwitcherProjectViewNodeDecorator().decorate(node, data)

    assertThat(data.presentableText).isEqualTo("<project>")
    assertThat(data.coloredText.single().text).isEqualTo("<project>")
  }

  @Test
  fun rootNodeLocationHashReplacedWithProject() {
    experimentService.setExperiment(WorkspaceSwitchManager.WORKSPACE_SWITCHER_ENABLED, true)
    val physicalPath = Path.of(FileUtil.getTempDirectory(), "physical_ws")
    val virtualRoot = project.setWorkspaceTarget(physicalPath)

    val mockVirtualFile =
      object : MockVirtualFile(true, virtualRoot.fileName.toString()) {
        override fun getPath(): String = FileUtil.toSystemIndependentName(virtualRoot.toString())
      }

    val data = PresentationData()
    data.presentableText = project.locationHash
    data.addText(project.locationHash, SimpleTextAttributes.REGULAR_ATTRIBUTES)

    val node = TestProjectViewNode(project, mockVirtualFile)
    WorkspaceSwitcherProjectViewNodeDecorator().decorate(node, data)

    assertThat(data.presentableText).isEqualTo("<project>")
    assertThat(data.coloredText.single().text).isEqualTo("<project>")
  }

  @Test
  fun locationStringWithParenthesesReplaced() {
    experimentService.setExperiment(WorkspaceSwitchManager.WORKSPACE_SWITCHER_ENABLED, true)
    val physicalPath = Path.of(FileUtil.getTempDirectory(), "physical_ws")
    val virtualRoot = project.setWorkspaceTarget(physicalPath)

    val homeRelative = FileUtil.toSystemIndependentName(FileUtil.getLocationRelativeToUserHome(virtualRoot.toString()))
    val data = PresentationData()
    data.locationString = " ($homeRelative/tools/vendor)"

    val dummyDir = fileSystem.createDirectory("dummy_dir")
    val node = TestProjectViewNode(project, dummyDir)
    WorkspaceSwitcherProjectViewNodeDecorator().decorate(node, data)

    assertThat(data.locationString).isEqualTo(" (<project>/tools/vendor)")
  }

  @Test
  fun unrelatedPathRemainsUnchanged() {
    experimentService.setExperiment(WorkspaceSwitchManager.WORKSPACE_SWITCHER_ENABLED, true)
    val physicalPath = Path.of(FileUtil.getTempDirectory(), "physical_ws")
    project.setWorkspaceTarget(physicalPath)

    val unrelated = "~/other/project/directory"
    val data = PresentationData()
    data.presentableText = unrelated
    data.addText(unrelated, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    data.locationString = " ($unrelated)"

    val dummyDir = fileSystem.createDirectory("dummy_dir")
    val node = TestProjectViewNode(project, dummyDir)
    WorkspaceSwitcherProjectViewNodeDecorator().decorate(node, data)

    assertThat(data.presentableText).isEqualTo(unrelated)
    assertThat(data.coloredText.single().text).isEqualTo(unrelated)
    assertThat(data.locationString).isEqualTo(" ($unrelated)")
  }

  @Test
  fun partialDirectoryMatchRemainsUnchanged() {
    experimentService.setExperiment(WorkspaceSwitchManager.WORKSPACE_SWITCHER_ENABLED, true)
    val physicalPath = Path.of(FileUtil.getTempDirectory(), "physical_ws")
    val virtualRoot = project.setWorkspaceTarget(physicalPath)

    val homeRelative = FileUtil.toSystemIndependentName(FileUtil.getLocationRelativeToUserHome(virtualRoot.toString()))
    val partialMatchText = "${homeRelative}_other/tools/vendor"

    val dataWithParens = PresentationData()
    dataWithParens.presentableText = partialMatchText
    dataWithParens.addText(partialMatchText, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    dataWithParens.locationString = " ($partialMatchText)"

    val dummyDir = fileSystem.createDirectory("dummy_dir")
    val node = TestProjectViewNode(project, dummyDir)
    WorkspaceSwitcherProjectViewNodeDecorator().decorate(node, dataWithParens)

    assertThat(dataWithParens.presentableText).isEqualTo(partialMatchText)
    assertThat(dataWithParens.coloredText.single().text).isEqualTo(partialMatchText)
    assertThat(dataWithParens.locationString).isEqualTo(" ($partialMatchText)")

    val dataWithoutParens = PresentationData()
    dataWithoutParens.locationString = partialMatchText
    WorkspaceSwitcherProjectViewNodeDecorator().decorate(node, dataWithoutParens)
    assertThat(dataWithoutParens.locationString).isEqualTo(partialMatchText)
  }
}
