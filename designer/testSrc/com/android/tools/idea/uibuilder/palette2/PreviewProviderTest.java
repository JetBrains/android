/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.palette2;

import com.android.tools.adtui.imagediff.ImageDiffUtil;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.rendering.parsers.ILayoutPullParserFactory;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.palette.NlPaletteModel;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.RELATIVE_LAYOUT;
import static com.google.common.truth.Truth.assertThat;
import static java.io.File.separator;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PreviewProviderTest extends LayoutTestCase {
  private static final float MAX_PERCENT_DIFFERENT = 6.5f;
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
    when(surface.getCurrentSceneView()).thenReturn(screenView);
    when(surface.getScale()).thenReturn(1.0);
    myPreviewProvider = new PreviewProvider(() -> surface, dependencyManager);
    myPreviewProvider.myRenderTimeoutSeconds = Long.MAX_VALUE;
    RenderService.shutdownRenderExecutor(5);
    RenderService.initializeRenderExecutor();
    RenderService.setForTesting(myFacet, new MyRenderService(myFacet));
  }

  @Override
  public void tearDown() throws Exception {
    try {
      RenderService.setForTesting(myFacet, null);
      Disposer.dispose(myPreviewProvider);
      RenderTestUtil.waitForRenderTaskDisposeToFinish();
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
    if (imageAndSize == null) {
      throw new RuntimeException(getTestDataPath());
    }
    File goldenFile = new File(getTestDataPath() + separator + "palette" + separator + "TextView.png");
    BufferedImage goldenImage = ImageIO.read(goldenFile);
    ImageDiffUtil.assertImageSimilar("TextView.png", goldenImage, imageAndSize.image, MAX_PERCENT_DIFFERENT);
    assertThat(imageAndSize.dimension.height).isEqualTo(41);
    assertThat(imageAndSize.dimension.width).isEqualTo(120);
  }

  public void testBug229723WorkAround() throws Exception {
    myPreviewProvider.myRenderTimeoutSeconds = 0L;
    assertNull(myPreviewProvider.renderDragImage(myTextViewItem));
  }

  private Palette loadPalette() throws Exception {
    NlPaletteModel model = NlPaletteModel.get(myFacet);

    try (Reader reader = new InputStreamReader(NlPaletteModel.class.getResourceAsStream(NlLayoutType.LAYOUT.getPaletteFileName()))) {
      model.loadPalette(reader, NlLayoutType.LAYOUT);
    }

    return model.getPalette(NlLayoutType.LAYOUT);
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

  // Disable security manager during tests (for bazel)
  private static class MyRenderService extends RenderService {

    public MyRenderService(@NotNull AndroidFacet facet) {
      super(facet);
    }

    @Nullable
    @Override
    public RenderTask createTask(@Nullable PsiFile psiFile,
                                 @NotNull Configuration configuration,
                                 @NotNull RenderLogger logger,
                                 @Nullable ILayoutPullParserFactory parserFactory) {
      RenderTask task = super.createTask(psiFile, configuration, logger, parserFactory);
      assert task != null;
      task.disableSecurityManager();
      return task;
    }
  }
}
