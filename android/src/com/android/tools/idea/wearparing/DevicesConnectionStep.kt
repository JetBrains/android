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
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ListenerManager
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
import com.intellij.ui.components.labels.LinkLabel
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
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component.LEFT_ALIGNMENT
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkListener

private const val WEAR_PACKAGE = "com.google.android.wearable.app"
private const val WEAR_MAIN_ACTIVITY = "com.google.android.clockwork.companion.launcher.LauncherActivity"
private const val TIME_TO_SHOW_MANUAL_RETRY = 60_000L


class DevicesConnectionStep(model: WearDevicePairingModel,
                            val project: Project,
                            val showFirstStage: Boolean,
                            val restartPairingAction: (Boolean) -> Unit) : ModelWizardStep<WearDevicePairingModel>(model, "") {
  private var runningJob: Job? = null
  private var currentUiHeader = ""
  private var currentUiDescription = ""
  private lateinit var wizardFacade: ModelWizard.Facade
  private val canGoForward = BoolValueProperty()
  private val deviceStateListener = ListenerManager()
  private val bindings = BindingsManager()
  private val mainPanel = JBPanel<JBPanel<*>>(null).apply {
    border = JBUI.Borders.empty(24, 24, 0, 24)
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
  }

  override fun onWizardStarting(wizard: ModelWizard.Facade) {
    wizardFacade = wizard
  }

  override fun onEntering() {
    dispose() // Cancel any previous jobs and error listeners

    runningJob = GlobalScope.launch(ioThread) {
      if (model.phoneDevice.valueOrNull == null || model.wearDevice.valueOrNull == null) {
        showUI(header = message("wear.assistant.device.connection.error.title"),
               description = message("wear.assistant.device.connection.error.subtitle"))
        return@launch
      }

      val wearPairingDevice = model.wearDevice.value
      val wearDevice = wearPairingDevice.launchDeviceIfNeeded()
      val phonePairingDevice = model.phoneDevice.value
      val phoneDevice = phonePairingDevice.launchDeviceIfNeeded()

      prepareErrorListener()
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

  override fun canGoBack(): Boolean = false

  override fun dispose() {
    runningJob?.cancel(null)
    deviceStateListener.releaseAll()
    bindings.releaseAll()
  }

  private suspend fun showFirstPhase(phonePairingDevice: PairingDevice, phoneDevice: IDevice,
                                     wearPairingDevice: PairingDevice, wearDevice: IDevice) {
    showUiBridgingDevices()
    WearPairingManager.setKeepForwardAlive(phonePairingDevice, wearPairingDevice)
    createDeviceBridge(phoneDevice, wearDevice)

    if (phoneDevice.isCompanionAppInstalled()) {
      // Companion App already installed, go to the next step
      showUiInstallCompanionAppSuccess(phoneDevice)
      goToNextStep()
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

    try {
      val iDevice = futureDevice.await()
      launch = { Futures.immediateFuture(iDevice) }  // We can only launch AVDs once!
      return iDevice
    }
    catch (ex: Throwable) {
      showDeviceError(this)
      throw RuntimeException(ex)
    }
  }

  private suspend fun showUI(
    header: String = "", description: String = "",
    progressTopLabel: String = "", progressBottomLabel: String = "",
    body: JComponent? = null,
    buttonLabel: String = "", listener: (ActionEvent) -> Unit = {}
  ) = withContext(uiThread(ModalityState.any())) {
    currentUiHeader = header
    currentUiDescription = description

    mainPanel.apply {
      removeAll()

      if (header.isNotEmpty()) {
        add(JBLabel(header, UIUtil.ComponentStyle.LARGE).apply {
          name = "header"
          alignmentX = LEFT_ALIGNMENT
          font = JBFont.label().biggerOn(5.0f)
        })
      }
      if (description.isNotEmpty()) {
        add(JBLabel(description).apply {
          name = "description"
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
        setHyperlinkText(message("wear.assistant.device.connection.check.again"))
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
    header = message("wear.assistant.device.connection.start.device.title"),
    description = message("wear.assistant.device.connection.start.device.subtitle"),
    progressTopLabel = progressTopLabel,
    progressBottomLabel = progressBottomLabel
  )

  private suspend fun showUiLaunchingDevice(deviceName: String) = showUiLaunchingDevice(
    progressTopLabel = message("wear.assistant.device.connection.start.device.top.label"),
    progressBottomLabel = message("wear.assistant.device.connection.start.device.bottom.label", deviceName)
  )

  private suspend fun showUiBridgingDevices() = showUiLaunchingDevice(
    progressTopLabel = message("wear.assistant.device.connection.connecting.device.top.label"),
    progressBottomLabel = message("wear.assistant.device.connection.connecting.device.bottom.label")
  )

  private suspend fun showUiInstallCompanionAppScanning(phoneDevice: IDevice) = showUiInstallCompanionApp(
    phoneDevice = phoneDevice,
    showLoadingIcon = true,
    scanningLabel = message("wear.assistant.device.connection.scanning.wear.os"),
  )

  private suspend fun showUiInstallCompanionAppSuccess(phoneDevice: IDevice) = showUiInstallCompanionApp(
    phoneDevice = phoneDevice,
    showSuccessIcon = true,
    scanningLabel = message("wear.assistant.device.connection.wear.os.installed"),
  )

  private suspend fun showUiInstallCompanionAppRetry(phoneDevice: IDevice) = showUiInstallCompanionApp(
    phoneDevice = phoneDevice,
    scanningLabel = message("wear.assistant.device.connection.wear.os.missing"),
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
    header = message("wear.assistant.device.connection.install.wear.os.title"),
    description = message("wear.assistant.device.connection.install.wear.os.subtitle"),
    body = createScanningPanel(showLoadingIcon, showSuccessIcon, scanningLabel, listener),
    buttonLabel = message("wear.assistant.device.connection.install.wear.os.button"),
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
    header = message("wear.assistant.device.connection.complete.pairing.title"),
    description = message("wear.assistant.device.connection.complete.pairing.subtitle"),
    body = createScanningPanel(showLoadingIcon, showSuccessIcon, scanningLabel, listener),
    buttonLabel = message("wear.assistant.device.connection.open.companion.button"),
    listener = {
      GlobalScope.launch(ioThread) {
        phoneDevice.executeShellCommand("am start -n $WEAR_PACKAGE/$WEAR_MAIN_ACTIVITY")
      }
    }
  )

  private suspend fun showUiPairingScanning(phoneDevice: IDevice) = showUiPairing(
    phoneDevice = phoneDevice,
    showLoadingIcon = true,
    scanningLabel = message("wear.assistant.device.connection.wait.pairing"),
  )

  private suspend fun showUiPairingSuccess(phoneName: String, watchName: String) = showUI(
    header = message("wear.assistant.device.connection.pairing.success.title"),
    body = createSuccessPanel(message("wear.assistant.device.connection.pairing.success.subtitle", phoneName, watchName))
  )

  private suspend fun showUiPairingRetry(phoneDevice: IDevice, wearDevice: IDevice) = showUiPairing(
    phoneDevice = phoneDevice,
    scanningLabel = message("wear.assistant.device.connection.pairing.not.detected"),
    listener = {
      check(runningJob?.isActive != true) // This is a manual retry. No job should be running at this point.
      runningJob = GlobalScope.launch(ioThread) {
        showPairingPhase(phoneDevice, wearDevice)
      }
    }
  )

  private fun prepareErrorListener() {
    deviceStateListener.listenAll(model.phoneDevice, model.wearDevice).withAndFire {
      val errorDevice = model.phoneDevice.valueOrNull.takeIf { it?.isOnline() == false }
                        ?: model.wearDevice.valueOrNull.takeIf { it?.isOnline() == false }
      if (errorDevice != null) {
        showDeviceError(errorDevice)
      }
    }
  }

  private fun showDeviceError(errorDevice: PairingDevice) {
    dispose()
    GlobalScope.launch(ioThread) {
      val body = JPanel().apply {
        layout = GridBagLayout()
        add(
          JBLabel(StudioIcons.Common.WARNING).withBorder(JBUI.Borders.empty(0, 0, 0, 8)),
          gridConstraint(x = 0, y = 0)
        )
        add(
          JBLabel(message("wear.assistant.device.connection.error", errorDevice.displayName)),
          gridConstraint(x = 1, y = 0, weightx = 1.0, fill = GridBagConstraints.HORIZONTAL)
        )
        add(
          LinkLabel<Unit>(message("wear.assistant.device.connection.restart.pairing"), null) { _, _ -> restartPairingAction(true) },
          gridConstraint(x = 1, y = 1, weightx = 1.0, weighty = 1.0, fill = GridBagConstraints.HORIZONTAL)
        )
      }
      showUI(header = currentUiHeader, description = currentUiDescription, body = body)
    }
  }

  private fun goToNextStep() {
    // The "Next" button changes asynchronously. Create a temporary property that will change state at the same time.
    val doGoForward = BoolValueProperty()
    bindings.bind(doGoForward, canGoForward)
    deviceStateListener.listen(doGoForward) {
      ApplicationManager.getApplication().invokeLater {
        wizardFacade.goForward()
      }
    }

    canGoForward.set(true)
  }
}

suspend fun <T> Future<T>.await(): T {
  // There is no good way to convert a Java Future to a suspendCoroutine
  if (this is CompletionStage<*>) {
    @Suppress("UNCHECKED_CAST")
    return this.await() as T
  }

  while (!isDone) {
    delay(100)
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