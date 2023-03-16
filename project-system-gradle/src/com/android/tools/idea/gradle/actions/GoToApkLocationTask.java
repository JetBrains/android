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

import com.android.tools.idea.apk.viewer.ApkFileSystem;
import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildResult;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterators;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.notification.ActionCenter;
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
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvokerKt.whenFinished;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.notification.NotificationType.INFORMATION;

public class GoToApkLocationTask {
  public static final String ANALYZE = "analyze:";
  public static final String MODULE = "module:";
  @NotNull private final Project myProject;
  @NotNull private final Collection<Module> myModules;
  @NotNull private final String myNotificationTitle;
  @NotNull private final List<String> myBuildVariants;

  public GoToApkLocationTask(@NotNull Project project, @NotNull Collection<Module> modules, @NotNull String notificationTitle) {
    this(project, modules, notificationTitle, Collections.emptyList());
  }

  public GoToApkLocationTask(@NotNull Project project,
                             @NotNull Collection<Module> modules,
                             @NotNull String notificationTitle,
                             @NotNull List<String> buildVariants) {
    myProject = project;
    myModules = modules;
    myNotificationTitle = notificationTitle;
    myBuildVariants = buildVariants;
  }

  public void executeWhenBuildFinished(@NotNull ListenableFuture<AssembleInvocationResult> resultFuture) {
    whenFinished(
      resultFuture,
      directExecutor(),
      result -> {
        BuildsToPathsMapper buildsToPathsMapper =
          BuildsToPathsMapper.getInstance(myProject);
        Map<String, File> apkBuildsToPaths =
          buildsToPathsMapper.getBuildsToPaths(result, myBuildVariants, myModules, false);
        showNotification(result, apkBuildsToPaths);
        return null;
      });
  }

  private void showNotification(@NotNull GradleBuildResult result,
                                @NotNull Map<String, File> apkBuildsToPaths) {
    AndroidNotification notification = AndroidNotification.getInstance(myProject);
    boolean isSigned = !myBuildVariants.isEmpty();

    if (result.isBuildSuccessful()) {
      StringBuilder builder = new StringBuilder();
      int count = apkBuildsToPaths.size();
      builder.append("APK(s) generated successfully for ");
      if (isSigned) {
        String moduleName = Iterators.getOnlyElement(myModules.iterator()).getName();
        builder.append("module '").append(moduleName).append("' with ").append(count)
          .append(count == 1 ? " build variant" : " build variants");
      }
      else {
        builder.append(count).append(count == 1 ? " module" : " modules");
      }

      builder.append(":<br/>");
      if (isShowFilePathActionSupported()) {
        for (Iterator<String> iterator = apkBuildsToPaths.keySet().iterator(); iterator.hasNext(); ) {
          String moduleOrBuildVariant = iterator.next();
          if (isSigned) {
            builder.append("Build variant '");
          }
          else {
            builder.append("Module '");
          }
          builder.append(moduleOrBuildVariant).append("': ");
          builder.append("<a href=\"").append(MODULE).append(moduleOrBuildVariant).append("\">locate</a> or ");
          builder.append("<a href=\"").append(ANALYZE).append(moduleOrBuildVariant).append("\">analyze</a> the APK.");
          if (iterator.hasNext()) {
            builder.append("<br/>");
          }
        }

        String text = builder.toString();
        notification
          .showBalloon(myNotificationTitle, text, INFORMATION, new OpenFolderNotificationListener(apkBuildsToPaths, myProject));
      }
      else {
        // Platform does not support showing the location of a file.
        // Display file paths in the 'Log' view, since they could be too long to show in a balloon notification.
        builder.append(apkBuildsToPaths.entrySet().stream()
                         .map(entry -> String.format(" - %s: %s", entry.getKey(), entry.getValue().getPath()))
                         .collect(Collectors.joining("\n")));
        StringBuilder balloonBuilder = new StringBuilder();
        balloonBuilder.append("APK(s) generated successfully for ");
        if (isSigned) {
          String moduleName = Iterators.getOnlyElement(myModules.iterator()).getName();
          balloonBuilder.append("module '").append(moduleName).append("' with ").append(count)
            .append(count == 1 ? " build variant" : " build variants");
        }
        else {
          balloonBuilder.append(count).append(count == 1 ? " module" : " modules");
        }
        notification.showBalloon(myNotificationTitle, balloonBuilder.toString(), INFORMATION, new OpenEventLogHyperlink());
        notification.addLogEvent(myNotificationTitle, builder.toString(), INFORMATION);
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

  @VisibleForTesting
  boolean isShowFilePathActionSupported() {
    return RevealFileAction.isSupported();
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
      if (description.startsWith(ANALYZE)) {
        File apkPath = myApkPathsPerModule.get(description.substring(ANALYZE.length()));
        VirtualFile apk;
        if (apkPath.isFile()) {
          apk = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(apkPath);
        }
        else {
          FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withDescription("Select APK to analyze")
            .withFileFilter(file -> ApkFileSystem.EXTENSIONS.contains(file.getExtension()));
          apk = FileChooser.chooseFile(descriptor, myProject, LocalFileSystem.getInstance().findFileByIoFile(apkPath));
        }
        if (apk != null) {
          OpenFileDescriptor fd = new OpenFileDescriptor(myProject, apk);
          FileEditorManager.getInstance(myProject).openEditor(fd, true);
        }
      }
      else if (description.startsWith(MODULE)) {
        File apkPath = myApkPathsPerModule.get(description.substring(MODULE.length()));
        assert apkPath != null;
        if (apkPath.isFile()) {
          apkPath = apkPath.getParentFile();
        }
        RevealFileAction.openDirectory(apkPath);
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

  @VisibleForTesting
  static class OpenEventLogHyperlink extends NotificationHyperlink {
    OpenEventLogHyperlink() {
      super("open.event.log", "Show APK path(s) in the '" + ActionCenter.getToolwindowName() + "' view");
    }

    @Override
    protected void execute(@NotNull Project project) {
      ToolWindow tw = ActionCenter.getToolWindow(project);
      if (tw != null) {
        tw.activate(null, false);
      }
    }

    @Override
    public int hashCode() {
      return Objects.hash(getUrl(), toHtml());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      // Compare important fields of super class for equality.
      OpenEventLogHyperlink other = (OpenEventLogHyperlink)o;
      return getUrl().equals(other.getUrl()) && toHtml().equals(other.toHtml());
    }
  }
}
