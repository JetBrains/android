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
package com.android.tools.idea.logcat

import com.android.ddmlib.IDevice
import com.android.tools.idea.ddms.DeviceContext
import com.android.tools.idea.ddms.actions.AbstractDeviceAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.android.util.AndroidBundle

class SuppressLogTagsAction(context: DeviceContext, private val onRefresh: Runnable) :
  AbstractDeviceAction(
    context,
    AndroidBundle.message("android.configure.logcat.suppress.tags.text"),
    AndroidBundle.message("android.configure.logcat.suppress.tags.description"),
    AllIcons.RunConfigurations.ShowIgnored) {

  override fun performAction(e: AnActionEvent, device: IDevice) {
    val preferences = AndroidLogcatGlobalPreferences.getInstance()
    val suppressedLogTags = preferences.suppressedLogTags
    val editor = e.getData(EDITOR) ?: throw IllegalArgumentException("AnActionEvent Data Context is missing required Editor")

    val tagsFromLogcat: Set<String> = StringUtil.splitByLines(editor.document.text)
      .map { AndroidLogcatFormatter.TAG_PATTERN.matcher(it) }.filter { it.find() }.mapTo(HashSet()) {
        it.group(AndroidLogcatFormatter.TAG_PATTERN_GROUP_NAME)
      }
      .subtract(suppressedLogTags)

    val project = e.getData(PROJECT) ?: throw IllegalArgumentException("AnActionEvent Data Context is missing required Project")
    val dialog = SuppressLogTagsDialog.newManageTagsDialog(project, suppressedLogTags, tagsFromLogcat)
    dialog.dialogWrapper.setSize(preferences.suppressedLogTagsDialogDimension.width, preferences.suppressedLogTagsDialogDimension.height)
    if (dialog.dialogWrapper.showAndGet()) {
      val selectedTags = dialog.getSelectedTags()
      if (selectedTags != suppressedLogTags) {
        suppressedLogTags.clear()
        suppressedLogTags.addAll(selectedTags)
        onRefresh.run()
      }
    }
    preferences.suppressedLogTagsDialogDimension = dialog.dialogWrapper.size
  }
}