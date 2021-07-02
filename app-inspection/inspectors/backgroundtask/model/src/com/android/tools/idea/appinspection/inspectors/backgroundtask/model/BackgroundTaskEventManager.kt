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

import androidx.work.inspection.WorkManagerInspectorProtocol
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.AlarmEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.BackgroundTaskEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.JobEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WakeLockEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WorkEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.createBackgroundTaskEntry
import kotlinx.coroutines.launch
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Class used to manage entry data from a [BackgroundTaskInspectorClient] and update its [treeModel] accordingly.
 *
 * @param client: a client that feeds background task entry data.
 */
class BackgroundTaskEventManager(client: BackgroundTaskInspectorClient) {
  private val treeRoot = DefaultMutableTreeNode()
  private val nodeMap = mutableMapOf<String, DefaultMutableTreeNode>()
  val treeModel = DefaultTreeModel(treeRoot)

  private val worksNode = DefaultMutableTreeNode("Works")
  private val jobsNode = DefaultMutableTreeNode("Jobs")
  private val alarmsNode = DefaultMutableTreeNode("Alarms")
  private val wakesNode = DefaultMutableTreeNode("WakeLocks")

  init {
    expandRootNode()
    client.addEventListener {
      client.scope.launch(client.uiThread) {
        handleEvent(it)
      }
    }
  }

  private fun expandRootNode() {
    treeRoot.add(worksNode)
    treeRoot.add(jobsNode)
    treeRoot.add(alarmsNode)
    treeRoot.add(wakesNode)
  }

  fun getTreeNode(entryId: String): DefaultMutableTreeNode? {
    return nodeMap[entryId]
  }

  /**
   * Returns a chain of works with topological ordering containing the selected work.
   *
   * @param id id of the selected work.
   */
  fun getOrderedWorkChain(id: String): List<WorkManagerInspectorProtocol.WorkInfo> {
    val work = nodeMap[id]?.getWorkInfo() ?: return listOf()
    val connectedWorks = mutableListOf(work)
    val visitedWorks = mutableSetOf(work)
    val orderedWorks = mutableListOf<WorkManagerInspectorProtocol.WorkInfo>()
    // Number of prerequisites not loaded into [orderedWorks].
    val degreeMap = mutableMapOf<WorkManagerInspectorProtocol.WorkInfo, Int>()
    var index = 0

    // Find works connected with the selected work and load works without prerequisites.
    while (index < connectedWorks.size) {
      val currentWork = connectedWorks[index]
      val previousWorks = currentWork.prerequisitesList.mapNotNull { nodeMap[it]?.getWorkInfo() }
      val nextWorks = currentWork.dependentsList.mapNotNull { nodeMap[it]?.getWorkInfo() }
      degreeMap[currentWork] = previousWorks.size
      if (previousWorks.isEmpty()) {
        orderedWorks.add(currentWork)
      }
      for (connectedWork in (previousWorks + nextWorks)) {
        if (!visitedWorks.contains(connectedWork)) {
          visitedWorks.add(connectedWork)
          connectedWorks.add(connectedWork)
        }
      }
      index += 1
    }
    // Load works with topological ordering.
    index = 0
    while (index < orderedWorks.size) {
      val currentWork = orderedWorks[index]
      val nextWorks = currentWork.dependentsList.mapNotNull { nodeMap[it]?.getWorkInfo() }
      for (nextWork in nextWorks) {
        degreeMap[nextWork] = degreeMap[nextWork]!! - 1
        if (degreeMap[nextWork] == 0) {
          orderedWorks.add(nextWork)
        }
      }
      index += 1
    }
    return orderedWorks
  }

  private fun handleEvent(event: EventWrapper) {
    val newEntry = createBackgroundTaskEntry(event)
    nodeMap[newEntry.id]?.let { oldNode ->
      // Update or remove an existing node.
      val oldEntry = oldNode.userObject as BackgroundTaskEntry
      if (oldEntry.isValid) {
        oldEntry.consume(event)
        treeModel.nodeChanged(oldNode)
      }
      else {
        nodeMap.remove(newEntry.id)
        val parent = oldNode.parent
        oldNode.removeFromParent()
        treeModel.nodeStructureChanged(parent)
      }
    } ?: DefaultMutableTreeNode(newEntry).let { newNode ->
      // Insert a new node.
      newEntry.consume(event)
      val parent = when (newEntry) {
        is WorkEntry -> worksNode
        is JobEntry -> jobsNode
        is AlarmEntry -> alarmsNode
        is WakeLockEntry -> wakesNode
        else -> return
      }
      parent.add(newNode)
      nodeMap[newEntry.id] = newNode
      treeModel.nodeStructureChanged(parent)
    }
  }

  private fun DefaultMutableTreeNode.getWorkInfo() = (userObject as? WorkEntry)?.getWorkInfo()
}
