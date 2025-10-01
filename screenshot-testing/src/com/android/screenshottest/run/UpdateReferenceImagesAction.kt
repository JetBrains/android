/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.screenshottest.run


import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

/**
 * Action to add or update the reference images for screenshot tests.
 */
class UpdateReferenceImagesAction : AnAction("Add/Update Reference Images",
                                             "Updates the reference images for screenshot tests.",
                                             AllIcons.FileTypes.Image) {

  override fun actionPerformed(e: AnActionEvent) {
    //TODO(b/439995792): Add the backend logic for adding/updating reference images
    Messages.showInfoMessage(
      "Add/Update option clicked",
      "Add/Update Reference Images"
    )
  }
}
