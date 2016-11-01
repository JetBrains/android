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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link OutdatedAppEngineGradlePluginErrorHandler}.
 */
public class OutdatedAppEngineGradlePluginErrorHandlerTest extends SyncErrorHandlerTestCase {
  @NotNull
  @Override
  protected SyncErrorHandler createErrorHandler() {
    return new OutdatedAppEngineGradlePluginErrorHandler();
  }

  public void testFindErrorMessage() {
    String expectedNotificationMessage = "Cause: java.io.File cannot be cast to org.gradle.api.artifacts.Configuration";
    ExternalSystemException cause = new ExternalSystemException(expectedNotificationMessage);

    // Verify that findErrorMessage returns correct text.
    String text = myErrorHandler.findErrorMessage(createExternalSystemException(cause), myNotification, myProject);
    assertThat(text).contains(expectedNotificationMessage);
  }

  public void testGetQuickFixHyperlinks() {
    String text = "Cause: java.io.File cannot be cast to org.gradle.api.artifacts.Configuration";

    // Verify that getQuickFixHyperlinks returns empty.
    List<NotificationHyperlink> hyperlinks = myErrorHandler.getQuickFixHyperlinks(myNotification, myProject, text);
    assertEmpty(hyperlinks);
    // TODO: find a way to mock build.gradle file, so that getQuickFixHyperlinks returns non-empty list
  }
}