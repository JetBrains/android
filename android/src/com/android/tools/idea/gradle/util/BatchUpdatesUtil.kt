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
package com.android.tools.idea.gradle.util

import com.intellij.ide.file.BatchFileChangeListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.impl.stores.BatchUpdateListener
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.executeProjectChangeAction
import com.intellij.openapi.project.Project

object BatchUpdatesUtil {
  private val LOG = Logger.getInstance(BatchUpdatesUtil::class.java)

  /**
   * Start batch update, thus ensuring that any VFS/root model changes do not immediately cause associated indexing. This
   * helps avoid redundant indexing operations and contention during a massive model update (for example,
   * the one happening during the project setup phase of gradle sync).
   */
  @JvmStatic
  fun startBatchUpdate(project: Project) {
    LOG.info("Starting batch update for project: " + project.toString())
    ApplicationManager.getApplication().invokeLater(
      Runnable {
        executeProjectChangeAction(true, object : DisposeAwareProjectChange(project) {
          override fun execute() {
            project.messageBus.syncPublisher(BatchUpdateListener.TOPIC).onBatchUpdateStarted()
            ApplicationManager.getApplication().messageBus
              .syncPublisher(BatchFileChangeListener.TOPIC).batchChangeStarted(project, "batch update")
          }
        })
      }, project.disposed)
  }

  /**
   * Finish batch update, thus allowing any model changes which happened along the way to trigger indexing.
   */
  @JvmStatic
  fun finishBatchUpdate(project: Project) {
    LOG.info("Finishing batch update for project: " + project.toString())
    ApplicationManager.getApplication().invokeLater(
      Runnable {
        executeProjectChangeAction(true, object : DisposeAwareProjectChange(project) {
          override fun execute() {
            project.messageBus.syncPublisher(BatchUpdateListener.TOPIC).onBatchUpdateFinished()
            ApplicationManager.getApplication().messageBus.syncPublisher(BatchFileChangeListener.TOPIC).batchChangeCompleted(project)
          }
        })
      }, project.disposed)
  }
}
