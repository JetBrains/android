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
package com.android.tools.idea.run.deployment.liveedit

import com.android.annotations.Trace
import com.android.tools.idea.editors.literals.LiveEditService
import com.android.tools.idea.editors.liveedit.LiveEditAdvancedConfiguration
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.internalError
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.nonPrivateInlineFunctionFailure
import com.google.common.collect.HashMultimap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.SimpleFunctionDescriptor
import org.jetbrains.kotlin.descriptors.containingPackage
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclarationUtil
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.BindingContext
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

class AndroidLiveEditCodeGenerator(val project: Project, val inlineCandidateCache: SourceInlineCandidateCache? = null) {
  data class CodeGeneratorInput(val file: PsiFile, var element: KtElement, var parentGroups: List<KtFunction>? = null)

  enum class FunctionType {
    NONE, KOTLIN, COMPOSABLE
  }

  data class CodeGeneratorOutput(val className: String,
                                 val methodName: String,
                                 val methodDesc: String,
                                 val classData: ByteArray,
                                 val functionType: FunctionType,
                                 val hasGroupId: Boolean,
                                 val groupId: Int,
                                 val supportClasses: Map<String, ByteArray>)

  /**
   * Compile a given set of MethodReferences to Java .class files and populates the output list with the compiled code.
   * The compilation is wrapped in a cancelable read action, and will be interrupted by a PSI write action.
   *
   * Returns true if the compilation is successful, and false if the compilation was interrupted and did not complete.
   * If compilation fails due to issues with invalid syntax or other compiler-specific errors, throws a
   * LiveEditException detailing the failure.
   */
  @Trace
  fun compile(inputs: List<CodeGeneratorInput>, outputs: MutableList<CodeGeneratorOutput>, giveWritePriority : Boolean = true) : Boolean {
    outputs.clear()

    // Bundle changes per-file to prevent wasted recompilation of the same file. The most common
    // scenario is multiple pending changes in the same file, so this is somewhat important.
    val changedFiles = HashMultimap.create<KtFile, CodeGeneratorInput>()
    for (input in inputs) {
      if (input.file is KtFile) {
        changedFiles.put(input.file, input)
      }
    }

    // Wrap compilation in a read action that can be interrupted by any other read or write action,
    // which prevents the UI from freezing during compilation if the user continues typing.
    val progressManager = ProgressManager.getInstance()


    val compileCmd = {
      for ((file, input) in changedFiles.asMap()) {
        outputs.addAll(compileKtFile(file, input))
      }
    }

    // In manual mode, we trigger when SaveAll action shortcut is detected. Which means we run concurrently with SaveAllAction.
    // Therefore we cannot use runInReadActionWithWriteActionPriority (otherwise we would be continuously interrupted because
    // save is happening, upon testing on a small file we were interrupted 1000 times by save writes).
    // Instead we run with runReadAction.
    //
    // In automatic mode, we want to be interrupted on each keystroke so we only run when the user is done typing.
    // A keystroke writes the PSI trees so running with runInReadActionWithWriteActionPriority yield exactly the interrupt policy we need.
    if (giveWritePriority) {
      return progressManager.runInReadActionWithWriteActionPriority(compileCmd, progressManager.progressIndicator)
    } else {
      ApplicationManager.getApplication().runReadAction(compileCmd)
      return true
    }
  }

  private fun compileKtFile(file: KtFile, inputs: Collection<CodeGeneratorInput>) : List<CodeGeneratorOutput> {
    val tracker = PerformanceTracker()
    var inputFiles = listOf(file)

    return runWithCompileLock {
      // This is a three-step process:
      // 1) Compute binding context based on any previous cached analysis results.
      //    On small edits of previous analyzed project, this operation should be below 30ms or so.
      ProgressManager.checkCanceled()
      val resolution = tracker.record({ fetchResolution(project, inputFiles) }, "resolution_fetch")

      ProgressManager.checkCanceled()
      var bindingContext = tracker.record({ analyze(inputFiles, resolution) }, "analysis")
      var inlineCandidates = inlineCandidateCache?.let { analyzeSingleDepthInlinedFunctions(resolution, file, bindingContext, it) }

      // 2) Invoke the backend with the inputs and the binding context computed from step 1.
      //    This is the one of the most time-consuming step with 80 to 500ms turnaround, depending on
      //    the complexity of the input .kt file.
      ProgressManager.checkCanceled()
      var generationState : GenerationState? = null
      try {
        generationState = tracker.record({backendCodeGen(project, resolution, bindingContext, inputFiles,
                                                         inputFiles.first().module!!,
                                                         inlineCandidates,
                                                         AndroidLiveEditLanguageVersionSettings(file.languageVersionSettings))},
                                         "codegen")
      } catch (e : LiveEditUpdateException) {
        if (e.error != LiveEditUpdateException.Error.UNABLE_TO_INLINE) {
          throw e
        }

        // 2.1) Add any extra source file this compilation need in order to support the input file calling an inline function
        //      from another source file then perform a compilation again.
        if (LiveEditAdvancedConfiguration.getInstance().useInlineAnalysis) {
          inputFiles = performInlineSourceDependencyAnalysis(resolution, file, bindingContext)

          // We need to perform the analysis once more with the new set of input files.
          val newAnalysisResult = resolution.analyzeWithAllCompilerChecks(inputFiles)

          // We will need to start using the binding context from the new analysis for code gen.
          bindingContext = newAnalysisResult.bindingContext

          generationState = tracker.record({
                                             backendCodeGen(project, resolution, bindingContext, inputFiles,
                                                            inputFiles.first().module!!, inlineCandidates,
                                                            AndroidLiveEditLanguageVersionSettings(file.languageVersionSettings))
                                           },
                                           "codegen_inline")
        }
        else {
          throw e
        }
      } catch (p : ProcessCanceledException) {
        throw p
      } catch (t : Throwable) {
        throw internalError("Internal Error During Code Gen", t)
      }

      // 3) From the information we gather at the PSI changes and the output classes of Step 2, we
      //    decide which classes we want to send to the device along with what extra meta-information the
      //    agent need.
      return@runWithCompileLock inputs.map { getGeneratedCode(it, generationState!!)}
    }
  }

