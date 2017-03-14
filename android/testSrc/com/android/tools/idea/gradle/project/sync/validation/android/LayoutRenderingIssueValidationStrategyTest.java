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
package com.android.tools.idea.gradle.project.sync.validation.android;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.AndroidModelFeatures;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link LayoutRenderingIssueValidationStrategy}.
 */
public class LayoutRenderingIssueValidationStrategyTest extends AndroidGradleTestCase {
  private LayoutRenderingIssueValidationStrategy myStrategy;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myStrategy = new LayoutRenderingIssueValidationStrategy(getProject());
  }

  public void testValidate() {
    GradleVersion modelVersion = GradleVersion.parse("1.2.0");
    AndroidModuleModel androidModel = mockAndroidModel(modelVersion, true);

    myStrategy.validate(mock(Module.class), androidModel);

    assertEquals(modelVersion, myStrategy.getModelVersion());
  }

  public void testValidateWithoutLayoutRenderingIssue() {
    GradleVersion modelVersion = GradleVersion.parse("2.2.0");
    AndroidModuleModel androidModel = mockAndroidModel(modelVersion, false);

    myStrategy.validate(mock(Module.class), androidModel);

    assertNull(myStrategy.getModelVersion());
  }

  @NotNull
  private static AndroidModuleModel mockAndroidModel(@NotNull GradleVersion version, boolean hasLayoutRenderingIssue) {
    AndroidModelFeatures features = mock(AndroidModelFeatures.class);
    when(features.isLayoutRenderingIssuePresent()).thenReturn(hasLayoutRenderingIssue);

    AndroidModuleModel androidModel = mock(AndroidModuleModel.class);
    when(androidModel.getFeatures()).thenReturn(features);
    when(androidModel.getModelVersion()).thenReturn(version);

    return androidModel;
  }

  // See https://code.google.com/p/android/issues/detail?id=170841
  public void testFixAndReportFoundIssues() {
    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());

    myStrategy.setModelVersion(GradleVersion.parse("1.2.0"));
    myStrategy.fixAndReportFoundIssues();

    SyncMessage message = syncMessages.getFirstReportedMessage();
    assertNotNull(message);

    String[] text = message.getText();
    assertThat(text).isNotEmpty();

    assertThat(text[0]).startsWith("Using an obsolete version of the Gradle plugin (1.2.0)");
  }

  public void testFixAndReportFoundIssuesWithNoIssues() {
    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());

    myStrategy.setModelVersion(null);
    myStrategy.fixAndReportFoundIssues();

    SyncMessage message = syncMessages.getFirstReportedMessage();
    assertNull(message);
  }
}