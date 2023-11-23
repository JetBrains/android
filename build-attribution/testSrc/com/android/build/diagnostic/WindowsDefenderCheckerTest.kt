/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.build.diagnostic

import com.android.testutils.AssumeUtil
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.diagnostic.WindowsDefenderChecker
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class WindowsDefenderCheckerTest {

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  companion object {
    @BeforeClass
    @JvmStatic
    fun setup() {
      AssumeUtil.assumeWindows()
    }
  }

  @Test
  fun testReturnsImportantPaths() {
    val checker = WindowsDefenderChecker.getInstance()
    val paths = checker.getPathsToExclude(projectRule.project)
    println("\n${WindowsDefenderCheckerTest::class.simpleName}: ImportantPaths=$paths")
    Truth.assertThat(paths).isNotEmpty()
  }

  /**
   * This test verifies WindowsDefenderChecker validity, that it can be instantiated and can read defender status
   * from the system without an error.
   * There is no way to safely test the correctness of this interaction but at least it should not break unexpectedly.
   */
  @Test
  fun testProtectionStatusRead() {
    val protectionStatus = WindowsDefenderChecker.getInstance().isRealTimeProtectionEnabled
    println("\n${WindowsDefenderCheckerTest::class.simpleName}: protectionStatus=$protectionStatus")
    Truth.assertThat(protectionStatus).isNotNull()
  }
}