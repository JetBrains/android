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
package com.android.tools.idea.sdk.wizard;

import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.repository.api.InstallerFactory;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.api.UpdatablePackage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.adtui.validation.validators.FalseValidator;
import com.android.tools.adtui.validation.validators.TrueValidator;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.progress.ThrottledProgressWrapper;
import com.android.tools.idea.sdk.SdkInstallListener;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.sdk.install.StudioSdkInstallerUtil;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.ui.deprecated.StudioWizardStepPanel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ModalityUiUtil;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.StyleConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link ModelWizardStep} responsible for installing all selected packages before allowing the user the proceed.
 * This class extends {@link WithoutModel} since this step only acts as a middleman between the user
 * accepting the packages and an InstallTask installing the packages in the background. No model is needed since no data
 * is recorded.
 */
public class InstallSelectedPackagesStep extends ModelWizardStep.WithoutModel {
  private final BoolProperty myInstallFailed = new BoolValueProperty();
  private final BoolProperty myInstallationFinished = new BoolValueProperty();
  private final ListenerManager myListeners = new ListenerManager();

  private final StudioWizardStepPanel myStudioPanel;
  private final ValidatorPanel myValidatorPanel;
  /** We defer retrieval of the SDK handler in case a previous wizard step installed the SDK. */
  private final Supplier<AndroidSdkHandler> mySdkHandlerSupplier;

  private JPanel myContentPanel;
  private JBLabel myLabelSdkPath;
  private JBLabel myProgressOverallLabel;
  private JTextPane mySdkManagerOutput;
  private JProgressBar myProgressBar;
  private JBLabel myProgressDetailLabel;

  private List<UpdatablePackage> myInstallRequests;
  private Collection<LocalPackage> myUninstallRequests;

  private com.android.repository.api.ProgressIndicator myLogger;
  private static final Object LOGGER_LOCK = new Object();
  private BackgroundAction myBackgroundAction = new BackgroundAction();
  private final boolean myBackgroundable;
  private InstallerFactoryFactory myFactory;
  private boolean myThrottleProgress;

  private MutableAttributeSet myOutputStyle;

  private interface InstallerFactoryFactory {
    InstallerFactory createInstallerFactory(@NotNull AndroidSdkHandler sdkHandler);
  }

  public InstallSelectedPackagesStep(@NotNull List<UpdatablePackage> installRequests,
                                     @NotNull Collection<LocalPackage> uninstallRequests,
                                     @NotNull Supplier<AndroidSdkHandler> sdkHandlerSupplier,
                                     boolean backgroundable,
                                     @NotNull Insets borderInsets) {
    this(installRequests, uninstallRequests, sdkHandlerSupplier, backgroundable, borderInsets,
         StudioSdkInstallerUtil::createInstallerFactory, false);
  }

  @VisibleForTesting
  public InstallSelectedPackagesStep(@NotNull List<UpdatablePackage> installRequests,
                                     @NotNull Collection<LocalPackage> uninstallRequests,
                                     @NotNull AndroidSdkHandler sdkHandler,
                                     boolean backgroundable,
                                     @NotNull Insets borderInsets,
                                     @NotNull InstallerFactory factory,
                                     boolean throttleProgress) {
    this(installRequests, uninstallRequests, () -> sdkHandler, backgroundable, borderInsets, (unused) -> factory, throttleProgress);
  }

  @VisibleForTesting
  private InstallSelectedPackagesStep(@NotNull List<UpdatablePackage> installRequests,
                                     @NotNull Collection<LocalPackage> uninstallRequests,
                                     @NotNull Supplier<AndroidSdkHandler> sdkHandlerSupplier,
                                     boolean backgroundable,
                                      @NotNull Insets borderInsets,
                                     @NotNull InstallerFactoryFactory factory,
                                     boolean throttleProgress) {
    super(message("android.sdk.manager.installer.panel.title"));
    setupUI();
    myInstallRequests = installRequests;
    myUninstallRequests = uninstallRequests;
    myValidatorPanel = new ValidatorPanel(this, myContentPanel);
    myStudioPanel = new StudioWizardStepPanel(myValidatorPanel, message("android.sdk.manager.installer.panel.description"));
    myStudioPanel.setBorder(JBUI.Borders.empty(borderInsets));
    myBackgroundable = backgroundable;
    mySdkHandlerSupplier = sdkHandlerSupplier;
    myFactory = factory;
    myThrottleProgress = throttleProgress;
  }

  @Override
  public Action getExtraAction() {
    return myBackgroundable ? myBackgroundAction : null;
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    // This will show a warning to the user once installation starts and will disable the next/finish button until installation finishes
    String finishedText = message("android.sdk.manager.installer.install.finished");
    myValidatorPanel.registerValidator(myInstallationFinished, new TrueValidator(Validator.Severity.INFO, finishedText));

    String installError = message("android.sdk.manager.installer.install.error");
    myValidatorPanel.registerValidator(myInstallFailed, new FalseValidator(installError));

    myBackgroundAction.setWizard(wizard);

    // Note: Calling updateNavigationProperties while myInstallationFinished is updated causes ConcurrentModificationException
    myListeners.listen(myInstallationFinished, () -> ApplicationManager.getApplication().invokeLater(wizard::updateNavigationProperties));
  }

