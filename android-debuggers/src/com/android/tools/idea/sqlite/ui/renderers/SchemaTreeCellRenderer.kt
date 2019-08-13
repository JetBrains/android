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
import com.android.tools.idea.sqlite.model.getFormattedSqliteDatabaseName
import com.intellij.icons.AllIcons
import com.intellij.ui.SimpleColoredComponent
import java.awt.Component
import java.util.Locale
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer

/**
 * Implementation of [TreeCellRenderer] for nodes of the [JTree] in the [SqliteSchemaPanel].
 */
class SchemaTreeCellRenderer : TreeCellRenderer {
  private val component = SimpleColoredComponent()

  override fun getTreeCellRendererComponent(
    tree: JTree?,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean
  ): Component {
    component.clear()
    if (value is DefaultMutableTreeNode) {
      when (val userObject = value.userObject) {
        is SqliteDatabase -> {
          component.icon = AllIcons.Nodes.DataTables
          component.append(userObject.getFormattedSqliteDatabaseName())
        }

        is SqliteTable -> {
          component.icon = AllIcons.Nodes.DataTables
          component.append(userObject.name)
        }

        is SqliteColumn -> {
          component.icon = AllIcons.Nodes.DataColumn
          component.append(userObject.name)
          component.append(" : ")
          component.append(userObject.type.name.toLowerCase(Locale.US))
        }

        // String (e.g. "Tables" node)
        is String -> {
          component.append(userObject)
        }
      }
    }
    return component
  }
}