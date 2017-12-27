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
package com.android.tools.idea.gradle.actions;

import com.android.build.OutputFile;
import com.android.builder.model.*;
import com.android.tools.idea.apk.viewer.ApkFileSystem;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.run.OutputBuildAction;
import com.android.tools.idea.gradle.run.PostBuildModel;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.notification.EventLog;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.*;

import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.notification.NotificationType.INFORMATION;

public class GoToApkLocationTask implements GradleBuildInvoker.AfterGradleInvocationTask {
  public static final String ANALYZE = "analyze:";
  public static final String MODULE = "module:";
  @NotNull private final Project myProject;
  @Nullable private final Collection<Module> myModules;
  @NotNull private final String myNotificationTitle;

  private Map<Module, File> myModulesAndApkPaths;

  public GoToApkLocationTask(@NotNull Map<Module, File> modulesAndPaths, @NotNull String notificationTitle) {
    this(modulesAndPaths.entrySet().iterator().next().getKey().getProject(), modulesAndPaths, null, notificationTitle);
  }
  public GoToApkLocationTask(@NotNull Collection<Module> modules, @NotNull String notificationTitle) {
    this(modules.iterator().next().getProject(), null, modules, notificationTitle);
  }

  @VisibleForTesting
  GoToApkLocationTask(@NotNull Project project,
                      @Nullable Map<Module, File> modulesAndPaths,
                      @Nullable Collection<Module> modules,
                      @NotNull String notificationTitle) {
    myProject = project;
    myModulesAndApkPaths = modulesAndPaths;
    myModules = modules;
    myNotificationTitle = notificationTitle;
  }

  @Override
  public void execute(@NotNull GradleInvocationResult result) {
    try {
      if (myModulesAndApkPaths == null) {
        myModulesAndApkPaths = getModulesAndPaths(result.getModel());
      }

      List<String> moduleNames = new ArrayList<>();
      Map<String, File> apkPathsByModule = new HashMap<>();
      for (Map.Entry<Module, File> moduleAndApkPath : myModulesAndApkPaths.entrySet()) {
        Module module = moduleAndApkPath.getKey();
        File apkPath = moduleAndApkPath.getValue();
        if (apkPath != null) {
          String moduleName = module.getName();
          moduleNames.add(moduleName);
          apkPathsByModule.put(moduleName, apkPath);
        }
      }

      Collections.sort(moduleNames);

      AndroidNotification notification = AndroidNotification.getInstance(myProject);
      if (result.isBuildSuccessful()) {
        if (ShowFilePathAction.isSupported()) {
          StringBuilder buffer = new StringBuilder();
          buffer.append("APK(s) generated successfully:<br/>");
          int moduleCount = moduleNames.size();
          for (int i = 0; i < moduleCount; i++) {
            String moduleName = moduleNames.get(i);
            buffer.append("Module '" ).append(moduleName).append("': ");
            buffer.append("<a href=\"").append(MODULE).append(moduleName).append("\">locate</a> or ");
            buffer.append("<a href=\"").append(ANALYZE).append(moduleName).append("\">analyze</a> the APK.");
            if (i < moduleCount - 1) {
              buffer.append("<br/>");
            }
          }
          String text = buffer.toString();
          notification.showBalloon(myNotificationTitle, text, INFORMATION, new OpenFolderNotificationListener(apkPathsByModule, myProject));
        }
        else {
          // Platform does not support showing the location of a file.
          // Display file paths in the 'Log' view, since they could be too long to show in a balloon notification.
          StringBuilder buffer = new StringBuilder();
          buffer.append("APK(s) generated successfully:\n");
          int moduleCount = moduleNames.size();
          for (int i = 0; i < moduleCount; i++) {
            String moduleName = moduleNames.get(i);
            buffer.append(" - ").append(moduleName).append(": ");
            buffer.append(apkPathsByModule.get(moduleName).getPath());
            if (i < moduleCount - 1) {
              buffer.append("\n");
            }
          }
          notification.showBalloon(myNotificationTitle, "APK(s) generated successfully.", INFORMATION, new OpenEventLogHyperlink());
          notification.addLogEvent(myNotificationTitle, buffer.toString(), INFORMATION);
        }
      }
      else if (result.isBuildCancelled()) {
        notification.showBalloon(myNotificationTitle, "Build cancelled.", INFORMATION);
      }
      else {
        String msg = "Errors while building APK. You can find the errors in the 'Messages' view.";
        notification.showBalloon(myNotificationTitle, msg, ERROR);
      }
    }
    finally {
      // See https://code.google.com/p/android/issues/detail?id=195369
      GradleBuildInvoker.getInstance(myProject).remove(this);
    }
  }

