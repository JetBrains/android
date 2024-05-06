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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.run.deployment.liveedit.analysis.createKtFile
import com.android.tools.idea.run.deployment.liveedit.analysis.directApiCompileIr
import com.android.tools.idea.run.deployment.liveedit.analysis.disableLiveEdit
import com.android.tools.idea.run.deployment.liveedit.analysis.enableLiveEdit
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass
import com.android.tools.idea.run.deployment.liveedit.analysis.modifyKtFile
import com.android.tools.idea.testing.AndroidProjectRule
import org.jetbrains.kotlin.psi.KtFile
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals

@RunWith(JUnit4::class)
class IncompatibleChangeCompileTest {
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)
    disableLiveEdit()
  }

  @After
  fun tearDown() {
    enableLiveEdit()
  }

  @Test
  fun `Add field`() {
    val file = projectRule.createKtFile("A.kt", """
      class Test {
        val x = 0
      }
    """)

    val cache = MutableIrClassCache()
    val apk = projectRule.directApiCompileIr(file)
    val compiler = LiveEditCompiler(projectRule.project, cache, provider(apk))

    projectRule.modifyKtFile(file, """
      class Test {
        val x = 0
        val y = 0
      }
    """)

    // First edit - diff with APK
    val firstError = Assert.assertThrows(LiveEditUpdateException::class.java) { compile(file, compiler) }
    assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_UNRECOVERABLE, firstError.error)
    assertEquals("in Test, added field(s): y", firstError.message)

    // Second+ edit - diff with cache
    cache.update(apk.values.toList())
    val secondError = Assert.assertThrows(LiveEditUpdateException::class.java) { compile(file, compiler) }
    assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_UNRECOVERABLE, secondError.error)
    assertEquals("in Test, added field(s): y", secondError.message)
  }

  @Test
  fun `Remove field`() {
    val file = projectRule.createKtFile("A.kt", """
      class Test {
        val x = 0
        val y = 0
      }
    """)

    val cache = MutableIrClassCache()
    val apk = projectRule.directApiCompileIr(file)
    val compiler = LiveEditCompiler(projectRule.project, cache, provider(apk))

    projectRule.modifyKtFile(file, """
      class Test {
        val y = 0
      }
    """)

    // First edit - diff with APK
    val firstError = Assert.assertThrows(LiveEditUpdateException::class.java) { compile(file, compiler) }
    assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_UNRECOVERABLE, firstError.error)
    assertEquals("in Test, removed field(s): x", firstError.message)

    // Second+ edit - diff with cache
    cache.update(apk.values.toList())
    val secondError = Assert.assertThrows(LiveEditUpdateException::class.java) { compile(file, compiler) }
    assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_UNRECOVERABLE, secondError.error)
    assertEquals("in Test, removed field(s): x", secondError.message)
  }

  private fun provider(apk: Map<String, IrClass>): ApkClassProvider = object: ApkClassProvider {
    override fun getClass(ktFile: KtFile, className: String) = apk[className]
  }
}