  /**
   * Pick out what classes we need from the generated list of .class files.
   */
  private fun getGeneratedCode(input: CodeGeneratorInput, generationState: GenerationState): CodeGeneratorOutput {
    val compilerOutput = generationState.factory.asList()
    val bindingContext = generationState.bindingContext

    if (compilerOutput.isEmpty()) {
      throw LiveEditUpdateException.internalError("No compiler output.", input.file)
    }

    when(input.element) {
      // When the edit event was contained in a function
      is KtNamedFunction -> {
        val targetFunction = input.element as KtNamedFunction
        var group = if (LiveEditAdvancedConfiguration.getInstance().usePartialRecompose)
          getGroupKey(compilerOutput, targetFunction) else null
        return getGeneratedMethodCode(compilerOutput, targetFunction, group, generationState)
      }

      is KtFunction -> {
        val targetFunction = input.element as KtFunction
        var group = if (LiveEditAdvancedConfiguration.getInstance().usePartialRecompose)
            getGroupKey(compilerOutput, targetFunction, input.parentGroups) else null
        return getGeneratedMethodCode(compilerOutput, targetFunction, group, generationState)
      }

      // When the edit event was at class level
      is KtClass -> {
        val targetClass = input.element as KtClass
        val desc = bindingContext[BindingContext.CLASS, targetClass]!!
        val internalClassName = getInternalClassName(desc.containingPackage(), targetClass.fqName.toString(), input.file)
        val (primaryClass, supportClasses) = getCompiledClasses(internalClassName, input.file as KtFile, compilerOutput)
        return CodeGeneratorOutput(internalClassName, "", "", primaryClass, FunctionType.NONE, false,0, supportClasses)
      }

      // When the edit was at top level
      is KtFile -> {
        val targetFile = input.element as KtFile
        val internalClassName = getInternalClassName(targetFile.packageFqName, targetFile.javaFileFacadeFqName.toString(), input.file)
        val (primaryClass, supportClasses) = getCompiledClasses(internalClassName, input.file as KtFile, compilerOutput)
        return CodeGeneratorOutput(internalClassName, "", "", primaryClass, FunctionType.NONE, false,0, supportClasses)
      }
    }

    throw LiveEditUpdateException.compilationError("Event was generated for unsupported kotlin element")
  }

  private fun getGeneratedMethodCode(compilerOutput: List<OutputFile>, targetFunction: KtFunction, groupId: Int?, generationState: GenerationState) : CodeGeneratorOutput {
    val desc = generationState.bindingContext[BindingContext.FUNCTION, targetFunction]!!
    val methodSignature = remapFunctionSignatureIfNeeded(desc, generationState.typeMapper)
    val isCompose = desc.hasComposableAnnotation()

    var elem: PsiElement = targetFunction
    while (elem.getKotlinFqName() == null || elem !is KtNamedFunction) {
      if (elem.parent == null) {
        // Suppose you are editing:
        // val direct = @Composable{Text(text = "hi")}
        //
        // We would not be able to find a named function with the current implementation. What we need to do is figure out the name
        // of the function in the .class that is changed. This can only be done with something like a class differ.
        throw LiveEditUpdateException.internalError("Unsupported edit of unnamed function", elem.containingFile);
      }
      elem = elem.parent
    }

    val function: KtNamedFunction = elem

    // Class name can be either the class containing the function fragment or a KtFile
    var className = KtNamedDeclarationUtil.getParentFqName(function).toString()
    if (function.isTopLevel) {
      val grandParent: KtFile = function.parent as KtFile
      className = grandParent.javaFileFacadeFqName.toString()
    }

    if (className.isEmpty() || methodSignature.isEmpty()) {
      throw LiveEditUpdateException.internalError("Empty class name / method signature.", function.containingFile)
    }

    val internalClassName = getInternalClassName(desc.containingPackage(), className, function.containingFile)
    val (primaryClass, supportClasses) = getCompiledClasses(internalClassName, elem.containingFile as KtFile, compilerOutput)

    val idx = methodSignature.indexOf('(')
    val methodName = methodSignature.substring(0, idx);
    val methodDesc = methodSignature.substring(idx)
    val functionType = if (isCompose) FunctionType.COMPOSABLE else FunctionType.KOTLIN
    val result = CodeGeneratorOutput(
      internalClassName, methodName, methodDesc, primaryClass, functionType, groupId != null, groupId?: 0, supportClasses)
    checkNonPrivateInline(desc, function.containingFile)
    return result;
  }

