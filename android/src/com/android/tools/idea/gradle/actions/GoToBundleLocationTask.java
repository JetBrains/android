/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.AppBundleProjectBuildOutput;
import com.android.builder.model.AppBundleVariantBuildOutput;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.run.OutputBuildAction;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.notification.EventLog;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.*;

import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.notification.NotificationType.INFORMATION;

public class GoToBundleLocationTask implements GradleBuildInvoker.AfterGradleInvocationTask {
  public static final String MODULE = "module:";
  @NotNull private final Project myProject;
  @NotNull private final String myNotificationTitle;
  @Nullable private final Collection<Module> myModules;
  @Nullable private Map<Module, File> myModulesAndBundlePaths;

  public GoToBundleLocationTask(@NotNull Project project, @NotNull Collection<Module> modules, @NotNull String notificationTitle) {
    this(project, notificationTitle, modules, null);
  }

  @VisibleForTesting
  GoToBundleLocationTask(@NotNull Project project,
                         @NotNull String notificationTitle,
                         @Nullable Collection<Module> modules,
                         @Nullable Map<Module, File> modulesAndPaths) {
    myProject = project;
    myNotificationTitle = notificationTitle;
    myModulesAndBundlePaths = modulesAndPaths;
    myModules = modules;
  }

  @Override
  public void execute(@NotNull GradleInvocationResult result) {
    try {
      if (myModulesAndBundlePaths == null) {
        myModulesAndBundlePaths = getModulesAndPaths(result.getModel());
      }

      // Sorted module name -> output bundle file
      SortedMap<String, File> bundlePathsByModule = sortModules(myModulesAndBundlePaths);

      AndroidNotification notification = AndroidNotification.getInstance(myProject);
      if (result.isBuildSuccessful()) {
        notifySuccess(notification, bundlePathsByModule);
      }
      else if (result.isBuildCancelled()) {
        notification.showBalloon(myNotificationTitle, "Build cancelled.", INFORMATION);
      }
      else {
        String msg = "Errors while building Bundle file. You can find the errors in the 'Messages' view.";
        notification.showBalloon(myNotificationTitle, msg, ERROR);
      }
    }
    finally {
      // See https://code.google.com/p/android/issues/detail?id=195369
      GradleBuildInvoker.getInstance(myProject).remove(this);
    }
  }

