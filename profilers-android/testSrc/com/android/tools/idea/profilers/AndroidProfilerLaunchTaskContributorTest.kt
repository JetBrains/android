/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.profilers

import com.google.common.truth.Truth.assertThat
import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Rule
import org.junit.Test

class AndroidProfilerLaunchTaskContributorTest : LightPlatformTestCase() {
  @get:Rule
  val disposableRule = DisposableRule()

  @Test
  fun testIsProfilerLaunch() {
    // Register Profiler and Profiler Group as executors.
    ExtensionTestUtil.addExtensions(Executor.EXECUTOR_EXTENSION_NAME, listOf(ProfileRunExecutor(), ProfileRunExecutorGroup()),
                                    disposableRule.disposable)

    // Should return true for the legacy profile executor.
    val profileExecutor = ProfileRunExecutor.getInstance()!!
    assertThat(AndroidProfilerLaunchTaskContributor.isProfilerLaunch(profileExecutor)).isTrue()

    // Should return true for the profileable executor group.
    val profileableExecutor = ProfileRunExecutorGroup.getInstance()!!.childExecutors()[0]
    assertThat(AndroidProfilerLaunchTaskContributor.isProfilerLaunch(profileableExecutor)).isTrue()

    // Should return false otherwise.
    val defaultRunExecutor = DefaultRunExecutor.getRunExecutorInstance()
    assertThat(AndroidProfilerLaunchTaskContributor.isProfilerLaunch(defaultRunExecutor)).isFalse()
  }
}