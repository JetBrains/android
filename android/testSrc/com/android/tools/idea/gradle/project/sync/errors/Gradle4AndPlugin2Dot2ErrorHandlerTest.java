/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors;

import static com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub.replaceSyncMessagesService;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_PRE30;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.project.sync.hyperlink.FixGradleVersionInWrapperHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import java.util.List;
import org.junit.Ignore;

/**
 * Tests for {@link Gradle4AndPlugin2Dot2ErrorHandler}.
 */
// Disabled due to b/117515863
@Ignore
public class Gradle4AndPlugin2Dot2ErrorHandlerTest extends AndroidGradleTestCase {
  private GradleSyncMessagesStub mySyncMessagesStub;
  private static String GRADLE_VERSION = "4.10.1";
  private static String GRADLE_PLUGIN_VERSION = "2.2.0";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = replaceSyncMessagesService(getProject());
  }


  public void testHandleError() throws Exception {
    loadProject(SIMPLE_APPLICATION_PRE30, null, GRADLE_VERSION, GRADLE_PLUGIN_VERSION);

    requestSyncAndGetExpectedFailure();

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    String message = notificationUpdate.getText();
    assertThat(message).contains("The versions of the Android Gradle plugin and Gradle are not compatible");
    assertThat(message).contains("Update your plugin to version 2.4");
    assertThat(message).contains("Downgrade Gradle to version 3.5");

    List<NotificationHyperlink> fixes = notificationUpdate.getFixes();
    assertThat(fixes).hasSize(1);

    NotificationHyperlink fix = fixes.get(0);
    assertThat(fix).isInstanceOf(FixGradleVersionInWrapperHyperlink.class);
    String gradleVersion = ((FixGradleVersionInWrapperHyperlink)fix).getGradleVersion();
    assertEquals("3.5", gradleVersion);
  }
}