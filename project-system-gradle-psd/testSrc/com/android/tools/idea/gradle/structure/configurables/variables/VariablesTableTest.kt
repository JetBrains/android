/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.variables

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.repositories.search.ArtifactRepositorySearchService
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.testStructure
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.ShadowedTreeNode
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.childNodes
import com.android.tools.idea.gradle.structure.daemon.PsAnalyzerDaemon
import com.android.tools.idea.gradle.structure.daemon.PsLibraryUpdateCheckerDaemon
import com.android.tools.idea.gradle.structure.daemon.PsSdkIndexCheckerDaemon
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.PsVariable
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.annotated
import com.android.tools.idea.gradle.structure.model.meta.maybeLiteralValue
import com.android.tools.idea.gradle.structure.model.meta.maybeValue
import com.android.tools.idea.structure.dialog.ProjectStructureConfigurable
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBTextField
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

@RunsInEdt
class VariablesTableTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  private var defaultTestDialog: TestDialog? = null
  private val stub = object : ValidationResultsKeeper {
    override fun updateValidationResult(hasValidationError: Boolean) {}
  }
  private fun contextFor(project: PsProject) = object : PsContext {
    override val analyzerDaemon: PsAnalyzerDaemon get() = throw UnsupportedOperationException()
    override val project: PsProject = project
    override val libraryUpdateCheckerDaemon: PsLibraryUpdateCheckerDaemon get() = throw UnsupportedOperationException()
    override val sdkIndexCheckerDaemon: PsSdkIndexCheckerDaemon get() = throw UnsupportedOperationException()
    override val uiSettings: PsUISettings get() = throw UnsupportedOperationException()
    override val selectedModule: String? get() = throw UnsupportedOperationException()
    override val mainConfigurable: ProjectStructureConfigurable get() = throw UnsupportedOperationException()
    override fun getArtifactRepositorySearchServiceFor(module: PsModule): ArtifactRepositorySearchService = throw UnsupportedOperationException()
    override fun setSelectedModule(gradlePath: String, source: Any) = throw UnsupportedOperationException()
    override fun add(listener: PsContext.SyncListener, parentDisposable: Disposable) = throw UnsupportedOperationException()
    override fun applyRunAndReparse(runnable: () -> Boolean) = throw UnsupportedOperationException()
    override fun applyChanges() = throw UnsupportedOperationException()
    override fun logFieldEdited(fieldId: PSDEvent.PSDField) = throw UnsupportedOperationException()
    override fun getEditedFieldsAndClear(): List<PSDEvent.PSDField> = throw UnsupportedOperationException()
    override fun dispose() = throw UnsupportedOperationException()
  }

  @Before
  fun setUp() {
    defaultTestDialog = TestDialogManager.setTestDialog(object : TestDialog {
      override fun show(message: String): Int = Messages.YES
    })

  }

  @After
  fun tearDown() {
    TestDialogManager.setTestDialog(defaultTestDialog)
  }

  @Test
  fun testModuleNodeDisplay() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val rootNode = tableModel.root as DefaultMutableTreeNode
      assertThat(rootNode.childCount).isEqualTo(11)

      val buildScriptNode = rootNode.getChildAt(0) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(buildScriptNode, 0) as String).isEqualTo("project (build script)")
      assertThat(tableModel.getValueAt(buildScriptNode, 1)).isEqualTo(ParsedValue.NotSet)
      assertThat(buildScriptNode.childCount).isNotEqualTo(0)
      assertThat(variablesTable.tree.isExpanded(TreePath(buildScriptNode.path))).isEqualTo(true)

      val projectNode = rootNode.getChildAt(1) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(projectNode, 0) as String).isEqualTo("project (project)")
      assertThat(tableModel.getValueAt(projectNode, 1)).isEqualTo(ParsedValue.NotSet)
      assertThat(projectNode.childCount).isNotEqualTo(0)
      assertThat(variablesTable.tree.isExpanded(TreePath(projectNode.path))).isEqualTo(false)

      val appNode = rootNode.getChildAt(2) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(appNode, 0) as String).isEqualTo(":app")
      assertThat(tableModel.getValueAt(appNode, 1)).isEqualTo(ParsedValue.NotSet)
      assertThat(appNode.childCount).isNotEqualTo(0)
      assertThat(variablesTable.tree.isExpanded(TreePath(appNode.path))).isEqualTo(false)

      val dynFeatureNode = rootNode.getChildAt(3) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(dynFeatureNode, 0) as String).isEqualTo(":dyn_feature")
      assertThat(tableModel.getValueAt(dynFeatureNode, 1)).isEqualTo(ParsedValue.NotSet)
      assertThat(dynFeatureNode.childCount).isEqualTo(1)
      assertThat(variablesTable.tree.isExpanded(TreePath(dynFeatureNode.path))).isEqualTo(false)

      val javNode = rootNode.getChildAt(4) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(javNode, 0) as String).isEqualTo(":jav")
      assertThat(tableModel.getValueAt(javNode, 1)).isEqualTo(ParsedValue.NotSet)
      assertThat(javNode.childCount).isEqualTo(1)
      assertThat(variablesTable.tree.isExpanded(TreePath(javNode.path))).isEqualTo(false)

      val libNode = rootNode.getChildAt(5) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(libNode, 0) as String).isEqualTo(":lib")
      assertThat(tableModel.getValueAt(libNode, 1)).isEqualTo(ParsedValue.NotSet)
      assertThat(libNode.childCount).isEqualTo(1)
      assertThat(variablesTable.tree.isExpanded(TreePath(libNode.path))).isEqualTo(false)

      val nested1Node = rootNode.getChildAt(6) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(nested1Node, 0) as String).isEqualTo(":nested1")
      assertThat(tableModel.getValueAt(nested1Node, 1)).isEqualTo(ParsedValue.NotSet)
      assertThat(nested1Node.childCount).isEqualTo(1)
      assertThat(variablesTable.tree.isExpanded(TreePath(nested1Node.path))).isEqualTo(false)

      val nested1DeepNode = rootNode.getChildAt(7) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(nested1DeepNode, 0) as String).isEqualTo(":nested1:deep")
      assertThat(tableModel.getValueAt(nested1DeepNode, 1)).isEqualTo(ParsedValue.NotSet)
      assertThat(nested1DeepNode.childCount).isEqualTo(1)
      assertThat(variablesTable.tree.isExpanded(TreePath(nested1DeepNode.path))).isEqualTo(false)

      val nested2Node = rootNode.getChildAt(8) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(nested2Node, 0) as String).isEqualTo(":nested2")
      assertThat(tableModel.getValueAt(nested2Node, 1)).isEqualTo(ParsedValue.NotSet)
      assertThat(nested2Node.childCount).isEqualTo(1)
      assertThat(variablesTable.tree.isExpanded(TreePath(nested2Node.path))).isEqualTo(false)

      val nested2DeepNode = rootNode.getChildAt(9) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(nested2DeepNode, 0) as String).isEqualTo(":nested2:deep")
      assertThat(tableModel.getValueAt(nested2DeepNode, 1)).isEqualTo(ParsedValue.NotSet)
      assertThat(nested2DeepNode.childCount).isEqualTo(1)
      assertThat(variablesTable.tree.isExpanded(TreePath(nested2DeepNode.path))).isEqualTo(false)

      val nested2Deep2Node = rootNode.getChildAt(10) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(nested2Deep2Node, 0) as String).isEqualTo(":nested2:trans:deep2")
      assertThat(tableModel.getValueAt(nested2Deep2Node, 1)).isEqualTo(ParsedValue.NotSet)
      assertThat(nested2Deep2Node.childCount).isEqualTo(1)
      assertThat(variablesTable.tree.isExpanded(TreePath(nested2Deep2Node.path))).isEqualTo(false)

      val row = variablesTable.tree.getRowForPath(TreePath(appNode.path))
      for (column in 0..1) {
        val component = variablesTable.getCellRenderer(row, column)
          .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row, column), false, false, row, column)
        assertThat(component.background).isEqualTo(variablesTable.background)
      }
    }
  }


  @Test
  fun testVersionCatalogNodeDisplay() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val rootNode = tableModel.root as DefaultMutableTreeNode
      assertThat(rootNode.childCount).isEqualTo(4)

      val buildScriptNode = rootNode.getChildAt(0) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(buildScriptNode, 0) as String).isEqualTo("project (build script)")
      assertThat(tableModel.getValueAt(buildScriptNode, 1)).isEqualTo(ParsedValue.NotSet)
      assertThat(buildScriptNode.childCount).isNotEqualTo(0)
      assertThat(variablesTable.tree.isExpanded(TreePath(buildScriptNode.path))).isEqualTo(true)

      val versionCatalogNode = rootNode.getChildAt(1) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(versionCatalogNode, 0) as String).isEqualTo("Default version catalog: libs (libs.versions.toml)")
      assertThat(tableModel.getValueAt(versionCatalogNode, 1)).isEqualTo(ParsedValue.NotSet)
      assertThat(versionCatalogNode.childCount).isNotEqualTo(0)
      assertThat(variablesTable.tree.isExpanded(TreePath(versionCatalogNode.path))).isEqualTo(false)

      val projectNode = rootNode.getChildAt(2) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(projectNode, 0) as String).isEqualTo("project (project)")
      assertThat(tableModel.getValueAt(projectNode, 1)).isEqualTo(ParsedValue.NotSet)
      assertThat(projectNode.childCount).isNotEqualTo(0)
      assertThat(variablesTable.tree.isExpanded(TreePath(projectNode.path))).isEqualTo(false)
    }
  }

  @Test
  fun testMultiVersionCatalogNodeDisplay() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_MULTI_VERSION_CATALOG_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val rootNode = tableModel.root as DefaultMutableTreeNode
      assertThat(rootNode.childCount).isEqualTo(5)

      val buildScriptNode = rootNode.getChildAt(0) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(buildScriptNode, 0) as String).isEqualTo("project (build script)")
      assertThat(tableModel.getValueAt(buildScriptNode, 1)).isEqualTo(ParsedValue.NotSet)
      assertThat(buildScriptNode.childCount).isNotEqualTo(0)
      assertThat(variablesTable.tree.isExpanded(TreePath(buildScriptNode.path))).isEqualTo(true)

      val versionCatalogNode = rootNode.getChildAt(1) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(versionCatalogNode, 0) as String).isEqualTo("Default version catalog: libs (libs.versions.toml)")
      assertThat(tableModel.getValueAt(versionCatalogNode, 1)).isEqualTo(ParsedValue.NotSet)
      assertThat(versionCatalogNode.childCount).isNotEqualTo(0)
      assertThat(variablesTable.tree.isExpanded(TreePath(versionCatalogNode.path))).isEqualTo(false)

      val secondVersionCatalogNode = rootNode.getChildAt(2) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(secondVersionCatalogNode, 0) as String).isEqualTo("Version catalog: libsTest (libsTest.versions.toml)")
      assertThat(tableModel.getValueAt(secondVersionCatalogNode, 1)).isEqualTo(ParsedValue.NotSet)
      assertThat(secondVersionCatalogNode.childCount).isNotEqualTo(0)
      assertThat(variablesTable.tree.isExpanded(TreePath(secondVersionCatalogNode.path))).isEqualTo(false)

      val projectNode = rootNode.getChildAt(3) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(projectNode, 0) as String).isEqualTo("project (project)")
      assertThat(tableModel.getValueAt(projectNode, 1)).isEqualTo(ParsedValue.NotSet)
      assertThat(projectNode.childCount).isNotEqualTo(0)
      assertThat(variablesTable.tree.isExpanded(TreePath(projectNode.path))).isEqualTo(false)
    }
  }

  @Test
  fun testTreeStructure() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val rootNode = (tableModel.root as ShadowedTreeNode).childNodes.toList()[2]
      assertThat(
        rootNode.testStructure().toString().trimIndent()).isEqualTo(
          """
        :app
            myVariable
            variable1
            anotherVariable
            varInt
            varBool
            varRefString
            varProGuardFiles
                0
                1
                (null)
            localList
                0
                1
                (null)
            localMap
                KTSApp
                LocalApp
                (null)
            valVersion
            versionVal
            moreVariable
            mapVariable
                a
                b
                (null)
            (null)
            """.trimIndent()
        )
    }
  }

  @Test
  fun testTreeStructure_addVariable() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val appModuleVariables = psProject.findModuleByName("app")?.variables
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      appModuleVariables?.addNewListVariable("varList")

      val rootNode = (tableModel.root as ShadowedTreeNode).childNodes.toList()[2]
      assertThat(
        rootNode.testStructure().toString().trimIndent()).isEqualTo(
          """
        :app
            myVariable
            variable1
            anotherVariable
            varInt
            varBool
            varRefString
            varProGuardFiles
                0
                1
                (null)
            localList
                0
                1
                (null)
            localMap
                KTSApp
                LocalApp
                (null)
            valVersion
            versionVal
            moreVariable
            mapVariable
                a
                b
                (null)
            varList
                (null)
            (null)
            """.trimIndent()
        )
    }
  }

  @Test
  fun testTreeStructure_removeListItem() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val appModuleVariables = psProject.findModuleByName("app")?.variables
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      appModuleVariables?.getVariable("varProGuardFiles")?.listItems?.findElement(0)?.delete()

      val rootNode = (tableModel.root as ShadowedTreeNode).childNodes.toList()[2]
      assertThat(
        rootNode.testStructure().toString().trimIndent()).isEqualTo(
          """
        :app
            myVariable
            variable1
            anotherVariable
            varInt
            varBool
            varRefString
            varProGuardFiles
                0
                (null)
            localList
                0
                1
                (null)
            localMap
                KTSApp
                LocalApp
                (null)
            valVersion
            versionVal
            moreVariable
            mapVariable
                a
                b
                (null)
            (null)
            """.trimIndent()
        )
    }
  }

  @Test
  fun testStringVariableNodeDisplay() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
      val variableNode =
        appNode.children().asSequence().find { "anotherVariable" == (it as VariableNode).toString() } as VariableNode
      variablesTable.tree.expandPath(TreePath(appNode.path))

      assertThat(variableNode.variable.value).isEqualTo("3.0.1".asParsed<Any>())
      assertThat(variableNode.childCount).isEqualTo(0)
      assertThat(tableModel.getValueAt(variableNode, 0) as String).isEqualTo("anotherVariable")
      assertThat(tableModel.getValueAt(variableNode, 1)).isEqualTo("3.0.1".asParsed())

      val row = variablesTable.tree.getRowForPath(TreePath(variableNode.path))
      for (column in 0..1) {
        val component = variablesTable.getCellRenderer(row, column)
          .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row, column), false, false, row, column)
        assertThat(component.background).isEqualTo(variablesTable.background)
      }
    }
  }

  @Test
  fun testVersionCatalogVariableNodeDisplay() {
      val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
      preparedProject.open { project ->
        val psProject = PsProjectImpl(project)
        val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
        val tableModel = variablesTable.tableModel

        val catalogNode = (tableModel.root as DefaultMutableTreeNode).defaultVersionCatalogChild as DefaultMutableTreeNode
        val variableNode =
          catalogNode.children().asSequence().find { "constraint-layout" == (it as CatalogVariableNode).toString() } as CatalogVariableNode
        variablesTable.tree.expandPath(TreePath(catalogNode.path))

        assertThat(variableNode.variable.value).isEqualTo("1.0.2".asParsed<Any>())
        assertThat(variableNode.childCount).isEqualTo(0)
        assertThat(tableModel.getValueAt(variableNode, 0) as String).isEqualTo("constraint-layout")
        assertThat(tableModel.getValueAt(variableNode, 1)).isEqualTo("1.0.2".asParsed<Any>())

        val row = variablesTable.tree.getRowForPath(TreePath(variableNode.path))
        for (column in 0..1) {
          val component = variablesTable.getCellRenderer(row, column)
            .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row, column), false, false, row, column)
          assertThat(component.background).isEqualTo(variablesTable.background)
        }
      }
  }

  @Test
  fun testBooleanVariableNodeDisplay() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
      val variableNode =
        appNode.children().asSequence().find { "varBool" == (it as VariableNode).toString() } as VariableNode
      variablesTable.tree.expandPath(TreePath(variableNode.path))

      assertThat(variableNode.variable.value).isEqualTo(true.asParsed<Any>())
      assertThat(variableNode.childCount).isEqualTo(0)
      assertThat(tableModel.getValueAt(variableNode, 0) as String).isEqualTo("varBool")
      assertThat(tableModel.getValueAt(variableNode, 1)).isEqualTo(true.asParsed())
    }
  }

  @Test
  fun testVariableVariableNodeDisplay() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
      val variableNode =
        appNode.children().asSequence().find { "varRefString" == (it as VariableNode).toString() } as VariableNode
      variablesTable.tree.expandPath(TreePath(variableNode.path))

      assertThat(
        variableNode.variable.value
      ).isEqualTo(ParsedValue.Set.Parsed("1.3", DslText.Reference("variable1")))
      assertThat(variableNode.variable.value.maybeValue is String).isEqualTo(true)
      assertThat(variableNode.childCount).isEqualTo(0)
      assertThat(tableModel.getValueAt(variableNode, 0) as String).isEqualTo("varRefString")
      assertThat(tableModel.getValueAt(variableNode, 1)).isEqualTo(("variable1" to "1.3").asParsed())
    }
  }

  @Test
  fun testListNodeDisplay() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
      variablesTable.tree.expandPath(TreePath(appNode.path))

      val listNode =
        appNode.children().asSequence().find { "varProGuardFiles" == (it as VariableNode).toString() } as VariableNode
      assertThat(listNode.variable.isList()).isEqualTo(true)
      assertThat(listNode.childCount).isEqualTo(3)
      assertThat(tableModel.getValueAt(listNode, 0) as String).isEqualTo("varProGuardFiles")
      assertThat(
        tableModel.getValueAt(listNode, 1)
      ).isEqualTo(listOf("proguard-rules.txt".asParsed(), "proguard-rules2.txt".asParsed()).asParsed())

      val firstElementNode = listNode.getChildAt(0)
      assertThat(tableModel.getValueAt(firstElementNode, 0) as String).isEqualTo("0")
      assertThat(tableModel.getValueAt(firstElementNode, 1)).isEqualTo("proguard-rules.txt".asParsed())

      val secondElementNode = listNode.getChildAt(1)
      assertThat(tableModel.getValueAt(secondElementNode, 0) as String).isEqualTo("1")
      assertThat(tableModel.getValueAt(secondElementNode, 1)).isEqualTo("proguard-rules2.txt".asParsed())

      val emptyElement = listNode.getChildAt(2)
      assertThat(tableModel.getValueAt(emptyElement, 0) as String).isEqualTo("")
      assertThat(tableModel.getValueAt(emptyElement, 1)).isEqualTo(ParsedValue.NotSet)

      val row = variablesTable.tree.getRowForPath(TreePath(listNode.path))
      for (column in 0..1) {
        val component = variablesTable.getCellRenderer(row, column)
          .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row, column), false, false, row, column)
        assertThat(component.background).isEqualTo(variablesTable.background)
      }
    }
  }

  @Test
  fun testMapNodeDisplay() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
      variablesTable.tree.expandPath(TreePath(appNode.path))

      val mapNode =
        appNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
      assertThat(mapNode.variable.isMap()).isEqualTo(true)
      assertThat(mapNode.childCount).isEqualTo(3)
      assertThat(tableModel.getValueAt(mapNode, 0) as String).isEqualTo("mapVariable")
      assertThat(
        tableModel.getValueAt(mapNode, 1)
      ).isEqualTo(mapOf("a" to "\"double\" quotes".asParsed(), "b" to "'single' quotes".asParsed()).asParsed())

      val firstElementNode = mapNode.getChildAt(0)
      assertThat(tableModel.getValueAt(firstElementNode, 0) as String).isEqualTo("a")
      assertThat(tableModel.getValueAt(firstElementNode, 1)).isEqualTo("\"double\" quotes".asParsed())

      val secondElementNode = mapNode.getChildAt(1)
      assertThat(tableModel.getValueAt(secondElementNode, 0) as String).isEqualTo("b")
      assertThat(tableModel.getValueAt(secondElementNode, 1)).isEqualTo("'single' quotes".asParsed())

      val emptyElement = mapNode.getChildAt(2)
      assertThat(tableModel.getValueAt(emptyElement, 0) as String).isEqualTo("")
      assertThat(tableModel.getValueAt(emptyElement, 1)).isEqualTo(ParsedValue.NotSet)

      val row = variablesTable.tree.getRowForPath(TreePath(mapNode.path))
      for (column in 0..1) {
        val component = variablesTable.getCellRenderer(row, column)
          .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row, column), false, false, row, column)
        assertThat(component.background).isEqualTo(variablesTable.background)
      }
    }
  }

  @Test
  fun testModuleNodeRename() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild
      assertThat(tableModel.isCellEditable(appNode, 0)).isEqualTo(false)
    }
  }

  @Test
  fun testVariableNodeRename() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val variableNode = appNode.children().asSequence().find { "anotherVariable" == (it as VariableNode).toString() } as VariableNode
      variablesTable.tree.expandPath(TreePath(variableNode.path))
      assertThat(tableModel.getValueAt(variableNode, 0) as String).isEqualTo("anotherVariable")
      assertThat(tableModel.isCellEditable(variableNode, 0)).isEqualTo(true)

      tableModel.setValueAt("renamed", variableNode, 0)
      assertThat(tableModel.getValueAt(variableNode, 0) as String).isEqualTo("renamed")

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val variableNames = newAppNode.children().asSequence().map { it.toString() }.toList()
      assertThat(variableNames).contains("renamed")
      assertThat(variableNames).doesNotContain("anotherVariable")
    }
  }

  @Test
  fun testListNodeEditable() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
      variablesTable.tree.expandPath(TreePath(appNode.path))

      val listNode = appNode.children().asSequence().find { "varProGuardFiles" == (it as VariableNode).toString() } as VariableNode
      assertThat(listNode.variable.isList()).isEqualTo(true)

      variablesTable.tree.expandPath(TreePath(listNode.path))
      val firstElementNode = listNode.getChildAt(0)
      assertThat(tableModel.isCellEditable(firstElementNode, 0)).isEqualTo(false)
    }
  }

  @Test
  fun testMapNodeRename() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      variablesTable.tree.expandPath(TreePath(appNode.path))

      val mapNode = appNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
      assertThat(mapNode.variable.isMap()).isEqualTo(true)

      variablesTable.tree.expandPath(TreePath(mapNode.path))
      val firstElementNode = mapNode.getChildAt(0)
      assertThat(tableModel.getValueAt(firstElementNode, 0) as String).isEqualTo("a")
      assertThat(tableModel.isCellEditable(firstElementNode, 0)).isEqualTo(true)

      tableModel.setValueAt("renamed", firstElementNode, 0)
      assertThat(tableModel.getValueAt(firstElementNode, 0) as String).isEqualTo("renamed")

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newMapNode = newAppNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
      val keyNames = newMapNode.children().asSequence().map { it.toString() }.toList()
      assertThat(keyNames).contains("renamed")
      assertThat(keyNames).doesNotContain("a")
    }
  }

  @Test
  fun testModuleNodeSetValue() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild
      assertThat(tableModel.isCellEditable(appNode, 1)).isEqualTo(false)
    }
  }

  @Test
  fun testVariableNodeSetValue() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val variableNode = appNode.children().asSequence().find { "anotherVariable" == (it as VariableNode).toString() } as VariableNode
      variablesTable.tree.expandPath(TreePath(variableNode.path))
      assertThat(tableModel.getValueAt(variableNode, 1)).isEqualTo("3.0.1".asParsed())
      assertThat(tableModel.isCellEditable(variableNode, 1)).isEqualTo(true)

      tableModel.setValueAt("3.0.1".asParsed().annotated(), variableNode, 1)
      assertThat(variableNode.variable.parent.isModified).isEqualTo(false)

      tableModel.setValueAt("new value".asParsed().annotated(), variableNode, 1)
      assertThat(tableModel.getValueAt(variableNode, 1)).isEqualTo("new value".asParsed())

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newVariableNode = newAppNode.children().asSequence().find { "anotherVariable" == (it as VariableNode).toString() } as VariableNode
      assertThat(newVariableNode.getUnresolvedValue(false)).isEqualTo("new value".asParsed<Any>())
    }
  }

  @Test
  fun testListNodeSetValue() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      variablesTable.tree.expandPath(TreePath(appNode.path))

      val listNode = appNode.children().asSequence().find { "varProGuardFiles" == (it as VariableNode).toString() } as VariableNode
      assertThat(listNode.variable.isList()).isEqualTo(true)
      assertThat(tableModel.isCellEditable(listNode, 1)).isEqualTo(false)

      variablesTable.tree.expandPath(TreePath(listNode.path))
      val firstElementNode = listNode.getChildAt(0) as ListItemNode
      assertThat(tableModel.isCellEditable(listNode, 1)).isEqualTo(false)
      assertThat(tableModel.getValueAt(firstElementNode, 1)).isEqualTo("proguard-rules.txt".asParsed())
      assertThat(tableModel.isCellEditable(firstElementNode, 1)).isEqualTo(true)

      tableModel.setValueAt("proguard-rules.txt".asParsed().annotated(), firstElementNode, 1)
      assertThat(firstElementNode.variable.parent.isModified).isEqualTo(false)

      tableModel.setValueAt("new value".asParsed().annotated(), firstElementNode, 1)
      assertThat(tableModel.getValueAt(firstElementNode, 1)).isEqualTo("new value".asParsed())

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newListNode = newAppNode.children().asSequence().find { "varProGuardFiles" == (it as VariableNode).toString() } as VariableNode
      assertThat((newListNode.getChildAt(0) as ListItemNode).getUnresolvedValue(false)).isEqualTo("new value".asParsed<Any>())
    }
  }

  @Test
  fun testMapNodeSetValue() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      variablesTable.tree.expandPath(TreePath(appNode.path))

      val mapNode = appNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
      assertThat(mapNode.variable.isMap()).isEqualTo(true)
      assertThat(tableModel.isCellEditable(mapNode, 1)).isEqualTo(false)

      variablesTable.tree.expandPath(TreePath(mapNode.path))
      val firstElementNode = mapNode.getChildAt(0) as MapItemNode
      assertThat(tableModel.isCellEditable(mapNode, 1)).isEqualTo(false)
      assertThat(tableModel.getValueAt(firstElementNode, 1)).isEqualTo("\"double\" quotes".asParsed())
      assertThat(tableModel.isCellEditable(firstElementNode, 1)).isEqualTo(true)

      tableModel.setValueAt("\"\"double\" quotes\"".asParsed().annotated(), firstElementNode, 1)
      assertThat(firstElementNode.variable.parent.isModified).isEqualTo(false)

      tableModel.setValueAt("new value".asParsed().annotated(), firstElementNode, 1)
      assertThat(tableModel.getValueAt(firstElementNode, 1)).isEqualTo("new value".asParsed())

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newMapNode = newAppNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
      assertThat((newMapNode.getChildAt(0) as MapItemNode).getUnresolvedValue(false)).isEqualTo("new value".asParsed<Any>())
    }
  }

  @Test
  fun testAddSimpleVariable() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      assertThat(appNode.children().asSequence().map { it.toString() }.toSet()).doesNotContain("newVariable")

      variablesTable.selectNode(appNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.STRING)
      variablesTable.simulateTextInput("newVariable")

      val variableNode = appNode.children().asSequence().find { "newVariable" == (it as VariableNode).toString() } as VariableNode
      variableNode.setValue("new value".asParsed())

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newVariableNode = newAppNode.children().asSequence().find { "newVariable" == (it as VariableNode).toString() } as VariableNode
      assertThat(newVariableNode.getUnresolvedValue(false)).isEqualTo("new value".asParsed<Any>())
    }
  }

  @Test
  fun testAddAndEditSimpleVariable() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)

      val buildScriptNode =
        (variablesTable.tableModel.root as DefaultMutableTreeNode).children().asSequence().find { it.toString() == ":app" } as ModuleNode
      assertThat(buildScriptNode.children().asSequence().map { it.toString() }.toSet()).doesNotContain("newVariable")

      //create variable
      variablesTable.selectNode(buildScriptNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.STRING)
      variablesTable.simulateTextInput("newVariable")

      setVariableValue(buildScriptNode, "newVariable", "new value 1")
      psProject.applyAllChanges()

      assertVariableValue(psProject, "newVariable", "new value 1") { it.toString() == ":app" }

      // Second change
      assertThat(buildScriptNode.children().asSequence().map { it.toString() }.toSet()).contains("newVariable")

      setVariableValue(buildScriptNode, "newVariable", "new value 2")
      psProject.applyAllChanges()
      assertVariableValue(psProject, "newVariable", "new value 2") { it.toString() == ":app" }

      // Emulate opening PSD again and check value was applied
      val psProject2 = PsProjectImpl(project)
      assertVariableValue(psProject2, "newVariable", "new value 2") { it.toString() == ":app" }
    }
  }

  @Test
  fun testAddAndEditBuildscriptSimpleVariable() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)

      val buildScriptNode = (variablesTable.tableModel.root as DefaultMutableTreeNode).children()
        .asSequence()
        .find { it.toString().contains("(build script)") } as ModuleNode
      assertThat(buildScriptNode.children().asSequence().map { it.toString() }.toSet()).doesNotContain("newVariable")

      //create variable
      variablesTable.selectNode(buildScriptNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.STRING)
      variablesTable.simulateTextInput("newVariable")

      setVariableValue(buildScriptNode, "newVariable", "new value 1")
      psProject.applyAllChanges()

      assertVariableValue(psProject, "newVariable", "new value 1") { it.toString().contains("(build script)") }

      // Second change
      assertThat(buildScriptNode.children().asSequence().map { it.toString() }.toSet()).contains("newVariable")

      setVariableValue(buildScriptNode, "newVariable", "new value 2")
      psProject.applyAllChanges()
      assertVariableValue(psProject, "newVariable", "new value 2") { it.toString().contains("(build script)") }

      // Emulate opening PSD again and check value was applied
      val psProject2 = PsProjectImpl(project)
      assertVariableValue(psProject2, "newVariable", "new value 2") { it.toString().contains("(build script)") }
    }
  }

  private fun setVariableValue(node: VariablesTableNode, name: String, value: String) {
    val variableNode = node.children().asSequence().find { name == (it as BaseVariableNode).toString() } as BaseVariableNode
    variableNode.setValue(value.asParsed())
  }

  private fun assertVariableValue(psProject: PsProject, name: String, value: String, moduleSelector: (Any) -> Boolean) {
    val newTableModel1 = VariablesTable(psProject.ideProject, contextFor(psProject), psProject, projectRule.testRootDisposable, stub).tableModel
    val newModuleNode1 = (newTableModel1.root as DefaultMutableTreeNode).children().asSequence().find(
      moduleSelector) as ContainerNode
    val newVariableNode1 = newModuleNode1.children().asSequence().find { name == (it as BaseVariableNode).toString() } as BaseVariableNode
    assertThat(newVariableNode1.getUnresolvedValue(false)).isEqualTo(value.asParsed<Any>())
  }

  @Test
  fun testAddAndEditVersionCatalogVariable() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)

      val versionCatalogNode: VersionCatalogNode = (variablesTable.tableModel.root as DefaultMutableTreeNode).children().asSequence().find {
        it.toString().contains("libs")
      } as VersionCatalogNode
      assertThat(versionCatalogNode.children().asSequence().map(Any::toString).toSet())
        .isEqualTo(setOf("constraint-layout", "guava", "junit", ""))
      assertThat(versionCatalogNode.children().asSequence().map { it.toString() }.toSet()).doesNotContain("newVersion")

      //create variable
      variablesTable.selectNode(versionCatalogNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.STRING)
      variablesTable.simulateTextInput("newVersion")

      setVariableValue(versionCatalogNode, "newVersion", "1.2.3")
      psProject.applyAllChanges()

      assertVariableValue(psProject, "newVersion", "1.2.3") { it.toString().contains("libs") }

      // Second change
      assertThat(versionCatalogNode.children().asSequence().map { it.toString() }.toSet()).contains("newVersion")

      setVariableValue(versionCatalogNode, "newVersion", "2.3.4")
      psProject.applyAllChanges()
      assertVariableValue(psProject, "newVersion", "2.3.4") { it.toString().contains("libs") }

      // Emulate opening PSD again and check value was applied
      val psProject2 = PsProjectImpl(project)
      assertVariableValue(psProject2, "newVersion", "2.3.4") { it.toString().contains("libs") }
    }
  }

  @Test
  fun testValidationVariableName() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)

      val validationResults = mock(ValidationResultsKeeper::class.java)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, validationResults)


      val tableModel = variablesTable.tableModel
      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode

      variablesTable.selectNode(appNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.STRING)
      emulateInputAndAssertWarning(variablesTable, " ", "Variable name cannot have whitespaces.")
      variablesTable.editCellAt(variablesTable.getRowByNode(appNode) + 14, 0)
      emulateInputAndAssertWarning(variablesTable, "", "Variable name cannot be empty.")
      variablesTable.editCellAt(variablesTable.getRowByNode(appNode) + 14, 0)
      emulateInputAndAssertWarning(variablesTable, "abc.def", "Variable name cannot have dot.")
      variablesTable.editCellAt(variablesTable.getRowByNode(appNode) + 14, 0)
      emulateInputAndAssertWarning(variablesTable, "valVersion", "Duplicate variable name: 'valVersion'")
      // At the end we have two failed validation after stop editing.
      // We cannot handle duplicate variable name after editing because of existing PSD issue
      verify(validationResults, times(3)).updateValidationResult(true)
    }
  }

  @Test
  fun testValidationCatalogVariableName() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val validationResults = mock(ValidationResultsKeeper::class.java)

      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, validationResults)

      val versionCatalogNode: VersionCatalogNode = (variablesTable.tableModel.root as DefaultMutableTreeNode).children().asSequence().find {
        it.toString().contains("libs")
      } as VersionCatalogNode
      assertThat(versionCatalogNode.children().asSequence().map { it.toString() }.toSet()).doesNotContain("newVersion")

      val message = """Variable name must match the following regular expression: [a-z]([a-zA-Z0-9_-])+"""

      //create variable
      variablesTable.selectNode(versionCatalogNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.STRING)

      // valid name
      emulateInputAndAssertWarning(variablesTable, "guava-core", null)
      variablesTable.editCellAt(variablesTable.getRowByNode(versionCatalogNode) + 4, 0)
      emulateInputAndAssertWarning(variablesTable, "guava_core", null)
      variablesTable.editCellAt(variablesTable.getRowByNode(versionCatalogNode) + 4, 0)

      // invalid names
      emulateInputAndAssertWarning(variablesTable, " ", message)
      variablesTable.editCellAt(variablesTable.getRowByNode(versionCatalogNode) + 4, 0)
      emulateInputAndAssertWarning(variablesTable, "", message)
      variablesTable.editCellAt(variablesTable.getRowByNode(versionCatalogNode) + 4, 0)
      emulateInputAndAssertWarning(variablesTable, "guava.core", message)
      variablesTable.editCellAt(variablesTable.getRowByNode(versionCatalogNode) + 4, 0)
      emulateInputAndAssertWarning(variablesTable, "guava/core", message) // backslash
      variablesTable.editCellAt(variablesTable.getRowByNode(versionCatalogNode) + 4, 0)
      emulateInputAndAssertWarning(variablesTable, "a", message) // one symbol

      // duplicate
      variablesTable.editCellAt(variablesTable.getRowByNode(versionCatalogNode) + 4, 0)
      emulateInputAndAssertWarning(variablesTable, "guava", "Duplicate variable name: 'guava'")

      // At the end we have 4 failed validation after stop editing.
      // We cannot handle duplicate variable name after editing because of existing PSD issue
      verify(validationResults, times(5)).updateValidationResult(true)
    }
  }

    @Test
  fun testValidationCatalogVariableValue() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)

      val versionCatalogNode: VersionCatalogNode = (variablesTable.tableModel.root as DefaultMutableTreeNode).children().asSequence().find {
        it.toString().contains("libs")
      } as VersionCatalogNode
      assertThat(versionCatalogNode.children().asSequence().map(Any::toString).toSet())
        .isEqualTo(setOf("constraint-layout", "guava", "junit", ""))
      assertThat(versionCatalogNode.children().asSequence().map { it.toString() }.toSet()).doesNotContain("newVersion")

      //create variable
      variablesTable.selectNode(versionCatalogNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.STRING)
      variablesTable.simulateTextInput("newVersion")

      variablesTable.editCellAt(variablesTable.selectedRow,1)
      val textBox = variablesTable.editorComponent as JBTextField
      textBox.text = ""
      assertThat(textBox.getWarningMessage()).isEqualTo("Variable value cannot be empty.")
    }
  }

  private fun emulateInputAndAssertWarning(variablesTable: VariablesTable, input:String, expectedMessage:String?){
    variablesTable.simulateTextInput(input) { textBox ->
      assertThat(textBox.getWarningMessage()).isEqualTo(expectedMessage)
    }
  }

  // regression b/258243668
  @Test
  fun testAddVersionCatalogVariableAfterMultipleSelections() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)

      val versionCatalogNode: VersionCatalogNode = (variablesTable.tableModel.root as DefaultMutableTreeNode).children().asSequence().find {
        it.toString().contains("libs")
      } as VersionCatalogNode
      val buildScriptNode = (variablesTable.tableModel.root as DefaultMutableTreeNode).children()
        .asSequence()
        .find { it.toString().contains("(build script)") } as ModuleNode

      val addNewCatalogVariable = versionCatalogNode.lastChild as VariablesBaseNode
      //expand
      variablesTable.tree.expandRow(variablesTable.getRowByNode(versionCatalogNode))
      variablesTable.tree.expandRow(variablesTable.getRowByNode(buildScriptNode))

      //new catalog var, focus on build script then edit new catalog variable
      variablesTable.editNode(addNewCatalogVariable)

      variablesTable.editNode(buildScriptNode)
      // this will open popup with new variable options: simple, list, map
      assertThat(variablesTable.isEditing).isEqualTo(false)

      variablesTable.editNode(addNewCatalogVariable)
      // checking if we start editing for last node
      assertThat(variablesTable.isEditing).isEqualTo(true)
    }
  }

  @Test
  fun testAddList() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      assertThat(appNode.children().asSequence().map { it.toString() }.toSet()).doesNotContain("newList")

      variablesTable.selectNode(appNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.LIST)
      variablesTable.simulateTextInput("newList")

      val variableNode = appNode.children().asSequence().find { "newList" == (it as VariableNode).toString() } as VariableNode
      assertThat(variableNode.childCount).isEqualTo(1)
      assertThat(variablesTable.tree.isExpanded(TreePath(variableNode.path))).isEqualTo(true)

      tableModel.setValueAt("list item".asParsed().annotated(), variableNode.getChildAt(0), 1)
      assertThat(variableNode.childCount).isEqualTo(2)

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newListNode = newAppNode.children().asSequence().find { "newList" == (it as VariableNode).toString() } as VariableNode

      val firstElementNode = newListNode.getChildAt(0)
      assertThat(tableModel.getValueAt(firstElementNode, 0) as String).isEqualTo("0")
      assertThat(tableModel.getValueAt(firstElementNode, 1)).isEqualTo("list item".asParsed())

      val secondElementNode = newListNode.getChildAt(1)
      assertThat(tableModel.getValueAt(secondElementNode, 0) as String).isEqualTo("")
      assertThat(tableModel.getValueAt(secondElementNode, 1)).isEqualTo(ParsedValue.NotSet)
    }
  }

  @Test
  fun testAddMap() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      assertThat(appNode.children().asSequence().map { it.toString() }.toSet()).doesNotContain("newMap")

      variablesTable.selectNode(appNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.MAP)
      variablesTable.simulateTextInput("newMap")

      val variableNode = appNode.children().asSequence().find { "newMap" == (it as VariableNode).toString() } as VariableNode
      assertThat(variableNode.childCount).isEqualTo(1)
      assertThat(variablesTable.tree.isExpanded(TreePath(variableNode.path))).isEqualTo(true)

      tableModel.setValueAt("key", variableNode.getChildAt(0), 0)
      tableModel.setValueAt("value".asParsed().annotated(), variableNode.getChildAt(0), 1)
      assertThat(variableNode.childCount).isEqualTo(2)

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newMapNode = newAppNode.children().asSequence().find { "newMap" == (it as VariableNode).toString() } as VariableNode

      val firstElementNode = newMapNode.getChildAt(0)
      assertThat(tableModel.getValueAt(firstElementNode, 0) as String).isEqualTo("key")
      assertThat(tableModel.getValueAt(firstElementNode, 1)).isEqualTo("value".asParsed())

      val secondElementNode = newMapNode.getChildAt(1)
      assertThat(tableModel.getValueAt(secondElementNode, 0) as String).isEqualTo("")
      assertThat(tableModel.getValueAt(secondElementNode, 1)).isEqualTo(ParsedValue.NotSet)
    }
  }

  @Test
  fun testAddEmptyVariable() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      assertThat(appNode.childCount).isEqualTo(14)

      variablesTable.selectNode(appNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.STRING)
      assertThat(appNode.childCount).isEqualTo(14)
      variablesTable.editingStopped(null)
      assertThat(appNode.childCount).isEqualTo(14)

      variablesTable.selectNode(appNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.STRING)
      assertThat(appNode.childCount).isEqualTo(14)
      variablesTable.editingCanceled(null)
      assertThat(appNode.childCount).isEqualTo(14)
    }
  }

  @Test
  fun testVariableNodeDelete() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      variablesTable.tree.expandPath(TreePath(appNode.path))
      val childCount = appNode.childCount
      val variableNode = appNode.children().asSequence().find { "anotherVariable" == (it as VariableNode).toString() } as VariableNode
      variablesTable.selectNode(variableNode)
      variablesTable.deleteSelectedVariables()

      val variableNames = appNode.children().asSequence().map { it.toString() }.toList()
      assertThat(variableNames).doesNotContain("anotherVariable")
      assertThat(appNode.childCount).isEqualTo(childCount - 1)

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newVariableNames = newAppNode.children().asSequence().map { it.toString() }.toList()
      assertThat(newVariableNames).doesNotContain("anotherVariable")
      assertThat(newAppNode.childCount).isEqualTo(childCount - 1)
    }
  }

  @Test
  fun testCatalogVersionDelete() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).defaultVersionCatalogChild as VersionCatalogNode
      variablesTable.tree.expandPath(TreePath(appNode.path))
      val childCount = appNode.childCount
      val variableNode = appNode.children().asSequence().find { "constraint-layout" == (it as CatalogVariableNode).toString() } as CatalogVariableNode
      variablesTable.selectNode(variableNode)
      variablesTable.deleteSelectedVariables()

      val variableNames = appNode.children().asSequence().map { it.toString() }.toList()
      assertThat(variableNames).doesNotContain("constraint-layout")
      assertThat(appNode.childCount).isEqualTo(childCount - 1)

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).defaultVersionCatalogChild as VersionCatalogNode
      val newVariableNames = newAppNode.children().asSequence().map { it.toString() }.toList()
      assertThat(newVariableNames).doesNotContain("constraint-layout")
      assertThat(newAppNode.childCount).isEqualTo(childCount - 1)
    }
  }

  @Test
  fun testListNodeDelete() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      variablesTable.tree.expandPath(TreePath(appNode.path))

      val listNode = appNode.children().asSequence().find { "varProGuardFiles" == (it as VariableNode).toString() } as VariableNode
      assertThat(listNode.variable.isList()).isEqualTo(true)
      val childCount = listNode.childCount

      variablesTable.tree.expandPath(TreePath(listNode.path))
      val firstElementNode = listNode.getChildAt(0) as ListItemNode
      assertThat(tableModel.getValueAt(firstElementNode, 0) as String).isEqualTo("0")
      assertThat(tableModel.getValueAt(firstElementNode, 1)).isEqualTo("proguard-rules.txt".asParsed())

      variablesTable.selectNode(firstElementNode)
      variablesTable.deleteSelectedVariables()

      val listNodeFirstChild = listNode.getChildAt(0) as ListItemNode
      assertThat(tableModel.getValueAt(listNodeFirstChild, 0) as String).isEqualTo("0")
      assertThat(tableModel.getValueAt(listNodeFirstChild, 1)).isEqualTo("proguard-rules2.txt".asParsed())
      assertThat(listNode.childCount).isEqualTo(childCount - 1)

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newListNode = newAppNode.children().asSequence().find { "varProGuardFiles" == (it as VariableNode).toString() } as VariableNode
      variablesTable.tree.expandPath(TreePath(listNode.path))
      val newFirstElementNode = newListNode.getChildAt(0) as ListItemNode
      assertThat(tableModel.getValueAt(newFirstElementNode, 0) as String).isEqualTo("0")
      assertThat(tableModel.getValueAt(newFirstElementNode, 1)).isEqualTo("proguard-rules2.txt".asParsed())
      assertThat(newListNode.childCount).isEqualTo(childCount - 1)
    }
  }

  // regression b/258712110
  @Test
  fun testVersionCatalogVariableEditorIsInputText() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)

      val versionCatalogNode: VersionCatalogNode = (variablesTable.tableModel.root as DefaultMutableTreeNode).children().asSequence().find {
        it.toString().contains("libs")
      } as VersionCatalogNode
      //create variable
      variablesTable.selectNode(versionCatalogNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.STRING)

      val rowIndex = variablesTable.getRowByNode(versionCatalogNode.lastChild as VariablesBaseNode)
      variablesTable.simulateTextInput("newVersion")
      variablesTable.editCellAt(rowIndex, 1)

      assertThat(variablesTable.editorComponent).isInstanceOf(JBTextField::class.java)
    }
  }

  @Test
  fun testMapNodeDelete() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      variablesTable.tree.expandPath(TreePath(appNode.path))

      val mapNode = appNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
      assertThat(mapNode.variable.isMap()).isEqualTo(true)
      val childCount = mapNode.childCount

      variablesTable.tree.expandPath(TreePath(mapNode.path))
      val firstElementNode = mapNode.getChildAt(0) as MapItemNode
      assertThat(tableModel.getValueAt(firstElementNode, 0) as String).isEqualTo("a")
      assertThat(tableModel.getValueAt(firstElementNode, 1)).isEqualTo("\"double\" quotes".asParsed())

      variablesTable.selectNode(firstElementNode)
      variablesTable.deleteSelectedVariables()

      val mapNodeFirstChild = mapNode.getChildAt(0) as MapItemNode
      assertThat(tableModel.getValueAt(mapNodeFirstChild, 0) as String).isEqualTo("b")
      assertThat(tableModel.getValueAt(mapNodeFirstChild, 1)).isEqualTo("'single' quotes".asParsed())
      assertThat(mapNode.childCount).isEqualTo(childCount - 1)

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable, stub).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newMapNode = newAppNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
      val newFirstElementNode = mapNode.getChildAt(0) as MapItemNode
      assertThat(tableModel.getValueAt(newFirstElementNode, 0) as String).isEqualTo("b")
      assertThat(tableModel.getValueAt(newFirstElementNode, 1)).isEqualTo("'single' quotes".asParsed())
      assertThat(newMapNode.childCount).isEqualTo(childCount - 1)
    }
  }
}

