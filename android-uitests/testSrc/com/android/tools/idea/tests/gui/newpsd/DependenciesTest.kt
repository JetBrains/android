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
package com.android.tools.idea.tests.gui.newpsd

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.items
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.openPsd
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectDependenciesConfigurable
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.timing.Wait
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRemoteRunner::class)
class DependenciesTest {

  @Rule
  @JvmField
  val guiTest = PsdGuiTestRule()

  @Before
  fun setUp() {
    StudioFlags.NEW_PSD_ENABLED.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.NEW_PSD_ENABLED.clearOverride()
  }

  @Test
  fun addLibraryDependency() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("PsdSimple")

    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().run {
          selectModule("app")
        }
        findDependenciesPanel().run {
          clickAddLibraryDependency().run {
            findSearchQueryTextBox().enterText("com.example.jlib:*")
            findSearchButton().click()
            findArtifactsView().run {
              Wait
                .seconds(10)
                .expecting("Search completed")
                .until {
                  rowCount() == 2
                }
              cell(Pattern.compile("lib3")).click()
            }
            findVersionsView().run {
              cell(Pattern.compile("0.5")).click()
            }
            clickOk()
          }
        }
        clickOk()
      }
    }
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().run {
          selectModule("app")
        }
        findDependenciesPanel().run {
          assertThat(
            findDependenciesTable().contents().map { it.toList()})
            .contains(listOf("com.example.jlib:lib3:0.5", "implementation"))
        }
      }
      clickCancel()
    }
  }

  @Test
  fun addModuleDependency() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("PsdSimple")

    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().run {
          selectModule("app")
        }
        findDependenciesPanel().run {
          clickAddModuleDependency().run {
            toggleModule("mylibrary")
            clickOk()
          }
        }
      }
      clickOk()
    }
    ide.openPsd().run {
      selectDependenciesConfigurable().run {
        findModuleSelector().run {
          selectModule("app")
        }
        findDependenciesPanel().run {
          assertThat(items()).contains("mylibrary")
        }
      }
      clickCancel()
    }
  }
}
