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
package com.android.tools.idea.run

import com.android.tools.idea.editors.literals.LiveEditService
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import junit.framework.Assert
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch

@RunWith(JUnit4::class)
class BasicCompileTest {
  private lateinit var myProject: Project
  private lateinit var ktFileA: PsiFile

  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    myProject = projectRule.project
    ktFileA = projectRule.fixture.configureByText("A.kt", "fun foo() : String { return \"I am foo\"}")
  }

  @Test
  fun simpleChange() {
    var output = compile(ktFileA, findFunction(ktFileA, "foo"))
    var returnedValue = invokeStatic("foo", loadClass("AKt", output))
    Assert.assertEquals("I am foo", returnedValue)
  }

  private fun compile(file: PsiFile, function: KtNamedFunction) : ByteArray {
    val done = CountDownLatch(1)
    var output = ByteArray(0)
    AndroidLiveEditCodeGenerator().compile(myProject, listOf(
      LiveEditService.MethodReference(file, function))) {
      _: String, _: String, bytes: ByteArray ->
      output = bytes
      done.countDown()
    }
    done.await()
    return output
  }

  /**
   * Look for the first named function with a given name.
   */
  private fun findFunction(file: PsiFile, name: String): KtNamedFunction {
    return runReadAction {
      file.collectDescendantsOfType<KtNamedFunction>().first { it.name?.contains(name) ?: false }
    }
  }

  /**
   * Loads a class with given bytecode in an isolated classloader.
   */
  private fun loadClass(name: String, bytecode: ByteArray) : Class<*> {
    // We use a temp classloader so we can have the same class name across different classes without conflict.
    val tempLoader = object : ClassLoader() {
      override fun findClass(name: String): Class<*>? {
        return defineClass(name, bytecode, 0, bytecode.size)
      }
    }
    return tempLoader.loadClass(name)
  }

  /**
   * Invoke a given function of a given class and return the return value.
   */
  private fun invokeStatic(name: String, clazz: Class<*>) : Any {
    return clazz.getMethod(name).invoke(null)
  }
}