// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.tests.gui.framework

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testGuiFramework.framework.BetweenRestarts
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import com.intellij.testGuiFramework.framework.restartIdeBetween
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(GuiTestRemoteRunner::class)
@RunIn(TestGroup.TEST_FRAMEWORK)
class RestartIdeTest {

  @JvmField @Rule val guiTest = GuiTestRule()

  companion object {
    private const val MARKER_FILE_NAME = "RestartIdeTestMarker"
  }

  @BetweenRestarts
  fun createMarkerFile() {
    FileUtil.createTempFile(MARKER_FILE_NAME, null, false)
  }

  // This is a simple test of the framework, which ensures that the IDE can be restarted, a test can be resumed afterward, and the
  // @BetweenRestarts functionality works.
  @Test
  fun testRestart() {
    fun firstRun() {
      FileUtil.delete(File(FileUtil.getTempDirectory(), MARKER_FILE_NAME))
      guiTest.importSimpleLocalApplication()
    }
    fun secondRun() {
      assertThat(File(FileUtil.getTempDirectory(), MARKER_FILE_NAME).exists()).isTrue()
      guiTest.importSimpleLocalApplication()
    }
    restartIdeBetween(::firstRun, ::secondRun)
  }
}