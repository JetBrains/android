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
package com.android.tools.idea.compose.pickers.base.model

import com.android.tools.idea.compose.pickers.base.property.PsiPropertyItem
import com.android.tools.idea.compose.pickers.base.tracking.ComposePickerTracker
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.collect.HashBasedTable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

/** Returns the [PsiPropertyItem]s that will be available for the given [PsiCallPropertiesModel]. */
internal typealias PsiPropertiesProvider =
  (Project, PsiCallPropertiesModel, ResolvedCall<*>) -> Collection<PsiPropertyItem>

/**
 * [PsiPropertiesModel] for pickers handling calls. This is common in Compose where most pickers
 * interact with method calls.
 *
 * For example, a theme in Compose is a method call. This model allows editing those properties.
 *
 * ```
 * val darkThemePalette = themePalette(
 *   colorPrimary = Color(12, 13, 14)
 * )
 * ```
 *
 * The same applies for annotations, where the parameters are considered by the PSI parsing as a
 * method call.
 *
 * ```
 * @Preview(name = "Hello", group = "group")
 * ```
 *
 * In both cases, this [PsiCallPropertiesModel] will deal with the named parameters as properties.
 */
internal abstract class PsiCallPropertiesModel
internal constructor(
  val project: Project,
  val module: Module,
  resolvedCall: ResolvedCall<*>,
  psiPropertiesProvider: PsiPropertiesProvider,
  override val tracker:
    ComposePickerTracker // TODO(b/205195408): Refactor tracker to a more general use
) : PsiPropertiesModel(), DataProvider {

  val psiFactory: KtPsiFactory by lazy(LazyThreadSafetyMode.NONE) { KtPsiFactory(project, true) }

  val ktFile = resolvedCall.call.callElement.containingKtFile

  override val properties: PropertiesTable<PsiPropertyItem> =
    PropertiesTable.create(
      HashBasedTable.create<String, String, PsiPropertyItem>().also { table ->
        psiPropertiesProvider(project, this@PsiCallPropertiesModel, resolvedCall).forEach {
          table.put(it.namespace, it.name, it)
        }
      }
    )
}
