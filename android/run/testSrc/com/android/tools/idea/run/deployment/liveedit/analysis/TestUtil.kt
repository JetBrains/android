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

import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.ClassDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.ClassVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.Differ
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.FieldDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.FieldVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.LocalVariableDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.LocalVariableVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.MethodDiff
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.MethodVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrField
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrLocalVariable
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrMethod
import com.android.tools.idea.run.deployment.liveedit.runWithCompileLock
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.psi.KtFile
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

fun diff(old: IrClass, new: IrClass) = Differ.diff(old, new)

fun AndroidProjectRule.Typed<CodeInsightTestFixture, Nothing>.compileIr(text: String, fileName: String, className: String): IrClass {
  return compileIr(text, fileName).single { it.name == className }
}

fun AndroidProjectRule.Typed<CodeInsightTestFixture, Nothing>.compileIr(text: String, fileName: String): List<IrClass> {
  val originalFile = fixture.configureByText(fileName, text) as KtFile
  val output = mutableListOf<ByteArray>()
  ApplicationManager.getApplication().runReadAction {
    runWithCompileLock {
      val inputFiles = listOf(originalFile)
      val resolution = fetchResolution(project, inputFiles)
      val analysisResult = analyze(inputFiles, resolution)
      val generationState: GenerationState = try {
        backendCodeGen(project,
                       analysisResult,
                       inputFiles,
                       inputFiles.first().module!!,
                       emptySet())
      } catch (t: Throwable) {
        throw LiveEditUpdateException.internalError("Internal Error During Code Gen", t)
      }

      generationState.factory.asList()
        .filter { it.relativePath.endsWith(".class") }
        .map { it.asByteArray() }
        .forEach { output.add(it) }
    }
  }
  return output.map { IrClass(it) }
}

fun AndroidProjectRule.Typed<*, Nothing>.directApiCompile(inputFiles: List<KtFile>): List<ByteArray> {
  return ApplicationManager.getApplication().runReadAction(Computable<List<ByteArray>> {
    runWithCompileLock {
      val output = mutableListOf<ByteArray>()
      val resolution = fetchResolution(project, inputFiles)
      val analysisResult = analyze(inputFiles, resolution)
      val generationState: GenerationState = backendCodeGen(project,
                                                            analysisResult,
                                                            inputFiles,
                                                            inputFiles.first().module!!,
                                                            emptySet())
      generationState.factory.asList()
        .filter { it.relativePath.endsWith(".class") }
        .map { it.asByteArray() }
        .forEach { output.add(it) }
      output
    }
  })
}

/**
 * Asserts that:
 *  - the diff of [original] and [original] is null
 *  - the diff of [new] and [new] is null
 *  - the diff of [original] and [new] is null
 *  - the diff of [new] and [original] is null
 */
fun assertNoChanges(original: IrClass, new: IrClass) {
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
fun assertChanges(original: IrClass, new: IrClass) {
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