/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Map;

import static com.android.tools.idea.testing.TestProjectPaths.PROJECT_WITH_APPAND_LIB;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

public class ProjectBuildFileChecksumsTest extends AndroidGradleTestCase {
  public void testEndToEnd() throws Exception {
    loadProject(PROJECT_WITH_APPAND_LIB);

    Project project = myAndroidFacet.getModule().getProject();
    GradleSyncState syncState = GradleSyncState.getInstance(project);
    long previousSyncTime = syncState.getSummary().getSyncTimestamp();

    ProjectBuildFileChecksums data = ProjectBuildFileChecksums.createFrom(project);
    verifyGradleProjectSyncData(data, previousSyncTime);

    ProjectBuildFileChecksums newData;
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      try (ObjectOutputStream oos = new ObjectOutputStream(outputStream)) {
        oos.writeObject(data);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray())) {
          try (ObjectInputStream ois = new ObjectInputStream(inputStream)) {
            newData = (ProjectBuildFileChecksums)ois.readObject();
          }
        }
      }
    }
    verifyGradleProjectSyncData(newData, previousSyncTime);
  }

  private static void verifyGradleProjectSyncData(@NotNull ProjectBuildFileChecksums data, long previousSyncTime) {
    assertNotNull(data);

    Map<String, byte[]> checksums = data.getFileChecksums();
    assertEquals(7, checksums.size());
    assertThat(checksums.keySet()).containsAllOf("gradle.properties", "local.properties", "build.gradle", "settings.gradle",
                                                 toSystemDependentName("app/build.gradle"), toSystemDependentName("lib/build.gradle"));
    String home = System.getProperty("user.home");
    if (home != null) {
      File userProperties = new File(new File(home), toSystemDependentName(".gradle/gradle.properties"));
      assertThat(checksums.keySet()).contains(userProperties.getPath());
    }

    assertEquals(previousSyncTime, data.getLastGradleSyncTimestamp());
  }
}
