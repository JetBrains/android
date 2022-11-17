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
package com.android.tools.idea.retention.actions

import com.android.annotations.concurrency.Slow
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.emulator.control.SnapshotPackage
import com.android.prefs.AndroidLocationsSingleton
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.avdmanager.emulatorcommand.EmulatorCommandBuilder
import com.android.tools.idea.emulator.EmulatorController
import com.android.tools.idea.emulator.RunningEmulatorCatalog
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.run.DeploymentApplicationService
import com.android.tools.idea.run.editor.AndroidDebugger
import com.android.tools.idea.run.editor.AndroidJavaDebugger
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.testartifacts.instrumented.AVD_NAME_KEY
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_FILE_KEY
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_ID_KEY
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_LAUNCH_PARAMETERS
import com.android.tools.idea.testartifacts.instrumented.IS_MANAGED_DEVICE
import com.android.tools.idea.testartifacts.instrumented.PACKAGE_NAME_KEY
import com.android.tools.idea.testartifacts.instrumented.RETENTION_AUTO_CONNECT_DEBUGGER_KEY
import com.android.tools.idea.testartifacts.instrumented.RETENTION_ON_FINISH_KEY
import com.android.utils.ILogger
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.debugger.ui.DebuggerContentInfo
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.ui.AppUIUtil
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.android.tools.idea.io.grpc.stub.ClientCallStreamObserver
import com.android.tools.idea.io.grpc.stub.ClientResponseObserver
import com.android.tools.idea.io.grpc.stub.StreamObserver
import org.jetbrains.android.actions.AndroidConnectDebuggerAction
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Path
import java.util.concurrent.CountDownLatch

// Const values for the progress bar
private const val PUSH_SNAPSHOT_FRACTION = 0.6
private const val LOAD_SNAPSHOT_FRACTION = 0.7
private const val CLIENTS_READY_FRACTION = 0.8
private const val DEBUGGER_CONNECTED_FRACTION = 0.9
private const val DEBUGGER_PAUSED_FRACTION = 0.95

private const val NOTIFICATION_GROUP_NAME = "Retention Snapshot Load"

/**
 * An action to load an Android Test Retention snapshot.
 */
