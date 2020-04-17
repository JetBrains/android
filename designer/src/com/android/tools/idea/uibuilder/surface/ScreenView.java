/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.tools.idea.flags.StudioFlags.NELE_RENDER_DIAGNOSTICS;

import com.android.tools.idea.common.scene.draw.ColorSet;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneLayer;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.imagepool.ImagePool;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.AndroidColorSet;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType;
import com.google.common.collect.ImmutableList;
import java.awt.Dimension;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * View of a device/screen/layout.
 * This is actually painted by {@link ScreenViewLayer}.
 */
public class ScreenView extends ScreenViewBase {
  /**
   * Default {@link Layer} provider to be used if no other is supplied.
   */
  private static final Function<ScreenView, ImmutableList<Layer>> DEFAULT_LAYERS_PROVIDER = (screenView) -> {
    ImmutableList.Builder<Layer> builder = ImmutableList.builder();

    if (screenView.hasBorderLayer()) {
      builder.add(new BorderLayer(screenView));
    }
    builder.add(new ScreenViewLayer(screenView));

    DesignSurface surface = screenView.getSurface();
    SceneLayer sceneLayer = new SceneLayer(surface, screenView, false);
    sceneLayer.setAlwaysShowSelection(true);
    builder.add(sceneLayer);
    if (screenView.myIsResizeable && screenView.getSceneManager().getModel().getType().isEditable()) {
      builder.add(new CanvasResizeLayer(surface, screenView));
    }

    if (NELE_RENDER_DIAGNOSTICS.get()) {
      builder.add(new DiagnosticsLayer(surface));
    }

    return builder.build();
  };

  /**
   * A {@link ScreenView} builder.
   */
  public static class Builder {
    @NotNull final NlDesignSurface surface;
    @NotNull final LayoutlibSceneManager manager;
    boolean useImageSize = false;
    boolean isResizeable = false;
    boolean hasBorderLayer;
    @Nullable ColorSet colorSet = null;
    @NotNull Function<ScreenView, ImmutableList<Layer>> layersProvider = DEFAULT_LAYERS_PROVIDER;

    private Builder(@NotNull NlDesignSurface surface, @NotNull LayoutlibSceneManager manager) {
      this.surface = surface;
      this.manager = manager;
      hasBorderLayer = manager.getModel().getType() instanceof LayoutEditorFileType;
    }

    /**
     * If called, the {@link ScreenView} will use the result rendered image (if available) to determine its size instead of the
     * device configuration.
     */
    @NotNull
    public Builder useImageSize() {
      useImageSize = true;
      return this;
    }

    /**
     * If called, the {@link ScreenView} will display the resize layer.
     */
    @NotNull
    public Builder resizeable() {
      isResizeable = true;
      return this;
    }

    /**
     * Sets a non-default {@link ColorSet} for the {@link ScreenView}
     */
    @NotNull
    public Builder withColorSet(@NotNull ColorSet colorSet) {
      this.colorSet = colorSet;
      return this;
    }

    /**
     * Sets a new provider that will determine the {@link Layer}s to be used.
     */
    @NotNull
    public Builder withLayersProvider(@NotNull Function<ScreenView, ImmutableList<Layer>> layersProvider) {
      this.layersProvider = layersProvider;
      return this;
    }

    /**
     * Disables the visible border.
     */
    @NotNull
    public Builder disableBorder() {
      hasBorderLayer = false;
      return this;
    }

    @NotNull
    public ScreenView build() {
      return new ScreenView(
        surface,
        manager,
        useImageSize,
        isResizeable,
        hasBorderLayer,
        colorSet == null ? new AndroidColorSet() : colorSet,
        layersProvider);
    }
  }

  /**
   * Returns a new {@link ScreenView.Builder}
   * @param surface The {@link NlDesignSurface}.
   * @param manager The {@link LayoutlibSceneManager}.
   */
  @NotNull
  public static Builder newBuilder(@NotNull NlDesignSurface surface, @NotNull LayoutlibSceneManager manager) {
    return new Builder(surface, manager);
  }

  /**
   * Whether this {@link ScreenView} has a {@link BorderLayer}, which should only happen if the file type is a subclass of
   * {@link LayoutEditorFileType}.
   */
  private final boolean myHasBorderLayer;

  /**
   * If true, {@link #getContentSize(Dimension)} will use the rendered image size (if available) instead of the device size.
   */
  private final boolean myUseImageSize;

  /**
   * If true, this ScreenView will incorporate the {@link CanvasResizeInteraction.ResizeLayer}.
   */
  private final boolean myIsResizeable;

  /**
   * The {@link ColorSet} to use for this view.
   */
  @NotNull private final ColorSet myColorSet;

  /**
   * A {@link Layer} provider for this view.
   */
  @NotNull private final Function<ScreenView, ImmutableList<Layer>> myLayersProvider;


  /**
   * Creates a new {@link ScreenView}.
   */
  private ScreenView(
    @NotNull NlDesignSurface surface,
    @NotNull LayoutlibSceneManager manager,
    boolean useImageSize,
    boolean isResizeable,
    boolean hasBorderLayer,
    @NotNull ColorSet colorSet,
    @NotNull Function<ScreenView, ImmutableList<Layer>> layersProvider) {
    super(surface, manager);
    myHasBorderLayer = hasBorderLayer;
    myUseImageSize = useImageSize;
    myIsResizeable = isResizeable;
    myColorSet = colorSet;
    myLayersProvider = layersProvider;
  }

  /**
   * This is a legacy constructor used by {@link com.android.tools.idea.uibuilder.menu.NavigationViewSceneView}.
   * @deprecated Use the {@link #newBuilder(NlDesignSurface, LayoutlibSceneManager)} instead.
   */
  @Deprecated
  protected ScreenView(@NotNull NlDesignSurface surface, @NotNull LayoutlibSceneManager manager) {
    this(surface, manager, false, true, false, new AndroidColorSet(), DEFAULT_LAYERS_PROVIDER);
  }

  @NotNull
  @Override
  public Dimension getContentSize(@Nullable Dimension dimension) {
    if (myUseImageSize) {
      RenderResult result = getSceneManager().getRenderResult();
      if (result != null && result.hasImage()) {
        if (dimension == null) {
          dimension = new Dimension();
        }
        ImagePool.Image image = result.getRenderedImage();
        dimension.setSize(image.getWidth(), image.getHeight());

        return dimension;
      }
    }
    return super.getContentSize(dimension);
  }

  @NotNull
  @Override
  protected ImmutableList<Layer> createLayers() {
    return myLayersProvider.apply(this);
  }

  public boolean hasBorderLayer() {
    return myHasBorderLayer;
  }

  /**
   * Returns if the given {@link RenderResult} is for an error in Layoutlib.
   */
  private static boolean isErrorResult(@NotNull RenderResult result) {
    // If the RenderResult does not have an image, then we probably have an error. If we do, Layoutlib will
    // sometimes return images of 1x1 when exceptions happen. Try to determine if that's the case here.
    return result.getLogger().hasErrors() &&
           (!result.hasImage() ||
            result.getRenderedImage().getWidth() * result.getRenderedImage().getHeight() < 2);
  }

  @Override
  public boolean hasContent() {
    RenderResult result = getSceneManager().getRenderResult();
    return result != null && !isErrorResult(result);
  }

  @Override
  @NotNull
  public ColorSet getColorSet() {
    return myColorSet;
  }
}
