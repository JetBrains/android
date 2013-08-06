/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.GradleModelConstants;
import com.google.common.base.Splitter;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.find.FindSettings;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationExtension;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.AdapterProcessor;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.event.HyperlinkEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradleNotificationExtension implements ExternalSystemNotificationExtension {
  private static final Pattern ERROR_LOCATION_IN_FILE_PATTERN = Pattern.compile("Build file '(.*)' line: ([\\d]+)");
  private static final Pattern ERROR_IN_FILE_PATTERN = Pattern.compile("Build file '(.*)'");

  @NotNull
  @Override
  public ProjectSystemId getTargetExternalSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  @Nullable
  @Override
  public CustomizationResult customize(@NotNull Project project, @NotNull Throwable error, @Nullable UsageHint hint) {
    Throwable cause = error;
    if (error instanceof UndeclaredThrowableException) {
      cause = ((UndeclaredThrowableException)error).getUndeclaredThrowable();
      if (cause instanceof InvocationTargetException) {
        cause = ((InvocationTargetException)cause).getTargetException();
      }
    }
    if (cause instanceof ExternalSystemException) {
      return createNotification(project, (ExternalSystemException)cause);
    }
    return null;
  }

  @Nullable
  private static CustomizationResult createNotification(@NotNull Project project, @NotNull ExternalSystemException error) {
    String msg = error.getMessage();
    if (msg != null) {
      if (msg.startsWith("Project is using an old version of the Android Gradle plug-in")) {
        return createUpdateGradlePluginVersionNotification(project);
      }
      Iterable<String> lines = Splitter.on('\n').split(msg);
      String lastLine = ContainerUtil.iterateAndGetLastItem(lines);
      if (lastLine != null) {
        if (lastLine.contains("install") && lastLine.contains("from the Android SDK Manager")) {
          return createOpenAndroidSdkNotification(project, msg);
        }
        Matcher matcher = ERROR_LOCATION_IN_FILE_PATTERN.matcher(lastLine);
        if (matcher.matches()) {
          String filePath = matcher.group(1);
          int line = -1;
          try {
            line = Integer.parseInt(matcher.group(2));
          }
          catch (NumberFormatException e) {
            // ignored.
          }
          return createGoToFileNotification(project, msg, filePath, line);
        }
        matcher = ERROR_IN_FILE_PATTERN.matcher(lastLine);
        if (matcher.matches()) {
          String filePath = matcher.group(1);
          return createGoToFileNotification(project, msg, filePath, -1);
        }
      }
    }
    return null;
  }

  @NotNull
  private static CustomizationResult createUpdateGradlePluginVersionNotification(@NotNull final Project project) {
    NotificationListener notificationListener = new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
          return;
        }
        searchGradleModelInBuildFiles(project);
      }
    };
    String title = createNotificationTitle(project);
    String messageToShow =
      GradleModelConstants.UNSUPPORTED_MODEL_VERSION_HTML_ERROR + "<br><br><a href=\"search\">Search in build.gradle files</a>";
    return new CustomizationResult(title, messageToShow, NotificationType.ERROR, notificationListener);
  }

  private static void searchGradleModelInBuildFiles(@NotNull final Project project) {
    FindManager findManager = FindManager.getInstance(project);
    UsageViewManager usageViewManager = UsageViewManager.getInstance(project);

    FindModel findModel = (FindModel) findManager.getFindInProjectModel().clone();
    findModel.setStringToFind(GradleModelConstants.ANDROID_GRADLE_MODEL_DEPENDENCY_NAME);
    findModel.setReplaceState(false);
    findModel.setOpenInNewTabVisible(true);
    findModel.setOpenInNewTabEnabled(true);
    findModel.setOpenInNewTab(true);
    findModel.setFileFilter(SdkConstants.FN_BUILD_GRADLE);

    findManager.getFindInProjectModel().copyFrom(findModel);
    final FindModel findModelCopy = (FindModel)findModel.clone();

    UsageViewPresentation presentation = FindInProjectUtil.setupViewPresentation(findModel.isOpenInNewTabEnabled(), findModelCopy);
    boolean showPanelIfOnlyOneUsage = !FindSettings.getInstance().isSkipResultsWithOneUsage();
    final FindUsagesProcessPresentation processPresentation =
      FindInProjectUtil.setupProcessPresentation(project, showPanelIfOnlyOneUsage, presentation);
    UsageTarget usageTarget = new FindInProjectUtil.StringUsageTarget(findModel.getStringToFind());
    usageViewManager.searchAndShowUsages(new UsageTarget[] { usageTarget }, new Factory<UsageSearcher>() {
      @Override
      public UsageSearcher create() {
        return new UsageSearcher() {
          @Override
          public void generate(@NotNull final Processor<Usage> processor) {
            AdapterProcessor<UsageInfo, Usage> consumer = new AdapterProcessor<UsageInfo, Usage>(processor, UsageInfo2UsageAdapter.CONVERTER);
            //noinspection ConstantConditions
            FindInProjectUtil.findUsages(findModelCopy, null, project, true, consumer, processPresentation);
          }
        };
      }
    }, processPresentation, presentation, null);
  }

  @NotNull
  private static CustomizationResult createOpenAndroidSdkNotification(@NotNull final Project project, @NotNull final String errorMsg) {
    String messageToShow = errorMsg;
    NotificationListener notificationListener = null;

    List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    if (!facets.isEmpty()) {
      // We can only open SDK manager if the project has an Android facet because the facet stores the path of the SDK manager.
      notificationListener = new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
            return;
          }
          RunAndroidSdkManagerAction action = new RunAndroidSdkManagerAction();
          action.doAction(project);
        }
      };
      messageToShow = errorMsg + "<br><a href=\"openSdk\">Open Android SDK</a>";
    }

    String title = createNotificationTitle(project);
    return new CustomizationResult(title, messageToShow, NotificationType.ERROR, notificationListener);
  }

  @NotNull
  private static CustomizationResult createGoToFileNotification(@NotNull final Project project,
                                                                @NotNull final String errorMsg,
                                                                @NotNull final String filePath,
                                                                final int line) {
    NotificationListener notificationListener = new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
          return;
        }
        VirtualFile projectFile = project.getProjectFile();
        if (projectFile == null) {
          // This is the default project. This will NEVER happen.
          return;
        }
        VirtualFile file = projectFile.getParent().getFileSystem().findFileByPath(filePath);
        if (file != null) {
          Navigatable openFile = new OpenFileDescriptor(project, file, line, -1, false);
          if (openFile.canNavigate()) {
            openFile.navigate(true);
          }
        }
      }
    };
    String title = createNotificationTitle(project);
    String messageToShow = errorMsg + "<br><a href=\"openFile\">Open file</a>";
    return new CustomizationResult(title, messageToShow, NotificationType.ERROR, notificationListener);
  }

  @NotNull
  private static String createNotificationTitle(@NotNull Project project) {
    return String.format("Failed to refresh Gradle project '%1$s':", project.getName());
  }
}
