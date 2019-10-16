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
package com.android.tools.idea.npw.assetstudio.wizard;

import com.android.resources.Density;
import com.android.tools.idea.npw.assetstudio.GeneratedIcon;
import com.android.tools.idea.npw.assetstudio.GeneratedImageIcon;
import com.android.tools.idea.npw.assetstudio.IconCategory;
import com.android.tools.idea.npw.assetstudio.TvBannerGenerator;
import com.android.tools.idea.npw.assetstudio.icon.IconGeneratorResult;
import com.android.tools.idea.npw.assetstudio.ui.PreviewIconsPanel;
import com.android.utils.Pair;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

class TvBannerPreviewPanel extends PreviewIconsPanel {
  TvBannerPreviewPanel() {
    super("", Theme.TRANSPARENT);
  }

  /**
   * Overrides the default implementation to show only preview images, sorted by
   * a predefined preview name/category order.
   */
  @Override
  public void showPreviewImages(@NotNull IconGeneratorResult result) {
    // Default
    Collection<GeneratedIcon> generatedIcons = result.getIcons();
    List<Pair<String, BufferedImage>> list = generatedIcons.stream()
      .filter(icon -> icon instanceof GeneratedImageIcon)
      .map(icon -> (GeneratedImageIcon)icon)
      .map(icon -> Pair.of(getPreviewShapeFromId(icon.getName()), icon.getImage()))
      .sorted((pair1, pair2) -> comparePreviewShapes(pair1.getFirst(), pair2.getFirst()))
      .map(pair -> Pair.of(pair.getFirst().displayName, pair.getSecond()))
      .collect(Collectors.toList());
    showPreviewImagesImpl(list);
  }

  protected boolean filterPreviewIcon(@NotNull GeneratedImageIcon icon, @NotNull Density density) {
    return Objects.equals(IconCategory.PREVIEW, icon.getCategory()) && Objects.equals(icon.getDensity(), density);
  }

  private static int comparePreviewShapes(@NotNull TvBannerGenerator.PreviewShape x, @NotNull TvBannerGenerator.PreviewShape y) {
    return Integer.compare(getPreviewShapeDisplayOrder(x), getPreviewShapeDisplayOrder(y));
  }

  private static int getPreviewShapeDisplayOrder(@NotNull TvBannerGenerator.PreviewShape previewShape) {
    switch (previewShape) {
      case ADAPTIVE:
        return 1;
      case LEGACY:
        return 2;
      case NONE:
      default:
        return 1000;  // Arbitrary high value.
    }
  }

  @NotNull
  private static TvBannerGenerator.PreviewShape getPreviewShapeFromId(@NotNull String previewShapeId) {
    for (TvBannerGenerator.PreviewShape shape : TvBannerGenerator.PreviewShape.values()) {
      if (Objects.equals(shape.id, previewShapeId)) {
        return shape;
      }
    }
    return TvBannerGenerator.PreviewShape.ADAPTIVE;
  }
}
