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
import backgroundtask.inspection.BackgroundTaskInspectorProtocol
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.BackgroundTaskEvent.MetadataCase.ALARM_CANCELLED
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.BackgroundTaskEvent.MetadataCase.ALARM_FIRED
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.BackgroundTaskEvent.MetadataCase.ALARM_SET
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.BackgroundTaskEvent.MetadataCase.JOB_FINISHED
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.BackgroundTaskEvent.MetadataCase.JOB_SCHEDULED
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.BackgroundTaskEvent.MetadataCase.JOB_STARTED
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.BackgroundTaskEvent.MetadataCase.JOB_STOPPED
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.BackgroundTaskEvent.MetadataCase.WAKE_LOCK_ACQUIRED
import backgroundtask.inspection.BackgroundTaskInspectorProtocol.BackgroundTaskEvent.MetadataCase.WAKE_LOCK_RELEASED
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.AlarmEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.BackgroundTaskEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.JobEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WakeLockEntry
import com.android.tools.idea.appinspection.inspectors.backgroundtask.model.entries.WorkEntry
import kotlinx.coroutines.launch
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Class used to build a tree model for a table with basic information for all background tasks.
 */
class BackgroundTaskTreeModel(client: BackgroundTaskInspectorClient) {
  private val treeRoot = DefaultMutableTreeNode()
  private val nodeMap = mutableMapOf<String, EntryNode<*>>()
  val treeModel = DefaultTreeModel(treeRoot)

  inner class EntriesNode<T : BackgroundTaskEntry>(name: String,
                                                   private val entryCreator: (id: String) -> T) : DefaultMutableTreeNode(name) {

    fun consume(id: String, event: Any) {
      val targetChild = children().asSequence().firstOrNull { child ->
        (child as EntryNode<*>).entry.id == id
      } ?: EntryNode(entryCreator(id)).also {
        add(it)
        nodeMap[it.entry.id] = it
        treeModel.nodeStructureChanged(this)
      }

      (targetChild as EntryNode<*>).entry.consume(event)
      if (targetChild.entry.isValid) {
        treeModel.nodeChanged(targetChild)
      }
      else {
        nodeMap.remove(targetChild.entry.id)
        remove(targetChild)
        treeModel.nodeStructureChanged(this)
      }
    }
  }

  inner class EntryNode<T : BackgroundTaskEntry>(entry: T) : DefaultMutableTreeNode(entry) {
    val entry = userObject as BackgroundTaskEntry
  }

  private val worksNode = EntriesNode("Works") { id -> WorkEntry(id) }
  private val jobsNode = EntriesNode("Jobs") { id -> JobEntry(id) }
  private val alarmsNode = EntriesNode("Alarms") { id -> AlarmEntry(id) }
  private val wakesNode = EntriesNode("WakeLocks") { id -> WakeLockEntry(id) }

  init {
    treeRoot.add(worksNode)
    treeRoot.add(jobsNode)
    treeRoot.add(alarmsNode)
    treeRoot.add(wakesNode)
    client.addEventListener {
      client.scope.launch(client.uiThread) {
        handleEvent(it)
      }
    }
  }

  fun getTreeNode(entryId: String): DefaultMutableTreeNode? {
    return nodeMap[entryId]
  }

  fun getEntry(entryId: String): BackgroundTaskEntry? {
    return nodeMap[entryId]?.entry
  }

  private fun handleEvent(event: Any) {
    when (event) {
      is WorkManagerInspectorProtocol.Event -> {
        worksNode.consume(event.getId(), event)
      }
      is BackgroundTaskInspectorProtocol.Event -> {
        val id = event.getId()
        when (event.backgroundTaskEvent.metadataCase) {
          JOB_SCHEDULED, JOB_STARTED, JOB_STOPPED, JOB_FINISHED -> {
            jobsNode.consume(id.toEntryId(), event)
          }
          ALARM_SET, ALARM_CANCELLED, ALARM_FIRED -> {
            alarmsNode.consume(id.toEntryId(), event)
          }
          WAKE_LOCK_ACQUIRED, WAKE_LOCK_RELEASED -> {
            wakesNode.consume(id.toEntryId(), event)
          }
          else -> throw RuntimeException()
        }
      }
    }
  }

  /**
   * Converts an integer based ID to String ID that won't overlap with the UUID based IDs of work entries
   */
  private fun Long.toEntryId() = "L-$this"
}
