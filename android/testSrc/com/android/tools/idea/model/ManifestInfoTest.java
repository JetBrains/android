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

import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;
import static com.android.tools.idea.projectsystem.ProjectSystemSyncUtil.PROJECT_SYSTEM_SYNC_TOPIC;
import static com.google.common.truth.Truth.assertThat;

public class ManifestInfoTest extends AndroidTestCase {
  public void testLastSyncTimestamp() throws Exception {
    ManifestInfo.ManifestFile manifestFile = ManifestInfo.ManifestFile.create(myFacet);

    // The first call to refresh will always be successful, so get that out of the way
    manifestFile.refresh();

    // No refresh necessary
    assertThat(manifestFile.refresh()).isFalse();

    // Make it look like the project has been synced
    myModule.getMessageBus().syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(ProjectSystemSyncManager.SyncResult.SUCCESS);

    // Syncing should have changed the timestamp, making a refresh necessary
    assertThat(manifestFile.refresh()).isTrue();
  }

  public void testGetLibManifests() throws Exception {
    List<VirtualFile> libManifests = ManifestInfo.ManifestFile.getLibManifests(myFacet);
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
