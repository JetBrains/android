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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_NAV_GRAPH
import com.android.SdkConstants.VIEW_FRAGMENT
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.stripPrefixFromId
import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.res.ResourceRepositoryManager
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.GeneralSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiReference
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
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
import org.jetbrains.android.dom.navigation.isNavHostFragment
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.DefaultListSelectionModel
import javax.swing.JList

class HostPanel(private val surface: NavDesignSurface) : AdtSecondaryPanel(CardLayout()) {

  private val asyncIcon = AsyncProcessIcon("find NavHostFragments")
  @VisibleForTesting val list = JBList<SmartPsiElementPointer<XmlTag>>(DefaultListModel())
  private val cardLayout = layout as CardLayout
  private var resourceVersion = 0L

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
    list.background = secondaryPanelBackground
    if (GeneralSettings.getInstance().isSupportScreenReaders) {
      list.addFocusListener(object: FocusListener {
        override fun focusLost(e: FocusEvent?) {
          list.selectedIndices = intArrayOf()
        }

        override fun focusGained(e: FocusEvent?) {
          if (list.selectedIndices.isEmpty() && !list.isEmpty) {
            list.selectedIndex = 0
          }
        }
      })
    }
    else {
      list.selectionModel = object : DefaultListSelectionModel() {
        override fun setAnchorSelectionIndex(anchorIndex: Int) {
        }

        override fun setSelectionInterval(index0: Int, index1: Int) {
        }

        override fun setLeadSelectionIndex(leadIndex: Int) {
        }
      }
    }
    list.cellRenderer = object : ColoredListCellRenderer<SmartPsiElementPointer<XmlTag>>() {
      override fun customizeCellRenderer(list: JList<out SmartPsiElementPointer<XmlTag>>,
                                         value: SmartPsiElementPointer<XmlTag>?,
                                         index: Int,
                                         selected: Boolean,
                                         hasFocus: Boolean) {
        if (value == null) {
          return
        }
        icon = StudioIcons.NavEditor.Tree.ACTIVITY
        val containingFile = value.containingFile?.name ?: "Unknown File"
        append(FileUtil.getNameWithoutExtension(containingFile))
        append(" (${value.element?.getAttributeValue(ATTR_ID, ANDROID_URI)?.let(::stripPrefixFromId) ?: "no id"})")
      }
    }
    list.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.clickCount == 2) {
          activate(list.locationToIndex(e.point))
        }
      }
    })
    list.addKeyListener(object: KeyAdapter() {
      override fun keyTyped(e: KeyEvent?) {
        if (e?.keyCode == VK_ENTER || e?.keyChar == '\n') {
          activate(list.selectedIndex)
        }
      }
    })
    surface.model?.facet?.let {
      val resourceRepository = ResourceRepositoryManager.getAppResources(it)
      surface.model?.addListener(object : ModelListener {
        override fun modelActivated(model: NlModel) {
          val modCount = resourceRepository.modificationCount
          if (resourceVersion < modCount) {
            resourceVersion = modCount
            startLoading()
          }
        }
      })
    }

    startLoading()
  }

  private fun activate(index: Int) {
    if (index != -1) {
      val containingFile = list.model.getElementAt(index).containingFile
      if (containingFile != null) {
        FileEditorManager.getInstance(surface.project).openFile(containingFile.virtualFile, true)
      }
    }
  }

  private fun startLoading() {
    ApplicationManager.getApplication().executeOnPooledThread {
      val model = surface.model
      if (model == null) {
        cardLayout.show(this, "ERROR")
        return@executeOnPooledThread
      }
      val psi = model.file

      ProgressManager.getInstance().executeProcessUnderProgress(
        {
          surface.model?.project?.let { project ->
            val listModel = list.model as DefaultListModel
            listModel.clear()
            val module = surface.model?.module
            if (module != null) {
              DumbService.getInstance(project).runReadActionInSmartMode {
                findReferences(psi, module).forEach { listModel.addElement(SmartPointerManager.createPointer(it)) }
              }
            }
          }
          cardLayout.show(this, "LIST")
        }, EmptyProgressIndicator())
    }
  }
}

@VisibleForTesting
fun findReferences(psi: XmlFile, module: Module): List<XmlTag> {
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
    val className = tag.getAttributeValue(ATTR_NAME, ANDROID_URI) ?: continue
    if (!isNavHostFragment(className, module)) {
      continue
    }
    result.add(tag)
  }
  return result
}