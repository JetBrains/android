/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property

import com.android.tools.idea.uibuilder.property.support.HelpActions
import com.android.tools.property.panel.api.PropertyItem
import com.android.tools.property.ptable.PTableItem
import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.Navigatable
import icons.StudioIcons
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import javax.swing.Icon
import org.jetbrains.concurrency.Promise

/** DocumentationTarget for Nele properties. */
class NlPropertyDocumentationTarget(
  private val componentModel: NlPropertiesModel,
  private val currentPropertyItem: () -> Promise<PTableItem?>,
) : DocumentationTarget, Pointer<NlPropertyDocumentationTarget> {

  override fun computePresentation(): TargetPresentation {
    val item = currentPropertyItem().blockingGet(100, TimeUnit.MILLISECONDS)
    val property = item?.asProperty()
    return TargetPresentation.builder(property?.name ?: "")
      .icon(property?.iconPresentation())
      .presentation()
  }

  override fun createPointer(): Pointer<NlPropertyDocumentationTarget> = this

  override fun dereference(): NlPropertyDocumentationTarget = this

  /** Provide a way to "Jump to source" of the component being inspected. */
  override val navigatable: Navigatable?
    get() = componentModel.selection.singleOrNull()?.navigatable

  override fun computeDocumentation(): DocumentationResult {
    return DocumentationResult.asyncDocumentation(
      object : Supplier<DocumentationResult.Documentation?> {
        override fun get(): DocumentationResult.Documentation? {
          try {
            val item = currentPropertyItem().blockingGet(100, TimeUnit.MILLISECONDS)
            val property = item?.asProperty() ?: return null
            return DocumentationResult.documentation(
              HelpActions.createHelpText(property, allowEmptyDescription = true)
            )
          } catch (ex: Exception) {
            return null
          }
        }
      }
    )
  }

  private fun PTableItem.asProperty(): NlPropertyItem? =
    when (this) {
      is NlFlagPropertyItem -> this.flags
      is NlPropertyItem -> this
      is PropertyItem -> componentModel.properties.getOrNull(namespace, name)
      else -> null
    }

  private fun NlPropertyItem.iconPresentation(): Icon? =
    when (this) {
      is NlFlagsPropertyItem -> StudioIcons.LayoutEditor.Properties.FLAG
      else -> namespaceIcon ?: colorButton?.actionIcon
    }
}
