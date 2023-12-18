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

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.descendantsOfType
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.base.plugin.isFe10Plugin
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes


// The PSI returns the class name in the same format it would be used in an import statement: com.package.Class.InnerClass; however,
// java's internal name format requires the same class name to be formatted as com/package/Class$InnerClass. This method takes a package
// and class name in "import" format and returns the same class name in "internal" format.
internal fun getInternalClassName(packageName : FqName?, className : String, file: PsiFile) : String {
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

internal fun getCompiledClasses(internalClassName: String, input: KtFile, compilerOutput: List<OutputFile>,
                               liveEditOutput : LiveEditCompilerOutput.Builder, inlineCandidateCache: SourceInlineCandidateCache) {
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
    val classInfo = JvmFileClassUtil.getFileClassInfoNoResolve(input)
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
  if (isFe10Plugin()) {
    put(JVMConfigurationKeys.DO_NOT_CLEAR_BINDING_CONTEXT, true)
  }

  // We don't support INVOKE_DYNAMIC in the interpreter at the moment.
  put(JVMConfigurationKeys.SAM_CONVERSIONS, JvmClosureGenerationScheme.CLASS)
  put(JVMConfigurationKeys.LAMBDAS, JvmClosureGenerationScheme.CLASS)

  // Link via signatures and not descriptors.
  //
  // This ensures that even if the project has descriptors for basic types from multiple stdlib
  // versions, they all end up mapping to the basic types from the stdlib used for the current
  // compilation.
  //
  // See b/256957527 for details.
  put(JVMConfigurationKeys.LINK_VIA_SIGNATURES, true)
}

internal fun KtFunction.getNamedFunctionParent(): KtNamedFunction {
  // TODO: Double-check whether we can replace this with `getNonStrictParentOfType<KtNamedFunction>()` or not.
  var elem: PsiElement = this
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
  return elem
}