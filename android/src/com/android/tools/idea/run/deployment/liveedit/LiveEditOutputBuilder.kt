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
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.nonPrivateInlineFunctionFailure
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationAddedUserClass
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationClinit
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationConstructor
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.unsupportedSourceModificationWhenEnumPath
import com.android.tools.idea.run.deployment.liveedit.analysis.RegularClassVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.SyntheticClassVisitor
import com.android.tools.idea.run.deployment.liveedit.analysis.diffing.Differ
import com.android.tools.idea.run.deployment.liveedit.analysis.isInline
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrAccessFlag
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrMethod
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.codegen.`when`.WhenByEnumsMapping.MAPPINGS_CLASS_NAME_POSTFIX
import org.jetbrains.kotlin.codegen.`when`.WhenByEnumsMapping.MAPPING_ARRAY_FIELD_PREFIX
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtFile
import java.util.concurrent.TimeUnit

private val logger = LogWrapper(Logger.getInstance(LiveEditOutputBuilder::class.java))
private val debug = LiveEditLogger("LiveEditOutputBuilder")

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
      throw LiveEditUpdateException.internalErrorNoCompilerOutput(sourceFile)
    }

    val keyMetaFiles = outputFiles.filter(::isKeyMeta)
    if (keyMetaFiles.size > 1) {
      throw IllegalStateException("Multiple KeyMeta files Found: $keyMetaFiles")
    }

    val keyMetaClass = keyMetaFiles.singleOrNull()?.let{ IrClass(it.asByteArray()) }
    val groups = if (keyMetaClass != null) { extractComposeGroups(sourceFile, keyMetaClass) } else { emptyList() }

    for (classFile in outputFiles) {
      if (isKeyMeta(classFile)) {
        continue
      }
      val newClass = handleClassFile(classFile, sourceFile, groups, irCache, inlineCandidateCache, outputs)
      outputs.addIrClass(newClass)
    }

    val durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNs)
    debug.log("Class file analysis ran in ${durationMs}ms")
  }

  private fun handleClassFile(classFile: OutputFile,
                              sourceFile: KtFile,
                              groups: List<ComposeGroup>,
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
        throw unsupportedSourceModificationAddedUserClass("added new class ${newClass.name} in ${newClass.sourceFile}", sourceFile)
      }

      inlineCandidateCache.computeIfAbsent(newClass.name) { SourceInlineCandidate(sourceFile, it) }.setByteCode(classBytes)
      output.addClass(LiveEditCompiledClass(newClass.name, classBytes, sourceFile.module, LiveEditClassType.SUPPORT_CLASS))

      if (groups.isEmpty()) {
        output.resetState = true
        return newClass
      }

      selectComposeGroups(sourceFile, groups, newClass.methods).forEach { output.groupIds.add(it.key) }
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

    if (classType == LiveEditClassType.SUPPORT_CLASS && isWhenMapping(newClass)) {
      if (modifiedMethods.any {it.name.equals("<clinit>")}) {
        throw unsupportedSourceModificationWhenEnumPath("Changing `when` on enum code path in ${newClass.sourceFile}", sourceFile)
      }
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

    // Skip group ID resolution if the user's change modified any non-composable methods. Generated classes may not have @Composable
    // annotations, so still perform group ID resolution in that case. Also skip if we don't have any groups.
    val modifiedNonCompose = classType == LiveEditClassType.NORMAL_CLASS && modifiedIrMethods.any { !isComposableMethod(it) }
    if (modifiedNonCompose || groups.isEmpty()) {
      output.resetState = true
      return newClass
    }

    debug.log("select groups for ${newClass.name}")
    selectComposeGroups(sourceFile, groups, modifiedIrMethods).forEach { output.groupIds.add(it.key) }
    return newClass
  }
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
  //   - class is an inner class (class is contained in a class or method)
  //   - class implements a single interface
  //   - class exposes one public method (not including constructors, static initializers, or bridge methods)
  if (clazz.enclosingMethod != null && clazz.interfaces.size == 1) {
    val publicMethods = clazz.methods.filter {
      it.access.contains(IrAccessFlag.PUBLIC) &&
      !it.access.contains(IrAccessFlag.SYNTHETIC) &&
      !it.access.contains(IrAccessFlag.BRIDGE) &&
      !it.access.contains(IrAccessFlag.STATIC) &&
      it.name != SpecialNames.INIT.asString()
    }
    if (publicMethods.size == 1) {
      return true
    }
  }

  // Check for WhenMapping.
  if (isWhenMapping(clazz)) {
    return true
  }

  return false
}

/**
 * Kotlin compiler generates a synthetic mapping class when the code contains a 'when' on an enum type.
 *
 * This class only contain a static int[] that maps the enum's ordinate to an int that represent a control flow branch in the body.
 * Therefore, when the branching is changed, we need to update that mapping. However, since we can't rerun the <clinit> this is
 * impossible for now. We can tree WhenMappings just like any helper class and allow adding of new mappings but as long as existing
 * mapping is changed, we will need to go into unsupported state.
 */
private fun isWhenMapping(clazz: IrClass) : Boolean {
  return clazz.name.endsWith(MAPPINGS_CLASS_NAME_POSTFIX) && clazz.fields.all { it.name.startsWith(MAPPING_ARRAY_FIELD_PREFIX)}
}


// First diff is against the APK, so the diff for the constructors and static initializers is likely to be noisy. We skip this check
// for that particular case, and rely on the PSI validation that happened before.
private fun checkForInit(irClass: IrClass, irMethod: IrMethod, throwOnFail: Boolean) {
  if (irMethod.name == "<init>") {
    if (throwOnFail) {
      throw unsupportedSourceModificationConstructor("in ${irClass.name.replace('/', '.')}, modified constructor ${irMethod.getReadableDesc()}")
    } else {
      logger.warning("Live Edit detected modified constructor ${irClass.name}${irMethod.desc} in ${irClass.sourceFile}")
    }
  }

  if (irMethod.name == "<clinit>") {
    if (throwOnFail) {
      throw unsupportedSourceModificationClinit("in ${irClass.name.replace('/', '.')}, modified static initializer")
    } else {
      logger.warning("Live Edit detected modified static initializer block in class ${irClass.name} of ${irClass.sourceFile}")
    }
  }
}

private fun isKeyMeta(classFile: OutputFile) = classFile.relativePath.endsWith("\$KeyMeta.class")


private fun isNonPrivateInline(method: IrMethod) = method.isInline() && !method.access.contains(IrAccessFlag.PRIVATE)

private fun isComposableMethod(method: IrMethod) = method.annotations.any { it.desc == "Landroidx/compose/runtime/Composable;" }