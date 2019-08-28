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
package com.android.tools.idea.whatsnew.assistant

import com.android.tools.idea.assistant.AssistantBundleCreator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.apache.http.concurrent.FutureCallback
import java.lang.RuntimeException

class WhatsNewCheckVersionTask(
  project: Project,
  private val uiCallback: FutureCallback<Boolean>
  ): Task.Backgroundable(project, "Checking What's New Assistant version...") {

  private var isNewVersion = false

  override fun run(indicator: ProgressIndicator) {
    val bundleCreator = AssistantBundleCreator.EP_NAME.findExtension(WhatsNewBundleCreator::class.java)
    isNewVersion = bundleCreator!!.isNewConfigVersion
  }

  override fun onSuccess() {
    super.onSuccess()
    uiCallback.completed(isNewVersion)
  }

  override fun onCancel() {
    super.onCancel()
    uiCallback.cancelled()
  }

  override fun onThrowable(error: Throwable) {
    uiCallback.failed(RuntimeException(error))
  }
}