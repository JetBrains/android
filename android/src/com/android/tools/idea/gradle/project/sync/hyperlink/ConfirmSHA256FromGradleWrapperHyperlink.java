/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_DISTRIBUTIONSHA256SUM_CONFIRMED_BY_USER;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.gradle.util.PersistentSHA256Checksums;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConfirmSHA256FromGradleWrapperHyperlink extends NotificationHyperlink {
  @NotNull String myDistributionUrl;
  @NotNull String myDistributionSHA256;

  private ConfirmSHA256FromGradleWrapperHyperlink(@NotNull String distribution, @NotNull String checksum) {
    super("confirm.SHA256.from.gradle.wrapper", generateMessage(distribution, checksum));
    myDistributionUrl = distribution;
    myDistributionSHA256 = checksum;
  }

  private static String generateMessage(@NotNull String distribution, @NotNull String checksum) {
    String shortSHA = checksum.length() <= 9 ? checksum : checksum.substring(0,6) + "...";
    return "Use \"" + shortSHA + "\" as checksum for "+ distribution + " and sync project";
  }

  @Override
  protected void execute(@NotNull Project project) {
    // Add checksum to map of used checksums
    PersistentSHA256Checksums.getInstance().storeChecksum(myDistributionUrl, myDistributionSHA256);

    // Invoke Gradle Sync.
    GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_QF_DISTRIBUTIONSHA256SUM_CONFIRMED_BY_USER);
  }

  public static ConfirmSHA256FromGradleWrapperHyperlink create(@NotNull GradleWrapper wrapper) {
    try {
      return create(wrapper.getDistributionUrl(), wrapper.getDistributionSha256Sum());
    }
    catch (IOException e) {
      return null;
    }
  }

  @VisibleForTesting
  public static ConfirmSHA256FromGradleWrapperHyperlink create(@Nullable String distribution, @Nullable String checksum) {
    if (StringUtil.isEmptyOrSpaces(distribution) || StringUtil.isEmptyOrSpaces(checksum)) {
      return null;
    }
    return new ConfirmSHA256FromGradleWrapperHyperlink(distribution, checksum);
  }
}
