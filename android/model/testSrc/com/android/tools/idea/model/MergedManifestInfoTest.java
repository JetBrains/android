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
package com.android.tools.idea.model;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY;
import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

public class MergedManifestInfoTest extends AndroidTestCase {
  public void testLastSyncTimestamp() throws Exception {
    MergedManifestInfo mergedManifestInfo = MergedManifestInfo.create(myFacet);

    // No refresh necessary
    assertThat(mergedManifestInfo.isUpToDate()).isTrue();

    // Make it look like the project has been synced
    getProject().getMessageBus().syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(ProjectSystemSyncManager.SyncResult.SUCCESS);

    // Syncing should have changed the timestamp, making a refresh necessary
    assertThat(mergedManifestInfo.isUpToDate()).isFalse();
  }

  public void testGetLibManifests() throws Exception {
    List<VirtualFile> libManifests = ProjectSystemUtil.getModuleSystem(myFacet).getMergedManifestContributors().libraryManifests;
    // TODO: add external library dependency to local library module and check to make sure libManifests lists the local one first.
  }

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    final String testName = getTestName(true);

    if (testName.equals("getLibManifests")) {
      addModuleWithAndroidFacet(projectBuilder, modules, "lib", PROJECT_TYPE_LIBRARY);
    }
  }
}