  /**
   * Generates a map from module to the location (either the apk itself if only one or to the folder if multiples).
   */
  @NotNull
  @VisibleForTesting
  Map<Module, File> getModulesAndPaths(@Nullable Object model) {
    assert myModules != null;

    Map<Module, File> modulesAndPaths = new HashMap<>();
    PostBuildModel postBuildModel = null;

    if (model != null && model instanceof OutputBuildAction.PostBuildProjectModels) {
      postBuildModel = new PostBuildModel((OutputBuildAction.PostBuildProjectModels) model);
    }

    for (Module module : myModules) {
      AndroidModuleModel androidModel = AndroidModuleModel.get(module);
      if (androidModel == null) {
        continue;
      }

      File outputFolderOrApk = null;
      if (postBuildModel != null) {
        outputFolderOrApk = tryToGetOutputPostBuild(androidModel, module, postBuildModel);
        if (outputFolderOrApk == null) {
          outputFolderOrApk = tryToGetOutputPostBuildInstantApp(androidModel, module, postBuildModel);
        }
      }
      if (outputFolderOrApk == null) {
        outputFolderOrApk = tryToGetOutputPreBuild(androidModel);
      }

      modulesAndPaths.put(module, outputFolderOrApk);
    }

    return modulesAndPaths;
  }

  @Nullable
  private static File tryToGetOutputPostBuild(@NotNull AndroidModuleModel androidModel,
                                              @NotNull Module module,
                                              @NotNull PostBuildModel postBuildModel) {
    if (androidModel.getAndroidProject().getProjectType() == AndroidProject.PROJECT_TYPE_APP) {
      ProjectBuildOutput projectBuildOutput = postBuildModel.findProjectBuildOutput(module);
      if (projectBuildOutput != null) {
        for (VariantBuildOutput variantBuildOutput : projectBuildOutput.getVariantsBuildOutput()) {
          if (variantBuildOutput.getName().equals(androidModel.getSelectedVariant().getName())) {
            Collection<OutputFile> outputs = variantBuildOutput.getOutputs();
            File outputFolderOrApk;
            if (outputs.size() == 1) {
              outputFolderOrApk = outputs.iterator().next().getOutputFile();
            }
            else {
              outputFolderOrApk = outputs.iterator().next().getOutputFile().getParentFile();
            }
            return outputFolderOrApk;
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private static File tryToGetOutputPostBuildInstantApp(@NotNull AndroidModuleModel androidModel,
                                                        @NotNull Module module,
                                                        @NotNull PostBuildModel postBuildModel) {
    if (androidModel.getAndroidProject().getProjectType() == AndroidProject.PROJECT_TYPE_INSTANTAPP) {
      InstantAppProjectBuildOutput instantAppProjectBuildOutput = postBuildModel.findInstantAppProjectBuildOutput(module);
      if (instantAppProjectBuildOutput != null) {
        for (InstantAppVariantBuildOutput variantBuildOutput : instantAppProjectBuildOutput.getInstantAppVariantsBuildOutput()) {
          if (variantBuildOutput.getName().equals(androidModel.getSelectedVariant().getName())) {
            return variantBuildOutput.getOutput().getOutputFile();
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private static File tryToGetOutputPreBuild(@NotNull AndroidModuleModel androidModel) {
    Collection<AndroidArtifactOutput> outputs = androidModel.getMainArtifact().getOutputs();
    if (outputs.size() == 1) {
      return outputs.iterator().next().getOutputFile();
    }
    return outputs.iterator().next().getOutputFile().getParentFile();
  }

  @VisibleForTesting
  static class OpenFolderNotificationListener extends NotificationListener.Adapter {
    @NotNull private final Map<String, File> myApkPathsPerModule;
    @NotNull private final Project myProject;

    OpenFolderNotificationListener(@NotNull Map<String, File> apkPathsPerModule, @NotNull Project project) {
      myApkPathsPerModule = apkPathsPerModule;
      myProject = project;
    }

    @Override
    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
      String description = e.getDescription();
      if (description.startsWith(ANALYZE)){
        File apkPath = myApkPathsPerModule.get(description.substring(ANALYZE.length()));
        VirtualFile apk;
        if (apkPath.isFile()) {
          apk = LocalFileSystem.getInstance().findFileByIoFile(apkPath);
        } else {
          FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withDescription("Select APK to analyze")
            .withFileFilter(file -> ApkFileSystem.EXTENSIONS.contains(file.getExtension()));
          apk = FileChooser.chooseFile(descriptor, myProject, LocalFileSystem.getInstance().findFileByIoFile(apkPath));
        }
        if (apk != null) {
          OpenFileDescriptor fd = new OpenFileDescriptor(myProject, apk);
          FileEditorManager.getInstance(myProject).openEditor(fd, true);
        }
      } else if (description.startsWith(MODULE)){
        File apkPath = myApkPathsPerModule.get(description.substring(MODULE.length()));
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
      return Objects.equals(myApkPathsPerModule, listener.myApkPathsPerModule);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myApkPathsPerModule);
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
