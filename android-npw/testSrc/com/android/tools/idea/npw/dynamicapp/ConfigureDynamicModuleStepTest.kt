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
package com.android.tools.idea.npw.dynamicapp

import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.npw.NewProjectWizardTestUtils.getAgpVersion
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RunsInEdt
import javax.swing.JLabel
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class ConfigureDynamicModuleStepTest {
  @get:Rule
  val projectRule =
    AndroidGradleProjectRule(agpVersionSoftwareEnvironment = getAgpVersion()).onEdt()

  private val emptyProjectSyncInvoker =
    object : ProjectSyncInvoker {
      override fun syncProject(project: Project) {}
    }

  @Test
  fun deprecationWarningShownForInstantDynamicFeatureTemplate() {
    val model =
      DynamicFeatureModel(
        project = projectRule.project,
        moduleParent = ":",
        projectSyncInvoker = emptyProjectSyncInvoker,
        isInstant = true,
        templateName = "Instant Dynamic Feature",
        templateDescription = "Instant Dynamic Feature description",
      )

    val step = ConfigureDynamicModuleStep(model, "com.example.base")
    Disposer.register(projectRule.fixture.testRootDisposable, step)
    val fakeUi =
      FakeUi(step.createMainPanel(), parentDisposable = projectRule.fixture.testRootDisposable)

    val deprecationWarningLabel =
      checkNotNull(
        fakeUi.findComponent<JLabel> {
          it.text != null && it.text.contains("Instant Apps support will be removed by Google Play")
        }
      )
    assertTrue { fakeUi.isShowing(deprecationWarningLabel) }
  }

  @Test
  fun deprecationWarningNotShownForNonInstantDynamicFeatureTemplate() {
    val model =
      DynamicFeatureModel(
        project = projectRule.project,
        moduleParent = ":",
        projectSyncInvoker = emptyProjectSyncInvoker,
        isInstant = false,
        templateName = "Dynamic Feature",
        templateDescription = "Dynamic Feature description",
      )

    val step = ConfigureDynamicModuleStep(model, "com.example.base")
    Disposer.register(projectRule.fixture.testRootDisposable, step)
    val fakeUi =
      FakeUi(step.createMainPanel(), parentDisposable = projectRule.fixture.testRootDisposable)

    val deprecationWarningLabel =
      fakeUi.findComponent<JLabel> {
        it.text != null && it.text.contains("Instant Apps support will be removed by Google Play")
      }
    assertNull(deprecationWarningLabel)
  }
}
