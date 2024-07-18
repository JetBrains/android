/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.light.AbstractLightClass
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.fileClasses.isJvmMultifileClassFile
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.analysisContext

fun Project.requestBuild(file: VirtualFile) {
  requestBuild(listOf(file))
}

fun Project.requestBuild(files: Collection<VirtualFile>) {
  // TODO(b/231401347): Move this to the client side
  if (this.isDisposed) {
    return
  }

  ProjectSystemService.getInstance(this).projectSystem.getBuildManager().compileFilesAndDependencies(files.map { it.getSourceFile() })
}

fun hasExistingClassFile(psiFile: PsiFile?) = if (psiFile is PsiClassOwner) {
  val androidModuleSystem by lazy {
    ReadAction.compute<AndroidModuleSystem?, Throwable> {
      psiFile.getModuleSystem()
    }
  }
  runReadAction { psiFile.findClassesFqNames() }
    .mapNotNull { androidModuleSystem?.getClassFileFinderForSourceFile(runReadAction { psiFile.virtualFile })?.findClassFile(it) }
    .firstOrNull() != null
}
else false

private fun PsiFile.findClassesFqNames(): List<String> {
  return when (this) {
    is KtFile -> asClassOwner().classes
    is PsiClassOwner -> classes
    else -> arrayOf()
  }.mapNotNull { it.qualifiedName }
}

private fun KtFile.topLevelDeclarationsAsClasses(): Array<PsiClass> {
  val result = mutableListOf<PsiClass>()
  if (!isJvmMultifileClassFile && !hasTopLevelCallables()) return result.toTypedArray()

  val kotlinAsJavaSupport = KotlinAsJavaSupport.getInstance(project)
  if (analysisContext == null) {
    kotlinAsJavaSupport.getLightFacade(this)?.let(result::add)
  } else {
    result.add(kotlinAsJavaSupport.createFacadeForSyntheticFile(this))
  }

  return result.toTypedArray()
}

fun KtFile.asClassOwner(): PsiClassOwner = object : PsiClassOwner by this {
  override fun getClasses(): Array<out PsiClass?> {
    val regularClasses = declarations.filterIsInstance<KtClassOrObject>().map { ktClass ->
      object : AbstractLightClass(manager) {
        override fun getQualifiedName(): String? = ktClass.fqName?.asString()
        override fun getDelegate(): PsiClass = error("Not implemented")
        override fun copy(): PsiElement = error("Not implemented")
      }
    }
    val topLevelDeclarations = topLevelDeclarationsAsClasses()
    return (regularClasses + topLevelDeclarations).toTypedArray()
  }
}

@Suppress("UnstableApiUsage")
private fun VirtualFile.getSourceFile(): VirtualFile = if (!this.isInLocalFileSystem && this is BackedVirtualFile) {
  this.originFile
}
else this