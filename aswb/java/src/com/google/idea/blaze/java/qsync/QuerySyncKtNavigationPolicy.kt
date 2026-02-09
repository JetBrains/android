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
package com.google.idea.blaze.java.qsync

import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.qsync.java.AddDependencyGenSrcsJars.Companion.ENABLED_NAVIGATION_POLICY
import com.google.idea.common.experiments.BoolExperiment
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile
import org.jetbrains.kotlin.idea.navigation.KotlinAnalysisApiBasedDeclarationNavigationPolicyImpl
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class QuerySyncKtNavigationPolicy : KotlinAnalysisApiBasedDeclarationNavigationPolicyImpl() {
  private val localCache = ThreadLocal.withInitial { mutableMapOf<ClassId, KtClsFile>() }

  companion object {
    val navigateToSourceForTopLevelDeclaration = BoolExperiment("querysync.navigate.kotlin.topleveldeclaration.enable", true)

    private fun <T> getCachedResult(ktClsFile: KtClsFile, project: Project, provider: (KtClsFile) -> T): T {
      return CachedValuesManager.getCachedValue(ktClsFile) {
        Result.create(provider(ktClsFile), ktClsFile, QuerySyncManager.getInstance(project).projectModificationTracker)
      }
    }

    /**
     * Returns candidate source file for KtClsFile provided. It will go over source files of target, source files in generated source jar
     * and in source jar of java target to find the one matched class file.
     */
    private fun findCandiateSourceFile(file: KtClsFile): PsiFile? {
      return ClassFileKtSourceFinder(file).findSourceFile()
        ?: ClassFileGenSrcJarJavaSourceFinder(file).findSourceFile()
        ?: ClassFileSrcJarJavaSourceFinder(file).findSourceFile()
    }

    /**
     * Returns a list of candidate source files for KtClsFile provided. It will go over source files in project, source files in generated
     * source jar and in source jar of java target.
     *
     * In most of cases, we should only have one candidate source file. But it's not true if your target declaration is top level
     * declaration whose class file may merged from multiple kotlin files.
     */
    private fun findCandiateSourceFiles(file: KtClsFile): Collection<PsiFile> {
      return ClassFileKtSourceFinder(file).findSourceFiles()?.takeIf { it.isNotEmpty() }
        ?: ClassFileGenSrcJarJavaSourceFinder(file).findSourceFiles()?.takeIf { it.isNotEmpty() }
        ?: ClassFileSrcJarJavaSourceFinder(file).findSourceFiles()?.takeIf { it.isNotEmpty() }
        ?: if (navigateToSourceForTopLevelDeclaration.value) ClassFileIterateOverAllKtSourceFinder(file).findSourceFiles() else emptySet()
    }
  }

  override fun getNavigationElement(ktDeclaration: KtDeclaration): KtElement {
    if (!ENABLED_NAVIGATION_POLICY.value) return super.getNavigationElement(ktDeclaration)

    val classIdToKtClsFile = localCache.get()
    var classId: ClassId? = null
    val project = ktDeclaration.project
    if (!project.isQuerySyncProject() || DaemonCodeAnalyzer.getInstance(project).isRunning) {
      return super.getNavigationElement(ktDeclaration)
    }

    try {
      val psiFile = ktDeclaration.containingFile
      if (psiFile is KtClsFile) {
        // Determine ClassID based on declaration type
        classId =
          if (ktDeclaration is KtClassLikeDeclaration) {
            ktDeclaration.getClassId()
          } else {
            ktDeclaration.containingClassOrObject?.getClassId()
          }

        if (classId != null) {
          classIdToKtClsFile[classId] = psiFile
        }
      }
      return super.getNavigationElement(ktDeclaration)
    } finally {
      if (classId != null) {
        classIdToKtClsFile.remove(classId)
      }
      // Clean up ThreadLocal to prevent memory leaks
      if (classIdToKtClsFile.isEmpty()) {
        localCache.remove()
      }
    }
  }

  override fun getTopLevelCallablesByName(
    declaration: KtCallableDeclaration,
    callableId: CallableId,
    project: Project,
    scope: Scope,
  ): Sequence<KtCallableDeclaration> {
    val containingFile = declaration.containingFile
    if (containingFile is KtClsFile) {
      val candidateSourceFiles = getCachedResult(containingFile, project, ::findCandiateSourceFiles)
      return candidateSourceFiles
        .asSequence()
        .filterIsInstance<KtFile>()
        .filter { it.packageFqName == callableId.packageName }
        .flatMap { sourceFile ->
          PsiTreeUtil.findChildrenOfType(sourceFile, KtCallableDeclaration::class.java).asSequence().filter {
            it.name == callableId.callableName.asString()
          }
        }
    }
    return super.getTopLevelCallablesByName(declaration, callableId, project, scope)
  }

  override fun getClassesByClassId(classId: ClassId, project: Project, scope: Scope): Sequence<KtClassOrObject> {
    val ktClsFile = localCache.get()[classId] ?: return super.getClassesByClassId(classId, project, scope)

    val candidateSourceFile = getCachedResult(ktClsFile, project, ::findCandiateSourceFile)

    if (candidateSourceFile is KtFile) {
      return PsiTreeUtil.findChildrenOfType(candidateSourceFile, KtClassOrObject::class.java)
        .asSequence()
        .filter { it.name == classId.shortClassName.asString() }
        .filter { classId.asSingleFqName() == it.fqName }
    }

    return super.getClassesByClassId(classId, project, scope)
  }
}
