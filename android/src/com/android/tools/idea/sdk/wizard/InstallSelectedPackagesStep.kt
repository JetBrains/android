/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.sdk.wizard

import com.android.repository.api.InstallerFactory
import com.android.repository.api.LocalPackage
import com.android.repository.api.ProgressIndicator
import com.android.repository.api.UpdatablePackage
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.tools.adtui.validation.Validator
import com.android.tools.adtui.validation.ValidatorPanel
import com.android.tools.adtui.validation.validators.FalseValidator
import com.android.tools.adtui.validation.validators.TrueValidator
import com.android.tools.idea.observable.InvalidationListener
import com.android.tools.idea.observable.ListenerManager
import com.android.tools.idea.observable.core.BoolProperty
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.observable.core.ObservableBool
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.progress.ThrottledProgressWrapper
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.SdkInstallListener
import com.android.tools.idea.sdk.install.StudioSdkInstallerUtil
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.model.ModelWizardStep
import com.android.tools.idea.wizard.ui.StudioWizardLayout
import com.android.tools.idea.wizard.ui.deprecated.StudioWizardStepPanel
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.uiDesigner.core.GridConstraints
import com.intellij.uiDesigner.core.GridLayoutManager
import com.intellij.util.ModalityUiUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Font
import java.awt.Insets
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JTextPane
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.text.BadLocationException
import javax.swing.text.MutableAttributeSet
import javax.swing.text.StyleConstants
import org.jetbrains.android.util.AndroidBundle

/**
 * [ModelWizardStep] responsible for installing all selected packages before allowing the user to proceed. This class extends [WithoutModel]
 * since this step only acts as a middleman between the user accepting the packages and an InstallTask installing the packages in the
 * background. No model is needed since no data is recorded.
 */
