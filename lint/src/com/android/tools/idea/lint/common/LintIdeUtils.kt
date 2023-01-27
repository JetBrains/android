/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common

import com.android.tools.lint.detector.api.Context
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

/**
 * Returns the [PsiFile] associated with a given lint [Context].
 */
fun Context.getPsiFile(): PsiFile? {
  val request = driver.request
  val project = (request as LintIdeRequest).project
  if (project.isDisposed) {
    return null
  }
  val file = VfsUtil.findFileByIoFile(file, false) ?: return null
  return file.getPsiFileSafely(project)
}

/** Checks if this [KtProperty] has a backing field or implements get/set on its own. */
fun KtProperty.hasBackingField(): Boolean {
  val propertyDescriptor = descriptor as? PropertyDescriptor ?: return false
  return analyze(BodyResolveMode.PARTIAL)[BindingContext.BACKING_FIELD_REQUIRED, propertyDescriptor] ?: false
}

/**
 * Looks up the [PsiFile] for a given [VirtualFile] in a given [Project], in
 * a safe way (meaning it will acquire a read lock first, and will check that the file is valid
 */
fun VirtualFile.getPsiFileSafely(project: Project): PsiFile? {
  return ApplicationManager.getApplication().runReadAction((Computable {
    when {
      project.isDisposed -> null
      isValid -> PsiManager.getInstance(project).findFile(this)
      else -> null
    }
  }))
}