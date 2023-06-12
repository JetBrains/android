/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.heap;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * It is an internal action that is only registered for Android Studio instances running from integration
 * End2End tests. It collects memory usage statistics of components
 */
public final class IntegrationTestCollectMemoryUsageStatisticsAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    HeapSnapshotTraverseService.getInstance().collectMemoryReportAndDumpToMetricsFile();
  }
}
