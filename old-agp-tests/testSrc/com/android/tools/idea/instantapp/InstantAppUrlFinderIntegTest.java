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
package com.android.tools.idea.instantapp;

import static com.android.tools.idea.testing.TestProjectPaths.INSTANT_APP_RESOURCE_HOST;
import static com.intellij.testFramework.UsefulTestCase.assertContainsElements;
import static com.intellij.testFramework.UsefulTestCase.assertSize;

import com.android.testutils.junit4.OldAgpTest;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.EdtRule;
import java.util.Collection;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

@OldAgpTest(agpVersions = "3.5.0", gradleVersions = "5.5")
public class InstantAppUrlFinderIntegTest {

  private final AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();

  @Rule
  public final RuleChain ruleChain = RuleChain.outerRule(projectRule).around(new EdtRule());

  @Test
  @Ignore("b/203803107")
  public void testHostIsResolved() throws Exception {
    // Use a plugin with instant app supportp
    projectRule.loadProject(INSTANT_APP_RESOURCE_HOST, null, "5.5", "3.5.0", null, null, "32");
    Module featureModule = projectRule.getModule("feature");
    Collection<String> urls = new InstantAppUrlFinder(featureModule).getAllUrls();
    assertSize(1, urls);
    assertContainsElements(urls, "https://android.example.com/example");
  }
}
