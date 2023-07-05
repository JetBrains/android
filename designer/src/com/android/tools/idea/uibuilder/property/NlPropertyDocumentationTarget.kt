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

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.property.support.HelpActions
import com.android.tools.property.ptable.PTableItem
import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.pom.Navigatable
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import org.jetbrains.concurrency.Promise

/** DocumentationTarget for Nele properties. */
class NlPropertyDocumentationTarget(
  private val currentPropertyItem: () -> Promise<PTableItem?>,
  private val currentSelectedComponent: () -> NlComponent?
) : DocumentationTarget, Pointer<NlPropertyDocumentationTarget> {

  override fun computePresentation(): TargetPresentation =
    TargetPresentation.builder("").presentation()

  override fun createPointer(): Pointer<NlPropertyDocumentationTarget> = this

  override fun dereference(): NlPropertyDocumentationTarget = this

  /** Provide a way to "Jump to source" of the component being inspected. */
  override val navigatable: Navigatable?
    get() = currentSelectedComponent()?.navigatable

  override fun computeDocumentation(): DocumentationResult {
    return DocumentationResult.asyncDocumentation(
      object : Supplier<DocumentationResult.Documentation?> {
        val item = currentPropertyItem()
        override fun get(): DocumentationResult.Documentation? {
          try {
            val property =
              item.blockingGet(100, TimeUnit.MILLISECONDS) as? NlPropertyItem ?: return null
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
}
