/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.test.TestProcessNotifier
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.legacy.LegacyClient
import com.android.tools.idea.layoutinspector.pipeline.transport.TransportInspectorClient
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.net.Socket
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

val MODERN_DEVICE = object : DeviceDescriptor {
  override val manufacturer = "Google"
  override val model = "Modern Model"
  override val serial = "123456"
  override val isEmulator = false
  override val apiLevel = AndroidVersion.VersionCodes.Q
  override val version = "Q"
  override val codename: String? = null
}

val LEGACY_DEVICE = object : DeviceDescriptor by MODERN_DEVICE {
  override val model = "Legacy Model"
  override val serial = "123"
  override val apiLevel = AndroidVersion.VersionCodes.M
  override val version = "M"
}

fun DeviceDescriptor.createProcess(name: String = "com.example.layout.MyApp", pid: Int = 1, streamId: Long = 13579): ProcessDescriptor {
  val device = this
  return object : ProcessDescriptor {
    override val device = device
    override val abiCpuArch = "x86_64"
    override val name = name
    override val isRunning = true
    override val pid = pid
    override val streamId = streamId
  }
}

/**
 * Rule providing mechanisms for testing core behavior used by the layout inspector.
 *
 * This includes things like fake ADB support, process management, and [InspectorClient] setup.
 *
 * Any passed-in objects shouldn't be registered as [org.junit.Rule]s by the caller: [LayoutInspectorRule] will call them as
 * needed.
 *
 * @param getPreferredProcessNames Optionally provide names of processes that, when they connect, will be automatically
 *    attached to. This simulates the experience when the user presses the "Run" button for example. Otherwise, the test
 *    caller must set [ProcessesModel.selectedProcess] directly.
 */
