/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.android.tools.idea.npw.module

import com.android.tools.adtui.util.FormScalingUtil
import com.android.tools.idea.adb.wireless.UIColors
import com.android.tools.idea.npw.importing.SourceToGradleModuleModel
import com.android.tools.idea.npw.importing.SourceToGradleModuleStep
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.project.ChooseAndroidProjectStep.Companion.createTitle
import com.android.tools.idea.npw.project.TABLE_CELL_HEIGHT
import com.android.tools.idea.npw.project.TABLE_CELL_LEFT_PADDING
import com.android.tools.idea.npw.project.TABLE_CELL_WIDTH
import com.android.tools.idea.npw.project.TABLE_TITLE_PADDING
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.ui.SelectedListValueProperty
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.android.tools.idea.wizard.ui.StudioWizardLayout
import com.google.common.annotations.VisibleForTesting
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.border.CustomLineBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.Optional
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import org.jetbrains.android.util.AndroidBundle.message

/** This step allows the user to select which type of module they want to create. */
class ChooseModuleTypeWizard(
  private val project: Project,
  private val moduleParent: String,
  moduleGalleryEntries: List<ModuleGalleryEntry>,
  private val projectSyncInvoker: ProjectSyncInvoker,
) : Disposable {

  val mainPanel = JPanel(BorderLayout())

  private val importModuleGalleryEntry =
    ImportModuleGalleryEntry() // Added to the left list bottom, and as a marker for the separator
  private val moduleGalleryEntryList: List<ModuleGalleryEntry> =
    sortModuleEntries(moduleGalleryEntries, moduleParent) + Separator() + importModuleGalleryEntry
  private var selectedEntry: ModuleGalleryEntry? = null
  private lateinit var currentModelWizard: ModelWizard
  private val modelWizardDialog: ModelWizardDialog by lazy {
    ModelWizardDialog(
      currentModelWizard,
      message("android.wizard.module.new.module.title"),
      dialogCustomLayout,
      project,
      null, // URL
      DialogWrapper.IdeModalityType.IDE,
      ModelWizardDialog.CancellationPolicy.ALWAYS_CAN_CANCEL,
      false,
    )
  }

  private val dialogCustomLayout = DialogCustomLayout()
  private val leftPanel = JPanel(BorderLayout())
  private val listEntriesListeners = ListenerManager()
  private val modelWizardListeners = ListenerManager()
  private val logger: Logger
    get() = logger<ChooseModuleTypeWizard>()

  private var wizardModelChangedListener: (ModelWizard) -> Unit = {}

  init {
    val leftList =
      JBList(moduleGalleryEntryList).apply {
        setCellRenderer { list, value, _, isSelected, cellHasFocus ->
          if (value is Separator) {
            GroupHeaderSeparator(JBUI.emptyInsets()).apply {
              border = JBUI.Borders.empty(TABLE_TITLE_PADDING, 0)
            }
          } else {
            JBLabel(value.name, value.icon, SwingConstants.LEFT).apply {
              isOpaque = true
              background = UIUtil.getListBackground(isSelected, cellHasFocus)
              foreground = UIUtil.getListForeground(isSelected, cellHasFocus)
              border = JBUI.Borders.emptyLeft(TABLE_CELL_LEFT_PADDING)

              val size = JBUI.size(TABLE_CELL_WIDTH, TABLE_CELL_HEIGHT)
              preferredSize = size
              if (icon != null && icon.iconHeight > size.height()) {
                // Only scale if needed, to keep icon bounded
                icon = IconUtil.scale(icon, this, size.height().toFloat() * 0.7f / icon.iconHeight)
              }
            }
          }
        }
        AccessibleContextUtil.setName(this, message("android.wizard.module.new.module.header"))
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        selectedIndex = 0
        selectionModel.addListSelectionListener(SeparatorSkippingListener(moduleGalleryEntryList))
      }

    fun setNewModelWizard(galleryEntry: Optional<ModuleGalleryEntry>) {
      if (galleryEntry.isPresent && selectedEntry != galleryEntry.get()) {
        val entry = galleryEntry.get()
        if (entry is Separator) {
          return
        }
        try {
          currentModelWizard =
            ModelWizard.Builder()
              .addStep(entry.createStep(project, moduleParent, projectSyncInvoker))
              .build()
        } catch (ex: Throwable) {
          logger.error(ex)
        }

        // Ignore first initialization, as currentModelWizard is supplied in modelWizardDialog
        // constructor
        if (selectedEntry != null) {
          modelWizardListeners.releaseAll()
          modelWizardDialog.setModelWizard(currentModelWizard)
          modelWizardDialog.contentPane.revalidate()
        }
        selectedEntry = entry

        modelWizardListeners.listen(currentModelWizard.onFirstStep()) {
          leftPanel.isVisible = currentModelWizard.onFirstStep().get()
        }

        wizardModelChangedListener(currentModelWizard)
      }
    }

    listEntriesListeners.listenAndFire(SelectedListValueProperty(leftList), ::setNewModelWizard)

    Disposer.register(modelWizardDialog.disposable, this)

    leftPanel.apply {
      border = CustomLineBorder(UIColors.ONE_PIXEL_DIVIDER, 0, 0, 0, 1)
      add(createTitle(), BorderLayout.NORTH)
      add(leftList, BorderLayout.CENTER)
    }

    mainPanel.add(leftPanel, BorderLayout.WEST)

    FormScalingUtil.scaleComponentTree(this.javaClass, mainPanel)
  }

  fun setWizardModelListenerAndFire(listener: (ModelWizard) -> Unit) {
    wizardModelChangedListener = listener
    listener(currentModelWizard)
  }

  fun show() {
    modelWizardDialog.show()
  }

  override fun dispose() {
    listEntriesListeners.releaseAll()
    modelWizardListeners.releaseAll()
  }

  private inner class DialogCustomLayout : ModelWizardDialog.CustomLayout {
    override fun decorate(titleHeader: ModelWizard.TitleHeader, innerPanel: JPanel): JPanel {
      mainPanel.add(innerPanel, BorderLayout.CENTER)
      return mainPanel
    }

    override fun getDefaultPreferredSize(): Dimension {
      return StudioWizardLayout.DEFAULT_PREFERRED_SIZE
    }

    override fun getDefaultMinSize(): Dimension {
      return StudioWizardLayout.DEFAULT_MIN_SIZE
    }

    override fun dispose() {}
  }
}

