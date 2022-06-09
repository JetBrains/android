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
import com.android.resources.ResourceType
import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.naveditor.analytics.NavUsageTracker
import com.android.tools.idea.naveditor.model.className
import com.android.tools.idea.naveditor.model.includeFile
import com.android.tools.idea.naveditor.model.isInclude
import com.android.tools.idea.naveditor.model.parentSequence
import com.android.tools.idea.naveditor.model.schema
import com.android.tools.idea.naveditor.scene.NavColors.HIGHLIGHTED_FRAME
import com.android.tools.idea.naveditor.scene.NavColors.LIST_MOUSEOVER
import com.android.tools.idea.naveditor.scene.NavColors.SUBDUED_TEXT
import com.android.tools.idea.naveditor.scene.layout.NEW_DESTINATION_MARKER_PROPERTY
import com.android.tools.idea.naveditor.structure.findReferences
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.npw.actions.NewAndroidFragmentAction
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import com.android.tools.idea.ui.resourcemanager.model.DesignAsset
import com.android.tools.idea.ui.resourcemanager.rendering.ImageCache
import com.android.tools.idea.ui.resourcemanager.rendering.LayoutSlowPreviewProvider
import com.android.tools.idea.ui.resourcemanager.rendering.SlowResourcePreviewManager
import com.android.tools.idea.util.addDependenciesWithUiConfirmation
import com.android.tools.idea.util.dependsOn
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
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
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.speedSearch.FilteringListModel
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import org.jetbrains.android.dom.navigation.NavigationSchema
import org.jetbrains.android.dom.navigation.dynamicModules
import org.jetbrains.android.dom.navigation.getClassesForTag
import org.jetbrains.android.dom.navigation.isInProject
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.LocalResourceManager
import org.jetbrains.android.util.AndroidUtils
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.HierarchyEvent
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.TreeSet
import java.util.stream.Collectors
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.SwingConstants
import javax.swing.border.CompoundBorder
import javax.swing.event.DocumentEvent

const val DESTINATION_MENU_MAIN_PANEL_NAME = "destinationMenuMainPanel"
private val DYNAMIC_DEPENDENCIES
  = listOf(GoogleMavenArtifactId.ANDROIDX_NAVIGATION_DYNAMIC_FEATURES_FRAGMENT.getCoordinate("+"))

/**
 * "Add" popup menu in the navigation editor.
 */
