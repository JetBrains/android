/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.guessProjectDir
import org.junit.Test

class ComposeRenderTest : ComposeRenderTestBase() {
  @Test
  fun baselineCompile() {
    val mainFile =
      projectRule.fixture.project
        .guessProjectDir()!!
        .findFileByRelativePath("app/src/main/java/google/simpleapplication/MainActivity.kt")!!
    ApplicationManager.getApplication().invokeAndWait {
      WriteAction.run<Throwable> {
        projectRule.fixture.openFileInEditor(mainFile)
        projectRule.fixture.type("//")
      }
    }
    SimpleComposeProjectScenarios.baselineCompileScenario(projectRule)
  }

  @Test
  fun baselineLayout() {
    SimpleComposeProjectScenarios.baselineRenderScenario(projectRule)
  }

  @Test
  fun complexLayout() {
    SimpleComposeProjectScenarios.complexRenderScenario(projectRule)
  }

  @Test
  fun interactiveLayout() {
    SimpleComposeProjectScenarios.interactiveRenderScenario(projectRule)
  }
}
