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

import com.android.ddmlib.EmulatorConsole
import com.android.ddmlib.IDevice
import com.android.tools.adtui.HtmlLabel
import com.android.tools.adtui.ui.SVGScaledImageProvider
import com.android.tools.adtui.ui.ScalingImagePanel
import com.android.tools.idea.concurrency.AndroidDispatchers.ioThread
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.google.common.util.concurrent.Futures
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBColor
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
import kotlinx.coroutines.withTimeoutOrNull
import org.apache.commons.lang.time.StopWatch
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.BorderLayout
import java.awt.Component.LEFT_ALIGNMENT
import java.awt.Component.TOP_ALIGNMENT
import java.awt.GridBagConstraints.HORIZONTAL
import java.awt.GridBagConstraints.RELATIVE
import java.awt.GridBagConstraints.REMAINDER
import java.awt.GridBagConstraints.VERTICAL
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkListener

private const val WEAR_PACKAGE = "com.google.android.wearable.app"
private const val WEAR_MAIN_ACTIVITY = "com.google.android.clockwork.companion.launcher.LauncherActivity"
private const val TIME_TO_SHOW_MANUAL_RETRY = 60_000L
private const val PATH_PLAY_SCREEN = "/wearPairing/screens/playStore.png"
private const val PATH_PAIR_SCREEN = "/wearPairing/screens/wearPair.png"

private val LOG get() = logger<WearPairingManager>()

