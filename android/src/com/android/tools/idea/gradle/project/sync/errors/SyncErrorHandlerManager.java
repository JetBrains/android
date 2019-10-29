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
import com.android.tools.idea.util.PositionInFile;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.build.BuildView;
import com.intellij.build.FilePosition;
import com.intellij.build.SyncViewManager;
import com.intellij.build.issue.BuildIssueQuickFix;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.pom.NonNavigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker;
import org.jetbrains.plugins.gradle.issue.GradleIssueData;

import java.io.File;
import java.util.Objects;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.project.messages.SyncMessage.DEFAULT_GROUP;
import static com.intellij.openapi.externalSystem.service.notification.NotificationCategory.ERROR;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.buildErrorMessage;

public class SyncErrorHandlerManager {
  @NotNull private final GradleSyncMessages mySyncMessages;
  @NotNull private final ErrorAndLocation.Factory myCauseAndLocationFactory;
  @NotNull private final SyncErrorHandler[] myErrorHandlers;
  @NotNull private final Project myProject;

  public SyncErrorHandlerManager(@NotNull Project project) {
    this(project, GradleSyncMessages.getInstance(project), new ErrorAndLocation.Factory(), SyncErrorHandler.getExtensions());
  }

  @VisibleForTesting
  SyncErrorHandlerManager(@NotNull Project project,
                          @NotNull GradleSyncMessages syncMessages,
                          @NotNull ErrorAndLocation.Factory causeAndLocationFactory,
                          @NotNull SyncErrorHandler... errorHandlers) {
    mySyncMessages = syncMessages;
    myCauseAndLocationFactory = causeAndLocationFactory;
    myErrorHandlers = errorHandlers;
    myProject = project;
  }

  // Create NotificationData and call sync error handlers
  public void handleError(@NotNull ExternalSystemTaskId id, @NotNull Throwable error) {
    Runnable runnable = () -> {
      ErrorAndLocation errorAndLocation = myCauseAndLocationFactory.create(error);
      ExternalSystemException errorToReport = errorAndLocation.getError();

      String message = buildErrorMessage(errorToReport);
      PositionInFile positionInFile = errorAndLocation.getPositionInFile();
      NotificationData notificationData = mySyncMessages.createNotification(DEFAULT_GROUP, message, ERROR, positionInFile);

      boolean isHandled = false;
      for (SyncErrorHandler errorHandler : myErrorHandlers) {
        if (errorHandler.handleError(errorToReport, notificationData, myProject)) {
          isHandled = true;
          break;
        }
      }
      // re-use common gradle issues checkers
      if (!isHandled) {
        String projectPath = getBaseDirPath(myProject).getPath();
        FilePosition filePosition = positionInFile != null
                                    ? new FilePosition(new File(positionInFile.file.getPath()), positionInFile.line, positionInFile.column)
                                    : null;
        GradleIssueData issueData = new GradleIssueData(projectPath, error, null, filePosition);
        GradleIssueChecker.getKnownIssuesCheckList().stream()
          .map(checker -> checker.check(issueData))
          .filter(Objects::nonNull)
          .findFirst().ifPresent(issue -> {
          notificationData.setMessage(issue.getDescription());
          for (BuildIssueQuickFix quickFix : issue.getQuickFixes()) {
            notificationData.setListener(quickFix.getId(), (notification, event) -> {
              SyncViewManager syncViewManager = ServiceManager.getService(myProject, SyncViewManager.class);
              BuildView buildView = syncViewManager.getBuildView(id);
              DataProvider provider = buildView == null ? dataId -> null : buildView;
              quickFix.runQuickFix(myProject, provider);
            });
          }
        });
      }

      if (notificationData.getNavigatable() == null) {
        notificationData.setNavigatable(NonNavigatable.INSTANCE);
      }
      mySyncMessages.report(notificationData);
    };

    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(runnable);
  }
}