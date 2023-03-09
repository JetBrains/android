/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions

import com.android.SdkConstants
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ResourceFolderType
import com.android.resources.ScreenOrientation
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.res.getFolderType
import com.android.tools.idea.res.getResourceVariations
import com.android.tools.idea.ui.designer.EditorDesignSurface
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.intentions.OverrideResourceAction

private fun generateLayoutAndQualifierTitle(file: VirtualFile?): String {
  val fileName = file?.name ?: return  "Switch Layout Qualifier"
  val folderName = file.parent?.name ?: return fileName
  if (folderName == SdkConstants.FD_RES_LAYOUT) {
    return fileName
  }
  val qualifier = folderName.substringAfter("layout-")
  return "$qualifier/$fileName"
}

/**
 * The dropdown menu for changing layout qualifier.
 * Note that this action is also registered to action system in designer.xml.
 */
class LayoutQualifierDropdownMenu(file: VirtualFile?)
  : DropDownAction(generateLayoutAndQualifierTitle(file), "Action to switch and create qualifiers for layout files", null) {

  /**
   * The default constructor is used by register point of action system through reflection. See designer.xml file.
   */
  @Suppress("unused")
  private constructor(): this(null)

  private val displayText = generateLayoutAndQualifierTitle(file)

  override fun displayTextInToolbar(): Boolean = true

  override fun isPerformableWithoutActionButton(): Boolean = true

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val screenViewProvider = (e.dataContext.getData(DESIGN_SURFACE) as NlDesignSurface?)?.screenViewProvider
    // Only enable when using layout editor
    e.presentation.isEnabled = screenViewProvider == NlScreenViewProvider.RENDER ||
                               screenViewProvider == NlScreenViewProvider.BLUEPRINT ||
                               screenViewProvider == NlScreenViewProvider.DEFAULT_SCREEN_MODE
    e.presentation.setText(displayText, false)
  }

  public override fun updateActions(context: DataContext): Boolean {
    removeAll()
    val surface = context.getData(DESIGN_SURFACE) ?: return true
    val config = surface.configurations.firstOrNull() ?: return true
    createVariationsActions(config, surface)
    return true
  }

  private fun createVariationsActions(configuration: Configuration, surface: EditorDesignSurface) {
    val virtualFile = configuration.file
    if (virtualFile != null) {
      val project = configuration.configModule.project
      val variations = getResourceVariations(virtualFile, true)
      for (file in variations) {
        val title = generateLayoutAndQualifierTitle(file)
        add(SwitchToVariationAction(title, project, file, virtualFile == file))
      }
      addSeparator()
      val folderType = getFolderType(configuration.file)
      if (folderType == ResourceFolderType.LAYOUT) {
        var haveLandscape = false
        var haveTablet = false
        for (file in variations) {
          val name = file.parent.name
          if (name.startsWith(SdkConstants.FD_RES_LAYOUT)) {
            val config = FolderConfiguration.getConfigForFolder(name)
            if (config != null) {
              val orientation = config.screenOrientationQualifier
              if (orientation != null && orientation.value == ScreenOrientation.LANDSCAPE) {
                haveLandscape = true
                if (haveTablet) {
                  break
                }
              }
              val size = config.smallestScreenWidthQualifier
              if (size != null && size.value >= 600) {
                haveTablet = true
                if (haveLandscape) {
                  break
                }
              }
            }
          }
        }

        // Create actions for creating "common" versions of a layout (that don't exist),
        // e.g. Create Landscape Version, Create RTL Version, Create tablet version
        // Do statistics on what is needed!
        if (!haveLandscape) {
          add(CreateVariationAction(surface, "Create Landscape Qualifier", "layout-land"))
        }
        if (!haveTablet) {
          add(CreateVariationAction(surface, "Create Tablet Qualifier", "layout-sw600dp"))
        }
        add(CreateVariationAction(surface, "Add Resource Qualifier", null))
      }
      else {
        add(CreateVariationAction(surface, "Create Alternative...", null))
      }
    }
  }
}


class SwitchToVariationAction(private val title: String,
                              private val myProject: Project,
                              private val myFile: VirtualFile,
                              private val selected: Boolean) : AnAction(title, null, null), Toggleable {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.setText(title, false)
    e.presentation.isEnabled = !selected
    Toggleable.setSelected(e.presentation, selected)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val descriptor = OpenFileDescriptor(myProject, myFile, -1)
    FileEditorManager.getInstance(myProject).openEditor(descriptor, true)
  }
}

class CreateVariationAction(private val mySurface: EditorDesignSurface,
                            title: String,
                            private val myNewFolder: String?) : AnAction(title, null, null) {
  override fun actionPerformed(e: AnActionEvent) {
    OverrideResourceAction.forkResourceFile(mySurface, myNewFolder, true)
  }
}
