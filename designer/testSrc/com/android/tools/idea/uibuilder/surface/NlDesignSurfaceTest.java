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

import static com.android.SdkConstants.ABSOLUTE_LAYOUT;
import static com.android.SdkConstants.BUTTON;
import static com.android.SdkConstants.FRAME_LAYOUT;
import static com.android.SdkConstants.LINEAR_LAYOUT;

import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.resources.Density;
import com.android.tools.adtui.actions.ZoomType;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurfaceActionHandler;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.configurations.Configuration;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.error.RenderIssueProvider;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.Disposer;
import java.awt.Point;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mockito;

public class NlDesignSurfaceTest extends LayoutTestCase {
  private NlDesignSurface mySurface;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mySurface = NlDesignSurface.build(getProject(), getTestRootDisposable());
    mySurface.setSize(1000, 1000);
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
    PropertiesComponent.getInstance().unsetValue(NlScreenViewProvider.Companion.getSCREEN_MODE_PROPERTY());

    // Test the default behavior when there is no setting.
    assertEquals(NlScreenViewProvider.Companion.loadPreferredMode(), NlScreenViewProvider.Companion.getDEFAULT_SCREEN_MODE());

    // Test the save and load functions
    NlScreenViewProvider[] modes = NlScreenViewProvider.values();
    for (NlScreenViewProvider mode : modes) {
      NlScreenViewProvider.Companion.savePreferredMode(mode);
      // The loaded mode should be same as the saved mode
      assertEquals(NlScreenViewProvider.Companion.loadPreferredMode(), mode);
    }

    // Test when the illegal mode is setup. (This happens when removing old mode or renaming the exist mode)
    PropertiesComponent.getInstance().setValue(NlScreenViewProvider.Companion.getSCREEN_MODE_PROPERTY(), "_illegalMode");
    assertEquals(NlScreenViewProvider.Companion.loadPreferredMode(), NlScreenViewProvider.Companion.getDEFAULT_SCREEN_MODE());

    // Test next() function
    assertEquals(NlScreenViewProvider.BLUEPRINT, NlScreenViewProvider.RENDER.next());
    assertEquals(NlScreenViewProvider.RENDER_AND_BLUEPRINT, NlScreenViewProvider.BLUEPRINT.next());
    assertEquals(NlScreenViewProvider.RENDER, NlScreenViewProvider.RENDER_AND_BLUEPRINT.next());
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

    mySurface.requestRender().join();
    assertTrue(mySurface.getSceneManager().getRenderResult().getRenderResult().isSuccess());
    assertFalse(mySurface.getIssueModel().getIssues()
                  .stream()
                  .anyMatch(
                    issue -> issue instanceof RenderIssueProvider.NlRenderIssueWrapper && issue.getSeverity() == HighlightSeverity.ERROR));
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
//    BuildSettings.getInstance(getProject()).setBuildMode(BuildMode.SOURCE_GEN);
    // Avoid rendering any other components (nav bar and similar) so we do not have dependencies on the Material theme
    model.getConfiguration().setTheme("android:Theme.NoTitleBar.Fullscreen");
    mySurface.setModel(model);

    mySurface.requestRender();

    // Now finish the build, and try to build again. The "project is still building" should be gone.
//    BuildSettings.getInstance(getProject()).setBuildMode(null);
    model = modelBuilder.build();
    model.getConfiguration().setTheme("android:Theme.NoTitleBar.Fullscreen");
    mySurface.setModel(model);

    mySurface.requestRender();
    // Because there is a missing view, some other extra errors will be generated about missing styles. This is caused by
    // MockView (which is based on TextView) that depends on some Material styles.
    // We only care about the missing class error.
    assertTrue(mySurface.getIssueModel().getIssues().stream()
                 .anyMatch(issue -> issue.getSummary().startsWith("Missing classes")));
    assertFalse(mySurface.getIssueModel().getIssues().stream()
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
    assertNull(mySurface.getSceneManager().getRenderResult());

    mySurface.setScreenViewProvider(NlScreenViewProvider.RENDER, false);
    mySurface.requestRender();
    assertTrue(mySurface.getSceneManager().getRenderResult().getRenderResult().isSuccess());
    assertNotNull(mySurface.getFocusedSceneView());
    assertNull(mySurface.getSceneManager().getSecondarySceneView());

    mySurface.setScreenViewProvider(NlScreenViewProvider.RENDER_AND_BLUEPRINT, false);
    mySurface.requestRender();
    assertTrue(mySurface.getSceneManager().getRenderResult().getRenderResult().isSuccess());

    SceneView screenView = mySurface.getFocusedSceneView();
    SceneView blueprintView = mySurface.getSceneManager().getSecondarySceneView();
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
    DesignSurfaceActionHandler handler = new NlDesignSurfaceActionHandler(mySurface);
    DataContext dataContext = Mockito.mock(DataContext.class);
    NlComponent button = model.find("cuteLittleButton");
    mySurface.getSelectionModel().setSelection(ImmutableList.of(button));
    handler.performCopy(dataContext);
    handler.performPaste(dataContext);
    NlComponent button2 = model.find("cuteLittleButton2");
    assertNotNull(button2);
    mySurface.getSelectionModel().setSelection(ImmutableList.of(button2));
    handler.performCopy(dataContext);
    handler.performPaste(dataContext);
    NlComponent button3 = model.find("cuteLittleButton3");
    assertNotNull(button3);
  }

