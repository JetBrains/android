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

import com.android.ide.common.rendering.api.ResourceValue
import com.android.resources.ResourceType
import com.android.tools.adtui.model.stdui.DefaultCommonComboBoxModel
import com.android.tools.adtui.stdui.CommonComboBox
import com.android.tools.adtui.ui.ClickableLabel
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.SampleDataResourceItem
import com.android.tools.idea.res.SampleDataResourceItem.ContentType.IMAGE
import com.android.tools.idea.res.getDrawableResources
import com.android.tools.idea.res.getSampleDataOfType
import com.android.tools.idea.ui.resourcechooser.DrawableGrid
import com.android.tools.idea.ui.resourcechooser.util.createResourcePickerDialog
import com.android.tools.idea.uibuilder.handlers.ImageViewHandler
import com.android.tools.idea.uibuilder.assistant.AssistantPopupPanel
import com.android.tools.idea.uibuilder.assistant.ComponentAssistantFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders
import com.intellij.util.ui.JBUI.scale
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.util.EnumSet
import java.util.concurrent.CompletableFuture
import java.util.function.BiConsumer
import java.util.function.Supplier
import javax.swing.Box
import javax.swing.DefaultListModel
import javax.swing.JComboBox
import javax.swing.JPanel

private const val ITEM_COUNT = 12
private val IMAGE_SIZE = scale(48)
private const val NONE_VALUE = "None"

/**
 * Assistant for the image view that display a grid of sample image that which the user
 * can select and will be applied to the provided
 * [ComponentAssistantFactory.Context.component]
 */
