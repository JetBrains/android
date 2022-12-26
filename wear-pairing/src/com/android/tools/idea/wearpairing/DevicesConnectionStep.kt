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
package com.android.tools.idea.wearpairing

import com.android.ddmlib.IDevice
import com.android.tools.adtui.HtmlLabel
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.observable.BindingsManager
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.observable.core.OptionalProperty
import com.android.tools.idea.run.DeviceHeadsUpListener
import com.android.tools.idea.wearpairing.AndroidWearPairingBundle.Companion.message
import com.android.tools.idea.wearpairing.WearPairingManager.PairingState
import com.android.tools.idea.wearpairing.WearPairingManager.PhoneWearPair
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.google.common.util.concurrent.Futures
import com.google.wireless.android.sdk.stats.WearPairingEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
import com.intellij.util.SVGLoader
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.kotlin.tools.projectWizard.wizard.ui.addBorder
import java.awt.EventQueue
import java.awt.GridBagConstraints.HORIZONTAL
import java.awt.GridBagConstraints.LINE_START
import java.awt.GridBagConstraints.RELATIVE
import java.awt.GridBagConstraints.REMAINDER
import java.awt.GridBagConstraints.VERTICAL
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.Future
import javax.swing.Box
import javax.swing.Box.createVerticalStrut
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingConstants
import javax.swing.event.HyperlinkListener

private const val WEAR_MAIN_ACTIVITY = "com.google.android.clockwork.companion.launcher.LauncherActivity"
private const val TIME_TO_SHOW_MANUAL_RETRY = 60_000L
private const val TIME_TO_INSTALL_COMPANION_APP = 120_000L
private const val PATH_PLAY_SCREEN = "/screens/playStore.png"
private const val PATH_PAIR_SCREEN = "/screens/wearPair.png"

private val LOG get() = logger<WearPairingManager>()

