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

import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneLayer;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.imagepool.ImagePool;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType;
import com.google.common.collect.ImmutableList;
import java.awt.Dimension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * View of a device/screen/layout.
 * This is actually painted by {@link ScreenViewLayer}.
 */
public class ScreenView extends ScreenViewBase {

  /**
   * Whether this {@link ScreenView} has a {@link BorderLayer}, which should only happen if the file type is a subclass of
   * {@link LayoutEditorFileType}.
   */
  private final boolean myHasBorderLayer;

  private final boolean myUseImageSize;

  private final boolean myIsResizeable;


  /**
   * Creates a new {@link ScreenView}.
   * @param surface The {@link NlDesignSurface}.
   * @param manager The {@link LayoutlibSceneManager}.
   * @param useImageSize If true, the ScreenView will be sized as the render image result instead of using the device
   *                     configuration.
   * @param isResizeable If true, this ScreenView canvas will allow to be resized for files that support it. When false, the resizing
   *                     target will not be displayed even if the file does support it.
   */
  // TODO(b/139046812): Replace this with a builder
  public ScreenView(@NotNull NlDesignSurface surface, @NotNull LayoutlibSceneManager manager, boolean useImageSize, boolean isResizeable) {
    super(surface, manager);
    myHasBorderLayer = manager.getModel().getType() instanceof LayoutEditorFileType;
    this.myUseImageSize = useImageSize;
    this.myIsResizeable = isResizeable;
  }

  public ScreenView(@NotNull NlDesignSurface surface, @NotNull LayoutlibSceneManager manager) {
    this(surface, manager, false, true);
  }

  @NotNull
  @Override
  public Dimension getPreferredSize(@Nullable Dimension dimension) {
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
    return super.getPreferredSize(dimension);
  }

  @NotNull
  @Override
  protected ImmutableList<Layer> createLayers() {
    ImmutableList.Builder<Layer> builder = ImmutableList.builder();

    if (myHasBorderLayer) {
      builder.add(new BorderLayer(this));
    }
    if (getSurface().isShowModelNames()) {
      builder.add(new ModelNameLayer(this));
    }
    builder.add(new ScreenViewLayer(this));

    SceneLayer sceneLayer = new SceneLayer(getSurface(), this, false);
    sceneLayer.setAlwaysShowSelection(true);
    builder.add(sceneLayer);
    if (myIsResizeable && getSceneManager().getModel().getType().isEditable()) {
      builder.add(new CanvasResizeLayer(getSurface(), this));
    }

    if (NELE_RENDER_DIAGNOSTICS.get()) {
      builder.add(new DiagnosticsLayer(getSurface()));
    }
    return builder.build();
  }

  public boolean hasBorderLayer() {
    return myHasBorderLayer;
  }
}
