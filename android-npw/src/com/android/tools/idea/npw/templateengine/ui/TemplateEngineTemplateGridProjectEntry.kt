/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.npw.templateengine.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.android.template.engine.TemplateDefinition
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.npw.model.NewProjectModel
import com.android.tools.idea.npw.model.NewProjectModuleModel
import com.android.tools.idea.npw.project.ChooseAndroidProjectEntry
import com.android.tools.idea.npw.project.GridItem
import com.android.tools.idea.npw.project.ItemGrid
import com.android.tools.idea.npw.templateengine.api.ExternalTemplateSpec
import com.android.tools.idea.npw.templateengine.api.PromotionCardSpec
import icons.StudioIllustrationsCompose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon

sealed interface TemplateGalleryItem : GridItem {
  override val title: String
  override val description: String

  data class Standard(val definition: TemplateDefinition) : TemplateGalleryItem {
    override val title: String
      get() = definition.name

    override val description: String
      get() = ""

    @Composable
    override fun Icon() {
      val isDark = JewelTheme.isDark
      val imageBitmap by
        produceState<ImageBitmap?>(initialValue = null, definition, isDark) {
          value = withContext(Dispatchers.Default) { getTemplateThumbnail(definition, isDark) }
        }

      if (imageBitmap == null) {
        Icon(key = StudioIllustrationsCompose.Wizards.NoActivity, contentDescription = "")
      } else {
        Image(bitmap = imageBitmap!!, contentDescription = "")
      }
    }
  }

  data class Promotion(val spec: PromotionCardSpec) : TemplateGalleryItem {
    override val title: String
      get() = spec.title

    override val description: String
      get() = spec.description

    @Composable
    override fun Icon() {
      val customThumb = spec.thumb
      if (customThumb != null) {
        customThumb()
      } else {
        Icon(key = StudioIllustrationsCompose.Wizards.NoActivity, contentDescription = "")
      }
    }
  }

  data class External(val spec: ExternalTemplateSpec) : TemplateGalleryItem {
    override val title: String
      get() = spec.title

    override val description: String
      get() = spec.description

    @Composable
    override fun Icon() {
      val customThumb = spec.thumb
      if (customThumb != null) {
        customThumb()
      } else {
        Icon(key = StudioIllustrationsCompose.Wizards.NoActivity, contentDescription = "")
      }
    }
  }
}

class TemplateEngineTemplateGridProjectEntry(
  val formFactor: FormFactor,
  val items: List<TemplateGalleryItem>,
  private val onTemplateDoubleClick: () -> Unit = {},
) : ChooseAndroidProjectEntry {
  var selectedItem by mutableStateOf(items.firstOrNull { it is TemplateGalleryItem.Standard } ?: items.firstOrNull())

  // Compatibility getter for tests
  val categoryTitle: String
    get() = if (formFactor == FormFactor.MOBILE) "Phone and Large screens" else formFactor.displayName

  // Compatibility getter/setter for ViewModel / Wizard
  var selectedTemplate: TemplateDefinition?
    get() = (selectedItem as? TemplateGalleryItem.Standard)?.definition
    set(value) {
      if (value != null) {
        selectedItem = items.firstOrNull { it is TemplateGalleryItem.Standard && it.definition == value }
      }
    }

  // Compatibility getter for ViewModel / Wizard
  val templates: List<TemplateDefinition>
    get() = items.filterIsInstance<TemplateGalleryItem.Standard>().map { it.definition }

  @Composable
  override fun AndroidProjectListEntry(isSelected: Boolean, isFocused: Boolean) {
    CategoryListCell(text = categoryTitle, isSelected = isSelected, isFocused = isFocused)
  }

  @Composable
  override fun AndroidProjectEntryDetails() {
    ItemGrid(
      gridItems = items,
      selectedGridItem = selectedItem,
      onGridItemClick = { item -> selectedItem = item as? TemplateGalleryItem },
      onGridItemDoubleClick = { item ->
        selectedItem = item as? TemplateGalleryItem
        onTemplateDoubleClick()
      },
    )
  }

  override val canGoForward: State<Boolean> = derivedStateOf { selectedItem != null }

  override fun onProceeding(newProjectModuleModel: NewProjectModuleModel, model: NewProjectModel) {
    // Satisfy ChooseAndroidProjectEntry interface contract
  }
}
