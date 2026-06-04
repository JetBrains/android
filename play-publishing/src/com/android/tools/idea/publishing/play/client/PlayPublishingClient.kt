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
package com.android.tools.idea.publishing.play.client

import com.android.tools.idea.publishing.play.client.type.App
import com.android.tools.idea.publishing.play.client.type.AppConfig
import com.android.tools.idea.publishing.play.client.type.AppEdit
import com.android.tools.idea.publishing.play.client.type.Bundle
import com.android.tools.idea.publishing.play.client.type.Developer
import com.android.tools.idea.publishing.play.client.type.Track
import com.intellij.openapi.components.service

interface PlayPublishingClient {

  suspend fun listApps(): List<App>

  suspend fun listDevelopers(): List<Developer>

  suspend fun createAppRecord(developerId: Long, appConfig: AppConfig): AppConfig

  suspend fun insertEdit(packageName: String): AppEdit

  suspend fun listEditTracks(packageName: String, editId: String): List<Track>

  suspend fun uploadBundle(packageName: String, editId: String, bundlePath: String): Bundle

  suspend fun createRelease(
    packageName: String,
    editId: String,
    releaseName: String,
    releaseNotes: Map<String, String>,
    versionCode: Int,
    trackId: String,
  )

  suspend fun commitEdit(packageName: String, editId: String)

  companion object {
    @JvmStatic fun getInstance(): PlayPublishingClient = service()
  }
}

class PlayPublishingException(message: String, cause: Throwable? = null) : Exception(message, cause)
