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
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.StudioRobot
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.openPsd
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectDependenciesConfigurable
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectSuggestionsConfigurable
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.waitForDialogToClose
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_TAB

@RunWith(GuiTestRemoteRunner::class)
class EditableConfigurationsTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  @Before
  fun setUp() {
    StudioFlags.NEW_PSD_ENABLED.override(true)
    // Under StudioRobot, typing into an editor happens character-by-character below a certain limit.  The EditorComboBox implementation
    // we currently use will pop up a completion window, which will update and disappear at uncertain times, and testing for the presence
    // of this window is also unreliable.  We work around this in these tests by using replaceText() with long strings, which the
    // StudioRobot will paste all in one go; the reliability of these tests is consequently dependent on the robot's implementation
    // strategy.
    assertThat(StudioRobot.MAX_CHARS_TO_TYPE).isAtMost(8)
  }

  @After
  fun tearDown() {
    StudioFlags.NEW_PSD_ENABLED.clearOverride()
  }

  @RunIn(TestGroup.UNRELIABLE)
  @Test
  fun testEditableJarDependencyConfiguration() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("psdObsoleteScopes")
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().selectModule("mylibrary")
        findDependenciesPanel().run {
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("libs", "compile"))

          findDependenciesTable().cell("libs").click()

          assertThat(findDependenciesTable().contents().map { it.toList() })
            .contains(listOf("com.android.support:appcompat-v7:26.0.1", "compile"))
          findConfigurationCombo().run {
            assertThat(selectedItem()).isEqualTo("compile")
            replaceText("implementation")
            pressAndReleaseKeys(VK_TAB)
            assertThat(selectedItem()).isEqualTo("implementation")
          }
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("libs", "implementation"))

          findDependenciesTable().cell("libs").click()
          findConfigurationCombo().run {
            assertThat(selectedItem()).isEqualTo("implementation")
            replaceText("releaseImplementation")
            pressAndReleaseKeys(VK_ENTER) // activates the dialog
          }
        }
      }
      waitForDialogToClose()
      waitForSyncToFinish()
    }
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().selectModule("mylibrary")
        findDependenciesPanel().run {
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("libs", "releaseImplementation"))
          findDependenciesTable().cell("libs").click()
          assertThat(findDependenciesTable().contents().map { it.toList() })
            .contains(listOf("com.android.support:appcompat-v7:26.0.1", "compile"))
          findConfigurationCombo().run { assertThat(selectedItem()).isEqualTo("releaseImplementation") }
        }
      }
      clickCancel()
    }
  }

  @Test
  fun testEditableJarDependencyConfigurationDropdown() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("psdObsoleteScopes")
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().selectModule("mylibrary")
        findDependenciesPanel().run {
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("libs", "compile"))
          findDependenciesTable().cell("libs").click()
          findConfigurationCombo().run {
            assertThat(selectedItem()).isEqualTo("compile")
            selectItem("implementation")
            assertThat(selectedItem()).isEqualTo("implementation")
          }
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("libs", "implementation"))

          findDependenciesTable().cell("libs").click()
          findConfigurationCombo().run {
            assertThat(selectedItem()).isEqualTo("implementation")
            selectItem("releaseImplementation")
            assertThat(selectedItem()).isEqualTo("releaseImplementation")
          }
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("libs", "releaseImplementation"))
        }
      }
      clickOk()
    }
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().selectModule("mylibrary")
        findDependenciesPanel().run {
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("libs", "releaseImplementation"))
          findDependenciesTable().cell("libs").click()
          findConfigurationCombo().run { assertThat(selectedItem()).isEqualTo("releaseImplementation") }
        }
      }
      clickCancel()
    }
  }

  @RunIn(TestGroup.UNRELIABLE)
  @Test
  fun testEditableModuleDependencyConfiguration() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("psdObsoleteScopes")
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().selectModule("app")
        findDependenciesPanel().run {
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("mylibrary", "compile"))
          findDependenciesTable().cell("mylibrary").click()
          findConfigurationCombo().run {
            assertThat(selectedItem()).isEqualTo("compile")
            replaceText("implementation")
            pressAndReleaseKeys(VK_TAB)
            assertThat(selectedItem()).isEqualTo("implementation")
          }
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("mylibrary", "implementation"))

          findDependenciesTable().cell("mylibrary").click()
          findConfigurationCombo().run {
            assertThat(selectedItem()).isEqualTo("implementation")
            replaceText("testImplementation")
            pressAndReleaseKeys(VK_ENTER) // activates dialog
          }
        }
      }
      waitForDialogToClose()
      waitForSyncToFinish()
    }
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().selectModule("app")
        findDependenciesPanel().run {
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("mylibrary", "testImplementation"))
          findDependenciesTable().cell("mylibrary").click()
          findConfigurationCombo().run { assertThat(selectedItem()).isEqualTo("testImplementation") }
        }
      }
      clickCancel()
    }
  }

  @Test
  fun testEditableModuleDependencyConfigurationDropdown() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("psdObsoleteScopes")
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().selectModule("app")
        findDependenciesPanel().run {
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("mylibrary", "compile"))
          findDependenciesTable().cell("mylibrary").click()
          findConfigurationCombo().run {
            assertThat(selectedItem()).isEqualTo("compile")
            selectItem("implementation")
            assertThat(selectedItem()).isEqualTo("implementation")
          }
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("mylibrary", "implementation"))

          findDependenciesTable().cell("mylibrary").click()
          findConfigurationCombo().run {
            assertThat(selectedItem()).isEqualTo("implementation")
            selectItem("testImplementation")
            assertThat(selectedItem()).isEqualTo("testImplementation")
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
          findConfigurationCombo().run { assertThat(selectedItem()).isEqualTo("testImplementation") }
        }
      }
      clickCancel()
    }
  }

  @RunIn(TestGroup.UNRELIABLE)
  @Test
  fun testEditableLibraryDependencyConfiguration() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("psdObsoleteScopes")
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().selectModule("mylibrary")
        findDependenciesPanel().run {
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("junit:junit:4.11", "testCompile"))

          findDependenciesTable().cell("junit:junit:4.11").click()
          assertThat(findDependenciesTable().contents().map { it.toList() })
            .contains(listOf("com.android.support:appcompat-v7:26.0.1", "compile"))

          findConfigurationCombo().run {
            assertThat(selectedItem()).isEqualTo("testCompile")
            replaceText("testImplementation")
            pressAndReleaseKeys(VK_TAB)
            assertThat(selectedItem()).isEqualTo("testImplementation")
          }
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("junit:junit:4.11", "testImplementation"))

          findDependenciesTable().cell("junit:junit:4.11").click()
          findConfigurationCombo().run {
            assertThat(selectedItem()).isEqualTo("testImplementation")
            replaceText("implementation")
            pressAndReleaseKeys(VK_ENTER) // activates the dialog
          }
        }
      }
      waitForDialogToClose()
      waitForSyncToFinish()
    }
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().selectModule("mylibrary")
        findDependenciesPanel().run {
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("junit:junit:4.11", "implementation"))
          findDependenciesTable().cell("junit:junit:4.11").click()
          assertThat(findDependenciesTable().contents().map { it.toList() })
            .contains(listOf("com.android.support:appcompat-v7:26.0.1", "compile"))
          findConfigurationCombo().run { assertThat(selectedItem()).isEqualTo("implementation") }
        }
      }
      clickCancel()
    }
  }

  @Test
  fun testEditableLibraryDependencyConfigurationDropdown() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("psdObsoleteScopes")
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().selectModule("mylibrary")
        findDependenciesPanel().run {
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("junit:junit:4.11", "testCompile"))

          findDependenciesTable().cell("junit:junit:4.11").click()

          findConfigurationCombo().run {
            assertThat(selectedItem()).isEqualTo("testCompile")
            selectItem("testImplementation")
            assertThat(selectedItem()).isEqualTo("testImplementation")
          }
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("junit:junit:4.11", "testImplementation"))

          findDependenciesTable().cell("junit:junit:4.11").click()
          findConfigurationCombo().run {
            assertThat(selectedItem()).isEqualTo("testImplementation")
            selectItem("implementation")
            assertThat(selectedItem()).isEqualTo("implementation")
          }
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
          findConfigurationCombo().run { assertThat(selectedItem()).isEqualTo("implementation") }
        }
      }
      clickCancel()
    }
  }

  @Test
  fun testEmptyConfigurationTriggersWarning() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("psdObsoleteScopes")
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().selectModule("app")
        findDependenciesPanel().run {
          assertThat(findDependenciesTable().contents().map { it.toList() }).contains(listOf("mylibrary", "compile"))
          findDependenciesTable().cell("mylibrary").click()
          findConfigurationCombo().run {
            assertThat(selectedItem()).isEqualTo("compile")
            selectItem("")
            assertThat(selectedItem()).isEqualTo("")
          }
        }
      }
      selectSuggestionsConfigurable().run {
        waitForGroup("Errors")
        findGroup("Errors").run {
          assertThat(findMessageMatching("Empty configuration")).isNotNull()
        }
      }
      clickCancel()
    }
  }
}
