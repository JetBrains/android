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

import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import java.io.File;
import java.util.Properties;

/**
 * Tests for {@link RemoveSHA256FromGradleWrapperHyperlink}.
 */
public class RemoveSHA256FromGradleWrapperHyperlinkTest extends AndroidGradleTestCase {

  public void testRemoveSHA256() throws Exception {
    loadSimpleApplication();
    Project project = getProject();

    GradleWrapper gradleWrapper = GradleWrapper.find(project);
    assertNotNull(gradleWrapper);
    File propertiesFilePath = gradleWrapper.getPropertiesFilePath();
    Properties properties = gradleWrapper.getProperties();
    assertNotNull(properties);

    // Add distributionSha256Sum to Gradle wrapper.
    properties.setProperty(DISTRIBUTION_SHA_256_SUM, "sha256");
    savePropertiesToFile(properties, propertiesFilePath, null);
    LocalFileSystem.getInstance().refreshIoFiles(ImmutableList.of(propertiesFilePath));

    gradleWrapper = GradleWrapper.find(project);
    assertNotNull(gradleWrapper);
    properties = gradleWrapper.getProperties();
    assertNotNull(properties);

    // Verify that distributionSha256Sum is added to Gradle wrapper.
    assertThat(properties.getProperty(DISTRIBUTION_SHA_256_SUM, null)).isNotNull();

    RemoveSHA256FromGradleWrapperHyperlink hyperlink = new RemoveSHA256FromGradleWrapperHyperlink();
    hyperlink.execute(project);

    // Verify the text is accurate.
    assertThat(hyperlink.toHtml()).contains("Remove distributionSha256Sum and sync project");

    gradleWrapper = GradleWrapper.find(project);
    assertNotNull(gradleWrapper);
    properties = gradleWrapper.getProperties();
    assertNotNull(properties);

    // Verify that distributionSha256Sum is removed from Gradle wrapper.
    assertThat(properties.getProperty(DISTRIBUTION_SHA_256_SUM, null)).isNull();
  }
}