class InstallSelectedPackagesStep(
  private val installRequests: Collection<UpdatablePackage> = emptyList(),
  private val uninstallRequests: Collection<LocalPackage> = emptyList(),
  private val sdkHandlerSupplier: () -> AndroidSdkHandler = { AndroidSdks.getInstance().tryToChooseSdkHandler() },
  private val backgroundable: Boolean = false,
  borderInsets: Insets = StudioWizardLayout.DEFAULT_BORDER_INSETS,
  private val factory: (AndroidSdkHandler) -> InstallerFactory = StudioSdkInstallerUtil::createInstallerFactory,
  private val throttleProgress: Boolean = false,
) : ModelWizardStep.WithoutModel(AndroidBundle.message("android.sdk.manager.installer.panel.title")) {
  private val installFailed: BoolProperty = BoolValueProperty()
  private val installationFinished: BoolProperty = BoolValueProperty()
  private val listeners = ListenerManager()

  private val labelSdkPath = JBLabel("<placeholder path>")
  private val progressOverallLabel = JBLabel()
  private val sdkManagerOutput = JTextPane().apply { isEditable = false }
  private val progressBar = JProgressBar()
  private val progressDetailLabel =
    JBLabel().apply {
      background = Color(0xcccccc)
      horizontalTextPosition = SwingConstants.LEADING
      verticalAlignment = SwingConstants.TOP
    }

  private val contentPanel = createContentPanel()
  private val validatorPanel = ValidatorPanel(this, contentPanel)
  private val studioPanel =
    StudioWizardStepPanel(
        validatorPanel,
        AndroidBundle.message("android.sdk.manager.installer.panel.description"),
      )
      .apply {
        border = JBUI.Borders.empty(borderInsets)
      }

  private var logger: ProgressIndicator? = null
  private val backgroundAction = BackgroundAction()
  private var outputStyle: MutableAttributeSet? = null

  val isBackgrounded: Boolean
    get() = backgroundAction.isBackgrounded

  constructor(
    installRequests: Collection<UpdatablePackage>,
    uninstallRequests: Collection<LocalPackage>,
    sdkHandlerSupplier: () -> AndroidSdkHandler,
    backgroundable: Boolean,
    borderInsets: Insets,
  ) : this(
    installRequests = installRequests,
    uninstallRequests = uninstallRequests,
    sdkHandlerSupplier = sdkHandlerSupplier,
    backgroundable = backgroundable,
    borderInsets = borderInsets,
    factory = StudioSdkInstallerUtil::createInstallerFactory,
    throttleProgress = false,
  )

  @VisibleForTesting
  constructor(
    installRequests: Collection<UpdatablePackage> = emptyList(),
    uninstallRequests: Collection<LocalPackage> = emptyList(),
    sdkHandler: AndroidSdkHandler,
    backgroundable: Boolean = false,
    borderInsets: Insets = JBUI.emptyInsets(),
    factory: InstallerFactory? = null,
    throttleProgress: Boolean = false,
  ) : this(
    installRequests = installRequests,
    uninstallRequests = uninstallRequests,
    sdkHandlerSupplier = { sdkHandler },
    backgroundable = backgroundable,
    borderInsets = borderInsets,
    factory =
      if (factory != null) {
        { factory }
      } else StudioSdkInstallerUtil::createInstallerFactory,
    throttleProgress = throttleProgress,
  )

  public override fun getExtraAction(): Action? = if (backgroundable) backgroundAction else null

  override fun onWizardStarting(wizard: ModelWizard.Facade) {
    // This will show a warning to the user once installation starts and will disable the next/finish button until installation finishes
    val finishedText = AndroidBundle.message("android.sdk.manager.installer.install.finished")
    validatorPanel.registerValidator(installationFinished, TrueValidator(Validator.Severity.INFO, finishedText))

    val installError = AndroidBundle.message("android.sdk.manager.installer.install.error")
    validatorPanel.registerValidator(installFailed, FalseValidator(installError))

    backgroundAction.wizard = wizard

    // Note: Calling updateNavigationProperties while myInstallationFinished is updated causes ConcurrentModificationException
    listeners.listen(
      installationFinished,
      InvalidationListener { ApplicationManager.getApplication().invokeLater { wizard.updateNavigationProperties() } },
    )
  }

  override fun onEntering() {
    sdkManagerOutput.text = ""
    sdkManagerOutput.font = JBFont.create(Font("Monospaced", Font.PLAIN, 13))
    outputStyle = sdkManagerOutput.addStyle(null, null)

    val sdkHandler = sdkHandlerSupplier()
    val repoManager = sdkHandler.getRepoManagerAndLoadSynchronously(StudioLoggerProgressIndicator(javaClass))
    val path = repoManager.localPath
    labelSdkPath.text = path.toString()

    installationFinished.set(false)
    startSdkInstall(sdkHandler)
  }

  override fun shouldShow(): Boolean = installRequests.isNotEmpty() || uninstallRequests.isNotEmpty()

  override fun canGoBack(): Boolean = false

  @VisibleForTesting public override fun canGoForward(): ObservableBool = installationFinished

  override fun getComponent(): JComponent = studioPanel

  override fun dispose() {
    listeners.releaseAll()
    synchronized(LOGGER_LOCK) {
      // If we're backgrounded, don't cancel when the window closes; allow the operation to continue.
      if (!backgroundAction.isBackgrounded) {
        logger?.cancel()
      }
    }
  }

  private fun startSdkInstall(sdkHandler: AndroidSdkHandler) {
    val customLogger = CustomLogger()
    val activeLogger =
      synchronized(LOGGER_LOCK) {
        val log = if (throttleProgress) ThrottledProgressWrapper(customLogger) else customLogger
        logger = log
        log
      }

    val task =
      InstallTask(
        installerFactory = factory(sdkHandler),
        sdkHandler = sdkHandler,
        logger = activeLogger,
        installRequests = installRequests,
        uninstallRequests = uninstallRequests,
        prepareCompleteCallback = { backgroundAction.isEnabled = false },
        completeCallback = { failures ->
          ModalityUiUtil.invokeLaterIfNeeded(ModalityState.any()) {
            progressBar.value = 100
            progressOverallLabel.text = ""

            for (project in ProjectManager.getInstance().openProjects) {
              if (!project.isDisposed) {
                project.messageBus.syncPublisher(SdkInstallListener.TOPIC).installCompleted(installRequests, uninstallRequests)
              }
            }

            if (failures.isNotEmpty()) {
              installFailed.set(true)
              progressBar.isEnabled = false
            } else {
              progressDetailLabel.text = "Done"
            }
            installationFinished.set(true)
          }
        },
      )
    backgroundAction.task = task

    task.runAsync()
  }

  private fun createContentPanel(): JPanel {
    val panel = JPanel(GridLayoutManager(5, 2, Insets(0, 0, 0, 0), -1, -1))
    panel.add(
      JBLabel("SDK Path:"),
      GridConstraints(
        0,
        0,
        1,
        1,
        GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_FIXED,
        GridConstraints.SIZEPOLICY_FIXED,
        null,
        null,
        null,
        0,
        false,
      ),
    )
    panel.add(
      labelSdkPath,
      GridConstraints(
        0,
        1,
        1,
        1,
        GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_WANT_GROW,
        GridConstraints.SIZEPOLICY_FIXED,
        null,
        null,
        null,
        0,
        false,
      ),
    )
    val scrollPane =
      JBScrollPane(sdkManagerOutput).apply {
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
      }
    panel.add(
      scrollPane,
      GridConstraints(
        1,
        0,
        1,
        2,
        GridConstraints.ANCHOR_CENTER,
        GridConstraints.FILL_BOTH,
        GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_WANT_GROW,
        null,
        null,
        null,
        0,
        false,
      ),
    )
    panel.add(
      progressOverallLabel,
      GridConstraints(
        2,
        0,
        1,
        2,
        GridConstraints.ANCHOR_CENTER,
        GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_WANT_GROW,
        GridConstraints.SIZEPOLICY_FIXED,
        null,
        null,
        null,
        0,
        false,
      ),
    )
    panel.add(
      progressBar,
      GridConstraints(
        3,
        0,
        1,
        2,
        GridConstraints.ANCHOR_CENTER,
        GridConstraints.FILL_HORIZONTAL,
        GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_WANT_GROW,
        GridConstraints.SIZEPOLICY_FIXED,
        null,
        null,
        null,
        0,
        false,
      ),
    )
    panel.add(
      progressDetailLabel,
      GridConstraints(
        4,
        0,
        1,
        2,
        GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_CAN_SHRINK or GridConstraints.SIZEPOLICY_CAN_GROW,
        GridConstraints.SIZEPOLICY_CAN_GROW,
        null,
        null,
        null,
        0,
        false,
      ),
    )
    return panel
  }

  private inner class CustomLogger : ProgressIndicator {
    @Volatile private var isCancelled = false
    // Maintain separately since JProgressBar has low resolution
    private var fraction = 0.0

    override fun setText(s: String?) {
      UIUtil.invokeLaterIfNeeded { progressOverallLabel.text = s }
    }

    override fun isCanceled(): Boolean = isCancelled

    override fun cancel() {
      isCancelled = true
    }

    override fun setCancellable(cancellable: Boolean) {
      // Nothing
    }

    override fun isCancellable(): Boolean = true

    override fun setIndeterminate(indeterminate: Boolean) {
      UIUtil.invokeLaterIfNeeded { progressBar.isIndeterminate = indeterminate }
    }

    override fun isIndeterminate(): Boolean = progressBar.isIndeterminate

    override fun setFraction(v: Double) {
      fraction = v
      UIUtil.invokeLaterIfNeeded {
        progressBar.isIndeterminate = false
        progressBar.value = (v * (progressBar.maximum - progressBar.minimum)).toInt()
      }
    }

    override fun getFraction(): Double = fraction

    override fun setSecondaryText(label: String?) {
      UIUtil.invokeLaterIfNeeded { progressDetailLabel.text = label }
    }

    override fun logWarning(s: String) {
      appendText(s, JBColor.RED)
      LOG.warn(s)
    }

    override fun logWarning(s: String, e: Throwable?) {
      appendText(s, JBColor.RED)
      LOG.warn(s, e)
    }

    override fun logError(s: String) {
      appendText(s, JBColor.RED)
      LOG.error(s)
    }

    override fun logError(s: String, e: Throwable?) {
      appendText(s, JBColor.RED)
      LOG.error(s, e)
    }

    override fun logInfo(s: String) {
      appendText(s, JBColor.foreground())
      LOG.info(s)
    }

    override fun logVerbose(s: String) {}

    private fun appendText(text: String, color: Color) {
      UIUtil.invokeLaterIfNeeded {
        val current = sdkManagerOutput.text.orEmpty()
        // Want to chew the first "extra" newline since in different places
        // the messages either end with an explicit "\n" or not, but the intention is always
        // to have one trailing newline.
        //
        // The calling code can still supply more than one newline,
        // and it will result in empty lines, since 2+ explicitly provided newlines
        // probably mean that this was the intention
        val offset = if (current.endsWith("\n")) 1 else 0

        val document = sdkManagerOutput.styledDocument
        val style = outputStyle ?: return@invokeLaterIfNeeded
        StyleConstants.setForeground(style, color)
        try {
          document.insertString(document.length - offset, text, style)
          document.insertString(document.length, "\n", style)
        } catch (exception: BadLocationException) {
          LOG.warn(exception)
        }
      }
    }
  }

  /**
   * Action shown as an extra action in the wizard (see [ModelWizardStep.getExtraAction]. Cancels the wizard, but lets our install task
   * continue running.
   */
  private class BackgroundAction : AbstractAction("Background") {
    var isBackgrounded: Boolean = false
      private set

    var wizard: ModelWizard.Facade? = null
    var task: InstallTask? = null

    override fun actionPerformed(e: ActionEvent?) {
      isBackgrounded = true
      task?.foregroundIndicatorClosed()
      wizard?.cancel()
    }
  }

  companion object {
    private val LOGGER_LOCK = Any()
    private val LOG = Logger.getInstance(InstallSelectedPackagesStep::class.java)
  }
}
