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
import com.android.tools.idea.editors.liveedit.LiveEditAdvancedConfiguration
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.compilationError
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.internalError
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.nonPrivateInlineFunctionFailure
import com.android.tools.idea.run.deployment.liveedit.desugaring.LiveEditDesugar
import com.google.common.collect.HashMultimap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
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
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil.getFileClassInfoNoResolve
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclarationUtil
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.BindingContext
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Optional

class LiveEditCompiler(val project: Project) {

  private val LOGGER = LogWrapper(Logger.getInstance(LiveEditCompiler::class.java))

  // Cache of fully-qualified class name to inlineable bytecode on disk or in memory
  var inlineCandidateCache = SourceInlineCandidateCache()

  private var desugarer = LiveEditDesugar()

  /**
   * Compile a given set of MethodReferences to Java .class files and populates the output list with the compiled code.
   * The compilation is wrapped in a cancelable read action, and will be interrupted by a PSI write action.
   *
   * Returns true if the compilation is successful, and false if the compilation was interrupted and did not complete.
   * If compilation fails due to issues with invalid syntax or other compiler-specific errors, throws a
   * LiveEditException detailing the failure.
   */
  @Trace
  fun compile(inputs: List<LiveEditCompilerInput>, giveWritePriority : Boolean = true) : Optional<LiveEditCompilerOutput> {
    // Bundle changes per-file to prevent wasted recompilation of the same file. The most common
    // scenario is multiple pending changes in the same file, so this is somewhat important.
    val changedFiles = HashMultimap.create<KtFile, LiveEditCompilerInput>()
    for (input in inputs) {
      if (input.file is KtFile) {
        changedFiles.put(input.file, input)
      }
    }

    // Wrap compilation in a read action that can be interrupted by any other read or write action,
    // which prevents the UI from freezing during compilation if the user continues typing.
    val progressManager = ProgressManager.getInstance()

    var outputs : LiveEditCompilerOutput = LiveEditCompilerOutput.Builder().build()
    val compileCmd = {
      var outputBuilder = LiveEditCompilerOutput.Builder()
      for ((file, input) in changedFiles.asMap()) {
        // Compiler pass
        compileKtFile(file, input, outputBuilder)
        outputs = outputBuilder.build()

        dumpOutputs(outputs)

        // Desugaring pass
        if (StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_R8_DESUGAR.get()) {
          desugarer.desugar(outputs)
          dumpOutputs(outputs, "desugared-")
        }
      }
    }

    // In manual mode, we trigger when SaveAll action shortcut is detected. Which means we run concurrently with SaveAllAction.
    // Therefore, we cannot use runInReadActionWithWriteActionPriority (otherwise we would be continuously interrupted because
    // save is happening, upon testing on a small file we were interrupted 1000 times by save writes).
    // Instead, we run with runReadAction.
    //
    // In automatic mode, we want to be interrupted on each keystroke, so we only run when the user is done typing.
    // A keystroke writes the PSI trees so running with runInReadActionWithWriteActionPriority yield exactly the interrupt policy we need.

    var success = true
    if (giveWritePriority) {
      success = progressManager.runInReadActionWithWriteActionPriority(compileCmd, progressManager.progressIndicator)
    } else {
      ApplicationManager.getApplication().runReadAction(compileCmd)
    }

    return if (success) Optional.of(outputs) else Optional.empty()
  }

  private fun dumpOutputs(outputs: LiveEditCompilerOutput, prefix : String = "") {
    if (!LiveEditAdvancedConfiguration.getInstance().useDebugMode) {
      return
    }

    for (clazz in outputs.classes) {
        writeDebugToTmp(prefix + clazz.name.replace("/".toRegex(), ".") + ".class", clazz.data)
    }
  }

