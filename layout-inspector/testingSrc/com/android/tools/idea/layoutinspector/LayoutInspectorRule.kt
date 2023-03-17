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
import com.android.ddmlib.testing.FakeAdbRule
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.appinspection.test.TestProcessDiscovery
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.metrics.LayoutInspectorSessionMetrics
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientLauncher
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.adb.AdbDebugViewProperties
import com.android.tools.idea.layoutinspector.pipeline.adb.FakeShellCommandHandler
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.DebugViewAttributes
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ComposeParametersCache
import com.android.tools.idea.layoutinspector.pipeline.foregroundprocessdetection.DeviceModel
import com.android.tools.idea.layoutinspector.pipeline.legacy.LegacyClient
import com.android.tools.idea.layoutinspector.pipeline.legacy.LegacyTreeLoader
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.layoutinspector.util.ReportingCountDownLatch
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.TestAndroidModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.facet.AndroidFacet
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

val OLDER_LEGACY_DEVICE = object : DeviceDescriptor by MODERN_DEVICE {
  override val model = "Older Legacy Model"
  override val serial = "12"
  override val apiLevel = AndroidVersion.VersionCodes.LOLLIPOP
  override val version = "L"
}

fun DeviceDescriptor.createProcess(
  name: String = "com.example",
  pid: Int = 1,
  streamId: Long = 13579,
  isRunning: Boolean = true
): ProcessDescriptor {
  val device = this
  return object : ProcessDescriptor {
    override val device = device
    override val abiCpuArch = "x86_64"
    override val name = name
    override val packageName = name
    override val isRunning = isRunning
    override val pid = pid
    override val streamId = streamId
  }
}

/**
 * Test interface for providing an [InspectorClient] that should get created when connecting to a
 * process.
 *
 * This will be used to handle initializing this rule's [InspectorClientLauncher].
 */
fun interface InspectorClientProvider {
  fun create(params: InspectorClientLauncher.Params, inspector: LayoutInspector): InspectorClient?
}

/**
 * Simple, convenient provider for generating a real [LegacyClient]
 */
fun LegacyClientProvider(
  getDisposable: () -> Disposable,
  treeLoaderOverride: LegacyTreeLoader? = Mockito.mock(LegacyTreeLoader::class.java).also {
    whenever(it.getAllWindowIds(ArgumentMatchers.any())).thenReturn(listOf("1"))
  }
) = InspectorClientProvider { params, inspector ->
  LegacyClient(
    params.process,
    params.isInstantlyAutoConnected,
    inspector.inspectorModel,
    LayoutInspectorSessionMetrics(inspector.inspectorModel.project, params.process),
    AndroidCoroutineScope(getDisposable()),
    getDisposable(),
    treeLoaderOverride
  )
}

/**
 * Rule providing mechanisms for testing core behavior used by the layout inspector.
 *
 * This includes things like fake ADB support, process management, and [InspectorClient] setup.
 *
 * Note that, when the rule first starts up, that [inspectorClient] will be set to a disconnected client. You must first
 * call [TestProcessDiscovery.fireConnected] (with a process that has a preferred process name) or
 * [ProcessesModel.selectedProcess] directly, to trigger a new client to get created.
 *
 * @param projectRule A rule providing access to a test project.
 *
 * @param isPreferredProcess Optionally provide a process selector that, when connected via [TestProcessDiscovery],
 *     will be automatically attached to. This simulates the experience when the user presses the "Run" button for example.
 *     Otherwise, the test caller must set [ProcessesModel.selectedProcess] directly.
 */