  private void notifySuccess(@NotNull AndroidNotification notification,
                             @NotNull SortedMap<String, File> bundlePathsByModule) {
    if (ShowFilePathAction.isSupported()) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("Bundle file(s) generated successfully:<br/>");

      int moduleIndex = 0;
      for (Map.Entry<String, File> entry : bundlePathsByModule.entrySet()) {
        String moduleName = entry.getKey();
        buffer.append("Module '" ).append(moduleName).append("': ");
        buffer.append("<a href=\"").append(MODULE).append(moduleName).append("\">locate</a> the Bundle file.");
        if (moduleIndex < bundlePathsByModule.size() - 1) {
          buffer.append("<br/>");
        }
      }

      String text = buffer.toString();
      notification.showBalloon(myNotificationTitle, text, INFORMATION, new OpenFolderNotificationListener(myProject,
                                                                                                          bundlePathsByModule));
    }
    else {
      // Platform does not support showing the location of a file.
      // Display file paths in the 'Log' view, since they could be too long to show in a balloon notification.
      StringBuilder buffer = new StringBuilder();
      buffer.append("Bundle file(s) generated successfully:\n");

      int moduleIndex = 0;
      for (Map.Entry<String, File> entry : bundlePathsByModule.entrySet()) {
        String moduleName = entry.getKey();
        buffer.append(" - ").append(moduleName).append(": ");
        buffer.append(entry.getValue().getPath());
        if (moduleIndex < bundlePathsByModule.size() - 1) {
          buffer.append("\n");
        }
      }
      notification.showBalloon(myNotificationTitle, "Bundle file(s) generated successfully.", INFORMATION, new OpenEventLogHyperlink());
      notification.addLogEvent(myNotificationTitle, buffer.toString(), INFORMATION);
    }
  }

  /**
   * Generates a map from module to the location (either the bundle file itself if only one or to the folder if multiples).
   */
  @NotNull
  @VisibleForTesting
  Map<Module, File> getModulesAndPaths(@Nullable Object model) {
    assert myModules != null;

    Map<Module, File> modulesAndPaths = new HashMap<>();
    if (model instanceof OutputBuildAction.PostBuildProjectModels) {
      PostBuildModel postBuildModel = new PostBuildModel((OutputBuildAction.PostBuildProjectModels)model);

      for (Module module : myModules) {
        AndroidModuleModel androidModel = AndroidModuleModel.get(module);
        if (androidModel != null) {
          File bundleFile = tryToGetOutputPostBuildBundleFile(androidModel, module, postBuildModel);
          if (bundleFile != null) {
            modulesAndPaths.put(module, bundleFile);
          }
        }
      }
    }

    return modulesAndPaths;
  }

  @NotNull
  private static SortedMap<String, File> sortModules(@NotNull Map<Module, File> modulesAndBundlePaths) {
    SortedMap<String, File> bundlePathsByModule = new TreeMap<>();
    for (Map.Entry<Module, File> moduleAndBundlePath : modulesAndBundlePaths.entrySet()) {
      Module module = moduleAndBundlePath.getKey();
      File bundlePath = moduleAndBundlePath.getValue();
      if (bundlePath != null) {
        String moduleName = module.getName();
        bundlePathsByModule.put(moduleName, bundlePath);
      }
    }
    return bundlePathsByModule;
  }

  @Nullable
  private static File tryToGetOutputPostBuildBundleFile(@NotNull AndroidModuleModel androidModel,
                                                        @NotNull Module module,
                                                        @NotNull PostBuildModel postBuildModel) {
    if (androidModel.getAndroidProject().getProjectType() == AndroidProject.PROJECT_TYPE_APP) {
      AppBundleProjectBuildOutput appBundleProjectBuildOutput = postBuildModel.findAppBundleProjectBuildOutput(module);
      if (appBundleProjectBuildOutput != null) {
        for (AppBundleVariantBuildOutput variantBuildOutput : appBundleProjectBuildOutput.getAppBundleVariantsBuildOutput()) {
          if (variantBuildOutput.getName().equals(androidModel.getSelectedVariant().getName())) {
            return variantBuildOutput.getBundleFile();
          }
        }
      }
    }

    return null;
  }

  @VisibleForTesting
  static class OpenFolderNotificationListener extends NotificationListener.Adapter {
    @NotNull private final Project myProject;
    @NotNull private final Map<String, File> myBundlePathsPerModule;

    OpenFolderNotificationListener(@NotNull Project project, @NotNull Map<String, File> apkPathsPerModule) {
      myProject = project;
      myBundlePathsPerModule = apkPathsPerModule;
    }

    @Override
    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
      // Safety check
      if (myProject.isDisposed()) {
        return;
      }

      String description = e.getDescription();
      if (description.startsWith(MODULE)){
        File apkPath = myBundlePathsPerModule.get(description.substring(MODULE.length()));
        assert apkPath != null;
        if (apkPath.isFile()){
          apkPath = apkPath.getParentFile();
        }
        ShowFilePathAction.openDirectory(apkPath);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      OpenFolderNotificationListener listener = (OpenFolderNotificationListener)o;
      return Objects.equals(myBundlePathsPerModule, listener.myBundlePathsPerModule);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myBundlePathsPerModule);
    }
  }

  private static class OpenEventLogHyperlink extends NotificationHyperlink {
    OpenEventLogHyperlink() {
      super("open.event.log", "Show APK path(s) in the 'Event Log' view");
    }

    @Override
    protected void execute(@NotNull Project project) {
      ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(EventLog.LOG_TOOL_WINDOW_ID);
      if (tw != null) {
        tw.activate(null, false);
      }
    }
  }
}