private class ImportModuleGalleryEntry : ModuleGalleryEntry {
  override val icon: Icon = AllIcons.ToolbarDecorator.Import
  override val name: String = "Import..."
  override val description: String = message("android.wizard.module.import.gradle.description")

  override fun createStep(
    project: Project,
    moduleParent: String,
    projectSyncInvoker: ProjectSyncInvoker,
  ): SkippableWizardStep<*> =
    SourceToGradleModuleStep(SourceToGradleModuleModel(project, projectSyncInvoker))
}

@VisibleForTesting
fun sortModuleEntries(
  moduleTypeProviders: List<ModuleGalleryEntry>,
  moduleParent: String,
): List<ModuleGalleryEntry> {
  // To have a sequence specified by design, we hardcode the sequence. Everything else is added at
  // the end (sorted by name)
  val orderedNames =
    arrayOf(
      message("android.wizard.module.new.mobile"),
      message("android.wizard.module.new.library"),
      message("android.wizard.module.new.native.library"),
      message("android.wizard.module.new.dynamic.module"),
      message("android.wizard.module.new.dynamic.module.instant"),
      message("android.wizard.module.new.automotive"),
      message("android.wizard.module.new.wear"),
      message("android.wizard.module.new.tv"),
      message("android.wizard.module.import.gradle.title"),
      message("android.wizard.module.import.eclipse.title"),
      message("android.wizard.module.new.java.or.kotlin.library"),
      message("android.wizard.module.new.google.cloud"),
      message("android.wizard.module.new.baselineprofiles.module.app"),
      message("android.wizard.module.new.benchmark.module.app"),
      message("android.wizard.module.new.kotlin.multiplatform.library"),
    )

  return moduleTypeProviders
    .filter {
      it.name != message("android.wizard.module.new.kotlin.multiplatform.library") ||
        shouldShowKMPModule(moduleParent)
    }
    .partition { it.name in orderedNames }
    .run { first.sortedBy { orderedNames.indexOf(it.name) } + second.sortedBy { it.name } }
}

/**
 * Determines if the KMP Module option should be shown. It requires being called from the top level
 * project (not inside another module)
 */
private fun shouldShowKMPModule(moduleParent: String): Boolean {
  return moduleParent == ":"
}

fun showDefaultWizard(
  project: Project,
  moduleParent: String,
  projectSyncInvoker: ProjectSyncInvoker,
) {
  val moduleDescriptions =
    ModuleDescriptionProvider.EP_NAME.extensions.flatMap { it.getDescriptions(project) }
  ChooseModuleTypeWizard(project, moduleParent, moduleDescriptions, projectSyncInvoker).show()
}

private class Separator : ModuleGalleryEntry {
  override val icon: Icon?
    get() = null

  override val name: String
    get() = ""

  override val description: String?
    get() = null

  override fun createStep(
    project: Project,
    moduleParent: String,
    projectSyncInvoker: ProjectSyncInvoker,
  ): SkippableWizardStep<*> {
    throw UnsupportedOperationException()
  }
}

/** Prevents [Separator] elements from being selected. */
private class SeparatorSkippingListener(private val list: List<*>) : ListSelectionListener {

  private var settingSelection = false

  override fun valueChanged(event: ListSelectionEvent) {
    if (settingSelection) {
      return // Recursive call.
    }
    val firstIndex = event.firstIndex
    val lastIndex = event.lastIndex
    val selectionModel = event.source as ListSelectionModel
    if (firstIndex == lastIndex || list[selectionModel.anchorSelectionIndex] !is Separator) {
      return // No corrective action needed.
    }
    val newIndex =
      if (list[firstIndex] is Separator) {
        if (event.valueIsAdjusting) {
          lastIndex // Restore previous selection.
        } else {
          firstIndex - 1 // Selection moving up.
        }
      } else if (list[lastIndex] is Separator) {
        if (event.valueIsAdjusting) {
          firstIndex // Restore previous selection.
        } else {
          lastIndex + 1 // Selection moving down.
        }
      } else {
        return // No corrective action needed.
      }

    settingSelection = true
    try {
      selectionModel.setSelectionInterval(newIndex, newIndex)
    } finally {
      settingSelection = false
    }
  }
}
