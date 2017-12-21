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
import com.android.tools.idea.gradle.project.sync.hyperlink.NotificationHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessages;
import com.android.tools.idea.sdk.IdeSdks;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JdkVersionDetector;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.gradle.project.sync.hyperlink.OpenProjectStructureHyperlink.openJdkSettings;
import static com.android.tools.idea.gradle.project.sync.hyperlink.StopGradleDaemonsHyperlink.createStopGradleDaemonsHyperlink;
import static com.android.tools.idea.gradle.project.sync.hyperlink.SyncProjectWithExtraCommandLineOptionsHyperlink.syncProjectRefreshingDependencies;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.CLASS_NOT_FOUND;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.METHOD_NOT_FOUND;
import static com.intellij.openapi.projectRoots.JavaSdkVersion.JDK_1_7;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

// See https://code.google.com/p/android/issues/detail?id=73872
public class ClassLoadingErrorHandler extends SyncErrorHandler {
  private static final Pattern CLASS_NOT_FOUND_PATTERN = Pattern.compile("(.+) not found.");

  @Override
  public boolean handleError(@NotNull ExternalSystemException error, @NotNull NotificationData notification, @NotNull Project project) {
    String text = findErrorMessage(getRootCause(error));
    if (text == null) {
      return false;
    }

    String firstLine = getFirstLineMessage(text);
    boolean classNotFound = firstLine.startsWith("Unable to load class");
    NotificationHyperlink openJdkSettingsHyperlink = null;
    NotificationHyperlink syncProjectHyperlink = syncProjectRefreshingDependencies();
    NotificationHyperlink stopDaemonsHyperlink = createStopGradleDaemonsHyperlink();

    boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();

    boolean isJdk7 = false;
    JdkVersionDetector.JdkVersionInfo jdkVersion = null;
    if (unitTestMode) {
      isJdk7 = true;
    }
    else if (classNotFound) {
      Sdk jdk = IdeSdks.getInstance().getJdk();
      if (jdk != null) {
        String jdkHomePath = jdk.getHomePath();
        if (jdkHomePath != null) {
          jdkVersion = SdkVersionUtil.getJdkVersionInfo(jdkHomePath);
        }
        JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
        isJdk7 = version == JDK_1_7;
      }
    }

    String jdk7Hint = "";
    if (isJdk7) {
      jdk7Hint = "<li>";
      if (jdkVersion != null) {
        jdk7Hint += String.format("You are using JDK version '%1$s'. ", jdkVersion.version);
      }
      jdk7Hint += "Some versions of JDK 1.7 (e.g. 1.7.0_10) may cause class loading errors in Gradle.\n" +
                  "Please update to a newer version (e.g. 1.7.0_67).";

      if (!unitTestMode) {
        openJdkSettingsHyperlink = openJdkSettings(project);
        if (openJdkSettingsHyperlink != null) {
          jdk7Hint = jdk7Hint + "\n" + openJdkSettingsHyperlink.toHtml();
        }
      }

      jdk7Hint += "</li>";
    }

    String newMsg = firstLine + "\nPossible causes for this unexpected error include:<ul>" + jdk7Hint +
                    "<li>Gradle's dependency cache may be corrupt (this sometimes occurs after a network connection timeout.)\n" +
                    syncProjectHyperlink.toHtml() + "</li>" +
                    "<li>The state of a Gradle build process (daemon) may be corrupt. Stopping all Gradle daemons may solve this problem.\n" +
                    stopDaemonsHyperlink.toHtml() + "</li>" +
                    "<li>Your project may be using a third-party plugin which is not compatible with the other plugins in the project " +
                    "or the version of Gradle requested by the project.</li></ul>" +
                    "In the case of corrupt Gradle processes, you can also try closing the IDE and then killing all Java processes.";

    notification.setTitle(SyncMessage.DEFAULT_GROUP);
    notification.setMessage(newMsg);
    notification.setNotificationCategory(NotificationCategory.convert(DEFAULT_NOTIFICATION_TYPE));

    List<NotificationHyperlink> hyperlinks = new ArrayList<>();

    if (openJdkSettingsHyperlink != null) {
      hyperlinks.add(openJdkSettingsHyperlink);
    }
    hyperlinks.add(syncProjectHyperlink);
    hyperlinks.add(stopDaemonsHyperlink);

    SyncMessages.getInstance(project).addNotificationListener(notification, hyperlinks);
    return true;
  }

  @Nullable
  private static String findErrorMessage(@NotNull Throwable rootCause) {
    String text = rootCause.getMessage();
    if (rootCause instanceof ClassNotFoundException) {
      String className = nullToEmpty(text);
      Matcher matcher = CLASS_NOT_FOUND_PATTERN.matcher(className);
      if (matcher.matches()) {
        className = matcher.group(1);
      }
      updateUsageTracker(CLASS_NOT_FOUND, className);
      return String.format("Unable to load class '%1$s'.", className);
    }

    if (rootCause instanceof NoSuchMethodError) {
      String methodName = nullToEmpty(text);
      updateUsageTracker(METHOD_NOT_FOUND, methodName);
      return String.format("Unable to find method '%1$s'.", methodName);
    }

    if (isNotEmpty(text) && text.contains("cannot be cast to")) {
      updateUsageTracker();
      return text;
    }
    return null;
  }
}