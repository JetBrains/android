/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.templates;

import com.android.tools.idea.gradle.project.GradleExperimentalSettings;

public abstract class AndroidGradleArtifactsTestCase extends AndroidGradleTestCase {
  private boolean myOriginalLoadAllTestArtifactsValue;

  @Override
  final protected boolean createDefaultProject() {
    return true;
  }

  protected boolean loadAllTestArtifacts() {
    return false;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myOriginalLoadAllTestArtifactsValue = GradleExperimentalSettings.getInstance().LOAD_ALL_TEST_ARTIFACTS;
    GradleExperimentalSettings.getInstance().LOAD_ALL_TEST_ARTIFACTS = loadAllTestArtifacts();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      GradleExperimentalSettings.getInstance().LOAD_ALL_TEST_ARTIFACTS = myOriginalLoadAllTestArtifactsValue;
    }
    finally {
      //noinspection ThrowFromFinallyBlock
      super.tearDown();
    }
  }
}
