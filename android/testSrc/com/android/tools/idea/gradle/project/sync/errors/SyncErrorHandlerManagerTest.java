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

import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.gradle.util.PositionInFile;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;

import static com.android.tools.idea.project.messages.SyncMessage.DEFAULT_GROUP;
import static com.intellij.openapi.externalSystem.service.notification.NotificationCategory.ERROR;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link SyncErrorHandlerManager}.
 */
public class SyncErrorHandlerManagerTest extends IdeaTestCase {
  @Mock private NotificationData myNotificationData;
  @Mock private SyncErrorHandler myErrorHandler1;
  @Mock private SyncErrorHandler myErrorHandler2;
  @Mock private ErrorAndLocation.Factory myCauseAndLocationFactory;
  @Mock private GradleSyncMessages mySyncMessages;

  private SyncErrorHandlerManager myErrorHandlerManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    myErrorHandlerManager = new SyncErrorHandlerManager(project, mySyncMessages, myCauseAndLocationFactory, myErrorHandler1,
                                                        myErrorHandler2);
  }

  public void testHandleError() {
    Throwable error = new Throwable("Test");
    ExternalSystemException errorToReport = new ExternalSystemException("Test error");
    errorToReport.initCause(error);
    PositionInFile positionInFile = new PositionInFile(mock(VirtualFile.class));
    ErrorAndLocation errorAndLocation = new ErrorAndLocation(errorToReport, positionInFile);

    when(myCauseAndLocationFactory.create(error)).thenReturn(errorAndLocation);
    when(mySyncMessages.createNotification(eq(DEFAULT_GROUP), any(), eq(ERROR), eq(positionInFile))).thenReturn(myNotificationData);

    // simulate that myErrorHandler1 can handle the error
    Project project = getProject();
    when(myErrorHandler1.handleError(errorToReport, myNotificationData, project)).thenReturn(true);

    myErrorHandlerManager.handleError(error);

    // Verify that the second error handler was not invoked because the first one already handled the error.
    verify(myErrorHandler1, times(1)).handleError(errorToReport, myNotificationData, project);
    verify(myErrorHandler2, never()).handleError(errorToReport, myNotificationData, project);

    // Verify the error was reported.
    verify(mySyncMessages, times(1)).report(myNotificationData);
  }

  /**
   * Verify an empty error can be processed by all error handlers returned by SyncErrorHandler.getExtensions() but that none of them
   * return true on handleError.
   */
  public void testEmptyError() {
    Throwable error = new Throwable("");
    ExternalSystemException errorToReport = new ExternalSystemException("");
    errorToReport.initCause(error);
    PositionInFile positionInFile = new PositionInFile(mock(VirtualFile.class));
    ErrorAndLocation errorAndLocation = new ErrorAndLocation(errorToReport, positionInFile);

    when(myCauseAndLocationFactory.create(error)).thenReturn(errorAndLocation);
    when(mySyncMessages.createNotification(eq(DEFAULT_GROUP), any(), eq(ERROR), eq(positionInFile))).thenReturn(myNotificationData);

    Project project = getProject();

    ArrayList<SyncErrorHandler> allExtensions = new ArrayList<>(Arrays.asList(SyncErrorHandler.getExtensions()));
    // add a mock handler
    when(myErrorHandler1.handleError(errorToReport, myNotificationData, project)).thenReturn(true);
    allExtensions.add(myErrorHandler1);
    allExtensions.add(myErrorHandler2);
    SyncErrorHandler[] asArray = new SyncErrorHandler[allExtensions.size()];
    allExtensions.toArray(asArray);

    SyncErrorHandlerManager realErrorHandler = new SyncErrorHandlerManager(project, mySyncMessages, myCauseAndLocationFactory, asArray);
    realErrorHandler.handleError(error);

    // Verify that only the first mock handler handled the error.
    verify(myErrorHandler1, times(1)).handleError(errorToReport, myNotificationData, project);
    verify(myErrorHandler2, never()).handleError(errorToReport, myNotificationData, project);

    // Verify the error was reported.
    verify(mySyncMessages, times(1)).report(myNotificationData);
  }
}