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

import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.run.deployment.liveedit.LiveEditCompiler
import com.android.tools.idea.run.deployment.liveedit.MutableIrClassCache
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
import com.android.tools.idea.run.deployment.liveedit.k2.backendCodeGenForK2
import com.android.tools.idea.run.deployment.liveedit.runWithCompileLock
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.utils.editor.commitToPsi
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.util.KOTLIN_FILE_TYPES
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.psi.KtFile
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

fun diff(old: IrClass, new: IrClass): ClassDiff? {
  return Differ.diff(old, new)
}

fun AndroidProjectRule.Typed<*, Nothing>.createKtFile(name: String, content: String): KtFile {
  val file = fixture.configureByText(name, content)
  assert(file.fileType in KOTLIN_FILE_TYPES)
  return file as KtFile
}

fun AndroidProjectRule.Typed<*, Nothing>.modifyKtFile(file: KtFile, content: String) {
  WriteCommandAction.runWriteCommandAction(project) {
    val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: fail("No document for $file")
    document.replaceString(0, document.textLength, content)
    document.commitToPsi(project)
  }
}

/**
 * Disables Live Edit so that we can edit files without triggering it. Creating a new project rule starts the LiveEditService in the
 * background, so this helps avoid wasted compute/potential unexpected behavior/noisy logs that come from LE detecting file changes.
 */
fun disableLiveEdit() {
  LiveEditApplicationConfiguration.getInstance().mode = LiveEditApplicationConfiguration.LiveEditMode.DISABLED
}

/**
 * Enables Live Edit so that edits to Kotlin files will trigger it
 */
fun enableLiveEdit() {
  LiveEditApplicationConfiguration.getInstance().mode = LiveEditApplicationConfiguration.LiveEditMode.LIVE_EDIT
}

/**
 * Compiles the given files and parses the generated classes into [IrClass] objects. Returns a map of class name to [IrClass]
 */
fun AndroidProjectRule.Typed<*, Nothing>.directApiCompileIr(inputFile: KtFile) = directApiCompileIr(listOf(inputFile))

/**
 * Compiles the given file and parses the generated classes into [IrClass] objects. Returns a map of class name to [IrClass]
 */
fun AndroidProjectRule.Typed<*, Nothing>.directApiCompileIr(inputFiles: List<KtFile>) = directApiCompile(inputFiles).map {
  IrClass(it)
}.associateBy { it.name }

/**
 * Compile the given file without calling into [LiveEditCompiler]. Should only be used to set up for tests.
 */
fun AndroidProjectRule.Typed<*, Nothing>.directApiCompile(inputFile: KtFile) = directApiCompile(listOf(inputFile))

fun AndroidProjectRule.Typed<*, Nothing>.directApiCompileByteArray(inputFiles: List<KtFile>): HashMap<String, ByteArray> {
  val result = HashMap<String, ByteArray>()
  directApiCompile(inputFiles).forEach {
    result[IrClass(it).name] = it
  }
  return result
}

/**
 * Compile the given files without calling into [LiveEditCompiler]. Should only be used to set up for tests.
 */
fun AndroidProjectRule.Typed<*, Nothing>.directApiCompile(inputFiles: List<KtFile>): List<ByteArray> {
  return ApplicationManager.getApplication().runReadAction(Computable<List<ByteArray>> {
    runWithCompileLock {
      val output = mutableListOf<ByteArray>()
      if (KotlinPluginModeProvider.isK2Mode()) {
        inputFiles.forEach { inputFile ->
          val result = backendCodeGenForK2(inputFile, inputFile.module)
          result.output.filter { it.path.endsWith(".class") } .forEach { output.add(it.content) }
        }
      } else {
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
      }
      output
    }
  })
}

fun AndroidProjectRule.Typed<*, Nothing>.initialCache(files: List<KtFile>): MutableIrClassCache {
  val cache = MutableIrClassCache()
  for (file in files) {
    val classes = directApiCompileIr(file)
    classes.values.forEach { cache.update(it) }
  }
  return cache
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