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
package com.android.tools.idea.gradle.project.sync.compatibility;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.project.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import org.intellij.lang.annotations.Language;

import static com.android.tools.idea.gradle.project.sync.compatibility.VersionCompatibilityChecker.VERSION_COMPATIBILITY_ISSUE_GROUP;
import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.android.tools.idea.gradle.project.sync.messages.SyncMessageSubject.syncMessage;
import static com.google.common.truth.Truth.assertAbout;

/**
 * Tests for {@link VersionCompatibilityChecker}.
 */
public class VersionCompatibilityCheckerTest extends AndroidGradleTestCase {
  private GradleSyncMessagesStub mySyncMessagesStub;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      VersionCompatibilityChecker.getInstance().reloadMetadata();
    }
    finally {
      super.tearDown();
    }
  }

  public void testCheckAndReportComponentIncompatibilities() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    GradleWrapper gradleWrapper = GradleWrapper.find(getProject());
    assertNotNull(gradleWrapper);
    String gradleVersion = gradleWrapper.getGradleVersion();
    assertNotNull(gradleVersion);

    Module appModule = myModules.getAppModule();
    AndroidModuleModel androidModel = AndroidModuleModel.get(appModule);
    assertNotNull(androidModel);
    GradleVersion currentModelVersion = androidModel.getModelVersion();
    assertNotNull(currentModelVersion);

    String failureMessage = "Please use Android Gradle plugin 100 or newer.";

    @Language("XML")
    String metadata = "<compatibility version='1'>\n" +
                      "  <check failureType='error'>\n" +
                      "    <component name='gradle' version='[" + gradleVersion + ", +)'>\n" +
                      "      <requires name='android-gradle-plugin' version='[100.0.0, +]'>\n" +
                      "        <failureMsg>\n" +
                      "           <![CDATA[\n" +
                      failureMessage + "\n" +
                      "]]>\n" +
                      "        </failureMsg>\n" +
                      "      </requires>\n" +
                      "    </component>\n" +
                      "  </check>\n" +
                      "</compatibility>";
    VersionCompatibilityChecker checker = VersionCompatibilityChecker.getInstance();
    checker.reloadMetadataForTesting(metadata);
    checker.checkAndReportComponentIncompatibilities(getProject());

    SyncMessage message = mySyncMessagesStub.getFirstReportedMessage();

    String firstLine = String.format("Gradle %1$s requires Android Gradle plugin 100.0.0 (or newer) but project is using version %2$s.",
                                     gradleVersion, currentModelVersion);

    // @formatter:off
    assertAbout(syncMessage()).that(message).hasGroup(VERSION_COMPATIBILITY_ISSUE_GROUP)
                                            .hasType(ERROR)
                                            .hasMessageLine(firstLine, 0)
                                            .hasMessageLine(failureMessage, 1);
    // @formatter:on
  }
}