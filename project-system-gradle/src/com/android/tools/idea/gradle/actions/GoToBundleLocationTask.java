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

import com.android.SdkConstants;
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvokerKt.whenFinished;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.notification.NotificationType.INFORMATION;

public class GoToBundleLocationTask {
  public static final String ANALYZE_URL_PREFIX = "analyze:";
  public static final String LOCATE_URL_PREFIX = "module:";
  public static final String LOCATE_KEY_URL_PREFIX = "key:";
  @NotNull private final Project myProject;
  @NotNull private final String myNotificationTitle;
  @NotNull private final Collection<Module> myModules;
  @Nullable private final File myExportedKeyFile;
  @NotNull private final List<String> myBuildVariants;

  public GoToBundleLocationTask(@NotNull Project project,
                                @NotNull Collection<Module> modules,
                                @NotNull String notificationTitle) {
    this(project, modules, notificationTitle, Collections.emptyList(), null);
  }

  public GoToBundleLocationTask(@NotNull Project project,
                                @NotNull Collection<Module> modules,
                                @NotNull String notificationTitle,
                                @NotNull List<String> buildVariants,
                                @Nullable File exportedKeyFile) {
    myProject = project;
    myNotificationTitle = notificationTitle;
    myModules = modules;
    myExportedKeyFile = exportedKeyFile;
    myBuildVariants = buildVariants;
  }

  public void executeWhenBuildFinished(@NotNull ListenableFuture<AssembleInvocationResult> resultFuture) {
    whenFinished(
      resultFuture,
      directExecutor(),
      result -> {
        BuildsToPathsMapper buildsToPathsMapper =
          BuildsToPathsMapper.getInstance(myProject);
        Map<String, File> bundleBuildsToPath =
          buildsToPathsMapper.getBuildsToPaths(result, myBuildVariants, myModules, true);
        showNotification(result, bundleBuildsToPath);
        return null;
      });
  }

  private void showNotification(@NotNull GradleBuildResult result,
                                @NotNull Map<String, File> buildsAndBundlePaths) {
    AndroidNotification notification = AndroidNotification.getInstance(myProject);
    if (result.isBuildSuccessful()) {
      notifySuccess(notification, buildsAndBundlePaths);
    }
    else if (result.isBuildCancelled()) {
      notification.showBalloon(myNotificationTitle, "Build cancelled.", INFORMATION);
    }
    else {
      String msg = "Errors while building Bundle file. You can find the errors in the 'Messages' view.";
      notification.showBalloon(myNotificationTitle, msg, ERROR);
    }
  }

