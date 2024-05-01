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

import com.android.ddmlib.internal.FakeAdbTestRule
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
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals

@RunWith(JUnit4::class)
class IncompatibleChangeCompileTest {
  private var projectRule = AndroidProjectRule.inMemory().withKotlin()
  private val fakeAdb: FakeAdbTestRule = FakeAdbTestRule("30")
  @get:Rule
  val chain = RuleChain.outerRule(projectRule).around(fakeAdb)

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
    assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_FIELD_ADDED, firstError.error)
    assertEquals("added field(s): y", firstError.message)

    // Second+ edit - diff with cache
    cache.update(apk.values.toList())
    val secondError = Assert.assertThrows(LiveEditUpdateException::class.java) { compile(file, compiler) }
    assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_FIELD_ADDED, secondError.error)
    assertEquals("added field(s): y", secondError.message)
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
    assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_FIELD_REMOVED, firstError.error)
    assertEquals("removed field(s): x", firstError.message)

    // Second+ edit - diff with cache
    cache.update(apk.values.toList())
    val secondError = Assert.assertThrows(LiveEditUpdateException::class.java) { compile(file, compiler) }
    assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_FIELD_REMOVED, secondError.error)
    assertEquals("removed field(s): x", secondError.message)
  }

  @Test
  fun `Add fun`() {
    val file = projectRule.createKtFile("A.kt", """
      class Test {
        fun x() = 0
      }
    """)

    val cache = MutableIrClassCache()
    val apk = projectRule.directApiCompileIr(file)
    val compiler = LiveEditCompiler(projectRule.project, cache, provider(apk))

    projectRule.modifyKtFile(file, """
      class Test {
        fun x() = 0
        fun y(param: java.lang.String) = 0
      }
    """)

    // First edit - diff with APK
    val firstError = Assert.assertThrows(LiveEditUpdateException::class.java) { compile(file, compiler) }
    assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_METHOD_ADDED, firstError.error)
    assertEquals("added method(s): y", firstError.message)

    // Second+ edit - diff with cache
    cache.update(apk.values.toList())
    val secondError = Assert.assertThrows(LiveEditUpdateException::class.java) { compile(file, compiler) }
    assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_METHOD_ADDED, secondError.error)
    assertEquals("added method(s): y", secondError.message)
  }

  @Test
  fun `Add fun overloaded`() {
    val file = projectRule.createKtFile("A.kt", """
      class Test {
        fun y() = 0
      }
    """)

    val cache = MutableIrClassCache()
    val apk = projectRule.directApiCompileIr(file)
    val compiler = LiveEditCompiler(projectRule.project, cache, provider(apk))

    projectRule.modifyKtFile(file, """
      class Test {
        fun y() = 0
        fun y(param: java.lang.String) = 0
        fun y(param: Test) = 0
      }
    """)

    // First edit - diff with APK
    val firstError = Assert.assertThrows(LiveEditUpdateException::class.java) { compile(file, compiler) }
    assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_METHOD_ADDED, firstError.error)
    assertEquals("added method(s): int y(String), int y(Test)", firstError.message)

    // Second+ edit - diff with cache
    cache.update(apk.values.toList())
    val secondError = Assert.assertThrows(LiveEditUpdateException::class.java) { compile(file, compiler) }
    assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_METHOD_ADDED, secondError.error)
    assertEquals("added method(s): int y(String), int y(Test)", secondError.message)
  }

  @Test
  fun `Change init`() {
    val file = projectRule.createKtFile("A.kt", """
      class Test {
        var x = 1
      }
    """)

    val cache = MutableIrClassCache()
    val apk = projectRule.directApiCompileIr(file)
    val compiler = LiveEditCompiler(projectRule.project, cache, provider(apk))

    projectRule.modifyKtFile(file, """
      class Test {
        var x = 1
        init { x = 2 }
      }
    """)

    // First edit - diff with APK
    // We don't check init in the first diff.
    compile(file, compiler)

    // Second+ edit - diff with cache
    cache.update(apk.values.toList())
    val secondError = Assert.assertThrows(LiveEditUpdateException::class.java) { compile(file, compiler) }
    assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_CONSTRUCTOR, secondError.error)
    assertEquals("in Test, modified constructor <init>", secondError.message)
  }

  @Test
  fun `Change init overloaded`() {
    val file = projectRule.createKtFile("A.kt", """
      class Test {
       constructor(x:Int, y:Int) { Test(x) }
       constructor(y:Int) { }
      }
    """)

    val cache = MutableIrClassCache()
    val apk = projectRule.directApiCompileIr(file)
    val compiler = LiveEditCompiler(projectRule.project, cache, provider(apk))

    projectRule.modifyKtFile(file, """
      class Test {
        constructor(x:Int, y:Int) { Test(y) }
        constructor(y:Int) { }
      }
    """)

    // First edit - diff with APK
    compile(file, compiler)

    // Second+ edit - diff with cache
    cache.update(apk.values.toList())
    val secondError = Assert.assertThrows(LiveEditUpdateException::class.java) { compile(file, compiler) }
    assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_CONSTRUCTOR, secondError.error)
    assertEquals("in Test, modified constructor void <init>(int, int)", secondError.message)
  }

  private fun provider(apk: Map<String, IrClass>): ApkClassProvider = object: ApkClassProvider {
    override fun getClass(ktFile: KtFile, className: String) = apk[className]
  }
}
