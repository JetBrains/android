/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea;

import com.android.testutils.JarTestSuiteRunner;
import com.android.testutils.junit4.OldAgpSuite;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_33Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_35Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_40Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_41Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_42Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_70Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_71Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_72Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_72_V1Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_73Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_74Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_80Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_81Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_82Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_83Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_84Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_85Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_86Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_87Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_88Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_89Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_8_10_Test;
import com.android.tools.idea.gradle.project.sync.SyncProject_AGP_8_11_Test;
import com.android.tools.tests.IdeaTestSuiteBase;
import com.android.tools.tests.MavenRepoRule;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.CoreIconManager;
import org.junit.ClassRule;
import org.junit.runner.RunWith;

/**
 * Runs tests annotated with {@link com.android.testutils.junit4.OldAgpTest}.
 */
@RunWith(OldAgpSuite.class)
@JarTestSuiteRunner.ExcludeClasses({
  OldAgpTests.class,
  IdeModelTestSuite.class,
  SyncProject_AGP_33Test.class,
  SyncProject_AGP_35Test.class,
  SyncProject_AGP_40Test.class,
  SyncProject_AGP_41Test.class,
  SyncProject_AGP_42Test.class,
  SyncProject_AGP_70Test.class,
  SyncProject_AGP_71Test.class,
  SyncProject_AGP_72_V1Test.class,
  SyncProject_AGP_72Test.class,
  SyncProject_AGP_73Test.class,
  SyncProject_AGP_74Test.class,
  SyncProject_AGP_80Test.class,
  SyncProject_AGP_81Test.class,
  SyncProject_AGP_82Test.class,
  SyncProject_AGP_83Test.class,
  SyncProject_AGP_84Test.class,
  SyncProject_AGP_85Test.class,
  SyncProject_AGP_86Test.class,
  SyncProject_AGP_87Test.class,
  SyncProject_AGP_88Test.class,
  SyncProject_AGP_89Test.class,
  SyncProject_AGP_8_10_Test.class,
  SyncProject_AGP_8_11_Test.class,
})
public class OldAgpTests extends IdeaTestSuiteBase {

  @ClassRule public static MavenRepoRule mavenRepos = MavenRepoRule.fromTestSuiteSystemProperty();

  static {
    IconLoader.activate();
    try {
      IconManager.Companion.activate(new CoreIconManager());
    }
    catch (Throwable e) {
      e.printStackTrace();
    }
  }
}