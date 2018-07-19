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

import com.android.ide.common.repository.GradleVersion;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.project.sync.hyperlink.FixBuildToolsVersionHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallBuildToolsHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.android.repository.Revision.parseRevision;
import static com.android.sdklib.repository.meta.DetailsTypes.getBuildToolsPath;
import static com.android.tools.idea.gradle.util.GradleUtil.findModuleByGradlePath;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;

public class SdkBuildToolsTooLowErrorHandler extends SyncErrorHandler {
  private static final Pattern SDK_BUILD_TOOLS_TOO_LOW_PATTERN =
    Pattern.compile("The SDK Build Tools revision \\((.*)\\) is too low for project '(.*)'. Minimum required is (.*)");

  @NotNull
  public static SdkBuildToolsTooLowErrorHandler getInstance() {
    for (SyncErrorHandler errorHandler : SyncErrorHandler.getExtensions()) {
      if (errorHandler instanceof SdkBuildToolsTooLowErrorHandler) {
        return (SdkBuildToolsTooLowErrorHandler)errorHandler;
      }
    }
    throw new IllegalStateException("Unable to find an instance of " + SdkBuildToolsTooLowErrorHandler.class.getName());
  }

  @Override
  public boolean handleError(@NotNull ExternalSystemException error, @NotNull NotificationData notification, @NotNull Project project) {
    //noinspection ThrowableResultOfMethodCallIgnored
    String text = getRootCause(error).getMessage();
    List<NotificationHyperlink> hyperlinks = getQuickFixHyperlinks(project, text);
    if (!hyperlinks.isEmpty()) {
      updateUsageTracker();
      GradleSyncMessages.getInstance(project).updateNotification(notification, text, hyperlinks);
      return true;
    }
    return false;
  }

  @NotNull
  private List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull Project project, @NotNull String text) {
    Matcher matcher = SDK_BUILD_TOOLS_TOO_LOW_PATTERN.matcher(getFirstLineMessage(text));
    if (matcher.matches()) {
      String gradlePath = matcher.group(2);
      Module module = findModuleByGradlePath(project, gradlePath);
      String minimumVersion = matcher.group(3);
      VirtualFile file = module == null ? null : getGradleBuildFile(module);
      Map<Module, VirtualFile> buildFileMap = (module != null && file != null) ? ImmutableMap.of(module, file) : ImmutableMap.of();
      List<NotificationHyperlink> links =
        getQuickFixHyperlinks(minimumVersion, file == null ? ImmutableList.of() : ImmutableList.of(module), buildFileMap);
      if (file != null) {
        links.add(new OpenFileHyperlink(file.getPath()));
      }
      return links;
    }
    return Collections.emptyList();
  }

  private static boolean doesAndroidGradlePluginPackageBuildTools(@NotNull List<Module> modules) {
    // All modules should be using the same version of the AGP
    for (Module module : modules) {
      if (AndroidFacet.getInstance(module) == null) {
        continue;
      }
      AndroidPluginInfo pluginInfo = AndroidPluginInfo.find(module.getProject());
      if (pluginInfo != null) {
        GradleVersion agpVersion = pluginInfo.getPluginVersion();
        if (agpVersion != null && !agpVersion.isAtLeast(3, 0, 0)) {
          return false;
        }
      }
    }
    return true;
  }

  @NotNull
  public List<NotificationHyperlink> getQuickFixHyperlinks(@NotNull String minimumVersion,
                                                           @NotNull List<Module> affectedModules,
                                                           @NotNull Map<Module, VirtualFile> buildFileMap) {
    List<NotificationHyperlink> hyperlinks = new ArrayList<>();
    boolean buildToolInstalled = false;

    AndroidSdkHandler sdkHandler = null;
    AndroidSdkData androidSdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk();
    if (androidSdkData != null) {
      sdkHandler = androidSdkData.getSdkHandler();
    }

    if (sdkHandler != null) {
      ProgressIndicator progress = new StudioLoggerProgressIndicator(SdkBuildToolsTooLowErrorHandler.class);
      RepositoryPackages packages = sdkHandler.getSdkManager(progress).getPackages();
      LocalPackage buildTool = packages.getLocalPackages().get(getBuildToolsPath(parseRevision(minimumVersion)));
      buildToolInstalled = buildTool != null;
    }


    List<VirtualFile> buildFiles =
      affectedModules.stream().map(m -> buildFileMap.get(m)).filter(Objects::nonNull).collect(Collectors.toList());

    if (!buildToolInstalled) {
      hyperlinks
        .add(new InstallBuildToolsHyperlink(minimumVersion, buildFiles, doesAndroidGradlePluginPackageBuildTools(affectedModules)));
    }
    else if (!buildFiles.isEmpty()) {
      hyperlinks
        .add(new FixBuildToolsVersionHyperlink(minimumVersion, buildFiles, doesAndroidGradlePluginPackageBuildTools(affectedModules)));
    }

    return hyperlinks;
  }
}