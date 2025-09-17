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
package com.android.tools.idea.npw.assetstudio.ui

import com.android.ide.common.rendering.api.SessionParams
import com.android.ide.common.vectordrawable.VdIcon
import com.android.resources.ResourceType
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.localization.MessageBundleReference
import com.android.tools.idea.material.icons.MaterialSymbolsLoader
import com.android.tools.idea.material.icons.MaterialSymbolsLoader.Companion.getMaterialSymbolsFontsAndMetadata
import com.android.tools.idea.material.icons.common.SymbolConfiguration
import com.android.tools.idea.material.icons.common.Symbols
import com.android.tools.idea.material.icons.metadata.MaterialIconsMetadata
import com.android.tools.idea.npw.assetstudio.assets.MaterialSymbolsVirtualFile
import com.android.tools.idea.ui.resourcemanager.plugin.LayoutRenderOptions
import com.android.tools.idea.ui.resourcemanager.rendering.AssetPreviewManagerImpl
import com.android.tools.idea.ui.resourcemanager.rendering.ImageCache
import com.android.tools.idea.ui.resourcemanager.rendering.SlowResourcePreviewManager
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.hover.TableHoverListener
import com.intellij.ui.table.JBTable
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ItemEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.ListSelectionModel
import javax.swing.event.ChangeEvent
import javax.swing.event.DocumentEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.table.AbstractTableModel
import kotlin.collections.get
import kotlin.math.min
import kotlin.reflect.KClass
import kotlinx.coroutines.launch
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE_NAME = "messages.SymbolPickerBundle"

private const val COLUMN_COUNT: Int = 6
private const val EXPECTED_NUMBER_OF_ICONS: Int = 6000
private const val ICON_HEIGHT = 64
private const val TEXT_HEIGHT = 16
private const val MAX_CACHE_SIZE = 2048

