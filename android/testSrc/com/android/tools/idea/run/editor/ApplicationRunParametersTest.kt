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
package com.android.tools.idea.run.editor

import com.android.tools.idea.run.AndroidAppRunConfigurationBase
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.BASIC
import com.intellij.execution.ui.ConfigurationModuleSelector
import com.intellij.openapi.module.Module
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.awt.event.ActionEvent

class ApplicationRunParametersTest : AndroidGradleTestCase() {
  private lateinit var myApplicationRunParameters: ApplicationRunParameters<AndroidAppRunConfigurationBase>
  private lateinit var myModule : Module
  private lateinit var myModuleSelector : ConfigurationModuleSelector

  override fun setUp() {
    super.setUp()
    loadProject(BASIC)
    myModule = mock(Module::class.java)
    myModuleSelector = mock(ConfigurationModuleSelector::class.java)
    myApplicationRunParameters = ApplicationRunParameters(project, myModuleSelector)
  }

  @Test
  fun testModuleChangedWithoutAndroidModuleModel() {
    `when`(myModuleSelector.module).thenReturn(myModule)
    // Disposed module causes AndroidModuleModel to return null.
    `when`(myModule.isDisposed).thenReturn(true)
    myApplicationRunParameters.onModuleChanged()
  }

  @Test
  fun testToggleInstantAppWithNullModule() {
    `when`(myModuleSelector.module).thenReturn(null)
    val myInstantAppDeployCheckbox = ApplicationRunParameters::class.java.getDeclaredField("myInstantAppDeployCheckBox")
    myInstantAppDeployCheckbox.isAccessible = true
    val event = mock(ActionEvent::class.java)
    `when`(event.source).thenReturn(myInstantAppDeployCheckbox.get(myApplicationRunParameters))
    myApplicationRunParameters.actionPerformed(event)
  }
}
