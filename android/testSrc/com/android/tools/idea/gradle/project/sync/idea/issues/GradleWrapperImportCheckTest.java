/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea.issues;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.util.PropertiesFiles.savePropertiesToFile;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_SHA_256_SUM;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_URL_PROPERTY;

import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.vfs.LocalFileSystem;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleWrapperImportCheckTest extends AndroidGradleTestCase {
  private static String SHA256 = "0986244820e4a35d32d91df2ec4b768b5ba5d6c8246753794f85159f9963ec12";
  private static String REMOTE_DISTRIBUTION = "https://services.gradle.org/distributions/gradle-5.6.2-bin.zip";
  private static String LOCAL_DISTRIBUTION = "file:///tmp/gradle-5.6.2-bin.zip";

  public void testDoCheckNoWrapper() {
    GradleWrapperImportCheck.validateGradleWrapper(getProject().getBasePath());
  }

  public void testDoCheckNoCheckSumRemote() throws IOException {
    verifyDoCheck(REMOTE_DISTRIBUTION, null, true);
  }

  public void testDoCheckNoCheckSumLocal() throws IOException {
    verifyDoCheck(LOCAL_DISTRIBUTION, null, true);
  }

  public void testDoCheckWithCheckSumRemote() throws IOException {
    verifyDoCheck(REMOTE_DISTRIBUTION, SHA256, false);
  }

  public void testDoCheckWithCheckSumLocal() throws IOException {
    verifyDoCheck(LOCAL_DISTRIBUTION, SHA256, true);
  }

  private void verifyDoCheck(@NotNull String distribution, @Nullable String checksum, boolean successful) throws IOException {
    GradleWrapper gradleWrapper = GradleWrapper.create(getBaseDirPath(getProject()));
    assertNotNull(gradleWrapper);
    File propertiesFilePath = gradleWrapper.getPropertiesFilePath();
    Properties properties = gradleWrapper.getProperties();
    assertNotNull(properties);

    // Add distribution to Gradle wrapper.
    properties.setProperty(DISTRIBUTION_URL_PROPERTY, distribution);
    if (checksum != null) {
      properties.setProperty(DISTRIBUTION_SHA_256_SUM, checksum);
    }
    savePropertiesToFile(properties, propertiesFilePath, null);
    LocalFileSystem.getInstance().refreshIoFiles(ImmutableList.of(propertiesFilePath));

    try {
      GradleWrapperImportCheck.validateGradleWrapper(getProject().getBasePath());
      if (!successful) {
        fail();
      }
    } catch (InvalidGradleWrapperException e) {
      if (successful) {
        fail();
      }
      // Exception expected
    }
  }
}