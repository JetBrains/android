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

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.resources.ResourceFolderType;
import com.android.tools.adtui.ImageUtils;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.model.NlModelHelperKt;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.google.common.util.concurrent.Futures;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RasterFormatException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static com.android.tools.idea.uibuilder.api.PaletteComponentHandler.NO_PREVIEW;

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
  public long myRenderTimeoutSeconds = 1L;

  public PreviewProvider(@NotNull Supplier<DesignSurface> supplier, @NotNull DependencyManager manager) {
    myDesignSurfaceSupplier = supplier;
    myDependencyManager = manager;
  }

  @NotNull
  @AndroidCoordinate
  public ImageAndDimension createPreview(@NotNull JComponent component, @NotNull Palette.Item item) {
    Dimension size;
    BufferedImage image = myDependencyManager.needsLibraryLoad(item) ? null : renderDragImage(item);
    if (image != null) {
      size = new Dimension(image.getWidth(), image.getHeight());
      image = ImageUtils.scale(image, getScale());
    }
    else {
      Icon icon = item.getIcon();
      //noinspection UndesirableClassUsage
      image = new BufferedImage(icon.getIconWidth(),
                                icon.getIconHeight(),
                                BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = (Graphics2D)image.getGraphics();
      icon.paintIcon(component, g2, 0, 0);
      g2.dispose();

      double scale = getScale();
      size = new Dimension((int)(image.getWidth() / scale), (int)(image.getHeight() / scale));
    }
    return new ImageAndDimension(image, size);
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

    NlModel model = sceneView.getModel();
    ViewEditor editor = ViewEditorImpl.getOrCreate(sceneView.getScene());
    NlComponent component = ApplicationManager.getApplication()
      .runWriteAction(
        (Computable<NlComponent>)() -> NlModelHelperKt.createComponent(model, editor, tag, null, null, InsertType.CREATE_PREVIEW
        ));

    if (component == null) {
      return null;
    }

    // Some components require a parent to render correctly.
    xml = String.format(LINEAR_LAYOUT, CONTAINER_ID, component.getTag().getText());

    RenderResult result = renderImage(myRenderTimeoutSeconds, getRenderTask(model.getConfiguration()), xml);
    if (result == null || !result.hasImage()) {
      return null;
    }

    ImagePool.Image image = result.getRenderedImage();
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
  private static RenderResult renderImage(long renderTimeoutSeconds, @Nullable RenderTask renderTask, @NotNull String xml) {
    if (renderTask == null) {
      return null;
    }
    PsiFile file = PsiFileFactory
      .getInstance(renderTask.getContext().getModule().getProject()).createFileFromText(PREVIEW_PLACEHOLDER_FILE, XmlFileType.INSTANCE, xml);

    assert file instanceof XmlFile;
    renderTask.setXmlFile((XmlFile)file);
    renderTask.setOverrideBgColor(UIUtil.TRANSPARENT_COLOR.getRGB());
    renderTask.setDecorations(false);
    renderTask.setRenderingMode(SessionParams.RenderingMode.V_SCROLL);
    renderTask.getContext().setFolderType(ResourceFolderType.LAYOUT);

    renderTask.inflate();
    try {
      return renderTask.render().get(renderTimeoutSeconds, TimeUnit.SECONDS);
    }
    catch (InterruptedException | ExecutionException | TimeoutException e) {
      Logger.getInstance(PreviewProvider.class).debug(e);
    }

    return null;
  }

  private double getScale() {
    DesignSurface surface = myDesignSurfaceSupplier.get();
    return surface != null ? surface.getScale() : 1.0;
  }

  @Nullable
  private SceneView getSceneView() {
    DesignSurface surface = myDesignSurfaceSupplier.get();
    return surface != null ? surface.getCurrentSceneView() : null;
  }

  @Nullable
  private RenderTask getRenderTask(@NotNull Configuration configuration) {
    Module module = configuration.getModule();

    if (myRenderTask == null || myRenderTask.getContext().getModule() != module) {
      disposeRenderTaskNoWait();

      if (module == null) {
        return null;
      }
      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet == null) {
        return null;
      }
      RenderService renderService = RenderService.getInstance(facet);
      RenderLogger logger = renderService.createLogger();
      myRenderTask = renderService.createTask(null, configuration, logger, null);
    }

    return myRenderTask;
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
