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

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.IdeModalityType.PROJECT
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.dialog
import org.jetbrains.android.util.AndroidBundle
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class SuppressLogTagsDialog private constructor(
  project: Project,
  title: String,
  private val description: String?,
  selectedTags: Set<String>,
  unselectedTags: Set<String>) {

  private val checkBoxes: List<JCheckBox> =
    selectedTags.sorted().map { JCheckBox(AndroidLogcatUtils.getTagDisplayText(it), true) } +
    unselectedTags.sorted().map { JCheckBox(AndroidLogcatUtils.getTagDisplayText(it), false) }

  val dialogWrapper = dialog(
    project = project,
    title = title,
    resizable = true,
    modality = PROJECT,
    panel = createPanel(),
  )

  private fun createPanel(): JComponent {
    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
    if (description != null) {
      panel.add(JLabel(description))
    }
    for (checkBox in checkBoxes) {
      panel.add(checkBox)
    }
    return JBScrollPane(panel)
  }

  fun getSelectedTags(): Set<String> {
    return checkBoxes.filter { it.isSelected }.mapTo(HashSet()) { AndroidLogcatUtils.getTagFromDisplayText(it.text) }
  }

  companion object {
    fun newManageTagsDialog(project: Project, selectedTags: Set<String>, unselectedTags: Set<String>) =
      SuppressLogTagsDialog(
        project,
        AndroidBundle.message("android.configure.logcat.suppress.tags.title"),
        description = null,
        selectedTags,
        unselectedTags)

    fun newConfirmTagsDialog(project: Project, selectedTags: Set<String>) =
      SuppressLogTagsDialog(
        project,
        AndroidBundle.message("android.configure.logcat.suppress.tags.confirm.title"),
        AndroidBundle.message("android.configure.logcat.suppress.tags.confirm.description"),
        selectedTags,
        unselectedTags = emptySet())
  }
}