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

import static com.android.tools.idea.gradle.project.sync.precheck.PreSyncCheckResult.SUCCESS;
import static com.android.tools.idea.gradle.project.sync.precheck.PreSyncCheckResult.failure;
import static com.android.tools.idea.sdk.IdeSdks.isJdkSameVersion;
import static com.intellij.openapi.projectRoots.JdkUtil.checkForJdk;

import com.android.annotations.Nullable;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.project.messages.MessageType;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import java.io.File;
import java.util.List;
import org.jetbrains.annotations.NotNull;

// Step to check if the selected Jdk is valid.
// Verify the Jdk in the following ways,
// 1. Jdk location has been set and has a valid Jdk home directory.
// 2. The selected Jdk has the same version with IDE, this is to avoid serialization problems.
// 3. The Jdk installation is complete, i.e. the has java executable, runtime and etc.
// 4. The selected Jdk is compatible with current platform.
class JdkPreSyncCheck extends AndroidStudioSyncCheck {
  @Override
  @NotNull
  PreSyncCheckResult doCheckCanSyncAndTryToFix(@NotNull Project project) {
    String errorMessage = getErrorMessage(IdeSdks.getInstance().getJdk());
    if (errorMessage != null) {
      SyncMessage syncMessage = new SyncMessage(SyncMessage.DEFAULT_GROUP, MessageType.ERROR, errorMessage);
      List<NotificationHyperlink> quickFixes = Jdks.getInstance().getWrongJdkQuickFixes(project);
      syncMessage.add(quickFixes);

      GradleSyncMessages.getInstance(project).report(syncMessage);

      return failure("Invalid Jdk");
    }
    return SUCCESS;
  }

  @Nullable
  private static String getErrorMessage(@Nullable Sdk jdk) {
    // Jdk location is not set.
    if (jdk == null) {
      return "Jdk location is not set.";
    }
    String jdkHomePath = jdk.getHomePath();
    if (jdkHomePath == null) {
      return "Could not find valid Jdk home from the selected Jdk location.";
    }

    String selectedJdkMsg = "Selected Jdk location is " + jdkHomePath + ".\n";

    // Check if the version of selected Jdk is the same with the Jdk IDE uses.
    JavaSdkVersion runningJdkVersion = IdeSdks.getInstance().getRunningVersionOrDefault();
    if (!isJdkSameVersion(new File(jdkHomePath), runningJdkVersion)) {
      return "The version of selected Jdk doesn't match the Jdk used by Studio. Please choose a valid Jdk " +
             runningJdkVersion.getDescription() + " directory.\n" + selectedJdkMsg;
    }

    // Check Jdk installation is complete.
    if (!checkForJdk(jdkHomePath)) {
      return "The Jdk installation is invalid.\n" + selectedJdkMsg;
    }

    // Check if the Jdk is compatible with platform.
    if (!Jdks.isJdkRunnableOnPlatform(jdk)) {
      return "The selected Jdk could not run on current OS.\n" +
             "If you are using embedded Jdk, please make sure to download Android Studio bundle compatible\n" +
             "with the current OS. For example, for x86 systems please choose a 32 bits download option.\n" +
             selectedJdkMsg;
    }

    return null;
  }
}