  /**
   * Cut a component and check that the id of the new component has been conserved
   */
  public void testCutPasteWithId() {
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
    DesignSurfaceActionHandler handler = new NlDesignSurfaceActionHandler(mySurface);
    DataContext dataContext = Mockito.mock(DataContext.class);
    NlComponent button = model.find("cuteLittleButton");
    mySurface.getSelectionModel().setSelection(ImmutableList.of(button));
    handler.performCut(dataContext);
    handler.performPaste(dataContext);
    assertComponentWithId(model, "cuteLittleButton");
  }

  /**
   * Cut a component and check that the id of the new component has been conserved
   */
  public void testMultipleCutPasteWithId() {
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
    DesignSurfaceActionHandler handler = new NlDesignSurfaceActionHandler(mySurface);
    DataContext dataContext = Mockito.mock(DataContext.class);
    NlComponent button = model.find("cuteLittleButton");
    NlComponent button2 = model.find("cuteLittleButton2");
    NlComponent button3 = model.find("cuteLittleButton3");
    mySurface.getSelectionModel().setSelection(ImmutableList.of(button, button2, button3));
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
    DesignSurfaceActionHandler handler = new NlDesignSurfaceActionHandler(mySurface);
    DataContext dataContext = Mockito.mock(DataContext.class);
    NlComponent button = model.find("cuteLittleButton");
    NlComponent button2 = model.find("cuteLittleButton2");
    NlComponent button3 = model.find("cuteLittleButton3");
    mySurface.getSelectionModel().setSelection(ImmutableList.of(button, button2, button3));
    handler.performCopy(dataContext);
    mySurface.getSelectionModel().clear();
    handler.performPaste(dataContext);
    assertComponentWithId(model, "cuteLittleButton4");
    assertComponentWithId(model, "cuteLittleButton5");
    assertComponentWithId(model, "cuteLittleButton6");
  }

  public void testCutThenCopyWithId() {
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
    DesignSurfaceActionHandler handler = new NlDesignSurfaceActionHandler(mySurface);
    DataContext dataContext = Mockito.mock(DataContext.class);
    NlComponent button = model.find("cuteLittleButton");
    mySurface.getSelectionModel().setSelection(ImmutableList.of(button));
    handler.performCut(dataContext);
    handler.performPaste(dataContext);
    NlComponent button2 = model.find("cuteLittleButton");
    assertNotNull("Component should have been pasted with the id cuteLittleButton", button2);

    mySurface.getSelectionModel().setSelection(ImmutableList.of(button2));
    handler.performCopy(dataContext);
    handler.performPaste(dataContext);
    assertComponentWithId(model, "cuteLittleButton2");
  }

