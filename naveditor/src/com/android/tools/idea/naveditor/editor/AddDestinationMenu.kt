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

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceFolderType
import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.idea.actions.NewAndroidComponentAction
import com.android.tools.idea.actions.NewAndroidFragmentAction
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.naveditor.analytics.NavUsageTracker
import com.android.tools.idea.naveditor.model.className
import com.android.tools.idea.naveditor.model.includeFile
import com.android.tools.idea.naveditor.model.isInclude
import com.android.tools.idea.naveditor.model.schema
import com.android.tools.idea.naveditor.scene.NavColors.HIGHLIGHTED_FRAME
import com.android.tools.idea.naveditor.scene.NavColors.LIST_MOUSEOVER
import com.android.tools.idea.naveditor.scene.NavColors.SUBDUED_TEXT
import com.android.tools.idea.naveditor.scene.layout.NEW_DESTINATION_MARKER_PROPERTY
import com.android.tools.idea.naveditor.structure.findReferences
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.speedSearch.FilteringListModel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.isInProject
import org.jetbrains.android.resourceManagers.LocalResourceManager
import org.jetbrains.android.util.AndroidUtils
import java.awt.BorderLayout
import java.awt.event.HierarchyEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.ListModel
import javax.swing.SwingConstants
import javax.swing.border.CompoundBorder
import javax.swing.event.DocumentEvent

const val DESTINATION_MENU_MAIN_PANEL_NAME = "destinationMenuMainPanel"

/**
 * "Add" popup menu in the navigation editor.
 */
