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

import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.nonPrivateInlineFunctionFailure
import com.android.tools.idea.run.deployment.liveedit.analysis.ComposeGroupTree
import com.android.tools.idea.run.deployment.liveedit.analysis.FunctionKeyMeta
import com.android.tools.idea.run.deployment.liveedit.analysis.PsiRange
import com.android.tools.idea.run.deployment.liveedit.analysis.RegularClassVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.SyntheticClassVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.Differ
import com.android.tools.idea.run.deployment.liveedit.analysis.isInline
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrAccessFlag
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrMethod
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.idea.base.psi.getLineEndOffset
import org.jetbrains.kotlin.idea.base.psi.getLineStartOffset
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtFile
import java.util.concurrent.TimeUnit

private val logger = LogWrapper(Logger.getInstance(LiveEditOutputBuilder::class.java))

// PLEASE someone help me name this better
internal class LiveEditOutputBuilder(private val apkClassProvider: ApkClassProvider) {
  internal fun getGeneratedCode(sourceFile: KtFile,
                                compiledFiles: List<OutputFile>,
                                irCache: IrClassCache,
                                inlineCandidateCache: SourceInlineCandidateCache,
                                outputs: LiveEditCompilerOutput.Builder) {
    val startTimeNs = System.nanoTime()
    val outputFiles = compiledFiles.filter { it.relativePath.endsWith(".class") }

    if (outputFiles.isEmpty()) {
      throw LiveEditUpdateException.internalError("No compiler output.")
    }

    val keyMetaFiles = outputFiles.filter(::isKeyMeta)
    if (keyMetaFiles.size > 1) {
      throw IllegalStateException("Multiple KeyMeta files Found: $keyMetaFiles")
    }
    val kotlinOnly = keyMetaFiles.isEmpty()

    val keyMetaClass = if (kotlinOnly) { null } else {IrClass(keyMetaFiles.single().asByteArray())}
    val groupTree = keyMetaClass?.let { FunctionKeyMeta.parseTreeFrom(it) }

    for (classFile in outputFiles) {
      if (isKeyMeta(classFile)) {
        continue
      }
      val newClass = handleClassFile(classFile, sourceFile, groupTree, irCache, inlineCandidateCache, outputs)
      outputs.addIrClass(newClass)
    }

    val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNs)
    println("classfile analysis ran in ${durationMs}ms")
  }

  private fun handleClassFile(classFile: OutputFile,
                              sourceFile: KtFile,
                              groupTree: ComposeGroupTree?,
                              irCache: IrClassCache,
                              inlineCandidateCache: SourceInlineCandidateCache,
                              output: LiveEditCompilerOutput.Builder): IrClass {
    val classBytes = classFile.asByteArray()
    val newClass = IrClass(classBytes)
    val oldClass = irCache[newClass.name] ?: run {
      logger.info("Live Edit: No cache entry for ${newClass.name}; using the APK for class diff")
      apkClassProvider.getClass(sourceFile, newClass.name)
    }

    val isFirstDiff = newClass.name !in irCache
    val classType = if (isSyntheticClass(newClass)) LiveEditClassType.SUPPORT_CLASS else LiveEditClassType.NORMAL_CLASS

    // Live Edit supports adding new synthetic classes in order to handle the lambda classes that Compose generates
    if (oldClass == null) {
      if (classType != LiveEditClassType.SUPPORT_CLASS) {
        throw LiveEditUpdateException(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_UNRECOVERABLE,
                                      "added new class ${newClass.name} in ${newClass.sourceFile}", sourceFile, null)
      }

      inlineCandidateCache.computeIfAbsent(newClass.name) { SourceInlineCandidate(sourceFile, it) }.setByteCode(classBytes)
      output.addClass(LiveEditCompiledClass(newClass.name, classBytes, sourceFile.module, LiveEditClassType.SUPPORT_CLASS))

      if (groupTree == null) {
        output.resetState = true
      } else {
        val groupIds = computeGroupIds(newClass.methods, sourceFile, groupTree)
        groupIds.forEach { output.addGroupId(it) }
      }
      return newClass
    }

    val diff = Differ.diff(oldClass, newClass)

    // If we have a class diff, or it is the first time we've diffed this class, send it to the device. The first time we see a class, we
    // need to send it to the device in case it or one of its dependencies has a different name than the version in the APK.
    if (diff != null || isFirstDiff) {
      // Update the inline cache for all classes, both normal and support.
      inlineCandidateCache.computeIfAbsent(newClass.name) { SourceInlineCandidate(sourceFile, it) }.setByteCode(classBytes)
      output.addClass(LiveEditCompiledClass(newClass.name, classBytes, sourceFile.module, classType))
    }

    // If we have no diff, we don't need to check for incompatible changes or resolve group IDs.
    if (diff == null) {
      return newClass
    }

    // Run validation on the class and get a list of method diffs containing all modified methods
    val modifiedMethods = if (classType == LiveEditClassType.SUPPORT_CLASS) {
      val validator = SyntheticClassVisitor(newClass.name)
      diff.accept(validator)
      validator.modifiedMethods
    } else {
      val validator = RegularClassVisitor(newClass.name, logger)
      diff.accept(validator)
      validator.modifiedMethods
    }

    val modifiedIrMethods = mutableListOf<IrMethod>()
    for (methodDiff in modifiedMethods) {
      // Map each method diff to the IR
      val irMethod = newClass.methods.single { it.name == methodDiff.name && it.desc == methodDiff.desc }
      if (isNonPrivateInline(irMethod)) {
        throw nonPrivateInlineFunctionFailure(sourceFile)
      }
      if (classType == LiveEditClassType.NORMAL_CLASS) {
        checkForInit(newClass, irMethod, !isFirstDiff)
      }
      modifiedIrMethods.add(irMethod)
    }

    // Skip group ID resolution if the user's change modified any non-composable methods. Synthetic classes may not have @Composable
    // annotations, so still perform group ID resolution in that case.
    if (classType == LiveEditClassType.NORMAL_CLASS && modifiedIrMethods.any { !isComposableMethod(it) }) {
      output.resetState = true
    } else {
      if (groupTree == null) {
        output.resetState = true
      } else {
        val groupIds = computeGroupIds(modifiedIrMethods, sourceFile, groupTree)
        groupIds.forEach { output.addGroupId(it) }
      }
    }

    return newClass
  }

  private fun computeGroupIds(modifiedIrMethods: List<IrMethod>,
                              sourceFile: KtFile,
                              groupTree: ComposeGroupTree): Set<Int> {
    val groupIds = mutableSetOf<Int>()
    for (method in modifiedIrMethods) {
      // If the method doesn't correspond to any lines in the source file, it can't have an associated FunctionKeyMeta.
      if (method.instructions.lines.isEmpty()) {
        continue
      }

      val methodOffsets = getMethodOffsets(method, sourceFile)
      groupIds.addAll(groupTree.getGroupIds(methodOffsets))
    }
    return groupIds
  }
}

