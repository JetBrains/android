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
package com.android.tools.idea.run.deployment.liveedit.k2

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.deployment.liveedit.IrClassCache
import com.android.tools.idea.run.deployment.liveedit.LiveEditCompilerInput
import com.android.tools.idea.run.deployment.liveedit.LiveEditCompilerOutput
import com.android.tools.idea.run.deployment.liveedit.LiveEditOutputBuilder
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.compilationError
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.nonKotlin
import com.android.tools.idea.run.deployment.liveedit.PsiValidator
import com.android.tools.idea.run.deployment.liveedit.ReadActionPrebuildChecks
import com.android.tools.idea.run.deployment.liveedit.SourceInlineCandidateCache
import com.android.tools.idea.run.deployment.liveedit.checkPsiErrorElement
import com.android.tools.idea.run.deployment.liveedit.getCompiledClasses
import com.android.tools.idea.run.deployment.liveedit.getGroupKey
import com.android.tools.idea.run.deployment.liveedit.getInternalClassName
import com.android.tools.idea.run.deployment.liveedit.getNamedFunctionParent
import com.android.tools.idea.run.deployment.liveedit.runWithCompileLock
import com.android.tools.idea.run.deployment.liveedit.setOptions
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.hasAnnotation
import org.jetbrains.kotlin.analysis.api.components.KtCompilationResult
import org.jetbrains.kotlin.analysis.api.components.KtCompilerTarget
import org.jetbrains.kotlin.analysis.api.diagnostics.getDefaultMessageWithFactoryName
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.getSymbolOfType
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclarationUtil
import org.jetbrains.kotlin.psi.KtNamedFunction

private val ComposableFqName = ClassId(FqName("androidx.compose.runtime"), FqName("Composable"), false)

/**
 * A class to help [LiveEditCompilerForK2.getGeneratedCode] function to collect internal class names and seal classes.
 */
private class InternalClassNamesToSealedClasses {
  val selectedClasses = mutableMapOf<String, PsiFile>()

  /**
   * This function throws an exception if the given [internalClassName] is mapped to a [PsiFile] other than [containingFile].
   */
  fun putWithDuplicationCheck(internalClassName: String, containingFile: PsiFile) {
    if (internalClassName !in selectedClasses) {
      selectedClasses[internalClassName] = containingFile
    }
    else if (selectedClasses[internalClassName] !== containingFile) {
      throw compilationError("Multiple KtFiles for class $internalClassName")
    }
  }

  fun getCompiledClasses(compilerOutput: List<OutputFile>,
                         inlineCandidateCache: SourceInlineCandidateCache,
                         output: LiveEditCompilerOutput.Builder) {
    for ((internalClassName, inputFile) in selectedClasses) {
      if (inputFile is KtFile) {
        getCompiledClasses(internalClassName, inputFile, compilerOutput, output, inlineCandidateCache)
      } else {
        throw nonKotlin(inputFile)
      }
    }
  }
}

