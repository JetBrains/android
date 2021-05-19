/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.intellij.testFramework.rules.TempDirectory
import io.grpc.ManagedChannelBuilder
import io.grpc.inprocess.InProcessChannelBuilder
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.nio.file.Path

/**
 * Allows tests to use [FakeEmulator] instead of the real one.
 */
class FakeEmulatorRule : TestRule {
  private val emulators = mutableListOf<FakeEmulator>()
  private var registrationDirectory: Path? = null
  private val tempDirectory = TempDirectory()
  private val emulatorResource = object : ExternalResource() {
    override fun before() {
      RuntimeConfigurationOverrider.overrideConfiguration(FakeEmulatorTestConfiguration())
      val emulatorCatalog = RunningEmulatorCatalog.getInstance()
      registrationDirectory = tempDirectory.newDirectory("avd/running").toPath()
      emulatorCatalog.overrideRegistrationDirectory(registrationDirectory)
    }

    override fun after() {
      for (emulator in emulators) {
        emulator.stop()
      }
      registrationDirectory = null
      val emulatorCatalog = RunningEmulatorCatalog.getInstance()
      emulatorCatalog.overrideRegistrationDirectory(null)
      RuntimeConfigurationOverrider.clearOverride()
    }
  }

  override fun apply(base: Statement, description: Description): Statement {
    return tempDirectory.apply(emulatorResource.apply(base, description), description)
  }

  val root: File
    get() = tempDirectory.root

  fun newFile(): File = File.createTempFile("junit", null, tempDirectory.root)

  fun newEmulator(avdFolder: Path, grpcPort: Int, standalone: Boolean = false): FakeEmulator {
    val dir = registrationDirectory ?: throw IllegalStateException()
    val emulator = FakeEmulator(avdFolder, grpcPort, dir, standalone)
    emulators.add(emulator)
    return emulator
  }

  private inner class FakeEmulatorTestConfiguration : RuntimeConfiguration() {

    override fun getDesktopOrUserHomeDirectory(): Path {
      return tempDirectory.root.toPath()
    }

    override fun newGrpcChannelBuilder(host: String, port: Int): ManagedChannelBuilder<*> {
      return InProcessChannelBuilder.forName(FakeEmulator.grpcServerName(port))
    }
  }
}