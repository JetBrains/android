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
package com.android.tools.idea.gradle.service.notification.errors;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.ProjectImportErrorHandler;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenGradleSettingsHyperlink;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.commands.ActionCommand;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.codeInsight.actions.AddGradleDslPluginAction;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.io.File;
import java.util.List;

public class GradleDslMethodNotFoundErrorHandler extends AbstractSyncErrorHandler {
  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String firstLine = message.get(0);
    NotificationHyperlink gradleSettingsHyperlink = getGradleSettingsHyperlink(project);
    NotificationHyperlink applyGradlePluginHyperlink = getApplyGradlePluginHyperlink(notification);

    if (firstLine != null && firstLine.startsWith(ProjectImportErrorHandler.GRADLE_DSL_METHOD_NOT_FOUND_ERROR_PREFIX)) {
      String newMsg = firstLine + "\nPossible causes:<ul>" +
                      String.format("<li>The project '%1$s' may be using a version of Gradle that does not contain the method.\n",
                                    project.getName()) +
                      gradleSettingsHyperlink.toHtml() + "</li>"  +
                      "<li>The build file may be missing a Gradle plugin." +
                      (applyGradlePluginHyperlink != null ? "\n" + applyGradlePluginHyperlink.toHtml() : "") + "</li>";
      String title = String.format(FAILED_TO_SYNC_GRADLE_PROJECT_ERROR_GROUP_FORMAT, project.getName());
      notification.setTitle(title);
      notification.setMessage(newMsg);
      notification.setNotificationCategory(NotificationCategory.convert(DEFAULT_NOTIFICATION_TYPE));

      if (applyGradlePluginHyperlink != null) {
        addNotificationListener(notification, project, gradleSettingsHyperlink, applyGradlePluginHyperlink);
      }
      else {
        addNotificationListener(notification, project, gradleSettingsHyperlink);
      }
      return true;
    }
    return false;
  }

  @NotNull
  private static NotificationHyperlink getGradleSettingsHyperlink(@NotNull Project project) {
    if (isUsingWrapper(project)) {
      File wrapperPropertiesFile = GradleUtil.findWrapperPropertiesFile(project);
      if (wrapperPropertiesFile != null) {
        final VirtualFile virtualFile = VfsUtil.findFileByIoFile(wrapperPropertiesFile, true);
        if (virtualFile != null) {
          return new NotificationHyperlink("open.wrapper.file", "Open Gradle wrapper file") {
            @Override
            protected void execute(@NotNull Project project) {
              OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile);
              FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
            }
          };
        }
      }
    }
    return new OpenGradleSettingsHyperlink();
  }

  private static boolean isUsingWrapper(@NotNull Project project) {
    GradleProjectSettings gradleSettings = GradleUtil.getGradleProjectSettings(project);
    File wrapperPropertiesFile = GradleUtil.findWrapperPropertiesFile(project);

    DistributionType distributionType = gradleSettings != null ? gradleSettings.getDistributionType() : null;

    return (distributionType == null || distributionType == DistributionType.DEFAULT_WRAPPED) && wrapperPropertiesFile != null;
  }

  @Nullable
  private static NotificationHyperlink getApplyGradlePluginHyperlink(@NotNull final NotificationData notification) {
    String filePath = notification.getFilePath();
    final VirtualFile virtualFile = filePath != null ? LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath) : null;
    if (virtualFile == null || !SdkConstants.FN_BUILD_GRADLE.equals(virtualFile.getName())) {
      return null;
    }
    return new NotificationHyperlink("apply.gradle.plugin", "Apply Gradle plugin") {
      @Override
      protected void execute(@NotNull Project project) {
        openFile(virtualFile, notification, project);

        ActionManager actionManager = ActionManager.getInstance();
        String actionId = AddGradleDslPluginAction.ID;
        AnAction action = actionManager.getAction(actionId);
        assert action instanceof AddGradleDslPluginAction;
        AddGradleDslPluginAction addPluginAction = (AddGradleDslPluginAction)action;
        actionManager.tryToExecute(addPluginAction, ActionCommand.getInputEvent(actionId), null, ActionPlaces.UNKNOWN, true);
      }
    };
  }

  private static void openFile(@NotNull VirtualFile virtualFile, @NotNull NotificationData notification, @NotNull Project project) {
    int line = notification.getLine() - 1;
    int column = notification.getColumn() - 1;

    line = line < 0 ? -1 : line; // NotificationData uses 1-based offsets, while OpenFileDescriptor 0-based.
    column = column < 0 ? -1 : column + 1;
    new OpenFileDescriptor(project, virtualFile, line, column).navigate(true);
  }
}
