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
package com.android.tools.idea.ui.resourcemanager.actions

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileTypes.StdFileTypes
import org.jetbrains.android.actions.CreateResourceFileAction
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle

/**
 * [AnAction] wrapper for the Resource Explorer that calls [CreateResourceFileAction.createFileResource] for a given resource type.
 *
 * Brings up the CreateResourceFileDialog, equivalent to the right click 'Android Resource File' action when clicking on a type-specific
 * folder (drawable, color, value, etc.).
 */
class NewResourceFileAction(
  private val type: ResourceType,
  private val resourceFolderType: ResourceFolderType,
  private val facet: AndroidFacet
) : AnAction(AndroidBundle.message("new.typed.resource.action.title", type.displayName),
             AndroidBundle.message("new.typed.resource.action.description", type.displayName),
             StdFileTypes.XML.icon) {
  override fun actionPerformed(e: AnActionEvent) {
    CreateResourceFileAction.createFileResource(
      facet,
      resourceFolderType,
      null,
      null,
      null,
      true,
      "${type.displayName} Resource File",
      null,
      e.dataContext
    )
    // TODO: Select created file in ResourceExplorer.
  }
}