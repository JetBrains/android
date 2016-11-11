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
package com.android.tools.idea.uibuilder.surface;

import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.openapi.util.Disposer;

import static com.android.SdkConstants.ABSOLUTE_LAYOUT;
import static org.mockito.Mockito.mock;

public class DesignSurfaceTest extends LayoutTestCase {
  public void testEmptyRenderSuccess() {
    NlModel model = model("absolute.xml",
                          component(ABSOLUTE_LAYOUT)
                            .withBounds(0, 0, 1000, 1000)
                            .matchParentWidth()
                            .matchParentHeight())
      .build();
    DesignSurface surface = new DesignSurface(getProject(), mock(DesignerEditorPanelFacade.class));
    // Avoid rendering any other components (nav bar and similar) so we do not have dependencies on the Material theme
    model.getConfiguration().setTheme("android:Theme.NoTitleBar.Fullscreen");
    surface.setModel(model);
    assertNull(model.getRenderResult());

    surface.requestRender();
    assertTrue(model.getRenderResult().getRenderResult().isSuccess());
    assertTrue(surface.getErrorModel().getIssues().isEmpty());
    Disposer.dispose(surface);
  }

  public void testRenderWhileBuilding() {
    ModelBuilder modelBuilder = model("absolute.xml",
                          component(ABSOLUTE_LAYOUT)
                            .withBounds(0, 0, 1000, 1000)
                            .matchParentWidth()
                            .matchParentHeight()
                            .children(
                              component("custom.view.not.present.yet")
                                .withBounds(100, 100, 100, 100)
                                .matchParentWidth()
                                .matchParentHeight()
                            ));

    NlModel model = modelBuilder.build();
    // Simulate that we are in the middle of a build
    BuildSettings.getInstance(getProject()).setBuildMode(BuildMode.SOURCE_GEN);
    DesignSurface surface = new DesignSurface(getProject(), mock(DesignerEditorPanelFacade.class));
    // Avoid rendering any other components (nav bar and similar) so we do not have dependencies on the Material theme
    model.getConfiguration().setTheme("android:Theme.NoTitleBar.Fullscreen");
    surface.setModel(model);
    assertNull(model.getRenderResult());

    surface.requestRender();
    assertEquals(1, surface.getErrorModel().getIssues().size());
    assertEquals("The project is still building", surface.getErrorModel().getIssues().get(0).getSummary());

    // Now finish the build, and try to build again. The "project is still building" should be gone.
    BuildSettings.getInstance(getProject()).setBuildMode(null);
    model = modelBuilder.build();
    model.getConfiguration().setTheme("android:Theme.NoTitleBar.Fullscreen");
    surface.setModel(model);

    surface.requestRender();
    // Because there is a missing view, some other extra errors will be generated about missing styles. This is caused by
    // MockView (which is based on TextView) that depends on some Material styles.
    // We only care about the missing class error.
    assertTrue(surface.getErrorModel().getIssues().stream()
                   .anyMatch(issue -> issue.getSummary().startsWith("Missing classes")));
    assertFalse(surface.getErrorModel().getIssues().stream()
                 .anyMatch(issue -> issue.getSummary().startsWith("The project is still building")));
    Disposer.dispose(surface);
  }
}