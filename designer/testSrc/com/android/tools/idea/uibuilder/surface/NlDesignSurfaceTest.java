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

import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurfaceActionHandler;
import com.android.tools.idea.common.surface.ZoomType;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.junit.Assume;
import org.mockito.Mockito;

import java.util.stream.Collectors;

import static com.android.SdkConstants.*;

public class NlDesignSurfaceTest extends LayoutTestCase {
  private NlDesignSurface mySurface;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mySurface = new NlDesignSurface(getProject(), false, getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(mySurface);
      mySurface = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testScreenMode() {
    // Just in case, cleanup current preference to make testing environment consistence.
    PropertiesComponent.getInstance().unsetValue(NlDesignSurface.ScreenMode.SCREEN_MODE_PROPERTY);

    // Test the default behavior when there is no setting.
    assertEquals(NlDesignSurface.ScreenMode.loadPreferredMode(), NlDesignSurface.ScreenMode.DEFAULT_SCREEN_MODE);

    // Test the save and load functions
    NlDesignSurface.ScreenMode[] modes = NlDesignSurface.ScreenMode.values();
    for (NlDesignSurface.ScreenMode mode : modes) {
      NlDesignSurface.ScreenMode.savePreferredMode(mode);
      // The loaded mode should be same as the saved mode
      assertEquals(NlDesignSurface.ScreenMode.loadPreferredMode(), mode);
    }

    // Test when the illegal mode is setup. (This happens when removing old mode or renaming the exist mode)
    PropertiesComponent.getInstance().setValue(NlDesignSurface.ScreenMode.SCREEN_MODE_PROPERTY, "_illegalMode");
    assertEquals(NlDesignSurface.ScreenMode.loadPreferredMode(), NlDesignSurface.ScreenMode.DEFAULT_SCREEN_MODE);

    // Test next() function
    assertEquals(NlDesignSurface.ScreenMode.SCREEN_ONLY.next(), NlDesignSurface.ScreenMode.BLUEPRINT_ONLY);
    assertEquals(NlDesignSurface.ScreenMode.BLUEPRINT_ONLY.next(), NlDesignSurface.ScreenMode.BOTH);
    assertEquals(NlDesignSurface.ScreenMode.BOTH.next(), NlDesignSurface.ScreenMode.SCREEN_ONLY);
  }

  public void testEmptyRenderSuccess() {
    NlModel model = model("absolute.xml",
                          component(ABSOLUTE_LAYOUT)
                            .withBounds(0, 0, 1000, 1000)
                            .matchParentWidth()
                            .matchParentHeight())
      .build();
    // Avoid rendering any other components (nav bar and similar) so we do not have dependencies on the Material theme
    model.getConfiguration().setTheme("android:Theme.NoTitleBar.Fullscreen");
    mySurface.setModel(model);
    assertNull(mySurface.getCurrentSceneView().getSceneManager().getRenderResult());

    mySurface.requestRender();
    assertTrue(mySurface.getCurrentSceneView().getSceneManager().getRenderResult().getRenderResult().isSuccess());
    assertFalse(mySurface.getIssueModel().hasRenderError());
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
    // Avoid rendering any other components (nav bar and similar) so we do not have dependencies on the Material theme
    model.getConfiguration().setTheme("android:Theme.NoTitleBar.Fullscreen");
    mySurface.setModel(model);
    assertNull(mySurface.getCurrentSceneView().getSceneManager().getRenderResult());

    mySurface.requestRender();

    // Now finish the build, and try to build again. The "project is still building" should be gone.
    BuildSettings.getInstance(getProject()).setBuildMode(null);
    model = modelBuilder.build();
    model.getConfiguration().setTheme("android:Theme.NoTitleBar.Fullscreen");
    mySurface.setModel(model);

    mySurface.requestRender();
    // Because there is a missing view, some other extra errors will be generated about missing styles. This is caused by
    // MockView (which is based on TextView) that depends on some Material styles.
    // We only care about the missing class error.
    assertTrue(mySurface.getIssueModel().getNlErrors().stream()
                 .anyMatch(issue -> issue.getSummary().startsWith("Missing classes")));
    assertFalse(mySurface.getIssueModel().getNlErrors().stream()
                  .anyMatch(issue -> issue.getSummary().startsWith("The project is still building")));
  }

  // https://code.google.com/p/android/issues/detail?id=227931
  public void /*test*/ScreenPositioning() {
    mySurface.addNotify();
    mySurface.setBounds(0, 0, 400, 4000);
    mySurface.validate();
    // Process the resize events
    IdeEventQueue.getInstance().flushQueue();

    NlModel model = model("absolute.xml",
                          component(ABSOLUTE_LAYOUT)
                            .withBounds(0, 0, 1000, 1000)
                            .matchParentWidth()
                            .matchParentHeight())
      .build();
    // Avoid rendering any other components (nav bar and similar) so we do not have dependencies on the Material theme
    model.getConfiguration().setTheme("android:Theme.NoTitleBar.Fullscreen");
    mySurface.setModel(model);
    assertNull(mySurface.getCurrentSceneView().getSceneManager().getRenderResult());

    mySurface.setScreenMode(NlDesignSurface.ScreenMode.SCREEN_ONLY, false);
    mySurface.requestRender();
    assertTrue(mySurface.getCurrentSceneView().getSceneManager().getRenderResult().getRenderResult().isSuccess());
    assertNotNull(mySurface.getCurrentSceneView());
    assertNull(mySurface.getBlueprintView());

    mySurface.setScreenMode(NlDesignSurface.ScreenMode.BOTH, false);
    mySurface.requestRender();
    assertTrue(mySurface.getCurrentSceneView().getSceneManager().getRenderResult().getRenderResult().isSuccess());

    ScreenView screenView = mySurface.getCurrentSceneView();
    ScreenView blueprintView = mySurface.getBlueprintView();
    assertNotNull(screenView);
    assertNotNull(blueprintView);

    assertTrue(screenView.getY() < blueprintView.getY());
    mySurface.setBounds(0, 0, 4000, 400);
    mySurface.validate();
    IdeEventQueue.getInstance().flushQueue();
    // Horizontal stack
    assertTrue(screenView.getY() == blueprintView.getY());
    mySurface.removeNotify();
  }

  /**
   * Copy a component and check that the id of the new component has the same
   * base and an incremented number
   */
  public void testCopyPasteWithId() {
    NlModel model = model("my_linear.xml", component(LINEAR_LAYOUT)
      .withBounds(0, 0, 200, 200)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        component(BUTTON)
          .id("@+id/cuteLittleButton")
          .withBounds(100, 100, 100, 100)
          .width("100dp")
          .height("100dp")
      ))
      .build();
    mySurface.setModel(model);
    DesignSurfaceActionHandler handler = new DesignSurfaceActionHandler(mySurface);
    DataContext dataContext = Mockito.mock(DataContext.class);
    NlComponent button = model.find("cuteLittleButton");
    model.getSelectionModel().setSelection(ImmutableList.of(button));
    handler.performCopy(dataContext);
    handler.performPaste(dataContext);
    NlComponent button2 = model.find("cuteLittleButton2");
    assertNotNull(button2);
    model.getSelectionModel().setSelection(ImmutableList.of(button2));
    handler.performCopy(dataContext);
    handler.performPaste(dataContext);
    NlComponent button3 = model.find("cuteLittleButton3");
    assertNotNull(button3);
  }