private val DefaultMutableTreeNode.appModuleChild: Any
  get() = children().asSequence().find { it.toString() == ":app" } as ModuleNode

private val DefaultMutableTreeNode.defaultVersionCatalogChild: Any
  get() = children().asSequence().find { it.toString().contains("libs") } as VersionCatalogNode

private fun PsProject.applyAllChanges() {
  if (isModified) {
    applyChanges()
  }
  forEachModule({ module ->
    if (module.isModified) {
      module.applyChanges()
    }
  })
}

private fun VariablesTable.simulateTextInput(input: String) {
  simulateTextInput(input) {}
}

private fun VariablesTable.simulateTextInput(input: String, f:(JBTextField) -> Unit) {
  val editorComp = editorComponent as JPanel
  val textBox = editorComp.components.first { it is JBTextField } as JBTextField
  textBox.text = input
  f.invoke(textBox)
  editingStopped(null)
}

private fun VariablesTable.selectNode(node: VariablesTableNode) {
  val selectedRow = tree.getRowForPath(TreePath(node.path))
  selectionModel.setSelectionInterval(selectedRow, selectedRow)
}

private fun VariablesTable.editNode(node: VariablesTableNode) {
  this.editCellAt(getRowByNode(node),0)
}

private fun VariablesTable.getRowByNode(node: VariablesTableNode): Int =
  tree.getRowForPath(TreePath(node.path))

private fun JBTextField.getWarningMessage(): String? {
  val maybeComponentValidator = ComponentValidator.getInstance(this)
  if(maybeComponentValidator.isPresent){
    val info = maybeComponentValidator.get().validationInfo
    return info?.message
  }
  return null
}

private fun PsVariable.isList() = value.maybeLiteralValue is List<*>
private fun PsVariable.isMap() = value.maybeLiteralValue is Map<*, *>
