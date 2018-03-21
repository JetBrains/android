// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.naveditor.editor

import com.android.annotations.VisibleForTesting
import com.android.resources.ResourceFolderType
import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.idea.naveditor.scene.NavColorSet
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.res.ResourceNotificationManager
import com.google.common.collect.ImmutableList
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.DottedBorder
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.speedSearch.FilteringListModel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import org.jetbrains.android.AndroidGotoRelatedProvider
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.resourceManagers.LocalResourceManager
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.event.DocumentEvent

/**
 * "Add" popup menu in the navigation editor.
 */
// open for testing only
@VisibleForTesting
open class AddDestinationMenu(surface: NavDesignSurface) :
    NavToolbarMenu(surface, "New Destination", StudioIcons.NavEditor.Toolbar.ADD_DESTINATION) {

  private lateinit var myButton: JComponent
  private var creatingInProgress = false

  @VisibleForTesting
  val destinations: List<Destination>
    get() {
      val model = surface.model!!
      val classToDestination = LinkedHashMap<PsiClass, Destination>()
      val module = model.module
      val schema = surface.schema

      val scope = GlobalSearchScope.moduleWithDependenciesScope(module)
      val project = model.project
      val parent = surface.currentNavigation
      for (superClassName in NavigationSchema.DESTINATION_SUPERCLASS_TO_TYPE.keys) {
        val psiSuperClass = JavaPsiFacade.getInstance(project).findClass(superClassName, GlobalSearchScope.allScope(project)) ?: continue
        val tag = schema.getTagForComponentSuperclass(superClassName) ?: continue
        val query = ClassInheritorsSearch.search(psiSuperClass, scope, true)
        for (psiClass in query) {
          val destination = Destination.RegularDestination(parent, tag, null, psiClass.name, psiClass.qualifiedName)
          classToDestination.put(psiClass, destination)
        }
      }

      val resourceManager = LocalResourceManager.getInstance(module) ?: return listOf()

      for (resourceFile in resourceManager.findResourceFiles(ResourceFolderType.LAYOUT).filterIsInstance<XmlFile>()) {
        // TODO: refactor AndroidGotoRelatedProvider so this can be done more cleanly
        val itemComputable = AndroidGotoRelatedProvider.getLazyItemsForXmlFile(resourceFile, model.facet)
        for (item in itemComputable?.compute() ?: continue) {
          val element = item.element as? PsiClass ?: continue
          val tag = schema.findTagForComponent(element) ?: continue
          val destination =
              Destination.RegularDestination(parent, tag, null, element.name, element.qualifiedName, layoutFile = resourceFile)
          classToDestination.put(element, destination)
        }
      }

      val result = classToDestination.values.toMutableList()

      for (navPsi in resourceManager.findResourceFiles(ResourceFolderType.NAVIGATION).filterIsInstance<XmlFile>()) {
        if (surface.model!!.file == navPsi) {
          continue
        }
        result.add(Destination.IncludeDestination(navPsi.name, parent))
      }

      return result
    }

  @VisibleForTesting
  lateinit var destinationsList: JBList<Destination>

  private var loadingPanel: JBLoadingPanel = JBLoadingPanel(BorderLayout(), surface)

  @VisibleForTesting
  var mySearchField = SearchTextField()

  private var _mainPanel: JPanel? = null

  override val mainPanel: JPanel
    get() {
      creatingInProgress = false
      return _mainPanel ?: createSelectionPanel().also { _mainPanel = it}
    }

  @VisibleForTesting
  lateinit var blankDestinationButton: ActionButtonWithText

  init {
    val listener = ResourceNotificationManager.ResourceChangeListener { _ -> _mainPanel = null }
    val notificationManager = ResourceNotificationManager.getInstance(surface.project)
    val facet = surface.model!!.facet
    notificationManager.addListener(listener, facet, null, null)
    Disposer.register(surface, Disposable { notificationManager.removeListener(listener, facet, null, null) })
  }

  private fun createSelectionPanel(): JPanel {
    val listModel = FilteringListModel<Destination>(CollectionListModel<Destination>(destinations))

    listModel.setFilter { destination -> destination.label.toLowerCase().contains(mySearchField.text.toLowerCase()) }
    @Suppress("UNCHECKED_CAST")
    destinationsList = JBList<Destination>(listModel as ListModel<Destination>)
    destinationsList.setCellRenderer { _, value, _, _, _ ->
      THUMBNAIL_RENDERER.icon = ImageIcon(value.thumbnail.getScaledInstance(JBUI.scale(50), JBUI.scale(64), Image.SCALE_SMOOTH))
      PRIMARY_TEXT_RENDERER.text = value.label
      SECONDARY_TEXT_RENDERER.text = value.typeLabel
      RENDERER
    }

    destinationsList.background = null
    destinationsList.addMouseMotionListener(
        object : MouseAdapter() {
          override fun mouseMoved(event: MouseEvent) {
            val index = destinationsList.locationToIndex(event.point)
            if (index != -1) {
              destinationsList.selectedIndex = index
              destinationsList.requestFocusInWindow()
            } else {
              destinationsList.clearSelection()
            }
          }
        }
    )

    val result = AdtSecondaryPanel(VerticalLayout(5))
    destinationsList.background = result.background
    result.add(mySearchField)

    val action: AnAction = object : AnAction("Create blank destination") {
      override fun actionPerformed(e: AnActionEvent?) {
        createBlankDestination()
      }
    }
    blankDestinationButton = ActionButtonWithText(action, action.templatePresentation, "Toolbar",  JBDimension(0, 45))
    val buttonPanel = AdtSecondaryPanel(BorderLayout())
    buttonPanel.border = CompoundBorder(JBUI.Borders.empty(1, 7), DottedBorder(JBUI.emptyInsets(), NavColorSet.FRAME_COLOR))
    buttonPanel.add(blankDestinationButton, BorderLayout.CENTER)
    mySearchField.addDocumentListener(
        object : DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
            listModel.refilter()
          }
        }
    )
    val scrollable = AdtSecondaryPanel(BorderLayout())
    scrollable.add(buttonPanel, BorderLayout.NORTH)
    scrollable.add(destinationsList, BorderLayout.CENTER)
    val scrollPane = JBScrollPane(scrollable)
    scrollPane.preferredSize = JBDimension(252, 300)
    scrollPane.border = BorderFactory.createEmptyBorder()
    val mediaTracker = MediaTracker(destinationsList)
    destinations.forEach { destination -> mediaTracker.addImage(destination.thumbnail, 0) }
    if (!mediaTracker.checkAll()) {
      loadingPanel.add(scrollPane, BorderLayout.CENTER)
      loadingPanel.startLoading()

      ApplicationManager.getApplication().executeOnPooledThread {
        try {
          mediaTracker.waitForAll()
          ApplicationManager.getApplication().invokeLater { loadingPanel.stopLoading() }
        } catch (e: Exception) {
          loadingPanel.setLoadingText("Failed to load thumbnails")
        }
      }

      result.add(loadingPanel)
    } else {
      result.add(scrollPane)
    }
    destinationsList.addMouseListener(
        object : MouseAdapter() {
          override fun mouseClicked(event: MouseEvent) {
            val element = destinationsList.selectedValue
            if (element != null) {
              addDestination(element)
            }
          }
        }
    )
    return result
  }

  @VisibleForTesting
  fun createBlankDestination() {
    addDestination(
        Destination.RegularDestination(
            surface.currentNavigation,
            surface.schema.getDefaultTag(NavigationSchema.DestinationType.FRAGMENT)!!
        )
    )
  }

  private fun addDestination(destination: Destination) {
    if (creatingInProgress) {
      return
    }
    creatingInProgress = true
    destination.addToGraph()
    // explicitly update so the new SceneComponent is created
    surface.sceneManager!!.update()
    val component = destination.component
    surface.selectionModel.setSelection(ImmutableList.of(component!!))
    surface.scrollToCenter(ImmutableList.of(component))
    balloon?.hide()
  }

  override fun createCustomComponent(presentation: Presentation): JComponent {
    myButton = super.createCustomComponent(presentation)
    return myButton
  }

  // open for testing only
  open fun show() {
    show(myButton)
  }

  companion object {

    private val RENDERER = AdtSecondaryPanel(BorderLayout())
    private val THUMBNAIL_RENDERER = JBLabel()
    private val PRIMARY_TEXT_RENDERER = JBLabel()
    private val SECONDARY_TEXT_RENDERER = JBLabel()

    init {
      SECONDARY_TEXT_RENDERER.foreground = NavColorSet.SUBDUED_TEXT_COLOR
      RENDERER.add(THUMBNAIL_RENDERER, BorderLayout.WEST)
      val leftPanel = JPanel(VerticalLayout(8))
      leftPanel.border = JBUI.Borders.empty(12, 6, 0, 0)
      leftPanel.add(PRIMARY_TEXT_RENDERER, VerticalLayout.CENTER)
      leftPanel.add(SECONDARY_TEXT_RENDERER, VerticalLayout.CENTER)
      RENDERER.add(leftPanel, BorderLayout.CENTER)
    }
  }
}
