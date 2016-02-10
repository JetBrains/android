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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.configurables.ui.PsdUISettings;
import com.intellij.testFramework.IdeaTestCase;

/**
 * Tests for {@link ArtifactDependencySpecs}.
 */
public class ArtifactDependencySpecsTest extends IdeaTestCase {
  private boolean myShowGroupId;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myShowGroupId = PsdUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID;
  }

  @Override
  public void tearDown() throws Exception {
    try {
      PsdUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID = myShowGroupId;
    }
    finally {
      super.tearDown();
    }
  }

  public void testAsText1() {
    ArtifactDependencySpec spec = ArtifactDependencySpec.create("group:name:version");
    assertNotNull(spec);

    PsdUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID = true;
    assertEquals("group:name:version", ArtifactDependencySpecs.asText(spec));

    PsdUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID = false;
    assertEquals("name:version", ArtifactDependencySpecs.asText(spec));
  }

  public void testAsText2() {
    ArtifactDependencySpec spec = ArtifactDependencySpec.create("group:name");
    assertNotNull(spec);

    PsdUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID = true;
    assertEquals("group:name", ArtifactDependencySpecs.asText(spec));

    PsdUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID = false;
    assertEquals("name", ArtifactDependencySpecs.asText(spec));
  }
}