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
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.UIUtil
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument
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
    val page = JetifierWarningDetailsFactory(mockHandlers).createPage(JetifierUsageAnalyzerResult(JetifierUsedCheckRequired, false))

    TreeWalker(page).descendants().filterIsInstance<JEditorPane>().single().let {
      Truth.assertThat(it.text).contains("<b>Confirm need for Jetifier flag in your project</b>")
    }
    TreeWalker(page).descendants().filterIsInstance<JButton>().single().let {
      Truth.assertThat(it.text).isEqualTo("Check Jetifier")
      it.doClick()
      Mockito.verify(mockHandlers).runCheckJetifierTask()
    }
  }

  @Test
  fun testJetifierCanBeRemovedPageCreation() {
    val page = JetifierWarningDetailsFactory(mockHandlers).createPage(JetifierUsageAnalyzerResult(JetifierCanBeRemoved, false))
    page.size = Dimension(600, 400)
    val ui = FakeUi(page)
    ui.layoutAndDispatchEvents()

    TreeWalker(page).descendants().filterIsInstance<JEditorPane>().single().let {
      val html = it.text.clearHtml()
      Truth.assertThat(html).contains("<b>Remove Jetifier flag</b>")
      Truth.assertThat(html).contains("Remove enableJetifier")
      // Emulate link click.
      val link = (it.document as HTMLDocument).getIterator(HTML.Tag.A)
      val rectangle2D = it.modelToView2D(link.startOffset)
      rectangle2D.add(it.modelToView2D(link.endOffset))
      ui.mouse.click(rectangle2D.centerX.toInt(), rectangle2D.centerY.toInt())
      Mockito.verify(mockHandlers).turnJetifierOffInProperties()
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

    val page = JetifierWarningDetailsFactory(mockHandlers).createPage(JetifierUsageAnalyzerResult(JetifierRequiredForLibraries(checkJetifierResult), false))
    page.size = Dimension(600, 400)
    val ui = FakeUi(page)
    ui.layoutAndDispatchEvents()

    TreeWalker(page).descendants().filterIsInstance<JEditorPane>().single().let {
      val html = it.text.clearHtml()
      Truth.assertThat(html).contains("<b>Jetifier flag is needed by some libraries in your project</b>")
    }

    TreeWalker(page).descendants().filterIsInstance<Tree>().single().let {
      val treePresentation = (it.model.root as DefaultMutableTreeNode).preorderEnumeration().asSequence()
        .drop(1) // Skip root.
        .filterIsInstance(DefaultMutableTreeNode::class.java)
        .joinToString(separator = "\n") { node ->
          val descriptor = node.userObject as JetifierWarningDetailsFactory.LibDescriptor
          "${" ".repeat((node.level - 1) * 2)}${descriptor.fullName} [${descriptor.usageSuffix}]"
        }

      Truth.assertThat(treePresentation).isEqualTo("""
        |com.android.support:collections:28.0.0 [ used directly]
        |example:B:1.0 [ used directly and transitively]
        |  example:C:1.0 [ used transitively]
        |    example:A:1.0 [ used directly]
      """.trimMargin())

    }
  }

  private fun String.clearHtml(): String = UIUtil.getHtmlBody(this)
    .trimIndent()
    .replace("\n", "")
    .replace("<br>", "\n")
    .replace("<p>", "\n")
    .replace("</p>", "\n")
}