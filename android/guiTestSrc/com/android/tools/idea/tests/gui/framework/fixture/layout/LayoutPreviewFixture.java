/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.android.tools.idea.configurations.ConfigurationToolBar;
import com.android.tools.idea.rendering.ImageUtils;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderedImage;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ToolWindowFixture;
import com.intellij.openapi.project.Project;
import org.fest.swing.core.Robot;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Pause;
import org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowForm;
import org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static com.android.tools.idea.tests.gui.framework.GuiTests.SHORT_TIMEOUT;
import static org.junit.Assert.assertNotNull;

/**
 * Fixture wrapping the the layout preview window
 */
public class LayoutPreviewFixture extends ToolWindowFixture implements LayoutFixture {
  private final Project myProject;

  public LayoutPreviewFixture(@NotNull Robot robot, @NotNull Project project) {
    super("Preview", project, robot);
    myProject = project;
  }

  @Override
  @NotNull
  public RenderErrorPanelFixture getRenderErrors() {
    return new RenderErrorPanelFixture(myRobot, this, getContent());
  }

  @Override
  @NotNull
  public ConfigurationToolbarFixture getToolbar() {
    AndroidLayoutPreviewToolWindowForm form = getContent();
    ConfigurationToolBar toolbar = myRobot.finder().findByType(form.getContentPanel(), ConfigurationToolBar.class, true);
    assertNotNull(toolbar);
    return new ConfigurationToolbarFixture(myRobot, this, form, toolbar);
  }

  @NotNull
  private AndroidLayoutPreviewToolWindowForm getContent() {
    activate();
    AndroidLayoutPreviewToolWindowForm form = getManager().getToolWindowForm();
    assertNotNull(form); // because we opened the window with activate() above
    return form;
  }

  @NotNull
  private AndroidLayoutPreviewToolWindowManager getManager() {
    return AndroidLayoutPreviewToolWindowManager.getInstance(myProject);
  }

  @NotNull
  @Override
  public Object waitForRenderToFinish() {
    return waitForNextRenderToFinish(null);
  }

  /** Rendering token used by {@link #waitForRenderToFinish()} */
  private Object myPreviousRender;

  @Override
  public void waitForNextRenderToFinish() {
    myPreviousRender = waitForNextRenderToFinish(myPreviousRender);
  }

  @NotNull
  @Override
  public Object waitForNextRenderToFinish(@Nullable final Object previous) {
    myRobot.waitForIdle();

    Pause.pause(new Condition("Render is pending") {
      @Override
      public boolean test() {
        AndroidLayoutPreviewToolWindowManager manager = getManager();
        return !manager.isRenderPending() && manager.getToolWindowForm() != null && manager.getToolWindowForm().getLastResult() != null
          && manager.getToolWindowForm().getLastResult() != previous;
      }
    }, SHORT_TIMEOUT);

    myRobot.waitForIdle();

    Object token = getManager().getToolWindowForm().getLastResult();
    assert token != null;
    return token;
  }

  @Override
  public void requireRenderSuccessful() {
    waitForRenderToFinish();
    requireRenderSuccessful(false, false);
  }

  @Override
  public void requireRenderSuccessful(boolean allowErrors, boolean allowWarnings) {
    getRenderErrors().requireRenderSuccessful(allowErrors, allowWarnings);
  }

  /**
   * Like {@link #requireThumbnailMatch(ImageFixture, String)} but using a default image fixture
   * @param relativePath path relative to the GUI test data directory where the thumbnail should
   *                     be located (allowed to use either / or File.separator)
   * @throws IOException if there is a problem reading/writing thumbnails
   */
  public void requireThumbnailMatch(@NotNull String relativePath) throws IOException {
    requireThumbnailMatch(new ImageFixture(), relativePath, true);
  }

  /**
   * Checks that the current rendering matches a given pre-recorded thumbnail. When you are writing
   * a text you don't typically have this thumbnail; run the test once, which will then generate
   * the thumbnail you can then copy into the data directory. (If you set the environment variable
   * pointed to by {@link GuiTests#AOSP_SOURCE_PATH} the file will be created directly into your
   * source tree).
   * <p>
   * NOTE: This will test the full painting of the result (e.g. the scaled view with device frame
   * overlays etc; it's painting the actual scaled {@link com.android.tools.idea.rendering.RenderedImage},
   * not just the layoutlib {@link java.awt.image.BufferedImage}. This helps test what the user would
   * actually see. But the actual size of that window may depend on the display size on the test machine,
   * so if layout zooming is turned off, there could be varying amounts of whitespace around the rendering
   * which could lead to thumbnail differences.
   * <p>
   * If the thumbnails do not match, a "diff" image will be created which shows the two thumbnails and
   * the delta between them.
   *
   * @param imageFixture the specific image fixture to use
   * @param relativePath path relative to the GUI test data directory where the thumbnail should
   *                     be located (allowed to use either / or File.separator)
   * @param includeOverlays whether overlays (selection highlighting, include masks etc) should be painted as well
   * @throws IOException if there is a problem reading/writing thumbnails
   */
  public void requireThumbnailMatch(@NotNull ImageFixture imageFixture, @NotNull String relativePath, boolean includeOverlays)
      throws IOException {
    waitForRenderToFinish();
    RenderResult lastResult = getContent().getLastResult();

    assertNotNull("No render result available", lastResult);
    RenderedImage image = lastResult.getImage();
    assertNotNull("No rendered image available", image);

    // Paint the image to the same scaled size that it is currently used
    Dimension scaledSize = image.getScaledSize();
    @SuppressWarnings("UndesirableClassUsage")
    BufferedImage painted = new BufferedImage(scaledSize.width, scaledSize.height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = painted.createGraphics();
    image.paint(g2, 0, 0);
    if (includeOverlays) {
      getContent().getPreviewPanel().paintOverlays(g2);
    }
    g2.dispose();

    // Strip out whitespace so we get the maximum number of useful pixels in the scaled bitmap
    BufferedImage cropped = ImageUtils.cropBlank(painted, null);
    if (cropped == null) {
      cropped = painted; // blank image to begin with
    }

    imageFixture.requireSimilar(relativePath, cropped);
  }
}
