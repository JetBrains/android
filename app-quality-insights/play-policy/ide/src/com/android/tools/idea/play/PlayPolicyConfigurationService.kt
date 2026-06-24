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
package com.android.tools.idea.play

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.androidAppId
import com.android.tools.idea.insights.getHolderModules
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.utils.associateWithNotNull
import com.google.android.tools.play.client.metadata.PlayMetadataClient
import com.google.gct.login2.GoogleLoginService
import com.google.gct.login2.fstLoginFeature
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.TestOnly

@Service(Service.Level.PROJECT)
class PlayPolicyConfigurationService(private val project: Project, private val coroutineScope: CoroutineScope) : Disposable {

  private val mutex = Mutex()

  @TestOnly var overrideMetadataClient: PlayMetadataClient? = null

  private val defaultMetadataClient: PlayMetadataClient =
    PlayMetadataClient.newClient(StudioFlags.PLAY_PUBLISHING_ENDPOINT.get()) { fstLoginFeature.oAuthToken() }
  private val metadataClient: PlayMetadataClient
    get() = overrideMetadataClient ?: defaultMetadataClient

  // In-memory cache of the latest fetched metadata as a StateFlow
  private val _cachedMetadata = MutableStateFlow<Map<String, PlayMetadata>>(emptyMap())
  val cachedMetadata: StateFlow<Map<String, PlayMetadata>> = _cachedMetadata.asStateFlow()

  init {
    project.messageBus
      .connect(coroutineScope)
      .subscribe(
        PROJECT_SYSTEM_SYNC_TOPIC,
        ProjectSystemSyncManager.SyncResultListener { result ->
          if (result.isSuccessful) {
            coroutineScope.launch { refreshMetadata() }
          }
        },
      )

    coroutineScope.launch {
      GoogleLoginService.instance.activeUserFlow
        .map { it?.email }
        .distinctUntilChanged()
        .collectLatest { email ->
          if (email != null) {
            refreshMetadata()
          } else {
            _cachedMetadata.value = emptyMap()
          }
        }
    }
  }

  suspend fun refreshMetadata(): Map<String, PlayMetadata> {
    if (GoogleLoginService.instance.activeUserFlow.value == null) {
      return emptyMap()
    }
    mutex.withLock {
      val appIds = readAction { project.getHolderModules().mapNotNull { it.androidAppId } }
      return appIds
        .associateWithNotNull { appId ->
          try {
            val applicationData = metadataClient.getApplication(appId).takeIf { it.isNotEmpty() } ?: return@associateWithNotNull null
            val declarationData = metadataClient.getAppContentDeclaration(appId) ?: ""
            PlayMetadata(appId, applicationData, declarationData)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            // Package not found is an expected reason for empty result.
            if (e.message?.lowercase()?.contains("package not found") != true) {
              thisLogger().warn("Failed to fetch Play metadata for appId: $appId", e)
            }
            null
          }
        }
        .also { _cachedMetadata.value = it }
    }
  }

  override fun dispose() {
    overrideMetadataClient?.close()
    defaultMetadataClient.close()
  }

  companion object {
    @JvmStatic fun getInstance(project: Project) = project.service<PlayPolicyConfigurationService>()
  }
}

data class PlayMetadata(val applicationId: String, val applicationInfoJson: String, val appContentDeclarationJson: String)
