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

import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.project.AndroidProjectEntryProvider
import com.android.tools.idea.npw.project.ChooseAndroidProjectEntry
import com.android.tools.idea.npw.templateengine.WizardConstants
import com.android.tools.idea.npw.templateengine.services.TemplateRegistryService
import com.google.common.collect.ImmutableListMultimap

class TemplateGridProjectEntryProvider : AndroidProjectEntryProvider {

  companion object {
    private val PREFERRED_TEMPLATE_ORDER_BY_CATEGORY: ImmutableListMultimap<FormFactor, String> =
      ImmutableListMultimap.builder<FormFactor, String>().putAll(FormFactor.MOBILE, WizardConstants.TemplateNames.EMPTY_ACTIVITY).build()
  }

  override fun getProjectEntries(): List<ChooseAndroidProjectEntry> {
    if (!StudioFlags.NPW_NEW_TEMPLATE_ENGINE.get()) {
      return emptyList()
    }
    val registry = TemplateRegistryService.getInstance()
    val templateDefinitions = registry.getTemplateDefinitions()
    val promotionCards = registry.getContributorPromotionCards()
    val externalTemplates = registry.getContributorExternalTemplates()

    val groupedItems = mutableMapOf<FormFactor, MutableList<TemplateGalleryItem>>()

    // 1. Group standard templates
    templateDefinitions.forEach { template ->
      val formFactor =
        when {
          template.metadata.tags.contains(WizardConstants.TAG_WEAR) -> FormFactor.WEAR
          template.metadata.tags.contains(WizardConstants.TAG_TV) -> FormFactor.TV
          template.metadata.tags.contains(WizardConstants.TAG_CAR) -> FormFactor.AUTOMOTIVE
          else -> FormFactor.MOBILE
        }
      groupedItems.getOrPut(formFactor) { mutableListOf() }.add(TemplateGalleryItem.Standard(template))
    }

    // 2. Group promotion cards
    promotionCards.forEach { card -> groupedItems.getOrPut(card.formFactor) { mutableListOf() }.add(TemplateGalleryItem.Promotion(card)) }

    // 3. Group external templates
    externalTemplates.forEach { template ->
      groupedItems.getOrPut(template.formFactor) { mutableListOf() }.add(TemplateGalleryItem.External(template))
    }

    val sortedKeys = groupedItems.keys.sorted()

    val entries = mutableListOf<ChooseAndroidProjectEntry>()
    sortedKeys.forEach { formFactor ->
      val categoryOrder = PREFERRED_TEMPLATE_ORDER_BY_CATEGORY[formFactor]
      val unsortedItems = groupedItems[formFactor] ?: emptyList()
      if (unsortedItems.isNotEmpty()) {
        val sortedItems =
          unsortedItems.sortedWith(
            compareBy<TemplateGalleryItem> { getGalleryItemPriority(it, categoryOrder) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
          )
        entries.add(TemplateEngineTemplateGridProjectEntry(formFactor, sortedItems))
      }
    }

    return entries
  }

  private fun getGalleryItemPriority(item: TemplateGalleryItem, categoryOrder: List<String>): Int {
    return when (item) {
      is TemplateGalleryItem.Promotion -> -1000 - item.spec.priority
      is TemplateGalleryItem.Standard -> {
        val index = categoryOrder.indexOf(item.definition.shortName)
        if (index >= 0) index else 1000
      }
      is TemplateGalleryItem.External -> 2000
    }
  }
}
