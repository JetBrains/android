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
package com.android.tools.idea.gradle.structure.model;

import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.intellij.testFramework.IdeaTestCase;

/**
 * Tests for {@link PsArtifactDependencySpec}.
 */
public class PsArtifactDependencySpecTest extends IdeaTestCase {
  private boolean myShowGroupId;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myShowGroupId = PsUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID;
  }

  @Override
  public void tearDown() throws Exception {
    try {
      PsUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID = myShowGroupId;
    }
    finally {
      super.tearDown();
    }
  }

  public void testGetDisplayText1() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("group:name:version");
    assertNotNull(spec);

    PsUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID = true;
    assertEquals("group:name:version", spec.getDisplayText());

    PsUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID = false;
    assertEquals("name:version", spec.getDisplayText());
  }

  public void testGetDisplayText2() {
    PsArtifactDependencySpec spec = PsArtifactDependencySpec.create("group:name");
    assertNotNull(spec);

    PsUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID = true;
    assertEquals("group:name", spec.getDisplayText());

    PsUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID = false;
    assertEquals("name", spec.getDisplayText());
  }

}