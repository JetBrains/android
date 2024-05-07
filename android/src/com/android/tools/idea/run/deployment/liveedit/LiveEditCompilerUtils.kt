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

import com.android.tools.idea.flags.StudioFlags
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

internal fun validatePsiDiff(inputs: Collection<LiveEditCompilerInput>, file: KtFile) {
  val oldState = inputs.first().oldState // All these should be the same; they all refer to the same file. This will get refactored.
  val newState = getPsiValidationState(file)
  val errors = validatePsiChanges(oldState, newState)
  if (errors.isNotEmpty()) {
    throw errors[0]
  }
}

internal fun getCompiledClasses(internalClassName: String, input: KtFile, compilerOutput: List<OutputFile>,
                               liveEditOutput : LiveEditCompilerOutput.Builder, inlineCandidateCache: SourceInlineCandidateCache) {
  val logger = LiveEditLogger("Live Edit")
  logger.log("Lived edit classes summary start")
  for (c in compilerOutput) {

    // We get things like folder path an
    if (!c.relativePath.endsWith(".class")) {
      logger.log("   Skipping output: ${c.relativePath}")
      continue
    }

    if (isKeyMetaClass(c)) {
      logger.log("   Skipping MetaKey: ${c.relativePath}")
      continue
    }

    // Query kotlin compiler via getFileClassInfoNoResolve to handle file level annotations that changes output filenames.
    // For example: "@file:JvmName("CustomJvmName")" or "@file:JvmMultifileClass"
    val classInfo = JvmFileClassUtil.getFileClassInfoNoResolve(input)
    if (c.relativePath == "$internalClassName.class" ||
        c.relativePath == "${classInfo.fileClassFqName.toString().replace(".", "/")}.class" ||
        c.relativePath == "${classInfo.facadeClassFqName.toString().replace(".", "/")}.class") {
      var primaryClass = c.asByteArray()
      var name = c.relativePath.substringBefore(".class")
      logger.log("   Primary class: ${c.relativePath}")
      inlineCandidateCache.computeIfAbsent(name) {
        SourceInlineCandidate(input, it)
      }.setByteCode(primaryClass)
      liveEditOutput.addClass(LiveEditCompiledClass(name, primaryClass, input.module, LiveEditClassType.NORMAL_CLASS))
      continue
    }

    // Lambdas and compose classes are proxied in the interpreted on device.
    val reader = ClassReader(c.asByteArray());
    if (isProxiable(reader)) {
      logger.log("   Proxiable class: ${c.relativePath}")
      val name = c.relativePath.substringBefore(".class")
      val supportClass = c.asByteArray()
      inlineCandidateCache.computeIfAbsent(name) {
        SourceInlineCandidate(input, it)
      }.setByteCode(supportClass)
      liveEditOutput.addClass(LiveEditCompiledClass(name, supportClass, input.module, LiveEditClassType.SUPPORT_CLASS))
      continue
    }

    logger.log("   Ignored class: ${c.relativePath}")
    // TODO: New classes (or existing unmodified classes) are not handled here. We should let the user know here.
  }
  logger.log("Lived edit classes summary end")
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

internal fun List<KtFile>.checkPsiErrorElement() {
  forEach { file ->
    val errorElement = file.descendantsOfType<PsiErrorElement>().firstOrNull()
    errorElement?.let { throw LiveEditUpdateException.compilationError(it.errorDescription, it.containingFile, null) }
  }
}

internal fun CompilerConfiguration.setOptions(languageVersionSettings: LanguageVersionSettings) {
  put(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS, languageVersionSettings)

  // Needed so we can diff changes to method parameters and parameter annotations.
  put(JVMConfigurationKeys.PARAMETERS_METADATA, true)

  // Not 100% sure what causes the issue but not seeing this in the IR backend causes exceptions.
  if (KotlinPluginModeProvider.isK1Mode()) {
    put(JVMConfigurationKeys.DO_NOT_CLEAR_BINDING_CONTEXT, true)
  }

  when(StudioFlags.CLOSURE_SCHEME.get()!!) {
    StudioFlags.ClosureScheme.CLASS -> {
      put(JVMConfigurationKeys.SAM_CONVERSIONS, JvmClosureGenerationScheme.CLASS)
      put(JVMConfigurationKeys.LAMBDAS, JvmClosureGenerationScheme.CLASS)
    }
    StudioFlags.ClosureScheme.INDY -> {
      put(JVMConfigurationKeys.SAM_CONVERSIONS, JvmClosureGenerationScheme.INDY)
      put(JVMConfigurationKeys.LAMBDAS, JvmClosureGenerationScheme.INDY)
    }
  }

  // Link via signatures and not descriptors.
  //
  // This ensures that even if the project has descriptors for basic types from multiple stdlib
  // versions, they all end up mapping to the basic types from the stdlib used for the current
  // compilation.
  //
  // See b/256957527 for details.
  put(JVMConfigurationKeys.LINK_VIA_SIGNATURES, true)
}