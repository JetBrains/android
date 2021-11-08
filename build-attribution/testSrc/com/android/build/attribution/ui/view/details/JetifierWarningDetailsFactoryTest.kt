/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.build.attribution.ui.view.details

import com.android.build.attribution.analyzers.JetifierCanBeRemoved
import com.android.build.attribution.analyzers.JetifierRequiredForLibraries
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.analyzers.JetifierUsedCheckRequired
import com.android.build.attribution.ui.view.ViewActionHandlers
import com.android.ide.common.attribution.CheckJetifierResult
import com.android.ide.common.attribution.DependencyPath
import com.android.ide.common.attribution.FullDependencyPath
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.swing.FakeUi
import com.google.common.truth.Truth
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.components.JBList
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.tree.DefaultMutableTreeNode

@RunsInEdt
class JetifierWarningDetailsFactoryTest {

  @get:Rule
  val applicationRule: ApplicationRule = ApplicationRule()

  @get:Rule
  val edtRule = EdtRule()

  private val mockHandlers = Mockito.mock(ViewActionHandlers::class.java)

  @Test
  fun testCheckRequiredPageCreation() {
    val page = JetifierWarningDetailsFactory(mockHandlers).createPage(JetifierUsageAnalyzerResult(JetifierUsedCheckRequired))

    TreeWalker(page).descendants().filterIsInstance<JEditorPane>().single().let {
      val html = it.text.clearHtml()
      Truth.assertThat(html).contains("<b>Check if you need Jetifier in your project</b>")
      Truth.assertThat(html).contains("Run check to see if you have any of such dependencies in your project.")
    }
    TreeWalker(page).descendants().filterIsInstance<JButton>().single().let {
      Truth.assertThat(it.text).isEqualTo("Run Jetifier check")
      it.doClick()
      Mockito.verify(mockHandlers).runCheckJetifierTask()
    }
    val declaredDependenciesList = TreeWalker(page).descendants().filterIsInstance<JBList<String>>().single()
    Truth.assertThat(declaredDependenciesList.isEmpty).isTrue()

    val header = TreeWalker(page).descendants().filterIsInstance<SimpleColoredComponent>()
      .single { it.name == "declared-dependencies-header" }
    Truth.assertThat(header.toString()).isEqualTo("Declared Dependencies Requiring Jetifier")

    val dependenciesTree = TreeWalker(page).descendants().filterIsInstance<Tree>().single()
    Truth.assertThat(dependenciesTree.isEmpty).isTrue()
  }

  @Test
  fun testJetifierCanBeRemovedPageCreation() {
    val page = JetifierWarningDetailsFactory(mockHandlers).createPage(JetifierUsageAnalyzerResult(
      JetifierCanBeRemoved,
      lastCheckJetifierBuildTimestamp = 0
    ))
    page.size = Dimension(600, 400)
    val ui = FakeUi(page)
    ui.layoutAndDispatchEvents()

    TreeWalker(page).descendants().filterIsInstance<JEditorPane>().single().let {
      val html = it.text.clearHtml()
      Truth.assertThat(html).contains("<b>Jetifier flag can be removed</b>")
      Truth.assertThat(html).contains("The last check did not find any dependencies that require Jetifier in your project.")

      val declaredDependenciesList = TreeWalker(page).descendants().filterIsInstance<JBList<String>>().single()
      Truth.assertThat(declaredDependenciesList.isEmpty).isTrue()

      val header = TreeWalker(page).descendants().filterIsInstance<SimpleColoredComponent>()
        .single { it.name == "declared-dependencies-header" }
      Truth.assertThat(header.toString()).startsWith("Declared Dependencies Requiring Jetifier (last updated ")

      val dependenciesTree = TreeWalker(page).descendants().filterIsInstance<Tree>().single()
      Truth.assertThat(dependenciesTree.isEmpty).isTrue()
    }
  }

