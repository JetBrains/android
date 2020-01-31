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
import static com.google.common.truth.Truth.assertThat;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_SHA_256_SUM;
import static org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_URL_PROPERTY;

import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.gradle.util.PersistentSHA256Checksums;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import java.io.File;
import java.util.Properties;

/**
 * Tests for {@link ConfirmSHA256FromGradleWrapperHyperlink}
 */
public class ConfirmSHA256FromGradleWrapperHyperlinkTest extends AndroidGradleTestCase {
  private static String DISTRIBUTION = "https://services.gradle.org/distributions/gradle-5.6.2-bin.zip";
  private static String SHA256 = "0986244820e4a35d32d91df2ec4b768b5ba5d6c8246753794f85159f9963ec12";
  private static String EXPECTED_HTML = "<a href=\"confirm.SHA256.from.gradle.wrapper\">Use \"098624...\" as checksum for " +
                        "https://services.gradle.org/distributions/gradle-5.6.2-bin.zip and sync project</a>";

  public void testCreate() {
    assertThat(ConfirmSHA256FromGradleWrapperHyperlink.create(null, null)).isNull();
    assertThat(ConfirmSHA256FromGradleWrapperHyperlink.create(null, " ")).isNull();
    assertThat(ConfirmSHA256FromGradleWrapperHyperlink.create(null, SHA256)).isNull();
    assertThat(ConfirmSHA256FromGradleWrapperHyperlink.create(" ", null)).isNull();
    assertThat(ConfirmSHA256FromGradleWrapperHyperlink.create(" ", " ")).isNull();
    assertThat(ConfirmSHA256FromGradleWrapperHyperlink.create(" ", SHA256)).isNull();
    assertThat(ConfirmSHA256FromGradleWrapperHyperlink.create(DISTRIBUTION, null)).isNull();
    assertThat(ConfirmSHA256FromGradleWrapperHyperlink.create(DISTRIBUTION, " ")).isNull();

    ConfirmSHA256FromGradleWrapperHyperlink hyperlink = ConfirmSHA256FromGradleWrapperHyperlink.create(DISTRIBUTION, SHA256);
    assertThat(hyperlink).isNotNull();
    assertThat(hyperlink.toHtml()).isEqualTo(EXPECTED_HTML);
  }

  public void testExecute() throws Exception {
    loadSimpleApplication();
    Project project = getProject();

    GradleWrapper gradleWrapper = GradleWrapper.find(project);
    assertNotNull(gradleWrapper);
    File propertiesFilePath = gradleWrapper.getPropertiesFilePath();
    Properties properties = gradleWrapper.getProperties();
    assertNotNull(properties);

    // Add distribution to Gradle wrapper.
    properties.setProperty(DISTRIBUTION_SHA_256_SUM, SHA256);
    properties.setProperty(DISTRIBUTION_URL_PROPERTY, DISTRIBUTION);
    savePropertiesToFile(properties, propertiesFilePath, null);
    LocalFileSystem.getInstance().refreshIoFiles(ImmutableList.of(propertiesFilePath));

    gradleWrapper = GradleWrapper.find(project);
    assertNotNull(gradleWrapper);
    properties = gradleWrapper.getProperties();
    assertNotNull(properties);

    // Verify that distribution is added to Gradle wrapper.
    assertThat(properties.getProperty(DISTRIBUTION_URL_PROPERTY, null)).isEqualTo(DISTRIBUTION);
    assertThat(properties.getProperty(DISTRIBUTION_SHA_256_SUM, null)).isEqualTo(SHA256);

    // Verify hyperlink is created correctly
    ConfirmSHA256FromGradleWrapperHyperlink hyperlink = ConfirmSHA256FromGradleWrapperHyperlink.create(DISTRIBUTION, SHA256);
    assertThat(hyperlink).isNotNull();
    assertThat(hyperlink.toHtml()).isEqualTo(EXPECTED_HTML);

    PersistentSHA256Checksums persistentSHA256 = new PersistentSHA256Checksums();
    new IdeComponents(getProject()).replaceApplicationService(PersistentSHA256Checksums.class, persistentSHA256);
    hyperlink.execute(project);
    assertThat(persistentSHA256.myStoredChecksums).hasSize(1);
    assertThat(persistentSHA256.myStoredChecksums).containsEntry(DISTRIBUTION, SHA256);
  }
}
