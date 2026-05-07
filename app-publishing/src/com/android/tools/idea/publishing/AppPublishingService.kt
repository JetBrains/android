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

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class AppPublishingService(private val project: Project) {

  fun isPublisherAvailable(publisherId: String) = AppPublisher.getPublisher(publisherId)?.isAvailable() ?: false

  /**
   * Calls the publisher [publisherId] to start the process of publishing the app. The publisher may have additional steps that require user
   * interaction.
   */
  fun publishApp(publisherId: String, context: AppPublishingContext) {
    val publisher = AppPublisher.getPublisher(publisherId) ?: throw IllegalArgumentException("Publisher with ID $publisherId not found")
    if (!isPublisherAvailable(publisherId)) {
      throw IllegalStateException("Publisher with ID $publisherId is not available.")
    }
    publisher.publishApp(project, context)
  }
}