// open for testing only
@VisibleForTesting
open class AddDestinationMenu(surface: NavDesignSurface) :
  NavToolbarMenu(surface, "New Destination", StudioIcons.NavEditor.Toolbar.ADD_DESTINATION) {

  private lateinit var button: JComponent
  private var creatingInProgress = false
  private val iconProvider: SlowResourcePreviewManager by lazy {
    val model = surface.model!!
    // TODO(147157941): Get the Icon provider with AssetPreviewManager
    SlowResourcePreviewManager(
      ImageCache.createImageCache(model.project),
      LayoutSlowPreviewProvider(model.facet, model.configuration.resourceResolver))
  }

  private val renderer = AdtSecondaryPanel(BorderLayout())
  private val thumbnailRenderer = JBLabel()
  private val primaryTextRenderer = JBLabel()
  private val secondaryTextRenderer = JBLabel()

  init {
    secondaryTextRenderer.foreground = SUBDUED_TEXT
    val leftPanel = JPanel(VerticalLayout(0, SwingConstants.CENTER))
    leftPanel.isOpaque = false
    thumbnailRenderer.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
    thumbnailRenderer.text = " "
    secondaryTextRenderer.fontColor = UIUtil.FontColor.BRIGHTER
    leftPanel.add(thumbnailRenderer, VerticalLayout.CENTER)
    renderer.add(leftPanel, BorderLayout.WEST)
    val rightPanel = JPanel(VerticalLayout(8))
    rightPanel.isOpaque = false
    rightPanel.border = JBUI.Borders.empty(14, 6, 10, 0)
    rightPanel.add(primaryTextRenderer, VerticalLayout.CENTER)
    rightPanel.add(secondaryTextRenderer, VerticalLayout.CENTER)
    renderer.add(rightPanel, BorderLayout.CENTER)
    renderer.background = LIST_MOUSEOVER
    renderer.isOpaque = false
  }

  @VisibleForTesting
  val destinations: List<Destination>
    get() {
      val model = surface.model!!
      val module = model.module
      val resourceManager = LocalResourceManager.getInstance(module) ?: return listOf()

      val layoutFiles = getAllLayoutFiles()

      val classToDestination = mutableMapOf<PsiClass, Destination>()
      val schema = model.schema
      val parent = surface.currentNavigation.parentSequence().last()
      val existingClasses = parent.flatten().map { it?.className }.filter { it != null }.collect(Collectors.toCollection { TreeSet() })
      val hosts = findReferences(model.file, module).map { it.containingFile }

      for (tag in schema.allTags) {
        for ((psiClass, dynamicModule) in getClassesForTag(module, tag).filterKeys { !existingClasses.contains(it.qualifiedName) }) {
          val layoutFile = layoutFiles[psiClass]
          if (layoutFile !in hosts) {
            val inProject = psiClass.isInProject()
            val destination = Destination.RegularDestination(
              parent, tag, destinationClass = psiClass, inProject = inProject, layoutFile = layoutFile,
              dynamicModuleName = dynamicModule)
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
  lateinit var createNewDestinationButton: ActionButtonWithText

  private var neverShown = true

  private fun createSelectionPanel(): JPanel {
    destinationsList = JBList()
    val result = AdtSecondaryPanel(VerticalLayout(8))
    result.name = DESTINATION_MENU_MAIN_PANEL_NAME
    result.background = BACKGROUND_COLOR
    result.border = BorderFactory.createEmptyBorder(8, 4, 8, 4)

    searchField = SearchTextField()
    searchField.textEditor.putClientProperty("JTextField.Search.Gap", JBUI.scale(3))
    searchField.textEditor.putClientProperty("JTextField.Search.GapEmptyText", JBUI.scale(-1))
    searchField.textEditor.putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION, SearchFieldStatusTextVisibility.isVisibleFunction)
    searchField.textEditor.emptyText.text = "Search existing destinations"
    result.add(searchField)

    val action: AnAction = object : AnAction("Create new destination") {
      override fun actionPerformed(e: AnActionEvent) {
        createNewDestination()
      }
    }
    createNewDestinationButton = ActionButtonWithText(action, action.templatePresentation, "Toolbar", JBDimension(0, 45))
    val buttonPanel = AdtSecondaryPanel(BorderLayout(0, 8))
    buttonPanel.border = CompoundBorder(JBUI.Borders.empty(1, 1), DottedRoundedBorder(JBInsets.emptyInsets(), HIGHLIGHTED_FRAME, 8.0f))
    buttonPanel.add(createNewDestinationButton, BorderLayout.CENTER)
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
    destinationsList.setCellRenderer { list, value, index, selected, _ ->
      val iconCallback = { file: VirtualFile, dimension: Dimension ->
        iconProvider.getIcon(DesignAsset(file, listOf(), ResourceType.LAYOUT),
                             dimension.width,
                             dimension.height,
                             list,
                             { list.getCellBounds(index, index)?.let(list::repaint) },
                             { index in list.firstVisibleIndex..list.lastVisibleIndex })
      }

      thumbnailRenderer.icon = IconUtil.createImageIcon(value.thumbnail(iconCallback, list))
      thumbnailRenderer.iconTextGap = (maxIconWidth - thumbnailRenderer.icon.iconWidth).coerceAtLeast(0)
      primaryTextRenderer.text = value.label
      secondaryTextRenderer.text = value.typeLabel
      renderer.isOpaque = selected
      renderer
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
          maxIconWidth = dests.maxOfOrNull { it.iconWidth } ?: 0
          val listModel = FilteringListModel(CollectionListModel(dests))
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
            destinationsList.model = listModel

            destinationsList.setPaintBusy(false)
            destinationsList.emptyText.text = "No existing destinations"
          }
        }
      }, EmptyProgressIndicator())

    result.isFocusCycleRoot = true
    result.focusTraversalPolicy = LayoutFocusTraversalPolicy()

    return result
  }

  private fun createNewDestination() {
    balloon?.hide()

    val model = surface.model ?: return
    val module = model.module
    val facet = AndroidFacet.getInstance(module)
    // Find navigation resource folder.
    val targetDirectory = CommonDataKeys.VIRTUAL_FILE.getData(DataManager.getInstance().getDataContext(surface))?.parent
    if (facet == null || targetDirectory == null) {
      return
    }

    // returned argument to receive created files from wizard.
    val createdFiles = mutableListOf<File>()
    NewAndroidFragmentAction.openNewFragmentWizard(facet, model.project, targetDirectory, createdFiles, false)
    postNewDestinationFileCreated(model, createdFiles)
  }

  @VisibleForTesting
  fun postNewDestinationFileCreated(model: NlModel, createdFiles: MutableList<File>) {
    val schema = model.schema
    val resourceManager = LocalResourceManager.getInstance(model.module) ?: return

    DumbService.getInstance(model.project).runWhenSmart {
      var layoutFile: XmlFile? = null
      var psiClass: PsiClass? = null

      for (file in createdFiles) {
        val virtualFile = VfsUtil.findFileByIoFile(file, true) ?: continue
        val psiFile = PsiUtil.getPsiFile(model.project, virtualFile)
        if (psiFile is XmlFile && resourceManager.getFileResourceFolderType(psiFile) == ResourceFolderType.LAYOUT) {
          layoutFile = psiFile
        }
        if (psiFile is PsiClassOwner) {
          psiClass = psiFile.classes[0]
        }
      }
      if (psiClass != null) {
        NavUsageTracker.getInstance(surface.model).createEvent(NavEditorEvent.NavEditorEventType.CREATE_FRAGMENT).log()

        // If the new destination requires a dependency that hasn't been added yet we might not be able to
        // get the tag name from the navigation schema. Default to fragment since we're being called from
        // the new fragment wizard.
        val tag = schema.getTagsForDestinationClass(psiClass)?.singleOrNull()
                  ?: schema.getDefaultTag(NavigationSchema.DestinationType.FRAGMENT)
                  ?: return@runWhenSmart

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
      surface.sceneManager!!.requestRenderAsync()
    }, surface.model?.file)

    addDynamicDependency(destination)

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

  private fun addDynamicDependency(destination: Destination) {
    (destination as? Destination.RegularDestination)?.dynamicModuleName ?: return

    val module = surface.model?.module ?: return
    if (module.dependsOn(GoogleMavenArtifactId.ANDROIDX_NAVIGATION_DYNAMIC_FEATURES_FRAGMENT)) {
      return
    }

    module.addDependenciesWithUiConfirmation(DYNAMIC_DEPENDENCIES, promptUserBeforeAdding = true, requestSync = false)
  }

  private fun getAllLayoutFiles() : Map<PsiClass?, XmlFile> {
    val module = surface.models.firstOrNull()?.module ?: return mapOf()
    val result = getLayoutFilesForModule(module).toMutableMap()

    for(dynamicModule in dynamicModules(module)) {
      result.putAll(getLayoutFilesForModule(dynamicModule))
    }

    return result
  }

  private fun getLayoutFilesForModule(module: Module): Map<PsiClass?, XmlFile> {
    val resourceManager = LocalResourceManager.getInstance(module) ?: return mapOf()

    return resourceManager.findResourceFiles(ResourceNamespace.TODO(), ResourceFolderType.LAYOUT)
      .filterIsInstance<XmlFile>()
      .associateBy { AndroidUtils.getContextClass(module, it) }
  }
}
