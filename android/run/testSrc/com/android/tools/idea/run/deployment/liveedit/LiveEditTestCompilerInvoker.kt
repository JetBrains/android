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

import com.android.tools.idea.run.deployment.liveedit.tokens.ApplicationLiveEditServices
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

internal fun compile(file: PsiFile, irClassCache: MutableIrClassCache = MutableIrClassCache()): LiveEditCompilerOutput {
  val ktFile = file as KtFile
  val psiState = ReadAction.compute<PsiState, Throwable> { getPsiValidationState(ktFile) }
  return compile(listOf(LiveEditCompilerInput(ktFile, psiState)), irClassCache)
}

internal fun compile(inputs: List<LiveEditCompilerInput>, irClassCache: IrClassCache = MutableIrClassCache()): LiveEditCompilerOutput {
  val project = inputs.first().file.project
  val compiler =
    LiveEditCompiler(project, irClassCache).also { it.setApplicationLiveEditServicesForTests(ApplicationLiveEditServices.Legacy(project)) }
  return compile(inputs, compiler)
}

internal fun compile(input: KtFile, compiler: LiveEditCompiler): LiveEditCompilerOutput {
  val psiState = ReadAction.compute<PsiState, Throwable> { getPsiValidationState(input) }
  return compile(listOf(LiveEditCompilerInput(input, psiState)), compiler)
}

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

internal inline fun <reified T : PsiElement> findFirst(file: PsiFile?, crossinline match: (T) -> Boolean): T {
  return runReadAction {
    file!!.collectDescendantsOfType<T>().first { match(it) }
  }
}