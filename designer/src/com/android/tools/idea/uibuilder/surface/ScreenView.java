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

import static com.android.tools.idea.common.surface.ShapePolicyKt.SQUARE_SHAPE_POLICY;
import static com.android.tools.idea.flags.StudioFlags.NELE_RENDER_DIAGNOSTICS;

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.rendering.api.HardwareConfig;
import com.android.resources.Density;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.configurations.Configuration;
import com.android.tools.idea.common.scene.draw.ColorSet;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneLayer;
import com.android.tools.idea.common.surface.ShapePolicy;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.AndroidColorSet;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.surface.interaction.CanvasResizeInteraction;
import com.android.tools.idea.uibuilder.surface.layer.BorderLayer;
import com.android.tools.idea.uibuilder.surface.layer.CanvasResizeLayer;
import com.android.tools.idea.uibuilder.surface.layer.DiagnosticsLayer;
import com.android.tools.idea.uibuilder.surface.layer.OverlayLayer;
import com.android.tools.idea.uibuilder.surface.sizepolicy.ContentSizePolicy;
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType;
import com.google.common.collect.ImmutableList;
import java.awt.Dimension;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * View of a device/screen/layout.
 * This is actually painted by {@link ScreenViewLayer}.
 */
public class ScreenView extends ScreenViewBase {

  /**
   * {@link ContentSizePolicy} that uses the device configuration size.
   */
  public static final ContentSizePolicy DEVICE_CONTENT_SIZE_POLICY = (screenView, outDimension) -> {
    Configuration configuration = screenView.getConfiguration();
    Device device = configuration.getCachedDevice();
    if (device != null) {
      State state = configuration.getDeviceState();
      if (state != null) {
        HardwareConfig config =
          new HardwareConfigHelper(device).setOrientation(state.getOrientation()).getConfig();

        float densityRatio = Density.DEFAULT_DENSITY * 1.0f / config.getDensity().getDpiValue();
        int dpWidth = Math.round(config.getScreenWidth() * densityRatio);
        int dpHeight = Math.round(config.getScreenHeight() * densityRatio);
        outDimension.setSize(dpWidth, dpHeight);
      }
    }
  };

  /**
   * Default {@link Layer} provider to be used if no other is supplied.
   */
  static final Function<ScreenView, ImmutableList<Layer>> DEFAULT_LAYERS_PROVIDER = (screenView) -> {
    ImmutableList.Builder<Layer> builder = ImmutableList.builder();

    if (screenView.hasBorderLayer()) {
      builder.add(new BorderLayer(screenView, () -> screenView.getSurface().isRotating()));
    }
    NlDesignSurface surface = screenView.getSurface();
    builder.add(new ScreenViewLayer(screenView, surface, surface::getRotateSurfaceDegree));
    SceneLayer sceneLayer = new SceneLayer(surface, screenView, false);
    sceneLayer.setAlwaysShowSelection(true);
    builder.add(sceneLayer);

    builder.add(new OverlayLayer(screenView, surface::getOverlayConfiguration));

    if (screenView.myIsResizeable && screenView.getSceneManager().getModel().getType().isEditable()) {
      builder.add(new CanvasResizeLayer(screenView, () -> { surface.repaint(); return null; }));
    }

    if (NELE_RENDER_DIAGNOSTICS.get()) {
      builder.add(new DiagnosticsLayer(surface, surface.getProject()));
    }

    return builder.build();
  };

  /**
   * Returns a new {@link ScreenViewBuilder}
   * @param surface The {@link NlDesignSurface}.
   * @param manager The {@link LayoutlibSceneManager}.
   */
  @NotNull
  public static ScreenViewBuilder newBuilder(@NotNull NlDesignSurface surface, @NotNull LayoutlibSceneManager manager) {
    return new ScreenViewBuilder(surface, manager);
  }

  /**
   * Whether this {@link ScreenView} has a {@link BorderLayer}, which should only happen if the file type is a subclass of
   * {@link LayoutEditorFileType}.
   */
  private final boolean myHasBorderLayer;

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

  @NotNull private final ContentSizePolicy myContentSizePolicy;

  /**
   * Creates a new {@link ScreenView}.
   */
  ScreenView(
    @NotNull NlDesignSurface surface,
    @NotNull LayoutlibSceneManager manager,
    @NotNull ShapePolicy shapePolicy,
    boolean isResizeable,
    boolean hasBorderLayer,
    @NotNull ColorSet colorSet,
    @NotNull Function<ScreenView, ImmutableList<Layer>> layersProvider,
    @NotNull ContentSizePolicy contentSizePolicy) {
    super(surface, manager, shapePolicy);
    myHasBorderLayer = hasBorderLayer;
    myIsResizeable = isResizeable;
    myColorSet = colorSet;
    myLayersProvider = layersProvider;
    myContentSizePolicy = contentSizePolicy;
  }

  /**
   * Used for testing only.
   */
  @TestOnly
  public ScreenView(@NotNull NlDesignSurface surface,
                    @NotNull LayoutlibSceneManager manager,
                    @NotNull ContentSizePolicy contentSizePolicy) {
    this(surface, manager, SQUARE_SHAPE_POLICY, true, false, new AndroidColorSet(), DEFAULT_LAYERS_PROVIDER, contentSizePolicy);
  }

  /**
   * Returns the current preferred size for the view.
   *
   * @param dimension optional existing {@link Dimension} instance to be reused. If not null, the values will be set and this instance
   *                  returned.
   */
  @Override
  @NotNull
  public Dimension getContentSize(@Nullable Dimension dimension) {
    if (dimension == null) {
      dimension = new Dimension();
    }

    myContentSizePolicy.measure(this, dimension);
    return dimension;
  }

  @NotNull
  @Override
  protected ImmutableList<Layer> createLayers() {
    return myLayersProvider.apply(this);
  }

  public boolean hasBorderLayer() {
    return myHasBorderLayer;
  }



  @Override
  public boolean hasContentSize() {
    return myContentSizePolicy.hasContentSize(this);
  }

  @Override
  @NotNull
  public ColorSet getColorSet() {
    return myColorSet;
  }

  @Override
  public boolean isResizeable() {
    return myIsResizeable;
  }
}
