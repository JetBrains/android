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

import static com.android.tools.idea.uibuilder.api.PaletteComponentHandler.NO_PREVIEW;

import com.google.common.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.resources.ResourceFolderType;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.rendering.imagepool.ImagePool;
import com.google.common.util.concurrent.Futures;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.StartupUiUtil;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import javax.swing.Icon;
import javax.swing.JComponent;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates a preview image that is used when dragging an item from the palette.
 * If possible a image is generated from the actual Android view. Otherwise we
 * simply generate the image from the icon used in the palette.
 */
public class PreviewProvider implements Disposable {
  @AndroidCoordinate
  private static final int SHADOW_SIZE = 6;
  private static final String PREVIEW_PLACEHOLDER_FILE = "preview.xml";
  private static final String CONTAINER_ID = "TopLevelContainer";
  private static final String LINEAR_LAYOUT = "<LinearLayout\n" +
                                              "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                              "    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n" +
                                              "    android:id=\"@+id/%1$s\"\n" +
                                              "    android:layout_width=\"match_parent\"\n" +
                                              "    android:layout_height=\"wrap_content\"\n" +
                                              "    android:orientation=\"vertical\">\n" +
                                              "  %2$s\n" +
                                              "</LinearLayout>\n";

  private final Supplier<DesignSurface> myDesignSurfaceSupplier;
  private final DependencyManager myDependencyManager;
  private RenderTask myRenderTask;

  @VisibleForTesting
  public long myRenderTaskTimeoutMillis = 300L;
  @VisibleForTesting
  public long myRenderTimeoutMillis = 300L;

  public PreviewProvider(@NotNull Supplier<DesignSurface> supplier, @NotNull DependencyManager manager) {
    myDesignSurfaceSupplier = supplier;
    myDependencyManager = manager;
  }

  @NotNull
  @AndroidCoordinate
  public ImageAndDimension createPreview(@NotNull JComponent component, @NotNull Palette.Item item) {
    Dimension size;
    Image image;
    ScaleContext scaleContext = ScaleContext.create(component);
    Image renderedItem = myDependencyManager.needsLibraryLoad(item) ? null : renderDragImage(item);

    if (renderedItem == null) {
      Icon icon = item.getIcon();
      image = IconLoader.toImage(icon, scaleContext);
    }
    else {
      image = ImageUtil.ensureHiDPI(renderedItem, scaleContext);
    }

    int width = ImageUtil.getRealWidth(image);
    int height = ImageUtil.getRealHeight(image);
    image = ImageUtil.scaleImage(image, getScale());
    size = new Dimension(width, height);

    // Workaround for https://youtrack.jetbrains.com/issue/JRE-224
    boolean inUserScale = !SystemInfo.isWindows || !StartupUiUtil.isJreHiDPI(component);
    BufferedImage bufferedImage = ImageUtil.toBufferedImage(image, inUserScale);

    return new ImageAndDimension(bufferedImage, size);
  }

