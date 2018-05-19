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
package com.android.tools.idea.naveditor.structure

import com.android.SdkConstants.*
import com.android.annotations.VisibleForTesting
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.Query
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import org.jetbrains.android.dom.layout.LayoutDomFileDescription
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.DefaultListSelectionModel
import javax.swing.JList

class HostPanel(private val surface: NavDesignSurface) : AdtSecondaryPanel(CardLayout()) {

  private val asyncIcon = AsyncProcessIcon("find NavHostFragments")
  private val model = DefaultListModel<XmlTag>()
  private val list = JBList<XmlTag>(model)
  private val cardLayout = layout as CardLayout

  init {
    val loadingPanel = AdtSecondaryPanel(BorderLayout())
    val loadingSubPanel = AdtSecondaryPanel()
    loadingPanel.border = JBUI.Borders.emptyLeft(5)
    loadingSubPanel.add(asyncIcon)
    val loadingLabel = JBLabel("Loading...")
    loadingLabel.isEnabled = false
    loadingSubPanel.add(loadingLabel)
    loadingPanel.add(loadingSubPanel, BorderLayout.WEST)

    val errorLabel = JBLabel("Error finding host activity")
    errorLabel.isEnabled = false
    val errorPanel = AdtSecondaryPanel(BorderLayout())
    errorPanel.add(errorLabel, BorderLayout.WEST)
    errorPanel.border = JBUI.Borders.emptyLeft(5)

    add(loadingPanel, "LOADING")
    add(list, "LIST")
    add(errorLabel, "ERROR")
    cardLayout.show(this, "LOADING")

    list.emptyText.text = "No NavHostFragments found"
    list.selectionModel = object : DefaultListSelectionModel() {
      override fun setAnchorSelectionIndex(anchorIndex: Int) {
      }

      override fun setSelectionInterval(index0: Int, index1: Int) {
      }

      override fun setLeadSelectionIndex(leadIndex: Int) {
      }
    }

    list.cellRenderer = object : ColoredListCellRenderer<XmlTag>() {
      override fun customizeCellRenderer(list: JList<out XmlTag>,
                                         value: XmlTag?,
                                         index: Int,
                                         selected: Boolean,
                                         hasFocus: Boolean) {
        if (value == null) {
          return
        }
        icon = StudioIcons.NavEditor.Tree.ACTIVITY
        append(FileUtil.getNameWithoutExtension(value.containingFile.name))
        append(" (${NlComponent.stripId(value.getAttributeValue(ATTR_ID, ANDROID_URI)) ?: "no id"})")
      }
    }
    list.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.clickCount == 2) {
          val index = list.locationToIndex(e.point)
          if (index != -1) {
            FileEditorManager.getInstance(surface.project).openFile(list.model.getElementAt(index).containingFile.virtualFile, true)
          }
        }
      }
    })

    startLoading()
  }

  private fun startLoading() {
    ApplicationManager.getApplication().executeOnPooledThread {
      val psi = surface.model?.file
      if (psi == null) {
        cardLayout.show(this, "ERROR")
        return@executeOnPooledThread
      }

      ProgressManager.getInstance().executeProcessUnderProgress(
        {
          ApplicationManager.getApplication().runReadAction(
            {
              for (tag in findReferences(psi)) {
                model.addElement(tag)
              }
            })
        }, EmptyProgressIndicator())
      cardLayout.show(this, "LIST")
    }
  }

  @VisibleForTesting
  fun findReferences(psi: XmlFile): List<XmlTag> {
    val result = mutableListOf<XmlTag>()
    val query: Query<PsiReference> = ReferencesSearch.search(psi)
    for (ref: PsiReference in query) {
      val element = ref.element as? XmlAttributeValue ?: continue
      val file = element.containingFile as? XmlFile ?: continue
      if (!LayoutDomFileDescription.isLayoutFile(file)) {
        continue
      }
      val attribute = element.parent as? XmlAttribute ?: continue
      if (attribute.localName != ATTR_NAV_GRAPH || attribute.namespace != ResourceNamespace.TODO().xmlNamespaceUri) {
        continue
      }
      val tag = attribute.parent
      if (tag.name != VIEW_FRAGMENT) {
        continue
      }
      val className = tag.getAttributeValue(ATTR_NAME, ANDROID_URI)
      if (className != FQCN_NAV_HOST_FRAGMENT) {
        continue
      }
      result.add(tag)
    }
    return result
  }
}