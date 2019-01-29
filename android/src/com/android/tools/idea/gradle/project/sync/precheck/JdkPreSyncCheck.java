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
package com.android.tools.idea.gradle.project.sync.precheck;

import com.android.annotations.Nullable;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.precheck.PreSyncCheckResult.SUCCESS;
import static com.android.tools.idea.gradle.project.sync.precheck.PreSyncCheckResult.failure;
import static com.intellij.openapi.projectRoots.JdkUtil.checkForJdk;
import static com.intellij.pom.java.LanguageLevel.JDK_1_8;

// We only check jdk for Studio, because only Studio uses the same JDK for all modules and all Gradle invocations.
// See https://code.google.com/p/android/issues/detail?id=172714
class JdkPreSyncCheck extends AndroidStudioSyncCheck {
  @Override
  @NotNull
  PreSyncCheckResult doCheckCanSync(@NotNull Project project) {
    Sdk jdk = IdeSdks.getInstance().getJdk();
    String errorMessage = null;
    if (!isValidJdk(jdk)) {
      errorMessage = "Could not run JVM from the selected JDK.\n" +
                     "Please ensure JDK installation is valid and compatible with the current OS (" +
                      SystemInfo.getOsNameAndVersion() + ", " + SystemInfo.OS_ARCH + ").\n" +
                     "If you are using embedded JDK, please make sure to download Android Studio bundle compatible\n" +
                     "with the current OS. For example, for x86 systems please choose a 32 bits download option.";
    }
    else if (!Jdks.getInstance().isApplicableJdk(jdk, JDK_1_8)) {
      errorMessage = "Please use JDK 8 or newer.";
    }

    if (errorMessage != null) {
      SyncMessage syncMessage = new SyncMessage(SyncMessage.DEFAULT_GROUP, MessageType.ERROR, errorMessage);
      List<NotificationHyperlink> quickFixes = Jdks.getInstance().getWrongJdkQuickFixes(project);
      syncMessage.add(quickFixes);

      GradleSyncMessages.getInstance(project).report(syncMessage);

      return failure(errorMessage);
    }

    return SUCCESS;
  }

  private static boolean isValidJdk(@Nullable Sdk jdk) {
    if (jdk == null) {
      return false;
    }
    String jdkHomePath = jdk.getHomePath();
    return jdkHomePath != null && checkForJdk(new File(jdkHomePath)) && Jdks.isJdkRunnableOnPlatform(jdk);
  }
}
