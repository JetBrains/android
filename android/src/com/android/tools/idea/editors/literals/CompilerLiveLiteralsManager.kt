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

import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.runReadAction
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.literals.internal.LiveLiteralsFinder
import com.android.tools.idea.editors.literals.internal.MethodData
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import kotlinx.coroutines.withContext
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.util.module
import org.jetbrains.org.objectweb.asm.ClassReader

private val log = Logger.getInstance(CompilerLiveLiteralsManager::class.java)

/**
 * Finds the list of Live Literals declared by the compiler in the given `.class` file.
 */
private fun findLiteralsInClasses(classes: Collection<ByteArray>): List<CompilerLiveLiteralsManager.CompilerLiteralDefinition> =
  classes.flatMap { classContents ->
    try {
      val reader = ClassReader(classContents)
      val result = mutableListOf<CompilerLiveLiteralsManager.CompilerLiteralDefinition>()
      // This uses the LiveLiteralsFinder to check all the annotations and retrieve the metadata. Because here we are not
      // transforming the class, we pass null as delegate.
      val finder = object : LiveLiteralsFinder(null) {
        override fun onLiteralAccessor(fileName: String, offset: Int, initialValue: Any?, data: MethodData) {
          log.debug { "Compiler Literal found ($fileName:$offset) with initial value $initialValue" }
          result.add(CompilerLiveLiteralsManager.CompilerLiteralDefinition(fileName, offset))
        }
      }
      reader.accept(finder, 0)

      if (result.isEmpty()) emptyList() else result
    } catch (t: Throwable) {
      log.warn("Unable to parse class for literals", t)
      emptyList()
    }
  }

@VisibleForTesting
internal fun PsiFile.getRelativePath(): String? =
  ProjectFileIndex.getInstance(project).getSourceRootForFile(virtualFile)?.let { sourceRoot ->
    VfsUtilCore.getRelativePath(virtualFile, sourceRoot)
  }

/**
 * Class that manages the Live Literals declared by the compiler in a class file.
 *
 * By default, the [LiteralsManager] will list all the literals in a given source file. However, the compiler might not handle
 * all literals as "Live". This class allows reading that information from the compiler so it can be used in different places
 * of the editor.
 *
 * @param findLiteralsInClasses Function that given the "$LiveLiterals" classes bytecode returns the defined literals in them.
 */
class CompilerLiveLiteralsManager private constructor(
  private val findLiteralsInClasses: (Collection<ByteArray>) -> List<CompilerLiteralDefinition>
) {
  /**
   * Result of a [find] call that allows to find which literals were defined by the compiler.
   */
  interface Finder {
    /**
     * Returns true if a Live Literal constant is defined for the given [file] at the given [offset].
     */
    fun hasCompilerLiveLiteral(file: PsiFile, offset: Int): Boolean
  }

  /**
   * Metadata of a Live Literal defined by the compiler.
   */
  data class CompilerLiteralDefinition(val path: String, val offset: Int)

  private suspend fun findClassFileForSourceFileAndClassName(sourceFile: PsiFile, className: String): VirtualFile? =
    runReadAction{ sourceFile.module }?.getModuleSystem()?.getClassFileFinderForSourceFile(sourceFile.virtualFile)?.findClassFile(className)

  /**
   * Finds the literals declared by the compiler for the given [sourceFile] and returns a [Finder] object with the result.
   */
  suspend fun find(sourceFile: PsiFile): Finder {
    val classOwner = sourceFile as? PsiClassOwner ?: return object: Finder {
      override fun hasCompilerLiveLiteral(file: PsiFile, offset: Int): Boolean = false
    }
    return withContext(workerThread) {
      val packageName = runReadAction { sourceFile.packageName }
      val overlayLoader = if (FastPreviewManager.getInstance(sourceFile.project).isEnabled) {
        runReadAction { sourceFile.module }?.let { ModuleClassLoaderOverlays.getInstance(it) }
      } else null
      val liveLiteralClasses = runReadAction {
        classOwner.classes.mapNotNull { it.name }
      }
        .map { className -> "${packageName}.LiveLiterals${'$'}$className" }
        .mapNotNull { classFqn ->
          overlayLoader?.classLoaderLoader?.loadClass(classFqn) ?:
          findClassFileForSourceFileAndClassName(sourceFile, classFqn)?.contentsToByteArray()
        }

      val literalDefinitions = runReadAction {
        findLiteralsInClasses(liveLiteralClasses)
      }

      return@withContext object : Finder {
        override fun hasCompilerLiveLiteral(file: PsiFile, offset: Int) =
          literalDefinitions.contains(CompilerLiteralDefinition(file.virtualFile.path, offset)) ||
          // If we could not find the file using the absolute path, try with the relative since newer versions of the compiler might use that.
          file.getRelativePath()?.let { relativePath -> literalDefinitions.contains(CompilerLiteralDefinition(relativePath, offset)) } ?: false
      }
    }
  }

  companion object {
    private val instance = CompilerLiveLiteralsManager(::findLiteralsInClasses)
    fun getInstance() = instance

    @TestOnly
    fun getTestInstance(findLiteralsInClasses: (Collection<ByteArray>) -> List<CompilerLiteralDefinition> = ::findLiteralsInClasses) =
      CompilerLiveLiteralsManager(findLiteralsInClasses)
  }
}