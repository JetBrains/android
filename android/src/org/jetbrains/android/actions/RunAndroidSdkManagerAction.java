/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.actions;

import com.android.SdkConstants;
import com.android.tools.idea.stats.UsageTracker;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;

import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidSdkManagerEnabled;

/**
 * @author Eugene.Kudelevsky
 */
public class RunAndroidSdkManagerAction extends AndroidRunSdkToolAction {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.actions.RunAndroidSdkManagerAction");

  public static void updateInWelcomePage(@Nullable Component component) {
    if (!isAndroidSdkManagerEnabled()) {
      // The action is not visible anyway, no need to do anything.
      return;
    }

    if (!ApplicationManager.getApplication().isUnitTestMode() && ProjectManager.getInstance().getOpenProjects().length == 0) {
      // If there are no open projects, the "SDK Manager" configurable was invoked from the "Welcome Page". We need to update the
      // "SDK Manager" action to enable it.
      ActionManager actionManager = ActionManager.getInstance();
      AnAction sdkManagerAction = actionManager.getAction("WelcomeScreen.RunAndroidSdkManager");
      if (sdkManagerAction instanceof RunAndroidSdkManagerAction) {
        Presentation presentation = sdkManagerAction.getTemplatePresentation();

        IdeFrame frame = WelcomeFrame.getInstance();
        if (frame == null) {
          return;
        }
        Component c = component != null ? component : frame.getComponent();
        DataContext dataContext = DataManager.getInstance().getDataContext(c);

        //noinspection ConstantConditions
        AnActionEvent event = new AnActionEvent(null, dataContext, ActionPlaces.WELCOME_SCREEN, presentation, actionManager, 0);
        sdkManagerAction.update(event);
      }
    }
  }

  public RunAndroidSdkManagerAction() {
    super(getName());
  }

  private static String getName() {
    return AndroidBundle.message("android.run.sdk.manager.action.text");
  }

  @Override
  public void update(AnActionEvent e) {
    if (ActionPlaces.WELCOME_SCREEN.equals(e.getPlace())) {
      // Don't need a project when invoking SDK Manager from Welcome Screen
      e.getPresentation().setEnabled(AndroidSdkUtils.isAndroidSdkAvailable());
      return;
    }
    super.update(e);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (ActionPlaces.WELCOME_SCREEN.equals(e.getPlace())) {
      // Invoked from Welcome Screen, might not have an SDK setup yet
      AndroidSdkData sdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
      if (sdkData != null) {
        doRunTool(null, sdkData.getLocation().getPath());
      }
    }
    else {
      // Invoked from a project context
      super.actionPerformed(e);
    }
  }

  public static void runSpecificSdkManager(@Nullable Project project, @NotNull File sdkHome) {
    new RunAndroidSdkManagerAction().doRunTool(project, sdkHome.getPath());
  }

  public static void runSpecificSdkManagerSynchronously(@Nullable Project project, @NotNull File sdkHome) {
    new SdkManagerRunner(sdkHome.getPath(), null, project).run();
  }

  @Override
  protected void doRunTool(@Nullable final Project project, @NotNull final String sdkPath) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        launchExternalSdkManager(project, sdkPath);
      }
    });
  }


  private static void launchExternalSdkManager(@Nullable final Project project, @NotNull final String sdkPath) {
    ProgressWindow p = new ProgressWindow(false, true, project);
    p.setIndeterminate(false);
    p.setDelayInMillis(0);

    ApplicationManager.getApplication().executeOnPooledThread(new SdkManagerRunner(sdkPath, p, project));
  }

  private static class SdkManagerRunner implements Runnable {
    private final String mySdkPath;
    private final ProgressWindow myProgressWindow;
    private final Project myProject;

    private SdkManagerRunner(@NotNull String sdkPath, @Nullable ProgressWindow progressWindow, @Nullable Project project) {
      mySdkPath = sdkPath;
      myProgressWindow = progressWindow;
      myProject = project;
    }

    @Override
    public void run() {
      UsageTracker.getInstance()
        .trackEvent(UsageTracker.CATEGORY_SDK_MANAGER, UsageTracker.ACTION_SDK_MANAGER_STANDALONE_LAUNCHED, null, null);

      String toolPath = mySdkPath + File.separator + AndroidCommonUtils.toolPath(SdkConstants.androidCmdName());
      GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setExePath(toolPath);
      commandLine.addParameter("sdk");

      StringBuildingOutputProcessor processor = new StringBuildingOutputProcessor();
      try {
        if (AndroidUtils.executeCommand(commandLine, processor, WaitingStrategies.WaitForTime.getInstance(500)) ==
            ExecutionStatus.TIMEOUT) {

          // It takes about 2 seconds to start the SDK Manager on Windows. Display a small
          // progress indicator otherwise it seems like the action wasn't invoked and users tend
          // to click multiple times on it, ending up with several instances of the manager
          // window.
          if (myProgressWindow != null) {
            try {
              myProgressWindow.start();
              myProgressWindow.setText("Starting SDK Manager...");
              for (double d = 0; d < 1; d += 1.0 / 20) {
                myProgressWindow.setFraction(d);
                //noinspection BusyWait
                Thread.sleep(100);
              }
            }
            catch (InterruptedException ignore) {
            }
            finally {
              myProgressWindow.stop();
            }
          }
          return;
        }
      }
      catch (ExecutionException e) {
        LOG.error(e);
        return;
      }

      final String message = processor.getMessage();
      if (message.toLowerCase().contains("error")) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            Messages.showErrorDialog(myProject, "Cannot launch SDK manager.\nOutput:\n" + message, getName());
          }
        });
      }
    }
  }
}
