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

import com.android.tools.idea.welcome.wizard.ConfigureInstallationModel.InstallationType.CUSTOM
import org.jetbrains.android.util.AndroidBundle.message

import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder
import com.android.tools.idea.welcome.config.AndroidFirstRunPersistentData
import com.android.tools.idea.welcome.config.FirstRunWizardMode
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults
import com.android.tools.idea.wizard.model.ModelWizard
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WelcomeScreen
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import java.awt.Window
import java.awt.event.WindowEvent
import java.awt.event.WindowListener
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel

/**
 * Android Studio's implementation of a [WelcomeScreen]. Starts up a wizard  meant to run the first time someone starts up
 * Android Studio to ask them to pick from some initial, useful options. Once the wizard is complete, it will bring the  user to the
 * initial "Welcome Screen" UI (with a list of projects and options to start a new project, etc.)
 */
class StudioFirstRunWelcomeScreen(private val mode: FirstRunWizardMode) : WelcomeScreen {
  private val modelWizard: ModelWizard
  private val mainPanel: JComponent

  init {
    val initialSdkLocation = FirstRunWizardDefaults.getInitialSdkLocation(mode)
    val sdkExists = if (initialSdkLocation.isDirectory) {
      val sdkHandler = AndroidSdkHandler.getInstance(initialSdkLocation)
      val progress = StudioLoggerProgressIndicator(javaClass)
      sdkHandler.getSdkManager(progress).packages.localPackages.isNotEmpty()
    } else {
      false
    }

    val model = ConfigureInstallationModel()
    // TODO(qumeric): Add more steps and check witch steps to add for each different FirstRunWizardMode
    modelWizard = ModelWizard.Builder().apply {
      addStep(FirstRunWelcomeStep(sdkExists))
      if (initialSdkLocation.path.isEmpty()) {
        // We don't have a default path specified, have to do custom install.
        model.installationType().set(CUSTOM)
      }
      else {
        addStep(InstallationTypeWizardStep(model))
      }
      addStep(SelectThemeStep())
      // TODO(qumeric): Add JdkSetupStep here
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

  override fun getWelcomePanel(): JComponent = mainPanel

  override fun setupFrame(frame: JFrame) {
    // Intercept windowClosing event, to show the closing confirmation dialog
    val oldIdeaListeners = removeAllWindowListeners(frame)
    frame.run {
      title = message("android.wizard.welcome.dialog.title")
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

  private fun shouldPreventWizardCancel(frame: Window) = when (ConfirmFirstRunWizardCloseDialog().open()) {
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
