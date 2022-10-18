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
package com.android.tools.idea.editors.manifest

import com.android.tools.idea.editors.manifest.ManifestPanel.ManifestTreeNode
import com.android.tools.idea.gradle.project.sync.internal.ProjectDumper
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TemplateBasedTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.model.MergedManifestManager
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.idea.projectsystem.sourceProviders
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.SnapshotComparisonTest
import com.android.tools.idea.testing.assertAreEqualToSnapshots
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.util.androidFacet
import com.android.utils.FileUtils.toSystemIndependentPath
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.io.StringWriter
import java.util.concurrent.TimeUnit
import javax.swing.tree.TreeModel
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

@RunWith(JUnit4::class)
class ManifestPanelContentTest : SnapshotComparisonTest {

  companion object {
    private const val MANIFEST_REPORT_SNAPSHOT_SUFFIX = "_manifest_report.html"
    private const val MERGED_MANIFEST_SHAPSHOT_SUFFIX = "_merged_manifest.xml"
  }
  @get:Rule
  var testName = TestName()
  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  fun getTestDataDirectoryWorkspaceRelativePath(): String = "tools/adt/idea/android/testData"

  override lateinit var snapshotDirectoryWorkspaceRelativePath: String

  override fun getName(): String = testName.methodName

  @Test
  fun testProject_navigationEditor_includeFromLib() {
    testProject(AndroidCoreTestProject.NAVIGATION_EDITOR_INCLUDE_FROM_LIB)
  }
  @Test
  fun testProject_withErrors_simpleApplicationMissingExport() {
    testProject(AndroidCoreTestProject.WITH_ERRORS_SIMPLE_APPLICATION_MISSING_EXPORT)
  }

  @Test
  fun testProject_withErrors_simpleApplicationMultipleErrors() {
    testProject(AndroidCoreTestProject.WITH_ERRORS_SIMPLE_APPLICATION_MULTIPLE_ERRORS)
  }

  @Test
  fun testProject_dynamicApp() {
    testProject(AndroidCoreTestProject.DYNAMIC_APP, gradlePath = ":feature1")
  }

  private fun testProject(testProject : TemplateBasedTestProject, gradlePath: String = ":app") {
    snapshotDirectoryWorkspaceRelativePath = testProject
      .templateAbsolutePath
      .resolve("snapshots")
      .toString()
    val preparedProject = projectRule.prepareTestProject(testProject)
    preparedProject.open { project ->
      val appModule = project.gradleModule(gradlePath)?.getMainModule() ?: error("Cannot find $gradlePath module")
      val appModuleFacet = appModule.androidFacet ?: error("Cannot find the facet for $gradlePath")

      val mergedManifest = MergedManifestManager.getMergedManifestSupplier(appModule).get().get(2, TimeUnit.SECONDS)

      val panel = ManifestPanel(appModuleFacet, projectRule.testRootDisposable)
      panel.showManifest(mergedManifest, appModuleFacet.sourceProviders.mainManifestFile!!, false)
      val detailsPaneContent = panel.detailsPane.text
      val model: TreeModel? = panel.tree.model
      val manifestPaneContent: String? = model?.transformToString()

      ProjectDumper().nest(preparedProject.root, "PROJECT_DIR") {
        assertAreEqualToSnapshots(
          normalizeContentForTest(detailsPaneContent) to MANIFEST_REPORT_SNAPSHOT_SUFFIX,
          normalizeContentForTest(manifestPaneContent) to MERGED_MANIFEST_SHAPSHOT_SUFFIX
        )
      }
    }
  }

  /* Goes through each line, removing empty lines and replacing hyperlinks with files with stable naming across different config/runs. */
  private fun ProjectDumper.normalizeContentForTest(htmlString: String?) = htmlString
    .let { it ?: "Pane content is empty" }
    .lines()
    .filter { it.trim().isNotEmpty() }
    .joinToString(separator = "\n", postfix = "\n") {
      it.replace(Regex("\"file:(.*)\"")) { matchResult ->
        val fileAndPosition = matchResult.groupValues[1]
        val (file, suffix) = splitFileAndSuffixPosition(fileAndPosition)
        "'${toSystemIndependentPath(File(file).absolutePath).toPrintablePath()}$suffix'"
      }.trimEnd()
    }.trimIndent()


  private fun splitFileAndSuffixPosition(fileAndPosition: String): Pair<String, String> {
    var suffixPosition = fileAndPosition.length
    var columns = 0
    while (suffixPosition > 0 && columns <= 2 && fileAndPosition[suffixPosition - 1].let { it.isDigit() || it == ':' }) {
      suffixPosition--
      if (fileAndPosition[suffixPosition] == ':') columns++
    }
    if (columns < 2) suffixPosition = fileAndPosition.length
    return fileAndPosition.substring(0, suffixPosition) to fileAndPosition.substring(suffixPosition, fileAndPosition.length)
  }

  private fun TreeModel?.transformToString() : String? =
    if (this == null) {
      null
    } else {
      StringWriter().let {
        TransformerFactory.newInstance().newTransformer().apply {
          setOutputProperty(OutputKeys.INDENT, "yes")
          setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        }.transform(DOMSource((this.root as ManifestTreeNode).userObject), StreamResult(it))
        it.buffer.toString()
      }
    }
}