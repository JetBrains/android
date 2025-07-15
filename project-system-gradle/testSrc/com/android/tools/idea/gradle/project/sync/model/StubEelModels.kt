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
package com.android.tools.idea.gradle.project.sync.model

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.path.EelPath
//import com.intellij.platform.eel.provider.EelNioBridgeService
import com.intellij.platform.eel.provider.LocalPosixEelApi
import com.intellij.platform.eel.EelExecPosixApi
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.EelPosixProcess
import com.intellij.platform.eel.ExecuteProcessException
import org.jetbrains.annotations.NonNls
import java.nio.file.FileSystem
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider

// TODO KMT-1388
class StubLocalPosixEelApi(private val envVariables: Map<String, String>) : EelExecPosixApi {
  private val executeResultMock by lazy {
    ExecuteProcessException(errno = 12345, message = "mock result")
  }
  override val descriptor: EelDescriptor get() = throw UnsupportedOperationException()
  override suspend fun fetchLoginShellEnvVariables(): Map<String, String> = envVariables
  override suspend fun findExeFilesInPath(binaryName: String): List<EelPath> {
    return emptyList()
  }

  override suspend fun createExternalCli(options: EelExecApi.ExternalCliOptions): EelExecApi.ExternalCliEntrypoint {
    throw UnsupportedOperationException()
  }

  override suspend fun spawnProcess(builder: EelExecApi.ExecuteProcessOptions): EelPosixProcess {
    throw executeResultMock
  }
}

//class StubEelNioBridgeService(private val eelDescriptor: EelDescriptor) : EelNioBridgeService {
//  override fun tryGetEelDescriptor(nioPath: Path) = eelDescriptor
//  override fun tryGetNioRoots(eelDescriptor: EelDescriptor) = null
//  override fun tryGetId(eelDescriptor: EelDescriptor) = null
//  override fun tryGetDescriptorByName(name: String) = eelDescriptor
//  override fun register(localRoot: String,
//                        descriptor: EelDescriptor,
//                        internalName: @NonNls String,
//                        prefix: Boolean,
//                        caseSensitive: Boolean,
//                        fsProvider: (FileSystemProvider, FileSystem?) -> FileSystem?) {}
//
//  override fun unregister(descriptor: EelDescriptor): Boolean {
//    return true
//  }
//}

class StubEelDescriptor(private val eelApi: LocalPosixEelApi) : EelDescriptor {
  override val operatingSystem = EelPath.OS.UNIX
  override val userReadableDescription: @NonNls String
    get() = ""
  override val osFamily: EelOsFamily
    get() = EelOsFamily.Posix

  override suspend fun toEelApi(): EelApi = eelApi

  override suspend fun upgrade() = eelApi
}