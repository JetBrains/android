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
import com.intellij.ui.SimpleColoredComponent
import icons.StudioIcons
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
          component.icon = StudioIcons.DatabaseInspector.DATABASE
          component.append(userObject.name)
        }

        is SqliteTable -> {
          component.icon = StudioIcons.DatabaseInspector.TABLE
          component.append(userObject.name)
        }

        is SqliteColumn -> {
          if (userObject.inPrimaryKey) component.icon = StudioIcons.DatabaseInspector.PRIMARY_KEY
          else component.icon = StudioIcons.DatabaseInspector.COLUMN
          component.append(userObject.name)
          component.append(" : ")
          component.append(userObject.affinity.name.toLowerCase(Locale.US))
          component.append(if (userObject.isNullable) "" else ", not null")
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