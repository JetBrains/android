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
package com.android.tools.idea.gradle.structure.configurables.variables

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.repositories.search.ArtifactRepositorySearchService
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.daemon.PsAnalyzerDaemon
import com.android.tools.idea.gradle.structure.daemon.PsLibraryUpdateCheckerDaemon
import com.android.tools.idea.gradle.structure.daemon.PsSdkIndexCheckerDaemon
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.testResolve
import com.android.tools.idea.structure.dialog.ProjectStructureConfigurable
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.openapi.Disposable
import com.intellij.testFramework.RunsInEdt
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@RunsInEdt
class VariablesConfigurableTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testIsModified() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project).also { it.testResolve() }
      val context = object : PsContext {
        override val analyzerDaemon: PsAnalyzerDaemon get() = throw UnsupportedOperationException()
        override val project: PsProject = psProject
        override val libraryUpdateCheckerDaemon: PsLibraryUpdateCheckerDaemon get() = throw UnsupportedOperationException()
        override val sdkIndexCheckerDaemon: PsSdkIndexCheckerDaemon get() = throw UnsupportedOperationException()
        override val uiSettings: PsUISettings get() = throw UnsupportedOperationException()
        override val selectedModule: String? get() = throw UnsupportedOperationException()
        override val mainConfigurable: ProjectStructureConfigurable get() = throw UnsupportedOperationException()
        override fun getArtifactRepositorySearchServiceFor(module: PsModule): ArtifactRepositorySearchService = throw UnsupportedOperationException()
        override fun setSelectedModule(gradlePath: String, source: Any) = throw UnsupportedOperationException()
        override fun add(listener: PsContext.SyncListener, parentDisposable: Disposable) = throw UnsupportedOperationException()
        override fun applyRunAndReparse(runnable: () -> Boolean) = throw UnsupportedOperationException()
        override fun applyChanges() = psProject.applyChanges()
        override fun logFieldEdited(fieldId: PSDEvent.PSDField) = throw UnsupportedOperationException()
        override fun getEditedFieldsAndClear(): List<PSDEvent.PSDField> = throw UnsupportedOperationException()
        override fun dispose() = throw UnsupportedOperationException()
      }
      val configurable = VariablesConfigurable(project, context)
      val variablesTable = mock(VariablesTable::class.java)
      configurable.table = variablesTable
      configurable.reset()

      assertThat(configurable.isModified).isEqualTo(false)

      `when`(variablesTable.isEditing).thenReturn(true)
      assertThat(configurable.isModified).isEqualTo(true)

      `when`(variablesTable.isEditing).thenReturn(false)
      assertThat(configurable.isModified).isEqualTo(false)
    }
  }
}