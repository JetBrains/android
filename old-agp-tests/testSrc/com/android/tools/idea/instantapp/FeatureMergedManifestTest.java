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
package com.android.tools.idea.instantapp;

import static com.android.tools.idea.testing.TestProjectPaths.INSTANT_APP_LIBRARY_DEPENDENCY;
import static com.intellij.testFramework.UsefulTestCase.assertSize;
import static org.junit.Assert.assertEquals;

import com.android.testutils.junit4.OldAgpTest;
import com.android.tools.idea.model.MergedManifestManager;
import com.android.tools.idea.model.MergedManifestSnapshot;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.EdtRule;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

@OldAgpTest(agpVersions = "3.5.0", gradleVersions = "5.5")
public class FeatureMergedManifestTest {

  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(projectRule).around(new EdtRule());

  @Test
  @Ignore("b/203803107")
  public void testLibraryManifestMergedOnFeature() throws Exception {
    // Use a plugin version with feature support
    projectRule.loadProject(INSTANT_APP_LIBRARY_DEPENDENCY, null, "5.5", "3.5.0", null, null, "32");
    Module featureModule = projectRule.getModule("feature");
    MergedManifestSnapshot mergedManifestManager = MergedManifestManager.getSnapshot(featureModule);
    assertSize(1, mergedManifestManager.getActivities());
  }

  @Test
  @Ignore("b/203803107")
  public void testCanFindURL() throws Exception {
    // Use a plugin version with feature support
    projectRule.loadProject(INSTANT_APP_LIBRARY_DEPENDENCY, null, "5.5", "3.5.0", null, null, "32");
    Module bundleModule = projectRule.getModule("instantapp");
    AndroidFacet facet = AndroidFacet.getInstance(bundleModule);
    assertEquals("https://android.example.com/example", InstantApps.getDefaultInstantAppUrl(facet));
  }
}
