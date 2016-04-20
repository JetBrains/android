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

import com.android.repository.api.*;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.sdk.StudioDownloader;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.android.tools.idea.sdk.install.StudioSdkInstallerUtil;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.progress.ThrottledProgressWrapper;
import com.android.tools.idea.ui.properties.core.BoolProperty;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.validation.Validator;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.android.tools.idea.ui.validation.validators.FalseValidator;
import com.android.tools.idea.ui.validation.validators.TrueValidator;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Map;

/**
 * {@link ModelWizardStep} responsible for installing all selected packages before allowing the user the proceed.
 * This class extends {@link WithoutModel} since this step only acts as a middleman between the user
 * accepting the packages and an InstallTask installing the packages in the background. No model is needed since no data
 * is recorded.
 */
public final class InstallSelectedPackagesStep extends ModelWizardStep.WithoutModel {
  private final BoolProperty myInstallFailed = new BoolValueProperty();
  private final BoolProperty myInstallationFinished = new BoolValueProperty();

  private final StudioWizardStepPanel myStudioPanel;
  private final ValidatorPanel myValidatorPanel;

  private JPanel myContentPanel;
  private JBLabel myLabelSdkPath;
  private JBLabel myProgressOverallLabel;
  private JTextArea mySdkManagerOutput;
  private JProgressBar myProgressBar;
  private JBLabel myProgressDetailLabel;

  private List<RemotePackage> myInstallRequests;
  // Ok to keep a reference, since the wizard is short-lived and modal.
  private final RepoManager myRepoManager;
  private final AndroidSdkHandler mySdkHandler;
  private com.android.repository.api.ProgressIndicator myLogger;
  private static final Object LOGGER_LOCK = new Object();
  private final BackgroundAction myBackgroundAction = new BackgroundAction();
  private final boolean myBackgroundable;

  public InstallSelectedPackagesStep(@NotNull List<RemotePackage> installRequests,
                                     @NotNull RepoManager mgr,
                                     @NotNull AndroidSdkHandler sdkHandler,
                                     boolean backgroundable) {
    super("Component Installer");
    myInstallRequests = installRequests;
    myRepoManager = mgr;
    mySdkHandler = sdkHandler;
    myValidatorPanel = new ValidatorPanel(this, myContentPanel);
    myStudioPanel = new StudioWizardStepPanel(myValidatorPanel, "Installing Requested Components");
    myBackgroundable = backgroundable;
  }

  @Override
  public Action getExtraAction() {
    return myBackgroundable ? myBackgroundAction : null;
  }

  /**
   * Look through the list of completed changes, and set a key if any new platforms
   * were installed.
   */
  private static void checkForUpgrades(@Nullable List<? extends RepoPackage> completedChanges) {
    if (completedChanges == null) {
      return;
    }
    int highestNewApiLevel = 0;
    for (RepoPackage updated : completedChanges) {
      TypeDetails details = updated.getTypeDetails();
      if (details instanceof DetailsTypes.PlatformDetailsType) {
        int api = ((DetailsTypes.PlatformDetailsType)details).getApiLevel();
        if (api > highestNewApiLevel) {
          highestNewApiLevel = api;
        }
      }
    }
    if (highestNewApiLevel > 0) {
      // TODO: Fix this code after we delete WizardConstants
      PropertiesComponent.getInstance().setValue(WizardConstants.NEWLY_INSTALLED_API_KEY.name, highestNewApiLevel, -1);
    }
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    // This will show a warning to the user once installation starts and will disable the next/finish button until installation finishes
    String finishedText = "Please wait until the installation finishes to continue";
    myValidatorPanel.registerValidator(myInstallationFinished, new TrueValidator(Validator.Severity.INFO, finishedText));

    String installError = "Install Failed. Please check your network connection and try again. " +
                          "You may continue with creating your project, but it will not compile correctly " +
                          "without the missing components.";
    myValidatorPanel.registerValidator(myInstallFailed, new FalseValidator(installError));
    myBackgroundAction.setWizard(wizard);
  }

  @Override
  protected void onEntering() {
    mySdkManagerOutput.setText("");
    myLabelSdkPath.setText(myRepoManager.getLocalPath().getPath());

    startSdkInstall();
  }

