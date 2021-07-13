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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.model

import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.AlarmEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.BackgroundTaskEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.JobEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WakeLockEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WorkEntry
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class BackgroundTaskTreeModel(private val client: BackgroundTaskInspectorClient) : DefaultTreeModel(DefaultMutableTreeNode()) {
  private val nodeMap = mutableMapOf<BackgroundTaskEntry, DefaultMutableTreeNode>()

  init {
    val mutableRoot = root as DefaultMutableTreeNode
    val worksNode = DefaultMutableTreeNode("Works")
    val jobsNode = DefaultMutableTreeNode("Jobs")
    val alarmsNode = DefaultMutableTreeNode("Alarms")
    val wakesNode = DefaultMutableTreeNode("WakeLocks")
    mutableRoot.add(worksNode)
    mutableRoot.add(jobsNode)
    mutableRoot.add(alarmsNode)
    mutableRoot.add(wakesNode)

    client.addEntryUpdateEventListener { type, entry ->
      when (type) {
        EntryUpdateEventType.ADD -> {
          val parent = when (entry) {
            is WorkEntry -> worksNode
            is JobEntry -> jobsNode
            is AlarmEntry -> alarmsNode
            is WakeLockEntry -> wakesNode
            else -> throw RuntimeException()
          }
          DefaultMutableTreeNode(entry).let { newNode ->
            nodeMap[entry] = newNode
            parent.add(newNode)
          }

          nodeStructureChanged(parent)
        }
        EntryUpdateEventType.UPDATE -> {
          nodeChanged(nodeMap[entry])
        }
        EntryUpdateEventType.REMOVE -> {
          val node = nodeMap[entry]!!
          val parent = node.parent
          node.removeFromParent()
          nodeStructureChanged(parent)
        }
      }
    }
  }

  fun getTreeNode(id: String) = nodeMap[client.getEntry(id)]
}