  @Test
  fun testJetifierRequiredForLibsPageCreation() {
    val checkJetifierResult = CheckJetifierResult(LinkedHashMap<String, FullDependencyPath>().apply {
      put("example:A:1.0", FullDependencyPath(
        projectPath = ":app",
        configuration = "debugAndroidTestCompileClasspath",
        dependencyPath = DependencyPath(
          listOf("example:A:1.0", "example:C:1.0", "example:B:1.0", "com.android.support:support-annotations:28.0.0"))
      ))
      put("com.android.support:collections:28.0.0", FullDependencyPath(
        projectPath = ":app",
        configuration = "debugAndroidTestCompileClasspath",
        dependencyPath = DependencyPath(listOf("com.android.support:collections:28.0.0"))
      ))
      put("example:B:1.0", FullDependencyPath(
        projectPath = ":lib",
        configuration = "debugAndroidTestCompileClasspath",
        dependencyPath = DependencyPath(listOf("example:B:1.0", "com.android.support:support-annotations:28.0.0"))
      ))
    })

    val page = JetifierWarningDetailsFactory(mockHandlers).createPage(JetifierUsageAnalyzerResult(
      JetifierRequiredForLibraries(checkJetifierResult),
      lastCheckJetifierBuildTimestamp = 0
    ))
    page.size = Dimension(600, 400)
    val ui = FakeUi(page)
    ui.layoutAndDispatchEvents()

    TreeWalker(page).descendants().filterIsInstance<JEditorPane>().single().let {
      val html = it.text.clearHtml()
      Truth.assertThat(html).contains("<b>Some project dependencies require Jetifier</b>")
      Truth.assertThat(html).contains("This check found 3 declared dependencies that require legacy support libraries.")
      Truth.assertThat(html).contains(
        "To disable Jetifier you need to upgrade them to versions that do not require legacy support libraries or find alternatives.")
    }

    val declaredDependenciesList = TreeWalker(page).descendants().filterIsInstance<JBList<String>>().single()
    val declaredDependenciesListModel = declaredDependenciesList.model
    Truth.assertThat(declaredDependenciesListModel.size).isEqualTo(3)

    val dependenciesTree = TreeWalker(page).descendants().filterIsInstance<Tree>().single()
    Truth.assertThat(dependenciesTree.isEmpty).isTrue()

    // Table sorted as:
    // com.android.support:collections:28.0.0
    // example:A:1.0
    // example:B:1.0

    val header = TreeWalker(page).descendants().filterIsInstance<SimpleColoredComponent>()
      .single { it.name == "declared-dependencies-header" }
    Truth.assertThat(header.toString()).startsWith("Declared Dependencies Requiring Jetifier (last updated ")
    Truth.assertThat(declaredDependenciesListModel.getElementAt(1)).isEqualTo("example:A:1.0")
    declaredDependenciesList.selectedIndex = 1
    Truth.assertThat(dependenciesTree.isEmpty).isFalse()

    fun treePresentation(dependenciesTree: Tree) = (dependenciesTree.model.root as DefaultMutableTreeNode).preorderEnumeration().asSequence()
      .drop(1) // Skip root.
      .filterIsInstance(DefaultMutableTreeNode::class.java)
      .joinToString(separator = "\n") { node ->
        val descriptor = node.userObject as JetifierWarningDetailsFactory.DependencyDescriptor
        "${" ".repeat((node.level - 1) * 2)}[${descriptor.prefix}]${descriptor.fullName}"
      }

    Truth.assertThat(treePresentation(dependenciesTree)).isEqualTo("""
      |[depends on ]com.android.support:support-annotations:28.0.0
      |  [via ]example:B:1.0
      |    [via ]example:C:1.0
      |      []example:A:1.0
    """.trimMargin())

    Truth.assertThat(declaredDependenciesListModel.getElementAt(0)).isEqualTo("com.android.support:collections:28.0.0")
    declaredDependenciesList.selectedIndex = 0
    Truth.assertThat(treePresentation(dependenciesTree)).isEqualTo("""
      |[]com.android.support:collections:28.0.0
    """.trimMargin())
  }

  @Test
  fun testJetifierRequiredForSingleDeclaredLibPageCreation() {
    val checkJetifierResult = CheckJetifierResult(LinkedHashMap<String, FullDependencyPath>().apply {
      put("example:A:1.0", FullDependencyPath(
        projectPath = ":app",
        configuration = "debugAndroidTestCompileClasspath",
        dependencyPath = DependencyPath(
          listOf("example:A:1.0", "example:C:1.0", "example:B:1.0", "com.android.support:support-annotations:28.0.0"))
      ))
    })

    val page = JetifierWarningDetailsFactory(mockHandlers).createPage(JetifierUsageAnalyzerResult(
      JetifierRequiredForLibraries(checkJetifierResult),
      lastCheckJetifierBuildTimestamp = 0
    ))
    page.size = Dimension(600, 400)
    val ui = FakeUi(page)
    ui.layoutAndDispatchEvents()

    TreeWalker(page).descendants().filterIsInstance<JEditorPane>().single().let {
      val html = it.text.clearHtml()
      Truth.assertThat(html).contains("<b>Some project dependencies require Jetifier</b>")
      Truth.assertThat(html).contains("found a declared dependency that requires")
      Truth.assertThat(html).contains(
        "To disable Jetifier you need to upgrade it to a version that does not require legacy support libraries or find an alternative.")
    }
  }

  private fun String.clearHtml(): String = UIUtil.getHtmlBody(this)
    .trimIndent()
    .replace("\n", "")
    .replace("<br>", "\n")
    .replace("<p>", "\n")
    .replace("</p>", "\n")
}