  /**
   * Cut component1, paste it, copy it, cut the copy and paste it.
   * The copy should keep the same id as the first time.
   */
  public void testCutPasteCut() {
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
    DesignSurfaceActionHandler handler = new NlDesignSurfaceActionHandler(mySurface);
    DataContext dataContext = Mockito.mock(DataContext.class);
    NlComponent button = model.find("cuteLittleButton");
    mySurface.getSelectionModel().setSelection(ImmutableList.of(button));
    handler.performCut(dataContext);
    handler.performPaste(dataContext);
    NlComponent buttonCut = model.find("cuteLittleButton");
    assertNotNull("Component should have been pasted with the id cuteLittleButton", buttonCut);

    mySurface.getSelectionModel().setSelection(ImmutableList.of(buttonCut));
    handler.performCopy(dataContext);
    handler.performPaste(dataContext);
    assertComponentWithId(model, "cuteLittleButton2");

    NlComponent buttonCopied = model.find("cuteLittleButton2");
    mySurface.getSelectionModel().setSelection(ImmutableList.of(buttonCopied));
    handler.performCut(dataContext);
    handler.performPaste(dataContext);
    handler.performPaste(dataContext);
    assertNull(model.find("cuteLittleButton4"));
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
    mySurface.setScrollViewSizeAndValidate(1000, 1000);
    mySurface.getZoomController().zoomToFit();
    double origScale = mySurface.getZoomController().getScale();
    assertEquals(origScale, mySurface.getZoomController().getMinScale());

    SceneView view = mySurface.getFocusedSceneView();
    assertEquals(new Point(-122, -122), Coordinates.getAndroidCoordinate(view, mySurface.getScrollPosition()));

    mySurface.getZoomController().zoom(ZoomType.IN);
    double scale = mySurface.getZoomController().getScale();
    assertTrue(scale > origScale);
    assertEquals(new Point(8, 8), Coordinates.getAndroidCoordinate(view, mySurface.getScrollPosition()));

    mySurface.getZoomController().zoom(ZoomType.IN, 100, 100);
    assertTrue(mySurface.getZoomController().getScale() > scale);
    assertEquals(new Point(12, 12), Coordinates.getAndroidCoordinate(view, mySurface.getScrollPosition()));

    mySurface.getZoomController().zoom(ZoomType.OUT, 100, 100);
    assertEquals(new Point(7, 7), Coordinates.getAndroidCoordinate(view, mySurface.getScrollPosition()));
    mySurface.getZoomController().zoom(ZoomType.OUT);
    assertEquals(new Point(-122, -122), Coordinates.getAndroidCoordinate(view, mySurface.getScrollPosition()));
    mySurface.getZoomController().zoom(ZoomType.OUT);

    assertEquals(mySurface.getZoomController().getScale(), origScale);
    mySurface.getZoomController().zoom(ZoomType.OUT);
    assertEquals(mySurface.getZoomController().getScale(), origScale);

    mySurface.setScrollViewSizeAndValidate(2000, 2000);
    assertEquals(1.0, mySurface.getZoomController().getMinScale());

    mySurface.getZoomController().setScale(1.099, 0, 0);
    scale = mySurface.getZoomController().getScale();
    mySurface.getZoomController().zoom(ZoomType.IN);
    assertTrue(mySurface.getZoomController().getScale() > scale);
  }

  public void testZoomHiDPIScreen() {
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
    Configuration config = model.getConfiguration().clone();
    config.getFullConfig().setDensityQualifier(new DensityQualifier(Density.XHIGH));
    model.setConfiguration(config);
    mySurface.setModel(model);
    assertEquals(2.f, mySurface.getSceneManager(model).getSceneScalingFactor());
    mySurface.setScrollViewSizeAndValidate(1000, 1000);
    mySurface.getZoomController().zoomToFit();
    double origScale = mySurface.getZoomController().getScale();
    assertEquals(origScale, mySurface.getZoomController().getMinScale());

    SceneView view = mySurface.getFocusedSceneView();
    assertEquals(new Point(-122, -122), Coordinates.getAndroidCoordinate(view, mySurface.getScrollPosition()));

    mySurface.getZoomController().zoom(ZoomType.IN);
    double scale = mySurface.getZoomController().getScale();
    assertTrue(scale > origScale);
    assertEquals(new Point(-44, -44), Coordinates.getAndroidCoordinate(view, mySurface.getScrollPosition()));

    mySurface.getZoomController().zoom(ZoomType.IN, 100, 100);
    assertTrue(mySurface.getZoomController().getScale() > scale);
    assertEquals(new Point(-29, -29), Coordinates.getAndroidCoordinate(view, mySurface.getScrollPosition()));

    mySurface.getZoomController().zoom(ZoomType.OUT, 100, 100);
    assertEquals(new Point(-43, -43), Coordinates.getAndroidCoordinate(view, mySurface.getScrollPosition()));
    mySurface.getZoomController().zoom(ZoomType.OUT);
    assertEquals(new Point(-122, -122), Coordinates.getAndroidCoordinate(view, mySurface.getScrollPosition()));
    mySurface.getZoomController().zoom(ZoomType.OUT);

    assertEquals(mySurface.getZoomController().getScale(), origScale);
    mySurface.getZoomController().zoom(ZoomType.OUT);
    assertEquals(mySurface.getZoomController().getScale(), origScale);

    mySurface.setScrollViewSizeAndValidate(2000, 2000);
    assertEquals(1.0, mySurface.getZoomController().getMinScale());

    mySurface.getZoomController().setScale(1.099, 0, 0);
    scale = mySurface.getZoomController().getScale();
    mySurface.getZoomController().zoom(ZoomType.IN);
    assertTrue(mySurface.getZoomController().getScale() > scale);
  }

