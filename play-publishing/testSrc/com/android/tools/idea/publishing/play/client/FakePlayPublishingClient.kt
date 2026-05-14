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

  override suspend fun listApps(): List<App> = config.listAppsCall()

  override suspend fun listDevelopers(): List<Developer> = config.listDeveloperCall()

  override suspend fun createAppRecord(developerId: Long, appConfig: AppConfig): AppConfig =
    config.createAppRecordCall(developerId, appConfig)

  override suspend fun insertEdit(packageName: String): AppEdit = config.insertEditCall(packageName)

  override suspend fun listEditTracks(packageName: String, editId: String): List<Track> = config.listEditTracksCall(packageName, editId)

  override suspend fun uploadArtifact(packageName: String, editId: String, artifactPath: String, isBundle: Boolean): Artifact =
    config.uploadArtifactCall(packageName, editId, artifactPath, isBundle)

  override suspend fun createRelease(
    packageName: String,
    editId: String,
    releaseName: String,
    releaseNotes: Map<String, String>,
    versionCode: Int,
    trackId: String,
  ) = config.createReleaseCall(packageName, editId, releaseName, releaseNotes, versionCode, trackId)

  override suspend fun commitEdit(packageName: String, editId: String) = config.commitEditCall(packageName, editId)

  data class Config(
    val listAppsCall: suspend () -> List<App> = { emptyList() },
    val listDeveloperCall: suspend () -> List<Developer> = { emptyList() },
    val createAppRecordCall: suspend (Long, AppConfig) -> AppConfig = { _, cfg -> cfg },
    val insertEditCall: suspend (String) -> AppEdit = { AppEdit("1", "1") },
    val listEditTracksCall: suspend (String, String) -> List<Track> = { _, _ -> emptyList() },
    val uploadArtifactCall: suspend (String, String, String, Boolean) -> Artifact = { _, _, _, _ -> Bundle(0, "", "") },
    val createReleaseCall: suspend (String, String, String, Map<String, String>, Int, String) -> Unit = { _, _, _, _, _, _ -> },
    val commitEditCall: suspend (String, String) -> Unit = { _, _ -> },
  )
}
