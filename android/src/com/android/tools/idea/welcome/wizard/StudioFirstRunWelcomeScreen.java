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
package com.android.tools.idea.welcome.wizard;

import com.android.SdkConstants;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder;
import com.android.tools.idea.welcome.config.AndroidFirstRunPersistentData;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;

import static org.jetbrains.android.util.AndroidBundle.message;

/**
 * Android Studio's implementation of a {@link WelcomeScreen}. Starts up a wizard  meant to run the first time someone starts up
 * Android Studio to ask them to pick from some initial, useful options. Once the wizard is complete, it will bring the  user to the
 * initial "Welcome Screen" UI (with a list of projects and options to start a new project, etc.)
 */
public class StudioFirstRunWelcomeScreen implements WelcomeScreen {
  @NotNull private final ModelWizard myModelWizard;
  @NotNull private final JComponent myMainPanel;
  private final FirstRunWizardMode myMode;

  public StudioFirstRunWelcomeScreen(FirstRunWizardMode mode) {
    myMode = mode;
    ModelWizard.Builder wizardBuilder = new ModelWizard.Builder();

    // TODO: Add more steps and check wich steps to add for each different FirstRunWizardMode
    boolean sdkExists = false;
    File initialSdkLocation = FirstRunWizardDefaults.getInitialSdkLocation(mode);
    if (initialSdkLocation.isDirectory()) {
      AndroidSdkHandler sdkHandler = AndroidSdkHandler.getInstance(initialSdkLocation);
      ProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
      sdkExists = sdkHandler.getLocalPackage(SdkConstants.FD_TOOLS, progress) != null;
    }

    myModelWizard = new ModelWizard.Builder()
      .addStep(new FirstRunWelcomeStep(sdkExists))
      .build();

    // Note: We create a ModelWizardDialog, but we are only interested in its Content Panel
    // This is a bit of a hack, but it's the simplest way to reuse logic from ModelWizardDialog
    // (which inherits from IntelliJ's DialogWrapper class, which we can't refactor here).
    ModelWizardDialog modelWizardDialog = new StudioWizardDialogBuilder(myModelWizard, "").build();
    myMainPanel = modelWizardDialog.getContentPanel();

    // Replace Content Panel with dummy version, as we are going to return its original value to the welcome frame
    modelWizardDialog.getPeer().setContentPane(new JPanel());

    Disposer.register(this, modelWizardDialog.getDisposable());
    Disposer.register(this, myModelWizard);
  }

  @Override
  public JComponent getWelcomePanel() {
    return myMainPanel;
  }

  @Override
  public void setupFrame(@NotNull JFrame frame) {
    frame.setTitle(message("android.wizard.welcome.dialog.title"));
    frame.pack();
    frame.setLocationRelativeTo(null);

    // Intercept windowClosing event, to show the closing confirmation dialog
    WindowListener[] oldIdeaListeners = removeAllWindowListeners(frame);
    frame.addWindowListener(new DelegatingListener(oldIdeaListeners));

    myModelWizard.addResultListener(new ModelWizard.WizardListener() {
      @Override
      public void onWizardFinished(@NotNull ModelWizard.WizardResult wizardResult) {
        closeDialog(frame);
      }
    });

    myModelWizard.setCancelInterceptor(() -> shouldPreventWizardCancel(frame));
  }

  @Override
  public void dispose() {
  }

  private static void closeDialog(Window frame) {
    frame.setVisible(false);
    frame.dispose();
    WelcomeFrame.showNow();
  }

  private boolean shouldPreventWizardCancel(Window frame) {
    ConfirmFirstRunWizardCloseDialog.Result result = new ConfirmFirstRunWizardCloseDialog().open();
    switch (result) {
      case Skip:
        AndroidFirstRunPersistentData.getInstance().markSdkUpToDate(myMode.getInstallerTimestamp());
        // Fallthrough
      case Rerun:
        closeDialog(frame);
        return false;
      case DoNotClose:
        break; // Do nothing
      default:
        throw new RuntimeException("Invalid Close result"); // Unknown option
    }
    return true;
  }

  private static WindowListener[] removeAllWindowListeners(Window frame) {
    WindowListener[] listeners = frame.getWindowListeners();
    for (WindowListener listener : listeners) {
      frame.removeWindowListener(listener);
    }
    return listeners;
  }

  /**
   * This code is needed to avoid breaking IntelliJ native event processing.
   */
  private class DelegatingListener implements WindowListener {
    @NotNull private final WindowListener[] myIdeaListeners;

    public DelegatingListener(@NotNull WindowListener[] ideaListeners) {
      myIdeaListeners = ideaListeners;
    }

    @Override
    public void windowOpened(WindowEvent e) {
      for (WindowListener listener : myIdeaListeners) {
        listener.windowOpened(e);
      }
    }

    @Override
    public void windowClosed(WindowEvent e) {
      for (WindowListener listener : myIdeaListeners) {
        listener.windowClosed(e);
      }
    }

    @Override
    public void windowIconified(WindowEvent e) {
      for (WindowListener listener : myIdeaListeners) {
        listener.windowIconified(e);
      }
    }

    @Override
    public void windowClosing(WindowEvent e) {
      // Don't let listener get this event, as it will shut down Android Studio completely. Instead, just delegate to the model wizard.
      myModelWizard.cancel();
    }

    @Override
    public void windowDeiconified(WindowEvent e) {
      for (WindowListener listener : myIdeaListeners) {
        listener.windowDeiconified(e);
      }
    }

    @Override
    public void windowActivated(WindowEvent e) {
      for (WindowListener listener : myIdeaListeners) {
        listener.windowActivated(e);
      }
    }

    @Override
    public void windowDeactivated(WindowEvent e) {
      for (WindowListener listener : myIdeaListeners) {
        listener.windowDeactivated(e);
      }
    }
  }
}
