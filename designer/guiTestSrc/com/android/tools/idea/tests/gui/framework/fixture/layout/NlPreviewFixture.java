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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.android.tools.idea.rendering.ImageUtils;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderedImage;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.Wait;
import com.android.tools.idea.tests.gui.framework.fixture.ToolWindowFixture;
import com.android.tools.idea.uibuilder.editor.NlPreviewForm;
import com.android.tools.idea.uibuilder.editor.NlPreviewManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.project.Project;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.junit.Assert.assertNotNull;

/**
 * Fixture for the layout editor preview window
 */
public class NlPreviewFixture extends ToolWindowFixture {
  private final Project myProject;
  private final DesignSurfaceFixture myDesignSurfaceFixture;

  public NlPreviewFixture(@NotNull Project project, @NotNull Robot robot) {
    super("Preview", project, robot);
    myProject = project;
    myDesignSurfaceFixture = new DesignSurfaceFixture(robot, getContent().getSurface());
  }

  @NotNull
  public NlConfigurationToolbarFixture getConfigToolbar() {
    ActionToolbar toolbar = myRobot.finder().findByName(getContent().getContentPanel(), "NlConfigToolbar", ActionToolbarImpl.class, false);
    Wait.seconds(30).expecting("Configuration toolbar to be showing").until(() -> toolbar.getComponent().isShowing());
    return new NlConfigurationToolbarFixture(myRobot, getContent().getSurface(), toolbar);
  }

  @NotNull
  private NlPreviewForm getContent() {
    activate();
    NlPreviewForm form = getManager().getPreviewForm();
    assertNotNull(form); // because we opened the window with activate() above
    return form;
  }

  @NotNull
  private NlPreviewManager getManager() {
    return NlPreviewManager.getInstance(myProject);
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
  public void requireThumbnailMatch(@NotNull ImageFixture imageFixture, @NotNull String relativePath, @Deprecated boolean includeOverlays)
    throws IOException {
    myDesignSurfaceFixture.waitForRenderToFinish();
    RenderResult lastResult = getContent().getRenderResult();

    assertNotNull("No render result available", lastResult);
    RenderedImage image = lastResult.getImage();
    assertNotNull("No rendered image available", image);

    // Paint the image to the same scaled size that it is currently used
    Dimension scaledSize = image.getScaledSize();
    @SuppressWarnings("UndesirableClassUsage")
    BufferedImage painted = new BufferedImage(scaledSize.width, scaledSize.height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = painted.createGraphics();
    image.paint(g2, 0, 0);
    g2.dispose();

    // Strip out whitespace so we get the maximum number of useful pixels in the scaled bitmap
    BufferedImage cropped = ImageUtils.cropBlank(painted, null);
    if (cropped == null) {
      cropped = painted; // blank image to begin with
    }

    imageFixture.requireSimilar(relativePath, cropped);
  }

  public void waitForRenderToFinish() {
    waitUntilIsVisible();
    myDesignSurfaceFixture.waitForRenderToFinish();
  }

  public boolean hasRenderErrors() {
    return myDesignSurfaceFixture.hasRenderErrors();
  }

  public boolean errorPanelContains(@NotNull String errorText) {
    return myDesignSurfaceFixture.errorPanelContains(errorText);
  }

  public void performSuggestion(@NotNull String linkText) {
    myDesignSurfaceFixture.performSuggestion(linkText);
  }

  @NotNull
  public NlComponentFixture findView(@NotNull String tag, int occurrence) {
    return myDesignSurfaceFixture.findView(tag, occurrence);
  }
}
