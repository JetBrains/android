/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.tools.idea.assistant.OpenAssistSidePanelAction
import com.android.tools.idea.common.assistant.HelpPanelBundle
import com.android.tools.idea.common.assistant.HelpPanelToolWindowListener
import com.android.tools.idea.common.assistant.LayoutEditorHelpPanelAssistantBundleCreatorBase
import com.android.tools.idea.flags.StudioFlags
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DesignEditorHelpPanelEvent.HelpPanelType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction

import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

const val MOTION_EDITOR_BUNDLE_ID = "LayoutEditor.HelpAssistant.MotionLayout"
const val CONSTRAINT_LAYOUT_BUNDLE_ID = "LayoutEditor.HelpAssistant.ConstraintLayout"
const val FULL_HELP_BUNDLE_ID = "LayoutEditor.HelpAssistant.Full"

/**
 * Action that :
 * 1) Determines if the "?" icon should be displayed or not
 * 2) Performs opening Assistant Panel based on the file type that's opened.
 */
class LayoutEditorHelpAssistantAction : OpenAssistSidePanelAction() {

  companion object {
    const val BUNDLE_ID = "LayoutEditor.HelpAssistant"
  }

  enum class Type {
    NONE,
    CONSTRAINT_LAYOUT,
    MOTION_LAYOUT,
    FULL
  }

  private val ONLY_FULL: Boolean = true // for now redirect to a single help content
  @VisibleForTesting var type = Type.NONE

  init {
    HelpPanelToolWindowListener.map[MOTION_EDITOR_BUNDLE_ID] = HelpPanelType.MOTION_LAYOUT
    HelpPanelToolWindowListener.map[CONSTRAINT_LAYOUT_BUNDLE_ID] = HelpPanelType.CONSTRAINT_LAYOUT
    HelpPanelToolWindowListener.map[FULL_HELP_BUNDLE_ID] = HelpPanelType.FULL_ALL
  }

  override fun update(e: AnActionEvent) {
    updateInternalVariables(e)
    e.presentation.isEnabledAndVisible = type != Type.NONE
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    HelpPanelToolWindowListener.registerListener(project)

    when (type) {
      Type.CONSTRAINT_LAYOUT -> {
        openWindow(constraintLayoutHelpPanelBundle.bundleId, event.project!!)
      }
      Type.MOTION_LAYOUT -> {
        openWindow(motionLayoutHelpPanelBundle.bundleId, event.project!!)
      }
      Type.FULL -> {
        openWindow(fullHelpPanelBundle.bundleId, event.project!!)
      }
      Type.NONE -> Unit
    }
  }

  private fun updateInternalVariables(e: AnActionEvent) {
    if (ONLY_FULL) {
      type = Type.FULL
    } else {
      val tagName = tagName(e)
      type = getType(tagName, e)
    }
  }

  private fun tagName(e: AnActionEvent): String {
    // Copy the string here rather than holding onto a field within XmlTag so it can be freely released.
    return "" + getTag(e)?.name
  }

  private fun getType(tagName: String, e: AnActionEvent): Type {
    if (tagName.isEmpty()) {
      return Type.NONE
    }

    return getDirectType(tagName).let{ type ->
      if (type != Type.NONE) {
        return@let type
      }
      /**
       * For Data Binding. It has the format like:
       * <layout>
       *   <data>  </data>
       *   <androidx...ConstraintLayout>
       */
      if (SdkConstants.TAG_LAYOUT == tagName) {
          val tag = getTag(e) ?: return@let Type.NONE
          return@let getChildrenType(tag)
        }
      return@let type
    }
  }

  private fun getTag(e: AnActionEvent): XmlTag? {
    if (!ApplicationManager.getApplication().isReadAccessAllowed) {
      return runReadAction { getTag(e) }
    }
    val psiFile = e.getData(PlatformDataKeys.PSI_FILE)
    if (psiFile !is XmlFile) {
      return null
    }

    return psiFile.rootTag
  }

  private fun getChildrenType(tag: XmlTag): Type {
    if (!ApplicationManager.getApplication().isReadAccessAllowed) {
      return runReadAction { getChildrenType(tag) }
    }

    tag.children.forEach {
      if (it !is XmlTag) {
        return@forEach
      }
      val childType = getDirectType(it.name)
      if (childType != Type.NONE) {
        return childType
      }
    }

    return Type.NONE
  }

  private fun getDirectType(tagName: String): Type {
    if (AndroidXConstants.MOTION_LAYOUT.isEquals(tagName)) {
      return Type.MOTION_LAYOUT
    } else if (AndroidXConstants.CONSTRAINT_LAYOUT.isEquals(tagName)) {
      return Type.CONSTRAINT_LAYOUT
    }

    return Type.NONE
  }
}


private val motionLayoutHelpPanelBundle =
  HelpPanelBundle(MOTION_EDITOR_BUNDLE_ID, "/motionlayout_help_assistance_bundle.xml")

private val constraintLayoutHelpPanelBundle =
  HelpPanelBundle(CONSTRAINT_LAYOUT_BUNDLE_ID, "/constraintlayout_help_assistance_bundle.xml")

private val fullHelpPanelBundle =
  HelpPanelBundle(FULL_HELP_BUNDLE_ID, "/layout_editor_help_assistance_bundle.xml")

class MotionLayoutPanelAssistantBundleCreator :
  LayoutEditorHelpPanelAssistantBundleCreatorBase(motionLayoutHelpPanelBundle)

class ConstraintLayoutPanelAssistantBundleCreator :
  LayoutEditorHelpPanelAssistantBundleCreatorBase(constraintLayoutHelpPanelBundle)

class LayoutEditorPanelAssistantBundleCreator :
  LayoutEditorHelpPanelAssistantBundleCreatorBase(fullHelpPanelBundle)

