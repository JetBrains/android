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

import com.android.tools.idea.gradle.GradleSyncState;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.intellij.openapi.project.Project;
import com.intellij.util.PathUtil;

import java.io.*;
import java.util.Map;

public class GradleProjectSyncDataTest extends AndroidGradleTestCase {
  public void testEndToEnd() throws Exception {
    loadProject("projects/projectWithAppandLib");

    Project project = myAndroidFacet.getModule().getProject();
    GradleSyncState syncState = GradleSyncState.getInstance(project);
    long previousSyncTime = syncState.getLastGradleSyncTimestamp();

    GradleProjectSyncData data = GradleProjectSyncData.createFrom(project);
    verifyGradleProjectSyncData(data, previousSyncTime);

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(outputStream);
    oos.writeObject(data);
    oos.close();

    ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
    ObjectInputStream ois = new ObjectInputStream(inputStream);
    GradleProjectSyncData newData = (GradleProjectSyncData)ois.readObject();
    ois.close();

    verifyGradleProjectSyncData(newData, previousSyncTime);
  }

  private void verifyGradleProjectSyncData(GradleProjectSyncData data, long previousSyncTime) {
    assertNotNull(data);

    Map<String, byte[]> checksums = data.getFileChecksums();
    assertEquals(7, checksums.size());
    assertContainsElements(checksums.keySet(), "gradle.properties", "local.properties", "build.gradle", "settings.gradle",
                           PathUtil.toSystemDependentName("app/build.gradle"), PathUtil.toSystemDependentName("lib/build.gradle"));
    String home = System.getProperty("user.home");
    if (home != null) {
      File userProperties = new File(new File(home), PathUtil.toSystemDependentName(".gradle/gradle.properties"));
      assertContainsElements(checksums.keySet(), userProperties.getPath());
    }

    assertEquals(previousSyncTime, data.getLastGradleSyncTimestamp());
  }
}
