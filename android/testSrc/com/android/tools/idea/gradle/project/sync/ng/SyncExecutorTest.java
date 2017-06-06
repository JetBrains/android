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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.tools.idea.gradle.project.sync.common.CommandLineArgs;
import com.android.tools.idea.gradle.project.sync.errors.SyncErrorHandlerManager;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.IdeaTestCase;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link SyncExecutor}.
 */
public class SyncExecutorTest extends IdeaTestCase {
  @Mock private GradleSyncMessages mySyncMessages;
  @Mock private CommandLineArgs myCommandLineArgs;
  @Mock private SyncErrorHandlerManager myErrorHandlerManager;
  @Mock private ExtraSyncModelExtensionManager myExtraSyncModelExtensionManager;

  private SyncExecutor mySyncExecutor;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    mySyncExecutor = new SyncExecutor(getProject(), mySyncMessages, myCommandLineArgs, myErrorHandlerManager,
                                      myExtraSyncModelExtensionManager);
  }

  public void testGetCommandLineOptionsWithNewProject() {
    Project project = getProject();

    List<String> args = Arrays.asList("test1", "test2");
    when(myCommandLineArgs.get(any(), same(project))).thenReturn(args);

    List<String> actualArgs = mySyncExecutor.getCommandLineOptions(true);
    assertSame(args, actualArgs);

    CommandLineArgs.Options options = new CommandLineArgs.Options();
    options.applyJavaPlugin();
    options.includeLocalMavenRepo();
    verify(myCommandLineArgs, times(1)).get(options, project);
  }

  public void testGetCommandLineOptionsWithProjectThatIsNotNew() {
    Project project = getProject();

    List<String> args = Arrays.asList("test1", "test2");
    when(myCommandLineArgs.get(any(), same(project))).thenReturn(args);

    List<String> actualArgs = mySyncExecutor.getCommandLineOptions(false);
    assertSame(args, actualArgs);

    CommandLineArgs.Options options = new CommandLineArgs.Options();
    options.applyJavaPlugin();
    verify(myCommandLineArgs, times(1)).get(options, project);
  }
}