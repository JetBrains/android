/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.annotations.NonNull;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.repository.updater.SdkUpdaterNoWindow;
import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.wizard.TemplateWizardState;
import com.android.tools.idea.wizard.TemplateWizardStep;
import com.android.utils.ILogger;
import com.android.utils.IReaderLogger;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SmwOldApiDirectInstall extends TemplateWizardStep implements Disposable {
  private final TemplateWizardState myWizardState;
  private JBLabel myLabelSdkPath;
  private JTextArea myTextArea1;
  private JBLabel myLabelProgress1;
  private JProgressBar myProgressBar;
  private JBLabel myLabelProgress2;
  private JLabel myErrorLabel;
  private JPanel myContentPanel;
  private JButton myDoSomethingButton;


  @Override
  public void actionPerformed(ActionEvent e) {

  }

  public SmwOldApiDirectInstall(@NotNull TemplateWizardState wizardState, @Nullable TemplateWizardStep.UpdateListener updateListener) {
    super(wizardState, null /*project*/, null /*module*/, null /*sidePanelIcon*/, updateListener);
    myWizardState = wizardState;
    myDoSomethingButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        startSdkInstallUsingNonSwtOldApi();
      }
    });
  }

  @Override
  public void dispose() {
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }

  @Override
  public void _init() {
    super._init();
  }

  @Override
  public void _commit(boolean finishChosen) throws CommitStepException {
    super._commit(finishChosen);
  }

  @NotNull
  @Override
  protected JLabel getDescription() {
    // We're not using the Description field of the Template Wizard Step is useful here.
    // Since nullable isn't supported, share it with the error label (which is
    // fine since, again, template wizard description field isn't useful here.)
    return myErrorLabel;
  }

  @NotNull
  @Override
  protected JLabel getError() {
    return myErrorLabel;
  }

  //-----------

  private void startSdkInstallUsingNonSwtOldApi() {

    // TODO retrieve what to install, e.g. from the wizard state.
    // To know what IDs to use, run ~/sdk/tools/android.bat list sdk --all --extended
    // and look for the ids listed for each package.
    final ArrayList<String> requestedPackages = new ArrayList<String>(Arrays.asList(
      "android-19",   // platform
      "sample-19",
      "doc",
      "build-tools-19.1.0",
      "sysimg-19",    // obsolete ID (I have a pending CL changing sys-img management, notable this doesn't suppoort tags & vendors)
      "addon-google_apis-google-19",
      "addon-google_apis_x86-google-19",  // to become obsolete
      "extra-android-m2repository",
      "extra-android-support",            // typically not needed for studio
      "extra-google-m2repository"
      ));



    // Get the SDK instance.
    final AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
    SdkState sdkState = sdkData == null ? null : SdkState.getInstance(sdkData);

    if (sdkState == null) {
      myErrorLabel.setText("Error: can't get SDK instance.");
      return;
    }

    myLabelSdkPath.setText(sdkData.getLocation().getPath());

    final CustomLogger logger = new CustomLogger();

    Runnable onSdkAvailable = new Runnable() {
      @Override
      public void run() {
        // TODO: since the local SDK has been parsed, this is now a good time
        // to filter requestedPackages to remove current installed packages.
        // That's because on Windows trying to update some of the packages in-place
        // *will* fail (e.g. typically the android platform or the tools) as the
        // folder is most likely locked.
        // As mentioned in InstallTask below, the shortcut we're taking here will
        // install all the requested packages, even if already installed, which is
        // useless so that's another incentive to remove already installed packages
        // from the requested list.



        InstallTask task = new InstallTask(sdkData, requestedPackages, logger);
        BackgroundableProcessIndicator indicator = new BackgroundableProcessIndicator(task);
        logger.setIndicator(indicator);
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator);
      }
    };

    // loadAsync checks if the timeout expired and/or loads the SDK if it's not loaded yet.
    // If needed, it does a backgroundable Task to load the SDK and then calls onSdkAvailable.
    // Otherwise it returns false, in which case we call onSdkAvailable ourselves.
    logger.info("Loading SDK information...\n");
    if (!sdkState.loadAsync(1000 * 3600 * 24,  // 24 hour timeout since last check
                       false,           // canBeCancelled
                       onSdkAvailable,  // onSuccess
                       null)) {         // onError -- TODO display something?
      onSdkAvailable.run();
    }
  }

  private class InstallTask extends Task.Backgroundable {
    @NotNull private final AndroidSdkData mySdkData;
    @NotNull private final ArrayList<String> myRequestedPackages;
    @NotNull private final ILogger myLogger;

    private InstallTask(@NotNull AndroidSdkData sdkData,
                        @NotNull ArrayList<String> requestedPackages,
                        @NotNull ILogger logger) {
      super(null /*project*/,
            "Installing Android SDK",
            false /*canBeCancelled*/,
            PerformInBackgroundOption.ALWAYS_BACKGROUND);
      mySdkData = sdkData;
      myRequestedPackages = requestedPackages;
      myLogger = logger;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      // TODO: gray the "Next" wizard button if we want to make this page blocking.
      // This runs in a background task and isn't interrupted.

      // Perform the install by using the command-line interface and dumping the output into the logger.
      // The command-line API is a bit archaic and has some drastic limitations, one of them being that
      // it blindly re-install stuff even if already present IIRC.

      String osSdkFolder = mySdkData.getLocation().getPath();
      SdkManager sdkManager = SdkManager.createManager(osSdkFolder, myLogger);

      SdkUpdaterNoWindow upd = new SdkUpdaterNoWindow(
        osSdkFolder,
        sdkManager,
        myLogger,
        false,  // force -- The reply to any question asked by the update process.
               //          Currently this will be yes/no for ability to replace modified samples, restart ADB, restart on locked win folder.
        false, // useHttp -- True to force using HTTP instead of HTTPS for downloads.
        null,  // proxyPort -- An optional HTTP/HTTPS proxy port. Can be null. -- Can we get it from Studio?
        null); // proxyHost -- An optional HTTP/HTTPS proxy host. Can be null. -- Can we get it from Studio?

      upd.updateAll(myRequestedPackages,
                    true,   // all
                    false,  // dryMode
                    null);  // acceptLicense

      // TODO at the end of the task, signal the UI that the "Next" wizard button can be enabled.
    }
  }

  // Groups: 1=license-id
  private static Pattern sLicenceText = Pattern.compile("^\\s*License id:\\s*([a-z0-9-]+).*\\s*");
  // Groups: 1=progress values (%, ETA), 2=% int, 3=progress text
  private static Pattern sProgress1Text = Pattern.compile("^\\s+\\((([0-9]+)%,\\s*[^)]*)\\)(.*)\\s*");
  // Groups: 1=progress text, 2=progress values, 3=% int
  private static Pattern sProgress2Text = Pattern.compile("^\\s+([^(]+)\\s+\\((([0-9]+)%)\\)\\s*");

  private class CustomLogger implements IReaderLogger {

    private BackgroundableProcessIndicator myIndicator;
    private String myCurrLicense;
    private String myLastLine;

    public CustomLogger() {
    }

    public void setIndicator(BackgroundableProcessIndicator indicator) {
      myIndicator = indicator;
    }

    /**
     * Used by UpdaterData.acceptLicense() to prompt for license acceptance
     * when updating the SDK from the command-line.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public int readLine(byte[] inputBuffer) throws IOException {
      if (myLastLine != null && myLastLine.contains("Do you accept the license")) {
        // Let's take a simple shortcut and simply reply 'y' for yes.
        inputBuffer[0] = 'y';
        inputBuffer[1] = 0;
        return 1;
      }
      inputBuffer[0] = 'n';
      inputBuffer[1] = 0;
      return 1;
    }

    @Override
    public void error(@com.android.annotations.Nullable Throwable t,
                      @com.android.annotations.Nullable String msgFormat,
                      Object... args) {
      if (msgFormat == null && t != null) {
        if (myIndicator != null) myIndicator.setText2(t.toString());
        outputLine(t.toString());
      } else if (msgFormat != null) {
        if (myIndicator != null) myIndicator.setText2(String.format(msgFormat, args));
        outputLine(String.format(msgFormat, args));
      }
    }

    @Override
    public void warning(@NonNull String msgFormat, Object... args) {
      if (myIndicator != null) myIndicator.setText2(String.format(msgFormat, args));
      outputLine(String.format(msgFormat, args));
    }

    @Override
    public void info(@NonNull String msgFormat, Object... args) {
      if (myIndicator != null) myIndicator.setText2(String.format(msgFormat, args));
      outputLine(String.format(msgFormat, args));
    }

    @Override
    public void verbose(@NonNull String msgFormat, Object... args) {
      // Don't log verbose stuff in the background indicator.
      outputLine(String.format(msgFormat, args));
    }

    /**
     * This method takes the console output from the command-line updater.
     * It filters it to remove some verbose output that is not desirable here.
     * It also detects progress-bar like text and updates the dialog's progress
     * bar accordingly.
     */
    private void outputLine(@NonNull String line) {
      myLastLine = line;
      try {
        // skip some of the verbose output such as license text & refreshing http sources
        Matcher m = sLicenceText.matcher(line);
        if (m.matches()) {
          myCurrLicense = m.group(1);
          return;
        }
        else if (myCurrLicense != null) {
          if (line.contains("Do you accept the license") && line.contains(myCurrLicense)) {
            myCurrLicense = null;
          }
          return;
        }
        else if (line.contains("Fetching http") ||
                 line.contains("Fetching URL:") ||
                 line.contains("Validate XML") ||
                 line.contains("Parse XML") ||
                 line.contains("---------")) {
          return;
        }

        int progInt = -1;
        String progText2 = null;
        String progText1 = null;

        m = sProgress1Text.matcher(line);
        if (m.matches()) {
          // Groups: 1=progress values (%, ETA), 2=% int, 3=progress text
          try {
            progInt = Integer.parseInt(m.group(2));
          }
          catch (NumberFormatException ignore) {
            progInt = 0;
          }
          progText1 = m.group(3);
          progText2 = m.group(1);
          line = null;
        } else {
          m = sProgress2Text.matcher(line);
          if (m.matches()) {
            // Groups: 1=progress text, 2=progress values, 3=% int
            try {
              progInt = Integer.parseInt(m.group(3));
            }
            catch (NumberFormatException ignore) {
              progInt = 0;
            }
            progText1 = m.group(1);
            progText2 = m.group(2);
            line = null;
          }
        }

        final int fProgInt = progInt;
        final String fProgText2 = progText2;
        final String fProgText1 = progText1;
        final String fAddLine = line;

        // This is invoked from a backgroundable task, only update text on the ui thread.
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (fAddLine != null) {
              String current = myTextArea1.getText();
              if (current == null) {
                current = "";
              }
              myTextArea1.setText(current + fAddLine);
            }

            if (fProgText1 != null) {
              myLabelProgress1.setText(fProgText1);
            }

            if (fProgText2 != null) {
              myLabelProgress2.setText(fProgText2);
            }

            if (fProgInt >= 0) {
              myProgressBar.setValue(fProgInt);
            }
          }
        });
      } catch (Exception ignore) {}
    }
  }

}
