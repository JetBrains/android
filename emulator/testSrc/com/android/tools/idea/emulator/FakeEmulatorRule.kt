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

import com.android.tools.idea.testing.TemporaryDirectoryRule
import com.android.tools.idea.io.grpc.ManagedChannelBuilder
import com.android.tools.idea.io.grpc.inprocess.InProcessChannelBuilder
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.nio.file.Files
import java.nio.file.Path

/**
 * Allows tests to use [FakeEmulator] instead of the real one.
 */
class FakeEmulatorRule : TestRule {
  private val emulators = mutableListOf<FakeEmulator>()
  private var availableGrpcPort = 8554
  private var registrationDirectory: Path? = null
  private val savedUserHome = System.getProperty("user.home")
  private val tempDirectory = TemporaryDirectoryRule()
  private val emulatorResource = object : ExternalResource() {
    override fun before() {
      RuntimeConfigurationOverrider.overrideConfiguration(FakeEmulatorTestConfiguration())
      val emulatorCatalog = RunningEmulatorCatalog.getInstance()
      val root = Files.createDirectories(tempDirectory.newPath())
      nullableRoot = root
      val userHome = Files.createDirectories(root.resolve("home"))
      Files.createDirectories(userHome.resolve("Desktop"))
      System.setProperty("user.home", userHome.toString())
      registrationDirectory = Files.createDirectories(root.resolve("avd/running"))
      emulatorCatalog.overrideRegistrationDirectory(registrationDirectory)
    }

    override fun after() {
      for (emulator in emulators) {
        emulator.stop()
      }
      System.setProperty("user.home", savedUserHome)
      registrationDirectory = null
      val emulatorCatalog = RunningEmulatorCatalog.getInstance()
      emulatorCatalog.overrideRegistrationDirectory(null)
      RuntimeConfigurationOverrider.clearOverride()
    }
  }

  private var nullableRoot: Path? = null

  val root: Path
    get() = nullableRoot ?: throw IllegalStateException()

  override fun apply(base: Statement, description: Description): Statement {
    return tempDirectory.apply(emulatorResource.apply(base, description), description)
  }

  fun newPath(): Path = tempDirectory.newPath()

  fun newEmulator(avdFolder: Path, standalone: Boolean = false): FakeEmulator {
    val dir = registrationDirectory ?: throw IllegalStateException()
    val emulator = FakeEmulator(avdFolder, availableGrpcPort++, dir, standalone)
    emulators.add(emulator)
    return emulator
  }

  private inner class FakeEmulatorTestConfiguration : RuntimeConfiguration() {

    override fun newGrpcChannelBuilder(host: String, port: Int): ManagedChannelBuilder<*> {
      return InProcessChannelBuilder.forName(FakeEmulator.grpcServerName(port))
    }
  }
}