class DevicesConnectionStep(model: WearDevicePairingModel,
                            val project: Project?,
                            private val wizardAction: WizardAction,
                            private val isFirstStage: Boolean = true) : ModelWizardStep<WearDevicePairingModel>(model, "") {
  private val coroutineScope = AndroidCoroutineScope(this)
  private var runningJob: Job? = null
  private var backgroundJob: Job? = null // Independent of the UI state, monitors the devices for pairing
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
    border = empty(24, 24, 0, 24)
  }

  override fun createDependentSteps(): Collection<ModelWizardStep<*>> {
    return if (secondStageStep == null) super.createDependentSteps() else listOf(secondStageStep)
  }

  override fun onWizardStarting(wizard: ModelWizard.Facade) {
    wizardFacade = wizard
  }

  override fun onEntering() {
    startStepFlow()
  }

  override fun getComponent(): JComponent = mainPanel

  override fun canGoForward(): ObservableBool = canGoForward

  override fun canGoBack(): Boolean = false

  override fun dispose() {
    synchronized(this) { // Dispose can be called from the UI thread or from coroutines
      runningJob?.cancel(null)
      backgroundJob?.cancel(null)
      deviceStateListener.releaseAll()
      bindings.releaseAll()
    }
  }

  private suspend fun forceDispose() {
    runningJob?.cancelAndJoin()
    backgroundJob?.cancelAndJoin()
    dispose()
  }

  private fun startStepFlow() {
    model.removePairingOnCancel.set(true)

    dispose() // Cancel any previous jobs and error listeners
    runningJob = coroutineScope.launch(Dispatchers.IO) {
      if (model.selectedPhoneDevice.valueOrNull == null || model.selectedWearDevice.valueOrNull == null) {
        showUI(header = message("wear.assistant.device.connection.error.title"),
               description = message("wear.assistant.device.connection.error.subtitle"))
        return@launch
      }

      if (isFirstStage) {
        phoneIDevice = model.selectedPhoneDevice.launchDeviceIfNeeded() ?: return@launch
        wearIDevice = model.selectedWearDevice.launchDeviceIfNeeded() ?: return@launch
        secondStageStep!!.phoneIDevice = phoneIDevice
        secondStageStep.wearIDevice = wearIDevice

        waitForDevicePairingStatus(phoneIDevice, wearIDevice)
        LOG.warn("Devices are online")
      }

      prepareErrorListener()
      if (isFirstStage) {
        showFirstPhase(model.selectedPhoneDevice.value, phoneIDevice, model.selectedWearDevice.value, wearIDevice)
      }
      else {
        showSecondPhase(model.selectedPhoneDevice.value, phoneIDevice, model.selectedWearDevice.value, wearIDevice)
      }
    }
  }

  private suspend fun showFirstPhase(phonePairingDevice: PairingDevice, phoneDevice: IDevice,
                                     wearPairingDevice: PairingDevice, wearDevice: IDevice) {
    if (!phoneDevice.hasPairingFeature(PairingFeature.MULTI_WATCH_SINGLE_PHONE_PAIRING)) {
      showDeviceGmscoreNeedsUpdate(phonePairingDevice)
      phoneDevice.executeShellCommand("am start -a android.intent.action.VIEW -d 'market://details?id=com.google.android.gms'")
      showEmbeddedEmulator(phoneDevice)
      return
    }
    if (!wearDevice.hasPairingFeature(PairingFeature.REVERSE_PORT_FORWARD)) {
      showDeviceGmscoreNeedsUpdate(wearPairingDevice)
      wearDevice.executeShellCommand("am start -a android.intent.action.VIEW -d 'market://details?id=com.google.android.gms'")
      showEmbeddedEmulator(wearDevice)
      return
    }
    showUiBridgingDevices()
    if (checkWearMayNeedFactoryReset(phoneDevice, wearDevice)) {
      showUiNeedsFactoryReset(model.selectedWearDevice.value.displayName)
      return
    }
    val isNewWearPairingDevice =
      WearPairingManager.getInstance().getPairsForDevice(phonePairingDevice.deviceID)
      .firstOrNull { wearPairingDevice.deviceID == it.wear.deviceID } == null
    WearPairingManager.getInstance().removeAllPairedDevices(wearPairingDevice.deviceID, restartWearGmsCore = isNewWearPairingDevice)

    companionAppStep(phoneDevice, wearDevice)
  }

  private suspend fun companionAppStep(phoneDevice: IDevice, wearDevice: IDevice) {
    val companionAppId = wearDevice.getCompanionAppIdForWatch()
    if (phoneDevice.isCompanionAppInstalled(companionAppId)) {
      // Companion App already installed, go to the next step
      goToNextStep()
    }
    else if (companionAppId == OEM_COMPANION_FALLBACK_APP_ID) {
      // Wear 2.x companion app
      showUiInstallCompanionAppInstructions(phoneDevice, wearDevice)
    }
    else {
      showIncompatibleCompanionAppError(phoneDevice, wearDevice)
    }
  }

  private fun showIncompatibleCompanionAppError(phoneDevice: IDevice, wearDevice: IDevice) {
    dispose()
    coroutineScope.launch(Dispatchers.IO) {
      val body = createWarningPanel(message("wear.assistant.device.connection.wear.os.wear3"))
      body.add(
        LinkLabel<Unit>("Retry", null) { _, _ ->
          check(runningJob?.isActive != true) // This is a manual retry. No job should be running at this point.
          runningJob = coroutineScope.launch(Dispatchers.IO) {
            companionAppStep(phoneDevice, wearDevice)
          }
        },
        gridConstraint(x = 1, y = RELATIVE, anchor = LINE_START)
      )
      showUI(header = currentUiHeader, description = currentUiDescription, body = body)
    }
  }

  private fun showDeviceGmscoreNeedsUpdate(device: PairingDevice) {
    coroutineScope.launch(Dispatchers.IO) {
      val warningMessage = if (device.isWearDevice) {
        "wear.assistant.device.connection.gmscore.error.wear"
      } else if (device.isEmulator) {
        "wear.assistant.device.connection.gmscore.error.phone.emulator"
      } else {
        "wear.assistant.device.connection.gmscore.error.phone.physical"
      }
      val body = createWarningPanel(message(warningMessage, device.displayName), StudioIcons.Common.ERROR)
      body.add(
        LinkLabel<Unit>(message("wear.assistant.device.connection.restart.pairing"), null) { _, _ ->
          wizardAction.restart(project)
        }.addBorder(
          empty(10, 0)),
        gridConstraint(x = 1, y = RELATIVE, anchor = LINE_START)
      )
      showUI(header = currentUiHeader, description = currentUiDescription, body = body)
    }
  }

  private suspend fun showWaitForCompanionAppInstall(phoneDevice: IDevice, wearDevice: IDevice, launchPlayStore: Boolean) {
    if (launchPlayStore) {
      showUiInstallCompanionAppScanning(phoneDevice, wearDevice,
                                        scanningLabel = message("wear.assistant.device.connection.scanning.wear.os.btn"))
      phoneDevice.executeShellCommand(
        "am start -a android.intent.action.VIEW -d 'market://details?id=${wearDevice.getCompanionAppIdForWatch()}'")
      showEmbeddedEmulator(phoneDevice)
    }
    else {
      showUiInstallCompanionAppScanning(phoneDevice, wearDevice,
                                        scanningLabel = message("wear.assistant.device.connection.scanning.wear.os.lnk"))
    }

    if (waitForCondition(TIME_TO_INSTALL_COMPANION_APP) { phoneDevice.isCompanionAppInstalled(wearDevice.getCompanionAppIdForWatch()) }) {
      showUiInstallCompanionAppSuccess(phoneDevice, wearDevice)
      canGoForward.set(true)
    }
    else {
      showUiInstallCompanionAppRetry(phoneDevice, wearDevice) // After some time we give up and show the manual retry ui
    }
  }

  private suspend fun showSecondPhase(phone: PairingDevice, phoneDevice: IDevice, wear: PairingDevice, wearDevice: IDevice) {
    // Note: createPairedDeviceBridge() may restart GmsCore, so it may take a bit of time until pairing. Show some UI placeholder.
    showUiBridgingDevices()
    try {
      val phoneWearPair = WearPairingManager.getInstance().createPairedDeviceBridge(phone, phoneDevice, wear, wearDevice)
      if (phoneWearPair.pairingStatus != PairingState.CONNECTED) {
        showPairing(phoneWearPair, phoneDevice, wearDevice)
      }

      waitForPairingSuccessOnBackground(phoneWearPair, phoneDevice, wearDevice)
    }
    catch (ex: IOException) {
      showGenericError(ex)
    }
  }

  private suspend fun showPairing(phoneWearPair: PhoneWearPair, phoneDevice: IDevice, wearDevice: IDevice) {
    val companionAppId = wearDevice.getCompanionAppIdForWatch()
    if (phoneDevice.hasPairingFeature(PairingFeature.COMPANION_EMULATOR_ACTIVITY, companionAppId)) {
      showUiPairingNonInteractive(phoneWearPair, phoneDevice, wearDevice)
      NonInteractivePairing.startPairing(this, phoneDevice, wearDevice.avdName!!, companionAppId, wearDevice.loadNodeID()).use {
        withTimeoutOrNull(Duration.ofMinutes(1)) {
          it.pairingState.takeWhile { !it.hasFinished() }.collect { state ->
            if (state == NonInteractivePairing.PairingState.CONSENT) {
              showUiPairingNonInteractive(phoneWearPair, phoneDevice, wearDevice,
                                          message("wear.assistant.device.connection.pairing.auto.consent", phoneWearPair.phone.displayName))
            }
          }
        }
        if (WearPairingManager.getInstance().updateDeviceStatus(phoneWearPair, phoneDevice, wearDevice) != PairingState.CONNECTED) {
          showUiPairingNonInteractive(phoneWearPair, phoneDevice, wearDevice,
                                      message("wear.assistant.device.connection.pairing.auto.failed"),
                                      "Retry", "Skip to manual instructions", false)
        } // else waitForPairingSuccessOnBackground() will take care of success case
      }
    }
    else {
      showUiPairingAppInstructions(phoneWearPair, phoneDevice, wearDevice)
    }
  }

  private suspend fun showWaitForPairingSetup(phoneWearPair: PhoneWearPair, phoneDevice: IDevice, wearDevice: IDevice,
                                              launchCompanionApp: Boolean) {
    if (launchCompanionApp) {
      showUiPairingScanning(phoneWearPair, phoneDevice, wearDevice,
                            scanningLabel = message("wear.assistant.device.connection.wait.pairing.btn"))
      phoneDevice.executeShellCommand("am start -n ${wearDevice.getCompanionAppIdForWatch()}/$WEAR_MAIN_ACTIVITY")
      showEmbeddedEmulator(phoneDevice)
    }
    else {
      showUiPairingScanning(phoneWearPair, phoneDevice, wearDevice,
                            scanningLabel = message("wear.assistant.device.connection.wait.pairing.lnk"))
    }

    // After some time we give up and show the manual retry ui. waitForPairingSuccessOnBackground() will take care of success case
    delay(TIME_TO_SHOW_MANUAL_RETRY)
    showUiPairingRetry(phoneWearPair, phoneDevice, wearDevice)
  }

  private fun waitForPairingSuccessOnBackground(phoneWearPair: PhoneWearPair, phoneDevice: IDevice, wearDevice: IDevice) {
    check(backgroundJob?.isActive != true) // There can only be a single background job at any time
    backgroundJob = coroutineScope.launch(Dispatchers.IO) {
      try {
        while (phoneWearPair.pairingStatus != PairingState.CONNECTED &&
               WearPairingManager.getInstance().updateDeviceStatus(phoneWearPair, phoneDevice, wearDevice) != PairingState.CONNECTED) {
          delay(2_000)
        }

        // If 2.x companion older than 773393865 is used with manual pairing, we have to let the
        // user know how they can finish the pairing on the companion.
        val showTapAndFinishWarning =
          !phoneDevice.hasPairingFeature(PairingFeature.COMPANION_SKIP_AND_FINISH_FIXED, wearDevice.getCompanionAppIdForWatch())
        showPairingSuccess(phoneWearPair.phone.displayName, phoneWearPair.wear.displayName,
                           showTapAndFinishWarning)
      }
      catch (ex: IOException) {
        showGenericError(ex)
      }
    }
  }

  private suspend fun showPairingSuccess(phoneName: String, watchName: String, tapAndFinishWarning: Boolean) {
    showUiPairingSuccess(phoneName, watchName, tapAndFinishWarning)
    canGoForward.set(true)
  }

  private suspend fun OptionalProperty<PairingDevice>.launchDeviceIfNeeded(): IDevice? {
    try {
      showUiLaunchingDevice(value.displayName)

      val iDevice = value.launch(project).await()
      value.launch = { Futures.immediateFuture(iDevice) }  // We can only launch AVDs once!

      // If it was not launched by us, it may still be booting. Wait for "boot complete".
      while (!iDevice.arePropertiesSet() || iDevice.getProperty("dev.bootcomplete") == null) {
        LOG.warn("${iDevice.name} not ready yet")
        delay(2000)
      }

      return iDevice
    }
    catch (ex: Throwable) {
      showDeviceError(
        header = message("wear.assistant.connection.alert.cant.start.device.title", value.displayName), description = " ",
        errorMessage = message("wear.assistant.connection.alert.cant.start.device.subtitle", value.displayName)
      )
      LOG.warn("Failed to launch device", ex)
      return null
    }
  }

  private suspend fun IDevice.waitForPairingStatus() {
    if (hasPairingFeature(PairingFeature.GET_PAIRING_STATUS)) {
      waitForCondition(50_000) { isPairingStatusAvailable() }
    }
    else {
      // Give some time for Node/Cloud ID to load, but not too long, as it may just mean it never paired before
      waitForCondition(50_000) { loadNodeID().isNotEmpty() }
      waitForCondition(10_000) { loadCloudNetworkID(ignoreNullOutput = false).isNotEmpty() }
    }
  }

  private suspend fun waitForDevicePairingStatus(phoneDevice: IDevice, wearDevice: IDevice) {
    showUiWaitingDeviceStatus()

    val companionAppId = wearDevice.getCompanionAppIdForWatch()
    if (phoneDevice.isCompanionAppInstalled(companionAppId)) { // No need to wait, if Companion App is not installed
      phoneDevice.waitForPairingStatus()
    }

    wearDevice.waitForPairingStatus()
  }

  private suspend fun showUI(
    header: String = "", description: String = "",
    progressTopLabel: String = "", progressBottomLabel: String = "",
    body: JComponent? = null,
    imagePath: String = ""
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
          border = empty(20, 0, 20, 16)
        }, gridConstraint(x = 0, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL, gridwidth = 3))
      }
      if (progressTopLabel.isNotEmpty()) {
        add(JBLabel(progressTopLabel).apply {
          border = empty(4, 0)
        }, gridConstraint(x = 0, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL, gridwidth = 2))
        add(JProgressBar().apply {
          isIndeterminate = true
        }, gridConstraint(x = 0, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL, gridwidth = 2))
      }
      if (progressBottomLabel.isNotEmpty()) {
        add(JBLabel(progressBottomLabel).apply {
          border = empty(4, 0)
          foreground = JBColor.DARK_GRAY
        }, gridConstraint(x = 0, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL, gridwidth = 2))
      }
      if (body != null) {
        add(body, gridConstraint(x = 0, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL, gridwidth = 2))
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
    firstStepLabel: String,
    buttonLabel: String,
    buttonListener: (ActionEvent) -> Unit = {},
    showLoadingIcon: Boolean,
    showSuccessIcon: Boolean,
    scanningLabel: String,
    scanningLink: String,
    scanningListener: HyperlinkListener?,
    additionalStepsLabel: String
  ): JPanel = JPanel(GridBagLayout()).apply {
    add(
      JBLabel(firstStepLabel).addBorder(empty(8, 0, 8, 0)),
      gridConstraint(x = 0, y = 0, weightx = 1.0, fill = HORIZONTAL, gridwidth = 2)
    )
    if (buttonLabel.isNotBlank()) {
      add(
        JButton(buttonLabel).apply {
          addActionListener(buttonListener)
        },
        gridConstraint(x = 0, y = RELATIVE, gridwidth = 2, anchor = LINE_START)
      )
    }
    add(
      JBLabel(additionalStepsLabel).addBorder(empty(8, 0, 0, 0)),
      gridConstraint(x = 0, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL, gridwidth = 2)
    )
    if (showLoadingIcon) {
      add(
        AsyncProcessIcon("ScanningLabel").addBorder(empty(0, 0, 0, 8)),
        gridConstraint(x = 0, y = RELATIVE)
      )
    }
    if (showSuccessIcon || scanningLabel.isNotEmpty()) {
      add(
        JBLabel(scanningLabel).apply {
          foreground = JBColor.DARK_GRAY
          icon = StudioIcons.Common.SUCCESS.takeIf { showSuccessIcon }
        }.addBorder(empty(4, 0, 0, 0)),
        when (showLoadingIcon) { // Scanning label may be on the right of the "loading" icon
          true -> gridConstraint(x = 1, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL, gridwidth = 1)
          else -> gridConstraint(x = 0, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL, gridwidth = 2)
        }
      )
    }
    if (scanningLink.isNotEmpty()) {
      add(
        HyperlinkLabel().apply {
          setHyperlinkText(scanningLink)
          addHyperlinkListener(scanningListener)
        }.addBorder(empty(4, 0, 0, 0)),
        gridConstraint(x = 0, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL, gridwidth = 2)
      )
    }

    isOpaque = false
    border = empty(8, 2, 12, 4)
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

  private suspend fun showUiInstallCompanionAppInstructions(phoneDevice: IDevice, wearDevice: IDevice) {
    showUiInstallCompanionApp(
      phoneDevice = phoneDevice,
      wearDevice = wearDevice
    )

    WearPairingUsageTracker.log(WearPairingEvent.EventKind.SHOW_INSTALL_WEAR_OS_COMPANION)
  }

  private suspend fun showUiInstallCompanionAppScanning(phoneDevice: IDevice,
                                                        wearDevice: IDevice,
                                                        scanningLabel: String) = showUiInstallCompanionApp(
    phoneDevice = phoneDevice,
    showLoadingIcon = true,
    scanningLabel = scanningLabel,
    wearDevice = wearDevice
  )

  private suspend fun showUiInstallCompanionAppSuccess(phoneDevice: IDevice, wearDevice: IDevice) = showUiInstallCompanionApp(
    phoneDevice = phoneDevice,
    wearDevice = wearDevice,
    showSuccessIcon = true,
    scanningLabel = message("wear.assistant.device.connection.wear.os.installed"),
  )

  private suspend fun showUiInstallCompanionAppRetry(phoneDevice: IDevice, wearDevice: IDevice) = showUiInstallCompanionApp(
    phoneDevice = phoneDevice,
    wearDevice = wearDevice,
    scanningLabel = message("wear.assistant.device.connection.wear.os.missing"),
    scanningLink = message("wear.assistant.device.connection.check.again"),
    scanningListener = {
      check(runningJob?.isActive != true) // This is a manual retry. No job should be running at this point.
      runningJob = coroutineScope.launch(Dispatchers.IO) {
        showWaitForCompanionAppInstall(phoneDevice, wearDevice, launchPlayStore = false)
      }
    },
  )

  private suspend fun showUiInstallCompanionApp(
    phoneDevice: IDevice, showLoadingIcon: Boolean = false, showSuccessIcon: Boolean = false,
    scanningLabel: String = "", scanningLink: String = "", scanningListener: HyperlinkListener? = null, wearDevice: IDevice
  ) = showUI(
    header = message("wear.assistant.device.connection.install.wear.os.title"),
    description = message("wear.assistant.device.connection.install.wear.os.subtitle", WEAR_DOCS_LINK),

    body = createScanningPanel(
      firstStepLabel = message("wear.assistant.device.connection.install.wear.os.firstStep"),
      buttonLabel = message("wear.assistant.device.connection.install.wear.os.button"),
      buttonListener = {
        runningJob?.cancel()
        runningJob = coroutineScope.launch(Dispatchers.IO) {
          showWaitForCompanionAppInstall(phoneDevice, wearDevice, launchPlayStore = true)
        }
      },
      showLoadingIcon = showLoadingIcon,
      showSuccessIcon = showSuccessIcon,
      scanningLabel = scanningLabel,
      scanningLink = scanningLink,
      scanningListener = scanningListener,
      additionalStepsLabel = message("wear.assistant.device.connection.install.wear.os.additionalSteps"),
    ),

    imagePath = PATH_PLAY_SCREEN,
  )

  private suspend fun showUiPairing(
    phoneWearPair: PhoneWearPair, phoneDevice: IDevice, wearDevice: IDevice, showLoadingIcon: Boolean = false,
    showSuccessIcon: Boolean = false, scanningLabel: String = "", scanningLink: String = "", scanningListener: HyperlinkListener? = null
  ) = showUI(
    header = message("wear.assistant.device.connection.complete.pairing.title"),
    description = message("wear.assistant.device.connection.complete.pairing.subtitle", WEAR_DOCS_LINK),

    body = createScanningPanel(
      firstStepLabel = message("wear.assistant.device.connection.complete.pairing.firstStep"),
      buttonLabel = message("wear.assistant.device.connection.open.companion.button"),
      buttonListener = {
        runningJob?.cancel()
        runningJob = coroutineScope.launch(Dispatchers.IO) {
          showWaitForPairingSetup(phoneWearPair, phoneDevice, wearDevice, launchCompanionApp = true)
        }
      },
      showLoadingIcon = showLoadingIcon,
      showSuccessIcon = showSuccessIcon,
      scanningLabel = scanningLabel,
      scanningLink = scanningLink,
      scanningListener = scanningListener,
      additionalStepsLabel = message("wear.assistant.device.connection.complete.pairing.additionalSteps"),
    ),

    imagePath = PATH_PAIR_SCREEN,
  )

  private suspend fun showUiPairingNonInteractive(phoneWearPair: PhoneWearPair, phoneDevice: IDevice, wearDevice: IDevice, scanningLabel: String = message(
    "wear.assistant.device.connection.pairing.auto.start"), buttonLabel: String = "", scanningLink: String = "", showLoadingIcon: Boolean = true) = showUI(
    header = message("wear.assistant.device.connection.pairing.auto.title"),
    body = createScanningPanel(
      firstStepLabel = message("wear.assistant.device.connection.pairing.auto.step"),
      buttonLabel = buttonLabel,
      buttonListener = {
        check(runningJob?.isActive != true) // This is a manual retry. No job should be running at this point.
        runningJob = coroutineScope.launch(Dispatchers.IO) {
          showPairing(phoneWearPair, phoneDevice, wearDevice)
        }
      },
      showLoadingIcon = showLoadingIcon,
      showSuccessIcon = false,
      scanningLabel = scanningLabel,
      scanningLink = scanningLink,
      scanningListener = {
        check(runningJob?.isActive != true) // This is a manual retry. No job should be running at this point.
        runningJob = coroutineScope.launch(Dispatchers.IO) {
          showUiPairingAppInstructions(phoneWearPair, phoneDevice, wearDevice)
        }
      },
      additionalStepsLabel = "",
    )
  )

  private suspend fun showUiPairingAppInstructions(wearPairing: PhoneWearPair, phoneDevice: IDevice, wearDevice: IDevice) = showUiPairing(
    wearPairing, phoneDevice = phoneDevice, wearDevice = wearDevice,
  )

  private suspend fun showUiPairingScanning(phoneWearPair: PhoneWearPair, phoneDevice: IDevice, wearDevice: IDevice,
                                            scanningLabel: String) = showUiPairing(
    phoneWearPair = phoneWearPair, phoneDevice = phoneDevice, wearDevice = wearDevice,
    showLoadingIcon = true,
    scanningLabel = scanningLabel,
  )

  private suspend fun showUiPairingSuccess(phoneName: String, watchName: String, tapAndFinishWarning: Boolean) {
    // Load svg image offline
    check(!EventQueue.isDispatchThread())
    val svgUrl = (StudioIcons.Common.SUCCESS as IconLoader.CachedImageIcon).url!!
    val svgImg = SVGLoader.load(svgUrl, svgUrl.openStream(), ScaleContext.create(mainPanel), 150.0, 150.0)
    val successLabel = message(if (tapAndFinishWarning) { "wear.assistant.device.connection.pairing.success.skipandfinish" }
                               else { "wear.assistant.device.connection.pairing.success.subtitle" }, phoneName, watchName)
    val svgLabel = JBLabel(successLabel).apply {
      horizontalAlignment = SwingConstants.CENTER
      horizontalTextPosition = JLabel.CENTER
      verticalTextPosition = JLabel.BOTTOM
      icon = ImageIcon(svgImg)
    }

    // Show ui on UI Thread
    withContext(uiThread(ModalityState.any())) {
      mainPanel.apply {
        removeAll()

        add(JBLabel(message("wear.assistant.device.connection.pairing.success.title"), UIUtil.ComponentStyle.LARGE).apply {
          font = JBFont.label().biggerOn(5.0f)
        }, gridConstraint(x = 0, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL))
        add(Box.createVerticalGlue(), gridConstraint(x = 0, y = RELATIVE, weighty = 1.0))
        add(svgLabel, gridConstraint(x = 0, y = RELATIVE, weightx = 1.0, fill = HORIZONTAL))
        add(Box.createVerticalGlue(), gridConstraint(x = 0, y = RELATIVE, weighty = 1.0))

        revalidate()
        repaint()
      }
    }

    WearPairingUsageTracker.log(WearPairingEvent.EventKind.SHOW_SUCCESSFUL_PAIRING)
  }

  private suspend fun showUiNeedsFactoryReset(wearDeviceName: String) {
    val wipeButton = JButton(message("wear.assistant.factory.reset.button"))
    val warningPanel = createWarningPanel(message("wear.assistant.factory.reset.subtitle", wearDeviceName)).apply {
      add(createVerticalStrut(8), gridConstraint(x = 1, y = RELATIVE))
      add(wipeButton, gridConstraint(x = 1, y = RELATIVE, anchor = LINE_START))
      border = empty(20, 0, 0, 0)
    }

    val actionListener: (ActionEvent) -> Unit = {
      warningPanel.remove(wipeButton)
      warningPanel.add(
        JLabel(message("wear.assistant.factory.reset.progress", wearDeviceName)).addBorder(empty(0, 0, 4, 0)),
        gridConstraint(x = 1, y = RELATIVE, anchor = LINE_START)
      )
      warningPanel.add(JProgressBar().apply {
        isIndeterminate = true
      }, gridConstraint(x = 1, y = RELATIVE, fill = HORIZONTAL))

      check(runningJob?.isActive != true) // This is a button callback. No job should be running at this point.
      dispose() // Stop listening for device connection lost
      runningJob = coroutineScope.launch(Dispatchers.IO) {
        try {
          showUI(header = message("wear.assistant.factory.reset.title"), body = warningPanel)

          val wearDeviceId = model.selectedWearDevice.valueOrNull?.deviceID ?: ""
          val avdManager = AvdManagerConnection.getDefaultAvdManagerConnection()
          avdManager.getAvds(false).firstOrNull { it.id == wearDeviceId }?.apply {
            WearPairingManager.getInstance().removeAllPairedDevices(wearDeviceId, restartWearGmsCore = false)
            avdManager.stopAvd(this)
            waitForCondition(10_000) { model.selectedWearDevice.valueOrNull?.isOnline() != true }
            avdManager.wipeUserData(this)
          }
        }
        finally {
          startStepFlow()
        }
      }
    }

    wipeButton.addActionListener(actionListener)
    showUI(header = message("wear.assistant.factory.reset.title"), body = warningPanel)
  }

  private suspend fun showUiPairingRetry(phoneWearPair: PhoneWearPair, phoneDevice: IDevice, wearDevice: IDevice) = showUiPairing(
    phoneWearPair = phoneWearPair, phoneDevice = phoneDevice, wearDevice = wearDevice,
    scanningLabel = message("wear.assistant.device.connection.pairing.not.detected"),
    scanningLink = message("wear.assistant.device.connection.check.again"),
    scanningListener = {
      check(runningJob?.isActive != true) // This is a manual retry. No job should be running at this point.
      runningJob = coroutineScope.launch(Dispatchers.IO) {
        showWaitForPairingSetup(phoneWearPair, phoneDevice, wearDevice, launchCompanionApp = false)
      }
    }
  )

  private fun prepareErrorListener() {
    deviceStateListener.listenAll(model.selectedPhoneDevice, model.selectedWearDevice).withAndFire {
      val errorDevice = model.selectedPhoneDevice.valueOrNull.takeIf { it?.isOnline() == false }
                        ?: model.selectedWearDevice.valueOrNull.takeIf { it?.isOnline() == false }
      if (errorDevice != null) {
        showDeviceError(
          header = currentUiHeader, description = currentUiDescription,
          errorMessage = message("wear.assistant.device.connection.error", errorDevice.displayName)
        )
      }
    }
  }

  private fun showDeviceError(header: String, description: String, errorMessage: String) {
    coroutineScope.launch(uiThread) {
      forceDispose()
      val body = createWarningPanel(errorMessage)
      body.add(
        JButton(message("wear.assistant.connection.alert.button.try.again")).apply { addActionListener { wizardAction.restart(project) } },
        gridConstraint(x = 1, y = RELATIVE, anchor = LINE_START)
      )
      showUI(header = header, description = description, body = body)
    }
  }

  private fun showGenericError(ex: Throwable) {
    LOG.warn("Error connecting devices", ex)
    showDeviceError(
      header = message("wear.assistant.connection.alert.cant.bridge.title"), description = " ",
      errorMessage = message("wear.assistant.connection.alert.cant.bridge.subtitle")
    )
  }

  private fun goToNextStep() {
    ApplicationManager.getApplication().invokeLater({
      // The "Next" button changes asynchronously. Create a temporary property that will change state at the same time.
      val doGoForward = BoolValueProperty()
      bindings.bind(doGoForward, canGoForward)
      deviceStateListener.listenAndFire(doGoForward) {
        if (canGoForward.get()) {
          dispose()
          ApplicationManager.getApplication().invokeLater({
            wizardFacade.goForward()
          }, ModalityState.any())
        }
      }

      canGoForward.set(true)
    }, ModalityState.any())
  }

  private fun showEmbeddedEmulator(device: IDevice) {
    // Show embedded emulator tab if needed
    project?.messageBus?.syncPublisher(DeviceHeadsUpListener.TOPIC)?.userInvolvementRequired(device.serialNumber, project)
  }
}

