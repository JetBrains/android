/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.android.dom.manifest.Manifest;

import java.util.Arrays;
import java.util.List;

public class AndroidModuleInfoTest extends AndroidGradleTestCase {
  public void testManifestOnly() throws Exception {
    //noinspection ConstantConditions
    if (!CAN_SYNC_PROJECTS ) {
      System.err.println("AndroidModuleInfoTest.testManifestOnly temporarily disabled");
      return;
    }
    loadProject("projects/moduleInfo/manifestOnly");
    assertNotNull(myAndroidFacet);
    assertEquals(7, myAndroidFacet.getAndroidModuleInfo().getMinSdkVersion());
    assertEquals(18, myAndroidFacet.getAndroidModuleInfo().getTargetSdkVersion());
    //noinspection SpellCheckingInspection
    assertEquals("com.example.unittest", myAndroidFacet.getAndroidModuleInfo().getPackage());
  }

  public void testGradleOnly() throws Exception {
    //noinspection ConstantConditions
    if (!CAN_SYNC_PROJECTS ) {
      System.err.println("AndroidModuleInfoTest.testGradleOnly temporarily disabled");
      return;
    }
    loadProject("projects/moduleInfo/gradleOnly");
    assertNotNull(myAndroidFacet);
    assertEquals(9, myAndroidFacet.getAndroidModuleInfo().getMinSdkVersion());
    assertEquals(17, myAndroidFacet.getAndroidModuleInfo().getTargetSdkVersion());
    assertEquals("from.gradle", myAndroidFacet.getAndroidModuleInfo().getPackage());
  }

  public void testBoth() throws Exception {
    //noinspection ConstantConditions
    if (!CAN_SYNC_PROJECTS ) {
      System.err.println("AndroidModuleInfoTest.testBoth temporarily disabled");
      return;
    }
    loadProject("projects/moduleInfo/both");
    assertNotNull(myAndroidFacet);
    assertEquals(9, myAndroidFacet.getAndroidModuleInfo().getMinSdkVersion());
    assertEquals(17, myAndroidFacet.getAndroidModuleInfo().getTargetSdkVersion());
    assertEquals("from.gradle", myAndroidFacet.getAndroidModuleInfo().getPackage());
  }

  public void testFlavors() throws Exception {
    //noinspection ConstantConditions
    if (!CAN_SYNC_PROJECTS ) {
      System.err.println("AndroidModuleInfoTest.testFlavors temporarily disabled");
      return;
    }
    loadProject("projects/moduleInfo/flavors");
    assertNotNull(myAndroidFacet);
    IdeaAndroidProject gradleProject = myAndroidFacet.getIdeaAndroidProject();
    assertNotNull(gradleProject);
    assertEquals("freeDebug", gradleProject.getSelectedVariant().getName());

    assertEquals(14, myAndroidFacet.getAndroidModuleInfo().getMinSdkVersion());
    assertEquals(17, myAndroidFacet.getAndroidModuleInfo().getTargetSdkVersion());
    assertEquals("com.example.free.debug", myAndroidFacet.getAndroidModuleInfo().getPackage());
  }

  public void testMerge() throws Exception {
    //noinspection ConstantConditions
    if (!CAN_SYNC_PROJECTS ) {
      System.err.println("AndroidModuleInfoTest.testFlavors temporarily disabled");
      return;
    }
    loadProject("projects/moduleInfo/merge");
    assertNotNull(myAndroidFacet);
    IdeaAndroidProject gradleProject = myAndroidFacet.getIdeaAndroidProject();
    assertNotNull(gradleProject);
    assertEquals("debug", gradleProject.getSelectedVariant().getName());

    // GradleInvoker.executeTasks is mostly a no-op while unit testing. As a result,
    // the merged manifest won't be generated. The test data includes a merged manifest
    // in the build folder to overcome this limitation.
    // GradleInvoker.getInstance(getProject()).executeTasks(Arrays.asList("generateDebugSources"));

    Manifest mainManifest = myAndroidFacet.getAndroidModuleInfo().getManifest(false);
    Manifest mergedManifest = myAndroidFacet.getAndroidModuleInfo().getManifest(true);

    assertNotNull(mainManifest);
    assertNotNull(mergedManifest);

    List<Activity> mainActivities = mainManifest.getApplication().getActivities();
    assertEquals(1, mainActivities.size());
    assertEquals(".Main", mainActivities.get(0).getActivityClass().getRawText());

    List<Activity> mergedActivities = mergedManifest.getApplication().getActivities();
    assertEquals(2, mergedActivities.size());
    assertEquals("com.example.unittest.Main", mergedActivities.get(0).getActivityClass().getRawText());
    assertEquals(".Debug", mergedActivities.get(1).getActivityClass().getRawText());
  }
}