  @Override
  protected void onEntering() {
    mySdkManagerOutput.setText("");
    mySdkManagerOutput.setFont(JBFont.create(new Font("Monospaced", Font.PLAIN, 13)));
    myOutputStyle = mySdkManagerOutput.addStyle(null, null);

    AndroidSdkHandler sdkHandler = mySdkHandlerSupplier.get();
    RepoManager repoManager = sdkHandler.getRepoManagerAndLoadSynchronously(new StudioLoggerProgressIndicator(getClass()));
    Path path = repoManager.getLocalPath();
    myLabelSdkPath.setText(path.toString());

    myInstallationFinished.set(false);
    startSdkInstall(sdkHandler);
  }

  @Override
  protected boolean shouldShow() {
    return !myInstallRequests.isEmpty() || !myUninstallRequests.isEmpty();
  }

  @Override
  protected boolean canGoBack() {
    // No turning back! Once we've started installing, it's too late to stop and change options.
    return false;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myInstallationFinished;
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myStudioPanel;
  }

  @Override
  public void dispose() {
    myListeners.releaseAll();
    synchronized (LOGGER_LOCK) {
      // If we're backgrounded, don't cancel when the window closes; allow the operation to continue.
      if (myLogger != null && !myBackgroundAction.isBackgrounded()) {
        myLogger.cancel();
      }
    }
  }

