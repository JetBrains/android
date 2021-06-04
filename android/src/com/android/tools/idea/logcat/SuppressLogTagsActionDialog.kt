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
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBScrollPane
import org.jetbrains.android.util.AndroidBundle
import java.util.TreeSet
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

private val EMPTY_TAG = AndroidBundle.message("android.configure.logcat.tag.empty")

class SuppressLogTagsActionDialog(
  project: Project,
  private val androidLogConsole: AndroidLogConsole,
  private val selectedTags: Set<String>) :
  DialogWrapper(project, false, IdeModalityType.PROJECT) {

  val checkBoxes: MutableList<JCheckBox> = mutableListOf()

  init {
    init()
    title = AndroidBundle.message("android.configure.logcat.suppress.tags.title")
  }

  override fun createCenterPanel(): JComponent {
    // Extract all tags from active log excluding already selected tags which will be added separately.
    val tags = StringUtil.splitByLines(androidLogConsole.originalDocument.toString()).mapTo(TreeSet()) {
      LogcatJson.fromJson(it).header.tag
    }.filter { !selectedTags.contains(it) }

    val panel = JPanel()
    panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

    for (tag in selectedTags.sorted()) {
      checkBoxes.add(JCheckBox(getTagDisplayText(tag), true))
    }
    for (tag in tags) {
      checkBoxes.add(JCheckBox(getTagDisplayText(tag), false))
    }
    for (checkBox in checkBoxes) {
      panel.add(checkBox)
    }

    return JBScrollPane(panel)
  }

  fun getSelectedTags(): List<String> {
    return checkBoxes.filter { it.isSelected }.map { getTagFromDisplayText(it.text) }
  }

  private fun getTagDisplayText(tag: String): String = tag.ifEmpty {
    EMPTY_TAG
  }

  private fun getTagFromDisplayText(displayText: String): String =
    if (displayText == EMPTY_TAG) {
      ""
    }
    else {
      displayText
    }
}