/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post.project;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.project.messages.SyncMessage;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.testFramework.PlatformTestCase;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

public class IgnoredBuildScriptSetupStepTest extends PlatformTestCase {
  @Mock private GradleSyncMessages myMessages;
  @Mock private FileTypeManager myFileTypeManager;
  @NotNull private IgnoredBuildScriptSetupStep mySetupStep = new IgnoredBuildScriptSetupStep();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
  }

  public void testCheckIsIgnored() throws IOException {
    when(myFileTypeManager.isFileIgnored((String)any())).thenReturn(true);
    IgnoredBuildScriptSetupStep.checkIsNotIgnored("prefix ", createTempFile("buildScript", null), myFileTypeManager, myMessages);
    verify(myMessages).report((SyncMessage)any());
  }

  public void testCheckIsNotIgnored() throws IOException {
    when(myFileTypeManager.isFileIgnored((String)any())).thenReturn(false);
    IgnoredBuildScriptSetupStep.checkIsNotIgnored("prefix ", createTempFile("buildScript", null), myFileTypeManager, myMessages);
    verifyNoMoreInteractions(myMessages);
  }

  public void testInvokeOnFailedSync() {
    assertFalse(mySetupStep.invokeOnFailedSync());
  }
}