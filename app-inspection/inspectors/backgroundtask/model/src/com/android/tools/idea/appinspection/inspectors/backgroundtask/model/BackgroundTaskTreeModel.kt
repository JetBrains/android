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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

typealias BackgroundTaskOnFilteredListener = (filter: String?) -> Unit

class BackgroundTaskTreeModel(
  private val client: BackgroundTaskInspectorClient,
  scope: CoroutineScope,
  uiDispatcher: CoroutineDispatcher
) : DefaultTreeModel(DefaultMutableTreeNode()) {
  private val nodeMap = mutableMapOf<BackgroundTaskEntry, DefaultMutableTreeNode>()
  private val parentFinder: (BackgroundTaskEntry) -> DefaultMutableTreeNode
  private val filterListeners = mutableListOf<BackgroundTaskOnFilteredListener>()

  /** A Mapping from work id to its related [JobEntry]. */
  private val workIdJobMap = mutableMapOf<String, JobEntry>()

  private var currentSortComparator: Comparator<*>? = null

  /** The category parent nodes. */
  private val categoryRoots: List<DefaultMutableTreeNode>

  var filterTag: String? = null
    set(value) {
      if (field != value) {
        field = value
        refreshTree()
        filterListeners.forEach { listener -> listener(value) }
      }
    }

  init {
    val mutableRoot = root as DefaultMutableTreeNode
    val worksNode = BackgroundTaskCategoryNode("Workers", "No workers have been detected.")
    val jobsNode = BackgroundTaskCategoryNode("Jobs", "No jobs have been detected.")
    val alarmsNode = BackgroundTaskCategoryNode("Alarms", "No alarms have been detected.")
    val wakesNode = BackgroundTaskCategoryNode("WakeLocks", "No wake locks have been detected.")
    categoryRoots = listOf(worksNode, jobsNode, alarmsNode, wakesNode)
    categoryRoots.forEach { mutableRoot.add(it) }
    parentFinder = { entry ->
      when (entry) {
        is WorkEntry -> worksNode
        is JobEntry -> {
          // Link the job under its target work entry.
          entry.targetWorkId?.let { client.getEntry(it) }?.let { nodeMap[it] } ?: jobsNode
        }
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

                if (entry is JobEntry) {
                  entry.targetWorkId?.let { workIdJobMap[it] = entry }
                } else if (entry is WorkEntry) {
                  // Find affiliated jobs for the work.
                  workIdJobMap[entry.id]?.let { jobEntry -> nodeMap[jobEntry] }?.let { jobNode ->
                    jobsNode.remove(jobNode)
                    nodeStructureChanged(jobsNode)
                    newNode.add(jobNode)
                  }
                }
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
            workIdJobMap.remove(entry.id)?.let { jobEntry -> nodeMap.remove(jobEntry) }
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

  fun addOnFilteredListener(listener: BackgroundTaskOnFilteredListener) {
    filterListeners.add(listener)
  }

  fun getTreeNode(id: String) = nodeMap[client.getEntry(id)]

  fun getJobUnderWork(workId: String) = workIdJobMap[workId]

  val allTags
    get() = nodeMap.keys.flatMap { entry -> entry.tags }.toSortedSet().toList()

  private fun BackgroundTaskEntry.acceptedByFilter() = filterTag == null || tags.contains(filterTag)

  fun sort(comparator: Comparator<*>) {
    currentSortComparator = comparator
    refreshTree()
  }

  /** Reconstructs the entire tree while respecting the current filter and sort ordering. */
  private fun refreshTree() {
    categoryRoots.forEach { it.removeAllChildren() }
    val groupedByParentNode =
      nodeMap.entries.filter { entry -> entry.key.acceptedByFilter() }.groupBy { entry ->
        parentFinder(entry.key)
      }

    (currentSortComparator?.let { comparator ->
        groupedByParentNode.mapValues { entry ->
          val result =
            entry.value.sortedWith { o1, o2 ->
              val value =
                (comparator as Comparator<DefaultMutableTreeNode>).compare(o1.value, o2.value)
              value
            }
          result
        }
      }
        ?: groupedByParentNode).forEach { (parent, children) ->
      children.forEach { parent.add(it.value) }
    }

    nodeStructureChanged(root)
  }
}
