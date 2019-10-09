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
package com.android.tools.idea.project;

import static com.android.tools.idea.project.AndroidKtsSupportNotification.KTS_DISABLED_WARNING_MSG;
import static com.android.tools.idea.project.AndroidKtsSupportNotification.KTS_ENABLED_WARNING_MSG;
import static com.android.tools.idea.project.AndroidKtsSupportNotification.KTS_NOTIFICATION_GROUP;
import static com.android.tools.idea.project.AndroidKtsSupportNotification.KTS_WARNING_TITLE;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.notification.NotificationType.WARNING;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.sync.hyperlink.FileBugHyperlink;
import com.android.tools.idea.project.AndroidKtsSupportNotification.DisableAndroidKtsNotificationHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.testFramework.PlatformTestCase;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

/**
 * Tests for {@link AndroidKtsSupportNotification}
 */
public class AndroidKtsSupportNotificationTest extends PlatformTestCase {
  @Mock private AndroidNotification myAndroidNotification;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    new IdeComponents(myProject).replaceProjectService(AndroidNotification.class, myAndroidNotification);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      StudioFlags.KOTLIN_DSL_PARSING.clearOverride();
    }
    finally {
      super.tearDown();
    }
  }

  /**
   * Verify an AndroidNotification is created with the expected parameters
   */
  public void testShowEnabledWarningIfNotShown() {
    StudioFlags.KOTLIN_DSL_PARSING.override(true);
    AndroidKtsSupportNotification myKtsNotification = new AndroidKtsSupportNotification(myProject);
    myKtsNotification.showWarningIfNotShown();
    verifyBalloon();
  }

  public void testShowDisabledWarningIfNotShown() {
    StudioFlags.KOTLIN_DSL_PARSING.override(false);
    AndroidKtsSupportNotification myKtsNotification = new AndroidKtsSupportNotification(myProject);
    myKtsNotification.showWarningIfNotShown();
    verifyBalloon();
  }

  /**
   * Verify that warning is generated only once even when called multiple times
   */
  public void testShowEnabledWarningIfNotShownTwice() {
    StudioFlags.KOTLIN_DSL_PARSING.override(true);
    AndroidKtsSupportNotification myKtsNotification = new AndroidKtsSupportNotification(myProject);
    myKtsNotification.showWarningIfNotShown();
    myKtsNotification.showWarningIfNotShown();
    verifyBalloon();
  }

  public void testShowDisabledWarningIfNotShownTwice() {
    StudioFlags.KOTLIN_DSL_PARSING.override(false);
    AndroidKtsSupportNotification myKtsNotification = new AndroidKtsSupportNotification(myProject);
    myKtsNotification.showWarningIfNotShown();
    myKtsNotification.showWarningIfNotShown();
    verifyBalloon();
  }

  private void verifyBalloon() {
    boolean enabled = StudioFlags.KOTLIN_DSL_PARSING.get();
    ArgumentCaptor<NotificationHyperlink> hyperlinkCaptor = ArgumentCaptor.forClass(NotificationHyperlink.class);
    verify(myAndroidNotification, times(1))
      .showBalloon(same(KTS_WARNING_TITLE),
                   enabled ? same(KTS_ENABLED_WARNING_MSG) : same(KTS_DISABLED_WARNING_MSG),
                   same(WARNING),
                   same(KTS_NOTIFICATION_GROUP),
                   hyperlinkCaptor.capture());
    List<NotificationHyperlink> hyperlinks = hyperlinkCaptor.getAllValues();
    assertThat(hyperlinks).hasSize(enabled ? 2 : 1);
    assertThat(hyperlinks.get(0)).isInstanceOf(DisableAndroidKtsNotificationHyperlink.class);
    if (enabled) {
      assertThat(hyperlinks.get(1)).isInstanceOf(FileBugHyperlink.class);
    }
  }
}