private fun createWarningPanel(errorMessage: String, icon: Icon = StudioIcons.Common.WARNING): JPanel = JPanel(GridBagLayout()).apply {
  add(JBLabel(IconUtil.scale(icon, null, 2f)).withBorder(empty(0, 0, 0, 8)), gridConstraint(x = 0, y = 0))
  add(HtmlLabel().apply {
    name = "errorMessage"
    HtmlLabel.setUpAsHtmlLabel(this)
    text = errorMessage
  }, gridConstraint(x = 1, y = 0, weightx = 1.0, fill = HORIZONTAL))
}

suspend fun <T> Future<T>.await(): T {
  // There is no good way to convert a Java Future to a suspendCoroutine
  if (this is CompletionStage<*>) {
    @Suppress("UNCHECKED_CAST")
    return this.await()
  }

  while (!isDone) {
    delay(100)
  }
  @Suppress("BlockingMethodInNonBlockingContext")
  return get() // If isDone() returned true, this call will not block
}

private suspend fun waitForCondition(timeMillis: Long, condition: suspend () -> Boolean): Boolean {
  val res = withTimeoutOrNull(timeMillis) {
    while (!condition()) {
      delay(1000)
    }
    true
  }
  return res == true
}

private suspend fun checkWearMayNeedFactoryReset(phoneDevice: IDevice, wearDevice: IDevice): Boolean {
  if (wearDevice.hasPairingFeature(PairingFeature.GET_PAIRING_STATUS)) {
    val (wearNodeId, wearPairingStatus) = wearDevice.getPairingStatus()
    if (wearNodeId != null) {
      // Only need factory reset if the watch thinks it's paired with another phone
      return wearPairingStatus.isNotEmpty() && !wearPairingStatus[0].nodeId.isNullOrEmpty() &&
             !wearPairingStatus[0].nodeId.equals(phoneDevice.loadNodeID(), ignoreCase = true)
    }
  }
  val phoneCloudID = phoneDevice.loadCloudNetworkID()
  val wearCloudID = wearDevice.loadCloudNetworkID()

  return wearCloudID.isNotEmpty() && phoneCloudID != wearCloudID
}