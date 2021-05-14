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
package com.android.tools.idea.editors.literals

import com.android.tools.idea.editors.literals.internal.LiveLiteralsFinder
import com.android.tools.idea.editors.literals.internal.MethodData
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.org.objectweb.asm.ClassReader

/**
 * Class that manages the Live Literals declared by the compiler in a class file.
 *
 * By default, the [LiteralsManager] will list all the literals in a given source file. However, the compiler might not handle
 * all literals as "Live". This class allows reading that information from the compiler so it can be used in different places
 * of the editor.
 */
object CompilerLiveLiteralsManager {
  /**
   * Result of a [findAsync] call that allows to find which literals were defined by the compiler.
   */
  interface Finder {
    /**
     * Returns true if a Live Literal constant is defined for the given [path] at the given [offset].
     */
    fun hasCompilerLiveLiteral(path: String, offset: Int): Boolean
  }

  /**
   * Metadata of a Live Literal defined by the compiler.
   */
  data class CompilerLiteralDefinition(val path: String, val offset: Int)

  private val log = Logger.getInstance(CompilerLiveLiteralsManager::class.java)

  /**
   * Finds the list of Live Literals declared by the compiler in the given `.class` file.
   */
  fun findLiteralsInClass(classFile: VirtualFile?): List<CompilerLiteralDefinition> {
    if (classFile == null) return emptyList()

    val reader = ClassReader(classFile.contentsToByteArray())
    val result = mutableListOf<CompilerLiteralDefinition>()
    // This uses the LiveLiteralsFinder to check all the annotations and retrieve the metadata. Because here we are not
    // transforming the class, we pass null as delegate.
    val finder = object : LiveLiteralsFinder(null) {
      override fun onLiteralAccessor(fileName: String, offset: Int, initialValue: Any?, data: MethodData) {
        log.debug { "Compiler Literal found ($fileName:$offset) with initial value $initialValue" }
        result.add(CompilerLiteralDefinition(fileName, offset))
      }
    }
    reader.accept(finder, 0)

    return if (result.isEmpty()) emptyList() else result
  }

  private fun findClassFileForSourceFileAndClassName(sourceFile: PsiFile, className: String): VirtualFile? =
    sourceFile.module?.getModuleSystem()?.getClassFileFinderForSourceFile(sourceFile.virtualFile)?.findClassFile(className)

  /**
   * Finds the literals declared by the compiler for the given [sourceFile]. If any are found, [onCompilerLiteralsFound] will be called with
   * the result of the lookup in a [Finder] object.
   */
  fun findAsync(sourceFile: PsiFile, onCompilerLiteralsFound: (Finder) -> Unit = {}) {
    val project = sourceFile.project
    val classOwner = sourceFile as? PsiClassOwner ?: return

    ReadAction.nonBlocking<List<CompilerLiteralDefinition>> {
      classOwner.classes
        .mapNotNull { it.name }
        .flatMap { className ->
          val classFileLiterals = findLiteralsInClass(
            findClassFileForSourceFileAndClassName(sourceFile, "${sourceFile.packageName}.LiveLiterals${'$'}$className"))
          classFileLiterals
        }
    }
      .coalesceBy(sourceFile, LiveLiteralsService::class)
      .inSmartMode(project)
      .submit(AppExecutorUtil.getAppExecutorService())
      .onSuccess {
        if (it.isNotEmpty()) {
          onCompilerLiteralsFound(object : Finder {
            override fun hasCompilerLiveLiteral(path: String, offset: Int) = it.contains(CompilerLiteralDefinition(path, offset))
          })
        }
      }
  }
}