class FindEmulatorAndSetupRetention(private val avdManagerBuilder: (AndroidSdkHandler,
                                                                    Path,
                                                                    ILogger) -> AvdManager? = { sdkHandler, path, logger ->
  AvdManager.getInstance(sdkHandler, path, logger)
}) : AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    val dataContext = event.dataContext
    val project = dataContext.getData<Project>(CommonDataKeys.PROJECT) ?: return
    ProgressManager.getInstance().run(
      object : Task.Backgroundable(project, "Loading retained test failure", true) {
        override fun onFinished() {
          dataContext.getData(RETENTION_ON_FINISH_KEY)?.run()
        }

        override fun run(indicator: ProgressIndicator) {
          indicator.isIndeterminate = false
          indicator.fraction = 0.0
          val deviceName = dataContext.getData(AVD_NAME_KEY)
          val isManagedDevice = dataContext.getData(IS_MANAGED_DEVICE)!!
          val catalog = RunningEmulatorCatalog.getInstance()
          val androidSdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
          val logWrapper = LogWrapper(LOG)
          val baseAvdFolder = if (isManagedDevice) {
            AndroidLocationsSingleton.gradleAvdLocation
          } else {
            AndroidLocationsSingleton.avdLocation
          }
          val avdManager = avdManagerBuilder(
            androidSdkHandler,
            baseAvdFolder,
            logWrapper
          )
          val avdInfo = avdManager?.getAvd(deviceName, true)
          try {
            AndroidDebugBridge.init(true)
          } catch (exp: IllegalStateException) {
            LOG.debug("ADB already initialized: ${exp}")
          }
          if (!AndroidDebugBridge.getClientSupport()) {
            showErrorMessage(project, "Adb client support required. " +
                                      "Please try to reboot adb by running \"adb kill-server\"")
            return
          }
          if (avdInfo == null) {
            showErrorMessage(project, "Cannot find valid AVD with name: ${deviceName}")
            return
          }
          if (!avdManager.isAvdRunning(avdInfo)) {
            val deviceFuture = bootEmulator(project,
                                            avdInfo,
                                            isManagedDevice,
                                            (dataContext.getData(EMULATOR_SNAPSHOT_LAUNCH_PARAMETERS) ?: listOf())
            )
            val device = ProgressIndicatorUtils.awaitWithCheckCanceled(deviceFuture)
            ProgressIndicatorUtils.awaitWithCheckCanceled { device.isOnline }
          }
          val emulatorControllers = ProgressIndicatorUtils.awaitWithCheckCanceled(catalog.updateNow())
          val emulatorController = emulatorControllers.find { it.emulatorId.avdId == deviceName }
          if (emulatorController == null) {
            showErrorMessage(project, "Failed to find or launch to AVD with device name: ${deviceName}")
            return
          }
          if (emulatorController.connectionState != EmulatorController.ConnectionState.CONNECTED) {
            emulatorController.connectGrpc()
          }
          val emulatorSerialString = "emulator-${emulatorController.emulatorId.serialPort}"
          val snapshotId = dataContext.getData(EMULATOR_SNAPSHOT_ID_KEY) ?: return
          val snapshotFile = dataContext.getData(EMULATOR_SNAPSHOT_FILE_KEY) ?: return
          val shouldAttachDebugger = dataContext.getData(RETENTION_AUTO_CONNECT_DEBUGGER_KEY) ?: false
          val packageName = dataContext.getData(PACKAGE_NAME_KEY) ?: return
          if (!shouldAttachDebugger) {
            if (!emulatorController.pushAndLoadAndDeleteSync(snapshotId, snapshotFile, indicator)) {
              showErrorMessage(project, "Failed to import snapshots. Please try to boot emulator (${deviceName}) with the same "
                                        + "command line parameters as you run the test.")
            }
            return
          }

          AndroidDebugBridge.getBridge()?.devices?.asSequence()?.filter {
            it.serialNumber == emulatorSerialString
          }?.forEach {
            it.getClient(packageName)?.kill()
          }
          val deviceReadySignal = CountDownLatch(1)
          // After loading a snapshot, the following events will happen:
          // Device disconnects -> device reconnects-> device client list changes
          // But the device client list changes callback does not really catch all client changes.
          val deviceChangeListener: AndroidDebugBridge.IDeviceChangeListener = object : AndroidDebugBridge.IDeviceChangeListener {
            override fun deviceDisconnected(device: IDevice) {}

            override fun deviceConnected(device: IDevice) {
              if (emulatorSerialString == device.serialNumber) {
                deviceReadySignal.countDown()
                AndroidDebugBridge.removeDeviceChangeListener(this)
              }
            }

            // Sometimes it reports back in device changed instead of connected.
            override fun deviceChanged(device: IDevice, changeMask: Int) {
              if (device.isOnline && emulatorSerialString == device.serialNumber) {
                deviceReadySignal.countDown()
                AndroidDebugBridge.removeDeviceChangeListener(this)
              }
            }
          }
          AndroidDebugBridge.addDeviceChangeListener(deviceChangeListener)
          try {
            if (!emulatorController.pushAndLoadAndDeleteSync(snapshotId, snapshotFile, indicator)) {
              showErrorMessage(project, "Failed to import snapshots. Please try to boot emulator (${deviceName}) with the same "
                                        + "command line parameters as you run the test.")
              return
            }
            indicator.fraction = LOAD_SNAPSHOT_FRACTION
            LOG.info("Snapshot loaded.")
            ProgressIndicatorUtils.awaitWithCheckCanceled(deviceReadySignal)
            LOG.info("Device ready.")
          } finally {
            AndroidDebugBridge.removeDeviceChangeListener(deviceChangeListener)
          }

          // Adb reports device connected, but occasionally it will still give us a stale device.
          // This happens a lot on AOSP images.
          lateinit var targetDevice: IDevice
          val adb = requireNotNull(AndroidDebugBridge.getBridge())

          // Check if the ddm client is ready.
          // Alternatively we can register a callback to check clients. But the IDeviceChangeListener does not really deliver all new
          // client events. Also, because of the implementation of ProgressIndicatorUtils.awaitWithCheckCanceled, it is going to poll
          // and wait even if we use callbacks.
          LOG.info("Checking client for $packageName.")
          ProgressIndicatorUtils.awaitWithCheckCanceled {
            // We need to refresh the device list.
            targetDevice = adb.devices.find { device ->
              device.serialNumber == emulatorSerialString
            }?: return@awaitWithCheckCanceled false
            return@awaitWithCheckCanceled DeploymentApplicationService.instance.findClient(targetDevice, packageName).isNotEmpty()
          }
          LOG.info("Client ready.")
          indicator.fraction = CLIENTS_READY_FRACTION

          val debugSessionReadySignal = CountDownLatch(1)
          val messageBusConnection = project.messageBus.connect()
          try {
            messageBusConnection.subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
              override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
                if (currentSession != null) {
                  messageBusConnection.disconnect()
                  debugSessionReadySignal.countDown()
                }
              }
            })
            connectDebugger(targetDevice, dataContext)
            // Wait for debug session ready.
            ProgressIndicatorUtils.awaitWithCheckCanceled(debugSessionReadySignal)
            indicator.fraction = DEBUGGER_CONNECTED_FRACTION
          }
          catch (exception: Exception) {
            messageBusConnection.disconnect()
            throw exception
          }
          val currentSession = XDebuggerManager.getInstance(getProject()).currentSession!!
          val pauseSignal = CountDownLatch(1)
          // Pause the current session. It turns out that the session can only be paused after some setting updates. So we need to send
          // pause commands in settingsChanged() as well as in the current thread, in case it is updated before we set up the callbacks.
          currentSession.addSessionListener(object : XDebugSessionListener {
            override fun sessionPaused() {
              currentSession.removeSessionListener(this)
              pauseSignal.countDown()
            }

            override fun settingsChanged() {
              currentSession.pause()
            }
          })
          currentSession.pause()
          ProgressIndicatorUtils.awaitWithCheckCanceled(pauseSignal)
          indicator.fraction = DEBUGGER_PAUSED_FRACTION
          val stackReadySignal = CountDownLatch(1)
          // Switch focus to the debugger
          AppUIUtil.invokeOnEdt {
            currentSession.ui.run {
              selectAndFocus(findContent(DebuggerContentInfo.FRAME_CONTENT), true, false)
            }
          }
          // Find user content
          currentSession.suspendContext.computeExecutionStacks(object: XSuspendContext.XExecutionStackContainer {
            var foundUserFrame = false
            override fun errorOccurred(errorMessage: String) {
              stackReadySignal.countDown()
              LOG.warn("$errorMessage")
            }

            override fun addExecutionStack(executionStacks: List<XExecutionStack>, last: Boolean) {
              if (foundUserFrame) {
                return
              }
              for (stack in executionStacks) {
                stack.computeStackFrames(0, object  : XExecutionStack.XStackFrameContainer {
                  override fun errorOccurred(errorMessage: String) {
                    stackReadySignal.countDown()
                    LOG.warn("$errorMessage")
                  }

                  override fun addStackFrames(stackFrames: List<XStackFrame>, last: Boolean) {
                    if (foundUserFrame) {
                      return
                    }
                    for (frame in stackFrames) {
                      if (frame.sourcePosition?.file?.canonicalPath?.startsWith(
                          project.basePath?:"") == true) {
                          frame.sourcePosition?.createNavigatable(project)?.also {
                            AppUIUtil.invokeOnEdt {
                              currentSession.setCurrentStackFrame(stack, frame)
                              stackReadySignal.countDown()
                            }
                            foundUserFrame = true
                            return@addStackFrames
                          }
                        }
                    }
                  }
                })
              }
              if (last && !foundUserFrame) {
                stackReadySignal.countDown()
              }
            }
          })
          ProgressIndicatorUtils.awaitWithCheckCanceled(stackReadySignal)
          LOG.info("Ready for debugging.")
        }
      })
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    val snapshotId = event.dataContext.getData(EMULATOR_SNAPSHOT_ID_KEY)
    val snapshotFile = event.dataContext.getData(EMULATOR_SNAPSHOT_FILE_KEY)
    event.presentation.isEnabledAndVisible = (snapshotId != null && snapshotFile != null)
  }
}

