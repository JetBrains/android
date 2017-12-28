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
import com.android.resources.ResourceEnum
import com.android.tools.adtui.flat.FlatButton
import com.android.tools.idea.resourceExplorer.viewmodel.QualifierLexerPresenter
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
class QualifierParserPanel(
    private val presenter: QualifierLexerPresenter)
  : JPanel(BorderLayout()) {

  private val qualifierEntries = JPanel(VerticalFlowLayout())

  init {
    preferredSize = JBUI.size(300, 500)
    background = JBColor.WHITE
    addQualifierEntry()
    add(ScrollPaneFactory.createScrollPane(qualifierEntries))
    add(createControlPanel(), BorderLayout.SOUTH)
    background = JBColor.DARK_GRAY
  }

  private fun createControlPanel(): JPanel {
    val controlPanel = JPanel()
    controlPanel.add(JButton("Add Qualifier").also { it.addActionListener { addQualifierEntry() } })
    controlPanel.add(JButton("Apply").also { it.addActionListener { updatePresenter() } })
    return controlPanel
  }

  private fun addQualifierEntry() {
    with(qualifierEntries) {
      add(MapperEntry(presenter))
      revalidate()
      repaint()
    }
  }

  private fun updatePresenter() {
    qualifierEntries.components
        .filterIsInstance<MapperEntry>()
        .map { it.getQualifier() to it.getMatcherEntries() }
        .let { presenter.setMatcherEntries(it) }
  }

  /**
   * Representation of a [com.android.tools.idea.resourceExplorer.importer.Mapper]
   */
  private class MapperEntry(val presenter: QualifierLexerPresenter) : JPanel(VerticalFlowLayout()) {

    private val matchers = JPanel(VerticalFlowLayout())
    private val qualifierCombo = createQualifierCombo()

    init {
      matchers.add(MatcherEntry(presenter, qualifierCombo))
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
          matchers.add(MatcherEntry(presenter, qualifierCombo))
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
        override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
          if (value is ResourceQualifier) {
            icon = DeviceConfiguratorPanel.getResourceIcon(value)
            text = value.name
          }
          return this
        }
      }
      return qualifierCombo
    }

    internal fun getMatcherEntries(): List<Pair<String, ResourceEnum>> {
      return matchers.components
          .filterIsInstance<MatcherEntry>()
          .map { it.getMatcherPair() }
    }

    internal fun getQualifier(): ResourceQualifier {
      return qualifierCombo.selectedItem as ResourceQualifier
    }

  }

  private class MatcherEntry(private val presenter: QualifierLexerPresenter, qualifierCombo: JComboBox<ResourceQualifier>) : JPanel(null) {

    private val matchString = JTextField()
    private val matchParameterCombo = createParameterCombo(qualifierCombo)

    init {
      val boxLayout = BoxLayout(this, BoxLayout.X_AXIS)
      layout = boxLayout
      add(matchString)
      add(matchParameterCombo)
      add(FlatButton(AllIcons.Actions.Delete).also {
        it.addActionListener {
          val grandParent = parent.parent
          parent.remove(this)
          grandParent.revalidate()
          grandParent.repaint()
        }
      })
    }

    private fun createParameterCombo(qualifierCombo: JComboBox<ResourceQualifier>): JComboBox<ResourceEnum> {
      val parameterCombo = ComboBox<ResourceEnum>()
      parameterCombo.model = DefaultComboBoxModel(presenter.getValuesForQualifier(qualifierCombo.selectedItem as ResourceQualifier))
      parameterCombo.renderer = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
          if (value is ResourceEnum) {
            text = value.longDisplayValue
          }
          return this
        }
      }
      qualifierCombo.addActionListener { event ->
        val box = event.source as JComboBox<*>
        val selectedQualifier = box.selectedItem as ResourceQualifier
        parameterCombo.model = DefaultComboBoxModel(presenter.getValuesForQualifier(selectedQualifier))
      }
      return parameterCombo
    }

    internal fun getMatcherPair() = matchString.text to matchParameterCombo.selectedItem as ResourceEnum
  }
}