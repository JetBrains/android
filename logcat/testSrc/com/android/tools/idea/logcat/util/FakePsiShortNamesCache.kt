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
package com.android.tools.idea.logcat.util

import com.intellij.mock.MockPsiFile
import com.intellij.mock.MockPsiManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.Processor

/**
 * A fake [PsiShortNamesCache] for tests.
 *
 * Minimal functionality only is provided. We currently only need to class to answer to
 * getFilesByName().
 */
internal class FakePsiShortNamesCache(project: Project, filenames: List<String>) :
  PsiShortNamesCache() {
  private val files: Map<String, List<FakePsiFile>> =
    filenames.groupBy({ it.substringAfterLast('/') }) { FakePsiFile(project, it) }

  override fun getFilesByName(name: String): Array<PsiFile> =
    (files[name] ?: emptyList()).toTypedArray()

  override fun getClassesByName(name: String, scope: GlobalSearchScope): Array<PsiClass> {
    TODO("Not yet implemented")
  }

  override fun getAllClassNames(): Array<String> {
    TODO("Not yet implemented")
  }

  override fun getMethodsByName(name: String, scope: GlobalSearchScope): Array<PsiMethod> {
    TODO("Not yet implemented")
  }

  override fun getMethodsByNameIfNotMoreThan(
    name: String,
    scope: GlobalSearchScope,
    maxCount: Int,
  ): Array<PsiMethod> {
    TODO("Not yet implemented")
  }

  override fun getFieldsByNameIfNotMoreThan(
    name: String,
    scope: GlobalSearchScope,
    maxCount: Int,
  ): Array<PsiField> {
    TODO("Not yet implemented")
  }

  override fun processMethodsWithName(
    name: String,
    scope: GlobalSearchScope,
    processor: Processor<in PsiMethod>,
  ): Boolean {
    TODO("Not yet implemented")
  }

  override fun getAllMethodNames(): Array<String> {
    TODO("Not yet implemented")
  }

  override fun getFieldsByName(name: String, scope: GlobalSearchScope): Array<PsiField> {
    TODO("Not yet implemented")
  }

  override fun getAllFieldNames(): Array<String> {
    TODO("Not yet implemented")
  }
}

private class FakePsiFile(project: Project, private val filename: String) :
  MockPsiFile(LightVirtualFile(filename), MockPsiManager(project)) {
  override fun getName(): String = filename

  override fun getContainingFile(): PsiFile = this
}