private fun bootEmulator(project: Project, avdInfo: AvdInfo, isManagedDevice: Boolean, parameters: List<String>): ListenableFuture<IDevice> {
  val avdManagerConnection = if (isManagedDevice) {
    AvdManagerConnection.getDefaultGradleAvdManagerConnection()
  } else {
    AvdManagerConnection.getDefaultAvdManagerConnection()
  }
  return avdManagerConnection.startAvd(
    project,
    avdInfo
  ) { emulator, avd ->
     EmulatorCommandBuilder(emulator, avd).addAllStudioEmuParams(filterEmulatorBootParameters(parameters))
  }
}

@VisibleForTesting
fun filterEmulatorBootParameters(parameters: List<String>): List<String> {
  val filtered = mutableListOf<String>()
  if (parameters.isEmpty()) {
    return filtered
  }
  val iterator = parameters.iterator()
  // The first parameter is the emulator path. Skip it.
  iterator.next()
  while (iterator.hasNext()) {
    when (val parameter = iterator.next()) {
      // Skip avd name. Skip flags set by studio.
      // Need to be careful about which flag has extra parameters.
      "-avd", "-netdelay", "-netspeed", "-idle-grpc-timeout" -> {
        iterator.next()
      }
      "-qt-hide-window", "-grpc-use-token" -> Unit
      else -> {
        // You can set AVD name by passing "@avd_name". So we skip it.
        if (!parameter.startsWith("@")) {
          filtered.add(parameter)
        }
      }
    }
  }
  return filtered.toList()
}