class LayoutInspectorRule(
  private val clientProviders: List<InspectorClientProvider>,
  private val projectRule: AndroidProjectRule,
  isPreferredProcess: (ProcessDescriptor) -> Boolean = { false }
) : TestRule {

  lateinit var launcher: InspectorClientLauncher
    private set
  private val launcherDisposable = Disposer.newDisposable()

  private var runningThreadCount = AtomicInteger(0)

  private val asyncLauncherThreads = mutableListOf<Thread>()

  private val launcherExecutor = Executor { runnable ->
    if (launchSynchronously) {
      runnable.run()
    }
    else {
      asyncLauncherThreads.add(Thread {
        runningThreadCount.incrementAndGet()
        runnable.run()
        runningThreadCount.decrementAndGet()
        asyncLaunchLatch.countDown()
      }.apply { start() })
    }
  }

  fun awaitLaunch() {
    assertThat(asyncLaunchLatch.await(10, TimeUnit.SECONDS)).isTrue()
    assertThat(runningThreadCount.get()).isEqualTo(0)
  }

  fun startLaunch(expectedTasks: Int) {
    asyncLaunchLatch = ReportingCountDownLatch(expectedTasks)
  }

  /**
   * Set this to false if the test requires the launcher to execute on a different thread.
   * Use [asyncLaunchLatch] to make sure the thread finished.
   */
  var launchSynchronously = true

  /**
   * Use this latch to control the execution of background launchers
   */
  private lateinit var asyncLaunchLatch: CountDownLatch

  /**
   * Convenience accessor, as this property is used a lot
   */
  val project get() = projectRule.project

  val disposable get() = projectRule.testRootDisposable

  /**
   * A notifier which acts as a source of processes being externally connected.
   */
  val processNotifier = TestProcessDiscovery()

  /**
   * The underlying processes model, automatically affected by [processNotifier] but can be
   * interacted directly with to force a connection via its [ProcessesModel.selectedProcess]
   * property.
   */
  val processes = ProcessesModel(processNotifier, isPreferredProcess)
  private lateinit var deviceModel: DeviceModel

  val adbRule = FakeAdbRule()
  val adbProperties: AdbDebugViewProperties = FakeShellCommandHandler().apply {
    adbRule.withDeviceCommandHandler(this)
  }
  val adbService = AdbServiceRule(projectRule::project, adbRule)

  lateinit var inspector: LayoutInspector
    private set
  lateinit var inspectorClient: InspectorClient
    private set
  lateinit var inspectorModel: InspectorModel
    private set

  val parametersCache: ComposeParametersCache?
    get() = (inspectorClient as? AppInspectionInspectorClient)?.composeInspector?.parametersCache

  /**
   * Notify this rule about a device that it should be aware of.
   *
   * Note that devices associated with launched processes will be added automatically, but it can
   * still be useful to manually add devices before that happens.
   */
  fun attachDevice(device: DeviceDescriptor) {
    if (adbRule.bridge.devices.none { it.serialNumber == device.serial }) {
      adbRule.attachDevice(device.serial, device.manufacturer, device.model, device.version, device.apiLevel.toString())
    }
  }

  private fun before() {
    projectRule.replaceService(PropertiesComponent::class.java, PropertiesComponentMock())

    val layoutInspectorCoroutineScope = AndroidCoroutineScope(projectRule.testRootDisposable)

    deviceModel = DeviceModel(disposable, processes)
    inspectorModel = InspectorModel(projectRule.project)
    launcher = InspectorClientLauncher(
      processes,
      clientProviders.map { provider -> { params -> provider.create(params, inspector) } },
      project,
      layoutInspectorCoroutineScope,
      launcherDisposable,
      executor = launcherExecutor
    )
    Disposer.register(projectRule.testRootDisposable, launcherDisposable)
    AndroidFacet.getInstance(projectRule.module)?.let { AndroidModel.set(it, TestAndroidModel("com.example")) }

    // Client starts disconnected, and will be updated after the ProcessesModel's selected process is updated
    inspectorClient = launcher.activeClient
    assertThat(inspectorClient.isConnected).isFalse()
    processes.addSelectedProcessListeners {
      processes.selectedProcess?.let { process ->
        // If a process is selected, let's just make sure we have ADB aware of the device as well. Some client code expects
        // ADB and our processes model to by in sync in normal situations.
        attachDevice(process.device)
      }
    }

    // This factory will be triggered when LayoutInspector is created
    val treeSettings = FakeTreeSettings()
    inspector = LayoutInspector(
      coroutineScope = layoutInspectorCoroutineScope,
      processModel = processes,
      deviceModel = deviceModel,
      foregroundProcessDetection = null,
      inspectorClientSettings = InspectorClientSettings(project),
      launcher = launcher,
      layoutInspectorModel = inspectorModel,
      treeSettings = treeSettings,
      executor = MoreExecutors.directExecutor()
    )
    launcher.addClientChangedListener {
      inspectorClient = it
    }

    (DataManager.getInstance() as HeadlessDataManager).setTestDataProvider(
      dataProviderForLayoutInspector(inspector),
      projectRule.fixture.testRootDisposable
    )

    DebugViewAttributes.reset()
  }

  fun disconnect() {
    // Disconnect the active client explicitly and block until it's done, since otherwise this
    // might happen on a background thread after the test framework is done tearing down.
    launcher.disconnectActiveClient(10, TimeUnit.SECONDS)

    launchSynchronously = true // Do not start more threads, since that would cause ConcurrentModificationException below
    asyncLauncherThreads.forEach {
      it.join(1000) // Wait for the thread to finish
      if (it.isAlive) {
        it.interrupt() // Force the thread to finish
        it.join()
      }
    }
  }

  private fun after() {
    disconnect()
  }

  override fun apply(base: Statement, description: Description): Statement {
    // List of rules that will be applied in order, with this rule being last
    val innerRules = listOf(adbService, adbRule)
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
