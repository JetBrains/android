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

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.rendering.api.HardwareConfig;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.resources.Density;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.scene.draw.ColorSet;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneLayer;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.flags.StudioFlags;
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
import org.jetbrains.annotations.TestOnly;

/**
 * View of a device/screen/layout.
 * This is actually painted by {@link ScreenViewLayer}.
 */
public class ScreenView extends ScreenViewBase {
  /**
   * Policy for determining the content size of a {@link ScreenView}.
   */
  public interface ContentSizePolicy {
    /**
     * Called by the {@link ScreenView} when it needs to be measured.
     * @param screenView The {@link ScreenView} to measure.
     * @param outDimension A {@link Dimension} to return the size.
     */
    void measure(@NotNull ScreenView screenView, @NotNull Dimension outDimension);

    /**
     * Called by the {@link ScreenView} when it needs to check the content size.
     * @return true if content size is determined.
     */
    default boolean hasContentSize(@NotNull ScreenView screenView) {
      if (!screenView.isVisible()) {
        return false;
      }
      RenderResult result = screenView.getSceneManager().getRenderResult();
      return result != null && !isErrorResult(result);
    }
  }

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

        if (StudioFlags.NELE_DP_SIZED_PREVIEW.get()) {
          float densityRatio = Density.DEFAULT_DENSITY * 1.0f / config.getDensity().getDpiValue();
          int dpWidth = Math.round(config.getScreenWidth() * densityRatio);
          int dpHeight = Math.round(config.getScreenHeight() * densityRatio);
          outDimension.setSize(dpWidth, dpHeight);
        }
        else {
          outDimension.setSize(config.getScreenWidth(), config.getScreenHeight());
        }
      }
    }
  };

  /**
   * {@link ImageContentSizePolicy} that obtains the size from the image render result if available.
   * If not available, it obtains the size from the given delegate.
   */
  public static final class ImageContentSizePolicy implements ContentSizePolicy {
    @NotNull private final ContentSizePolicy mySizePolicyDelegate;
    private Dimension cachedDimension = null;

    public ImageContentSizePolicy(@NotNull ContentSizePolicy delegate) {
      mySizePolicyDelegate = delegate;
    }

    @Override
    public void measure(@NotNull ScreenView screenView, @NotNull Dimension outDimension) {
      RenderResult result = screenView.getSceneManager().getRenderResult();
      if (result != null && result.getSystemRootViews().size() == 1) {
        ViewInfo viewInfo = result.getSystemRootViews().get(0);

        try {
          if (StudioFlags.NELE_DP_SIZED_PREVIEW.get()) {
            outDimension.setSize(Coordinates.pxToDp(screenView, viewInfo.getRight()), Coordinates.pxToDp(screenView, viewInfo.getBottom()));
          } else {
            outDimension.setSize(viewInfo.getRight(), viewInfo.getBottom());
          }
          // Save in case a future render fails. This way we can keep a constant size for failed
          // renders.
          if (cachedDimension == null) {
            cachedDimension = new Dimension(outDimension);
          }
          else {
            cachedDimension.setSize(outDimension);
          }
          return;
        } catch (AssertionError ignored) {
        }
      }

      if (cachedDimension != null) {
        outDimension.setSize(cachedDimension);
        return;
      }

      mySizePolicyDelegate.measure(screenView, outDimension);
    }
  }

  /**
   * Default {@link Layer} provider to be used if no other is supplied.
   */
  private static final Function<ScreenView, ImmutableList<Layer>> DEFAULT_LAYERS_PROVIDER = (screenView) -> {
    ImmutableList.Builder<Layer> builder = ImmutableList.builder();

    if (screenView.hasBorderLayer()) {
      builder.add(new BorderLayer(screenView));
    }
    builder.add(new ScreenViewLayer(screenView));

    DesignSurface<?> surface = screenView.getSurface();
    SceneLayer sceneLayer = new SceneLayer(surface, screenView, false);
    sceneLayer.setAlwaysShowSelection(true);
    builder.add(sceneLayer);

    if(StudioFlags.NELE_OVERLAY_PROVIDER.get()) {
      builder.add(new OverlayLayer(screenView));
    }

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
    @NotNull final NlDesignSurface mySurface;
    @NotNull final LayoutlibSceneManager myManager;
    boolean isResizeable = false;
    boolean hasBorderLayer;
    @Nullable ColorSet myColorSet = null;
    @NotNull Function<ScreenView, ImmutableList<Layer>> myLayersProvider = DEFAULT_LAYERS_PROVIDER;
    @NotNull private ContentSizePolicy myContentSizePolicy = DEVICE_CONTENT_SIZE_POLICY;
    @NotNull private ShapePolicy myShapePolicy = DEVICE_CONFIGURATION_SHAPE_POLICY;

    private Builder(@NotNull NlDesignSurface surface, @NotNull LayoutlibSceneManager manager) {
      this.mySurface = surface;
      this.myManager = manager;
      hasBorderLayer = manager.getModel().getType() instanceof LayoutEditorFileType;
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
      this.myColorSet = colorSet;
      return this;
    }

    /**
     * Sets a new provider that will determine the {@link Layer}s to be used.
     */
    @NotNull
    public Builder withLayersProvider(@NotNull Function<ScreenView, ImmutableList<Layer>> layersProvider) {
      this.myLayersProvider = layersProvider;
      return this;
    }

    /**
     * Sets a new {@link ContentSizePolicy}.
     */
    @NotNull
    public Builder withContentSizePolicy(@NotNull ContentSizePolicy contentSizePolicy) {
      this.myContentSizePolicy = contentSizePolicy;
      return this;
    }

    /**
     * Sets a new {@link ContentSizePolicy}.
     */
    @NotNull
    public Builder withShapePolicy(@NotNull ShapePolicy shapePolicy) {
      this.myShapePolicy = shapePolicy;
      return this;
    }

    /**
     * Sets a new {@link ContentSizePolicy}. The method receives the current policy and returns a new one that can wrap it.
     * Use this method if you want to decorate the current policy and not simply replace it.
     */
    @NotNull
    public Builder decorateContentSizePolicy(@NotNull Function<ContentSizePolicy, ContentSizePolicy> contentSizePolicyProvider) {
      this.myContentSizePolicy = contentSizePolicyProvider.apply(myContentSizePolicy);
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
        mySurface,
        myManager,
        myShapePolicy,
        isResizeable,
        hasBorderLayer,
        myColorSet == null ? new AndroidColorSet() : myColorSet,
        myLayersProvider,
        myContentSizePolicy);
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
  private ScreenView(
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

  /**
   * Returns if the given {@link RenderResult} is for an error in Layoutlib.
   */
  private static boolean isErrorResult(@NotNull RenderResult result) {
    // If the RenderResult does not have an image, then we probably have an error. If we do, Layoutlib will
    // sometimes return images of 1x1 when exceptions happen. Try to determine if that's the case here.
    ImagePool.Image image = result.getRenderedImage();
    return result.getLogger().hasErrors() &&
           (!image.isValid() ||
            image.getWidth() * image.getHeight() < 2);
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