// open for testing only
@VisibleForTesting
open class AddDestinationMenu(surface: NavDesignSurface) :
  NavToolbarMenu(surface, "New Destination", StudioIcons.NavEditor.Toolbar.ADD_DESTINATION) {

  private lateinit var button: JComponent
  private var creatingInProgress = false
  private val createdFiles: MutableList<File> = mutableListOf()

  @VisibleForTesting
  val destinations: List<Destination>
    get() {
      val model = surface.model!!
      val module = model.module
      val resourceManager = LocalResourceManager.getInstance(module) ?: return listOf()

      val layoutFiles = resourceManager.findResourceFiles(ResourceNamespace.TODO(), ResourceFolderType.LAYOUT)
        .filterIsInstance<XmlFile>()
        .associateBy { AndroidUtils.getContextClass(module, it) }

      val classToDestination = mutableMapOf<PsiClass, Destination>()
      val schema = model.schema
      val parent = surface.currentNavigation
      val existingClasses = parent.children.mapNotNull { it.className }.toSortedSet()
      val hosts = findReferences(model.file, module).map { it.containingFile }

      for (tag in schema.allTags) {
        for (psiClass in schema.getProjectClassesForTag(tag).filter { !existingClasses.contains(it.qualifiedName) }) {
          val layoutFile = layoutFiles[psiClass]
          if (layoutFile !in hosts) {
            val inProject = psiClass.isInProject()
            val destination = Destination.RegularDestination(
              parent, tag, destinationClass = psiClass, inProject = inProject, layoutFile = layoutFile)
            classToDestination[psiClass] = destination
          }
        }
      }

      val result = classToDestination.values.toMutableList()
      val existingIncludes = parent.children.filter { it.isInclude }.map { it.includeFile }

      for (navPsi in resourceManager.findResourceFiles(ResourceNamespace.TODO(),
                                                       ResourceFolderType.NAVIGATION).filterIsInstance<XmlFile>()) {
        if (model.file == navPsi || existingIncludes.contains(navPsi)) {
          continue
        }

        result.add(Destination.IncludeDestination(navPsi.name, parent))
      }

      result.add(Destination.PlaceholderDestination(parent))
      result.sort()
      return result
    }

  @VisibleForTesting
  lateinit var destinationsList: JBList<Destination>

  var maxIconWidth: Int = 0

  @VisibleForTesting
  lateinit var searchField: SearchTextField

  override val mainPanel: JPanel
    get() {
      creatingInProgress = false
      return createSelectionPanel()
    }

  @VisibleForTesting
  lateinit var blankDestinationButton: ActionButtonWithText

  private var neverShown = true

  private fun createSelectionPanel(): JPanel {
    destinationsList = JBList()
    val result = object : AdtSecondaryPanel(VerticalLayout(8)), DataProvider {
      override fun getData(dataId: String): Any? {
        return if (NewAndroidComponentAction.CREATED_FILES.`is`(dataId)) {
          createdFiles
        }
        else {
          surface.getData(dataId)
        }
      }
    }
    result.name = DESTINATION_MENU_MAIN_PANEL_NAME
    result.background = BACKGROUND_COLOR
    result.border = BorderFactory.createEmptyBorder(8, 4, 8, 4)

    searchField = SearchTextField()
    searchField.textEditor.putClientProperty("JTextField.Search.Gap", JBUI.scale(3))
    searchField.textEditor.putClientProperty("JTextField.Search.GapEmptyText", JBUI.scale(-1))
    searchField.textEditor.putClientProperty("StatusVisibleFunction", SearchFieldStatusTextVisibility.isVisibleFunction)
    searchField.textEditor.emptyText.text = "Search existing destinations"
    result.add(searchField)

    val action: AnAction = object : AnAction("Create new destination") {
      override fun actionPerformed(e: AnActionEvent) {
        createNewDestination(e)
      }
    }
    blankDestinationButton = ActionButtonWithText(action, action.templatePresentation, "Toolbar", JBDimension(0, 45))
    val buttonPanel = AdtSecondaryPanel(BorderLayout(0, 8))
    buttonPanel.border = CompoundBorder(JBUI.Borders.empty(1, 1), DottedRoundedBorder(JBUI.emptyInsets(), HIGHLIGHTED_FRAME, 8.0f))
    buttonPanel.add(blankDestinationButton, BorderLayout.CENTER)
    buttonPanel.background = BACKGROUND_COLOR
    val scrollable = AdtSecondaryPanel(BorderLayout(0, 8))
    scrollable.add(buttonPanel, BorderLayout.NORTH)
    scrollable.background = BACKGROUND_COLOR
    val scrollPane = JBScrollPane(scrollable)
    scrollPane.preferredSize = JBDimension(252, 300)
    scrollPane.border = BorderFactory.createEmptyBorder()
    scrollPane.background = BACKGROUND_COLOR

    result.add(scrollPane)

    val application = ApplicationManager.getApplication()

    result.addHierarchyListener { e ->
      if (e?.changeFlags?.and(HierarchyEvent.SHOWING_CHANGED.toLong())?.let { it > 0 } == true) {
        if (neverShown || balloon?.wasFadedOut() == true) {
          neverShown = false
          application.invokeLater { searchField.requestFocusInWindow() }
        }
      }
    }
    destinationsList.emptyText.text = "Loading..."
    destinationsList.setPaintBusy(true)
    destinationsList.setCellRenderer { _, value, _, selected, _ ->
      THUMBNAIL_RENDERER.icon = ImageIcon(value.thumbnail)
      THUMBNAIL_RENDERER.iconTextGap = (maxIconWidth - THUMBNAIL_RENDERER.icon.iconWidth).coerceAtLeast(0)
      PRIMARY_TEXT_RENDERER.text = value.label
      SECONDARY_TEXT_RENDERER.text = value.typeLabel
      RENDERER.isOpaque = selected
      RENDERER
    }
    destinationsList.background = BACKGROUND_COLOR

    destinationsList.addMouseListener(object : MouseAdapter() {
      override fun mouseExited(e: MouseEvent?) {
        destinationsList.clearSelection()
      }

      override fun mouseClicked(event: MouseEvent) {
        destinationsList.selectedValue?.let { addDestination(it) }
      }
    })

    destinationsList.addMouseMotionListener(
      object : MouseAdapter() {
        override fun mouseMoved(event: MouseEvent) {
          val index = destinationsList.locationToIndex(event.point)
          if (index != -1) {
            destinationsList.selectedIndex = index
          }
          else {
            destinationsList.clearSelection()
          }
        }
      }
    )

    destinationsList.addKeyListener(object : KeyAdapter() {
      override fun keyTyped(e: KeyEvent?) {
        if (e?.keyChar == '\n' || e?.keyCode == VK_ENTER) {
          destinationsList.selectedValue?.let { addDestination(it) }
        }
        else {
          searchField.requestFocus()
          application.invokeLater { searchField.dispatchEvent(e) }
        }
      }
    })
    scrollable.add(destinationsList, BorderLayout.CENTER)

    ProgressManager.getInstance().runProcessWithProgressAsynchronously(
      object : Task.Backgroundable(surface.project, "Get Available Destinations") {
        override fun run(indicator: ProgressIndicator) {
          val dests = DumbService.getInstance(project).runReadActionInSmartMode(Computable { destinations })
          maxIconWidth = dests.map { it.thumbnail.getWidth(null) }.max() ?: 0
          val listModel = FilteringListModel<Destination>(CollectionListModel<Destination>(dests))
          listModel.setFilter { destination -> destination.label.toLowerCase().contains(searchField.text.toLowerCase()) }
          searchField.addDocumentListener(
            object : DocumentAdapter() {
              override fun textChanged(e: DocumentEvent) {
                listModel.refilter()
              }
            }
          )

          application.invokeLater {
            @Suppress("UNCHECKED_CAST")
            destinationsList.model = listModel as ListModel<Destination>

            destinationsList.setPaintBusy(false)
            destinationsList.emptyText.text = "No existing destinations"
          }
        }
      }, EmptyProgressIndicator())

    result.isFocusCycleRoot = true
    result.focusTraversalPolicy = LayoutFocusTraversalPolicy()

    return result
  }

  private fun createNewDestination(e: AnActionEvent) {
    balloon?.hide()
    val action = if (StudioFlags.NPW_SHOW_FRAGMENT_GALLERY.get()) {
      NewAndroidFragmentAction().apply {
        // Not moving out the same setShouldOpenFiles method as the other branch because AnAction
        // doesn't have the method and once NPW_SHOW_FRAGMENT_GALLERY flag is removed, this if
        // statement is going to be removed.
        shouldOpenFiles = false
      }
    } else {
      NewAndroidComponentAction("Fragment", "Fragment (Blank)", 7).apply {
        setShouldOpenFiles(false)
      }
    }
    createNewDestination(e, action)
  }

  @VisibleForTesting
  fun createNewDestination(e: AnActionEvent, action: AnAction) {
    createdFiles.clear()
    action.actionPerformed(e)
    val project = e.project ?: return
    val model = surface.model ?: return
    val schema = model.schema
    val resourceManager = LocalResourceManager.getInstance(model.module) ?: return

    DumbService.getInstance(project).runWhenSmart {
      var layoutFile: XmlFile? = null
      var psiClass: PsiClass? = null

      for (file in createdFiles) {
        val virtualFile = VfsUtil.findFileByIoFile(file, true) ?: continue
        val psiFile = PsiUtil.getPsiFile(project, virtualFile)
        if (psiFile is XmlFile && resourceManager.getFileResourceFolderType(psiFile) == ResourceFolderType.LAYOUT) {
          layoutFile = psiFile
        }
        if (psiFile is PsiClassOwner) {
          psiClass = psiFile.classes[0]
        }
      }
      if (psiClass != null) {
        NavUsageTracker.getInstance(surface.model).createEvent(NavEditorEvent.NavEditorEventType.CREATE_FRAGMENT).log()

        val tags = schema.getTagsForDestinationClass(psiClass) ?: return@runWhenSmart
        val tag = if (tags.size == 1) tags.first()
        else schema.getDefaultTag(NavigationSchema.DestinationType.FRAGMENT) ?: return@runWhenSmart

        val destination =
          Destination.RegularDestination(surface.currentNavigation, tag, null, psiClass, layoutFile = layoutFile)
        addDestination(destination)
      }
      createdFiles.clear()
    }
  }

  @VisibleForTesting
  fun addDestination(destination: Destination) {
    if (creatingInProgress) {
      return
    }
    creatingInProgress = true

    balloon?.hide()
    lateinit var component: NlComponent
    WriteCommandAction.runWriteCommandAction(surface.project, "Add ${destination.label}", null, Runnable {
      destination.addToGraph()
      component = destination.component ?: return@Runnable
      if (component.isInclude) {
        NavUsageTracker.getInstance(surface.model).createEvent(NavEditorEvent.NavEditorEventType.ADD_INCLUDE).log()
      }
      else {
        NavUsageTracker.getInstance(surface.model).createEvent(
          NavEditorEvent.NavEditorEventType.ADD_DESTINATION).withDestinationInfo(component).log()
      }
      component.putClientProperty(NEW_DESTINATION_MARKER_PROPERTY, true)
      // explicitly update so the new SceneComponent is created
      surface.sceneManager!!.update()
    }, surface.model?.file)
    surface.selectionModel.setSelection(ImmutableList.of(component))
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    button = super.createCustomComponent(presentation, place)
    buttonPresentation = presentation
    return button
  }

  // open for testing only
  open fun show() {
    show(button)
  }

  override fun update(e: AnActionEvent) {
    e.project?.let { buttonPresentation?.isEnabled = !DumbService.isDumb(it) }
  }

  companion object {

    private val RENDERER = AdtSecondaryPanel(BorderLayout())
    private val THUMBNAIL_RENDERER = JBLabel()
    private val PRIMARY_TEXT_RENDERER = JBLabel()
    private val SECONDARY_TEXT_RENDERER = JBLabel()

    init {
      SECONDARY_TEXT_RENDERER.foreground = SUBDUED_TEXT
      val leftPanel = JPanel(VerticalLayout(0, SwingConstants.CENTER))
      leftPanel.isOpaque = false
      THUMBNAIL_RENDERER.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
      THUMBNAIL_RENDERER.text = " "
      SECONDARY_TEXT_RENDERER.fontColor = UIUtil.FontColor.BRIGHTER
      leftPanel.add(THUMBNAIL_RENDERER, VerticalLayout.CENTER)
      RENDERER.add(leftPanel, BorderLayout.WEST)
      val rightPanel = JPanel(VerticalLayout(8))
      rightPanel.isOpaque = false
      rightPanel.border = JBUI.Borders.empty(14, 6, 10, 0)
      rightPanel.add(PRIMARY_TEXT_RENDERER, VerticalLayout.CENTER)
      rightPanel.add(SECONDARY_TEXT_RENDERER, VerticalLayout.CENTER)
      RENDERER.add(rightPanel, BorderLayout.CENTER)
      RENDERER.background = LIST_MOUSEOVER
      RENDERER.isOpaque = false
    }
  }
}