private fun getMethodOffsets(method: IrMethod, sourceFile: KtFile): PsiRange {
  val startLine = method.instructions.lines.first()
  val endLine = method.instructions.lines.last()
  val startOffset = sourceFile.getLineStartOffset(startLine - 1, false) ?: throw IllegalStateException()
  val endOffset = sourceFile.getLineEndOffset(endLine - 1) ?: throw IllegalStateException()
  return PsiRange(startOffset, endOffset)
}

/**
 * Check if the class is a synthetic class; that is, a class generated by either the Kotlin or Compose compiler. Live Edit treats these
 * classes differently, primarily to handle generated lambda classes and SAM interface implementations.
 *
 * Unfortunately, not all generated classes receive the synthetic access flag, so checking for extension of internal Kotlin types is used
 * as a sufficient heuristic.
 *
 * TODO: Many places in LE code refer to these as "support" classes; we should probably switch that to "synthetic".
 */
private fun isSyntheticClass(clazz: IrClass): Boolean {
  if (clazz.superName == "kotlin/jvm/internal/Lambda" ||
      clazz.superName == "kotlin/coroutines/jvm/internal/SuspendLambda" ||
      clazz.superName == "kotlin/coroutines/jvm/internal/RestrictedSuspendLambda" ||
      clazz.name.contains("ComposableSingletons\$")) {
    return true
  }

  // Checking for SAM (single abstract method) interfaces; these aren't specifically tagged in bytecode, so we need a heuristic.
  // All the following should be true:
  //   - inner classes (class is contained in a class or method)
  //   - that implement a single interface
  //   - that implement exactly one public method
  return clazz.enclosingMethod != null && clazz.interfaces.size == 1 && clazz.methods.singleOrNull(::isPublicSAMMethod) != null
}


// First diff is against the APK, so the diff for the constructors and static initializers is likely to be noisy. We skip this check
// for that particular case, and rely on the PSI validation that happened before.
private fun checkForInit(irClass: IrClass, irMethod: IrMethod, throwOnFail: Boolean) {
  if (irMethod.name == "<init>") {
    if (throwOnFail) {
      throw LiveEditUpdateException(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_UNRECOVERABLE,
                                    "in ${irClass.name.replace('/', '.')}, modified constructor ${irMethod.desc}", null, null)
    } else {
      logger.warning("Live Edit detected modified constructor ${irClass.name}${irMethod.desc} in ${irClass.sourceFile}")
    }
  }

  if (irMethod.name == "<clinit>") {
    if (throwOnFail) {
      throw LiveEditUpdateException(LiveEditUpdateException.Error.UNSUPPORTED_SRC_CHANGE_UNRECOVERABLE,
                                    "in ${irClass.name.replace('/', '.')}, modified static initializer", null, null)
    } else {
      logger.warning("Live Edit detected modified static initializer block in class ${irClass.name} of ${irClass.sourceFile}")
    }
  }
}

private fun isKeyMeta(classFile: OutputFile) = classFile.relativePath.endsWith("\$KeyMeta.class")

private fun isPublicSAMMethod(method: IrMethod) = method.access.contains(IrAccessFlag.PUBLIC) &&
                                                  !method.access.contains(IrAccessFlag.STATIC) &&
                                                  method.name != SpecialNames.INIT.asString()

private fun isNonPrivateInline(method: IrMethod) = method.isInline() && !method.access.contains(IrAccessFlag.PRIVATE)

private fun isComposableMethod(method: IrMethod) = method.annotations.any { it.desc == "Landroidx/compose/runtime/Composable;" }