  private inline fun checkNonPrivateInline(desc: SimpleFunctionDescriptor, file: PsiFile) {
    if (desc.isInline && desc.visibility != DescriptorVisibilities.PRIVATE) {
      throw nonPrivateInlineFunctionFailure(file)
    }
  }

  private fun getCompiledClasses(internalClassName: String, input: KtFile, compilerOutput: List<OutputFile>) : Pair<ByteArray, Map<String, ByteArray>> {
    var primaryClass = ByteArray(0)
    val supportClasses = mutableMapOf<String, ByteArray>()
    // TODO: Remove all these println once we are more stable.
    println("Lived edit classes summary start")
    for (c in compilerOutput) {

      // We get things like folder path an
      if (!c.relativePath.endsWith(".class")) {
        println("   Skipping output: ${c.relativePath}")
        continue
      }

      if (isKeyMetaClass(c)) {
        println("   Skipping MetaKey: ${c.relativePath}")
        continue
      }

      // The class to become interpreted
      if (c.relativePath == "$internalClassName.class") {
        primaryClass = c.asByteArray()
        println("   Primary class: ${c.relativePath}")
        inlineCandidateCache?.let { cache ->
          cache.computeIfAbsent(internalClassName) {
          SourceInlineCandidate(input, it, input.module!!)
        }.setByteCode(primaryClass)}
        continue
      }

      // Lambdas and compose classes are proxied in the interpreted on device.
      val reader = ClassReader(c.asByteArray());
      if (isProxiable(reader)) {
        println("   Proxiable class: ${c.relativePath}")
        val name = c.relativePath.substringBefore(".class")
        supportClasses[name] = c.asByteArray()
        inlineCandidateCache?.let { cache ->
          cache.computeIfAbsent(name) {
          SourceInlineCandidate(input, it, input.module!!)
        }.setByteCode(supportClasses[name]!!)}
        continue
      }

      println("   Ignored class: ${c.relativePath}")
      // TODO: New classes (or existing unmodified classes) are not handled here. We should let the user know here.
    }
    println("Lived edit classes summary end")
    return Pair(primaryClass, supportClasses)
  }

  private fun isProxiable(clazzFile: ClassReader): Boolean {
    if (clazzFile.superName == "kotlin/jvm/internal/Lambda" ||
        clazzFile.superName == "kotlin/coroutines/jvm/internal/SuspendLambda" ||
        clazzFile.superName == "kotlin/coroutines/jvm/internal/RestrictedSuspendLambda" ||
        clazzFile.className.contains("ComposableSingletons\$")) {
      return true
    }

    // Checking for SAM (single abstract method) interfaces; these aren't specifically tagged in bytecode, so we need a heuristic.
    // All the following should be true:
    //   - inner classes (classes with '$' in the name)
    //   - that implement a single interface
    //   - that implement exactly one public method
    if (!clazzFile.className.contains('$') || clazzFile.interfaces.size != 1) {
      return false
    }

    var publicMethodCount = 0
    clazzFile.accept(object : ClassVisitor(Opcodes.ASM5) {
      override fun visitMethod(access: Int,
                               name: String?,
                               descriptor: String?,
                               signature: String?,
                               exceptions: Array<out String>?): MethodVisitor? {
        if (access and Opcodes.ACC_PUBLIC != 0 &&
            access and Opcodes.ACC_STATIC == 0 &&
            !name.equals("<init>")) {
          publicMethodCount++
        }

        // visitMethod return
        return null
      }
    }, 0)

    return publicMethodCount == 1
  }

  // The PSI returns the class name in the same format it would be used in an import statement: com.package.Class.InnerClass; however,
  // java's internal name format requires the same class name to be formatted as com/package/Class$InnerClass. This method takes a package
  // and class name in "import" format and returns the same class name in "internal" format.
  private fun getInternalClassName(packageName : FqName?, className : String, file: PsiFile) : String {
    var packagePrefix = ""
    if (packageName != null && !packageName.isRoot) {
      packagePrefix = "$packageName."
    }
    if (!className.contains(packagePrefix)) {
      throw LiveEditUpdateException.internalError("Expected package prefix '$packagePrefix' not found in class name '$className'")
    }
    val classSuffix = className.substringAfter(packagePrefix)
    return packagePrefix.replace(".", "/") + classSuffix.replace(".", "$")
  }
}