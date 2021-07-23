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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class BackgroundTaskTreeModel(private val client: BackgroundTaskInspectorClient,
                              scope: CoroutineScope,
                              uiDispatcher: CoroutineDispatcher) : DefaultTreeModel(DefaultMutableTreeNode()) {
  private val nodeMap = mutableMapOf<BackgroundTaskEntry, DefaultMutableTreeNode>()
  private val parentFinder: (BackgroundTaskEntry) -> DefaultMutableTreeNode

  var filterTag: String? = null
    set(value) {
      if (field != value) {
        field = value
        root.children().toList().forEach {
          (it as DefaultMutableTreeNode).removeAllChildren()
        }
        nodeMap.entries
          .filter { entry -> entry.key.acceptedByFilter() }
          .forEach { entry ->
            parentFinder(entry.key).add(entry.value)
          }
        root.children().toList().forEach {
          nodeStructureChanged(it)
        }
      }
    }

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
    parentFinder = { entry ->
      when (entry) {
        is WorkEntry -> worksNode
        is JobEntry -> jobsNode
        is AlarmEntry -> alarmsNode
        is WakeLockEntry -> wakesNode
        else -> throw RuntimeException()
      }
    }

    client.addEntryUpdateEventListener { type, entry ->
      scope.launch(uiDispatcher) {
        when (type) {
          EntryUpdateEventType.ADD -> {
            DefaultMutableTreeNode(entry).let { newNode ->
              nodeMap[entry] = newNode
              if (entry.acceptedByFilter()) {
                val parent = parentFinder(entry)
                parent.add(newNode)
                nodeStructureChanged(parent)
              }
            }
          }
          EntryUpdateEventType.UPDATE -> {
            if (entry.acceptedByFilter()) {
              nodeChanged(nodeMap[entry])
            }
          }
          EntryUpdateEventType.REMOVE -> {
            val node = nodeMap.remove(entry)!!
            if (entry.acceptedByFilter()) {
              val parent = node.parent
              node.removeFromParent()
              nodeStructureChanged(parent)
            }
          }
        }
      }
    }
  }

  fun getTreeNode(id: String) = nodeMap[client.getEntry(id)]

  val allTags get() = nodeMap.keys.flatMap { entry -> entry.tags }.toSortedSet().toList()

  private fun BackgroundTaskEntry.acceptedByFilter() = filterTag == null || tags.contains(filterTag)
}