@Slow
private fun connectDebugger(device: IDevice, dataContext: DataContext) {
  val packageName = dataContext.getData(PACKAGE_NAME_KEY)
  val client = device.getClient(packageName)
  if (client == null) {
    LOG.warn("Cannot connect to ${packageName}")
    return
  }
  val project = dataContext.getData<Project>(CommonDataKeys.PROJECT) ?: return
  val androidDebugger = AndroidDebugger.EP_NAME.extensions.find {
    it.supportsProject(project) && it.id == AndroidJavaDebugger.ID
  }
  if (androidDebugger == null) {
    LOG.warn("Cannot find java debuggers.")
    return
  }
  AndroidConnectDebuggerAction.closeOldSessionAndRun(project, androidDebugger, client, null)
}

/**
 * Pushes a snapshot file into the emulator and load it.
 *
 * @param snapshotId a snapshot name which must match the snapshot in the snapshot file.
 * @param snapshotFile a file of an exported snapshot.
 *
 * @return true if succeeds.
 */
@Slow
private fun EmulatorController.pushAndLoadAndDeleteSync(snapshotId: String, snapshotFile: File, indicator: ProgressIndicator): Boolean {
  return pushSnapshotSync(snapshotId, snapshotFile, indicator) && loadSnapshotSync(snapshotId) && deleteSnapshotSync(snapshotId)
}

/**
 * Loads a snapshot in the emulator.
 *
 * @param snapshotId a name of a snapshot in the emulator.
 *
 * @return true if succeeds.
 */
@Slow
private fun EmulatorController.loadSnapshotSync(snapshotId: String): Boolean {
  val doneSignal = CountDownLatch(1)
  var succeeded = true
  loadSnapshot(snapshotId, object : StreamObserver<SnapshotPackage> {
    override fun onNext(response: SnapshotPackage) {
      if (!response.success) {
        succeeded = false
        showErrorMessage(null, "Snapshot load failed: " + response.err.toString(Charset.defaultCharset()))
      }
    }
    override fun onCompleted() {
      doneSignal.countDown()
    }

    override fun onError(throwable: Throwable) {
      succeeded = false
      doneSignal.countDown()
    }
  })
  ProgressIndicatorUtils.awaitWithCheckCanceled(doneSignal)
  return succeeded
}

/**
 * Delete a snapshot in the emulator.
 *
 * @param snapshotId a name of a snapshot in the emulator.
 *
 * @return true if succeeds.
 */