  private static void assertComponentWithId(@NotNull NlModel model, @NotNull String expectedId) {
    NlComponent component = model.find(expectedId);
    assertNotNull("Expected id is \"" +
                  expectedId +
                  "\" but current ids are: " +
                  model.flattenComponents().map(NlComponent::getId).collect(Collectors.joining(", ")),
                  component);
  }

  public void testCanZoomToFit() {
    NlModel model = model("absolute.xml",
                          component(ABSOLUTE_LAYOUT)
                            .withBounds(0, 0, 1000, 1000)
                            .matchParentWidth()
                            .matchParentHeight())
      .build();
    // Avoid rendering any other components (nav bar and similar) so we do not have dependencies on the Material theme
    model.getConfiguration().setTheme("android:Theme.NoTitleBar.Fullscreen");
    mySurface.setModel(model);
    mySurface.setSize(1000, 1000);
    mySurface.doLayout();

    mySurface.getZoomController().zoom(ZoomType.IN);
    assertTrue(mySurface.getZoomController().canZoomOut());
    assertTrue(mySurface.getZoomController().canZoomIn());
    mySurface.getZoomController().setScale(mySurface.getZoomController().getMinScale(), -1, -1);
    assertTrue(mySurface.getZoomController().canZoomIn());
    assertFalse(mySurface.getZoomController().canZoomOut());
    mySurface.getZoomController().zoomToFit();
    assertFalse(mySurface.getZoomController().canZoomToFit());
    assertTrue(mySurface.getZoomController().canZoomIn());
    assertFalse(mySurface.getZoomController().canZoomOut());
  }

  public void testCannotZoomToFit() {
    final NlModel model = model("absolute.xml",
                          component(ABSOLUTE_LAYOUT)
                            .withBounds(0, 0, 1000, 1000)
                            .matchParentWidth()
                            .matchParentHeight())
      .build();

    final int surfaceWidth = 500;
    final int surfaceHeight = 500;

    // First use an empty surface to measure the zoom-to-fit scale.
    NlDesignSurface surface = NlDesignSurface.builder(getProject(), getTestRootDisposable()).build();
    surface.addAndRenderModel(model);
    surface.setSize(surfaceWidth, surfaceHeight);
    surface.doLayout();
    surface.getZoomController().zoomToFit();
    double fitScale = surface.getZoomController().getScale();
    surface.removeModel(model);

    // Create another surface which the minimum scale is larger than fitScale.
    surface = NlDesignSurface.builder(getProject(), getTestRootDisposable())
      .setMinScale(fitScale * 2)
      .build();
    surface.addAndRenderModel(model);
    surface.setSize(surfaceWidth, surfaceHeight);
    surface.doLayout();
    // Cannot zoom lower than min scale.
    surface.getZoomController().zoomToFit();
    assertEquals(fitScale * 2, surface.getZoomController().getScale(), 0.01);
    assertFalse(surface.getZoomController().canZoomToFit());
    surface.removeModel(model);

    // Create another surface which the maximum scale is lower than fitScale.
    surface = NlDesignSurface.builder(getProject(), getTestRootDisposable())
      .setMaxScale(fitScale / 2)
      .build();
    surface.addAndRenderModel(model);
    surface.setSize(surfaceWidth, surfaceHeight);
    surface.doLayout();
    // Cannot zoom larger than max scale.
    surface.getZoomController().zoomToFit();
    assertEquals(fitScale / 2 , surface.getZoomController().getScale(), 0.01);
    assertFalse(surface.getZoomController().canZoomToFit());
    surface.removeModel(model);
  }

  /**
   * Test that we don't have any negative scale in case the windows size becomes too small
   */
  public void testsMinScale() {
    NlModel model = model("absolute.xml",
                          component(ABSOLUTE_LAYOUT)
                            .withBounds(0, 0, 1000, 1000)
                            .matchParentWidth()
                            .matchParentHeight()).build();
    NlDesignSurface surface = mySurface;
    surface.setModel(model);
    surface.setBounds(0, 0, 1000, 1000);
    surface.validate();
    surface.getLayout().layoutContainer(surface);
    surface.validateScrollArea();
    surface.getZoomController().zoomToFit();
    assertEquals(0.5, surface.getZoomController().getScale(), 0.1);

    surface.setBounds(0, 0, 1, 1);
    surface.revalidateScrollArea();
    surface.validate();
    surface.getLayout().layoutContainer(surface);
    surface.getZoomController().zoomToFit();
    assertEquals(0.01, surface.getZoomController().getScale());
  }

  public void testNlSupportedActions() {
    NlDesignSurface surface = mySurface;
    // All NlSupportedActions are supported by default in the NlDesignSurface
    for (NlSupportedActions value : NlSupportedActions.values()) {
      assertTrue(NlSupportedActionsKt.isActionSupported(surface, value));
    }
  }
}
