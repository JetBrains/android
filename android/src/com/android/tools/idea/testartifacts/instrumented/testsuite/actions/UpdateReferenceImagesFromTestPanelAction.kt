/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

/**
 * Action to trigger the update of reference images for screenshot tests.
 * This action is displayed as a button in the test details header.
 */
class UpdateReferenceImagesFromTestPanelAction : AnAction(
  "Update Reference Images",
  "Updates the reference images",
  null
) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    Messages.showInfoMessage(
      project,
      "Update Reference Image button from the test panel clicked",
      "Update Reference Images"
    )

    //TODO(b/445731253): Implement the test panel's update ref image button action
  }
}