class ImageViewAssistant(
  private val context: ComponentAssistantFactory.Context,
  private val imageHandler: ImageViewHandler
) {
  private val nlComponent = context.component

  private var selectedSampleItem: SampleDataResourceItem? = null

  private val originalValue = imageHandler.getToolsSrc(nlComponent)

  private val itemNameLabel = assistantLabel(getSampleItemDisplayName(originalValue))

  /**
   * [CompletableFuture] used to verify that the load of the sample data resources is complete.
   */
  @TestOnly
  val sampleDataLoaded: CompletableFuture<List<SampleDataResourceItem>>

  private var itemDisplayName: String?
    get() = itemNameLabel.text
    set(itemName) {
      itemNameLabel.text = StringUtil.shortenTextWithEllipsis(itemName.orEmpty(), 20, 15, true)
    }

  private val drawableGrid = createDrawableGrid()

  private var useAll = originalValue == null || isSampleValueAll(originalValue)
    set(value) {
      field = value
      drawableGrid.isEnabled = !value
      if (value) drawableGrid.clearSelection() else drawableGrid.selectedIndex = 0
    }

  private val useAllCheckBox = createUseAllCheckBox()

  private val bottomBar = createBottomBar()

  private val comboBoxModel = DefaultCommonComboBoxModel<SampleDataSetItem>(NONE_VALUE)

  private val sampleDataSetComboBox = CommonComboBox(comboBoxModel).apply {
    isOpaque = false
    isEnabled = false
    isEditable = false
    addActionListener { event ->
      val selectedItem = (event.source as JComboBox<*>).selectedItem as? SampleDataSetItem
      setSelectedSampleItem(selectedItem?.resource)
    }
  }

  private val content = JPanel(BorderLayout()).apply {
    isOpaque = false
    add(createHeader(), BorderLayout.NORTH)
    add(drawableGrid)
    add(bottomBar, BorderLayout.SOUTH)
  }

  val component = AssistantPopupPanel(content = content)

  init {
    displayResourceValues(null, -1)
    updateUIState()

    // Get SampleData drawables in background thread, then, update the widget on the EDT
    sampleDataLoaded = CompletableFuture.supplyAsync({
      StudioResourceRepositoryManager.getAppResources(nlComponent.model.facet).getSampleDataOfType(IMAGE).toList()
    }, AppExecutorUtil.getAppExecutorService()).whenCompleteAsync({ sampleDataItems, _ ->
      populateWidget(sampleDataItems)
    }, EdtExecutorService.getScheduledExecutorInstance())
  }

  private fun isSampleValueAll(value: String?) = value?.endsWith(']')?.not() ?: false

  private fun populateWidget(sampleDataItems: List<SampleDataResourceItem>) {
    updateComboBox(sampleDataItems)
    updateUIState()
  }

  private fun createHeader(): JPanel {
    return JPanel(BorderLayout()).apply {
      isOpaque = false
      add(assistantLabel("srcCompat"), BorderLayout.NORTH)
      add(sampleDataSetComboBox)
      add(useAllCheckBox, BorderLayout.EAST)
      border = Borders.emptyBottom(2)
    }
  }

  private fun createBottomBar() = Box.createHorizontalBox().apply {
    border = Borders.emptyTop(4)
    add(itemNameLabel)
    add(Box.createHorizontalGlue())
    add(ClickableLabel("Browse").apply {
      border = null
      isOpaque = false
      foreground = JBUI.CurrentTheme.Link.Foreground.ENABLED
      addActionListener { pickFromResourceDialog() }
    })
  }

  private fun createDrawableGrid() = DrawableGrid(nlComponent.model.facet.module,
                                                  DefaultListModel<ResourceValue>(),
                                                  IMAGE_SIZE,
                                                  ITEM_COUNT.toLong()).apply {
    isOpaque = false
    isEnabled = originalValue != null && !isSampleValueAll(originalValue)
    visibleRowCount = 3
    addListSelectionListener { _ ->
      applySampleItem(selectedSampleItem, if (useAll) -1 else selectedIndex)
    }
  }

  private fun createUseAllCheckBox() = JBCheckBox("Use as set").apply {
    setAssistantFont(this@apply)
    isSelected = useAll
    isOpaque = false
    addItemListener { event -> useAll = (event.source as JBCheckBox).isSelected }
  }

  private fun updateComboBox(sampleItems: List<SampleDataResourceItem>) {
    val sampleItemsWithNull = listOf(null) + sampleItems
    val elements = sampleItemsWithNull.map { it?.name ?: NONE_VALUE }
    val selectedIndex = elements.indexOfFirst { originalValue?.contains(it) ?: false }.coerceAtLeast(0)

    comboBoxModel.removeAllElements()
    sampleItemsWithNull.forEach { comboBoxModel.addElement(SampleDataSetItem(it)) }
    sampleDataSetComboBox.selectedIndex = selectedIndex
    selectedSampleItem = sampleItemsWithNull[selectedIndex]

    sampleDataSetComboBox.isEnabled = true
  }

  private fun updateUIState() {
    if (selectedSampleItem == null) {
      drawableGrid.isEnabled = false
      useAllCheckBox.isEnabled = false
    }
    else {
      drawableGrid.isEnabled = true && !useAll
      useAllCheckBox.isEnabled = true
    }
  }

  private fun getSampleItemDisplayName(attributeValue: String?) = attributeValue?.substringAfterLast("/").orEmpty()

  private fun setSelectedSampleItem(item: SampleDataResourceItem?) {
    if (item == selectedSampleItem) {
      return
    }
    selectedSampleItem = item
    val selectedIndex = drawableGrid.selectedIndex
    displayResourceValues(item, selectedIndex)
    if (drawableGrid.selectedIndex == -1) { // -1 to account for the None value
      applySampleItem(selectedSampleItem, -1)
    }
  }

  private fun displayResourceValues(item: SampleDataResourceItem?, selectedIndex: Int) {
    val listModel = drawableGrid.model as DefaultListModel<ResourceValue>
    listModel.removeAllElements()
    drawableGrid.resetCache()
    item?.getDrawableResources()?.take(ITEM_COUNT)?.forEach {
      listModel.addElement(it)
    }
    while (listModel.size() < ITEM_COUNT) {
      listModel.addElement(null)
    }
    drawableGrid.selectedIndex = Math.min(selectedIndex, drawableGrid.model.size - 1)
  }

  private fun applySampleItem(item: SampleDataResourceItem?, resourceValueIndex: Int) {
    val useAll = resourceValueIndex < 0 || item == null
    val itemName = if (item != null) item.name + if (useAll) "" else "[${resourceValueIndex}]" else ""
    itemDisplayName = itemName
    updateUIState()
    imageHandler.setToolsSrc(nlComponent, item, resourceValueIndex)
  }

  private fun pickFromResourceDialog() {
    val model = nlComponent.model
    val tag = nlComponent.backend.tag
    val virtualFile = tag?.containingFile?.virtualFile
    val dialog = createResourcePickerDialog(
      dialogTitle = "Pick a Drawable",
      currentValue = null,
      facet = model.facet,
      resourceTypes = EnumSet.of(ResourceType.DRAWABLE, ResourceType.MIPMAP),
      defaultResourceType = null,
      showColorStateLists = true,
      showSampleData = true,
      showThemeAttributes = true,
      file = virtualFile
    )

    if (dialog.showAndGet()) {
      imageHandler.setToolsSrc(nlComponent, dialog.resourceName)
      context.doClose(false)
    }
  }
}

/**
 * Class for the SampleData ComboBox model, which uses [Any.toString] to display data in the ComboBox, this makes sure that it displays the
 * SampleData resource name.
 */
private data class SampleDataSetItem(val resource: SampleDataResourceItem?) {
  override fun toString(): String {
    return resource?.name ?: NONE_VALUE
  }
}