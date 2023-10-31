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
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.items
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.openPsd
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectDependenciesConfigurable
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectSuggestionsConfigurable
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectVariablesConfigurable
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.timing.Wait
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern
import kotlin.test.assertNotNull

@RunWith(GuiTestRemoteRunner::class)
class DependenciesTest {

  @Rule
  @JvmField
  val guiTest = PsdGuiTestRule()

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

    ide.editor.open("app/build.gradle").run {
      assertThat(currentFileContents)
        .contains("    implementation 'com.android.support.constraint:constraint-layout:1.0.2'\n" +
                  "    implementation 'com.example.jlib:lib3:0.5'\n" +
                  "    testImplementation 'junit:junit:4.11'\n")
    }
  }

  @Test
  fun newVersionAvailableSuggestions() {
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
              cell(Pattern.compile("lib4")).click()
            }
            findVersionsView().run {
              cell(Pattern.compile("0.6.1-alpha")).click()
            }
            clickOk()
          }
        }
        findModuleSelector().run {
          selectModule("mylibrary")
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
              cell(Pattern.compile("lib4")).click()
            }
            findVersionsView().run {
              cell(Pattern.compile("0.6.1")).click()
            }
            clickOk()
          }
        }
        selectSuggestionsConfigurable().run {
          findModuleSelector().run {
            selectModule("<All Modules>")
          }
          waitAnalysesCompleted(Wait.seconds(1))
          waitForGroup("Updates")
          findGroup("Updates").run {
            assertNotNull(findMessageMatching("com.example.jlib:lib4:0.6.1-alpha\nNewer version available: 1.1-alpha"))
            assertNotNull(findMessageMatching("com.example.jlib:lib4:0.6.1\nNewer version available: 1.0"))
          }
        }
        clickOk()
      }
    }
    ide.openPsd().run {
      selectSuggestionsConfigurable().run {
        findModuleSelector().run {
          selectModule("<All Modules>")
        }
        waitAnalysesCompleted(Wait.seconds(1))
        waitForGroup("Updates")
        findGroup("Updates").run {
          assertNotNull(findMessageMatching("com.example.jlib:lib4:0.6.1-alpha\nNewer version available: 1.1-alpha"))
          assertNotNull(findMessageMatching("com.example.jlib:lib4:0.6.1\nNewer version available: 1.0"))
        }
      }
      clickCancel()
    }
  }

  @Test
  fun addLibraryDependencyViaVariable() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("PsdSimple")

    ide.openPsd().run {
      selectVariablesConfigurable().run {
        clickAddMap()
        enterText("deps")
        tab()
        enterText("lib3Version")
        tab()
        enterText("0.5")
        tab()
        enterText("lib4Version")
        tab()
        enterText("0.6.1")
        tab()
      }
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
              cell("\$deps.lib3Version : 0.5").click()
            }
            clickOk()
          }
          clickAddLibraryDependency().run {
            findSearchQueryTextBox().enterText("com.example.jlib:lib4")
            findSearchButton().click()
            findArtifactsView().run {
              Wait
                .seconds(10)
                .expecting("Search completed")
                .until {
                  rowCount() == 1
                }
              cell(Pattern.compile("lib4")).click()
            }
            findVersionsView().run {
              cell("\$deps.lib4Version : 0.6.1").click()
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
            findDependenciesTable().contents().map { it.toList() })
            .containsAllIn(listOf(
              listOf("com.example.jlib:lib3:0.5", "implementation"),
              listOf("com.example.jlib:lib4:0.6.1", "implementation")
            ))
        }
      }
      clickCancel()
    }

    ide.editor.open("app/build.gradle").run {
      assertThat(currentFileContents)
        .contains("    implementation 'com.android.support.constraint:constraint-layout:1.0.2'\n" +
                  "    implementation \"com.example.jlib:lib3:\${deps.lib3Version}\"\n" +
                  "    implementation \"com.example.jlib:lib4:\${deps.lib4Version}\"\n" +
                  "    testImplementation 'junit:junit:4.11'\n")
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
          assertThat(findDependenciesTable().contents().map { it[0] }).contains("mylibrary")
        }
      }
      clickCancel()
    }
    ide.editor.open("app/build.gradle").run {
      println(currentFileContents)
      assertThat(currentFileContents)
        .contains("    implementation 'com.android.support.constraint:constraint-layout:1.0.2'\n" +
                  "    implementation project(path: ':mylibrary')\n" +
                  "    testImplementation 'junit:junit:4.11'\n")
    }
  }

  @Test
  fun testFocusAfterUpdateQuickfix() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("psdDependency")

    ide.openPsd().run {
      // ensure that analyses are complete, so that quickfix hyperlinks will be available in the dependencies view
      selectSuggestionsConfigurable().run {
        waitAnalysesCompleted(Wait.seconds(5))
      }
      selectDependenciesConfigurable().run {
        findModuleSelector().run {
          selectModule("<All Modules>")
        }
        findDependenciesPanel().run {
          findDependenciesTree().run {
            // TODO(b/137730929): we need to wait for resolved dependencies to appear, because the subsequent clickPath() below
            //  is not atomic: the test infrastructure scrolls (atomically), then clicks (atomically), but if resolved dependencies
            //  appear between the two actions, the click will land on the wrong dependency.
            waitForTreeEntry("com.android.support:support-annotations:28.0.0")
            clickPath("com.example.jlib:lib3")
            requireSelection("com.example.jlib:lib3")
            clickQuickFixHyperlink("[Update]")
            // clicking the hyperlink should not have changed the dependency focus
            requireSelection("com.example.jlib:lib3")
          }
        }
      }
      clickCancel()
    }
  }
}
