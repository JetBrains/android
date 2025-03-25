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
package com.android.tools.idea.streaming.emulator

import com.android.ddmlib.IDevice
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.avdmanager.AvdLaunchListener.RequestType
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.io.grpc.ManagedChannelBuilder
import com.android.tools.idea.io.grpc.inprocess.InProcessChannelBuilder
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.AndroidSdksImpl
import com.android.tools.idea.sdk.IdeAvdManagers
import com.android.tools.idea.testing.TemporaryDirectoryRule
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.registerOrReplaceServiceInstance
import kotlinx.coroutines.Dispatchers
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.kotlin.mock
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds

/**
 * Allows tests to use [FakeEmulator] instead of the real one.
 */
class FakeEmulatorRule : TestRule {

  val avdRoot: Path by lazy { Files.createDirectories(userHome.resolve(".android/avd")) }
  private val emulators = mutableListOf<FakeEmulator>()
  private var availableGrpcPort = 8554
  private var registrationDirectory: Path? = null
  private val savedUserHome = System.getProperty("user.home")
  private val tempDirectory = TemporaryDirectoryRule()
  private var disposable: Disposable? = null
  private val root by lazy { Files.createDirectories(tempDirectory.newPath()) }
  private val userHome by lazy { Files.createDirectories(root.resolve("home")) }

  private val emulatorResource = object : ExternalResource() {
    override fun before() {
      val disposable = Disposer.newDisposable("FakeEmulatorRule").also { disposable = it }
      val grpcFactory = object : GrpcChannelBuilderFactory {
        override fun newGrpcChannelBuilder(host: String, port: Int): ManagedChannelBuilder<*> =
            InProcessChannelBuilder.forName(FakeEmulator.grpcServerName(port))
      }
      ApplicationManager.getApplication().registerOrReplaceServiceInstance(GrpcChannelBuilderFactory::class.java, grpcFactory, disposable)
      val emulatorCatalog = RunningEmulatorCatalog.getInstance()
      System.setProperty("user.home", userHome.toString())
      Files.createDirectories(userHome.resolve("Desktop"))
      registrationDirectory = Files.createDirectories(root.resolve("avd/running"))
      emulatorCatalog.overrideRegistrationDirectory(registrationDirectory)
      AvdManagerConnection.setConnectionFactory { sdkHandler, _ -> TestAvdManagerConnection(sdkHandler, avdRoot) }
      val androidSdks = object : AndroidSdksImpl() {
        override fun tryToChooseSdkHandler(): AndroidSdkHandler {
          val sdkRoot = FakeEmulator.getSdkFolder(avdRoot)
          return AndroidSdkHandler(sdkRoot, sdkRoot)
        }
      }
      ApplicationManager.getApplication()?.registerOrReplaceServiceInstance(AndroidSdks::class.java, androidSdks, disposable)
    }

    override fun after() {
      val emulatorCatalog = RunningEmulatorCatalog.getInstance()
      val emulatorControllers = emulatorCatalog.emulators
      try {
        for (emulator in emulatorControllers) {
          emulator.shutdown()
        }
        for (emulator in emulators) {
          emulator.stop()
        }
      }
      finally {
        disposable?.let { Disposer.dispose(it) }
        for (emulator in emulatorControllers) {
          emulator.awaitTermination(1.seconds)
        }
        System.setProperty("user.home", savedUserHome)
        registrationDirectory = null
        emulatorCatalog.overrideRegistrationDirectory(null)
        AvdManagerConnection.resetConnectionFactory()
      }
    }
  }

  override fun apply(base: Statement, description: Description): Statement {
    return tempDirectory.apply(emulatorResource.apply(base, description), description)
  }

  fun newPath(): Path = tempDirectory.newPath()

  fun newEmulator(avdFolder: Path): FakeEmulator {
    val dir = registrationDirectory ?: throw IllegalStateException()
    val emulator = FakeEmulator(avdFolder, availableGrpcPort++, dir)
    emulators.add(emulator)
    return emulator
  }

  private inner class TestAvdManagerConnection(
    sdkHandler: AndroidSdkHandler,
    avdHomeFolder: Path,
  ) : AvdManagerConnection(sdkHandler, IdeAvdManagers.getAvdManager(sdkHandler, avdHomeFolder), Dispatchers.Unconfined) {

    override fun getAvds(forceRefresh: Boolean): List<AvdInfo> {
      return super.getAvds(true) // Always refresh in tests.
    }

    override suspend fun startAvd(project: Project?, avd: AvdInfo, requestType: RequestType): IDevice {
      val emulator = emulators.firstOrNull { it.avdFolder == avd.dataFolderPath } ?:
          throw IllegalArgumentException("Unknown AVD: ${avd.id}")
      emulator.start(standalone = requestType != RequestType.DIRECT_RUNNING_DEVICES)
      return mock<IDevice>()
    }
  }
}