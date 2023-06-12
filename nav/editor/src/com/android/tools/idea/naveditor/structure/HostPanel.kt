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
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.stripPrefixFromId
import com.android.resources.ResourceFolderType
import com.android.support.FragmentTagUtil.isFragmentTag
import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.res.StudioResourceRepositoryManager
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
import com.intellij.ui.components.BrowserLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.Query
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import org.jetbrains.android.dom.AndroidResourceDomFileDescription.Companion.isFileInResourceFolderType
import org.jetbrains.android.dom.navigation.isNavHostFragment
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager
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

private const val NO_HOST_TEXT1 = "No NavHostFragments found"
private const val NO_HOST_TEXT2 = "This nav graph must be"
private const val NO_HOST_TEXT3 = "referenced from a"
private const val NO_HOST_TEXT4 = "NavHostFragment in a layout in"
private const val NO_HOST_TEXT5 = "order to be accessible."

private const val NO_HOST_LINK_TEXT = "Using Navigation Component"
private const val NO_HOST_LINK_TARGET = "https://developer.android.com/guide/navigation/navigation-getting-started#add-navhost"

private const val SPACING_1 = 6
private const val SPACING_2 = 24
private const val SPACING_TOTAL = SPACING_1 + SPACING_2

class HostPanel(private val surface: DesignSurface<*>) : AdtSecondaryPanel(CardLayout()) {

  private val asyncIcon = AsyncProcessIcon("find NavHostFragments")
  @VisibleForTesting
  val list = JBList<SmartPsiElementPointer<XmlTag>>(DefaultListModel())
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
    add(createEmptyPanel(), "EMPTY")
    cardLayout.show(this, "LOADING")

    list.emptyText.text = "No NavHostFragments found"

    list.background = secondaryPanelBackground
    if (GeneralSettings.getInstance().isSupportScreenReaders) {
      list.addFocusListener(object : FocusListener {
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
    list.addKeyListener(object : KeyAdapter() {
      override fun keyTyped(e: KeyEvent?) {
        if (e?.keyCode == VK_ENTER || e?.keyChar == '\n') {
          activate(list.selectedIndex)
        }
      }
    })
    surface.model?.facet?.let {
      val resourceRepository = StudioResourceRepositoryManager.getAppResources(it)
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

      val psi = AndroidPsiUtils.getPsiFileSafely(model.project, model.virtualFile) as? XmlFile ?: return@executeOnPooledThread
      surface.model?.project?.let { project ->
        (list.model as DefaultListModel).clear()
        DumbService.getInstance(project).waitForSmartMode()
        doLoad(psi)
      }
    }
  }

  private fun doLoad(psiFile: XmlFile) {
    val cancellableAction = {
      val listModel = list.model as DefaultListModel
      listModel.clear()
      val module = surface.model?.module
      if (module != null) {
        val newReferences = findReferences(psiFile, module).map { SmartPointerManager.createPointer(it) }
        listModel.addAll(newReferences)
      }
      val name = if (list.model.size == 0) {
        "EMPTY"
      }
      else {
        "LIST"
      }
      cardLayout.show(this, name)
    }
    repeat(1000) {
      if (ProgressManager.getInstance().runInReadActionWithWriteActionPriority(cancellableAction, EmptyProgressIndicator())) {
        return
      }
      Thread.sleep(10)
    }
  }
}

@VisibleForTesting
fun findReferences(psi: XmlFile, module: Module): List<XmlTag> {
  ProgressManager.checkCanceled()
  val result = mutableListOf<XmlTag>()
  val query: Query<PsiReference> = ReferencesSearch.search(psi)
  for (ref: PsiReference in query) {
    ProgressManager.checkCanceled()
    val element = ref.element as? XmlAttributeValue ?: continue
    val file = element.containingFile as? XmlFile ?: continue
    if (!isFileInResourceFolderType(file, ResourceFolderType.LAYOUT)) {
      continue
    }
    val attribute = element.parent as? XmlAttribute ?: continue
    if (attribute.localName != ATTR_NAV_GRAPH || attribute.namespace != ResourceNamespace.TODO().xmlNamespaceUri) {
      continue
    }
    val tag = attribute.parent
    if (!isFragmentTag(tag.name)) {
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

private fun createEmptyPanel(): Component {
  val panel = AdtSecondaryPanel(HostPanelLayoutManager())

  addLabel(NO_HOST_TEXT1, panel)
  addLabel(NO_HOST_TEXT2, panel)
  addLabel(NO_HOST_TEXT3, panel)
  addLabel(NO_HOST_TEXT4, panel)
  addLabel(NO_HOST_TEXT5, panel)

  panel.add(BrowserLink(NO_HOST_LINK_TEXT, NO_HOST_LINK_TARGET))

  return panel
}

private fun addLabel(text: String, container: Container) {
  val label = JBLabel(text)
  label.isEnabled = false
  container.add(label)
}

private class HostPanelLayoutManager : LayoutManager {
  override fun layoutContainer(parent: Container) {
    val insets = parent.insets
    val width = parent.width - insets.left - insets.right
    val height = parent.height - insets.top - insets.bottom
    val components = parent.components

    for (component in components) {
      component.size = component.preferredSize
    }

    val totalComponentHeight = components.sumOf { it.preferredSize.height } + SPACING_TOTAL
    var y = (insets.top + 0.45 * (height - totalComponentHeight)).toInt()

    for ((i, component) in components.withIndex()) {
      val x = insets.left + (width - component.width) / 2
      component.setLocation(x, y)

      y += component.height + when (i) {
        0 -> SPACING_1
        4 -> SPACING_2
        else -> 0
      }
    }
  }

  override fun preferredLayoutSize(parent: Container): Dimension {
    return minimumLayoutSize(parent)
  }

  override fun minimumLayoutSize(parent: Container): Dimension {
    val width = ((parent.components.map { it.preferredSize.width }.maxOrNull() ?: 0) / 0.7).toInt()
    val height = parent.components.sumOf { it.preferredSize.height } + SPACING_TOTAL
    return Dimension(width, height)
  }

  override fun addLayoutComponent(name: String, comp: Component) {}
  override fun removeLayoutComponent(comp: Component) {}
}


