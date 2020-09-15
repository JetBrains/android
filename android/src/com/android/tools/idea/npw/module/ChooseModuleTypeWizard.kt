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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.ui.SelectedListValueProperty
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder
import com.android.tools.idea.ui.wizard.StudioWizardLayout
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardDialog
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.Optional
import javax.swing.JPanel
import javax.swing.SwingConstants
import com.android.tools.idea.npw.module.deprecated.ChooseModuleTypeStep as DeprecatedChooseModuleTypeStep


private const val TABLE_CELL_WIDTH = 240
private const val TABLE_CELL_HEIGHT = 32
private const val TABLE_CELL_LEFT_PADDING = 16

/**
 * This step allows the user to select which type of module they want to create.
 */
class ChooseModuleTypeWizard(
  private val project: Project,
  private val moduleParent: String,
  moduleGalleryEntries: List<ModuleGalleryEntry>,
  private val projectSyncInvoker: ProjectSyncInvoker
): Disposable {

  val mainPanel = JPanel(BorderLayout())

  private val moduleGalleryEntryList: List<ModuleGalleryEntry> = sortModuleEntries(moduleGalleryEntries)
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
      ModelWizardDialog.CancellationPolicy.ALWAYS_CAN_CANCEL
    )
  }

  private val dialogCustomLayout = DialogCustomLayout()
  private val leftPanel = JPanel(BorderLayout())
  private val listEntriesListeners = ListenerManager()
  private val modelWizardListeners = ListenerManager()
  private val logger: Logger get() = logger<ChooseModuleTypeWizard>()

  private var wizardModelChangedListener: (ModelWizard) -> Unit = {}

  init {
    val leftList = JBList(moduleGalleryEntryList).apply {
      setCellRenderer { _, value, _, isSelected, cellHasFocus ->
        JBLabel(value.name, value.icon, SwingConstants.LEFT).apply {
          isOpaque = true
          background = UIUtil.getListBackground(isSelected, cellHasFocus)
          border = JBUI.Borders.emptyLeft(TABLE_CELL_LEFT_PADDING)

          val size = JBUI.size(TABLE_CELL_WIDTH, TABLE_CELL_HEIGHT)
          preferredSize = size
          if (icon != null && icon.iconHeight > size.height()) {
            // Only scale if needed, to keep icon bounded
            icon = IconUtil.scale(icon, this, size.height().toFloat() * 0.7f / icon.iconHeight)
          }
        }
      }
      selectedIndex = 0
    }

    fun setNewModelWizard(galleryEntry: Optional<ModuleGalleryEntry>) {
      if (galleryEntry.isPresent && selectedEntry != galleryEntry.get()) {
        try {
        currentModelWizard = ModelWizard.Builder().addStep(galleryEntry.get().createStep(project, moduleParent, projectSyncInvoker)).build()
        } catch (ex: Throwable) {
          logger.error(ex)
        }

        // Ignore first initialization, as currentModelWizard is supplied in modelWizardDialog constructor
        if (selectedEntry != null) {
          modelWizardListeners.releaseAll()
          modelWizardDialog.setModelWizard(currentModelWizard)
          modelWizardDialog.contentPane.revalidate()
        }
        selectedEntry = galleryEntry.get()

        modelWizardListeners.listen(currentModelWizard.onFirstStep()) {
          leftPanel.isVisible = currentModelWizard.onFirstStep().get()
        }

        wizardModelChangedListener(currentModelWizard)
      }
    }

    listEntriesListeners.listenAndFire(SelectedListValueProperty(leftList), ::setNewModelWizard)

    Disposer.register(modelWizardDialog.disposable, this)

    val titleLabel = JBLabel("Templates").apply {
      isOpaque = true
      background = UIUtil.getListBackground()
      preferredSize = JBUI.size(-1, TABLE_CELL_HEIGHT)
      border = JBUI.Borders.emptyLeft(TABLE_CELL_LEFT_PADDING)
    }

    leftPanel.add(titleLabel, BorderLayout.NORTH)
    leftPanel.add(leftList, BorderLayout.CENTER)

    mainPanel.add(leftPanel, BorderLayout.WEST)

    FormScalingUtil.scaleComponentTree(this.javaClass, mainPanel)
  }

  fun setWizardModelListenerAndFire(listener: (ModelWizard) -> Unit) {
    wizardModelChangedListener  = listener
    listener(currentModelWizard)
  }

  fun show() {
    modelWizardDialog.show()
  }

  override fun dispose() {
    listEntriesListeners.releaseAll()
    modelWizardListeners.releaseAll()
  }

  private inner class DialogCustomLayout: ModelWizardDialog.CustomLayout {
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

    override fun dispose() {
    }
  }
}


@VisibleForTesting
fun sortModuleEntries(moduleTypeProviders: List<ModuleGalleryEntry>): List<ModuleGalleryEntry> {
  // To have a sequence specified by design, we hardcode the sequence. Everything else is added at the end (sorted by name)
  val orderedNames = arrayOf(
    message("android.wizard.module.new.mobile"),
    message("android.wizard.module.new.library"),
    message("android.wizard.module.new.dynamic.module"),
    message("android.wizard.module.new.dynamic.module.instant"),
    message("android.wizard.module.new.automotive"),
    message("android.wizard.module.new.wear"),
    message("android.wizard.module.new.tv"),
    message("android.wizard.module.new.things"),
    message("android.wizard.module.import.gradle.title"),
    message("android.wizard.module.import.eclipse.title"),
    message("android.wizard.module.import.archive.title"),
    message("android.wizard.module.new.java.or.kotlin.library"),
    message("android.wizard.module.new.google.cloud"),
    message("android.wizard.module.new.benchmark.module.app"))

  return moduleTypeProviders.partition { it.name in orderedNames }.run {
    first.sortedBy { orderedNames.indexOf(it.name) } + second.sortedBy { it.name }
  }
}

fun showDefaultWizard(project: Project, moduleParent: String, projectSyncInvoker: ProjectSyncInvoker) {
  val moduleDescriptions = ModuleDescriptionProvider.EP_NAME.extensions.flatMap { it.getDescriptions(project) }
  if (StudioFlags.NPW_NEW_MODULE_WITH_SIDE_BAR.get()) {
    ChooseModuleTypeWizard(project, moduleParent, moduleDescriptions, projectSyncInvoker).show()
    return
  }

  val chooseModuleTypeStep = DeprecatedChooseModuleTypeStep(project, moduleParent, moduleDescriptions, projectSyncInvoker)
  val wizard = ModelWizard.Builder().addStep(chooseModuleTypeStep).build()
  StudioWizardDialogBuilder(wizard, message("android.wizard.module.new.module.title")).build().show()
}