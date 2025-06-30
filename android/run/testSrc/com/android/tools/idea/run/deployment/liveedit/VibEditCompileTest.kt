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
import com.android.tools.idea.run.deployment.liveedit.analysis.directApiCompileByteArray
import com.android.tools.idea.run.deployment.liveedit.analysis.disableLiveEdit
import com.android.tools.idea.run.deployment.liveedit.analysis.enableLiveEdit
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ExtensionTestUtil
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class VibEditCompileTest {
  private var projectRule = AndroidProjectRule.inMemory().withKotlin()

  // We don't need ADB in these tests. However, disableLiveEdit() or endableLiveEdit() does trigger calls to the AdbDebugBridge
  // so not having that available causes a NullPointerException when we call it.
  private val fakeAdb: FakeAdbTestRule = FakeAdbTestRule("30")

  @get:Rule
  val chain = RuleChain.outerRule(projectRule).around(fakeAdb)

  private val helloToGoodbyeTransformerProvider = object : VibeTransformerProvider {
    override fun createVibeTransformer() = object : VibeTransformer {
      override suspend fun transformVibe(file: PsiFile,
                                         vibe: String): VibeTransformerResult =
        VibeTransformerResult(file.text.replace("Hello", "Goodbye"))
    }
  }

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)
    disableLiveEdit()

    ExtensionTestUtil.maskExtensions(
      VibeTransformerProvider.EP_NAME,
      listOf(helloToGoodbyeTransformerProvider),
      projectRule.testRootDisposable,
    )
  }

  @After
  fun tearDown() {
    enableLiveEdit()
  }

  @Test
  fun simpleChange() {
    val file = projectRule.createKtFile("A.kt", """
      fun foo() = "Hello World!"
    """)

    val cache = MutableIrClassCache()
    val apk = projectRule.directApiCompileByteArray(file).toMutableMap()
    apk.putAll(projectRule.directApiCompileByteArray(file))

    val compiler = LiveEditCompiler(projectRule.project, cache).withClasses(apk)
    val output = compile(listOf(
      LiveEditCompilerInput(file, readPsiValidationState(file), "Change Hello to GoodBye")), compiler)

    val returnedValue = invokeStatic("foo", loadClass(output))
    Assert.assertEquals("Goodbye World!", returnedValue)
  }

  private fun readPsiValidationState(file: PsiFile): PsiState {
    return runReadAction {
      getPsiValidationState(file)
    }
  }
}
