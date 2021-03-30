/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.wearparing

import com.android.ddmlib.IDevice
import com.android.tools.adtui.ui.SVGScaledImageProvider
import com.android.tools.adtui.ui.ScalingImagePanel
import com.android.tools.idea.concurrency.AndroidDispatchers.ioThread
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.lang.time.StopWatch
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component.LEFT_ALIGNMENT
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingConstants
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future
import javax.swing.event.HyperlinkListener

private const val WEAR_PACKAGE = "com.google.android.wearable.app"
private const val WEAR_MAIN_ACTIVITY = "com.google.android.clockwork.companion.launcher.LauncherActivity"
private const val TIME_TO_SHOW_MANUAL_RETRY = 10_000L // TODO: Increase to 60s


class DevicesConnectionStep(model: WearDevicePairingModel,
                            val project: Project,
                            val showFirstStage: Boolean) : ModelWizardStep<WearDevicePairingModel>(model, "") {
  private var runningJob: Job? = null
  private val canGoForward = BoolValueProperty()
  private lateinit var wizardFacade: ModelWizard.Facade
  private val mainPanel = JBPanel<JBPanel<*>>(null).apply {
    border = JBUI.Borders.empty(24, 24, 0, 24)
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
  }

  override fun onWizardStarting(wizard: ModelWizard.Facade) {
    wizardFacade = wizard
  }

  override fun onEntering() {
    canGoForward.set(false)
    runningJob?.cancel()

    runningJob = GlobalScope.launch(ioThread) {
      if (model.phoneDevice.valueOrNull == null || model.wearDevice.valueOrNull == null) {
        showUI(header = "Found a problem", description = "Found a problem retrieving the previous selected devices")
        return@launch
      }

      val wearPairingDevice = model.wearDevice.value
      val wearDevice = wearPairingDevice.launchDeviceIfNeeded()
      val phonePairingDevice = model.phoneDevice.value
      val phoneDevice = phonePairingDevice.launchDeviceIfNeeded()

      if (showFirstStage) {
        showFirstPhase(phonePairingDevice, phoneDevice, wearPairingDevice, wearDevice)
      }
      else {
        showPairingPhase(phoneDevice, wearDevice)
      }
    }
  }

  override fun getComponent(): JComponent = mainPanel

  override fun canGoForward(): ObservableBool = canGoForward

  override fun dispose() {
    runningJob?.cancel()
  }

  private suspend fun showFirstPhase(phonePairingDevice: PairingDevice, phoneDevice: IDevice,
                                     wearPairingDevice: PairingDevice, wearDevice: IDevice) {
    showUiBridgingDevices()
    WearPairingManager.setKeepForwardAlive(phonePairingDevice, wearPairingDevice)
    createDeviceBridge(phoneDevice, wearDevice)

    if (phoneDevice.isCompanionAppInstalled()) {
      // Companion App already installed, go to the next step
      canGoForward.set(true)
      ApplicationManager.getApplication().invokeLater {
        wizardFacade.goForward()
      }
    }
    else {
      showInstallCompanionAppPhase(phoneDevice)
    }
  }

  private suspend fun showInstallCompanionAppPhase(phoneDevice: IDevice) {
    showUiInstallCompanionAppScanning(phoneDevice)

    val stopWatch = StopWatch().apply { start() }
    while (stopWatch.time < TIME_TO_SHOW_MANUAL_RETRY) {
      if (phoneDevice.isCompanionAppInstalled()) {
        showUiInstallCompanionAppSuccess(phoneDevice)
        canGoForward.set(true)
        return
      }
      delay(1000)
    }

    showUiInstallCompanionAppRetry(phoneDevice) // After some time we give up and show the manual retry ui
  }

  private suspend fun showPairingPhase(phoneDevice: IDevice, wearDevice: IDevice) {
    showUiPairingScanning(phoneDevice)

    val stopWatch = StopWatch().apply { start() }
    while (stopWatch.time < TIME_TO_SHOW_MANUAL_RETRY) {
      if (devicesPaired(phoneDevice, wearDevice)) {
        showUiPairingSuccess(model.phoneDevice.value.displayName, model.wearDevice.value.displayName)
        canGoForward.set(true)
        return
      }
      delay(1000)
    }

    showUiPairingRetry(phoneDevice, wearDevice)  // After some time we give up and show the manual retry ui
  }

  private suspend fun PairingDevice.launchDeviceIfNeeded(): IDevice {
    val futureDevice = launch(project)
    if (!futureDevice.isDone) {
      showUiLaunchingDevice(displayName)
    }
    val iDevice = futureDevice.await()
    launch = { Futures.immediateFuture(iDevice) }  // We can only launch AVDs once!
    return iDevice
  }

  private suspend fun showUI(
    header: String = "", description: String = "",
    progressTopLabel: String = "", progressBottomLabel: String = "",
    body: JComponent? = null,
    buttonLabel: String = "", listener: (ActionEvent) -> Unit = {}
  ) = withContext(uiThread(ModalityState.any())) {
    mainPanel.apply {
      removeAll()

      if (header.isNotEmpty()) {
        add(JBLabel(header, UIUtil.ComponentStyle.LARGE).apply {
          alignmentX = LEFT_ALIGNMENT
          font = JBFont.label().biggerOn(5.0f)
        })
      }
      if (description.isNotEmpty()) {
        add(JBLabel(description).apply {
          alignmentX = LEFT_ALIGNMENT
          border = JBUI.Borders.empty(16, 0, 32, 16)
        })
      }
      if (progressTopLabel.isNotEmpty()) {
        add(JBLabel(progressTopLabel).apply {
          alignmentX = LEFT_ALIGNMENT
          border = JBUI.Borders.empty(4, 0)
        })
        add(JProgressBar().apply {
          alignmentX = LEFT_ALIGNMENT
          isIndeterminate = true
        })
      }
      if (progressBottomLabel.isNotEmpty()) {
        add(JBLabel(progressBottomLabel).apply {
          alignmentX = LEFT_ALIGNMENT
          border = JBUI.Borders.empty(4, 0)
          foreground = Color.lightGray
        })
      }
      if (buttonLabel.isNotEmpty()) {
        add(JButton(buttonLabel).apply {
          alignmentX = LEFT_ALIGNMENT
          addActionListener(listener)
        })
      }
      if (body != null) {
        body.alignmentX = LEFT_ALIGNMENT
        add(body)
      }

      revalidate()
      repaint()
    }
  }

  private fun createScanningPanel(
    showLoadingIcon: Boolean, showSuccessIcon: Boolean, scanningLabel: String, listener: HyperlinkListener?
  ): JPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
    if (showLoadingIcon) {
      add(AsyncProcessIcon("ScanningLabel"))
    }
    if (showLoadingIcon || scanningLabel.isNotEmpty()) {
      add(JBLabel(scanningLabel).apply {
        foreground = Color.lightGray
        icon = StudioIcons.Common.SUCCESS.takeIf { showSuccessIcon }
      })
    }
    if (listener != null) {
      add(HyperlinkLabel().apply {
        setHyperlinkText("Check again")
        addHyperlinkListener(listener)
      })
    }

    isOpaque = false
    border = JBUI.Borders.empty(16, 0)
  }

  private fun createSuccessPanel(successLabel: String): JPanel = JPanel(BorderLayout()).apply {
    add(ScalingImagePanel().apply {
      Disposer.register(this@DevicesConnectionStep, this)
      preferredSize = JBUI.size(150, 150)
      scaledImageProvider = SVGScaledImageProvider.create(StudioIcons.Common.SUCCESS)
    }, BorderLayout.NORTH)
    add(JBLabel(successLabel).apply {
      verticalAlignment = SwingConstants.TOP
      horizontalAlignment = SwingConstants.CENTER
      font = JBFont.label().asBold()
    }, BorderLayout.CENTER)

    border = JBUI.Borders.empty(32, 0, 0, 0)
  }

  private suspend fun showUiLaunchingDevice(progressTopLabel: String, progressBottomLabel: String) = showUI(
    header = "Starting devices",
    description = "<html>Starting the devices and establishing a connection between the companion and Wear OS device</html>",
    progressTopLabel = progressTopLabel,
    progressBottomLabel = progressBottomLabel
  )

  private suspend fun showUiLaunchingDevice(deviceName: String) = showUiLaunchingDevice(
    progressTopLabel = "Starting devices...",
    progressBottomLabel = "Launching $deviceName"
  )

  private suspend fun showUiBridgingDevices() = showUiLaunchingDevice(
    progressTopLabel = "Connecting devices...",
    progressBottomLabel = "Establishing a bridge to communicate between devices"
  )

  private suspend fun showUiInstallCompanionAppScanning(phoneDevice: IDevice) = showUiInstallCompanionApp(
    phoneDevice = phoneDevice,
    showLoadingIcon = true,
    scanningLabel = "Scanning for installation of Wear OS app",
  )

  private suspend fun showUiInstallCompanionAppSuccess(phoneDevice: IDevice) = showUiInstallCompanionApp(
    phoneDevice = phoneDevice,
    showSuccessIcon = true,
    scanningLabel = "Wear OS installed on companion device",
  )

  private suspend fun showUiInstallCompanionAppRetry(phoneDevice: IDevice) = showUiInstallCompanionApp(
    phoneDevice = phoneDevice,
    scanningLabel = "Could not detect installation of Wear OS.",
    listener = {
      check(runningJob?.isActive != true) // This is a manual retry. No job should be running at this point.
      runningJob = GlobalScope.launch(ioThread) {
        showInstallCompanionAppPhase(phoneDevice)
      }
    }
  )

  private suspend fun showUiInstallCompanionApp(
    phoneDevice: IDevice, showLoadingIcon: Boolean = false, showSuccessIcon: Boolean = false, scanningLabel: String,
    listener: HyperlinkListener? = null
  ) = showUI(
    header = "Install Wear OS Companion Application",
    description = "<html>Wear OS app is unavailable on the companion device. Sign into the Play store and install " +
                  "the companion app.</html>",
    body = createScanningPanel(showLoadingIcon, showSuccessIcon, scanningLabel, listener),
    buttonLabel = "Open Wear OS in Play Store",
    listener = {
      GlobalScope.launch(ioThread) {
        phoneDevice.executeShellCommand("am start -a android.intent.action.VIEW -d 'market://details?id=$WEAR_PACKAGE'")
      }
    }
  )

  private suspend fun showUiPairing(
    phoneDevice: IDevice, showLoadingIcon: Boolean = false, showSuccessIcon: Boolean = false, scanningLabel: String,
    listener: HyperlinkListener? = null
  ) = showUI(
    header = "Complete Wear OS pairing",
    description = "<html>Open the Wear companion app on device and follow the instructions provided to complete pairing setup.</html>",
    body = createScanningPanel(showLoadingIcon, showSuccessIcon, scanningLabel, listener),
    buttonLabel = "Open Wear OS Companion App",
    listener = {
      GlobalScope.launch(ioThread) {
        phoneDevice.executeShellCommand("am start -n $WEAR_PACKAGE/$WEAR_MAIN_ACTIVITY")
      }
    }
  )

  private suspend fun showUiPairingScanning(phoneDevice: IDevice) = showUiPairing(
    phoneDevice = phoneDevice,
    showLoadingIcon = true,
    scanningLabel = "Waiting on completion of companion device pairing",
  )

  private suspend fun showUiPairingSuccess(phoneName: String, watchName: String) = showUI(
    header = "Successful pairing",
    body = createSuccessPanel("<html> $phoneName paired with $watchName</html>")
  )

  private suspend fun showUiPairingRetry(phoneDevice: IDevice, wearDevice: IDevice) = showUiPairing(
    phoneDevice = phoneDevice,
    scanningLabel = "Could not detect completion of Wear OS paring.",
    listener = {
      check(runningJob?.isActive != true) // This is a manual retry. No job should be running at this point.
      runningJob = GlobalScope.launch(ioThread) {
        showPairingPhase(phoneDevice, wearDevice)
      }
    }
  )
}

suspend fun <T> Future<T>.await(): T {
  // There is no good way to convert a Java Future to a suspendCoroutine
  if (this is CompletionStage<*>) {
    @Suppress("UNCHECKED_CAST")
    return this.await() as T
  }

  while (!isDone) {
    delay(1)
  }
  @Suppress("BlockingMethodInNonBlockingContext")
  return get() // If isDone() returned true, this call will not block
}

private suspend fun IDevice.isCompanionAppInstalled(): Boolean {
  val output = runShellCommand("dumpsys package $WEAR_PACKAGE | grep versionName")
  return output.contains("versionName=")
}

private suspend fun devicesPaired(phoneDevice: IDevice, wearDevice: IDevice): Boolean {
  val phoneDeviceID = phoneDevice.loadNodeID()
  if (phoneDeviceID.isNotEmpty()) {
    val wearPattern = "connection to peer node: $phoneDeviceID"
    val wearOutput = wearDevice.runShellCommand("dumpsys activity service WearableService | grep '$wearPattern'")
    return wearOutput.isNotBlank()
  }
  return false
}