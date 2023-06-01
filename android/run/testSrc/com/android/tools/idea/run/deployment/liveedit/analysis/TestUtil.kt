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
package com.android.tools.idea.run.deployment.liveedit.analysis

import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.ClassDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.ClassVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.Differ
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.FieldDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.FieldVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.LocalVariableDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.LocalVariableVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.MethodDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.MethodVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrField
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrLocalVariable
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrMethod
import com.android.tools.idea.run.deployment.liveedit.compile
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.psi.KtFile
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

fun diff(old: ByteArray, new: ByteArray) = Differ.diff(old, new)

// We need to add a method to guarantee to have something to hook group detection logic onto, at least for now.
const val TEST_COMPILE_METHOD = "f___"

fun AndroidProjectRule.Typed<CodeInsightTestFixture, Nothing>.compile(text: String, fileName: String): ByteArray {
  val classText = "${text.trimIndent().removeSuffix("}")}\tfun $TEST_COMPILE_METHOD() = 0\n}"
  val originalFile = fixture.configureByText(fileName, classText) as KtFile
  return compile(originalFile, "f___").classes[0].data
}

/**
 * Asserts that:
 *  - the diff of [original] and [original] is null
 *  - the diff of [new] and [new] is null
 *  - the diff of [original] and [new] is null
 *  - the diff of [new] and [original] is null
 */
fun assertNoChanges(original: ByteArray, new: ByteArray) {
  assertNull(diff(original, original))
  assertNull(diff(new, new))
  assertNull(diff(original, new))
  assertNull(diff(new, original))
}

/**
 * Asserts that:
 *  - the diff of [original] and [original] is null
 *  - the diff of [new] and [new] is null
 *  - the diff of [original] and [new] has changes
 *  - the diff of [new] and [original] has changes
 */
fun assertChanges(original: ByteArray, new: ByteArray) {
  assertNull(diff(original, original))
  assertNull(diff(new, new))
  assertNotNull(diff(original, new))
  assertNotNull(diff(new, original))
}

fun assertFields(diff: ClassDiff, visitors: Map<String, FieldVisitor>) {
  val fields = visitors.keys.toMutableSet()
  diff.accept(object : ClassVisitor {
    override fun visitFields(added: List<IrField>, removed: List<IrField>, modified: List<FieldDiff>) {
      for (field in modified) {
        visitors[field.name]?.let { field.accept(it) }
        fields.remove(field.name)
      }
    }
  })
  assertTrue(fields.isEmpty())
}

fun assertMethods(diff: ClassDiff, visitors: Map<String, MethodVisitor>) {
  val methods = visitors.keys.toMutableSet()
  diff.accept(object : ClassVisitor {
    override fun visitMethods(added: List<IrMethod>, removed: List<IrMethod>, modified: List<MethodDiff>) {
      for (method in modified) {
        val key = method.name + method.desc
        visitors[key]?.let { method.accept(it) }
        methods.remove(key)
      }
    }
  })
  assertTrue(methods.isEmpty())
}

fun assertLocalVars(diff: ClassDiff, methodName: String, visitors: Map<Int, LocalVariableVisitor>) {
  val localVars = visitors.keys.toMutableSet()
  val localVisitor = object : MethodVisitor {
    override fun visitLocalVariables(added: List<IrLocalVariable>, removed: List<IrLocalVariable>, modified: List<LocalVariableDiff>) {
      for (localVar in modified) {
        val key = localVar.index
        visitors[key]?.let { localVar.accept(it) }
        localVars.remove(key)
      }
    }
  }
  val methodVisitor = object : ClassVisitor {
    override fun visitMethods(added: List<IrMethod>, removed: List<IrMethod>, modified: List<MethodDiff>) {
      val method = modified.first { it.name == methodName }
      assertNotNull(method)
      method.accept(localVisitor)
    }
  }
  diff.accept(methodVisitor)
  assertTrue(localVars.isEmpty())
}