class SymbolPickerDialog(facet: AndroidFacet, parentDisposable: Disposable) :
  DialogWrapper(false), DataProvider, Disposable {

  // The following arrays are the possible values for the visual customizations of Material Symbols
  // Check out https://fonts.google.com/icons/ for more details
  private val weightSliderValues = arrayOf(100, 200, 300, 400, 500, 600, 700)
  private val gradeSliderValues = arrayOf(-25, 0, 200)
  private val opticalSizeSliderValues = arrayOf(20, 24, 40, 48)
  private val categoriesBoxNameMap: MutableMap<String, String> = HashMap(EXPECTED_NUMBER_OF_ICONS)

  private val loadingPanel = JBLoadingPanel(BorderLayout(), myDisposable)
  private val contentPanel = JPanel()
  private val searchField = SearchTextField(false)
  private val categoriesBox = ComboBox<String>()
  private val stylesBox = ComboBox<String>()
  private val weightSlider = JSlider(0, weightSliderValues.size - 1)
  private val gradeSlider = JSlider(0, gradeSliderValues.size - 1)
  private val opticalSizeSlider = JSlider(0, opticalSizeSliderValues.size - 1)
  private val refreshButton = JButton(AllIcons.General.Refresh)
  private val filledCheckBox = JCheckBox()
  private val iconsPanel = JPanel()
  private val licensePanel = JPanel()

  private object SymbolsBundle {
    private val bundleRef = MessageBundleReference(BUNDLE_NAME)

    fun message(@PropertyKey(resourceBundle = BUNDLE_NAME) key: String, vararg params: Any) =
      bundleRef.message(key, *params)
  }

  private val weightLabel = JLabel(SymbolsBundle.message("label.weight").format(400))
  private val gradeLabel = JLabel(SymbolsBundle.message("label.grade").format(0))
  private val opticalSizeLabel = JLabel(SymbolsBundle.message("label.optical_size").format(24))

  init {
    super.init()
    setupUI()
    title = SymbolsBundle.message("title")
    Disposer.register(parentDisposable, this)
  }

  private val coroutineScope = this.createCoroutineScope()

  private var time: Long = 0

  private lateinit var selectedIcon: VdIcon

  private var layoutIconList: List<MaterialSymbolsVirtualFile> = ArrayList(EXPECTED_NUMBER_OF_ICONS)
    set(value) {
      field = value
      updateFilter()
    }

  private var filteredSymbolList: MutableList<MaterialSymbolsVirtualFile> =
    ArrayList(EXPECTED_NUMBER_OF_ICONS)
  private var iconListMap: MutableMap<SymbolConfiguration, List<MaterialSymbolsVirtualFile>> =
    HashMap()
  private var metadata: MaterialIconsMetadata = MaterialIconsMetadata.EMPTY
    set(value) {
      field = value
      setCategoriesBoxModel()
      iconListMap.clear()
      imageCache.clear()
      updateIconList()
    }

  private val disposable = Disposer.newDisposable("SketchImporter")
  private val imageCache = ImageCache.createImageCache(disposable, null)
  private val resourceResolver =
    ConfigurationManager.getOrCreateInstance(facet.module)
      .getConfiguration(facet.module.project.projectFile!!)
      .getResourceResolver()

  // The default panel color in darcula mode is too dark given that our icons are all black.
  // We provide a lighter color for higher contrast.
  private val iconBackgroundColor: Color = UIUtil.getListBackground()

  private val placeholderImage =
    ImageUtil.createImage(80, 80, BufferedImage.TYPE_INT_ARGB).apply {
      with(createGraphics()) {
        this.color = iconBackgroundColor
        fillRect(0, 0, 80, 80)
        dispose()
      }
    }
  private val renderingOptions =
    LayoutRenderOptions(SessionParams.RenderingMode.SHRINK, true, transparentBackground = true)
  private val assetPreviewManager =
    AssetPreviewManagerImpl(
      facet,
      imageCache,
      resourceResolver,
      null,
      renderingOptions,
      placeholderImage,
    )
  private val layoutRenderer =
    IconPickerCellLayoutRenderer(
      assetPreviewManager.getPreviewProvider(ResourceType.LAYOUT) as SlowResourcePreviewManager
    )

  private val layoutModel = TableModel(MaterialSymbolsVirtualFile::class, filteredSymbolList)

  private val iconTable = JBTable()

  init {
    setupTable()
    ensureFontsAndMetadataAreDownloaded(false)

    setStylesBoxModel()
    setCategoriesBoxModel()

    val stylesBoxListener = { e: ItemEvent ->
      if (e.getStateChange() != ItemEvent.DESELECTED && e.getItem() != null) {
        val categoryCurrentIndex: Int = categoriesBox.getSelectedIndex()
        setCategoriesBoxModel()
        updateIconList()
        layoutModel.fireTableDataChanged()
        if (categoryCurrentIndex >= 0 && categoryCurrentIndex < categoriesBox.itemCount) {
          categoriesBox.setSelectedIndex(categoryCurrentIndex)
        }
      }
    }

    val categoriesBoxListener = { e: ItemEvent ->
      if (e.getStateChange() != ItemEvent.DESELECTED && e.getItem() != null) {
        updateFilter()
      }
    }

    stylesBox.addItemListener(stylesBoxListener)
    categoriesBox.addItemListener(categoriesBoxListener)
  }

  private fun setStylesBoxModel() {
    val collectionComboBoxModel =
      CollectionComboBoxModel(Symbols.entries.map { it.displayName }, Symbols.OUTLINED.displayName)
    stylesBox.setModel(collectionComboBoxModel)
    stylesBox.isVisible = true
  }

  private fun setCategoriesBoxModel() {
    categoriesBox.isVisible = true
    val items = metadata.categories.toMutableList()
    categoriesBoxNameMap.clear()
    items.forEach { item -> categoriesBoxNameMap[item.replaceFirstChar { it.uppercase() }] = item }
    items.sort()
    items.add(0, SymbolsBundle.message("categories.all"))
    val collectionComboBoxModel =
      CollectionComboBoxModel(items, SymbolsBundle.message("categories.all"))
    categoriesBox.model = collectionComboBoxModel
  }

  private fun selectVdIcon(icon: MaterialSymbolsVirtualFile) {
    isOKActionEnabled = false

    coroutineScope.launch {
      val vdIcon =
        MaterialSymbolsLoader.loadVdIcon(icon.symbolConfiguration, icon.metadata, metadata)
      selectedIcon = vdIcon
      isOKActionEnabled = true
    }
  }

  /**
   * Function that updates the complete list of loaded Material Symbols, with the currently selected
   * [SymbolConfiguration], based on the loaded [MaterialIconsMetadata]
   *
   * It employs a cache that stores all renders of Material Symbols on a per-configuration basis,
   * when it gets too large, it gets cleared. This is to facilitate quick and inexpensive
   * back-and-forth switches between configurations if the user is looking at mostly the same
   * symbols.
   *
   * It gets triggered whenever a configuration option is modified, or when the [metadata] gets
   * updated
   */
  private fun updateIconList() {
    val style = Symbols.getInstance(stylesBox.selectedItem as String)
    val symbolConfiguration =
      SymbolConfiguration(
        Symbols.getInstance(stylesBox.selectedItem as String),
        weightSliderValues[weightSlider.value],
        gradeSliderValues[gradeSlider.value],
        opticalSizeSliderValues[opticalSizeSlider.value],
        filledCheckBox.isSelected,
      )
    val mapResult = iconListMap[symbolConfiguration]

    if (mapResult != null) {
      layoutIconList = mapResult
      return
    }

    if (iconListMap.values.sumOf { it.size } > MAX_CACHE_SIZE) {
      iconListMap.clear()
    }
    if (imageCache.size() > MAX_CACHE_SIZE) {
      imageCache.clear()
    }

    layoutIconList =
      metadata.icons
        .filter { !it.unsupportedFamilies.contains(style.displayName) }
        .map { MaterialSymbolsVirtualFile(symbolConfiguration, it, JBColor.WHITE) }

    iconListMap[symbolConfiguration] = layoutIconList
    iconTable.emptyText.text = SymbolsBundle.message("table.empty")
  }

  /**
   * Function that updates the displayed list of Material Symbols, based on the currently selected
   * filter options and the [layoutIconList]. After filtering, it triggers the required table
   * updates in order to display what is required
   *
   * This is triggered on [searchField], [categoriesBox] and [layoutIconList] update
   */
  private fun updateFilter() {
    time = System.currentTimeMillis()
    filteredSymbolList.clear()
    val filtered =
      layoutIconList
        .filter {
          categoriesBox.selectedItem == SymbolsBundle.message("categories.all") ||
            it.metadata.categories.contains(categoriesBoxNameMap[categoriesBox.selectedItem])
        }
        .filter { it.metadata.name.contains(searchField.text) }
    filteredSymbolList.addAll(filtered)
    layoutModel.fireTableDataChanged()

    if (filteredSymbolList.isEmpty()) {
      iconTable.emptyText.text = SymbolsBundle.message("table.empty")
    }

    pack()
    repaint()
  }

  override fun createCenterPanel(): JComponent {
    return loadingPanel
  }

  override fun getData(dataId: @NonNls String): Any? {
    return if (SearchTextField.KEY.`is`(dataId)) searchField else null
  }

  fun getSelectedIcon(): VdIcon {
    return selectedIcon
  }

  /**
   * Function that, if required, downloads missing font files and metadata to the Sdk
   *
   * @param forceMetadataDownload even though some automatic checks for updates are in-place, we
   *   allow the user to manually trigger a redownload through the [refreshButton]
   */
  private fun ensureFontsAndMetadataAreDownloaded(forceMetadataDownload: Boolean) {
    coroutineScope.launch {
      loadingPanel.startLoading()
      try {
        getMaterialSymbolsFontsAndMetadata(
          coroutineScope,
          forceMetadataDownload,
          ({ metadata = it }),
        )
      } catch (e: Exception) {
        println("Error: " + e.message)
      } finally {
        loadingPanel.stopLoading()
      }
    }
  }

  private fun setupTable() {
    iconsPanel.add(JBScrollPane(iconTable))

    // Setup table visual properties and selection mode
    iconTable.background = iconBackgroundColor
    iconTable.tableHeader = null
    iconTable.rowHeight = JBUI.scale(ICON_HEIGHT + TEXT_HEIGHT)
    iconTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    iconTable.setCellSelectionEnabled(true)

    // Remove events on typing for the table, search box not affected
    TableHoverListener.DEFAULT.removeFrom(iconTable)
    iconTable.addKeyListener(
      object : KeyAdapter() {
        override fun keyTyped(e: KeyEvent) {
          val keyChar = e.getKeyChar()
          if (Character.isLetter(keyChar) || Character.isDigit(keyChar)) {
            searchField.text = keyChar.toString()
            searchField.requestFocus()
          }
          super.keyPressed(e)
        }
      }
    )

    // Setup table model with expected data type and selection models
    iconTable.model = layoutModel
    iconTable.setDefaultRenderer(MaterialSymbolsVirtualFile::class.java, layoutRenderer)
    val rowSelModel = iconTable.selectionModel
    val colSelModel = iconTable.columnModel.selectionModel

    iconTable.getColumnModel().columnSelectionAllowed = true
    iconTable.setGridColor(iconBackgroundColor)
    iconTable.setIntercellSpacing(JBUI.size(3, 3))
    iconTable.setRowMargin(0)

    // Add listeners for selecting icons
    val listListener = { e: ListSelectionEvent ->
      if (!e.valueIsAdjusting) {
        val row = iconTable.selectedRow
        val col = iconTable.selectedColumn
        val icon = if (row != -1 && col != -1) iconTable.getValueAt(row, col) else null
        if (icon != null) {
          selectVdIcon(icon as MaterialSymbolsVirtualFile)
        }
      }
    }

    rowSelModel.addListSelectionListener(listListener)
    colSelModel.addListSelectionListener(listListener)

    rowSelModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
    rowSelModel.setSelectionInterval(0, 0)
    iconTable.setColumnSelectionInterval(0, 0)
    iconTable.requestFocusInWindow()

    // Register the panel & dialog with the DataManager
    DataManager.registerDataProvider(contentPanel, this)
    val action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND)
    if (action != null) {
      SearchTextField.FindAction()
        .registerCustomShortcutSet(action.shortcutSet, rootPane, myDisposable)
    }

    // Set visual properties for initialized components
    contentPanel.preferredSize = JBUI.size(800, 600)

    iconTable.emptyText.text = SymbolsBundle.message("table.loading")
    stylesBox.isVisible = false
    stylesBox.name = SymbolsBundle.message("styles.name")
    categoriesBox.isVisible = false
    categoriesBox.name = SymbolsBundle.message("categories.name")

    // Add listeners for updating the variable properties of the Material Symbols, which will update
    // the icons list when triggered
    weightSlider.addChangeListener { e: ChangeEvent ->
      val source = e.source as JSlider
      if (!source.valueIsAdjusting) {
        updateIconList()
        weightLabel.text =
          SymbolsBundle.message("label.weight").format(weightSliderValues[weightSlider.value])
      }
    }
    gradeSlider.addChangeListener { e: ChangeEvent ->
      val source = e.source as JSlider
      if (!source.valueIsAdjusting) {
        updateIconList()
        gradeLabel.text =
          SymbolsBundle.message("label.grade").format(gradeSliderValues[gradeSlider.value])
      }
    }
    opticalSizeSlider.addChangeListener { e: ChangeEvent ->
      val source = e.source as JSlider
      if (!source.valueIsAdjusting) {
        updateIconList()
        opticalSizeLabel.text =
          SymbolsBundle.message("label.optical_size")
            .format(opticalSizeSliderValues[opticalSizeSlider.value])
      }
    }

    filledCheckBox.addItemListener {
      time = System.currentTimeMillis()
      updateIconList()
    }

    // Add listeners for the refresh button and the search field
    refreshButton.addActionListener { ensureFontsAndMetadataAreDownloaded(true) }

    searchField.addDocumentListener(
      object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
          updateFilter()
        }
      }
    )
  }

  private fun setupUI() {
    loadingPanel.setLoadingText(SymbolsBundle.message("table.downloading"))
    loadingPanel.add(contentPanel, BorderLayout.CENTER)

    contentPanel.setLayout(BorderLayout(20, 10))
    val panel1 = JPanel()
    panel1.setLayout(GridBagLayout())
    contentPanel.add(panel1, BorderLayout.NORTH)

    // add top row with the searchField, stylesBox and categoriesBox
    panel1.add(searchField, gridConstraintsHelper(0, 0, 2.0))
    panel1.add(stylesBox, gridConstraintsHelper(1, 0, 1.0))
    categoriesBox.minimumSize = Dimension(140, 30)
    panel1.add(categoriesBox, gridConstraintsHelper(2, 0, 1.0))

    // Add the labels to the three sliders for customizing the visual properties of the material
    // symbols to the first row of the slidersPanel
    val slidersPanel = JPanel(GridBagLayout())
    listOf(weightLabel, gradeLabel, opticalSizeLabel).forEachIndexed { index, label ->
      slidersPanel.add(
        label,
        gridConstraintsHelper(index, 0, 1.0, insets = JBUI.insetsRight(JBUI.scale(8))),
      )
    }

    // Add the sliders themselves to the second row of the slidersPanel
    listOf(weightSlider, gradeSlider, opticalSizeSlider).forEachIndexed { index, slider ->
      slider.setMajorTickSpacing(1)
      slider.setSnapToTicks(true)
      slider.setPaintTicks(true)

      slidersPanel.add(
        slider,
        gridConstraintsHelper(index, 1, 1.0, insets = JBUI.insetsRight(JBUI.scale(8))),
      )
    }

    // Add the filled checkbox and forced refresh button to the right of the 1st and 2nd rows of the
    // sliderPanel
    filledCheckBox.text = SymbolsBundle.message("label.filled")
    slidersPanel.add(
      filledCheckBox,
      gridConstraintsHelper(3, 1, 0.0, fill = GridBagConstraints.NONE, insets = JBUI.emptyInsets()),
    )
    slidersPanel.add(
      refreshButton,
      gridConstraintsHelper(3, 0, 0.0, fill = GridBagConstraints.NONE, insets = JBUI.emptyInsets()),
    )

    // Add the sliders panel to the main component of the contentPanel
    panel1.add(
      slidersPanel,
      gridConstraintsHelper(
        0,
        1,
        1.0,
        fill = GridBagConstraints.HORIZONTAL,
        insets = JBUI.insetsTop(JBUI.scale(5)),
        gridWidth = 3,
      ),
    )

    iconsPanel.setLayout(BorderLayout(0, 0))
    iconsPanel.minimumSize = Dimension(300, 400)
    contentPanel.add(iconsPanel, BorderLayout.CENTER)
    licensePanel.setLayout(GridLayoutManager(1, 1, JBUI.emptyInsets(), -1, -1))
    contentPanel.add(licensePanel, BorderLayout.SOUTH)
    val licenseLabel = HyperlinkLabel()
    licenseLabel.setTextWithHyperlink(SymbolsBundle.message("label.license"))
    licenseLabel.setHyperlinkTarget("http://www.apache.org/licenses/LICENSE-2.0.txt")

    licensePanel.add(
      licenseLabel,
      GridConstraints(
        0,
        0,
        1,
        1,
        GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
        null,
        null,
        null,
        0,
        false,
      ),
    )
  }

  private fun gridConstraintsHelper(
    gridx: Int,
    gridy: Int,
    weightx: Double,
    fill: Int = GridBagConstraints.HORIZONTAL,
    anchor: Int = GridBagConstraints.WEST,
    insets: Insets? = null,
    gridWidth: Int? = null,
  ): GridBagConstraints {
    val gbc = GridBagConstraints()
    gbc.gridx = gridx
    gbc.gridy = gridy
    gbc.weightx = weightx
    gbc.fill = fill
    gbc.anchor = anchor
    if (insets != null) gbc.insets = insets
    if (gridWidth != null) gbc.gridwidth = gridWidth
    return gbc
  }

  /** Must override to keep visibility modifier from defaulting to private */
  override fun dispose() = super.dispose()

  class TableModel<T : Any>(
    private val columnClass: KClass<T>,
    private val myFilteredIconList: MutableList<T>,
  ) : AbstractTableModel() {
    override fun getColumnName(column: Int): String? {
      return null
    }

    override fun getColumnClass(columnIndex: Int): Class<*> {
      return columnClass.java
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
      val index = rowIndex * COLUMN_COUNT + columnIndex
      if (index < 0) {
        return null
      }
      return if (myFilteredIconList.size > index) myFilteredIconList[index] else null
    }

    override fun getRowCount(): Int {
      return myFilteredIconList.size / COLUMN_COUNT + min(1, myFilteredIconList.size % COLUMN_COUNT)
    }

    override fun getColumnCount(): Int {
      return COLUMN_COUNT
    }
  }
}
