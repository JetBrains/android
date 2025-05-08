/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.settingssync

import com.android.tools.idea.settingssync.onboarding.USER_EMAIL
import com.intellij.openapi.diagnostic.Logger
import com.intellij.settingsSync.core.AbstractServerCommunicator
import com.intellij.settingsSync.core.FileState
import com.intellij.settingsSync.core.InvalidVersionIdException
import com.intellij.settingsSync.core.SettingsSnapshot
import com.intellij.settingsSync.core.SettingsSnapshot.MetaInfo
import com.intellij.settingsSync.core.SettingsSyncPushResult
import com.intellij.settingsSync.core.SettingsSyncRemoteCommunicator
import com.intellij.settingsSync.core.auth.SettingsSyncAuthService
import com.intellij.settingsSync.core.communicator.SettingsSyncCommunicatorProvider
import com.intellij.settingsSync.core.communicator.SettingsSyncUserData
import com.intellij.settingsSync.core.getLocalApplicationInfo
import com.intellij.settingsSync.core.plugins.SettingsSyncPluginsState
import java.awt.Component
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant
import java.util.concurrent.CountDownLatch
import javax.swing.Icon
import kotlinx.io.IOException

private val LOG = Logger.getInstance(FakeRemoteCommunicator::class.java)
private const val DISCONNECTED_ERROR = "disconnected"

internal val SAMPLE_SNAPSHOT =
  SettingsSnapshot(
    metaInfo = MetaInfo(Instant.parse("2024-05-08T10:15:30.00Z"), getLocalApplicationInfo()),
    fileStates = emptySet<FileState>(),
    plugins = SettingsSyncPluginsState(emptyMap()),
    settingsFromProviders = emptyMap(),
    additionalFiles = emptySet<FileState>(),
  )

// Mostly copied from JB
internal class FakeRemoteCommunicator(override val userId: String) : AbstractServerCommunicator() {
  private val filesAndVersions = mutableMapOf<String, Version>()
  private val versionIdStorage = mutableMapOf<String, String>()
  var isConnected = true

  private lateinit var pushedLatch: CountDownLatch
  private lateinit var pushedSnapshot: SettingsSnapshot
  private lateinit var pushedResult: SettingsSyncPushResult

  fun settingsPushed(snapshot: SettingsSnapshot, result: SettingsSyncPushResult) {
    if (::pushedLatch.isInitialized) {
      pushedSnapshot = snapshot
      pushedResult = result
      pushedLatch.countDown()
    }
  }

  override fun requestSuccessful() = Unit

  override fun handleRemoteError(e: Throwable): String {
    return e.message ?: "unknown error"
  }

  override fun readFileInternal(filePath: String): Pair<InputStream?, String?> {
    checkConnected()
    val version = filesAndVersions[filePath] ?: return Pair(null, null)
    versionIdStorage.put(filePath, version.versionId)
    LOG.info("Put version '${version.versionId}' for file $filePath (after read)")
    return Pair(ByteArrayInputStream(version.content), version.versionId)
  }

  override fun writeFileInternal(
    filePath: String,
    versionId: String?,
    content: InputStream,
  ): String? {
    checkConnected()
    val currentVersion = filesAndVersions[filePath]
    if (versionId != null && currentVersion != null && currentVersion.versionId != versionId) {
      throw InvalidVersionIdException(
        "Expected version $versionId, but actual is ${currentVersion.versionId}"
      )
    }
    val version = Version(content.readAllBytes())
    filesAndVersions[filePath] = version
    versionIdStorage.put(filePath, version.versionId)
    LOG.info("Put version '${version.versionId}' for file $filePath (after write)")
    return version.versionId
  }

  override fun getLatestVersion(filePath: String): String? {
    checkConnected()
    val version = filesAndVersions[filePath] ?: return null
    return version.versionId
  }

  override fun deleteFileInternal(filePath: String) {
    checkConnected()
    filesAndVersions - filePath
    versionIdStorage.remove(filePath)
    LOG.info("Removed version for file $filePath")
  }

  fun awaitForPush(testExecution: () -> Unit): PushResult {
    pushedLatch = CountDownLatch(1)
    testExecution()
    pushedLatch.await()
    return PushResult(snapshot = pushedSnapshot, result = pushedResult)
  }

  override fun push(
    snapshot: SettingsSnapshot,
    force: Boolean,
    expectedServerVersionId: String?,
  ): SettingsSyncPushResult {
    val result = super.push(snapshot, force, expectedServerVersionId)
    settingsPushed(snapshot, result)
    return result
  }

  fun deleteAllFiles() {
    filesAndVersions.clear()
  }

  fun prepareFileOnServer(snapshot: SettingsSnapshot) {
    push(snapshot, force = true, expectedServerVersionId = null)
  }

  private class Version(val content: ByteArray, val versionId: String) {
    constructor(content: ByteArray) : this(content, System.nanoTime().toString())
  }

  private fun checkConnected() {
    if (!isConnected) {
      throw IOException(DISCONNECTED_ERROR)
    }
  }
}

internal class FakeCommunicatorProvider(
  private val remoteCommunicator: SettingsSyncRemoteCommunicator,
  override val authService: SettingsSyncAuthService =
    FakeAuthService(
      SettingsSyncUserData(
        id = USER_EMAIL,
        providerCode = PROVIDER_CODE_GOOGLE,
        name = null,
        email = USER_EMAIL,
        printableName = null,
      )
    ),
) : SettingsSyncCommunicatorProvider {
  override val providerCode: String
    get() = PROVIDER_CODE_GOOGLE

  override fun createCommunicator(userId: String): SettingsSyncRemoteCommunicator? =
    remoteCommunicator
}

internal class FakeAuthService(private val userData: SettingsSyncUserData) :
  SettingsSyncAuthService {
  override val providerCode: String
    get() = PROVIDER_CODE_GOOGLE

  override val providerName: String
    get() = PROVIDER_NAME_GOOGLE

  override val icon: Icon?
    get() = null

  override suspend fun login(parentComponent: Component?): SettingsSyncUserData? {
    return null
  }

  override fun getUserData(userId: String): SettingsSyncUserData {
    return userData
  }

  override fun getAvailableUserAccounts(): List<SettingsSyncUserData> {
    return emptyList()
  }
}

internal data class PushResult(val snapshot: SettingsSnapshot, val result: SettingsSyncPushResult)
