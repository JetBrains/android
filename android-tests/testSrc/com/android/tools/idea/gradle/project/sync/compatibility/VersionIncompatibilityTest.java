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

import com.android.tools.idea.gradle.project.sync.compatibility.version.ComponentVersionReader;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessagesStub;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;

import static com.android.tools.idea.gradle.project.sync.compatibility.version.ComponentVersionReader.ANDROID_GRADLE_PLUGIN;
import static com.android.tools.idea.gradle.project.sync.compatibility.version.ComponentVersionReader.GRADLE;
import static com.android.tools.idea.gradle.project.sync.messages.MessageType.ERROR;
import static com.android.tools.idea.gradle.project.sync.messages.MessageType.WARNING;
import static com.android.tools.idea.gradle.project.sync.messages.SyncMessageSubject.syncMessage;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link VersionIncompatibility}.
 */
public class VersionIncompatibilityTest extends AndroidGradleTestCase {
  private SyncMessagesStub mySyncMessagesStub;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = SyncMessagesStub.replaceSyncMessagesService(getProject());
  }

  public void testReportMessagesWithWarning() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    Module appModule = myModules.getAppModule();

    Component base = new Component("android-gradle-plugin", "2.1.3", null);
    Pair<ComponentVersionReader, String> baseReaderAndVersion = Pair.create(ANDROID_GRADLE_PLUGIN, "2.1.3");

    String failureMessage = "Wrong Gradle version";
    Component requirement = new Component("gradle", "2.14.1", failureMessage);
    base.addRequirement(requirement);

    CompatibilityCheck check = new CompatibilityCheck(base, WARNING);

    VersionIncompatibility incompatibility = new VersionIncompatibility(appModule, check, baseReaderAndVersion, requirement, GRADLE);
    incompatibility.reportMessages(getProject());

    SyncMessage message = mySyncMessagesStub.getFirstReportedMessage();
    assertNotNull(message);
    assertThat(message.getText()).hasLength(2);

    // @formatter:off
    assertAbout(syncMessage()).that(message).hasType(WARNING)
                                            .hasMessageLine("Android Gradle plugin 2.1.3 requires Gradle 2.14.1 (or newer)", 0)
                                            .hasMessageLine(failureMessage, 1);
    // @formatter:on
  }

  public void testReportMessagesWithError() throws Exception {
    loadSimpleApplication();
    mySyncMessagesStub.clearReportedMessages();

    Module appModule = myModules.getAppModule();

    Component base = new Component("grade", "2.14.1", null);
    Pair<ComponentVersionReader, String> baseReaderAndVersion = Pair.create(GRADLE, "2.14.1");

    String failureMessage = "Wrong Android Gradle plugin version";
    Component requirement = new Component("android-gradle-plugin", "2.1.3", failureMessage);
    base.addRequirement(requirement);

    CompatibilityCheck check = new CompatibilityCheck(base, ERROR);

    VersionIncompatibility incompatibility =
      new VersionIncompatibility(appModule, check, baseReaderAndVersion, requirement, ANDROID_GRADLE_PLUGIN);
    incompatibility.reportMessages(getProject());

    SyncMessage message = mySyncMessagesStub.getFirstReportedMessage();
    assertNotNull(message);
    assertThat(message.getText()).hasLength(2);

    // @formatter:off
    assertAbout(syncMessage()).that(message).hasType(ERROR)
                                            .hasMessageLine("Gradle 2.14.1 requires Android Gradle plugin 2.1.3 (or newer)", 0)
                                            .hasMessageLine(failureMessage, 1);
    // @formatter:on
  }
}