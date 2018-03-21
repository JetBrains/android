/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:JvmName("SyncUtil")

package com.android.tools.idea.util

import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResultListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.MessageBusConnection

/**
 * Registers [listener] to be notified of any sync result broadcast on [PROJECT_SYSTEM_SYNC_TOPIC] on [project]'s message bus
 * until the next successful sync completes. The [listener] maintains its subscription to [PROJECT_SYSTEM_SYNC_TOPIC] until either
 *
 * 1) a sync completes successfully and [listener] is notified of the success,
 * 2) [parentDisposable] is disposed, or
 * 3) the [MessageBusConnection] returned by this method is disposed
 */
@JvmOverloads
fun listenUntilNextSuccessfulSync(project: Project, parentDisposable: Disposable = project, listener: SyncResultListener)
    : MessageBusConnection {

  val connection = project.messageBus.connect(parentDisposable)

  connection.subscribe(PROJECT_SYSTEM_SYNC_TOPIC, object: SyncResultListener {
    override fun syncEnded(result: SyncResult) {
      if (result.isSuccessful) {
        Disposer.dispose(connection)
      }
      listener.syncEnded(result)
    }
  })

  return connection
}
