/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.qsync;

import com.google.idea.blaze.base.qsync.QuerySyncManager.OperationType;
import com.google.idea.blaze.base.sync.SyncMode;
import com.google.idea.blaze.base.sync.SyncResult;
import com.google.idea.blaze.base.sync.status.BlazeSyncStatus;
import com.intellij.openapi.project.Project;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Class that keeps track of query sync operations. */
public class QuerySyncStatus {

  private final Project project;
  private final AtomicReference<OperationType> currentOperation = new AtomicReference<>(null);

  public QuerySyncStatus(Project project) {
    this.project = project;
  }

  public boolean operationInProgress() {
    return currentOperation.get() != null;
  }

  public void operationStarted(OperationType operationType) {
    currentOperation.set(operationType);
    BlazeSyncStatus.getInstance(project).syncStarted();
  }

  public void operationCancelled() {
    operationEnded(SyncResult.CANCELLED);
  }

  public void operationFailed() {
    operationEnded(SyncResult.FAILURE);
  }

  public void operationEnded() {
    operationEnded(SyncResult.SUCCESS);
  }

  private void operationEnded(SyncResult result) {
    currentOperation.set(null);
    BlazeSyncStatus.getInstance(project).syncEnded(SyncMode.FULL, result);
  }

  public Optional<OperationType> currentOperation() {
    return Optional.ofNullable(currentOperation.get());
  }
}
