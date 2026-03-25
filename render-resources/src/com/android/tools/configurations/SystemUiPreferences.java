/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.configurations;

import com.android.tools.res.FrameworkOverlay;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Encapsulates the specific System UI and display rendering settings for a Configuration.
 */
public class SystemUiPreferences {
  private float myFontScale = 1f;
  @NotNull private AdaptiveIconShape myAdaptiveShape = AdaptiveIconShape.getDefaultShape();
  private boolean myUseThemedIcon = false;
  private Wallpaper myWallpaper = null;
  private final EnumMap<Configuration.ImageTransformationType, Consumer<BufferedImage>> myImageTransformations =
    new EnumMap<>(Configuration.ImageTransformationType.class);
  private boolean myGestureNav = true;
  private boolean myEdgeToEdge = true;
  private FrameworkOverlay myCutoutOverlay = FrameworkOverlay.CUTOUT_NONE;
  private FrameworkOverlay myDeviceOverlay = null;

  public float getFontScale() { return myFontScale; }
  public void setFontScale(float fontScale) { this.myFontScale = fontScale; }

  @NotNull public AdaptiveIconShape getAdaptiveShape() { return myAdaptiveShape; }
  public void setAdaptiveShape(@NotNull AdaptiveIconShape shape) { this.myAdaptiveShape = shape; }

  public boolean getUseThemedIcon() { return myUseThemedIcon; }
  public void setUseThemedIcon(boolean use) { this.myUseThemedIcon = use; }

  @Nullable public Wallpaper getWallpaper() { return myWallpaper; }
  public void setWallpaper(@Nullable Wallpaper wallpaper) { this.myWallpaper = wallpaper; }

  public void setImageTransformation(@NotNull Configuration.ImageTransformationType type,
                                     @Nullable Consumer<BufferedImage> transformation) {
    if (transformation == null) {
      myImageTransformations.remove(type);
    } else {
      myImageTransformations.put(type, transformation);
    }
  }

  @Nullable public Consumer<BufferedImage> getImageTransformation() {
    if (myImageTransformations.isEmpty()) return null;
    return (image) -> myImageTransformations.values().forEach(c -> c.accept(image));
  }

  public boolean isGestureNav() { return myGestureNav; }
  public void setGestureNav(boolean gestureNav) { this.myGestureNav = gestureNav; }

  public boolean isEdgeToEdge() { return myEdgeToEdge; }
  public void setEdgeToEdge(boolean edgeToEdge) { this.myEdgeToEdge = edgeToEdge; }

  public FrameworkOverlay getCutoutOverlay() { return myCutoutOverlay; }
  public void setCutoutOverlay(FrameworkOverlay overlay) { this.myCutoutOverlay = overlay; }

  public FrameworkOverlay getDeviceOverlay() { return myDeviceOverlay; }
  public void setDeviceOverlay(FrameworkOverlay overlay) { this.myDeviceOverlay = overlay; }

  public void copyFrom(SystemUiPreferences other) {
    this.myFontScale = other.myFontScale;
    this.myAdaptiveShape = other.myAdaptiveShape;
    this.myUseThemedIcon = other.myUseThemedIcon;
    this.myWallpaper = other.myWallpaper;
    this.myGestureNav = other.myGestureNav;
    this.myEdgeToEdge = other.myEdgeToEdge;
    this.myCutoutOverlay = other.myCutoutOverlay;
    this.myDeviceOverlay = other.myDeviceOverlay;
    // Intentionally missing myImageTransformations to preserve exact original backward-compatibility
  }
}
