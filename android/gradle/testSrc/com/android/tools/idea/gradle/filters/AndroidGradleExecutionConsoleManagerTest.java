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
package com.android.tools.idea.gradle.filters;

import static com.android.tools.idea.gradle.project.sync.hyperlink.SyncProjectWithExtraCommandLineOptionsHyperlink.EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.externalSystem.util.ExternalSystemUtil.getConsoleManagerFor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tools.idea.gradle.filters.AndroidGradleExecutionConsoleManager.AndroidReRunSyncFilter;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.google.wireless.android.sdk.stats.GradleSyncStats;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.internal.AbstractExternalSystemTask;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemExecuteTaskTask;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * Test class for {@link AndroidGradleExecutionConsoleManager}.
 */
public class AndroidGradleExecutionConsoleManagerTest extends AndroidGradleTestCase {
  public void testGetConsoleManager() {
    // Verify that AndroidGradleExecutionConsoleManager is used for resolve project task.
    ExternalSystemTask resolveProjectTask = createExternalSystemTask(ExternalSystemResolveProjectTask.class);
    assertThat(getConsoleManagerFor(resolveProjectTask)).isInstanceOf(AndroidGradleExecutionConsoleManager.class);

    // Verify that AndroidGradleExecutionConsoleManager is NOT used for execute task task.
    ExternalSystemTask executeTaskTask = createExternalSystemTask(ExternalSystemExecuteTaskTask.class);
    assertThat(getConsoleManagerFor(executeTaskTask)).isNotInstanceOf(AndroidGradleExecutionConsoleManager.class);
  }

  public void testGetHyperLinkInfo() {
    AndroidReRunSyncFilter filter = new AndroidReRunSyncFilter("");

    // Simulate console output message.
    String output = "Run with --stacktrace option to get the stack trace. Run with --info or --debug option to get more log output.";
    Filter.Result result = filter.applyFilter(output, output.length());

    // Verify that 3 hyperlinks are created to re-run sync with options.
    List<HyperlinkInfo> hyperLinks = ContainerUtil.map(result.getResultItems(), item -> item.getHyperlinkInfo());
    assertThat(hyperLinks).hasSize(3);

    Project project = getProject();
    GradleSyncInvoker syncInvoker = new IdeComponents(project).mockApplicationService(GradleSyncInvoker.class);

    // Click the second hyperlink - "Run with --info"
    hyperLinks.get(1).navigate(project);

    // Verify that GradleSyncInvoker is called, and verify that extra command line options are set.
    verify(syncInvoker)
      .requestProjectSync(project,
                          new GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_USER_REQUEST_RERUN_WITH_ADDITIONAL_OPTIONS), null);
    assertThat(project.getUserData(EXTRA_GRADLE_COMMAND_LINE_OPTIONS_KEY)).asList().containsExactly("--info");
  }

  private <T extends AbstractExternalSystemTask> T createExternalSystemTask(Class<T> classToMock) {
    T externalSystemTask = mock(classToMock);
    ExternalSystemTaskId taskId = mock(ExternalSystemTaskId.class);
    when(taskId.getProjectSystemId()).thenReturn(GradleConstants.SYSTEM_ID);
    when(externalSystemTask.getId()).thenReturn(taskId);
    when(externalSystemTask.getExternalSystemId()).thenReturn(GradleConstants.SYSTEM_ID);
    when(externalSystemTask.getExternalProjectPath()).thenReturn("");
    when(externalSystemTask.getIdeProject()).thenReturn(getProject());
    return externalSystemTask;
  }
}
