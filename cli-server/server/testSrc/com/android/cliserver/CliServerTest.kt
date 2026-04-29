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
package com.android.cliserver

import com.google.common.truth.Truth.assertThat
import java.nio.file.Path
import java.time.Instant
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.stream.Stream
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CliServerTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val androidHome: Path by lazy { tempFolder.newFolder("android_home").toPath() }
  private val registry: CliServerRegistry by lazy {
    val processService =
      object : ProcessService {
        override val current = FakeProcessHandle(123)

        override fun of(pid: Long) = if (pid == 123L) FakeProcessHandle(pid) else null
      }
    CliServerRegistry(androidHome, processService)
  }

  private val infoProvider =
    object : ServerInfoProvider {
      override fun status(): ServerInfoProvider.ServerInfo {
        return ServerInfoProvider.ServerInfo(
          version = "2026.1.1",
          projects = listOf(ServerInfoProvider.ProjectInfo("TestProject", "/path/to/project", ServerInfoProvider.ProjectStatus.READY)),
        )
      }
    }

  @Test
  fun testCheckStatus() {
    val server = CliServer(registry, infoProvider)
    val service = server.CliService()

    runBlocking {
      val response = service.checkStatus(CheckStatusRequest.getDefaultInstance())

      assertThat(response.version).isEqualTo("2026.1.1")
      assertThat(response.pid).isGreaterThan(0L)
      assertThat(response.projectStatusList).hasSize(1)
      val projectStatus = response.projectStatusList[0]
      assertThat(projectStatus.name).isEqualTo("TestProject")
      assertThat(projectStatus.path).isEqualTo("/path/to/project")
      assertThat(projectStatus.status).isEqualTo(ProjectStatus.Status.READY)
    }
  }

  private class FakeProcessHandle(private val pid: Long) : ProcessHandle {
    override fun pid(): Long = pid

    override fun parent(): Optional<ProcessHandle> = Optional.empty()

    override fun children(): Stream<ProcessHandle> = Stream.empty()

    override fun descendants(): Stream<ProcessHandle> = Stream.empty()

    override fun info(): ProcessHandle.Info =
      object : ProcessHandle.Info {
        override fun command(): Optional<String> = Optional.empty()

        override fun commandLine(): Optional<String> = Optional.empty()

        override fun arguments(): Optional<Array<String>> = Optional.empty()

        override fun startInstant(): Optional<Instant> = Optional.of(Instant.now())

        override fun totalCpuDuration(): Optional<java.time.Duration> = Optional.empty()

        override fun user(): Optional<String> = Optional.empty()
      }

    override fun onExit(): CompletableFuture<ProcessHandle> = CompletableFuture.completedFuture(this)

    override fun supportsNormalTermination(): Boolean = true

    override fun destroy(): Boolean = true

    override fun destroyForcibly(): Boolean = true

    override fun isAlive(): Boolean = true

    override fun hashCode(): Int = pid.hashCode()

    override fun equals(other: Any?): Boolean = other is ProcessHandle && other.pid() == pid

    override fun compareTo(other: ProcessHandle?): Int = pid.compareTo(other?.pid() ?: 0)
  }
}
