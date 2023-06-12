/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard

import com.android.repository.api.RemotePackage
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.avdmanager.HardwareAccelerationCheck.isChromeOSAndIsNotHWAccelerated
import com.android.tools.idea.sdk.wizard.LicenseAgreementModel
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.android.tools.idea.welcome.config.AndroidFirstRunPersistentData
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.install.ComponentInstaller
import com.android.tools.idea.welcome.install.InstallableComponent
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.ui.StudioWizardDialogBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo.isLinux
import com.intellij.openapi.wm.WelcomeScreen
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import org.jetbrains.android.util.AndroidBundle.message
import java.awt.Window
import java.awt.event.WindowEvent
import java.awt.event.WindowListener
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel

/**
 * Android Studio's implementation of a [WelcomeScreen]. Starts up a wizard  meant to run the first time someone starts up
 * Android Studio to ask them to pick from some initial, useful options. Once the wizard is complete, it will bring the  user to the
 * initial "Welcome Screen" UI (with a list of projects and options to start a new project, etc.)
 */
class StudioFirstRunWelcomeScreen(private val mode: FirstRunWizardMode) : WelcomeScreen {
  private lateinit var modelWizard: ModelWizard
  private var mainPanel: JComponent? = null

  private fun setupWizard() {
    val model = FirstRunModel(mode)

    // TODO(qumeric): Add more steps and check witch steps to add for each different FirstRunWizardMode
    modelWizard = ModelWizard.Builder().apply {
      addStep(FirstRunWelcomeStep(model))
      if (model.installationType.get() != FirstRunModel.InstallationType.CUSTOM) {
        addStep(InstallationTypeWizardStep(model))
      }
      addStep(SelectThemeStep())
      if (mode == FirstRunWizardMode.MISSING_SDK) {
        addStep(MissingSdkAlertStep())
      }
      addStep(SdkComponentsStep(model))
      if (mode != FirstRunWizardMode.INSTALL_HANDOFF) {
        val supplier = Supplier<Collection<RemotePackage>?> {
          val components: Iterable<InstallableComponent> = model.componentTree.childrenToInstall
          try {
            ComponentInstaller(model.localHandler).getPackagesToInstall(components)
          }
          catch (e: SdkQuickfixUtils.PackageResolutionException) {
            logger<StudioFirstRunWelcomeScreen>().warn(e)
            null
          }
        }
        model.componentTree.steps.forEach { addStep(it) }
        addStep(InstallSummaryStep(model, supplier))
      }

      if (isLinux && !isChromeOSAndIsNotHWAccelerated() && mode == FirstRunWizardMode.NEW_INSTALL) {
        addStep(LinuxHaxmInfoStep())
      }

      if (mode != FirstRunWizardMode.INSTALL_HANDOFF) {
        addStep(LicenseAgreementStep(LicenseAgreementModel(model.sdkLocation.toPath()), listOf()))
      }

      // TODO: addStep(ProgressStep(model))
    }.build()


    // Note: We create a ModelWizardDialog, but we are only interested in its Content Panel
    // This is a bit of a hack, but it's the simplest way to reuse logic from ModelWizardDialog
    // (which inherits from IntelliJ's DialogWrapper class, which we can't refactor here).
    val modelWizardDialog = StudioWizardDialogBuilder(modelWizard, "").build()
    mainPanel = modelWizardDialog.contentPanel

    // Replace Content Panel with dummy version, as we are going to return its original value to the welcome frame
    modelWizardDialog.peer.setContentPane(JPanel())

    Disposer.register(this, modelWizardDialog.disposable)
    Disposer.register(this, modelWizard)
  }

  override fun getWelcomePanel(): JComponent {
    // TODO(qumeric): I am not sure at which point getWelcomePanel runs.
    //  Maybe it is worth to run setupWizard earlier and wait here for finish.
    if (mainPanel == null) {
      ApplicationManager.getApplication().invokeAndWait {
        setupWizard()
      }
    }
    return mainPanel!!
  }

  override fun setupFrame(frame: JFrame) {
    // Intercept windowClosing event, to show the closing confirmation dialog
    val oldIdeaListeners = removeAllWindowListeners(frame)
    frame.run {
      title = if (IdeInfo.getInstance().isAndroidStudio)
        message("android.as.wizard.welcome.dialog.title")
      else
        message("android.ij.wizard.welcome.dialog.title")
      pack()
      setLocationRelativeTo(null)
      addWindowListener(DelegatingListener(oldIdeaListeners))
    }

    modelWizard.addResultListener(object : ModelWizard.WizardListener {
      override fun onWizardFinished(wizardResult: ModelWizard.WizardResult) {
        closeDialog(frame)
      }
    })

    modelWizard.setCancelInterceptor { shouldPreventWizardCancel(frame) }
  }

  override fun dispose() {}

  private fun closeDialog(frame: Window) {
    frame.isVisible = false
    frame.dispose()
    WelcomeFrame.showNow()
  }

  private fun shouldPreventWizardCancel(frame: Window) = when (ConfirmFirstRunWizardCloseDialog.show()) {
    ConfirmFirstRunWizardCloseDialog.Result.Skip -> {
      AndroidFirstRunPersistentData.getInstance().markSdkUpToDate(mode.installerTimestamp)
      closeDialog(frame)
      false
    }
    ConfirmFirstRunWizardCloseDialog.Result.Rerun -> {
      closeDialog(frame)
      false
    }
    ConfirmFirstRunWizardCloseDialog.Result.DoNotClose -> {
      true
    }
    else -> throw RuntimeException("Invalid Close result") // Unknown option
  }

  private fun removeAllWindowListeners(frame: Window): Array<WindowListener> {
    frame.windowListeners.forEach {
      frame.removeWindowListener(it)
    }
    return frame.windowListeners
  }

  /**
   * This code is needed to avoid breaking IntelliJ native event processing.
   */
  private inner class DelegatingListener(private val myIdeaListeners: Array<WindowListener>) : WindowListener {
    override fun windowOpened(e: WindowEvent) {
      for (listener in myIdeaListeners) {
        listener.windowOpened(e)
      }
    }

    override fun windowClosed(e: WindowEvent) {
      for (listener in myIdeaListeners) {
        listener.windowClosed(e)
      }
    }

    override fun windowIconified(e: WindowEvent) {
      for (listener in myIdeaListeners) {
        listener.windowIconified(e)
      }
    }

    override fun windowClosing(e: WindowEvent) {
      // Don't let listener get this event, as it will shut down Android Studio completely. Instead, just delegate to the model wizard.
      modelWizard.cancel()
    }

    override fun windowDeiconified(e: WindowEvent) {
      for (listener in myIdeaListeners) {
        listener.windowDeiconified(e)
      }
    }

    override fun windowActivated(e: WindowEvent) {
      for (listener in myIdeaListeners) {
        listener.windowActivated(e)
      }
    }

    override fun windowDeactivated(e: WindowEvent) {
      for (listener in myIdeaListeners) {
        listener.windowDeactivated(e)
      }
    }
  }
}
