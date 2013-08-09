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
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.AdapterProcessor;
import com.intellij.util.Processor;
import com.intellij.util.SystemProperties;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradleNotificationExtension implements ExternalSystemNotificationExtension {
  private static final Pattern ERROR_LOCATION_IN_FILE_PATTERN = Pattern.compile("Build file '(.*)' line: ([\\d]+)");
  private static final Pattern ERROR_IN_FILE_PATTERN = Pattern.compile("Build file '(.*)'");
  private static final Pattern MISSING_DEPENDENCY_PATTERN = Pattern.compile("Could not find (.*)\\.");

  private static final NotificationType DEFAULT_NOTIFICATION_TYPE = NotificationType.ERROR;

  @NonNls private static final String OPEN_FILE_URL = "openFile";
  @NonNls private static final String SEARCH_IN_BUILD_FILES_URL = "searchInBuildFiles";

  @NonNls private static final String OPEN_FILE_LINK_TEXT = String.format("<a href=\"%1$s\">Open file</a>", OPEN_FILE_URL);
  @NonNls private static final String SEARCH_IN_BUILD_FILES_LINK_TEXT =
    String.format("<a href=\"%1$s\">Search in build.gradle files</a>", SEARCH_IN_BUILD_FILES_URL);

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
    if (msg != null && !msg.isEmpty()) {
      if (msg.startsWith("Project is using an old version of the Android Gradle plug-in")) {
        return createSearchInBuildFilesNotification(project, GradleModelConstants.UNSUPPORTED_MODEL_VERSION_HTML_ERROR,
                                                    GradleModelConstants.ANDROID_GRADLE_MODEL_DEPENDENCY_NAME);
      }

      if (msg.contains("failed to parse SDK")) {
        return createAddonsFolderMissingInSdkNotification(project, msg);
      }

      List<String> lines = splitLines(msg);
      String firstLine = lines.get(0);
      String lastLine = lines.get(lines.size() - 1);

      Matcher matcher = MISSING_DEPENDENCY_PATTERN.matcher(firstLine);
      if (matcher.matches() && lines.size() > 1 && lines.get(1).startsWith("Required by:")) {
        String dependency = matcher.group(1);
        if (!Strings.isNullOrEmpty(dependency)) {
          if (lastLine != null) {
            Pair<String, Integer> errorLocation = getErrorLocation(lastLine);
            if (errorLocation != null) {
              return createGoToFileAndSearchInBuildFilesNotification(project, msg, dependency, errorLocation.getFirst(),
                                                                     errorLocation.getSecond());
            }
          }
          return createSearchInBuildFilesNotification(project, msg, dependency);
        }
      }

      if (lastLine != null) {
        if (lastLine.contains("install") && lastLine.contains("from the Android SDK Manager")) {
          return createOpenAndroidSdkNotification(project, msg);
        }

        Pair<String, Integer> errorLocation = getErrorLocation(lastLine);
        if (errorLocation != null) {
          return createGoToFileNotification(project, msg, errorLocation.getFirst(), errorLocation.getSecond());
        }
      }
    }
    return null;
  }

  @Nullable
  private static Pair<String, Integer> getErrorLocation(@NotNull String msg) {
    Matcher matcher = ERROR_LOCATION_IN_FILE_PATTERN.matcher(msg);
    if (matcher.matches()) {
      String filePath = matcher.group(1);
      int line = -1;
      try {
        line = Integer.parseInt(matcher.group(2));
      }
      catch (NumberFormatException e) {
        // ignored.
      }
      return Pair.create(filePath, line);
    }

    matcher = ERROR_IN_FILE_PATTERN.matcher(msg);
    if (matcher.matches()) {
      String filePath = matcher.group(1);
      return Pair.create(filePath, -1);
    }
    return null;
  }

  private static CustomizationResult createAddonsFolderMissingInSdkNotification(@NotNull Project project, @NotNull String errorMsg) {
    String pathOfBrokenSdk = findPathOfSdkMissingOrEmptyAddonsFolder(project);
    String msg;
    if (pathOfBrokenSdk != null) {
      msg = String.format("The directory '%1$s', in the Android SDK at '%2$s', is either missing or empty", SdkConstants.FD_ADDONS,
                          pathOfBrokenSdk);
      File sdkHomeDir = new File(pathOfBrokenSdk);
      if (!sdkHomeDir.canWrite()) {
        msg += String.format("\n\nCurrent user (%1$s) does not have write access to the SDK directory.", SystemProperties.getUserName());
      }
    } else {
      msg = splitLines(errorMsg).iterator().next();
    }
    return new CustomizationResult(createNotificationTitle(project, msg), "", DEFAULT_NOTIFICATION_TYPE, null);
  }

  @Nullable
  private static String findPathOfSdkMissingOrEmptyAddonsFolder(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      Sdk moduleSdk = ModuleRootManager.getInstance(module).getSdk();
      if (moduleSdk != null && moduleSdk.getSdkType().equals(AndroidSdkType.getInstance())) {
        String sdkHomeDirPath = moduleSdk.getHomePath();
        File addonsDir = new File(sdkHomeDirPath, SdkConstants.FD_ADDONS);
        if (!addonsDir.isDirectory() || FileUtil.notNullize(addonsDir.listFiles()).length == 0) {
          return sdkHomeDirPath;
        }
      }
    }
    return null;
  }

  @NotNull
  private static List<String> splitLines(@NotNull String s) {
    return Lists.newArrayList(Splitter.on('\n').split(s));
  }

  @NotNull
  private static CustomizationResult createSearchInBuildFilesNotification(@NotNull final Project project,
                                                                          @NotNull final String errorMsg,
                                                                          @NotNull final String textToSearch) {
    NotificationListener notificationListener = new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
          return;
        }
        searchInBuildFiles(project, textToSearch);
      }
    };
    String title = createNotificationTitle(project, errorMsg);
    return new CustomizationResult(title, SEARCH_IN_BUILD_FILES_LINK_TEXT, DEFAULT_NOTIFICATION_TYPE, notificationListener);
  }

  @NotNull
  private static CustomizationResult createOpenAndroidSdkNotification(@NotNull final Project project, @NotNull final String errorMsg) {
    String msg = "";
    NotificationListener notificationListener = null;

    List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    if (!facets.isEmpty()) {
      // We can only open SDK manager if the project has an Android facet has a reference to the Android SDK manager.
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
      msg = "<a href=\"openSdk\">Open Android SDK</a>";
    }

    String title = createNotificationTitle(project, errorMsg);
    return new CustomizationResult(title, msg, DEFAULT_NOTIFICATION_TYPE, notificationListener);
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
        openFile(project, filePath, line);
      }
    };
    String title = createNotificationTitle(project, errorMsg);
    return new CustomizationResult(title, OPEN_FILE_LINK_TEXT, DEFAULT_NOTIFICATION_TYPE, notificationListener);
  }

  @NotNull
  private static CustomizationResult createGoToFileAndSearchInBuildFilesNotification(@NotNull final Project project,
                                                                                     @NotNull final String errorMsg,
                                                                                     @NotNull final String textToFind,
                                                                                     @NotNull final String filePath,
                                                                                     final int line) {
    NotificationListener notificationListener = new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
          return;
        }
        if (OPEN_FILE_URL.equals(event.getDescription())) {
          openFile(project, filePath, line);
        } else {
          searchInBuildFiles(project, textToFind);
        }
      }
    };
    String title = createNotificationTitle(project, errorMsg);
    String msg = OPEN_FILE_LINK_TEXT + " " + SEARCH_IN_BUILD_FILES_LINK_TEXT;
    return new CustomizationResult(title, msg, DEFAULT_NOTIFICATION_TYPE, notificationListener);
  }

  private static void openFile(@NotNull Project project, @NotNull String filePath, int line) {
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

  private static void searchInBuildFiles(@NotNull final Project project, @NotNull String textToFind) {
    FindManager findManager = FindManager.getInstance(project);
    UsageViewManager usageViewManager = UsageViewManager.getInstance(project);

    FindModel findModel = (FindModel) findManager.getFindInProjectModel().clone();
    findModel.setStringToFind(textToFind);
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
  private static String createNotificationTitle(@NotNull Project project, @NotNull String msg) {
    return String.format("Failed to refresh Gradle project '%1$s':\n", project.getName()) + msg;
  }
}
