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
package com.android.tools.componenttree.util

import com.android.tools.componenttree.api.NodeType
import com.intellij.util.ui.TextTransferable
import java.awt.Component
import java.awt.datatransfer.Transferable
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.tree.TreeCellRenderer

class StyleNodeType : NodeType<Style> {
  override val clazz = Style::class.java

  override fun parentOf(node: Style) = node.parent

  override fun childrenOf(node: Style) = node.children

  override fun toSearchString(node: Style) = node.name

  override fun createRenderer(): TreeCellRenderer = StyleRenderer()

  override fun createTransferable(node: Style): Transferable =
    TextTransferable(StringBuffer("style:${node.name}"))
}

class StyleRenderer : TreeCellRenderer {
  private val label = JLabel()

  override fun getTreeCellRendererComponent(
    tree: JTree?,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean,
  ): Component {
    label.text = (value as? Style)?.name ?: ""
    return label
  }
}