class LayoutInspectorRule(
  val projectRule: AndroidProjectRule = AndroidProjectRule.onDisk(),
  getPreferredProcessNames: () -> List<String> = { listOf() }
) : TestRule {

  /**
   * Class which installs fake handling for adb commands related to querying and updating debug
   * view properties.
   */
  class AdbDebugViewProperties(adbRule: FakeAdbRule) {
    var debugViewAttributesChangesCount = 0
      private set
    var debugViewAttributes: String? = null
      private set
    var debugViewAttributesApplicationPackage: String? = null
      private set

    init {
      adbRule.withDeviceCommandHandler(object : DeviceCommandHandler("shell") {
        override fun accept(server: FakeAdbServer, socket: Socket, device: DeviceState, command: String, args: String): Boolean {
          val response = when (command) {
            "shell" -> handleShellCommand(args) ?: return false
            else -> return false
          }
          writeOkay(socket.getOutputStream())
          writeString(socket.getOutputStream(), response)
          return true
        }
      })
    }

    /**
     * Handle shell commands.
     *
     * Examples:
     *  - "settings get global debug_view_attributes"
     *  - "settings get global debug_view_attributes_application_package"
     *  - "settings put global debug_view_attributes 1"
     *  - "settings put global debug_view_attributes_application_package com.example.myapp"
     *  - "settings delete global debug_view_attributes"
     *  - "settings delete global debug_view_attributes_application_package"
     */
    private fun handleShellCommand(command: String): String? {
      val args = ArrayDeque(command.split(' '))
      if (args.poll() != "settings") {
        return null
      }
      val operation = args.poll()
      if (args.poll() != "global") {
        return null
      }
      val variable = when (args.poll()) {
        "debug_view_attributes" -> this::debugViewAttributes
        "debug_view_attributes_application_package" -> this::debugViewAttributesApplicationPackage
        else -> return null
      }
      val argument = if (args.isEmpty()) "" else args.poll()
      if (args.isNotEmpty()) {
        return null
      }
      return when (operation) {
        "get" -> variable.get().toString()
        "put" -> {
          variable.set(argument); debugViewAttributesChangesCount++; ""
        }
        "delete" -> {
          variable.set(null); debugViewAttributesChangesCount++; ""
        }
        else -> null
      }
    }
  }

  /**
   * Convenience accessor, as this property is used a lot
   */
  val project get() = projectRule.project

  /**
   * A notifier which acts as a source of processes being externally connected.
   */
  val processNotifier = TestProcessNotifier()

  /**
   * The underlying processes model, automatically affected by [processNotifier] but can be
   * interacted directly with to force a connection via its [ProcessesModel.selectedProcess]
   * property.
   */
  val processes = ProcessesModel(processNotifier, getPreferredProcessNames)

  val adbRule = FakeAdbRule()
  val adbProperties = AdbDebugViewProperties(adbRule)

  lateinit var inspector: LayoutInspector
  lateinit var inspectorClient: InspectorClient
  lateinit var inspectorModel: InspectorModel

  private lateinit var clientFactory: () -> InspectorClient

  /**
   * Initialize this rule so [inspectorClient] is a [TransportInspectorClient].
   *
   * This method should be called when this rule is constructed, not after the test starts.
   */
  fun withTransportClient(grpcServer: FakeGrpcServer, scheduler: ScheduledExecutorService) = apply {
    clientFactory = {
      TransportInspectorClient(adbRule.bridge, processes, inspectorModel, projectRule.fixture.projectDisposable, grpcServer.name, scheduler)
    }
  }

  /**
   * Initialize this rule so [inspectorClient] is a [LegacyClient].
   *
   * This method should be called when this rule is constructed, not after the test starts.
   */
  fun withLegacyClient() = apply {
    clientFactory = { LegacyClient(adbRule.bridge, processes, inspectorModel) }
  }

  /**
   * Notify this rule about a device that it should be aware of.
   *
   * Note that devices associated with launched processes will be added automatically, but it can
   * still be useful to manually add devices before that happens.
   */
  fun attachDevice(device: DeviceDescriptor) {
    if (adbRule.bridge.devices.none { it.serialNumber == device.serial }) {
      adbRule.attachDevice(device.serial, device.manufacturer, device.model, device.version, device.apiLevel.toString(),
                           DeviceState.HostConnectionType.USB)
    }
  }

  /** We pull this out of a static singleton in [before] so we can safely restore in [after] */
  private lateinit var originalClientFactory: (AndroidDebugBridge, ProcessesModel, InspectorModel, Disposable) -> List<InspectorClient>

  private fun before() {
    check(::clientFactory.isInitialized) { "Test must call one of the 'withClient' methods during rule construction time" }
    originalClientFactory = InspectorClient.clientFactory

    projectRule.replaceService(PropertiesComponent::class.java, PropertiesComponentMock())

    processes.addSelectedProcessListeners {
      processes.selectedProcess?.let { process ->
        // If a process is selected, let's just make sure we have ADB aware of the device as well. Some client code expects
        // ADB and our processes model to by in sync in normal situations.
        attachDevice(process.device)
      }
    }

    inspectorModel = InspectorModel(projectRule.project)
    // This factory will be triggered when LayoutInspector is created
    InspectorClient.clientFactory = { _, _, _, _ -> listOf(clientFactory()) }
    inspector = LayoutInspector(adbRule.bridge, processes, inspectorModel, projectRule.fixture.testRootDisposable,
                                MoreExecutors.directExecutor())
    inspectorClient = inspector.allClients.first()
    inspector.setCurrentTestClient(inspectorClient)

    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider(dataProviderForLayoutInspector(inspector),
                                                                           projectRule.fixture.testRootDisposable)
  }

  private fun after() {
    InspectorClient.clientFactory = originalClientFactory

    if (inspectorClient.isConnected) {
      val processDone = CountDownLatch(1)
      inspectorClient.registerProcessChanged { processDone.countDown() }
      inspectorClient.disconnect().get(10, TimeUnit.SECONDS)
      assertThat(processDone.await(30, TimeUnit.SECONDS)).isTrue()
    }
  }

  override fun apply(base: Statement, description: Description): Statement {
    // List of rules that will be applied in order, with this rule being last
    val innerRules = listOf(adbRule, projectRule)
    val coreStatement = object : Statement() {
      override fun evaluate() {
        before()
        try {
          base.evaluate()
        }
        finally {
          after()
        }
      }
    }
    return innerRules.fold(coreStatement) { stmt: Statement, rule: TestRule -> rule.apply(stmt, description) }
  }
}
