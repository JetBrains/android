// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.intellij.ide.projectView;

import com.intellij.ide.projectView.impl.ProjectViewPane;
import junit.framework.TestCase;

/**
 * Tests for {@link ProjectView}
 */
public class ProjectViewTest extends TestCase {
  private static String PROJECTVIEW_KEY = "studio.projectview";
  private String myStudioProjectView;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myStudioProjectView = System.getProperty(PROJECTVIEW_KEY);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myStudioProjectView != null) {
        System.setProperty(PROJECTVIEW_KEY, myStudioProjectView);
      }
      else {
        System.clearProperty(PROJECTVIEW_KEY);
      }
    }
    finally {
      super.tearDown();
    }
  }

  /**
   * Verify that default view ID honors studio.projectview
   *
   * @throws Exception
   */
  public void testGetDefaultViewId() throws Exception {
    System.setProperty(PROJECTVIEW_KEY, "true");
    assertEquals(ProjectViewPane.ID, ProjectView.getDefaultViewId());
    System.setProperty(PROJECTVIEW_KEY, "false");
    assertEquals("AndroidView", ProjectView.getDefaultViewId());
    System.clearProperty(PROJECTVIEW_KEY);
    assertEquals("AndroidView", ProjectView.getDefaultViewId());
  }
}