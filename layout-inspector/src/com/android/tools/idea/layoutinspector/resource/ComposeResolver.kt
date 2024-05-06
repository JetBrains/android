/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.resource

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.packageNameHash
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.SearchScopeProvidingRunProfile
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.psi.KtFile

/** Service for finding Composable function locations. */
open class ComposeResolver(val project: Project) {

  @Slow
  fun findComposableNavigatable(node: ComposeViewNode): Navigatable? {
    val ktFile =
      findKotlinFile(node.composeFilename) { packageNameHash(it) == node.composePackageHash }
        ?: return null
    val vFile = ktFile.virtualFile ?: return null
    return PsiNavigationSupport.getInstance().createNavigatable(project, vFile, node.composeOffset)
  }

  /**
   * Find the kotlin file from the filename found in the tooling API.
   *
   * If there are multiple files with the same name use the package name matcher.
   */
  @Slow
  protected fun findKotlinFile(fileName: String, packageNameMatcher: (String) -> Boolean): KtFile? {
    val configuration = RunManager.getInstance(project).selectedConfiguration?.configuration
    val runScope = (configuration as? SearchScopeProvidingRunProfile)?.searchScope
    if (runScope != null) {
      // Attempt to use scope from current configuration, fallback to all scopes if that fails to
      // find the source file.
      findKotlinFileInScope(runScope, fileName, packageNameMatcher)?.let {
        return it
      }
    }
    return findKotlinFileInScope(GlobalSearchScope.allScope(project), fileName, packageNameMatcher)
  }

  private fun findKotlinFileInScope(
    scope: GlobalSearchScope,
    fileName: String,
    packageNameMatcher: (String) -> Boolean
  ): KtFile? {
    val files = FilenameIndex.getVirtualFilesByName(fileName, scope)
    val psiManager = PsiManager.getInstance(project)
    val sourceFiles: List<PsiFileSystemItem> =
      files.mapNotNull {
        when {
          !it.isValid -> null
          it.isDirectory -> null
          else -> psiManager.findFile(it)
        }
      }
    return sourceFiles.filterIsInstance<KtFile>().find {
      packageNameMatcher(it.packageFqName.asString())
    }
  }
}