  private void startSdkInstall(AndroidSdkHandler sdkHandler) {
    CustomLogger customLogger = new CustomLogger();
    synchronized (LOGGER_LOCK) {
      myLogger = myThrottleProgress ? new ThrottledProgressWrapper(customLogger) : customLogger;
    }

    Function<List<RepoPackage>, Void> completeCallback = failures -> {
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.any(), () -> {
        myProgressBar.setValue(100);
        myProgressOverallLabel.setText("");

        @NotNull Project[] projects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : projects) {
          if (!project.isDisposed()) {
            project.getMessageBus().syncPublisher(SdkInstallListener.TOPIC)
              .installCompleted(myInstallRequests, myUninstallRequests);
          }
        }

        if (!failures.isEmpty()) {
          myInstallFailed.set(true);
          myProgressBar.setEnabled(false);
        }
        else {
          myProgressDetailLabel.setText("Done");
          myInstallRequests.clear();
          myUninstallRequests.clear();
        }
        myInstallationFinished.set(true);
      });
      return null;
    };

    InstallTask task = new InstallTask(myFactory.createInstallerFactory(sdkHandler), sdkHandler, StudioSettingsController.getInstance(), myLogger);
    task.setInstallRequests(myInstallRequests);
    task.setUninstallRequests(myUninstallRequests);
    task.setCompleteCallback(completeCallback);
    task.setPrepareCompleteCallback(() -> myBackgroundAction.setEnabled(false));
    myBackgroundAction.setTask(task);

    ProgressIndicator indicator;
    boolean hasOpenProjects = ProjectManager.getInstance().getOpenProjects().length > 0;
    if (hasOpenProjects) {
      indicator = new BackgroundableProcessIndicator(task);
    }
    else {
      // If we don't have any open projects runProcessWithProgressAsynchronously will show a modal popup no matter what.
      // Instead use an empty progress indicator to suppress that.
      indicator = new EmptyProgressIndicator();
    }
    customLogger.setIndicator(indicator);
    indicator.setIndeterminate(false);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator);
  }

  private void setupUI() {
    myContentPanel = new JPanel();
    myContentPanel.setLayout(new GridLayoutManager(5, 2, new Insets(0, 0, 0, 0), -1, -1));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("SDK Path:");
    myContentPanel.add(jBLabel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                     0, false));
    myLabelSdkPath = new JBLabel();
    myLabelSdkPath.setText("<placeholder path>");
    myContentPanel.add(myLabelSdkPath, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                           GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                           null, null, 0, false));
    final JBScrollPane jBScrollPane1 = new JBScrollPane();
    jBScrollPane1.setVerticalScrollBarPolicy(22);
    myContentPanel.add(jBScrollPane1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                          null, null, null, 0, false));
    mySdkManagerOutput = new JTextPane();
    mySdkManagerOutput.setEditable(false);
    jBScrollPane1.setViewportView(mySdkManagerOutput);
    myProgressOverallLabel = new JBLabel();
    myContentPanel.add(myProgressOverallLabel,
                       new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                           GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                           false));
    myProgressBar = new JProgressBar();
    myContentPanel.add(myProgressBar, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myProgressDetailLabel = new JBLabel();
    myProgressDetailLabel.setBackground(new Color(-3355444));
    myProgressDetailLabel.setHorizontalTextPosition(10);
    myProgressDetailLabel.setVerticalAlignment(1);
    myContentPanel.add(myProgressDetailLabel, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                  null, null, null, 0, false));
  }

  private final class CustomLogger implements com.android.repository.api.ProgressIndicator {

    private ProgressIndicator myIndicator;
    private boolean myCancelled;
    private Logger myLogger = Logger.getInstance(getClass());
    // Maintain separately since JProgressBar has low resolution
    private double myFraction = 0;

    @Override
    public void setText(@Nullable final String s) {
      UIUtil.invokeLaterIfNeeded(() -> myProgressOverallLabel.setText(s));
      if (myIndicator != null) {
        myIndicator.setText(s);
      }
    }

    @Override
    public boolean isCanceled() {
      if (myIndicator != null) {
        myCancelled = myCancelled || myIndicator.isCanceled();
      }
      return myCancelled;
    }

    @Override
    public void cancel() {
      myCancelled = true;
      if (myIndicator != null) {
        myIndicator.cancel();
      }
    }

    @Override
    public void setCancellable(boolean cancellable) {
      // Nothing
    }

    @Override
    public boolean isCancellable() {
      return true;
    }

    @Override
    public void setIndeterminate(final boolean indeterminate) {
      UIUtil.invokeLaterIfNeeded(() -> myProgressBar.setIndeterminate(indeterminate));
      if (myIndicator != null) {
        myIndicator.setIndeterminate(indeterminate);
      }
    }

    @Override
    public boolean isIndeterminate() {
      return myProgressBar.isIndeterminate();
    }

    @Override
    public void setFraction(final double v) {
      myFraction = v;
      UIUtil.invokeLaterIfNeeded(() -> {
        myProgressBar.setIndeterminate(false);
        myProgressBar.setValue((int)(v * (double)(myProgressBar.getMaximum() - myProgressBar.getMinimum())));
      });
      if (myIndicator != null) {
        myIndicator.setFraction(v);
      }
    }

    @Override
    public double getFraction() {
      return myFraction;
    }

    @Override
    public void setSecondaryText(@Nullable String label) {
      UIUtil.invokeLaterIfNeeded(() -> myProgressDetailLabel.setText(label));
      if (myIndicator != null) {
        myIndicator.setText2(label);
      }
    }

    @Override
    public void logWarning(@NotNull String s) {
      appendText(s, JBColor.RED);
      myLogger.warn(s);
    }

    @Override
    public void logWarning(@NotNull String s, @Nullable Throwable e) {
      appendText(s, JBColor.RED);
      myLogger.warn(s, e);
    }

    @Override
    public void logError(@NotNull String s) {
      appendText(s, JBColor.RED);
      myLogger.error(s);
    }

    @Override
    public void logError(@NotNull String s, @Nullable Throwable e) {
      appendText(s, JBColor.RED);
      myLogger.error(s, e);
    }

    @Override
    public void logInfo(@NotNull String s) {
      appendText(s, JBColor.foreground());
      myLogger.info(s);
    }

    @Override
    public void logVerbose(@NotNull String s) {
    }

    private void appendText(@NotNull final String text, @NotNull Color color) {
      UIUtil.invokeLaterIfNeeded(() -> {
        String current = mySdkManagerOutput.getText();
        int offset = 0;
        if (current == null) {
          mySdkManagerOutput.setText("");
        }
        else if (current.endsWith("\n")) {
          // Want to chew the first "extra" newline since in different places
          // the messages either end with an explicit "\n" or not, but the intention is always
          // to have one trailing newline.
          //
          // The calling code can still supply more than one newline,
          // and it will result in empty lines, since 2+ explicitly provided newlines
          // probably mean that this was the intention
          offset = 1;
        }

        Document document = mySdkManagerOutput.getStyledDocument();
        StyleConstants.setForeground(myOutputStyle, color);
        try {
          document.insertString(document.getLength() - offset, text, myOutputStyle);
          document.insertString(document.getLength(), "\n", myOutputStyle);
        }
        catch (BadLocationException exception) {
          myLogger.warn(exception);
        }
      });
    }

    public void setIndicator(ProgressIndicator indicator) {
      myIndicator = indicator;
    }
  }

  /**
   * Action shown as an extra action in the wizard (see {@link ModelWizardStep#getExtraAction()}.
   * Cancels the wizard, but lets our install task continue running.
   */
  private static class BackgroundAction extends AbstractAction {
    private boolean myIsBackgrounded = false;
    private ModelWizard.Facade myWizard;
    private InstallTask myTask;

    public BackgroundAction() {
      super("Background");
    }

    public void setTask(InstallTask task) {
      myTask = task;
    }

    public void setWizard(@NotNull ModelWizard.Facade wizard) {
      myWizard = wizard;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myIsBackgrounded = true;
      myTask.foregroundIndicatorClosed();
      myWizard.cancel();
    }

    public boolean isBackgrounded() {
      return myIsBackgrounded;
    }
  }
}
