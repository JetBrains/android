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
import com.android.tools.idea.publishing.play.client.type.Artifact
import com.android.tools.idea.publishing.play.client.type.Bundle
import com.android.tools.idea.publishing.play.client.type.Developer
import com.android.tools.idea.publishing.play.client.type.Track

/** A fake implementation of [PlayPublishingClient] that is easy to extend for new methods. */
class FakePlayPublishingClient : PlayPublishingClient {

  var config = Config()

  override suspend fun listApps(): List<App> {
    return emptyList()
  }

  override suspend fun listDevelopers(): List<Developer> = config.listDeveloperCall()

  override suspend fun createAppRecord(developerId: Long, appConfig: AppConfig): AppConfig =
    config.createAppRecordCall(developerId, appConfig)

  override suspend fun insertEdit(packageName: String): AppEdit = AppEdit("", "")

  override suspend fun listEditTracks(packageName: String, editId: String): List<Track> {
    return emptyList()
  }

  override suspend fun uploadArtifact(packageName: String, editId: String, artifactPath: String, isBundle: Boolean): Artifact {
    return Bundle(0, "", "")
  }

  override suspend fun createRelease(
    packageName: String,
    editId: String,
    releaseName: String,
    releaseNotes: Map<String, String>,
    versionCode: Int,
    trackId: String,
  ) = Unit

  override suspend fun commitEdit(packageName: String, editId: String) = Unit

  data class Config(
    val listDeveloperCall: suspend () -> List<Developer> = { emptyList() },
    val createAppRecordCall: suspend (Long, AppConfig) -> AppConfig = { _, cfg -> cfg },
  )
}
