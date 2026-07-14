/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.bazel.java.qsync

import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.qsync.java.AddDependencyGenSrcsJars.Companion.ENABLED_NAVIGATION_POLICY
import com.google.idea.common.experiments.BoolExperiment
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import java.util.concurrent.CancellationException
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile
import org.jetbrains.kotlin.idea.navigation.KotlinAnalysisApiBasedDeclarationNavigationPolicyImpl
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class QuerySyncKtNavigationPolicy : KotlinAnalysisApiBasedDeclarationNavigationPolicyImpl() {

  companion object {
    private val logger = Logger.getInstance(QuerySyncKtNavigationPolicy::class.java)
    val navigateToSourceForTopLevelDeclaration = BoolExperiment("querysync.navigate.kotlin.topleveldeclaration.enable", true)

    private fun <T> getCachedResult(ktClsFile: KtClsFile, project: Project, provider: (KtClsFile) -> T): T {
      return CachedValuesManager.getCachedValue(ktClsFile) {
        val result =
          try {
            provider(ktClsFile)
          } catch (e: Exception) {
            if (e is ProcessCanceledException || e is CancellationException || e is InterruptedException) {
              throw e
            }
            logger.error("Failed to find navigation file for: ${ktClsFile.name}", e)
            null
          }
        Result.create(result, ktClsFile, QuerySyncManager.getInstance(project).projectModificationTracker)
      }
    }

    /**
     * Returns a list of candidate source files for KtClsFile provided. It will go over source files in project, source files in generated
     * source jar and in source jar of java target.
     *
     * In most of the cases, we should only have one candidate source file. But it's not true if your target declaration is top level
     * declaration whose class file may merged from multiple kotlin files.
     */
    private fun findCandidateSourceFiles(file: KtClsFile): Collection<PsiFile> {
      return ClassFileKtSourceFinder(file).findSourceFiles()?.takeIf { it.isNotEmpty() }
        ?: ClassFileGenSrcJarJavaSourceFinder(file).findSourceFiles()?.takeIf { it.isNotEmpty() }
        ?: ClassFileSrcJarJavaSourceFinder(file).findSourceFiles()?.takeIf { it.isNotEmpty() }
        ?: if (navigateToSourceForTopLevelDeclaration.value) ClassFileIterateOverAllKtSourceFinder(file).findSourceFiles() else emptySet()
    }
  }

  private fun eligibleToRun(project: Project): Boolean {
    return project.isQuerySyncProject() && !DaemonCodeAnalyzer.getInstance(project).isRunning
  }

  override fun getNavigationElement(ktDeclaration: KtDeclaration): KtElement {
    if (DebuggerManagerThreadImpl.isManagerThread() || !ENABLED_NAVIGATION_POLICY.value) return super.getNavigationElement(ktDeclaration)

    val project = ktDeclaration.project
    if (!eligibleToRun(project)) {
      return super.getNavigationElement(ktDeclaration)
    }

    val ktClsFile = ktDeclaration.containingFile as? KtClsFile ?: return super.getNavigationElement(ktDeclaration)

    val candidateSourceFiles = getCachedResult(ktClsFile, project, ::findCandidateSourceFiles)
    for (candidateSourceFile in candidateSourceFiles) {
      if (candidateSourceFile is KtFile) {
        val target = findMatchingDeclarationInSource(ktDeclaration, candidateSourceFile)
        if (target != null) {
          return target
        }
      }
    }

    return super.getNavigationElement(ktDeclaration)
  }

  private fun findMatchingDeclarationInSource(original: KtDeclaration, sourceFile: KtFile): KtElement? {
    return when (original) {
      is KtClassLikeDeclaration -> {
        val exactMatch =
          original.getClassId()?.let { classId ->
            val shortName = classId.shortClassName.asString()
            val fqName = classId.asSingleFqName()

            PsiTreeUtil.findChildrenOfType(sourceFile, KtClassOrObject::class.java).asSequence().firstOrNull { ktClass ->
              val nameMatches =
                ktClass.name == shortName || (shortName == "Companion" && ktClass is KtObjectDeclaration && ktClass.isCompanion())

              nameMatches && ktClass.fqName == fqName
            }
          }

        exactMatch ?: PsiTreeUtil.findChildOfType(sourceFile, KtClassOrObject::class.java)
      }

      is KtCallableDeclaration -> {
        val name = original.name ?: return null
        val parentClassId = original.containingClassOrObject?.getClassId()
        PsiTreeUtil.findChildrenOfType(sourceFile, KtCallableDeclaration::class.java)
          .asSequence()
          .filter { it.name == name }
          .filter {
            if (parentClassId != null) {
              it.containingClassOrObject?.getClassId() == parentClassId
            } else {
              it.containingClassOrObject == null
            }
          }
          .firstOrNull()
      }

      else -> null
    }
  }
}
