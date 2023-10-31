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
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.findSuggestionsConfigurable
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.items
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.openPsd
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectBuildVariantsConfigurable
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectDependenciesConfigurable
import com.google.common.truth.Truth
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.hamcrest.CoreMatchers.equalTo
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GuiTestRemoteRunner::class)
class BuildVariantsTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  @Test
  fun addBuildType() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("PsdSimple")

    ide.openPsd().run {
      selectBuildVariantsConfigurable().run {
        selectBuildTypesTab().run {
          clickAdd().run {
            type("newType")
            clickOk()
          }
          debuggable().selectItem("true")
          versionNameSuffix().enterText("suffix")
        }
      }
      clickOk()
    }
    ide.openPsd().run {
      selectBuildVariantsConfigurable().run {
        selectBuildTypesTab().run {
          Truth.assertThat(items())
            .containsAllIn(listOf("release", "newType"))  // If still syncing, debug build type may not be yet there.
        }
        selectBuildTypesTab().run {
          selectItemByPath("newType")
          assertThat(debuggable().getText(), equalTo("true"))
          assertThat(versionNameSuffix().getText(), equalTo("suffix"))
        }
      }
      clickCancel()
    }
  }

  @Test
  fun addProductFlavors() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("PsdSimple")

    ide.openPsd().run {
      selectBuildVariantsConfigurable().run {
        selectProductFlavorsTab().run {
          clickAddFlavorDimension().run {
            type("newDim")
            clickOk()
          }
          clickAddProductFlavor().run {
            type("newFlavor1")
            clickOk()
          }
          minSdkVersion().selectItem("24 (API 24: Android 7.0 (Nougat))")
          targetSdkVersion().enterText("24")
          clickAddProductFlavor().run {
            type("newFlavor2")
            clickOk()
          }
          minSdkVersion().enterText("27")
          targetSdkVersion().selectItemWithKeyboard("27 (API 27: Android 8.1 (Oreo))")
        }
      }
      clickOk()
    }
    ide.openPsd().run {
      selectBuildVariantsConfigurable().run {
        selectProductFlavorsTab().run {
          assertThat(items(), equalTo(listOf("newDim", "-newFlavor1", "-newFlavor2")))
        }
        selectProductFlavorsTab().run {
          selectItemByPath("newDim/newFlavor1")
          assertThat(minSdkVersion().getText(), equalTo("24"))
          assertThat(targetSdkVersion().getText(), equalTo("24"))
          selectItemByPath("newDim/newFlavor2")
          assertThat(minSdkVersion().getText(), equalTo("27"))
          assertThat(targetSdkVersion().getText(), equalTo("27"))
        }
      }
      clickCancel()
    }
  }

  @Test
  fun addDependentBuildTypes() {
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
      selectBuildVariantsConfigurable().run {
        findModuleSelector().run {
          selectModule("app")
        }
        selectBuildTypesTab().run {
          clickAdd().run {
            type("newBuildType")
            clickOk()
          }
        }
      }
      clickOkExpectConfirmation().run {
        clickReview()
      }
      findSuggestionsConfigurable().run {
        findGroup("Errors").run {
          findMessageMatching("No build type in module ':mylibrary' matches build type 'newBuildType'.")!!.run {
            clickAction()
          }
        }
      }
      clickOk()
    }
    ide.openPsd().run {
      selectBuildVariantsConfigurable().run {
        selectBuildTypesTab().run {
          Truth.assertThat(items()).contains("newBuildType")
        }
      }
      clickCancel()
    }
  }

  @Test
  fun extractVariableReleaseBuildType() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("PsdSimple")

    ide.openPsd().run {
      selectBuildVariantsConfigurable().run {
        selectBuildTypesTab().run {
          selectItemByPath("release")
          versionNameSuffix().run {
            invokeExtractVariable().run {
              findName().run {
                Truth.assertThat(text()).isEqualTo("releaseVersionNameSuffix")
              }
              findScope().run {
                selectItem("Module: app")
              }
              clickCancel()
            }
          }
        }
      }
      clickCancel()
    }
  }

  @Test
  fun extractVariableDebugBuildType() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("PsdSimple")

    ide.openPsd().run {
      selectBuildVariantsConfigurable().run {
        selectBuildTypesTab().run {
          selectItemByPath("debug")
          versionNameSuffix().run {
            invokeExtractVariable().run {
              findName().run {
                Truth.assertThat(text()).isEqualTo("debugVersionNameSuffix")
              }
              clickCancel()
            }
          }
        }
      }
      clickCancel()
    }
  }
}
