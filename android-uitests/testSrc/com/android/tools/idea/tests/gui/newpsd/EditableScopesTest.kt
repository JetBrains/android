/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.newpsd

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.GradleExperimentalSettings
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.openPsd
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectDependenciesConfigurable
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_TAB

@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRemoteRunner::class)
class EditableScopesTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  @Before
  fun setUp() {
    StudioFlags.NEW_PSD_ENABLED.override(true)
    GradleExperimentalSettings.getInstance().USE_NEW_PSD = true
  }

  @After
  fun tearDown() {
    StudioFlags.NEW_PSD_ENABLED.clearOverride()
    GradleExperimentalSettings.getInstance().USE_NEW_PSD = GradleExperimentalSettings().USE_NEW_PSD
  }

  @Test
  fun testEditableJarDependencyScope() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("psdObsoleteScopes")
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().selectModule("mylibrary")
        findDependenciesPanel().run {
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("libs", "compile"))
          findDependenciesTable().cell("libs").click()
          findScopeEditor().run {
            assertThat(text()).isEqualTo("compile")
            selectAll()
            enterText("implementation")
            pressAndReleaseKeys(VK_TAB)
          }
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("libs", "implementation"))

          findDependenciesTable().cell("libs").click()
          findScopeEditor().run {
            assertThat(text()).isEqualTo("implementation")
            selectAll()
            enterText("api")
            pressAndReleaseKeys(VK_ENTER)
          }
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("libs", "api"))
        }
      }
      clickOk()
    }
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().selectModule("mylibrary")
        findDependenciesPanel().run {
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("libs", "api"))
          findDependenciesTable().cell("libs").click()
          findScopeEditor().run { assertThat(text()).isEqualTo("api") }
        }
      }
      clickCancel()
    }
  }

  @Test
  fun testEditableModuleDependencyScope() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("psdObsoleteScopes")
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().selectModule("app")
        findDependenciesPanel().run {
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("mylibrary", "compile"))
          findDependenciesTable().cell("mylibrary").click()
          findScopeEditor().run {
            assertThat(text()).isEqualTo("compile")
            deleteText()
            enterText("implementation")
            pressAndReleaseKeys(VK_TAB)
          }
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("mylibrary", "implementation"))

          findDependenciesTable().cell("mylibrary").click()
          findScopeEditor().run {
            assertThat(text()).isEqualTo("implementation")
            deleteText()
            enterText("testImplementation")
            pressAndReleaseKeys(VK_ENTER)
          }
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("mylibrary", "testImplementation"))
        }
      }
      clickOk()
    }
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().selectModule("app")
        findDependenciesPanel().run {
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("mylibrary", "testImplementation"))
          findDependenciesTable().cell("mylibrary").click()
          findScopeEditor().run { assertThat(text()).isEqualTo("testImplementation") }
        }
      }
      clickCancel()
    }
  }

  @Test
  fun testEditableLibraryDependencyScope() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("psdObsoleteScopes")
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().selectModule("mylibrary")
        findDependenciesPanel().run {
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("junit:junit:4.11", "testCompile"))

          findDependenciesTable().cell("junit:junit:4.11").click()

          findScopeEditor(). run {
            assertThat(text()).isEqualTo("testCompile")
            deleteText()
            enterText("testImplementation")
            pressAndReleaseKeys(VK_TAB)
          }
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("junit:junit:4.11", "testImplementation"))

          findDependenciesTable().cell("junit:junit:4.11").click()
          findScopeEditor().run {
            assertThat(text()).isEqualTo("testImplementation")
            deleteText()
            enterText("implementation")
            pressAndReleaseKeys(VK_ENTER)
          }
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("junit:junit:4.11", "implementation"))
        }
      }
      clickOk()
    }
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().selectModule("mylibrary")
        findDependenciesPanel().run {
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("junit:junit:4.11", "implementation"))
          findDependenciesTable().cell("junit:junit:4.11").click()
          findScopeEditor().run { assertThat(text()).isEqualTo("implementation") }
        }
      }
      clickCancel()
    }
  }
}
