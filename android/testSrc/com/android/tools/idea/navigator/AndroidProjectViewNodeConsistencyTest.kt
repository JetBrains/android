/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.navigator

import com.android.tools.idea.navigator.nodes.FileGroupNode
import com.android.tools.idea.navigator.nodes.FolderGroupNode
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.GradleIntegrationTest
import com.android.tools.idea.testing.TestProjectToSnapshotPaths.BASIC_CMAKE_APP
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.openPreparedProject
import com.android.tools.idea.testing.prepareGradleProject
import com.android.tools.idea.util.toIoFile
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.File
import java.util.ArrayDeque

/**
 * A test to ensure that implementations of [ProjectViewNode.canRepresent] and [ProjectViewNode.contains] methods are consistent with
 * methods like [ProjectViewNode.getChildren], [ProjectViewNode.getVirtualFile] and others that expose files represented by a node.
 *
 * [ProjectViewNode.canRepresent] and [ProjectViewNode.contains] are used by the platform to locate a node by a file and are essential
 * to refresh the tree in response to changes in the virtual file system.
 */
@RunsInEdt
class AndroidProjectViewNodeConsistencyTest : GradleIntegrationTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @get:Rule
  var testName = TestName()

  override fun getName(): String = testName.methodName
  override fun getBaseTestPath(): String = projectRule.fixture.tempDirPath
  override fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData/snapshots"
  override fun getAdditionalRepos(): Collection<File> = listOf()

  private interface TestContext {
    val rootElement: ProjectViewNode<*>
    val projectRoot: File
    fun reportProblem(node: NodeWithParents, problem: String)
  }

  private data class NodeWithParents(val node: ProjectViewNode<*>, val parents: List<ProjectViewNode<*>> = emptyList()) {
    val parent: NodeWithParents? get() = if (parents.isNotEmpty()) NodeWithParents(parents.last(), parents.dropLast(1)) else null
    fun child(node: ProjectViewNode<*>): NodeWithParents = NodeWithParents(node, this.parents + this.node)
  }

  private fun runTestOn(projectName: String, test: TestContext.() -> Unit) {
    val root = prepareGradleProject(projectName, "project")
    openPreparedProject("project") { project ->
      val oldHideEmptyPackages = ProjectView.getInstance(project).isHideEmptyMiddlePackages(AndroidProjectViewPane.ID)
      ProjectView.getInstance(project).apply {
        setHideEmptyPackages(AndroidProjectViewPane.ID, true)
      }
      try {
        val viewPane = AndroidProjectViewPane(project)
        // We need to create a component to initialize the view pane.
        viewPane.createComponent()
        val treeStructure: AbstractTreeStructure? = viewPane.treeStructure

        val problems = mutableListOf<String>()
        with(object : TestContext {
          override val rootElement = (treeStructure?.rootElement ?: error("No root element")) as ProjectViewNode<*>
          override val projectRoot = root
          override fun reportProblem(node: NodeWithParents, problem: String) {
            val message = buildString {
              var prefix = ""
              (node.parents + node.node).forEach {
                appendln("$prefix/ ${it.nodeNameForTest()}")
                prefix += "    "
              }
              appendln(problem.prependIndent(prefix))
            }
            println(message);
            problems.add(message)
          }
        }) {
          test()
        }
        assertThat(problems).isEmpty()
      }
      finally {
        ProjectView.getInstance(project).apply {
          setHideEmptyPackages(AndroidProjectViewPane.ID, oldHideEmptyPackages)
        }
      }
    }
  }

  /**
   * Returns a sub-set of files known to be represented by this node. This is not necessarily the full set of files represented by the node
   * since there is no standard way to get these data.
   */
  private fun ProjectViewNode<*>.representedFiles(): Set<VirtualFile> {
    val result = mutableSetOf<VirtualFile>()
    virtualFile?.let(result::add)
    when (this) {
      is FileGroupNode -> files.mapNotNull { it.virtualFile }.let(result::addAll)
      is FolderGroupNode -> folders.map { it.virtualFile }.let(result::addAll)
    }
    return result
  }

  private fun ProjectViewNode<*>.enumerateNodes() = sequence<NodeWithParents> {
    val q = ArrayDeque(listOf(NodeWithParents(this@enumerateNodes)))
    while (!q.isEmpty()) {
      val current = q.pop()
      yield(current)
      q.addAll(current.node.children.reversed().filterIsInstance<ProjectViewNode<*>>().map { current.child(it) })
    }
  }

  private fun ProjectViewNode<*>.nodeNameForTest() = "${toTestString(null)} (${javaClass.simpleName})"

  /**
   * Tests that `contains` method recognises any files which are supposed to be "represented" by the node itself or any node it
   * expands into (via [ProjectViewNode.getChildren]). We learn about the fact that a node is supposed to "represent" a file from various
   * sources like [ProjectViewNode.getVirtualFile] method or [FolderGroupNode] and [FileGroupNode] interfaces. This does not guarantee that
   * we learn about all files "represented" by a node.
   */
  @Test
  fun testContains() {
    runTestOn(BASIC_CMAKE_APP) {
      val nodes: Sequence<NodeWithParents> = rootElement.enumerateNodes()

      val expectedForContains: Map<NodeWithParents, Set<VirtualFile>> =
        nodes.asSequence().flatMap { node ->
          node.node.representedFiles().asSequence().flatMap { file ->
            generateSequence(node) { it.parent }.map { parentNodeOrSelf -> parentNodeOrSelf to file }
          }
        }
          .groupBy({ it.first }, { it.second })
          .mapValues { it.value.toSet() }

      expectedForContains.forEach { (node, files) ->
        val missing = files.filter { !node.node.contains(it) }
        if (missing.isNotEmpty()) {
          reportProblem(node,
                        "does not contains the following files:\n" +
                        missing.joinToString("\n") { "* ${it.toIoFile().relativeTo(projectRoot)}" })
        }
      }
    }
  }

  /**
   * Tests that `canRepresent` method recognises any files which are supposed to be "represented" by the node itself. We learn about the
   * fact that a node is supposed to "represent" a file from various sources like [ProjectViewNode.getVirtualFile] method or
   * [FolderGroupNode] and [FileGroupNode] interfaces. This does not guarantee that we learn about all files "represented" by a node.
   */
  @Test
  fun testCanRepresent() {
    runTestOn(BASIC_CMAKE_APP) {
      val nodes: Sequence<NodeWithParents> = rootElement.enumerateNodes()

      val expectedForCanRepresent =
        nodes.asSequence().flatMap { node ->
          node.node.representedFiles().asSequence().map { file -> node to file }
        }
          .groupBy({ it.first }, { it.second })
          .mapValues { it.value.toSet() }

      expectedForCanRepresent.forEach { (node, files) ->
        val missing = files.filter { !node.node.canRepresent(it) }
        if (missing.isNotEmpty()) {
          reportProblem(node,
                        "does not represent the following files:\n" +
                        missing.joinToString("\n") { "* ${it.toIoFile().relativeTo(projectRoot)}" })
        }
      }
    }
  }
}
