/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.compose.templates

import com.android.tools.compose.ComposeBundle
import com.android.tools.compose.isComposeEnabled
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import org.jetbrains.kotlin.idea.liveTemplates.KotlinTemplateContextType

/**
 * This [TemplateContextType] replicates the structure of [KotlinTemplateContextType], intersecting
 * it with the [ComposeEnabledTemplateContextType].
 */
internal sealed class ComposeKotlinTemplateContextType(
  private val kotlin: KotlinTemplateContextType
) : TemplateContextType(kotlin.presentableName) {
  private val compose = ComposeEnabledTemplateContextType()

  override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
    return compose.isInContext(templateActionContext) && kotlin.isInContext(templateActionContext)
  }

  class Generic : ComposeKotlinTemplateContextType(KotlinTemplateContextType.Generic())

  class TopLevel : ComposeKotlinTemplateContextType(KotlinTemplateContextType.TopLevel())

  class ObjectDeclaration :
    ComposeKotlinTemplateContextType(KotlinTemplateContextType.ObjectDeclaration())

  class Class : ComposeKotlinTemplateContextType(KotlinTemplateContextType.Class())

  class Statement : ComposeKotlinTemplateContextType(KotlinTemplateContextType.Statement())

  class Expression : ComposeKotlinTemplateContextType(KotlinTemplateContextType.Expression())

  class Comment : ComposeKotlinTemplateContextType(KotlinTemplateContextType.Comment())
}

/**
 * Checks if the template is applied to context in which Compose is enabled. This template is used
 * to hide the Compose-related templates from plain Kotlin without Compose.
 */
internal class ComposeEnabledTemplateContextType :
  TemplateContextType(ComposeBundle.message("compose.templates.presentable.name")) {
  override fun isInContext(templateActionContext: TemplateActionContext) =
    isComposeEnabled(templateActionContext.file)
}