  private void notifySuccess(@NotNull AndroidNotification notification,
                             @NotNull Map<String, File> bundleBuildsToPath) {
    boolean isSigned = !myBuildVariants.isEmpty();
    StringBuilder builder = new StringBuilder();
    int count = bundleBuildsToPath.size();
    builder.append("App bundle(s) generated successfully for ");
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
      for (Iterator<String> iterator = bundleBuildsToPath.keySet().iterator(); iterator.hasNext(); ) {
        String moduleOrBuildVariant = iterator.next();
        if (isSigned) {
          builder.append("Build variant '");
        }
        else {
          builder.append("Module '");
        }
        builder.append(moduleOrBuildVariant).append("': ");
        builder.append("<a href=\"").append(LOCATE_URL_PREFIX).append(moduleOrBuildVariant).append("\">locate</a> or ");
        builder.append("<a href=\"").append(ANALYZE_URL_PREFIX).append(moduleOrBuildVariant).append("\">analyze</a> the app bundle.");
        if (iterator.hasNext()) {
          builder.append("<br/>");
        }
      }

      if (myExportedKeyFile != null) {
        builder.append("<br/>");
        builder.append("<a href=\"").append(LOCATE_KEY_URL_PREFIX).append("\">Locate</a> exported key file.");
      }

      String text = builder.toString();
      notification.showBalloon(myNotificationTitle, text, INFORMATION, new OpenFolderNotificationListener(myProject,
                                                                                                          bundleBuildsToPath,
                                                                                                          myExportedKeyFile));
    }
    else {
      builder.append(bundleBuildsToPath.entrySet().stream()
                       .map(entry -> String.format(" - %s: %s", entry.getKey(), entry.getValue().getPath()))
                       .collect(Collectors.joining("\n")));
      StringBuilder balloonBuilder = new StringBuilder();
      balloonBuilder.append("App bundle(s) generated successfully for ");
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

  @VisibleForTesting
  boolean isShowFilePathActionSupported() {
    return RevealFileAction.isSupported();
  }

  private static Logger getLog() {
    return Logger.getInstance(GoToBundleLocationTask.class);
  }

  @VisibleForTesting
  static class OpenFolderNotificationListener extends NotificationListener.Adapter {
    @NotNull private final Project myProject;
    @NotNull private final Map<String, File> myBundlePathsPerModule;
    @Nullable private final File myExportedKeyFile;

    OpenFolderNotificationListener(@NotNull Project project,
                                   @NotNull Map<String, File> myBuildsAndBundlePaths,
                                   @Nullable File exportedKeyFile) {
      myProject = project;
      myBundlePathsPerModule = myBuildsAndBundlePaths;
      myExportedKeyFile = exportedKeyFile;
    }

    @Override
    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
      // Safety check
      if (myProject.isDisposed()) {
        return;
      }

      String description = e.getDescription();
      if (description.startsWith(ANALYZE_URL_PREFIX)) {
        openBundleAnalyzer(description.substring(ANALYZE_URL_PREFIX.length()));
      }
      else if (description.startsWith(LOCATE_URL_PREFIX)) {
        openBundleDirectory(description.substring(LOCATE_URL_PREFIX.length()));
      }
      else if (description.startsWith(LOCATE_KEY_URL_PREFIX)) {
        openKeyDirectory();
      }
    }

    private void openBundleAnalyzer(@NotNull String bundlePath) {
      File bundleFile = myBundlePathsPerModule.get(bundlePath);
      if (bundleFile == null) {
        getLog().warn(String.format("Error finding bundle file \"%s\"", bundlePath));
        return;
      }

      VirtualFile virtualFile = !bundleFile.isFile() ? askUserForBundleFile(bundleFile)
                                                     : LocalFileSystem.getInstance().refreshAndFindFileByIoFile(bundleFile);

      if (virtualFile == null) {
        getLog().warn(String.format("Bundle file not found in virtual file system \"%s\"", bundlePath));
        return;
      }

      OpenFileDescriptor fd = new OpenFileDescriptor(myProject, virtualFile);
      List<FileEditor> editors = FileEditorManager.getInstance(myProject).openEditor(fd, true);
      if (editors.isEmpty()) {
        getLog().warn(String.format("Could not open editor for bundle file \"%s\"", bundlePath));
      }
    }

    @Nullable
    VirtualFile askUserForBundleFile(@NotNull File bundleFile) {
      FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
        .withDescription("Select Bundle file to analyze")
        .withFileFilter(file -> SdkConstants.EXT_APP_BUNDLE.equalsIgnoreCase(file.getExtension()));
      return FileChooser.chooseFile(descriptor, myProject, LocalFileSystem.getInstance().findFileByIoFile(bundleFile));
    }

    private void openBundleDirectory(String path) {
      showFileOrDirectory(myBundlePathsPerModule.get(path));
    }

    private void openKeyDirectory() {
      assert myExportedKeyFile != null;
      showFileOrDirectory(myExportedKeyFile);
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

    private static void showFileOrDirectory(@NotNull File file) {
      if (file.isFile()) {
        file = file.getParentFile();
      }
      RevealFileAction.openDirectory(file);
    }
  }

  @VisibleForTesting
  static class OpenEventLogHyperlink extends NotificationHyperlink {
    OpenEventLogHyperlink() {
      super("open.event.log", "Show app bundle path(s) in the '" + ActionCenter.getToolwindowName() + "' view");
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
      GoToBundleLocationTask.OpenEventLogHyperlink other = (GoToBundleLocationTask.OpenEventLogHyperlink)o;
      return getUrl().equals(other.getUrl()) && toHtml().equals(other.toHtml());
    }
  }
}
