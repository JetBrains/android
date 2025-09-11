/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.tools.idea.run.deployment.liveedit.analysis.createKtFile
import com.android.tools.idea.run.deployment.liveedit.analysis.enableLiveEdit
import com.android.tools.idea.run.deployment.liveedit.analysis.postDeploymentStateCompile
import com.android.tools.idea.testing.AndroidProjectRule
import org.junit.After
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class DesugarerCompileTest {

  private var projectRule = AndroidProjectRule.inMemory().withKotlin()

  // We don't need ADB in these tests. However, disableLiveEdit() or endableLiveEdit() does trigger calls to the AdbDebugBridge
  // so not having that available causes a NullPointerException when we call it.
  private val fakeAdb: FakeAdbTestRule = FakeAdbTestRule("30")
  @get:Rule
  val chain = RuleChain.outerRule(projectRule).around(fakeAdb)

  @After
  fun tearDown() {
    enableLiveEdit()
  }

  @Test
  fun simpleChange() {
    val file = projectRule.createKtFile("A.kt", """
      fun foo() = ""
      fun bar() = 1
    """)

    val exception = Assert.assertThrows(LiveEditUpdateException::class.java) {
      projectRule.postDeploymentStateCompile(file, """
      fun foo() = "I am not foo"
      fun bar() = 1
    """, setOf(30))
    }

    // We currently don't have all the tools to run desugaring in unit tests but we have at least one
    // test in the ETE that does desguaring.
    Assert.assertTrue(exception.message!!.contains("Unexpected error during compilation command"))
  }
}