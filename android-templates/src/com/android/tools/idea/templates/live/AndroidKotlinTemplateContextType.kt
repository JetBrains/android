/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.templates.live

import com.android.tools.idea.templates.TemplatesBundle
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.openapi.roots.ProjectFileIndex
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.liveTemplates.KotlinTemplateContextType

/**
 * This [TemplateContextType] replicates the structure of [KotlinTemplateContextType],
 * intersecting it with the [AndroidSourceSetTemplateContextType].
 */
internal sealed class AndroidKotlinTemplateContextType(
  private val kotlin: KotlinTemplateContextType,
) : TemplateContextType(kotlin.presentableName) {
  private val android = AndroidSourceSetTemplateContextType()
  override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
    return android.isInContext(templateActionContext) && kotlin.isInContext(templateActionContext)
  }

  class Generic : AndroidKotlinTemplateContextType(KotlinTemplateContextType.Generic())

  class TopLevel : AndroidKotlinTemplateContextType(KotlinTemplateContextType.TopLevel())

  class ObjectDeclaration : AndroidKotlinTemplateContextType(KotlinTemplateContextType.ObjectDeclaration())

  class Class : AndroidKotlinTemplateContextType(KotlinTemplateContextType.Class())

  class Statement : AndroidKotlinTemplateContextType(KotlinTemplateContextType.Statement())

  class Expression : AndroidKotlinTemplateContextType(KotlinTemplateContextType.Expression())

  class Comment : AndroidKotlinTemplateContextType(KotlinTemplateContextType.Comment())
}

/**
 * Checks if the template is applied to an Android-specific source set.
 * This template is used to hide the Android-related templates from unrelated to Android source sets (like common, jvm, ios, etc.)
 */
internal class AndroidSourceSetTemplateContextType : TemplateContextType(
  TemplatesBundle.message("templates.live.context.android")
) {
  override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
    val file = templateActionContext.file
    val module = file.module ?: ProjectFileIndex.getInstance(file.project)
      .getModuleForFile(file.virtualFile ?: file.viewProvider.virtualFile)
    if (module == null || module.isDisposed) return false
    return AndroidFacet.getInstance(module) != null
  }
}
