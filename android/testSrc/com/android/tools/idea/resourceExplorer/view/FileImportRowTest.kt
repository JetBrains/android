/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.view

import com.android.resources.ResourceFolderType
import com.android.tools.idea.resourceExplorer.viewmodel.FileImportRowViewModel
import com.intellij.ide.ui.laf.darcula.DarculaLaf
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.UIManager

fun main(vararg param: String) {
  UIManager.setLookAndFeel(DarculaLaf())
  val frame = JFrame("Test Qualifiers")
  val contentPane = JPanel(BorderLayout())
  contentPane.preferredSize = JBUI.size(700, 600)
  contentPane.add(FileImportRow(FileImportRowViewModel(ResourceFolderType.DRAWABLE)))
  frame.contentPane = contentPane
  frame.pack()
  frame.isVisible = true
}