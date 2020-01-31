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

import static com.android.tools.idea.util.PropertiesFiles.savePropertiesToFile;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_DISTRIBUTIONSHA256SUM_REMOVED_FROM_WRAPPER;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_SHA_256_SUM;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;

public class RemoveSHA256FromGradleWrapperHyperlink extends NotificationHyperlink {
  public RemoveSHA256FromGradleWrapperHyperlink() {
    super("remove.SHA256.from.gradle.wrapper", "Remove " + DISTRIBUTION_SHA_256_SUM + " and sync project");
  }

  @Override
  protected void execute(@NotNull Project project) {
    GradleWrapper gradleWrapper = GradleWrapper.find(project);
    if (gradleWrapper != null) {
      File propertiesFilePath = gradleWrapper.getPropertiesFilePath();
      try {
        Properties properties = gradleWrapper.getProperties();
        if (properties.getProperty(DISTRIBUTION_SHA_256_SUM) != null) {
          // Remove distributionSha256Sum from Gradle wrapper.
          properties.remove(DISTRIBUTION_SHA_256_SUM);
          savePropertiesToFile(properties, propertiesFilePath, null);
          LocalFileSystem.getInstance().refreshIoFiles(ImmutableList.of(propertiesFilePath));
        }
      }
      catch (IOException e) {
        Logger.getInstance(this.getClass()).warn("Failed to read file " + propertiesFilePath.getPath());
      }
    }
    // Invoke Gradle Sync.
    GradleSyncInvoker.getInstance().requestProjectSync(project, TRIGGER_QF_DISTRIBUTIONSHA256SUM_REMOVED_FROM_WRAPPER);
  }
}
