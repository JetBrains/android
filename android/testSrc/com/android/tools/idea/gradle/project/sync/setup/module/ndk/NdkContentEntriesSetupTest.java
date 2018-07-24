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
package com.android.tools.idea.gradle.project.sync.setup.module.ndk;

import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.model.NdkVariant;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;
import java.util.Collections;

import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
/**
 * Tests for {@link NdkContentEntriesSetup}.
 */
public class NdkContentEntriesSetupTest {
  @Mock NdkModuleModel myNdkModuleModel;
  @Mock ModifiableRootModel myRootModel;

  private NdkContentEntriesSetup myNdkContentEntriesSetup;

  @Before
  public void setUp() throws Exception {
    initMocks(this);
    myNdkContentEntriesSetup = new NdkContentEntriesSetup(myNdkModuleModel, myRootModel);
  }

  /**
   * Verify that folder .externalNativeBuild is excluded (b/72450552)
   */
  @Test
  public void excludeExternalNativeBuild() {
    // Prepare mock parameters
    ContentEntry contentEntry = mock(ContentEntry.class);
    NdkVariant mockNdkVariant = mock(NdkVariant.class);
    when (mockNdkVariant.getSourceFolders()).thenReturn(Collections.emptyList());
    when(myNdkModuleModel.getSelectedVariant()).thenReturn(mockNdkVariant);

    // Execute step
    myNdkContentEntriesSetup.execute(Collections.singletonList(contentEntry));

    // Verify exclusion
    File excludePath = new File(myNdkModuleModel.getRootDirPath(), ".externalNativeBuild");
    verify(contentEntry).addExcludeFolder(pathToIdeaUrl(excludePath));
  }
}
