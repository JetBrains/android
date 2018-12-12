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
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.ProjectStructureDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.items
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.openPsd
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectBuildVariantsConfigurable
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.hamcrest.CoreMatchers.equalTo
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRemoteRunner::class)
class BuildVariantsTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  @Before
  fun setUp() {
    StudioFlags.NEW_PSD_ENABLED.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.NEW_PSD_ENABLED.clearOverride()
  }

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
        }
      }
      clickOk()
    }
    ide.openPsd().run {
      selectBuildVariantsConfigurable().run {
        selectBuildTypesTab().run {
          assertThat(items(), equalTo(listOf("release", "newType", "debug")))
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
          clickAddProductFlavor().run {
            type("newFlavor2")
            clickOk()
          }
        }
      }
      clickOk()
    }
    ide.openPsd().run {
      selectBuildVariantsConfigurable().run {
        selectProductFlavorsTab().run {
          assertThat(items(), equalTo(listOf("newDim", "-newFlavor1", "-newFlavor2")))
        }
      }
      clickCancel()
    }
  }
}
