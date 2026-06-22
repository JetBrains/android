/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.publishing

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface AppPublisher {
  /** The unique identifier of the app publisher. */
  val id: String

  /** The user-facing display name of this publisher. */
  val displayName: String

  /**
   * Determines if the publisher is available.
   *
   * This method must remain fast, non-blocking, and free of heavy computations since it is queried frequently by the action system to
   * determine menu visibility.
   */
  fun isAvailable(): Boolean

  /**
   * Publisher can decide on the next steps they would like to take to publish the app. These can be showing wizard, background task,
   * opening a separate tool window, etc.
   */
  fun publishApp(project: Project, context: AppPublishingContext)

  companion object {
    val EP_NAME = ExtensionPointName.create<AppPublisher>("com.android.tools.idea.publishing.appPublisher")

    fun getPublisher(publisherId: String) = EP_NAME.findFirstSafe { it.id == publisherId }
  }
}
