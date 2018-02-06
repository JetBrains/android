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
package com.android.tools.idea.resourceExplorer.view

import com.android.ide.common.resources.configuration.ResourceQualifier
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.idea.resourceExplorer.model.StaticStringMapper
import com.android.tools.idea.resourceExplorer.model.MatcherEntry
import com.android.tools.idea.resourceExplorer.viewmodel.QualifierMatcherPresenter
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBUI
import org.jetbrains.android.uipreview.DeviceConfiguratorPanel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.*

/**
 * Panel to configure the qualifier parser.
 * This is mostly for test now and not a supposed to be a final version.
 */
class QualifierMatcherPanel(
  private val presenter: QualifierMatcherPresenter
) : JPanel(BorderLayout()) {

  private val qualifierEntries = JPanel(VerticalFlowLayout())

  init {
    preferredSize = JBUI.size(300, 500)
    background = JBColor.WHITE
    add(ScrollPaneFactory.createScrollPane(qualifierEntries))
    add(createControlPanel(), BorderLayout.SOUTH)
    background = JBColor.DARK_GRAY
    loadConfiguration(presenter.getConfiguration())
  }

  private fun loadConfiguration(mappers: Set<StaticStringMapper>) {
    if (mappers.isEmpty()) {
      addQualifierEntry()
    } else {
      var mapperEntryQualifier : ResourceQualifier? = null
      mappers.forEach { mapper ->
        val mapperEntryView = MapperEntryView(presenter)
        mapper.matchers.forEach { matchingString, matchedQualifier ->
          mapperEntryView.addMatcher(matchingString, matchedQualifier)
          if (mapperEntryQualifier == null) {
            mapperEntryQualifier = matchedQualifier
          }
        }
        mapperEntryQualifier?.let {
          mapperEntryView.setQualifier(it)
          qualifierEntries.add(mapperEntryView)
        }
      }
      updatePresenter()
    }
  }

  private fun createControlPanel(): JPanel {
    val controlPanel = JPanel()
    controlPanel.add(JButton("Add Qualifier").also { it.addActionListener { addQualifierEntry() } })
    controlPanel.add(JButton("Apply").also { it.addActionListener { updatePresenter() } })
    return controlPanel
  }

  private fun addQualifierEntry() {
    with(qualifierEntries) {
      val mapperEntry = MapperEntryView(presenter)
      mapperEntry.addMatcher("", mapperEntry.getQualifier())

      add(mapperEntry)
      revalidate()
      repaint()
    }
  }

  private fun updatePresenter() {
    qualifierEntries.components
      .filterIsInstance<MapperEntryView>()
      .map { it.getQualifier() to it.getMatcherEntries() }
      .let { presenter.setMatcherEntries(it) }
  }

  /**
   * Representation of an [com.android.tools.idea.resourceExplorer.model.StaticStringMapper]
   */
  private class MapperEntryView(val presenter: QualifierMatcherPresenter) : JPanel(VerticalFlowLayout()) {

    private val matchers = JPanel(VerticalFlowLayout())
    private val qualifierCombo = createQualifierCombo()

    init {
      val topPanel = JPanel(FlowLayout(FlowLayout.TRAILING))
      topPanel.add(JLabel("Mapper for: "))
      topPanel.add(qualifierCombo)
      topPanel.add(JButton("Remove").also {
        it.addActionListener {
          val grandParent = parent.parent
          parent.remove(this)
          grandParent.apply {
            repaint()
            revalidate()
          }
        }
      })
      add(topPanel)
      add(matchers)
      add(JButton("Add Matcher").also {
        it.addActionListener {
          matchers.add(MatcherEntryView(presenter, qualifierCombo, qualifierCombo.getItemAt(0)))
          revalidate()
          repaint()
        }
      })
    }

    private fun createQualifierCombo(): JComboBox<ResourceQualifier> {
      val qualifierCombo = ComboBox<ResourceQualifier>()
      qualifierCombo.model = DefaultComboBoxModel(presenter.getAvailableQualifiers().toTypedArray())
      qualifierCombo.model.selectedItem = qualifierCombo.model.getElementAt(0)
      qualifierCombo.renderer = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
          list: JList<*>?,
          value: Any?,
          index: Int,
          isSelected: Boolean,
          cellHasFocus: Boolean
        ): Component {
          if (value is ResourceQualifier) {
            icon = DeviceConfiguratorPanel.getResourceIcon(value)
            text = value.name
          }
          return this
        }
      }
      return qualifierCombo
    }

    internal fun getMatcherEntries(): List<MatcherEntry> {
      return matchers.components
        .filterIsInstance<MatcherEntryView>()
        .mapNotNull { it.getMatcherEntry() }
    }

    internal fun getQualifier(): ResourceQualifier {
      return qualifierCombo.selectedItem as ResourceQualifier
    }

    fun setQualifier(qualifier: ResourceQualifier) {
      val comboBoxModel = qualifierCombo.model
      for (i in 0 until comboBoxModel.size) {
        val itemQualifier = comboBoxModel.getElementAt(i)
        if (itemQualifier::class == qualifier::class) {
          qualifierCombo.selectedItem = itemQualifier
        }
      }
    }

    fun addMatcher(matchingString: String, matchedQualifier: ResourceQualifier) {
      matchers.add(MatcherEntryView(presenter, qualifierCombo, matchedQualifier, matchingString))
    }

  }

  private class MatcherEntryView(
    private val presenter: QualifierMatcherPresenter,
    qualifierCombo: JComboBox<ResourceQualifier>,
    defaultResourceEnum: ResourceQualifier,
    matchStringValue: String = ""
  ) :
    JPanel(null) {

    private val matchString = JTextField(matchStringValue)
    private val matchParameterCombo = createParameterCombo(qualifierCombo, defaultResourceEnum)

    init {
      val boxLayout = BoxLayout(this, BoxLayout.X_AXIS)
      layout = boxLayout
      add(matchString)
      add(matchParameterCombo)
      add(
        CommonButton(AllIcons.Actions.Delete).also {
          it.addActionListener {
            val grandParent = parent.parent
            parent.remove(this)
            grandParent.revalidate()
            grandParent.repaint()
          }
        })
    }

    private fun createParameterCombo(
      qualifierCombo: JComboBox<ResourceQualifier>,
      selectedQualifier: ResourceQualifier
    ): JComboBox<ResourceQualifier> {
      val parameterCombo = ComboBox<ResourceQualifier>()
      parameterCombo.model = DefaultComboBoxModel(presenter.getValuesForQualifier(qualifierCombo.selectedItem as ResourceQualifier)?.toTypedArray())
      parameterCombo.selectedItem = selectedQualifier
      parameterCombo.renderer = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
          list: JList<*>?,
          value: Any?,
          index: Int,
          isSelected: Boolean,
          cellHasFocus: Boolean
        ): Component {
          if (value is ResourceQualifier) {
            text = value.longDisplayValue
          }
          return this
        }
      }
      qualifierCombo.addActionListener { event ->
        val box = event.source as JComboBox<*>
        val selectedQualifier = box.selectedItem as ResourceQualifier
        parameterCombo.model = DefaultComboBoxModel(presenter.getValuesForQualifier(selectedQualifier)?.toTypedArray())
      }
      return parameterCombo
    }

    internal fun getMatcherEntry(): MatcherEntry? {
      val selectedItem = matchParameterCombo.selectedItem
      if (selectedItem is ResourceQualifier) {
        return MatcherEntry(matchString.text, selectedItem)
      }
      return null
    }
  }
}