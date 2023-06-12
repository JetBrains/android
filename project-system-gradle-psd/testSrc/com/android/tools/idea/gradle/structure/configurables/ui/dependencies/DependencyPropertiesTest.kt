/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.ui.dependencies

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.repositories.search.ArtifactRepositorySearchService
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.dependencies.details.DeclaredLibraryDependencyUiProperties
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.properties.SimplePropertyEditor
import com.android.tools.idea.gradle.structure.daemon.PsAnalyzerDaemon
import com.android.tools.idea.gradle.structure.daemon.PsLibraryUpdateCheckerDaemon
import com.android.tools.idea.gradle.structure.daemon.PsSdkIndexCheckerDaemon
import com.android.tools.idea.gradle.structure.model.PsDeclaredLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.structure.dialog.ProjectStructureConfigurable
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.openapi.Disposable
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.components.JBTextField
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class DependencyPropertiesTest {
  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

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

  @Test
  fun testDependencyEmptyVersionDisplay() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_BOM)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val app = psProject.findModuleByName("app")
      val dep = app!!.dependencies.findLibraryDependencies("com.android.support:appcompat-v7").firstOrNull()
      Assert.assertTrue(dep != null)
      val libraryDependency = dep as PsDeclaredLibraryDependency
      val propertyUIModel = DeclaredLibraryDependencyUiProperties.makeVersionUiProperty(libraryDependency)
      val editor = propertyUIModel.createEditor(contextFor(psProject), psProject, app, Unit, null) as SimplePropertyEditor<*, *>
      val comboBoxEditor = editor.testRenderedComboBox.editor
      Assert.assertTrue(comboBoxEditor != null)
      val textEdit = comboBoxEditor!!.editorComponent as? JBTextField
      Assert.assertTrue(textEdit?.emptyText?.text == "/*not specified*/")
    }
  }
}