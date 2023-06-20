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
package com.android.tools.idea.instantapp;

import static com.android.tools.idea.instantapp.InstantApps.findBaseFeature;
import static com.android.tools.idea.instantapp.InstantApps.getDefaultInstantAppUrl;
import static com.android.tools.idea.testing.TestProjectPaths.INSTANT_APP;
import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;

import com.android.testutils.junit4.OldAgpTest;
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.android.tools.idea.testing.TestModuleUtil;
import com.intellij.testFramework.EdtRule;
import org.jetbrains.android.facet.AndroidFacet;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

@OldAgpTest(agpVersions = "3.5.0", gradleVersions = "5.5")
public class InstantAppsTest {

  private final AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();

  @Rule
  public final RuleChain ruleChain = RuleChain.outerRule(projectRule).around(new EdtRule());

  @Test
  public void testFindBaseFeatureWithInstantApp() throws Exception {
    projectRule.loadProject(INSTANT_APP, "instant-app", AgpVersionSoftwareEnvironmentDescriptor.AGP_35);
    AndroidFacet facet = requireNonNull(AndroidFacet.getInstance(projectRule.getModule("instant-app")));
    assertEquals(TestModuleUtil.findModule(projectRule.getProject(), "feature"), findBaseFeature(facet));
  }

  @Test
  @Ignore("b/203803107")
  public void testGetDefaultInstantAppUrlWithInstantApp() throws Exception {
    // Use a plugin version that supports instant app
    projectRule.loadProject(INSTANT_APP, "instant-app", AgpVersionSoftwareEnvironmentDescriptor.AGP_35);
    AndroidFacet facet = requireNonNull(AndroidFacet.getInstance(projectRule.getModule("instant-app")));
    assertEquals("http://example.com/example", getDefaultInstantAppUrl(facet));
  }
}