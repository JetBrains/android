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
package com.android.tools.idea.customview.preview

import com.android.tools.adtui.workbench.DetachedToolWindowManager
import com.android.tools.adtui.workbench.WorkBenchManager
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.registerServiceInstance
import junit.framework.TestCase
import org.mockito.AdditionalAnswers.returnsSecondArg
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class CustomViewPreviewRepresentationTest : LightJavaCodeInsightFixtureTestCase() {

  @Mock
  private lateinit var persistenceManager : PropertiesComponent

  @Mock
  private lateinit var detachedToolWindowManager: DetachedToolWindowManager

  @Mock
  private lateinit var workbenchManager: WorkBenchManager

  @Mock
  private lateinit var projectSystemService: ProjectSystemService

  @Mock
  private lateinit var androidProjectSystem: AndroidProjectSystem

  @Mock
  private lateinit var syncManager: ProjectSystemSyncManager

  private lateinit var representation: CustomViewPreviewRepresentation

  override fun setUp() {
    super.setUp()

    MockitoAnnotations.initMocks(this)

    Mockito.`when`(persistenceManager.getValue(anyString(), anyString())).then(returnsSecondArg<String>())
    // For workbench
    project.registerServiceInstance(DetachedToolWindowManager::class.java, detachedToolWindowManager)
    ApplicationManager.getApplication().registerServiceInstance(WorkBenchManager::class.java, workbenchManager)
    // For setupBuildListener
    project.registerServiceInstance(ProjectSystemService::class.java, projectSystemService)
    Mockito.`when`(projectSystemService.projectSystem).thenReturn(androidProjectSystem)
    Mockito.`when`(androidProjectSystem.getSyncManager()).thenReturn(syncManager)
    Mockito.`when`(syncManager.getLastSyncResult()).thenReturn(ProjectSystemSyncManager.SyncResult.FAILURE)
  }

  override fun tearDown() {
    // CustomViewPreviewRepresentation keeps a reference to a project, so it should get disposed before.
    Disposer.dispose(representation)
    super.tearDown()
  }

  fun test() {
    val file = myFixture.addFileToProject("src/com/example/CustomView.java", """
      package com.example;

      import android.view.View;

      public class CustomView extends View {
        public CustomButton() {
          super();
        }
      }
    """.trimIndent())

    representation = CustomViewPreviewRepresentation(
      file,
      persistenceProvider = { persistenceManager },
      buildStateProvider = { CustomViewVisualStateTracker.BuildState.SUCCESSFUL })

    TestCase.assertNotNull(representation)
  }
}