  @Override
  protected boolean shouldShow() {
    return myInstallRequests.size() > 0;
  }

  @Override
  public boolean canGoBack() {
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
    synchronized (LOGGER_LOCK) {
      // If we're backgrounded, don't cancel when the window closes; allow the operation to continue.
      if (myLogger != null && !myBackgroundAction.isBackgrounded()) {
        myLogger.cancel();
      }
    }
  }

  private void startSdkInstall() {
    CustomLogger customLogger = new CustomLogger();
    synchronized (LOGGER_LOCK) {
      myLogger = new ThrottledProgressWrapper(customLogger);
    }

    InstallTask task = new InstallTask(myInstallRequests, myLogger);
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
    myLogger.logInfo("To install:");
    for (RemotePackage p : myInstallRequests) {
      myLogger.logInfo(String.format("- %1$s (%2$s)", p.getDisplayName(), p.getPath()));
    }
    myLogger.logInfo("");
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator);
  }

  private class InstallTask extends Task.Backgroundable {
    @NotNull private final List<RemotePackage> myRequestedPackages;
    @NotNull private final com.android.repository.api.ProgressIndicator myProgress;

    @Override
    public void onCancel() {
      myProgress.cancel();
    }

    private InstallTask(@NotNull List<RemotePackage> requestedPackages, @NotNull
                        com.android.repository.api.ProgressIndicator progress) {
      super(null, "Installing Android SDK", true, PerformInBackgroundOption.ALWAYS_BACKGROUND);
      myRequestedPackages = requestedPackages;
      myProgress = progress;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      final List<RemotePackage> failures = Lists.newArrayList();
      Map<RemotePackage, Installer> preparedPackages = Maps.newLinkedHashMap();
      try {
        for (RemotePackage remote : myRequestedPackages) {
          boolean success = false;
          // If there's already an installer in progress for this package, reuse it.
          PackageOperation op = myRepoManager.getInProgressInstallOperation(remote);
          Installer installer;
          if (op == null || !(op instanceof Installer)) {
            installer = StudioSdkInstallerUtil.createInstallerFactory(remote, mySdkHandler)
              .createInstaller(remote, myRepoManager, mySdkHandler.getFileOp());
          }
          else {
            installer = (Installer)op;
          }
          myLogger.logInfo(String.format("Installing %1$s", remote.getDisplayName()));
          try {
            success = installer.prepareInstall(new StudioDownloader(indicator), myProgress);
          }
          catch (Exception e) {
            Logger.getInstance(getClass()).warn(e);
          }
          if (success) {
            preparedPackages.put(remote, installer);
            myLogger.logInfo(String.format("%1$s ready to install.", remote.getDisplayName()));
          }
          else {
            myLogger.logInfo(String.format("Failed to install %1$s!", remote.getDisplayName()));
            failures.add(remote);
          }
          myLogger.logInfo("");
        }
        // Disable the background action so the final part can't be backgrounded.
        myBackgroundAction.setEnabled(false);
        for (RemotePackage remote : preparedPackages.keySet()) {
          Installer installer = preparedPackages.get(remote);
          if (!myBackgroundAction.isBackgrounded()) {
            // If we're not backgrounded, go on to the final part immediately.
            myLogger.logInfo(String.format("Finishing installation of %1$s.", remote.getDisplayName()));
            installer.completeInstall(myProgress);
            myLogger.logInfo(String.format("Installation of %1$s complete.", remote.getDisplayName()));
          }
          else {
            // Otherwise show a notification that we're ready to complete.
            showPrepareCompleteNotification(remote);
            return;
          }
        }
      }
      finally {
        if (!failures.isEmpty()) {
          myLogger.logInfo("Failed packages:");
          for (RemotePackage p : failures) {
            myLogger.logInfo(String.format("- %1$s (%2$s)", p.getDisplayName(), p.getPath()));
          }
        }
        synchronized (LOGGER_LOCK) {
          myLogger = null;
        }
      }
      // Use a simple progress indicator here so we don't pick up the log messages from the reload.
      StudioLoggerProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
      myRepoManager.loadSynchronously(RepoManager.DEFAULT_EXPIRATION_PERIOD_MS, progress, null, StudioSettingsController.getInstance());

      UIUtil.invokeLaterIfNeeded(() -> {
        myProgressBar.setValue(100);
        myProgressOverallLabel.setText("");

        if (!failures.isEmpty()) {
          myInstallFailed.set(true);
          myProgressBar.setEnabled(false);
        }
        else {
          myProgressDetailLabel.setText("Done");
          checkForUpgrades(myRequestedPackages);
        }
        myInstallationFinished.set(true);

        VirtualFile sdkDir = LocalFileSystem.getInstance().findFileByIoFile(myRepoManager.getLocalPath());
        if (sdkDir != null) {
          sdkDir.refresh(true, true);
        }
      });
    }

    private void showPrepareCompleteNotification(@NotNull final RemotePackage remotePackage) {
      final NotificationListener notificationListener = new NotificationListener.Adapter() {
        @Override
        protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          if ("install".equals(event.getDescription())) {
            List<String> requests = Lists.newArrayList();
            for (RemotePackage p : myInstallRequests) {
              requests.add(p.getPath());
            }
            ModelWizardDialog dialogForPaths = SdkQuickfixUtils.createDialogForPaths((Component)null, requests);
            if (dialogForPaths != null) {
              dialogForPaths.show();
            }
          }
          notification.expire();
        }
      };
      final NotificationGroup group = new NotificationGroup("SDK Installer", NotificationDisplayType.STICKY_BALLOON, false);
      Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
      final Project[] openProjectsOrNull = openProjects.length == 0 ? new Project[] {null} : openProjects;
      ApplicationManager.getApplication().invokeLater(
        () -> {
          for (Project p : openProjectsOrNull) {
            group.createNotification(
              "SDK Install",
              String.format("Installation of '%1$s' is ready to continue<br/><a href=\"install\">Install Now</a>",
                            remotePackage.getDisplayName()),
              NotificationType.INFORMATION, notificationListener).notify(p);
          }
        },
        ModalityState.NON_MODAL,  // Don't show while we're in a modal context (e.g. sdk manager)
        o -> {
          // We don't show the bubble until we're out of a modal context. If the install has already completed, don't show it at all.
          PackageOperation installer = myRepoManager.getInProgressInstallOperation(remotePackage);
          return installer == null || installer.getInstallStatus() != PackageOperation.InstallStatus.PREPARED;
        });
    }
  }

  private final class CustomLogger implements com.android.repository.api.ProgressIndicator {

    private ProgressIndicator myIndicator;
    private boolean myCancelled;
    private Logger myLogger = Logger.getInstance(getClass());

    @Override
    public void setText(@Nullable final String s) {
      UIUtil.invokeLaterIfNeeded(() -> myProgressOverallLabel.setText(s));
      if (myIndicator != null) {
        myIndicator.setText(s);
      }
    }

    @Override
    public boolean isCanceled() {
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
      return myProgressBar.getPercentComplete();
    }

    @Override
    public void setSecondaryText(@Nullable final String s) {
      UIUtil.invokeLaterIfNeeded(() -> myProgressDetailLabel.setText(s));
      if (myIndicator != null) {
        myIndicator.setText2(s);
      }
    }

    @Override
    public void logWarning(@NotNull String s) {
      appendText(s);
      myLogger.warn(s);
    }

    @Override
    public void logWarning(@NotNull String s, @Nullable Throwable e) {
      appendText(s);
      myLogger.warn(s, e);
    }

    @Override
    public void logError(@NotNull String s) {
      appendText(s);
      myLogger.error(s);
    }

    @Override
    public void logError(@NotNull String s, @Nullable Throwable e) {
      appendText(s);
      myLogger.error(s, e);
    }

    @Override
    public void logInfo(@NotNull String s) {
      appendText(s);
      myLogger.info(s);
    }

    private void appendText(@NotNull final String s) {
      UIUtil.invokeLaterIfNeeded(() -> {
        String current = mySdkManagerOutput.getText();
        if (current == null) {
          current = "";
        }
        mySdkManagerOutput.setText(current + "\n" + s);
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

    public BackgroundAction() {
      super("Background");
    }

    public void setWizard(@NotNull ModelWizard.Facade wizard) {
      myWizard = wizard;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myIsBackgrounded = true;
      myWizard.cancel();
    }

    public boolean isBackgrounded() {
      return myIsBackgrounded;
    }
  }
}
