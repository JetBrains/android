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
package com.android.tools.idea.gradle.project.sync.setup.module.android;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.GradleSyncSummary;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import org.mockito.Mock;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.pom.java.LanguageLevel.JDK_1_7;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link JdkModuleSetupStep}.
 */
public class JdkModuleSetupStepTest extends AndroidGradleTestCase {
  @Mock private IdeSdks myIdeSdks;
  @Mock private Jdks myJdks;
  private JdkModuleSetupStep mySetupStep;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    mySetupStep = new JdkModuleSetupStep(myIdeSdks, myJdks);
  }

  public void testSetUpInAndroidStudio() throws Exception {
    loadSimpleApplication();

    Module appModule = myModules.getAppModule();
    AndroidModuleModel androidModel = AndroidModuleModel.get(appModule);
    assertNotNull(androidModel);

    Sdk jdk = mock(Sdk.class);

    when(myIdeSdks.getJdk()).thenReturn(jdk);
    when(myJdks.isApplicableJdk(jdk, JDK_1_7)).thenReturn(false);

    GradleSyncMessagesStub syncMessages = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());

    mySetupStep.setUpInAndroidStudio(appModule, androidModel);

    SyncMessage message = syncMessages.getFirstReportedMessage();
    assertNotNull(message);

    String[] text = message.getText();
    assertThat(text).isNotEmpty();

    assertThat(text[0]).matches("compileSdkVersion (.*) requires compiling with JDK 7 or newer.");

    GradleSyncSummary summary = GradleSyncState.getInstance(getProject()).getSummary();
    assertTrue(summary.hasSyncErrors());
  }
}