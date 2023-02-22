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

import com.android.tools.idea.flags.StudioFlags
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
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBTextField
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.not
import org.hamcrest.Matchers.instanceOf
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

@RunsInEdt
class VariablesTableTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  private var defaultTestDialog: TestDialog? = null

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
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val rootNode = tableModel.root as DefaultMutableTreeNode
      assertThat(rootNode.childCount, equalTo(11))

      val buildScriptNode = rootNode.getChildAt(0) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(buildScriptNode, 0) as String, equalTo("project (build script)"))
      assertThat(tableModel.getValueAt(buildScriptNode, 1), equalTo(ParsedValue.NotSet))
      assertThat(buildScriptNode.childCount, not(0))
      assertThat(variablesTable.tree.isExpanded(TreePath(buildScriptNode.path)), equalTo(true))

      val projectNode = rootNode.getChildAt(1) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(projectNode, 0) as String, equalTo("project (project)"))
      assertThat(tableModel.getValueAt(projectNode, 1), equalTo(ParsedValue.NotSet))
      assertThat(projectNode.childCount, not(0))
      assertThat(variablesTable.tree.isExpanded(TreePath(projectNode.path)), equalTo(false))

      val appNode = rootNode.getChildAt(2) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(appNode, 0) as String, equalTo(":app"))
      assertThat(tableModel.getValueAt(appNode, 1), equalTo(ParsedValue.NotSet))
      assertThat(appNode.childCount, not(0))
      assertThat(variablesTable.tree.isExpanded(TreePath(appNode.path)), equalTo(false))

      val dynFeatureNode = rootNode.getChildAt(3) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(dynFeatureNode, 0) as String, equalTo(":dyn_feature"))
      assertThat(tableModel.getValueAt(dynFeatureNode, 1), equalTo(ParsedValue.NotSet))
      assertThat(dynFeatureNode.childCount, equalTo(1))
      assertThat(variablesTable.tree.isExpanded(TreePath(dynFeatureNode.path)), equalTo(false))

      val javNode = rootNode.getChildAt(4) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(javNode, 0) as String, equalTo(":jav"))
      assertThat(tableModel.getValueAt(javNode, 1), equalTo(ParsedValue.NotSet))
      assertThat(javNode.childCount, equalTo(1))
      assertThat(variablesTable.tree.isExpanded(TreePath(javNode.path)), equalTo(false))

      val libNode = rootNode.getChildAt(5) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(libNode, 0) as String, equalTo(":lib"))
      assertThat(tableModel.getValueAt(libNode, 1), equalTo(ParsedValue.NotSet))
      assertThat(libNode.childCount, equalTo(1))
      assertThat(variablesTable.tree.isExpanded(TreePath(libNode.path)), equalTo(false))

      val nested1Node = rootNode.getChildAt(6) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(nested1Node, 0) as String, equalTo(":nested1"))
      assertThat(tableModel.getValueAt(nested1Node, 1), equalTo(ParsedValue.NotSet))
      assertThat(nested1Node.childCount, equalTo(1))
      assertThat(variablesTable.tree.isExpanded(TreePath(nested1Node.path)), equalTo(false))

      val nested1DeepNode = rootNode.getChildAt(7) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(nested1DeepNode, 0) as String, equalTo(":nested1:deep"))
      assertThat(tableModel.getValueAt(nested1DeepNode, 1), equalTo(ParsedValue.NotSet))
      assertThat(nested1DeepNode.childCount, equalTo(1))
      assertThat(variablesTable.tree.isExpanded(TreePath(nested1DeepNode.path)), equalTo(false))

      val nested2Node = rootNode.getChildAt(8) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(nested2Node, 0) as String, equalTo(":nested2"))
      assertThat(tableModel.getValueAt(nested2Node, 1), equalTo(ParsedValue.NotSet))
      assertThat(nested2Node.childCount, equalTo(1))
      assertThat(variablesTable.tree.isExpanded(TreePath(nested2Node.path)), equalTo(false))

      val nested2DeepNode = rootNode.getChildAt(9) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(nested2DeepNode, 0) as String, equalTo(":nested2:deep"))
      assertThat(tableModel.getValueAt(nested2DeepNode, 1), equalTo(ParsedValue.NotSet))
      assertThat(nested2DeepNode.childCount, equalTo(1))
      assertThat(variablesTable.tree.isExpanded(TreePath(nested2DeepNode.path)), equalTo(false))

      val nested2Deep2Node = rootNode.getChildAt(10) as DefaultMutableTreeNode
      assertThat(tableModel.getValueAt(nested2Deep2Node, 0) as String, equalTo(":nested2:trans:deep2"))
      assertThat(tableModel.getValueAt(nested2Deep2Node, 1), equalTo(ParsedValue.NotSet))
      assertThat(nested2Deep2Node.childCount, equalTo(1))
      assertThat(variablesTable.tree.isExpanded(TreePath(nested2Deep2Node.path)), equalTo(false))

      val row = variablesTable.tree.getRowForPath(TreePath(appNode.path))
      for (column in 0..1) {
        val component = variablesTable.getCellRenderer(row, column)
          .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row, column), false, false, row, column)
        assertThat(component.background, equalTo(variablesTable.background))
      }
    }
  }


  @Test
  fun testVersionCatalogNodeDisplay() {
    StudioFlags.GRADLE_VERSION_CATALOG_EXTENDED_SUPPORT.override(true)
    try {
      val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
      preparedProject.open { project ->
        val psProject = PsProjectImpl(project)
        val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
        val tableModel = variablesTable.tableModel

        val rootNode = tableModel.root as DefaultMutableTreeNode
        assertThat(rootNode.childCount, equalTo(4))

        val buildScriptNode = rootNode.getChildAt(0) as DefaultMutableTreeNode
        assertThat(tableModel.getValueAt(buildScriptNode, 0) as String, equalTo("project (build script)"))
        assertThat(tableModel.getValueAt(buildScriptNode, 1), equalTo(ParsedValue.NotSet))
        assertThat(buildScriptNode.childCount, not(0))
        assertThat(variablesTable.tree.isExpanded(TreePath(buildScriptNode.path)), equalTo(true))

        val versionCatalogNode = rootNode.getChildAt(1) as DefaultMutableTreeNode
        assertThat(tableModel.getValueAt(versionCatalogNode, 0) as String,
                   equalTo("Default version catalog: libs (libs.versions.toml)"))
        assertThat(tableModel.getValueAt(versionCatalogNode, 1), equalTo(ParsedValue.NotSet))
        assertThat(versionCatalogNode.childCount, not(0))
        assertThat(variablesTable.tree.isExpanded(TreePath(versionCatalogNode.path)), equalTo(false))

        val projectNode = rootNode.getChildAt(2) as DefaultMutableTreeNode
        assertThat(tableModel.getValueAt(projectNode, 0) as String, equalTo("project (project)"))
        assertThat(tableModel.getValueAt(projectNode, 1), equalTo(ParsedValue.NotSet))
        assertThat(projectNode.childCount, not(0))
        assertThat(variablesTable.tree.isExpanded(TreePath(projectNode.path)), equalTo(false))
      }
    }
    finally {
      StudioFlags.GRADLE_VERSION_CATALOG_EXTENDED_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testTreeStructure() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val rootNode = (tableModel.root as ShadowedTreeNode).childNodes.toList()[2]
      assertThat(
        rootNode.testStructure().toString().trimIndent(), equalTo(
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
      )
    }
  }

  @Test
  fun testTreeStructure_addVariable() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val appModuleVariables = psProject.findModuleByName("app")?.variables
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      appModuleVariables?.addNewListVariable("varList")

      val rootNode = (tableModel.root as ShadowedTreeNode).childNodes.toList()[2]
      assertThat(
        rootNode.testStructure().toString().trimIndent(), equalTo(
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
      )
    }
  }

  @Test
  fun testTreeStructure_removeListItem() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val appModuleVariables = psProject.findModuleByName("app")?.variables
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      appModuleVariables?.getVariable("varProGuardFiles")?.listItems?.findElement(0)?.delete()

      val rootNode = (tableModel.root as ShadowedTreeNode).childNodes.toList()[2]
      assertThat(
        rootNode.testStructure().toString().trimIndent(), equalTo(
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
      )
    }
  }

  @Test
  fun testStringVariableNodeDisplay() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
      val variableNode =
        appNode.children().asSequence().find { "anotherVariable" == (it as VariableNode).toString() } as VariableNode
      variablesTable.tree.expandPath(TreePath(appNode.path))

      assertThat(variableNode.variable.value, equalTo("3.0.1".asParsed<Any>()))
      assertThat(variableNode.childCount, equalTo(0))
      assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("anotherVariable"))
      assertThat(tableModel.getValueAt(variableNode, 1), equalTo("3.0.1".asParsed()))

      val row = variablesTable.tree.getRowForPath(TreePath(variableNode.path))
      for (column in 0..1) {
        val component = variablesTable.getCellRenderer(row, column)
          .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row, column), false, false, row, column)
        assertThat(component.background, equalTo(variablesTable.background))
      }
    }
  }

  @Test
  fun testVersionCatalogVariableNodeDisplay() {
    StudioFlags.GRADLE_VERSION_CATALOG_EXTENDED_SUPPORT.override(true)
    try {
      val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
      preparedProject.open { project ->
        val psProject = PsProjectImpl(project)
        val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
        val tableModel = variablesTable.tableModel

        val catalogNode = (tableModel.root as DefaultMutableTreeNode).defaultVersionCatalogChild as DefaultMutableTreeNode
        val variableNode =
          catalogNode.children().asSequence().find { "constraint-layout" == (it as VariableNode).toString() } as VariableNode
        variablesTable.tree.expandPath(TreePath(catalogNode.path))

        assertThat(variableNode.variable.value, equalTo("1.0.2".asParsed<Any>()))
        assertThat(variableNode.childCount, equalTo(0))
        assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("constraint-layout"))
        assertThat(tableModel.getValueAt(variableNode, 1), equalTo<Any>("1.0.2".asParsed()))

        val row = variablesTable.tree.getRowForPath(TreePath(variableNode.path))
        for (column in 0..1) {
          val component = variablesTable.getCellRenderer(row, column)
            .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row, column), false, false, row, column)
          assertThat(component.background, equalTo(variablesTable.background))
        }
      }
    }
    finally {
      StudioFlags.GRADLE_VERSION_CATALOG_EXTENDED_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testBooleanVariableNodeDisplay() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
      val variableNode =
        appNode.children().asSequence().find { "varBool" == (it as VariableNode).toString() } as VariableNode
      variablesTable.tree.expandPath(TreePath(variableNode.path))

      assertThat(variableNode.variable.value, equalTo(true.asParsed<Any>()))
      assertThat(variableNode.childCount, equalTo(0))
      assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("varBool"))
      assertThat(tableModel.getValueAt(variableNode, 1), equalTo(true.asParsed()))
    }
  }

  @Test
  fun testVariableVariableNodeDisplay() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
      val variableNode =
        appNode.children().asSequence().find { "varRefString" == (it as VariableNode).toString() } as VariableNode
      variablesTable.tree.expandPath(TreePath(variableNode.path))

      assertThat(
        variableNode.variable.value,
        equalTo(ParsedValue.Set.Parsed("1.3", DslText.Reference("variable1")))
      )
      assertThat(variableNode.variable.value.maybeValue is String, equalTo(true))
      assertThat(variableNode.childCount, equalTo(0))
      assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("varRefString"))
      assertThat(tableModel.getValueAt(variableNode, 1), equalTo(("variable1" to "1.3").asParsed()))
    }
  }

  @Test
  fun testListNodeDisplay() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
      variablesTable.tree.expandPath(TreePath(appNode.path))

      val listNode =
        appNode.children().asSequence().find { "varProGuardFiles" == (it as VariableNode).toString() } as VariableNode
      assertThat(listNode.variable.isList(), equalTo(true))
      assertThat(listNode.childCount, equalTo(3))
      assertThat(tableModel.getValueAt(listNode, 0) as String, equalTo("varProGuardFiles"))
      assertThat(
        tableModel.getValueAt(listNode, 1),
        equalTo(listOf("proguard-rules.txt".asParsed(), "proguard-rules2.txt".asParsed()).asParsed())
      )

      val firstElementNode = listNode.getChildAt(0)
      assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("0"))
      assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo("proguard-rules.txt".asParsed()))

      val secondElementNode = listNode.getChildAt(1)
      assertThat(tableModel.getValueAt(secondElementNode, 0) as String, equalTo("1"))
      assertThat(tableModel.getValueAt(secondElementNode, 1), equalTo("proguard-rules2.txt".asParsed()))

      val emptyElement = listNode.getChildAt(2)
      assertThat(tableModel.getValueAt(emptyElement, 0) as String, equalTo(""))
      assertThat(tableModel.getValueAt(emptyElement, 1), equalTo(ParsedValue.NotSet))

      val row = variablesTable.tree.getRowForPath(TreePath(listNode.path))
      for (column in 0..1) {
        val component = variablesTable.getCellRenderer(row, column)
          .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row, column), false, false, row, column)
        assertThat(component.background, equalTo(variablesTable.background))
      }
    }
  }

  @Test
  fun testMapNodeDisplay() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
      variablesTable.tree.expandPath(TreePath(appNode.path))

      val mapNode =
        appNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
      assertThat(mapNode.variable.isMap(), equalTo(true))
      assertThat(mapNode.childCount, equalTo(3))
      assertThat(tableModel.getValueAt(mapNode, 0) as String, equalTo("mapVariable"))
      assertThat(
        tableModel.getValueAt(mapNode, 1),
        equalTo(mapOf("a" to "\"double\" quotes".asParsed(), "b" to "'single' quotes".asParsed()).asParsed())
      )

      val firstElementNode = mapNode.getChildAt(0)
      assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("a"))
      assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo("\"double\" quotes".asParsed()))

      val secondElementNode = mapNode.getChildAt(1)
      assertThat(tableModel.getValueAt(secondElementNode, 0) as String, equalTo("b"))
      assertThat(tableModel.getValueAt(secondElementNode, 1), equalTo("'single' quotes".asParsed()))

      val emptyElement = mapNode.getChildAt(2)
      assertThat(tableModel.getValueAt(emptyElement, 0) as String, equalTo(""))
      assertThat(tableModel.getValueAt(emptyElement, 1), equalTo(ParsedValue.NotSet))

      val row = variablesTable.tree.getRowForPath(TreePath(mapNode.path))
      for (column in 0..1) {
        val component = variablesTable.getCellRenderer(row, column)
          .getTableCellRendererComponent(variablesTable, variablesTable.getValueAt(row, column), false, false, row, column)
        assertThat(component.background, equalTo(variablesTable.background))
      }
    }
  }

  @Test
  fun testModuleNodeRename() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild
      assertThat(tableModel.isCellEditable(appNode, 0), equalTo(false))
    }
  }

  @Test
  fun testVariableNodeRename() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val variableNode = appNode.children().asSequence().find { "anotherVariable" == (it as VariableNode).toString() } as VariableNode
      variablesTable.tree.expandPath(TreePath(variableNode.path))
      assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("anotherVariable"))
      assertThat(tableModel.isCellEditable(variableNode, 0), equalTo(true))

      tableModel.setValueAt("renamed", variableNode, 0)
      assertThat(tableModel.getValueAt(variableNode, 0) as String, equalTo("renamed"))

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val variableNames = newAppNode.children().asSequence().map { it.toString() }.toList()
      assertThat(variableNames, hasItem("renamed"))
      assertThat(variableNames, not(hasItem("anotherVariable")))
    }
  }

  @Test
  fun testListNodeRename() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as DefaultMutableTreeNode
      variablesTable.tree.expandPath(TreePath(appNode.path))

      val listNode = appNode.children().asSequence().find { "varProGuardFiles" == (it as VariableNode).toString() } as VariableNode
      assertThat(listNode.variable.isList(), equalTo(true))

      variablesTable.tree.expandPath(TreePath(listNode.path))
      val firstElementNode = listNode.getChildAt(0)
      assertThat(tableModel.isCellEditable(firstElementNode, 0), equalTo(false))
    }
  }

  @Test
  fun testMapNodeRename() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      variablesTable.tree.expandPath(TreePath(appNode.path))

      val mapNode = appNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
      assertThat(mapNode.variable.isMap(), equalTo(true))

      variablesTable.tree.expandPath(TreePath(mapNode.path))
      val firstElementNode = mapNode.getChildAt(0)
      assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("a"))
      assertThat(tableModel.isCellEditable(firstElementNode, 0), equalTo(true))

      tableModel.setValueAt("renamed", firstElementNode, 0)
      assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("renamed"))

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newMapNode = newAppNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
      val keyNames = newMapNode.children().asSequence().map { it.toString() }.toList()
      assertThat(keyNames, hasItem("renamed"))
      assertThat(keyNames, not(hasItem("a")))
    }
  }

  @Test
  fun testModuleNodeSetValue() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild
      assertThat(tableModel.isCellEditable(appNode, 1), equalTo(false))
    }
  }

  @Test
  fun testVariableNodeSetValue() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val variableNode = appNode.children().asSequence().find { "anotherVariable" == (it as VariableNode).toString() } as VariableNode
      variablesTable.tree.expandPath(TreePath(variableNode.path))
      assertThat(tableModel.getValueAt(variableNode, 1), equalTo("3.0.1".asParsed()))
      assertThat(tableModel.isCellEditable(variableNode, 1), equalTo(true))

      tableModel.setValueAt("3.0.1".asParsed().annotated(), variableNode, 1)
      assertThat(variableNode.variable.parent.isModified, equalTo(false))

      tableModel.setValueAt("new value".asParsed().annotated(), variableNode, 1)
      assertThat(tableModel.getValueAt(variableNode, 1), equalTo("new value".asParsed()))

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newVariableNode = newAppNode.children().asSequence().find { "anotherVariable" == (it as VariableNode).toString() } as VariableNode
      assertThat(newVariableNode.getUnresolvedValue(false), equalTo("new value".asParsed<Any>()))
    }
  }

  @Test
  fun testListNodeSetValue() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      variablesTable.tree.expandPath(TreePath(appNode.path))

      val listNode = appNode.children().asSequence().find { "varProGuardFiles" == (it as VariableNode).toString() } as VariableNode
      assertThat(listNode.variable.isList(), equalTo(true))
      assertThat(tableModel.isCellEditable(listNode, 1), equalTo(false))

      variablesTable.tree.expandPath(TreePath(listNode.path))
      val firstElementNode = listNode.getChildAt(0) as ListItemNode
      assertThat(tableModel.isCellEditable(listNode, 1), equalTo(false))
      assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo("proguard-rules.txt".asParsed()))
      assertThat(tableModel.isCellEditable(firstElementNode, 1), equalTo(true))

      tableModel.setValueAt("proguard-rules.txt".asParsed().annotated(), firstElementNode, 1)
      assertThat(firstElementNode.variable.parent.isModified, equalTo(false))

      tableModel.setValueAt("new value".asParsed().annotated(), firstElementNode, 1)
      assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo("new value".asParsed()))

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newListNode = newAppNode.children().asSequence().find { "varProGuardFiles" == (it as VariableNode).toString() } as VariableNode
      assertThat((newListNode.getChildAt(0) as ListItemNode).getUnresolvedValue(false), equalTo("new value".asParsed<Any>()))
    }
  }

  @Test
  fun testMapNodeSetValue() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      variablesTable.tree.expandPath(TreePath(appNode.path))

      val mapNode = appNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
      assertThat(mapNode.variable.isMap(), equalTo(true))
      assertThat(tableModel.isCellEditable(mapNode, 1), equalTo(false))

      variablesTable.tree.expandPath(TreePath(mapNode.path))
      val firstElementNode = mapNode.getChildAt(0) as MapItemNode
      assertThat(tableModel.isCellEditable(mapNode, 1), equalTo(false))
      assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo("\"double\" quotes".asParsed()))
      assertThat(tableModel.isCellEditable(firstElementNode, 1), equalTo(true))

      tableModel.setValueAt("\"\"double\" quotes\"".asParsed().annotated(), firstElementNode, 1)
      assertThat(firstElementNode.variable.parent.isModified, equalTo(false))

      tableModel.setValueAt("new value".asParsed().annotated(), firstElementNode, 1)
      assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo("new value".asParsed()))

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newMapNode = newAppNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
      assertThat((newMapNode.getChildAt(0) as MapItemNode).getUnresolvedValue(false), equalTo("new value".asParsed<Any>()))
    }
  }

  @Test
  fun testAddSimpleVariable() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      assertThat(appNode.children().asSequence().map { it.toString() }.toSet(), not(hasItem("newVariable")))

      variablesTable.selectNode(appNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.STRING)
      variablesTable.simulateTextInput("newVariable")

      val variableNode = appNode.children().asSequence().find { "newVariable" == (it as VariableNode).toString() } as VariableNode
      variableNode.setValue("new value".asParsed())

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newVariableNode = newAppNode.children().asSequence().find { "newVariable" == (it as VariableNode).toString() } as VariableNode
      assertThat(newVariableNode.getUnresolvedValue(false), equalTo("new value".asParsed<Any>()))
    }
  }

  @Test
  fun testAddAndEditSimpleVariable() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)

      val buildScriptNode =
        (variablesTable.tableModel.root as DefaultMutableTreeNode).children().asSequence().find { it.toString() == ":app" } as ModuleNode
      assertThat(buildScriptNode.children().asSequence().map { it.toString() }.toSet(), not(hasItem("newVariable")))

      //create variable
      variablesTable.selectNode(buildScriptNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.STRING)
      variablesTable.simulateTextInput("newVariable")

      setVariableValue(buildScriptNode, "newVariable", "new value 1")
      psProject.applyAllChanges()

      assertVariableValue(psProject, "newVariable", "new value 1") { it.toString() == ":app" }

      // Second change
      assertThat(buildScriptNode.children().asSequence().map { it.toString() }.toSet(), hasItem("newVariable"))

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
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)

      val buildScriptNode = (variablesTable.tableModel.root as DefaultMutableTreeNode).children()
        .asSequence()
        .find { it.toString().contains("(build script)") } as ModuleNode
      assertThat(buildScriptNode.children().asSequence().map { it.toString() }.toSet(), not(hasItem("newVariable")))

      //create variable
      variablesTable.selectNode(buildScriptNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.STRING)
      variablesTable.simulateTextInput("newVariable")

      setVariableValue(buildScriptNode, "newVariable", "new value 1")
      psProject.applyAllChanges()

      assertVariableValue(psProject, "newVariable", "new value 1") { it.toString().contains("(build script)") }

      // Second change
      assertThat(buildScriptNode.children().asSequence().map { it.toString() }.toSet(), hasItem("newVariable"))

      setVariableValue(buildScriptNode, "newVariable", "new value 2")
      psProject.applyAllChanges()
      assertVariableValue(psProject, "newVariable", "new value 2") { it.toString().contains("(build script)") }

      // Emulate opening PSD again and check value was applied
      val psProject2 = PsProjectImpl(project)
      assertVariableValue(psProject2, "newVariable", "new value 2") { it.toString().contains("(build script)") }
    }
  }

  private fun setVariableValue(node: VariablesBaseNode, name: String, value: String) {
    val variableNode = node.children().asSequence().find { name == (it as VariableNode).toString() } as VariableNode
    variableNode.setValue(value.asParsed())
  }

  private fun assertVariableValue(psProject: PsProject, name: String, value: String, moduleSelector: (Any) -> Boolean) {
    val newTableModel1 = VariablesTable(psProject.ideProject, contextFor(psProject), psProject, projectRule.testRootDisposable).tableModel
    val newModuleNode1 = (newTableModel1.root as DefaultMutableTreeNode).children().asSequence().find(
      moduleSelector) as AbstractContainerNode
    val newVariableNode1 = newModuleNode1.children().asSequence().find { name == (it as VariableNode).toString() } as VariableNode
    assertThat(newVariableNode1.getUnresolvedValue(false), equalTo(value.asParsed<Any>()))
  }

  @Test
  fun testAddAndEditVersionCatalogVariable() {
    StudioFlags.GRADLE_VERSION_CATALOG_EXTENDED_SUPPORT.override(true)
    try {
      val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
      preparedProject.open { project ->
        val psProject = PsProjectImpl(project)
        val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)

        val versionCatalogNode: VersionCatalogNode = (variablesTable.tableModel.root as DefaultMutableTreeNode).children().asSequence().find {
          it.toString().contains("libs")
        } as VersionCatalogNode
        assertThat(versionCatalogNode.children().asSequence().map { it.toString() }.toSet(),
                   equalTo(setOf("constraint-layout", "guava", "junit", "")))
        assertThat(versionCatalogNode.children().asSequence().map { it.toString() }.toSet(), not(hasItem("newVersion")))

        //create variable
        variablesTable.selectNode(versionCatalogNode)
        variablesTable.createAddVariableStrategy().addVariable(ValueType.STRING)
        variablesTable.simulateTextInput("newVersion")

        setVariableValue(versionCatalogNode, "newVersion", "1.2.3")
        psProject.applyAllChanges()

        assertVariableValue(psProject, "newVersion", "1.2.3") { it.toString().contains("libs") }

        // Second change
        assertThat(versionCatalogNode.children().asSequence().map { it.toString() }.toSet(), hasItem("newVersion"))

        setVariableValue(versionCatalogNode, "newVersion", "2.3.4")
        psProject.applyAllChanges()
        assertVariableValue(psProject, "newVersion", "2.3.4") { it.toString().contains("libs") }

        // Emulate opening PSD again and check value was applied
        val psProject2 = PsProjectImpl(project)
        assertVariableValue(psProject2, "newVersion", "2.3.4") { it.toString().contains("libs") }
      }
    }
    finally {
      StudioFlags.GRADLE_VERSION_CATALOG_EXTENDED_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testValidationVariableName() {
    StudioFlags.GRADLE_VERSION_CATALOG_EXTENDED_SUPPORT.override(true)
    try {
      val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
      preparedProject.open { project ->
        val psProject = PsProjectImpl(project)
        val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)

        val versionCatalogNode: VersionCatalogNode = (variablesTable.tableModel.root as DefaultMutableTreeNode).children().asSequence().find {
          it.toString().contains("libs")
        } as VersionCatalogNode
        assertThat(versionCatalogNode.children().asSequence().map { it.toString() }.toSet(),
                   equalTo(setOf("constraint-layout", "guava", "junit", "")))
        assertThat(versionCatalogNode.children().asSequence().map { it.toString() }.toSet(), not(hasItem("newVersion")))

        //create variable
        variablesTable.selectNode(versionCatalogNode)
        variablesTable.createAddVariableStrategy().addVariable(ValueType.STRING)
        emulateInputAndAssertWarning(variablesTable, " ", "Variable name cannot have whitespaces.")
        variablesTable.editCellAt(variablesTable.getRowByNode(versionCatalogNode) + 4, 0)
        emulateInputAndAssertWarning(variablesTable, "", "Variable name cannot be empty.")
        variablesTable.editCellAt(variablesTable.getRowByNode(versionCatalogNode) + 4, 0)
        emulateInputAndAssertWarning(variablesTable, "guava", "Duplicate variable name: 'guava'")
      }
    }
    finally {
      StudioFlags.GRADLE_VERSION_CATALOG_EXTENDED_SUPPORT.clearOverride()
    }
  }

  private fun emulateInputAndAssertWarning(variablesTable: VariablesTable, input:String, expectedMessage:String){
    variablesTable.simulateTextInput(input) { textBox ->
      assertThat(textBox.getWarningMessage(), equalTo(expectedMessage))
    }
  }

  // regression b/258243668
  @Test
  fun testAddVersionCatalogVariableAfterMultipleSelections() {
    StudioFlags.GRADLE_VERSION_CATALOG_EXTENDED_SUPPORT.override(true)
    try {
      val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
      preparedProject.open { project ->
        val psProject = PsProjectImpl(project)
        val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)

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
        assertThat(variablesTable.isEditing, equalTo(false))

        variablesTable.editNode(addNewCatalogVariable)
        // checking if we start editing for last node
        assertThat(variablesTable.isEditing, equalTo(true))
      }
    }
    finally {
      StudioFlags.GRADLE_VERSION_CATALOG_EXTENDED_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testAddList() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      assertThat(appNode.children().asSequence().map { it.toString() }.toSet(), not(hasItem("newList")))

      variablesTable.selectNode(appNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.LIST)
      variablesTable.simulateTextInput("newList")

      val variableNode = appNode.children().asSequence().find { "newList" == (it as VariableNode).toString() } as VariableNode
      assertThat(variableNode.childCount, equalTo(1))
      assertThat(variablesTable.tree.isExpanded(TreePath(variableNode.path)), equalTo(true))

      tableModel.setValueAt("list item".asParsed().annotated(), variableNode.getChildAt(0), 1)
      assertThat(variableNode.childCount, equalTo(2))

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newListNode = newAppNode.children().asSequence().find { "newList" == (it as VariableNode).toString() } as VariableNode

      val firstElementNode = newListNode.getChildAt(0)
      assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("0"))
      assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo("list item".asParsed()))

      val secondElementNode = newListNode.getChildAt(1)
      assertThat(tableModel.getValueAt(secondElementNode, 0) as String, equalTo(""))
      assertThat(tableModel.getValueAt(secondElementNode, 1), equalTo(ParsedValue.NotSet))
    }
  }

  @Test
  fun testAddMap() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      assertThat(appNode.children().asSequence().map { it.toString() }.toSet(), not(hasItem("newMap")))

      variablesTable.selectNode(appNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.MAP)
      variablesTable.simulateTextInput("newMap")

      val variableNode = appNode.children().asSequence().find { "newMap" == (it as VariableNode).toString() } as VariableNode
      assertThat(variableNode.childCount, equalTo(1))
      assertThat(variablesTable.tree.isExpanded(TreePath(variableNode.path)), equalTo(true))

      tableModel.setValueAt("key", variableNode.getChildAt(0), 0)
      tableModel.setValueAt("value".asParsed().annotated(), variableNode.getChildAt(0), 1)
      assertThat(variableNode.childCount, equalTo(2))

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newMapNode = newAppNode.children().asSequence().find { "newMap" == (it as VariableNode).toString() } as VariableNode

      val firstElementNode = newMapNode.getChildAt(0)
      assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("key"))
      assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo("value".asParsed()))

      val secondElementNode = newMapNode.getChildAt(1)
      assertThat(tableModel.getValueAt(secondElementNode, 0) as String, equalTo(""))
      assertThat(tableModel.getValueAt(secondElementNode, 1), equalTo(ParsedValue.NotSet))
    }
  }

  @Test
  fun testAddEmptyVariable() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      assertThat(appNode.childCount, equalTo(14))

      variablesTable.selectNode(appNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.STRING)
      assertThat(appNode.childCount, equalTo(14))
      variablesTable.editingStopped(null)
      assertThat(appNode.childCount, equalTo(14))

      variablesTable.selectNode(appNode)
      variablesTable.createAddVariableStrategy().addVariable(ValueType.STRING)
      assertThat(appNode.childCount, equalTo(14))
      variablesTable.editingCanceled(null)
      assertThat(appNode.childCount, equalTo(14))
    }
  }

  @Test
  fun testVariableNodeDelete() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      variablesTable.tree.expandPath(TreePath(appNode.path))
      val childCount = appNode.childCount
      val variableNode = appNode.children().asSequence().find { "anotherVariable" == (it as VariableNode).toString() } as VariableNode
      variablesTable.selectNode(variableNode)
      variablesTable.deleteSelectedVariables()

      val variableNames = appNode.children().asSequence().map { it.toString() }.toList()
      assertThat(variableNames, not(hasItem("anotherVariable")))
      assertThat(appNode.childCount, equalTo(childCount - 1))

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newVariableNames = newAppNode.children().asSequence().map { it.toString() }.toList()
      assertThat(newVariableNames, not(hasItem("anotherVariable")))
      assertThat(newAppNode.childCount, equalTo(childCount - 1))
    }
  }

  @Test
  fun testCatalogVersionDelete() {
    StudioFlags.GRADLE_VERSION_CATALOG_EXTENDED_SUPPORT.override(true)
    try {
      val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
      preparedProject.open { project ->
        val psProject = PsProjectImpl(project)
        val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
        val tableModel = variablesTable.tableModel

        val appNode = (tableModel.root as DefaultMutableTreeNode).defaultVersionCatalogChild as VersionCatalogNode
        variablesTable.tree.expandPath(TreePath(appNode.path))
        val childCount = appNode.childCount
        val variableNode = appNode.children().asSequence().find { "constraint-layout" == (it as VariableNode).toString() } as VariableNode
        variablesTable.selectNode(variableNode)
        variablesTable.deleteSelectedVariables()

        val variableNames = appNode.children().asSequence().map { it.toString() }.toList()
        assertThat(variableNames, not(hasItem("constraint-layout")))
        assertThat(appNode.childCount, equalTo(childCount - 1))

        psProject.applyAllChanges()
        val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable).tableModel
        val newAppNode = (newTableModel.root as DefaultMutableTreeNode).defaultVersionCatalogChild as VersionCatalogNode
        val newVariableNames = newAppNode.children().asSequence().map { it.toString() }.toList()
        assertThat(newVariableNames, not(hasItem("constraint-layout")))
        assertThat(newAppNode.childCount, equalTo(childCount - 1))
      }
    }
    finally {
      StudioFlags.GRADLE_VERSION_CATALOG_EXTENDED_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testListNodeDelete() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      variablesTable.tree.expandPath(TreePath(appNode.path))

      val listNode = appNode.children().asSequence().find { "varProGuardFiles" == (it as VariableNode).toString() } as VariableNode
      assertThat(listNode.variable.isList(), equalTo(true))
      val childCount = listNode.childCount

      variablesTable.tree.expandPath(TreePath(listNode.path))
      val firstElementNode = listNode.getChildAt(0) as ListItemNode
      assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("0"))
      assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo("proguard-rules.txt".asParsed()))

      variablesTable.selectNode(firstElementNode)
      variablesTable.deleteSelectedVariables()

      val listNodeFirstChild = listNode.getChildAt(0) as ListItemNode
      assertThat(tableModel.getValueAt(listNodeFirstChild, 0) as String, equalTo("0"))
      assertThat(tableModel.getValueAt(listNodeFirstChild, 1), equalTo("proguard-rules2.txt".asParsed()))
      assertThat(listNode.childCount, equalTo(childCount - 1))

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newListNode = newAppNode.children().asSequence().find { "varProGuardFiles" == (it as VariableNode).toString() } as VariableNode
      variablesTable.tree.expandPath(TreePath(listNode.path))
      val newFirstElementNode = newListNode.getChildAt(0) as ListItemNode
      assertThat(tableModel.getValueAt(newFirstElementNode, 0) as String, equalTo("0"))
      assertThat(tableModel.getValueAt(newFirstElementNode, 1), equalTo("proguard-rules2.txt".asParsed()))
      assertThat(newListNode.childCount, equalTo(childCount - 1))
    }
  }

  // regression b/258712110
  @Test
  fun testVersionCatalogVariableEditorIsInputText() {
    StudioFlags.GRADLE_VERSION_CATALOG_EXTENDED_SUPPORT.override(true)
    try {
      val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
      preparedProject.open { project ->
        val psProject = PsProjectImpl(project)
        val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)

        val versionCatalogNode: VersionCatalogNode = (variablesTable.tableModel.root as DefaultMutableTreeNode).children().asSequence().find {
          it.toString().contains("libs")
        } as VersionCatalogNode
        //create variable
        variablesTable.selectNode(versionCatalogNode)
        variablesTable.createAddVariableStrategy().addVariable(ValueType.STRING)

        val rowIndex = variablesTable.getRowByNode(versionCatalogNode.lastChild as VariablesBaseNode)
        variablesTable.simulateTextInput("newVersion")
        variablesTable.editCellAt(rowIndex, 1)

        assertThat(variablesTable.editorComponent, instanceOf(JBTextField::class.java))
      }
    }
    finally {
      StudioFlags.GRADLE_VERSION_CATALOG_EXTENDED_SUPPORT.clearOverride()
    }
  }

  @Test
  fun testMapNodeDelete() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val variablesTable = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable)
      val tableModel = variablesTable.tableModel

      val appNode = (tableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      variablesTable.tree.expandPath(TreePath(appNode.path))

      val mapNode = appNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
      assertThat(mapNode.variable.isMap(), equalTo(true))
      val childCount = mapNode.childCount

      variablesTable.tree.expandPath(TreePath(mapNode.path))
      val firstElementNode = mapNode.getChildAt(0) as MapItemNode
      assertThat(tableModel.getValueAt(firstElementNode, 0) as String, equalTo("a"))
      assertThat(tableModel.getValueAt(firstElementNode, 1), equalTo("\"double\" quotes".asParsed()))

      variablesTable.selectNode(firstElementNode)
      variablesTable.deleteSelectedVariables()

      val mapNodeFirstChild = mapNode.getChildAt(0) as MapItemNode
      assertThat(tableModel.getValueAt(mapNodeFirstChild, 0) as String, equalTo("b"))
      assertThat(tableModel.getValueAt(mapNodeFirstChild, 1), equalTo("'single' quotes".asParsed()))
      assertThat(mapNode.childCount, equalTo(childCount - 1))

      psProject.applyAllChanges()
      val newTableModel = VariablesTable(project, contextFor(psProject), psProject, projectRule.testRootDisposable).tableModel
      val newAppNode = (newTableModel.root as DefaultMutableTreeNode).appModuleChild as ModuleNode
      val newMapNode = newAppNode.children().asSequence().find { "mapVariable" == (it as VariableNode).toString() } as VariableNode
      val newFirstElementNode = mapNode.getChildAt(0) as MapItemNode
      assertThat(tableModel.getValueAt(newFirstElementNode, 0) as String, equalTo("b"))
      assertThat(tableModel.getValueAt(newFirstElementNode, 1), equalTo("'single' quotes".asParsed()))
      assertThat(newMapNode.childCount, equalTo(childCount - 1))
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

private fun VariablesTable.selectNode(node: VariablesBaseNode) {
  val selectedRow = tree.getRowForPath(TreePath(node.path))
  selectionModel.setSelectionInterval(selectedRow, selectedRow)
}

private fun VariablesTable.editNode(node: VariablesBaseNode) {
  this.editCellAt(getRowByNode(node),0)
}

private fun VariablesTable.getRowByNode(node: VariablesBaseNode): Int =
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
