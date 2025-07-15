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
package com.android.tools.idea.apk.viewer.pagealign

import com.android.tools.apk.analyzer.internal.ArchiveTreeNode
import com.android.tools.idea.apk.viewer.ApkViewPanel.ApkTreeModel
import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import javax.swing.JTree
import javax.swing.SwingConstants

class AlignmentCellRenderer : ColoredTreeCellRenderer() {
  override fun customizeCellRenderer(tree: JTree,
                                     value: Any?,
                                     selected: Boolean,
                                     expanded: Boolean,
                                     leaf: Boolean,
                                     row: Int,
                                     hasFocus: Boolean) {
    setTextAlign(SwingConstants.LEFT)
    if (value is ArchiveTreeNode) {
      val model = tree.model as ApkTreeModel
      val archiveEntry = value.data
      val fieldValue = archiveEntry.getAlignmentFinding(model.extractNativeLibs)
      append(fieldValue.text)
      if (fieldValue.hasWarning) icon = AllIcons.General.BalloonWarning
    }
  }
}