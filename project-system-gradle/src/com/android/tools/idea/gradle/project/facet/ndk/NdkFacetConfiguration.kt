/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.facet.ndk

import com.android.tools.idea.gradle.project.model.VariantAbi
import com.intellij.facet.FacetConfiguration
import com.intellij.facet.ui.FacetEditorContext
import com.intellij.facet.ui.FacetEditorTab
import com.intellij.facet.ui.FacetValidatorsManager
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.WriteExternalException
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element

class NdkFacetConfiguration : FacetConfiguration {
  override fun createEditorTabs(editorContext: FacetEditorContext,
                                validatorsManager: FacetValidatorsManager): Array<FacetEditorTab> = emptyArray()
  var selectedVariantAbi: VariantAbi? = null

  @Throws(InvalidDataException::class)
  override fun readExternal(element: Element) {
    val serializedVariantAbi = SerializedVariantAbi()
    XmlSerializer.deserializeInto(serializedVariantAbi, element)
    selectedVariantAbi = serializedVariantAbi.toVariantAbi()
  }

  @Throws(WriteExternalException::class)
  override fun writeExternal(element: Element) {
    val serializedVariantAbi = SerializedVariantAbi.fromVariantAbi(selectedVariantAbi)
    XmlSerializer.serializeInto(serializedVariantAbi, element)
  }

  private class SerializedVariantAbi @JvmOverloads constructor(
    var SELECTED_VARIANT: String? = null,
    var SELECTED_ABI: String? = null
  ) {
    fun toVariantAbi(): VariantAbi? {
      val selectedVariant = SELECTED_VARIANT ?: return null
      val selectedAbi = SELECTED_ABI ?: return null
      return VariantAbi(selectedVariant, selectedAbi)
    }

    companion object {
      fun fromVariantAbi(variantAbi: VariantAbi?) = SerializedVariantAbi(variantAbi?.variant, variantAbi?.abi)
    }
  }
}