internal class LiveEditCompilerForK2(
  private val inlineCandidateCache: SourceInlineCandidateCache,
  private val irClassCache: IrClassCache,
  private val psiValidator: PsiValidator?,
  private val outputBuilder: LiveEditOutputBuilder,
  private val module: Module? = null,
) {
  fun compile(file: KtFile, inputs: Collection<LiveEditCompilerInput>, output: LiveEditCompilerOutput.Builder) {
    runWithCompileLock {
      ReadActionPrebuildChecks(file)
      val result = backendCodeGenForK2(file, module)
      val compilerOutput = result.output.map { OutputFileForKtCompiledFile(it) }

      if (StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_CLASS_DIFFER.get()) {
        // Run this validation *after* compilation so that PSI validation doesn't run until the class is in a state that compiles. This
        // allows the user time to undo incompatible changes without triggering an error, similar to how differ validation works.
        validatePsi(file)
        outputBuilder.getGeneratedCode(file, compilerOutput, irClassCache, inlineCandidateCache, output)
        return@runWithCompileLock
      }

      getGeneratedCode(inputs, compilerOutput, output)
    }
  }

  private fun validatePsi(file: KtFile) {
    val errors = psiValidator?.validatePsiChanges(file)
    if (!errors.isNullOrEmpty()) {
      throw errors[0]
    }
  }

  private fun getGeneratedCode(inputs: Collection<LiveEditCompilerInput>,
                               compilerOutput: List<OutputFile>,
                               output: LiveEditCompilerOutput.Builder) {
    if (compilerOutput.isEmpty()) {
      throw LiveEditUpdateException.internalError("No compiler output.")
    }

    val selectedClasses = InternalClassNamesToSealedClasses()
    for (input in inputs) {
      val element = input.element
      // The function we are looking at no longer belongs to file. This is mostly an IDE refactor / copy-and-paste action.
      // This should be solved nicely with a ClassDiffer.
      if (element?.containingFile == null) {
        continue
      }

      val (internalClassName, containingFile) = analyze(element) {
        when(element) {
          // When the edit event was contained in a function
          is KtFunction -> getClassForKtFunction(element, input, compilerOutput, output)
          // When the edit event was at class level
          is KtClass -> getInternalClassName(element.getClassOrObjectSymbol()?.classIdIfNonLocal?.packageFqName, element.fqName.toString(),
                                             input.file) to input.file
          // When the edit was at top level
          is KtFile -> getInternalClassName(element.packageFqName, element.javaFileFacadeFqName.toString(), element) to element
          else -> throw compilationError("Event was generated for unsupported kotlin element")
        }
      }

      selectedClasses.putWithDuplicationCheck(internalClassName, containingFile)
    }

    selectedClasses.getCompiledClasses(compilerOutput, inlineCandidateCache, output)
  }

  private fun KtAnalysisSession.getClassForKtFunction(function: KtFunction,
                                                      input: LiveEditCompilerInput,
                                                      compilerOutput: List<OutputFile>,
                                                      output: LiveEditCompilerOutput.Builder): Pair<String, KtFile> {
    val symbol = function.getSymbolOfType<KtFunctionSymbol>()
    if (symbol.hasAnnotation(ComposableFqName)) {
      // When a Composable is a lambda, we actually need to take into account of all the parent groups of that Composable
      val parentGroup = input.parentGroups.takeIf { function !is KtNamedFunction }
      val group = getGroupKey(compilerOutput, function, parentGroup)
      group?.let { output.addGroupId(group) }
    }
    else {
      output.resetState = true
    }
    return getClassForMethod(function, symbol)
  }

  private fun KtAnalysisSession.getClassForMethod(targetFunction: KtFunction, symbol: KtFunctionSymbol): Pair<String, KtFile> {
    val function: KtNamedFunction = targetFunction.getNamedFunctionParent()

    // Class name can be either the class containing the function fragment or a KtFile
    var className = KtNamedDeclarationUtil.getParentFqName(function).toString()
    if (function.isTopLevel) {
      val grandParent: KtFile = function.parent as KtFile
      className = grandParent.javaFileFacadeFqName.toString()
    }

    checkNonPrivateInline(symbol, function.containingFile)

    val internalClassName = getInternalClassName(symbol.callableIdIfNonLocal?.packageName, className, function.containingFile)
    return internalClassName to function.containingKtFile
  }
}

fun backendCodeGenForK2(file: KtFile, module: Module?): KtCompilationResult.Success {
  module?.let {
    if (file.module != it) {
      throw LiveEditUpdateException.internalError("KtFile outside targeted module found in code generation", file)
    }
  }

  // Since K2 compile AA reports syntax error, this may be unnecessary, but it throws an exception early when it has a syntax error.
  // In other words, there is no performance penalty from this early check. Let's keep it because there is no guarantee that
  // K2 compile AA covers all cases.
  listOf(file).checkPsiErrorElement()

  val configuration = CompilerConfiguration().apply {
    setModuleName(file)
    setOptions(file.languageVersionSettings)
  }

  // TODO(316965795): Check the performance and the responsiveness once we complete K2 LE implementation.
  //                  Add/remove ProgressManager.checkCanceled() based on the performance and the responsiveness.
  ProgressManager.checkCanceled()

  analyze(file) {
    val result = this@analyze.compile(file, configuration, KtCompilerTarget.Jvm(ClassBuilderFactories.BINARIES)) { true }
    when (result) {
      is KtCompilationResult.Success -> return result
      is KtCompilationResult.Failure -> throw compilationError(result.errors.joinToString { it.getDefaultMessageWithFactoryName() })
    }
  }
}

private fun CompilerConfiguration.setModuleName(file: KtFile) {
  val containingModule = file.module
  if (containingModule != null) {
    put(CommonConfigurationKeys.MODULE_NAME, containingModule.name)
  }
}

private fun KtAnalysisSession.checkNonPrivateInline(symbol: KtFunctionSymbol, file: PsiFile) {
  if (symbol.isInline && symbol.visibility != DescriptorVisibilities.PRIVATE) {
    throw LiveEditUpdateException.nonPrivateInlineFunctionFailure(file)
  }
}