/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.daemon

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.projectsystem.gradle.IdeGooglePlaySdkIndex
import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import java.util.EventListener

class PsSdkIndexCheckerDaemon(
  parentDisposable: Disposable,
  private val project: PsProject
) : PsDaemon(parentDisposable) {
  private val eventDispatcher = EventDispatcher.create(SdkIndexIssuesListener::class.java)

  fun queueCheck() {
    mainQueue.queue(RefreshSdkIndexIssues())
  }

  fun add(@UiThread listener: () -> Unit, parentDisposable: Disposable) {
    eventDispatcher.addListener(
      object : SdkIndexIssuesListener {
        @UiThread
        override fun availableIssues() = listener()
      }, parentDisposable)
  }

  private inner class RefreshSdkIndexIssues : Update(project) {
    override fun run() {
      IdeGooglePlaySdkIndex.initializeAndSetFlags();
      resultsUpdaterQueue.queue(SdkIndexAvailable())
    }
  }

  private inner class SdkIndexAvailable : Update(project) {
    @UiThread
    override fun run() {
      eventDispatcher.multicaster.availableIssues()
    }
  }


  override val mainQueue: MergingUpdateQueue = createQueue("Project Structure Daemon SDK Index Checker", null)
  override val resultsUpdaterQueue: MergingUpdateQueue = createQueue("Project Structure SDK Index Results Updater",
                                                                     MergingUpdateQueue.ANY_COMPONENT)

  private interface SdkIndexIssuesListener : EventListener {
    @UiThread
    fun availableIssues()
  }
}