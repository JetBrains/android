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
package com.android.tools.idea.run.deployment.liveedit

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

internal fun compile(file: PsiFile, irClassCache: MutableIrClassCache = MutableIrClassCache()) : LiveEditCompilerOutput {
  val ktFile = file as KtFile
  return compile(listOf(LiveEditCompilerInput(ktFile, ktFile)), irClassCache)
}

internal fun compile(file: PsiFile?, functionName: String, irClassCache: MutableIrClassCache = MutableIrClassCache()) =
  compile(file!!, findFunction(file, functionName), irClassCache)

internal fun compile(file: PsiFile, function: KtNamedFunction, irClassCache: MutableIrClassCache = MutableIrClassCache()) =
  compile(listOf(LiveEditCompilerInput(file, function)), irClassCache)

internal fun compile(inputs: List<LiveEditCompilerInput>, irClassCache: IrClassCache = MutableIrClassCache()): LiveEditCompilerOutput {
  val compiler = LiveEditCompiler(inputs.first().file.project, irClassCache)
  return compile(inputs, compiler)
}

internal fun compile(input: KtFile, compiler: LiveEditCompiler): LiveEditCompilerOutput = compile(
  listOf(LiveEditCompilerInput(input, input)), compiler)

internal fun compile(inputs: List<LiveEditCompilerInput>, compiler: LiveEditCompiler): LiveEditCompilerOutput {
  // The real Live Edit / Fast Preview has a retry system should the compilation got cancelled.
  // We are going to use a simplified version of that here and continue to try until
  // compilation succeeds.
  var output: LiveEditCompilerOutput? = null
  while (output == null) {
    output = compiler.compile(inputs).get().compilerOutput
  }
  return output
}

/**
 * Look for the first named function with a given name.
 */
internal fun findFunction(file: PsiFile?, name: String): KtNamedFunction {
  return findFirst(file) { it.name?.contains(name) ?: false }
}

internal inline fun <reified T : PsiElement> findFirst(file: PsiFile?, crossinline match: (T) -> Boolean): T {
  return runReadAction {
    file!!.collectDescendantsOfType<T>().first { match(it) }
  }
}