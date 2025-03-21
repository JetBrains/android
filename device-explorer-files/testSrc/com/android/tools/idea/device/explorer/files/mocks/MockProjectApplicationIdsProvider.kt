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
package com.android.tools.idea.device.explorer.files.mocks

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.projectsystem.ProjectApplicationIdsProvider
import com.android.tools.idea.projectsystem.ProjectApplicationIdsProvider.Companion.PROJECT_APPLICATION_IDS_CHANGED_TOPIC
import com.intellij.openapi.project.Project

/** Test implementation of [ProjectApplicationIdsProvider] */
internal class MockProjectApplicationIdsProvider(
  private val project: Project,
  vararg initialValue: String,
) : ProjectApplicationIdsProvider {
  private var applicationIds = initialValue.toSet()

  @UiThread
  fun setApplicationIds(vararg value: String) {
    val newIds = value.toSet()
    if (applicationIds != newIds) {
      applicationIds = newIds
      project.messageBus.syncPublisher(PROJECT_APPLICATION_IDS_CHANGED_TOPIC).applicationIdsChanged(newIds)
    }
  }

  override fun getPackageNames() = applicationIds
}