  /**
   * Cut a component and check that the id of the new component has been conserved
   */
  public void testCutPasteWithId() {
    Assume.assumeFalse("Test is failing on mac, ignoring for now", SystemInfo.isMac); // TODO remove once mac cut is fixed
    NlModel model = model("my_linear.xml", component(LINEAR_LAYOUT)
      .withBounds(0, 0, 200, 200)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        component(BUTTON)
          .id("@+id/cuteLittleButton")
          .withBounds(100, 100, 100, 100)
          .width("100dp")
          .height("100dp")
      ))
      .build();
    mySurface.setModel(model);
    DesignSurfaceActionHandler handler = new DesignSurfaceActionHandler(mySurface);
    DataContext dataContext = Mockito.mock(DataContext.class);
    NlComponent button = model.find("cuteLittleButton");
    model.getSelectionModel().setSelection(ImmutableList.of(button));
    handler.performCut(dataContext);
    handler.performPaste(dataContext);
    assertComponentWithId(model, "cuteLittleButton");
  }

  /**
   * Cut a component and check that the id of the new component has been conserved
   */
  public void testMultipleCutPasteWithId() {
    Assume.assumeFalse("Test is failing on mac, ignoring for now", SystemInfo.isMac); // TODO remove once mac cut is fixed
    NlModel model = model("my_linear.xml", component(LINEAR_LAYOUT)
      .withBounds(0, 0, 200, 200)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        component(BUTTON)
          .id("@+id/cuteLittleButton")
          .withBounds(100, 100, 100, 100)
          .width("100dp")
          .height("100dp"),
        component(BUTTON)
          .id("@+id/cuteLittleButton2")
          .withBounds(100, 100, 100, 100)
          .width("100dp")
          .height("100dp"),
        component(BUTTON)
          .id("@+id/cuteLittleButton3")
          .withBounds(100, 100, 100, 100)
          .width("100dp")
          .height("100dp")
      ))
      .build();
    mySurface.setModel(model);
    DesignSurfaceActionHandler handler = new DesignSurfaceActionHandler(mySurface);
    DataContext dataContext = Mockito.mock(DataContext.class);
    NlComponent button = model.find("cuteLittleButton");
    NlComponent button2 = model.find("cuteLittleButton2");
    NlComponent button3 = model.find("cuteLittleButton3");
    model.getSelectionModel().setSelection(ImmutableList.of(button, button2, button3));
    handler.performCut(dataContext);
    handler.performPaste(dataContext);
    assertComponentWithId(model, "cuteLittleButton");
    assertComponentWithId(model, "cuteLittleButton2");
    assertComponentWithId(model, "cuteLittleButton3");
  }

  /**
   * Cut a component and check that the id of the new component has been conserved
   */
  public void testMultipleCopyPasteWithId() {
    NlModel model = model("my_linear.xml", component(LINEAR_LAYOUT)
      .withBounds(0, 0, 200, 200)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        component(BUTTON)
          .id("@+id/cuteLittleButton")
          .withBounds(100, 100, 100, 100)
          .width("100dp")
          .height("100dp"),
        component(BUTTON)
          .id("@+id/cuteLittleButton2")
          .withBounds(100, 100, 100, 100)
          .width("100dp")
          .height("100dp"),
        component(BUTTON)
          .id("@+id/cuteLittleButton3")
          .withBounds(100, 100, 100, 100)
          .width("100dp")
          .height("100dp")
      ))
      .build();
    mySurface.setModel(model);
    DesignSurfaceActionHandler handler = new DesignSurfaceActionHandler(mySurface);
    DataContext dataContext = Mockito.mock(DataContext.class);
    NlComponent button = model.find("cuteLittleButton");
    NlComponent button2 = model.find("cuteLittleButton2");
    NlComponent button3 = model.find("cuteLittleButton3");
    model.getSelectionModel().setSelection(ImmutableList.of(button, button2, button3));
    handler.performCopy(dataContext);
    handler.performPaste(dataContext);
    assertComponentWithId(model, "cuteLittleButton4");
    assertComponentWithId(model, "cuteLittleButton5");
    assertComponentWithId(model, "cuteLittleButton6");
  }

  public void testCutThenCopyWithId() {
    Assume.assumeFalse("Test is failing on mac, ignoring for now", SystemInfo.isMac); // TODO remove once mac cut is fixed
    NlModel model = model("my_linear.xml", component(LINEAR_LAYOUT)
      .withBounds(0, 0, 200, 200)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        component(BUTTON)
          .id("@+id/cuteLittleButton")
          .withBounds(100, 100, 100, 100)
          .width("100dp")
          .height("100dp")
      ))
      .build();
    mySurface.setModel(model);
    DesignSurfaceActionHandler handler = new DesignSurfaceActionHandler(mySurface);
    DataContext dataContext = Mockito.mock(DataContext.class);
    NlComponent button = model.find("cuteLittleButton");
    model.getSelectionModel().setSelection(ImmutableList.of(button));
    handler.performCut(dataContext);
    handler.performPaste(dataContext);
    NlComponent button2 = model.find("cuteLittleButton");
    assertNotNull("Component should have been pasted with the id cuteLittleButton", button2);

    model.getSelectionModel().setSelection(ImmutableList.of(button2));
    handler.performCopy(dataContext);
    handler.performPaste(dataContext);
    assertComponentWithId(model, "cuteLittleButton2");
  }

  public void testZoom() {
    SyncNlModel model = model("my_linear.xml", component(LINEAR_LAYOUT)
      .withBounds(0, 0, 200, 200)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        component(FRAME_LAYOUT)
          .withBounds(100, 100, 100, 100)
          .width("100dp")
          .height("100dp")
      ))
      .build();
    mySurface.setModel(model);
    mySurface.getScrollPane().setSize(1000, 1000);
    mySurface.zoomToFit();
    double origScale = mySurface.getScale();
    assertEquals(origScale, mySurface.getMinScale());

    mySurface.zoom(ZoomType.IN);
    double scale = mySurface.getScale();
    assertTrue(scale > origScale);

    mySurface.zoom(ZoomType.IN);
    assertTrue(mySurface.getScale() > scale);

    mySurface.zoom(ZoomType.OUT);
    mySurface.zoom(ZoomType.OUT);
    mySurface.zoom(ZoomType.OUT);

    assertEquals(mySurface.getScale(), origScale);
    mySurface.zoom(ZoomType.OUT);
    assertEquals(mySurface.getScale(), origScale);

    mySurface.getScrollPane().setSize(2000, 2000);
    assertEquals(1.0, mySurface.getMinScale());
  }

  private static void assertComponentWithId(@NotNull NlModel model, @NotNull String expectedId) {
    NlComponent component = model.find(expectedId);
    assertNotNull("Expected id is \"" +
                  expectedId +
                  "\" but current ids are: " +
                  model.flattenComponents().map(NlComponent::getId).collect(Collectors.joining(", ")),
                  component);
  }
}
