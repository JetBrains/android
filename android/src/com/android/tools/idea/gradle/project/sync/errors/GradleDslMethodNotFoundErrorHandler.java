/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.annotations.Nullable;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.sync.hyperlink.FixAndroidGradlePluginVersionHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenGradleSettingsHyperlink;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder;
import com.android.tools.idea.gradle.util.GradleWrapper;
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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.codeInsight.actions.AddGradleDslPluginAction;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.DSL_METHOD_NOT_FOUND;

public class GradleDslMethodNotFoundErrorHandler extends SyncErrorHandler {
  private static final String GRADLE_DSL_METHOD_NOT_FOUND_ERROR_PREFIX = "Gradle DSL method not found";
  private static final Pattern MISSING_METHOD_PATTERN = Pattern.compile("Could not find method (.*?) .*");

  @Override
  public boolean handleError(@NotNull ExternalSystemException error, @NotNull NotificationData notification, @NotNull Project project) {
    String text = findErrorMessage(getRootCause(error));
    if (text != null) {
      // Handle update notification inside of getQuickFixHyperlinks,
      // because it uses different interfaces based on conditions
      getQuickFixHyperlinks(notification, project, text);
      return true;
    }
    return false;
  }

  @Nullable
  private static String findErrorMessage(@NotNull Throwable rootCause) {
    String errorType = rootCause.getClass().getName();
    if (errorType.equals("org.gradle.api.internal.MissingMethodException") ||
        errorType.equals("org.gradle.internal.metaobject.AbstractDynamicObject$CustomMessageMissingMethodException")) {
      String method = parseMissingMethod(rootCause.getMessage());
      updateUsageTracker(DSL_METHOD_NOT_FOUND, method);
      return GRADLE_DSL_METHOD_NOT_FOUND_ERROR_PREFIX + ": '" + method + "'";
    }
    return null;
  }

  @NotNull
  private static String parseMissingMethod(@NotNull String rootCauseText) {
    Matcher matcher = MISSING_METHOD_PATTERN.matcher(rootCauseText);
    return matcher.find() ? matcher.group(1) : "";
  }

  private static void getQuickFixHyperlinks(@NotNull NotificationData notification, @NotNull Project project, @NotNull String text) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    String filePath = notification.getFilePath();
    VirtualFile file = filePath != null ? LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath) : null;
    if (file != null && FN_BUILD_GRADLE.equals(file.getName())) {
      updateNotificationWithBuildFile(project, file, notification, text);
      return;
    }
    if (file != null && notification.getLine() > 0 && notification.getNavigatable() == null) {
      OpenFileHyperlink hyperlink = new OpenFileHyperlink(filePath, notification.getLine() - 1 /* lines are zero-based */);
      hyperlinks.add(hyperlink);
    }
    GradleSyncMessages.getInstance(project).updateNotification(notification, text, hyperlinks);
  }

  private static void updateNotificationWithBuildFile(@NotNull Project project,
                                                      @NotNull VirtualFile virtualFile,
                                                      @NotNull NotificationData notification,
                                                      @NotNull String text) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    NotificationHyperlink gradleSettingsHyperlink = getGradleSettingsHyperlink(project);
    NotificationHyperlink applyGradlePluginHyperlink = getApplyGradlePluginHyperlink(virtualFile, notification);
    NotificationHyperlink upgradeAndroidPluginHyperlink = new FixAndroidGradlePluginVersionHyperlink();

    String newMsg = text + "\nPossible causes:<ul>";
    if (!gradleModelIsRecent(project)) {
      newMsg = newMsg +
               String.format("<li>The project '%1$s' may be using a version of the Android Gradle plug-in that does" +
                             " not contain the method (e.g. 'testCompile' was added in 1.1.0).\n", project.getName()) +
               upgradeAndroidPluginHyperlink.toHtml() +
               "</li>";
    }
    newMsg = newMsg +
             String.format("<li>The project '%1$s' may be using a version of Gradle that does not contain the method.\n",
                           project.getName()) + gradleSettingsHyperlink.toHtml() + "</li>" +
             "<li>The build file may be missing a Gradle plugin.\n" + applyGradlePluginHyperlink.toHtml() + "</li>";
    notification.setTitle(SyncMessage.DEFAULT_GROUP);
    notification.setMessage(newMsg);
    notification.setNotificationCategory(NotificationCategory.convert(DEFAULT_NOTIFICATION_TYPE));

    hyperlinks.add(gradleSettingsHyperlink);
    hyperlinks.add(applyGradlePluginHyperlink);
    hyperlinks.add(upgradeAndroidPluginHyperlink);

    GradleSyncMessages.getInstance(project).addNotificationListener(notification, hyperlinks);
  }

  @NotNull
  private static NotificationHyperlink getGradleSettingsHyperlink(@NotNull Project project) {
    if (isUsingWrapper(project)) {
      GradleWrapper gradleWrapper = GradleWrapper.find(project);
      if (gradleWrapper != null) {
        VirtualFile propertiesFile = gradleWrapper.getPropertiesFile();
        if (propertiesFile != null) {
          return new NotificationHyperlink("open.wrapper.file", "Open Gradle wrapper file") {
            @Override
            protected void execute(@NotNull Project project) {
              OpenFileDescriptor descriptor = new OpenFileDescriptor(project, propertiesFile);
              FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
            }
          };
        }
      }
    }
    return new OpenGradleSettingsHyperlink();
  }

  private static boolean gradleModelIsRecent(@NotNull Project project) {
    // Sync has failed, so we can only check the build file.
    AndroidPluginInfo androidPluginInfo = AndroidPluginInfo.searchInBuildFilesOnly(project);
    if (androidPluginInfo != null) {
      GradleVersion pluginVersion = androidPluginInfo.getPluginVersion();
      if (pluginVersion != null) {
        AndroidPluginGeneration pluginGeneration = androidPluginInfo.getPluginGeneration();
        return pluginVersion.compareTo(pluginGeneration.getLatestKnownVersion()) > 0;
      }
    }
    return false;
  }

  private static boolean isUsingWrapper(@NotNull Project project) {
    GradleProjectSettings gradleSettings = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project);
    GradleWrapper gradleWrapper = GradleWrapper.find(project);
    DistributionType distributionType = gradleSettings != null ? gradleSettings.getDistributionType() : null;
    return (distributionType == null || distributionType == DistributionType.DEFAULT_WRAPPED) && gradleWrapper != null;
  }

  @NotNull
  private static NotificationHyperlink getApplyGradlePluginHyperlink(@NotNull final VirtualFile virtualFile,
                                                                     @NotNull final NotificationData notification) {
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