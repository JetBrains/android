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
package com.android.tools.idea.gradle.project.sync

import com.intellij.notification.NotificationGroup
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.project.Project
import com.intellij.util.ThreeState
import com.intellij.util.messages.MessageBusConnection
import org.gradle.util.GradleVersion

/**
 * NOTE: Do not use this interface. Most callers should use `ProjectSystemSyncManager` instead.
 */
interface GradleSyncState {
  val isSyncInProgress: Boolean
  val externalSystemTaskId: ExternalSystemTaskId?
  val lastSyncFinishedTimeStamp: Long
  val lastSyncedGradleVersion: GradleVersion?

  /**
   * Indicates whether the last started Gradle sync has failed.
   *
   * Possible failure causes:
   *   *An error occurred in Gradle (e.g. a missing dependency, or a missing Android platform in the SDK)
   *   *An error occurred while setting up a project using the models obtained from Gradle during sync (e.g. invoking a method that
   *    doesn't exist in an old version of the Android plugin)
   *   *An error in the structure of the project after sync (e.g. more than one module with the same path in the file system)
   */
  fun lastSyncFailed(): Boolean

  fun isSyncNeeded(): ThreeState

  fun subscribe(project: Project, listener: GradleSyncListenerWithRoot, disposable: Disposable): MessageBusConnection

  companion object {
    @JvmField
    val JDK_LOCATION_WARNING_NOTIFICATION_GROUP = NotificationGroup.logOnlyGroup("JDK Location different to JAVA_HOME")

    /**
     * These methods allow the registering of listeners to [GradleSyncState].
     *
     * See [GradleSyncListener] for more details on the different hooks through the syncing process.
     */

    fun subscribe(project: Project, listener: GradleSyncListenerWithRoot, disposable: Disposable): MessageBusConnection {
      return getInstance(project).subscribe(project, listener, disposable)
    }

    @JvmStatic
    fun subscribe(project: Project, listener: GradleSyncListener): MessageBusConnection = subscribe(project, listener, project)

    @JvmStatic
    fun subscribe(project: Project, listener: GradleSyncListener, disposable: Disposable): MessageBusConnection {
      return subscribe(
        project = project,
        listener = GradleSyncListenerAdapter(listener),
        disposable = disposable
      )
    }

    @JvmStatic
    fun getInstance(project: Project): GradleSyncState =
      project.getService(GradleSyncState::class.java)
        ?: if (ApplicationManager.getApplication().isUnitTestMode) object : GradleSyncState {
          override val isSyncInProgress: Boolean = false
          override val externalSystemTaskId: ExternalSystemTaskId? = null
          override val lastSyncFinishedTimeStamp: Long = -1
          override val lastSyncedGradleVersion: GradleVersion? = null
          override fun lastSyncFailed(): Boolean = false
          override fun isSyncNeeded(): ThreeState = ThreeState.NO
          override fun subscribe(project: Project, listener: GradleSyncListenerWithRoot, disposable: Disposable): MessageBusConnection {
            error("Not supported in unit test mode")
          }
        }
        else error("GradleSyncState service is not registered.")
  }
}

private class GradleSyncListenerAdapter(private val listener: GradleSyncListener) : GradleSyncListenerWithRoot {
  override fun syncStarted(project: Project, rootProjectPath: String) {
    listener.syncStarted(project)
  }

  override fun syncFailed(project: Project, errorMessage: String, rootProjectPath: String) {
    listener.syncFailed(project, errorMessage)
  }

  override fun syncSucceeded(project: Project, rootProjectPath: String) {
    listener.syncSucceeded(project)
  }

  override fun syncSkipped(project: Project) {
    listener.syncSkipped(project)
  }

  override fun syncCancelled(project: Project, rootProjectPath: String) {
    listener.syncCancelled(project)
  }
}
