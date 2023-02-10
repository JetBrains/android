/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.assistant

import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.TOOLS_PREFIX
import com.android.SdkConstants.TOOLS_SAMPLE_PREFIX
import com.android.SdkConstants.TOOLS_URI
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.adtui.model.stdui.CommonComboBoxModel
import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.adtui.stdui.CommonComboBoxRenderer
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.SampleDataResourceItem
import com.android.tools.idea.uibuilder.assistant.AssistantPopupPanel
import com.android.tools.idea.uibuilder.assistant.ComponentAssistantFactory.Context
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import java.awt.Component
import java.awt.GridLayout
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel

private const val NONE_VALUE = "None"

class TextViewAssistant(private val context: Context) : AssistantPopupPanel() {
  private val myComponent: NlComponent = context.component
  private val myOriginalTextValue: String?
  private val myAppResources = StudioResourceRepositoryManager.getAppResources(context.component.model.facet)

  private var myProject: Project = context.component.model.facet.module.project

  init {
    val mainPanel = JPanel(GridLayout(0, 1)).apply {
      isOpaque = true
      background = this@TextViewAssistant.background
      add(assistantLabel("Text"))

      val elements = listOf(null) + myAppResources.getResources(ResourceNamespace.TOOLS, ResourceType.SAMPLE_DATA).values()
        .filterIsInstance<SampleDataResourceItem>()
        .filter {
          it.contentType == SampleDataResourceItem.ContentType.TEXT
        }
        .map {
          val reference = it.referenceToSelf
          // TODO: referenceToSelf.getResourceUrl does not return the correct prefix for the TOOLS namespace
          ResourceUrl.create(TOOLS_PREFIX, reference.resourceType, reference.name)
        }
        .sortedBy { it.toString() }
        .toList()

      // Retrieve the existing reference to populate the selected item. Remove the index operators if present.
      val existingToolsText = context.component.getAttribute(TOOLS_URI, ATTR_TEXT).orEmpty().substringBefore('[')

      val model = DefaultCommonComboBoxModel("", elements)
      val combo = CommonComboBox<ResourceUrl?, CommonComboBoxModel<ResourceUrl?>>(model).apply {
        isEditable = false
        selectedIndex = model.findIndexForExistingUrl(existingToolsText)
        renderer = TextViewAssistantListRenderer()
        background = this@TextViewAssistant.background
        isOpaque = true
      }

      combo.addActionListener {
        onElementSelected(combo.selectedItem?.toString())
      }
      add(combo)
    }

    addContent(mainPanel)

    myOriginalTextValue = myComponent.getAttribute(TOOLS_URI, ATTR_TEXT)
  }

  private fun DefaultCommonComboBoxModel<ResourceUrl?>.findIndexForExistingUrl(
    existingUrl: String
  ): Int {

    if (existingUrl.startsWith(TOOLS_SAMPLE_PREFIX)) {
      for (i in 0 until size) {
        val resourceUrl = getElementAt(i)
        if (existingUrl == resourceUrl.toString()) {
          return i
        }
      }
    }
    return if (size > 0) 0 else -1
  }

  private fun onElementSelected(selectedItem: String?) {
    val attributeValue = if (selectedItem.isNullOrEmpty()) null else selectedItem
    CommandProcessor.getInstance().runUndoTransparentAction {
      WriteCommandAction.runWriteCommandAction(myProject) {
        myComponent.setAttribute(TOOLS_URI, ATTR_TEXT, attributeValue)
      }
    }
  }

  companion object {
    @JvmStatic
    fun createComponent(context: Context): JComponent {
      return TextViewAssistant(context)
    }
  }
}

private class TextViewAssistantListRenderer : CommonComboBoxRenderer() {
  override fun getListCellRendererComponent(list: JList<*>,
                                            value: Any?,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val displayValue = (value as? ResourceUrl)?.name ?: NONE_VALUE
    return super.getListCellRendererComponent(list, displayValue, index, isSelected, cellHasFocus)
  }
}