  private fun compileKtFile(file: KtFile, inputs: Collection<LiveEditCompilerInput>, output: LiveEditCompilerOutput.Builder) {
    val tracker = PerformanceTracker()
    var inputFiles = listOf(file)

    runWithCompileLock {
      ReadActionPrebuildChecks(file)

      // This is a three-step process:
      // 1) Compute binding context based on any previous cached analysis results.
      //    On small edits of previous analyzed project, this operation should be below 30ms or so.
      ProgressManager.checkCanceled()
      val resolution = tracker.record({ fetchResolution(project, inputFiles) }, "resolution_fetch")

      ProgressManager.checkCanceled()
      val analysisResult = tracker.record({ analyze(inputFiles, resolution) }, "analysis")
      val inlineCandidates = analyzeSingleDepthInlinedFunctions(file, analysisResult.bindingContext, inlineCandidateCache)

      // 2) Invoke the backend with the inputs and the binding context computed from step 1.
      //    This is the one of the most time-consuming step with 80 to 500ms turnaround, depending on
      //    the complexity of the input .kt file.
      ProgressManager.checkCanceled()
      var generationState : GenerationState? = null
      try {
        generationState = tracker.record(
          {
            backendCodeGen(project,
                           analysisResult,
                           inputFiles,
                           inputFiles.first().module!!,
                           inlineCandidates)
          },
          "codegen")
      } catch (e : LiveEditUpdateException) {
        if (e.error != LiveEditUpdateException.Error.UNABLE_TO_INLINE) {
          throw e
        }

        // 2.1) Add any extra source file this compilation need in order to support the input file calling an inline function
        //      from another source file then perform a compilation again.
        if (LiveEditAdvancedConfiguration.getInstance().useInlineAnalysis) {
          inputFiles = performInlineSourceDependencyAnalysis(resolution, file, analysisResult.bindingContext)

          // We need to perform the analysis once more with the new set of input files.
          val newAnalysisResult = resolution.analyzeWithAllCompilerChecks(inputFiles)

          // We will need to start using the new analysis for code gen.
          generationState = tracker.record(
            {
              backendCodeGen(project,
                             newAnalysisResult,
                             inputFiles,
                             inputFiles.first().module!!,
                             inlineCandidates)
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
      return@runWithCompileLock getGeneratedCode(inputs, generationState!!, output)
    }
  }

  /**
   * Pick out what classes we need from the generated list of .class files.
   */
  private fun getGeneratedCode(inputs: Collection<LiveEditCompilerInput>, generationState: GenerationState,
                               output: LiveEditCompilerOutput.Builder) {
    val compilerOutput = generationState.factory.asList()
    val bindingContext = generationState.bindingContext

    if (compilerOutput.isEmpty()) {
      throw internalError("No compiler output.")
    }

    for (input in inputs) {
      // The function we are looking at no longer belongs to file. This is mostly an IDE refactor. Make it a recoverable error
      // to see if the next step of the refactor can fix it. This should be solved nicely with a ClassDiffer.
      input.element.containingFile?: throw compilationError("Invalid AST. Function no longer belong to any files.")

      when(val element = input.element) {
        // When the edit event was contained in a function
        is KtFunction -> {
          // When a Composable is a lambda, we actually need to take into account of all the parent groups of that Composable
          val parentGroup = input.parentGroups.takeIf { element !is KtNamedFunction }
          val group = if (LiveEditAdvancedConfiguration.getInstance().usePartialRecompose)
            getGroupKey(compilerOutput, element, parentGroup) else null
          group?.let { output.addGroupId(group) }
          getGeneratedMethodCode(compilerOutput, element, generationState, output)
        }

        // When the edit event was at class level
        is KtClass -> {
          val desc = bindingContext[BindingContext.CLASS, element]!!
          val internalClassName = getInternalClassName(desc.containingPackage(), element.fqName.toString(), input.file)
          getCompiledClasses(internalClassName, input.file as KtFile, compilerOutput, output)
        }

        // When the edit was at top level
        is KtFile -> {
          val internalClassName = getInternalClassName(element.packageFqName, element.javaFileFacadeFqName.toString(), element)
          getCompiledClasses(internalClassName, element, compilerOutput, output)
        }

        else -> throw compilationError("Event was generated for unsupported kotlin element")
      }
    }
  }

  private fun getGeneratedMethodCode(compilerOutput: List<OutputFile>, targetFunction: KtFunction,
                                     generationState: GenerationState, builder : LiveEditCompilerOutput.Builder) {
    val desc = generationState.bindingContext[BindingContext.FUNCTION, targetFunction]!!
    val isCompose = desc.hasComposableAnnotation()

    var elem: PsiElement = targetFunction
    while (elem.getKotlinFqName() == null || elem !is KtNamedFunction) {
      if (elem.parent == null) {
        // Suppose you are editing:
        // val direct = @Composable{Text(text = "hi")}
        //
        // We would not be able to find a named function with the current implementation. What we need to do is figure out the name
        // of the function in the .class that is changed. This can only be done with something like a class differ.
        throw internalError("Unsupported edit of unnamed function", elem.containingFile);
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

    val internalClassName = getInternalClassName(desc.containingPackage(), className, function.containingFile)
    getCompiledClasses(internalClassName, elem.containingFile as KtFile, compilerOutput, builder)

    if (!isCompose) {
      builder.resetState = true
    }
    checkNonPrivateInline(desc, function.containingFile)
  }

  private inline fun checkNonPrivateInline(desc: SimpleFunctionDescriptor, file: PsiFile) {
    if (desc.isInline && desc.visibility != DescriptorVisibilities.PRIVATE) {
      throw nonPrivateInlineFunctionFailure(file)
    }
  }

  private fun getCompiledClasses(internalClassName: String, input: KtFile, compilerOutput: List<OutputFile>,
                                 liveEditOutput : LiveEditCompilerOutput.Builder) {
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

      // Query kotlin compiler via getFileClassInfoNoResolve to handle file level annotations that changes output filenames.
      // For example: "@file:JvmName("CustomJvmName")" or "@file:JvmMultifileClass"
      val classInfo = getFileClassInfoNoResolve(input)
      if (c.relativePath == "$internalClassName.class" ||
          c.relativePath == "${classInfo.fileClassFqName.toString().replace(".", "/")}.class" ||
          c.relativePath == "${classInfo.facadeClassFqName.toString().replace(".", "/")}.class") {
        var primaryClass = c.asByteArray()
        var name = c.relativePath.substringBefore(".class")
        println("   Primary class: ${c.relativePath}")
        inlineCandidateCache.computeIfAbsent(name) {
          SourceInlineCandidate(input, it)
        }.setByteCode(primaryClass)
        liveEditOutput.addClass(LiveEditCompiledClass(name, primaryClass, input.module, LiveEditClassType.NORMAL_CLASS))
        continue
      }

      // Lambdas and compose classes are proxied in the interpreted on device.
      val reader = ClassReader(c.asByteArray());
      if (isProxiable(reader)) {
        println("   Proxiable class: ${c.relativePath}")
        val name = c.relativePath.substringBefore(".class")
        val supportClass = c.asByteArray()
        inlineCandidateCache.computeIfAbsent(name) {
          SourceInlineCandidate(input, it)
        }.setByteCode(supportClass)
        liveEditOutput.addClass(LiveEditCompiledClass(name, supportClass, input.module, LiveEditClassType.SUPPORT_CLASS))
        continue
      }

      println("   Ignored class: ${c.relativePath}")
      // TODO: New classes (or existing unmodified classes) are not handled here. We should let the user know here.
    }
    println("Lived edit classes summary end")
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
            name != SpecialNames.INIT.asString()) {
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
      throw internalError("Expected package prefix '$packagePrefix' not found in class name '$className'")
    }
    val classSuffix = className.substringAfter(packagePrefix)
    return packagePrefix.replace(".", "/") + classSuffix.replace(".", "$")
  }

  fun resetState() {
    inlineCandidateCache.clear()

    try {
      // Desugarer caches jar indexes and entries. It MUST be closed and recreated.
      desugarer.close()
    } finally {
      desugarer = LiveEditDesugar()
    }
  }

  private fun writeDebugToTmp(name: String, data: ByteArray) {
    val tmpPath = System.getProperty("java.io.tmpdir") ?: return
    val path = Paths.get(tmpPath, name)
    try {
      Files.write(path, data)
      LOGGER.info("Wrote debug file at '%s'", path.toAbsolutePath())
    }
    catch (e: IOException) {
      LOGGER.info("Unable to write debug file '%s'", path.toAbsolutePath())
    }
  }
}
