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

import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.android.tools.idea.gradle.service.notification.hyperlink.FixBuildToolsVersionHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.InstallBuildToolsHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.service.notification.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.sdkv2.StudioLoggerProgressIndicator;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SdkBuildToolsTooLowErrorHandler extends AbstractSyncErrorHandler {
  private static final Pattern SDK_BUILD_TOOLS_TOO_LOW_PATTERN =
    Pattern.compile("The SDK Build Tools revision \\((.*)\\) is too low for project '(.*)'. Minimum required is (.*)");

  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String firstLine = message.get(0);

    Matcher matcher = SDK_BUILD_TOOLS_TOO_LOW_PATTERN.matcher(firstLine);
    if (matcher.matches()) {
      boolean buildToolInstalled = false;

      String minimumVersion = matcher.group(3);

      AndroidSdkHandler sdkHandler = null;
      AndroidSdkData androidSdkData = AndroidSdkUtils.tryToChooseAndroidSdk();
      if (androidSdkData != null) {
        sdkHandler = androidSdkData.getSdkHandler();
      }
      if (sdkHandler != null) {
        ProgressIndicator progress = new StudioLoggerProgressIndicator(getClass());
        RepositoryPackages packages = sdkHandler.getSdkManager(progress).getPackages();
        LocalPackage buildTool = packages.getLocalPackages().get(DetailsTypes.getBuildToolsPath(Revision.parseRevision(minimumVersion)));
        buildToolInstalled = buildTool != null;
      }

      String gradlePath = matcher.group(2);
      Module module = GradleUtil.findModuleByGradlePath(project, gradlePath);
      if (module != null) {
        VirtualFile buildFile = GradleUtil.getGradleBuildFile(module);
        List<NotificationHyperlink> hyperlinks = Lists.newArrayList();
        if (!buildToolInstalled) {
          hyperlinks.add(new InstallBuildToolsHyperlink(minimumVersion, buildFile));
        }
        else if (buildFile != null) {
          hyperlinks.add(new FixBuildToolsVersionHyperlink(buildFile, minimumVersion));
        }
        if (buildFile != null) {
          hyperlinks.add(new OpenFileHyperlink(buildFile.getPath()));
        }
        if (!hyperlinks.isEmpty()) {
          updateNotification(notification, project, error.getMessage(), hyperlinks);
          return true;
        }
      }
    }
    return false;
  }
}
