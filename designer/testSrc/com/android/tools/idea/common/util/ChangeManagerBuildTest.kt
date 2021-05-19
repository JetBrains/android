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
package com.android.tools.idea.common.util

import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.util.BuildMode
import com.intellij.testFramework.PlatformTestCase
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations.initMocks

class ChangeManagerBuildTest : PlatformTestCase() {
  private lateinit var buildContext: BuildContext
  private lateinit var buildState: GradleBuildState

  @Mock
  private lateinit var buildListener: BuildListener

  override fun setUp() {
    super.setUp()
    initMocks(this)

    setupBuildListener(project, buildListener, project)
    buildState = GradleBuildState.getInstance(project)
  }

  override fun tearDown() {
    try {
      buildState.clear()
    }
    finally {
      super.tearDown()
    }
  }

  private fun createContext(buildMode: BuildMode): BuildContext {
    return BuildContext(project, listOf("task1", "task2"), buildMode)
  }

  fun testBuildSuccessful() {
    buildContext = createContext(BuildMode.ASSEMBLE)

    buildState.buildStarted(buildContext)

    Mockito.verify(buildListener).buildStarted()

    buildState.buildFinished(BuildStatus.SUCCESS)

    Mockito.verify(buildListener).buildSucceeded()
  }

  fun testBuildFailed() {
    buildContext = createContext(BuildMode.ASSEMBLE)

    buildState.buildStarted(buildContext)
    buildState.buildFinished(BuildStatus.FAILED)

    Mockito.verify(buildListener).buildFailed()
  }

  fun testBuildCancelled() {
    buildContext = createContext(BuildMode.ASSEMBLE)

    buildState.buildStarted(buildContext)

    buildState.buildFinished(BuildStatus.FAILED)

    Mockito.verify(buildListener).buildFailed()
  }

  fun testCleanBuild() {
    buildContext = createContext(BuildMode.CLEAN)

    buildState.buildStarted(buildContext)

    Mockito.verify(buildListener, Mockito.never()).buildStarted()

    buildState.buildFinished(BuildStatus.SUCCESS)

    Mockito.verify(buildListener, Mockito.never()).buildSucceeded()
    Mockito.verify(buildListener).buildFailed()
  }
}