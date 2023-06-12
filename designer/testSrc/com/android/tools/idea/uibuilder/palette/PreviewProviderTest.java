/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.palette;

import static com.android.SdkConstants.RELATIVE_LAYOUT;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.io.Images;
import com.android.testutils.ImageDiffUtil;
import com.android.testutils.TestUtils;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.NoSecurityManagerRenderService;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.StudioRenderService;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.idea.uibuilder.type.LayoutFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.StartupUiUtil;
import icons.StudioIcons;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PreviewProviderTest extends LayoutTestCase {
  private static final float MAX_PERCENT_DIFFERENT = 6.5f;
  private static final String TEST_DATA_PATH = "tools/adt/idea/designer/testData/palette";
  private Palette.Item myTextViewItem;
  private JComponent myComponent;
  private PreviewProvider myPreviewProvider;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    Palette palette = loadPalette();
    List<Palette.Item> items = new ArrayList<>();
    palette.accept(items::add);
    myTextViewItem = items.stream()
      .filter(item -> item.getTagName().equals("TextView"))
      .findFirst()
      .orElse(null);
    myComponent = new JPanel();

    DependencyManager dependencyManager = mock(DependencyManager.class);
    SyncNlModel model = createModel();
    ScreenView screenView = screen(model).getScreen();
    NlDesignSurface surface = mock(NlDesignSurface.class);
    when(surface.getFocusedSceneView()).thenReturn(screenView);
    when(surface.getScale()).thenReturn(1.0);
    when(surface.getScreenScalingFactor()).thenReturn(1.0);
    LayoutlibSceneManager manager = (LayoutlibSceneManager)model.getSurface().getSceneManager();
    when(manager.getSceneScalingFactor()).thenReturn(1.0f);
    myPreviewProvider = new PreviewProvider(() -> surface, dependencyManager);
    myPreviewProvider.setRenderTimeoutMillis(TimeUnit.MINUTES.toMillis(1));
    RenderService.shutdownRenderExecutor(5);
    RenderService.initializeRenderExecutor();
    StudioRenderService.setForTesting(getProject(), NoSecurityManagerRenderService.createNoSecurityRenderService());
    IconLoader.activate();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      StudioRenderService.setForTesting(getProject(), null);
      IconLoader.deactivate();
      myPreviewProvider = null;
      myTextViewItem = null;
      myComponent = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testCreatePreviewOfTextView() throws Exception {
    PreviewProvider.ImageAndDimension imageAndSize = myPreviewProvider.createPreview(myComponent, myTextViewItem);
    Path goldenFile = TestUtils.resolveWorkspacePath(TEST_DATA_PATH).resolve("TextView.png");
    Image expected = ImageUtil.scaleImage(Images.readImage(goldenFile), 1.0f / JBUIScale.sysScale());
    BufferedImage buffered = ImageUtil.toBufferedImage(expected, false);
    ImageDiffUtil.assertImageSimilar("TextView", buffered, imageAndSize.getImage(), MAX_PERCENT_DIFFERENT);
    assertThat(imageAndSize.getDimension().height).isEqualTo(42);
    assertThat(imageAndSize.getDimension().width).isEqualTo(119);
    waitFor(imageAndSize.getRendering());
    waitFor(imageAndSize.getDisposal());
  }

  public void testRenderTaskTimeOutReturnsIconForDragImage() throws Exception {
    myPreviewProvider.setRenderTimeoutMillis(0L);
    PreviewProvider.ImageAndDimension imageAndSize = myPreviewProvider.createPreview(myComponent, myTextViewItem);
    boolean inUserScale = !SystemInfo.isWindows || !StartupUiUtil.isJreHiDPI(myComponent);
    BufferedImage expected = ImageUtil.toBufferedImage(
      Objects.requireNonNull(IconLoader.toImage(StudioIcons.LayoutEditor.Palette.TEXT_VIEW)), inUserScale);
    ImageDiffUtil.assertImageSimilar("TextViewIcon.png", expected, imageAndSize.getImage(), MAX_PERCENT_DIFFERENT);
    assertThat(imageAndSize.getDimension().height).isEqualTo((int)(16 * JBUIScale.sysScale()));
    assertThat(imageAndSize.getDimension().width).isEqualTo((int)(16 * JBUIScale.sysScale()));
    waitFor(imageAndSize.getRendering());
    waitFor(imageAndSize.getDisposal());
  }

  private Palette loadPalette() {
    NlPaletteModel model = NlPaletteModel.get(myFacet);
    return model.getPalette(LayoutFileType.INSTANCE);
  }

  @NotNull
  private SyncNlModel createModel() {
    ModelBuilder builder = model("relative.xml",
                                 component(RELATIVE_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight());
    return builder.build();
  }

  private static void waitFor(@Nullable Future<?> future) throws Exception {
    if (future != null) {
      future.get();
    }
  }
}