  @Nullable
  @VisibleForTesting
  BufferedImage renderDragImage(@NotNull Palette.Item item) {
    SceneView sceneView = getSceneView();
    if (sceneView == null) {
      disposeRenderTaskNoWait();
      return null;
    }

    XmlElementFactory elementFactory = XmlElementFactory.getInstance(sceneView.getModel().getProject());
    String xml = item.getDragPreviewXml();
    if (xml.equals(NO_PREVIEW)) {
      return null;
    }

    XmlTag tag;

    try {
      tag = elementFactory.createTagFromText(xml);
    }
    catch (IncorrectOperationException exception) {
      return null;
    }

    NlModel model = sceneView.getSceneManager().getModel();
    NlComponent component = ApplicationManager.getApplication()
      .runWriteAction(
        (Computable<NlComponent>)() -> model.createComponent(sceneView.getSurface(), tag, null, null, InsertType.CREATE_PREVIEW
        ));

    if (component == null) {
      return null;
    }

    // Some components require a parent to render correctly.
    XmlTag componentTag = component.getTag();
    if (componentTag == null) {
      return null;
    }
    xml = String.format(LINEAR_LAYOUT, CONTAINER_ID, componentTag.getText());
    try {
      myRenderTask = getRenderTask(model.getConfiguration()).get(myRenderTaskTimeoutMillis, TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException|ExecutionException|TimeoutException ex) {
      myRenderTask = null;
      return null;
    }

    RenderResult result = renderImage(myRenderTimeoutMillis, myRenderTask, xml);
    disposeRenderTaskNoWait();
    if (result == null) {
      return null;
    }

    ImagePool.Image image = result.getRenderedImage();
    if (!image.isValid()) {
      return null;
    }

    List<ViewInfo> infos = result.getRootViews();
    if (infos.isEmpty()) {
      return null;
    }
    infos = infos.get(0).getChildren();
    if (infos == null || infos.isEmpty()) {
      return null;
    }
    ViewInfo view = infos.get(0);
    if (image.getHeight() < view.getBottom() || image.getWidth() < view.getRight() ||
        view.getBottom() <= view.getTop() || view.getRight() <= view.getLeft()) {
      return null;
    }
    @SwingCoordinate
    int shadowIncrement = 1 + Coordinates.getSwingDimension(sceneView, SHADOW_SIZE);

    BufferedImage imageCopy = image.getCopy();
    if (imageCopy == null) {
      return null;
    }
    try {
      return imageCopy.getSubimage(view.getLeft(),
                                   view.getTop(),
                                   Math.min(view.getRight() + shadowIncrement, image.getWidth()),
                                   Math.min(view.getBottom() + shadowIncrement, image.getHeight()));
    }
    catch (RasterFormatException e) {
      // catch exception
      return null;
    }
  }

  @Nullable
  private static RenderResult renderImage(long renderTimeoutMillis, @Nullable RenderTask renderTask, @NotNull String xml) {
    if (renderTask == null) {
      return null;
    }
    PsiFile file = PsiFileFactory
      .getInstance(renderTask.getContext().getModule().getProject())
      .createFileFromText(PREVIEW_PLACEHOLDER_FILE, XmlFileType.INSTANCE, xml);

    assert file instanceof XmlFile;
    renderTask.setXmlFile((XmlFile)file);
    renderTask.setTransparentBackground();
    renderTask.setDecorations(false);
    renderTask.setRenderingMode(SessionParams.RenderingMode.V_SCROLL);
    renderTask.getContext().setFolderType(ResourceFolderType.LAYOUT);

    renderTask.inflate();
    try {
      return renderTask.render().get(renderTimeoutMillis, TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException | ExecutionException | TimeoutException e) {
      Logger.getInstance(PreviewProvider.class).debug(e);
    }

    return null;
  }

  private double getScale() {
    DesignSurface surface = myDesignSurfaceSupplier.get();
    return surface != null ? surface.getScale() * surface.getScreenScalingFactor() : 1.0;
  }

  @Nullable
  private SceneView getSceneView() {
    DesignSurface surface = myDesignSurfaceSupplier.get();
    return surface != null ? surface.getFocusedSceneView() : null;
  }

  @NotNull
  private Future<RenderTask> getRenderTask(@NotNull Configuration configuration) {
    Module module = configuration.getModule();

    if (myRenderTask != null && myRenderTask.getContext().getModule() == module) {
      return Futures.immediateFuture(myRenderTask);
    }

    disposeRenderTaskNoWait();

    if (module == null) {
      return Futures.immediateFuture(null);
    }
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return Futures.immediateFuture(null);
    }
    RenderService renderService = RenderService.getInstance(module.getProject());
    RenderLogger logger = renderService.createLogger(facet);
    return renderService.taskBuilder(facet, configuration)
      .withLogger(logger)
      .build();
  }

  @Override
  public void dispose() {
    if (myRenderTask != null) {
      // Wait until async dispose finishes
      Futures.getUnchecked(myRenderTask.dispose());
      myRenderTask = null;
    }
  }

  private void disposeRenderTaskNoWait() {
    if (myRenderTask != null) {
      myRenderTask.dispose();
      myRenderTask = null;
    }
  }

  public static class ImageAndDimension {
    public BufferedImage image;
    public Dimension dimension;

    private ImageAndDimension(@NotNull BufferedImage image, @NotNull Dimension dimension) {
      this.image = image;
      this.dimension = dimension;
    }
  }
}
