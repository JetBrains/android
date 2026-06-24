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

import com.android.flags.junit.FlagRule
import com.android.testutils.waitForCondition
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.createMainSourceProviderForDefaultTestProjectStructure
import com.android.tools.idea.testing.onEdt
import com.google.android.tools.play.client.metadata.PlayMetadataClient
import com.google.common.truth.Truth.assertThat
import com.google.gct.login2.CredentialedUser
import com.google.gct.login2.GoogleLoginService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.replaceService
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

private fun waitForCondition(condition: () -> Boolean) = waitForCondition(30.seconds, condition)

class PlayPolicyConfigurationServiceTest {

  private val projectRule =
    AndroidProjectRule.withAndroidModels(
        JavaModuleModelBuilder.rootModuleBuilder,
        AndroidModuleModelBuilder(":app1", "debug", createApp("com.example.app1")),
        AndroidModuleModelBuilder(":app2", "debug", createApp("com.example.app2")),
        // Non-app module to verify it is filtered out
        AndroidModuleModelBuilder(":lib", "debug", createLib("com.example.lib")),
      )
      .onEdt()

  @get:Rule val ruleChain = RuleChain(projectRule, EdtRule())
  @get:Rule val flagRule = FlagRule(StudioFlags.PLAY_POLICY_METADATA_EXPORT_ENABLED, true)

  private lateinit var fakeMetadataClient: FakePlayMetadataClient
  private lateinit var mockLoginService: GoogleLoginService
  private val activeUserFlow = MutableStateFlow<CredentialedUser?>(null)
  private lateinit var service: PlayPolicyConfigurationService
  private lateinit var testScope: CoroutineScope

  @Before
  fun setUp() {
    fakeMetadataClient = FakePlayMetadataClient()
    setupMetadataStubs()

    mockLoginService = mock()
    whenever(mockLoginService.activeUserFlow).thenReturn(activeUserFlow)
    ApplicationManager.getApplication().replaceService(GoogleLoginService::class.java, mockLoginService, projectRule.testRootDisposable)

    testScope = CoroutineScope(Dispatchers.Unconfined)
    service = PlayPolicyConfigurationService(projectRule.project, testScope)
    service.overrideMetadataClient = fakeMetadataClient
    projectRule.project.replaceService(PlayPolicyConfigurationService::class.java, service, projectRule.testRootDisposable)

    val mockUser = mock<CredentialedUser>()
    whenever(mockUser.email).thenReturn("user@google.com")
    activeUserFlow.value = mockUser
    waitForCondition { service.cachedMetadata.value.isNotEmpty() }
  }

  @After
  fun tearDown() {
    testScope.cancel()
  }

  private fun setupMetadataStubs() {
    fakeMetadataClient.applications["com.example.app1"] = "app1_info"
    fakeMetadataClient.declarations["com.example.app1"] = "app1_declaration"
    fakeMetadataClient.applications["com.example.app2"] = "app2_info"
    fakeMetadataClient.declarations["com.example.app2"] = "app2_declaration"
  }

  @Test
  fun testRefreshMetadataSuccess() = runTest {
    val metadata = service.refreshMetadata()

    assertThat(metadata).hasSize(2)
    assertThat(metadata["com.example.app1"]).isEqualTo(PlayMetadata("com.example.app1", "app1_info", "app1_declaration"))
    assertThat(metadata["com.example.app2"]).isEqualTo(PlayMetadata("com.example.app2", "app2_info", "app2_declaration"))
    assertThat(metadata["com.example.lib"]).isNull()

    // Verify cache is updated
    assertThat(service.cachedMetadata.value).isEqualTo(metadata)
  }

  @Test
  fun testSyncTriggersRefresh() = runTest {
    // Update server metadata to verify sync re-fetches
    fakeMetadataClient.applications["com.example.app1"] = "app1_info_updated"

    // Emulate sync
    projectRule.project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.SUCCESS)

    // Wait for cache to be updated with new metadata
    waitForCondition { service.cachedMetadata.value["com.example.app1"]?.applicationInfoJson == "app1_info_updated" }

    val metadata = service.cachedMetadata.value
    assertThat(metadata).hasSize(2)
    assertThat(metadata["com.example.app1"]).isEqualTo(PlayMetadata("com.example.app1", "app1_info_updated", "app1_declaration"))
  }

  @Test
  fun testLoginTriggersRefresh() = runTest {
    activeUserFlow.value = null
    waitForCondition { service.cachedMetadata.value.isEmpty() }

    // Initially cache is empty
    assertThat(service.cachedMetadata.value).isEmpty()

    // Mock logged in user
    val mockUser = mock<CredentialedUser>()
    whenever(mockUser.email).thenReturn("user@google.com")

    // Login
    activeUserFlow.value = mockUser

    // Wait for cache to be updated
    waitForCondition { service.cachedMetadata.value.isNotEmpty() }

    assertThat(service.cachedMetadata.value).hasSize(2)

    // Logout
    activeUserFlow.value = null

    // Wait for cache to be cleared
    waitForCondition { service.cachedMetadata.value.isEmpty() }
  }

  @Test
  fun testMetadataProviderReturnsJson() = runTest {
    // Populate service cache
    service.refreshMetadata()

    val provider = MetadataProvider.EP_NAME.getExtensionList(projectRule.project).first()
    val json = provider.get()

    assertThat(json).contains(""""applicationId":"com.example.app1"""")
    assertThat(json).contains(""""applicationInfoJson":"app1_info"""")
    assertThat(json).contains(""""appContentDeclarationJson":"app1_declaration"""")
    assertThat(json).contains(""""applicationId":"com.example.app2"""")
    assertThat(json).contains(""""applicationInfoJson":"app2_info"""")
    assertThat(json).contains(""""appContentDeclarationJson":"app2_declaration"""")
  }

  @Test
  fun testMetadataProviderReturnsEmptyJsonWhenFlagDisabled() = runTest {
    StudioFlags.PLAY_POLICY_METADATA_EXPORT_ENABLED.override(false)

    // Populate service cache
    service.refreshMetadata()

    val provider = MetadataProvider.EP_NAME.getExtensionList(projectRule.project).first()
    val json = provider.get()

    assertThat(json).isEqualTo("[]")
  }

  private fun createApp(applicationId: String) =
    AndroidProjectBuilder(
      projectType = { IdeAndroidProjectType.PROJECT_TYPE_APP },
      mainSourceProvider = { createMainSourceProviderForDefaultTestProjectStructure() },
      applicationIdFor = { applicationId },
    )

  private fun createLib(applicationId: String) =
    AndroidProjectBuilder(
      projectType = { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY },
      mainSourceProvider = { createMainSourceProviderForDefaultTestProjectStructure() },
      applicationIdFor = { applicationId },
    )
}

class FakePlayMetadataClient : PlayMetadataClient {
  val applications = mutableMapOf<String, String>()
  val declarations = mutableMapOf<String, String>()

  override suspend fun getApplication(packageName: String): String = applications[packageName] ?: ""

  override suspend fun getAppContentDeclaration(packageName: String): String = declarations[packageName] ?: ""

  override fun close() {}
}