class DevicesConnectionStep(model: WearDevicePairingModel,
                            val project: Project,
                            val wizardAction: WizardAction,
                            private val isFirstStage: Boolean = true) : ModelWizardStep<WearDevicePairingModel>(model, "") {
  private var runningJob: Job? = null
  private var currentUiHeader = ""
  private var currentUiDescription = ""
  private val secondStageStep = if (isFirstStage) DevicesConnectionStep(model, project, wizardAction, false) else null
  private lateinit var wizardFacade: ModelWizard.Facade
  private lateinit var phoneIDevice: IDevice
  private lateinit var wearIDevice: IDevice
  private val canGoForward = BoolValueProperty()
  private val deviceStateListener = ListenerManager()
  private val bindings = BindingsManager()
  private val mainPanel = JBPanel<JBPanel<*>>(GridBagLayout()).apply {
    border = JBUI.Borders.empty(24, 24, 0, 24)
  }

  override fun createDependentSteps(): Collection<ModelWizardStep<*>> {
    return if (secondStageStep == null) super.createDependentSteps() else listOf(secondStageStep)
  }

  override fun onWizardStarting(wizard: ModelWizard.Facade) {
    wizardFacade = wizard
  }

  override fun onEntering() {
    dispose() // Cancel any previous jobs and error listeners
    model.removePairingOnCancel.set(true)

    runningJob = GlobalScope.launch(ioThread) {
      if (model.selectedPhoneDevice.valueOrNull == null || model.selectedWearDevice.valueOrNull == null) {
        showUI(header = message("wear.assistant.device.connection.error.title"),
               description = message("wear.assistant.device.connection.error.subtitle"))
        return@launch
      }

      if (isFirstStage) {
        killNonSelectedRunningWearEmulators()
        phoneIDevice = model.selectedPhoneDevice.launchDeviceIfNeeded()
        wearIDevice = model.selectedWearDevice.launchDeviceIfNeeded()
        secondStageStep!!.phoneIDevice = phoneIDevice
        secondStageStep.wearIDevice = wearIDevice
        LOG.warn("Devices are online")
      }

      prepareErrorListener()
      if (isFirstStage) {
        showFirstPhase(model.selectedPhoneDevice.value, phoneIDevice, model.selectedWearDevice.value, wearIDevice)
      }
      else {
        showPairingPhase(phoneIDevice, wearIDevice)
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
    WearPairingManager.removePairedDevices()
    WearPairingManager.setPairedDevices(phonePairingDevice, wearPairingDevice)
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
        showUiPairingSuccess(model.selectedPhoneDevice.value.displayName, model.selectedWearDevice.value.displayName)
        canGoForward.set(true)
        return
      }
      delay(1000)
    }

    showUiPairingRetry(phoneDevice, wearDevice)  // After some time we give up and show the manual retry ui
  }

  private suspend fun OptionalProperty<PairingDevice>.launchDeviceIfNeeded(): IDevice {
    try {
      showUiLaunchingDevice(value.displayName)

      var isColdBoot = false
      val iDevice = value.launch(project).await()
      value.launch = { Futures.immediateFuture(iDevice) }  // We can only launch AVDs once!

      // If it was not launched by us, it may still be booting. Wait for "boot complete".
      while (!iDevice.arePropertiesSet() || iDevice.getProperty("dev.bootcomplete") == null) {
        LOG.warn("${iDevice.name} not ready yet")
        isColdBoot = true
        delay(2000)
      }

      if (isColdBoot || iDevice.retrieveUpTime() < 200.0) {
        // Give some time for Node/Cloud ID to load, but not too long, as it may just mean it never paired before
        showUiWaitingDeviceStatus()
        waitForCondition(50_000) { iDevice.loadNodeID().isNotEmpty()}
        waitForCondition(10_000) { iDevice.loadCloudNetworkID().isNotEmpty() }
      }

      return iDevice
    }
    catch (ex: Throwable) {
      showDeviceError(value)
      throw RuntimeException(ex)
    }
  }

  private suspend fun killNonSelectedRunningWearEmulators() {
    model.getNonSelectedRunningWearEmulators().apply {
      if (isNotEmpty()) {
        showUiLaunchingDevice(model.selectedWearDevice.valueOrNull?.displayName ?: "")
        WearPairingManager.removePairedDevices() // Remove pairing, in case we need to kill a paired device and that would show a toast
      }
      forEach {
        val iDevice = it.launch(project).get() // If device is running, then calling get() will return immediately
        EmulatorConsole.getConsole(iDevice)?.apply {
          kill()
          close()
        }
      }
    }

    val stopWatch = StopWatch().apply { start() }
    while (stopWatch.time < TIME_TO_SHOW_MANUAL_RETRY && model.getNonSelectedRunningWearEmulators().isNotEmpty()) {
      delay(200)
    }
  }

  private suspend fun showUI(
    header: String = "", description: String = "",
    progressTopLabel: String = "", progressBottomLabel: String = "",
    body: JComponent? = null,
    buttonLabel: String = "",
    firstStepLabel: String = "",
    additionalStepsLabel: String = "",
    imagePath: String = "",
    listener: (ActionEvent) -> Unit = {}
  ) = withContext(uiThread(ModalityState.any())) {
    currentUiHeader = header
    currentUiDescription = description

    mainPanel.apply {
      removeAll()

      if (header.isNotEmpty()) {
        add(JBLabel(header, UIUtil.ComponentStyle.LARGE).apply {
          name = "header"
          font = JBFont.label().biggerOn(5.0f)
        }, gridConstraint(x = 0, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL, gridwidth = 3))
      }
      if (description.isNotEmpty()) {
        add(HtmlLabel().apply {
          name = "description"
          HtmlLabel.setUpAsHtmlLabel(this)
          text = description
          border = JBUI.Borders.empty(20, 0, 20, 16)
        }, gridConstraint(x = 0, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL, gridwidth = 3))
      }
      if (progressTopLabel.isNotEmpty()) {
        add(JBLabel(progressTopLabel).apply {
          border = JBUI.Borders.empty(4, 0)
        }, gridConstraint(x = 0, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL, gridwidth = 2))
        add(JProgressBar().apply {
          isIndeterminate = true
        }, gridConstraint(x = 0, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL, gridwidth = 2))
      }
      if (progressBottomLabel.isNotEmpty()) {
        add(JBLabel(progressBottomLabel).apply {
          border = JBUI.Borders.empty(4, 0)
          foreground = JBColor.DARK_GRAY
        }, gridConstraint(x = 0, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL, gridwidth = 2))
      }
      if (firstStepLabel.isNotEmpty()) {
        add(JBLabel(firstStepLabel).apply {
          border = JBUI.Borders.empty(8, 0, 8, 16)
        }, gridConstraint(x = 0, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL, gridwidth = 2))
      }
      if (buttonLabel.isNotEmpty()) {
        add(JButton(buttonLabel).apply {
          addActionListener(listener)
        }, gridConstraint(x = 0, y = RELATIVE, fill = HORIZONTAL, gridwidth = 1))
      }
      if (body != null) {
        add(body, gridConstraint(x = 0, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL, gridwidth = 2))
      }
      if (additionalStepsLabel.isNotEmpty()) {
        add(JBLabel(additionalStepsLabel).apply {
          alignmentX = LEFT_ALIGNMENT
          border = JBUI.Borders.empty(4, 0, 0, 16)
        }, gridConstraint(x = 0, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL, gridwidth = 2))
      }
      if (imagePath.isNotEmpty()) {
        add(JBLabel(IconLoader.getIcon(imagePath, DevicesConnectionStep::class.java)).apply {
          verticalAlignment = JLabel.BOTTOM
        }, gridConstraint(x = 2, y = RELATIVE, fill = VERTICAL, weighty = 1.0).apply { gridheight = REMAINDER })
      }
      add(Box.createVerticalGlue(), gridConstraint(x = 0, y = RELATIVE, weighty = 1.0))

      revalidate()
      repaint()
    }
  }

  private fun createScanningPanel(
    showLoadingIcon: Boolean, showSuccessIcon: Boolean, scanningLabel: String, listener: HyperlinkListener?
  ): JPanel = JPanel(GridBagLayout()).apply {
    add(JPanel().apply {
      layout = BoxLayout(this, BoxLayout.LINE_AXIS)
      alignmentX = LEFT_ALIGNMENT
      if (showLoadingIcon) {
        add(AsyncProcessIcon("ScanningLabel").apply {
          alignmentY = TOP_ALIGNMENT
          border = JBUI.Borders.empty(0, 0, 0, 8)
        })
      }
      if (showLoadingIcon || scanningLabel.isNotEmpty()) {
        add(JBLabel(scanningLabel).apply {
          foreground = JBColor.DARK_GRAY
          icon = StudioIcons.Common.SUCCESS.takeIf { showSuccessIcon }
          alignmentY = TOP_ALIGNMENT
        })
      }
    }, gridConstraint(x = 0, y = 0, weightx = 1.0, fill = HORIZONTAL))
    if (listener != null) {
      add(HyperlinkLabel().apply {
        setHyperlinkText(message("wear.assistant.device.connection.check.again"))
        addHyperlinkListener(listener)
      }, gridConstraint(x = 0, y = 1, weightx = 1.0, fill = HORIZONTAL))
    }
    isOpaque = false
    border = JBUI.Borders.empty(8, 2, 12, 4)
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

  private suspend fun showUiWaitingDeviceStatus() = showUiLaunchingDevice(
    progressTopLabel = message("wear.assistant.device.connection.connecting.device.top.label"),
    progressBottomLabel = message("wear.assistant.device.connection.status.device.bottom.label")
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
    description = message("wear.assistant.device.connection.install.wear.os.subtitle", WEAR_DOCS_LINK),
    body = createScanningPanel(showLoadingIcon, showSuccessIcon, scanningLabel, listener),
    buttonLabel = message("wear.assistant.device.connection.install.wear.os.button"),
    firstStepLabel = message("wear.assistant.device.connection.install.wear.os.firstStep"),
    additionalStepsLabel = message("wear.assistant.device.connection.install.wear.os.additionalSteps"),
    imagePath = PATH_PLAY_SCREEN,
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
    description = message("wear.assistant.device.connection.complete.pairing.subtitle", WEAR_DOCS_LINK),
    body = createScanningPanel(showLoadingIcon, showSuccessIcon, scanningLabel, listener),
    buttonLabel = message("wear.assistant.device.connection.open.companion.button"),
    firstStepLabel = message("wear.assistant.device.connection.complete.pairing.firstStep"),
    additionalStepsLabel = message("wear.assistant.device.connection.complete.pairing.additionalSteps"),
    imagePath = PATH_PAIR_SCREEN,
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
    deviceStateListener.listenAll(model.selectedPhoneDevice, model.selectedWearDevice).withAndFire {
      val errorDevice = model.selectedPhoneDevice.valueOrNull.takeIf { it?.isOnline() == false }
                        ?: model.selectedWearDevice.valueOrNull.takeIf { it?.isOnline() == false }
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
          gridConstraint(x = 1, y = 0, weightx = 1.0, fill = HORIZONTAL)
        )
        add(
          LinkLabel<Unit>(message("wear.assistant.device.connection.restart.pairing"), null) { _, _ -> wizardAction.restart(project) },
          gridConstraint(x = 1, y = 1, weightx = 1.0, weighty = 1.0, fill = HORIZONTAL)
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

private suspend fun waitForCondition(timeMillis: Long, condition: suspend () -> Boolean) {
  withTimeoutOrNull(timeMillis) {
    while (!condition()) {
      delay(1000)
    }
  }
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