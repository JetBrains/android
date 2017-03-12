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
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.mockito.Mock;

import java.nio.charset.Charset;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link EncodingValidationStrategy}.
 */
public class EncodingValidationStrategyTest extends AndroidGradleTestCase {
  @Mock private EncodingProjectManager myEncodings;

  private EncodingValidationStrategy myStrategy;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    when(myEncodings.getDefaultCharset()).thenReturn(Charset.forName("ISO-8859-1"));

    myStrategy = new EncodingValidationStrategy(getProject(), myEncodings);
  }

  public void testValidate() {
    String modelEncoding = "UTF-8";

    AndroidModuleModel androidModel = mock(AndroidModuleModel.class);
    when(androidModel.getModelVersion()).thenReturn(GradleVersion.parse("1.2.0"));

    AndroidProjectStub androidProject = new AndroidProjectStub("app");
    androidProject.getJavaCompileOptions().setEncoding(modelEncoding);
    when(androidModel.getAndroidProject()).thenReturn(androidProject);

    myStrategy.validate(mock(Module.class), androidModel);

    assertEquals(modelEncoding, myStrategy.getMismatchingEncoding());
  }

  public void testFixAndReportFoundIssues() {
    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());

    String mismatchingEncoding = "UTF-8";
    myStrategy.setMismatchingEncoding(mismatchingEncoding);

    myStrategy.fixAndReportFoundIssues();

    SyncMessage message = syncMessages.getFirstReportedMessage();
    assertNotNull(message);

    String[] text = message.getText();
    assertThat(text).hasLength(2);

    assertThat(text[0]).startsWith("The project encoding (ISO-8859-1) has been reset");

    verify(myEncodings, times(1)).setDefaultCharsetName(mismatchingEncoding);
  }

  public void testFixAndReportFoundIssuesWithNoMismatch() {
    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());

    myStrategy.setMismatchingEncoding(null);
    myStrategy.fixAndReportFoundIssues();

    SyncMessage message = syncMessages.getFirstReportedMessage();
    assertNull(message);

    verify(myEncodings, never()).setDefaultCharsetName(anyString());
  }
}