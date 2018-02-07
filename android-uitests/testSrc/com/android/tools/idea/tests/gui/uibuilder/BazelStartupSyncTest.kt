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
package com.android.tools.idea.tests.gui.uibuilder

import com.android.tools.idea.tests.gui.framework.BuildSpecificGuiTestRunner
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.TargetBuildSystem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*

@RunWith(Parameterized::class)
@Parameterized.UseParametersRunnerFactory(BuildSpecificGuiTestRunner.Factory::class)
class BazelStartupSyncTest {
  @get:Rule
  val guiTest = GuiTestRule()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): List<TargetBuildSystem.BuildSystem> = Collections.singletonList(TargetBuildSystem.BuildSystem.BAZEL)
  }

  @Test
  @TargetBuildSystem(TargetBuildSystem.BuildSystem.BAZEL)
  fun startupBazelSyncSucceeds() {
    guiTest.importSimpleLocalApplication()
  }

}