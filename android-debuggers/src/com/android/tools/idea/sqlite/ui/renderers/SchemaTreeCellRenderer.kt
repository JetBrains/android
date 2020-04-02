/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.sqlite.ui.renderers

import com.android.tools.idea.sqlite.model.SqliteColumn
import com.android.tools.idea.sqlite.model.SqliteDatabase
import com.android.tools.idea.sqlite.model.SqliteTable
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import icons.StudioIcons
import java.util.Locale
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer

/**
 * Implementation of [TreeCellRenderer] for nodes of the [JTree] in the [SqliteSchemaPanel].
 */
class SchemaTreeCellRenderer : ColoredTreeCellRenderer() {
  private val colorTextAttributes = SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.gray)

  override fun customizeCellRenderer(
    tree: JTree,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean
  ) {
    if (value is DefaultMutableTreeNode) {
      when (val userObject = value.userObject) {
        is SqliteDatabase -> {
          icon = StudioIcons.DatabaseInspector.DATABASE
          append(userObject.name)
        }

        is SqliteTable -> {
          icon = StudioIcons.DatabaseInspector.TABLE
          append(userObject.name)
        }

        is SqliteColumn -> {
          if (userObject.inPrimaryKey) icon = StudioIcons.DatabaseInspector.PRIMARY_KEY
          else icon = StudioIcons.DatabaseInspector.COLUMN
          append(userObject.name)
          append("  :  ", colorTextAttributes)
          append(userObject.affinity.name.toUpperCase(Locale.US), colorTextAttributes)
          append(if (userObject.isNullable) "" else ", NOT NULL", colorTextAttributes)
        }

        // String (e.g. "Tables" node)
        is String -> {
          append(userObject)
        }
      }
    }
  }
}