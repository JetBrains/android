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

import com.android.tools.idea.util.findAndroidModule
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleUtil
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.asJava.KotlinAsJavaSupport
import org.jetbrains.kotlin.fileClasses.isJvmMultifileClassFile
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.analysisContext
import kotlin.collections.plus

suspend fun hasExistingClassFile(psiFile: PsiFile?): Boolean = if (psiFile != null) {
  readAction {
    val classesFqNames = psiFile.findClassesFqNames()
    if (classesFqNames.isEmpty()) {
      false
    } else {
      val module = ModuleUtil.findModuleForFile(psiFile) ?: return@readAction false
      val moduleSystem = module.getModuleSystem()
      val androidModule = module.findAndroidModule()
      val androidModuleSystem = androidModule?.getModuleSystem()
      classesFqNames.any {
        moduleSystem.getClassFileFinderForSourceFile(psiFile.virtualFile).findClassFile(it) != null ||
        androidModuleSystem?.getClassFileFinderForSourceFile(psiFile.virtualFile)?.findClassFile(it) != null
      }
    }
  }
} else {
  false
}

private fun PsiFile.findClassesFqNames(): List<String> {
  return when (this) {
    is KtFile -> kotlinClassDeclarations()
    is PsiClassOwner -> classes.mapNotNull { it.qualifiedName }
    else -> listOf()
  }
}

private fun KtFile.kotlinClassDeclarations(): List<String> =
  declarations.filterIsInstance<KtClassOrObject>().mapNotNull { ktClass -> ktClass.fqName?.asString() } + fetchTopLevelClasses(this)

private fun fetchTopLevelClasses(file: KtFile): List<String> = buildList {
  if (!file.isJvmMultifileClassFile && !file.hasTopLevelCallables()) return@buildList

  val kotlinAsJavaSupport = KotlinAsJavaSupport.getInstance(file.project)
  if (file.analysisContext == null) {
    kotlinAsJavaSupport.getLightFacade(file)?.qualifiedName?.let(this::add)
  } else {
    kotlinAsJavaSupport.createFacadeForSyntheticFile(file).qualifiedName?.let(this::add)
  }
}