@Slow
private fun EmulatorController.deleteSnapshotSync(snapshotId: String): Boolean {
  val doneSignal = CountDownLatch(1)
  var succeeded = true
  deleteSnapshot(snapshotId, object : StreamObserver<SnapshotPackage> {
    override fun onNext(response: SnapshotPackage) {
      if (!response.success) {
        succeeded = false
        showErrorMessage(null, "Snapshot load failed: " + response.err.toString(Charset.defaultCharset()))
      }
    }
    override fun onCompleted() {
      doneSignal.countDown()
    }

    override fun onError(throwable: Throwable) {
      succeeded = false
      doneSignal.countDown()
    }
  })
  ProgressIndicatorUtils.awaitWithCheckCanceled(doneSignal)
  return succeeded
}

/**
 * Pushes a snapshot file into the emulator.
 *
 * @param snapshotId a snapshot name which must match the snapshot in the snapshot file.
 * @param snapshotFile a file of an exported snapshot.
 *
 * @return true if succeeds.
 */
@Slow
@Throws(IOException::class)
private fun EmulatorController.pushSnapshotSync(snapshotId: String, snapshotFile: File, indicator: ProgressIndicator): Boolean {
  val format = if (snapshotFile.isDirectory) {
    SnapshotPackage.Format.DIRECTORY
  } else if (snapshotFile.extension.toLowerCase() == "gz") {
    SnapshotPackage.Format.TARGZ
  } else {
    SnapshotPackage.Format.TAR
  }
  val snapshotFileStream = if (format == SnapshotPackage.Format.DIRECTORY) {
    null
  } else {
    snapshotFile.inputStream()
  }
  val fileSize = if (format == SnapshotPackage.Format.DIRECTORY) {
    0L
  } else {
    snapshotFile.length()
  }
  var totalBytesSent = 0L
  var succeeded = true
  val doneSignal = CountDownLatch(1)
  pushSnapshot(object : ClientResponseObserver<SnapshotPackage, SnapshotPackage> {
    override fun onCompleted() {
      snapshotFileStream?.close()
      doneSignal.countDown()
    }

    override fun onError(throwable: Throwable) {
      succeeded = false
      snapshotFileStream?.close()
      doneSignal.countDown()
    }

    override fun beforeStart(clientCallStreamObserver: ClientCallStreamObserver<SnapshotPackage>) {
      var snapshotIdSent = false
      var completionRequested = false
      val bytes = ByteArray(2 * 1024 * 1024)
      clientCallStreamObserver.setOnReadyHandler {
        // https://grpc.github.io/grpc-java/javadoc/io/grpc/stub/CallStreamObserver.html#setOnReadyHandler-java.lang.Runnable-
        // Get rid of "spurious" notifications first.
        if (!clientCallStreamObserver.isReady) {
          return@setOnReadyHandler
        }
        if (!snapshotIdSent) {
          clientCallStreamObserver.onNext(SnapshotPackage
                                            .newBuilder()
                                            .setSnapshotId(snapshotId)
                                            .setFormat(format)
                                            .setPath(snapshotFile.absolutePath)
                                            .build())
          snapshotIdSent = true
        }
        var bytesRead = 0
        while (clientCallStreamObserver.isReady) {
          bytesRead = snapshotFileStream?.read(bytes)?:-1
          if (bytesRead <= 0) {
            break
          }
          clientCallStreamObserver.onNext(SnapshotPackage.newBuilder().setPayload(ByteString.copyFrom(bytes, 0, bytesRead)).build())
          totalBytesSent += bytesRead
          indicator.fraction = totalBytesSent.toDouble() / fileSize * PUSH_SNAPSHOT_FRACTION
        }
        if (bytesRead < 0 && !completionRequested) {
          completionRequested = true
          clientCallStreamObserver.onCompleted()
        }
      }
    }

    override fun onNext(response: SnapshotPackage) {
      if (!response.success) {
        succeeded = false
        showErrorMessage(null, "Snapshot push failed: " + response.err.toString(Charset.defaultCharset()))
      }
    }
  })

  // Slow
  ProgressIndicatorUtils.awaitWithCheckCanceled(doneSignal)
  return succeeded
}

private val LOG = logger<FindEmulatorAndSetupRetention>()
private val NOTIFICATION_GROUP = NotificationGroup(NOTIFICATION_GROUP_NAME, NotificationDisplayType.BALLOON)

private fun showErrorMessage(project: Project?, message: String) {
  NOTIFICATION_GROUP.createNotification(message, NotificationType.ERROR).notify(project)
}
