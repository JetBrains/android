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
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.decompiler.psi.file.KtClsFile
import org.jetbrains.kotlin.idea.navigation.KotlinAnalysisApiBasedDeclarationNavigationPolicyImpl
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClassLikeDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

class QuerySyncKtNavigationPolicy : KotlinAnalysisApiBasedDeclarationNavigationPolicyImpl() {

  private val localCache = ThreadLocal.withInitial { mutableMapOf<ClassId, KtClsFile>() }

  override fun getNavigationElement(ktDeclaration: KtDeclaration): KtElement {
    val classIdToKtClsFile = localCache.get()
    var classId: ClassId? = null

    try {
      val psiFile = ktDeclaration.containingFile
      if (psiFile is KtClsFile) {
        // Determine ClassID based on declaration type
        classId = if (ktDeclaration is KtClassLikeDeclaration) {
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

  override fun getClassesByClassId(
    classId: ClassId,
    project: Project,
    scope: Scope
  ): Sequence<KtClassOrObject> {
    val ktClsFile = localCache.get()[classId] ?: return super.getClassesByClassId(classId, project, scope)

    val psiFile = getSourceFile(ktClsFile, project)

    if (psiFile is KtFile) {
      return PsiTreeUtil.findChildrenOfType(psiFile, KtClassOrObject::class.java)
        .asSequence()
        .filter { it.name == classId.shortClassName.asString() }
        .filter { classId.asSingleFqName() == it.fqName }
    }

    return super.getClassesByClassId(classId, project, scope)
  }

  private fun getSourceFile(ktClsFile: KtClsFile, project: Project): PsiFile? {
    return CachedValuesManager.getCachedValue(ktClsFile) {
      Result.create(
        ClassFileKtSourceFinder(ktClsFile).findSourceFile()
        ?: ClassFileGenSrcJarJavaSourceFinder(ktClsFile).findSourceFile()
        ?: ClassFileSrcJarJavaSourceFinder(ktClsFile).findSourceFile(),
        ktClsFile,
        QuerySyncManager.getInstance(project).projectModificationTracker
      )
    }
  }
}