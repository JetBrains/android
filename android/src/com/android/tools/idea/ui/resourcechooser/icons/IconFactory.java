/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcechooser.icons;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.rendering.RenderTaskContext;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.lint.checks.IconDetector;
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.IconManager;
import com.intellij.util.concurrency.SameThreadExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import static com.android.SdkConstants.DOT_WEBP;
import static com.android.SdkConstants.DOT_XML;

public class IconFactory {
  private final Supplier<RenderTask> myRenderTaskSupplier;

  /**
   * Creates a new AsyncIconFactory. It receives a {@link Supplier<RenderTask>} to lazily obtain instances
   * of {@link RenderTask}.
   */
  public IconFactory(@NotNull Supplier<RenderTask> task) {
    myRenderTaskSupplier = task;
  }

  /**
   * This method will try to load the given icon. If the icon is not in the given path or can not
   * be loaded, null will be returned.
   * This method will only handle png and webp files.
   *
   * @param size             icon size
   * @param checkerboardSize size of the background checkboard
   * @param interpolate      if true, it will set the interpolation hint on the graphics context
   * @param path             Path a drawable (PNG, JPEG, GIF or WEBP)
   * @param resourceValue    resource value pointing to the resource to be rendered
   */
  //TODO: This method should also me made async
  @Nullable
  public Icon createIconFromPath(int size,
                                 int checkerboardSize,
                                 boolean interpolate,
                                 @NotNull String path) {
    if (!IconDetector.isDrawableFile(path) || path.endsWith(DOT_XML)) {
      return null;
    }

    // WebP images for unknown reasons don't load via ImageIcon(path)
    if (path.endsWith(DOT_WEBP)) {
      try {
        BufferedImage image = ImageIO.read(new File(path));
        if (image != null) {
          return new ResourceChooserImageIcon(size, image, checkerboardSize, interpolate);
        }
      }
      catch (IOException ignore) {
      }
    }

    return new ResourceChooserImageIcon(size, new ImageIcon(path).getImage(), checkerboardSize, interpolate);
  }

  /**
   * This method will try to load the given icon and will return a place holder if the icon couldn't be loaded immediately.
   * Once the icon is loaded, onIconLoaded will be called and the returned icon will contain the loaded icon.
   *
   * @param size             icon size
   * @param checkerboardSize size of the background checkboard
   * @param interpolate      if true, it will set the interpolation hint on the graphics context
   * @param resourceValue    resource value pointing to the resource to be rendered
   * @param placeHolderIcon  icon to be used as placeholder while the the asynchronous loading happens
   * @param onIconLoaded     callback that will be called after the icon is loaded
   */
  @NotNull
  public Icon createAsyncIconFromResourceValue(int size,
                                               int checkerboardSize,
                                               boolean interpolate,
                                               @NotNull ResourceValue resourceValue,
                                               @NotNull Icon placeHolderIcon,
                                               @Nullable Runnable onIconLoaded) {
    ResourceType type = resourceValue.getResourceType();
    Icon icon = null;

    if (type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP) {
      // TODO: Attempt to guess size for XML drawables since at least for vectors, we have attributes
      // like android:width, android:height, android:viewportWidth and android:viewportHeight
      // which we can use to get a suitable aspect ratio
      //noinspection UnnecessaryLocalVariable
      int width = size;
      //noinspection UnnecessaryLocalVariable
      int height = size;

      RenderTask renderTask = myRenderTaskSupplier.get();
      renderTask.setOverrideRenderSize(width, height)
        .setMaxRenderSize(width, height);
      ListenableFuture<Icon> futureIcon =
        Futures.transform(renderTask.renderDrawable(resourceValue), (Function<BufferedImage, ResourceChooserImageIcon>)drawable -> {
          if (drawable != null) {
            return new ResourceChooserImageIcon(size, drawable, checkerboardSize, interpolate);
          }

          return null;
        }, SameThreadExecutor.INSTANCE);
      // TODO maybe have a different icon for state list drawable
      // Return the async icon
      return new AsyncIcon(futureIcon, placeHolderIcon, onIconLoaded);
    }
    else if (type == ResourceType.COLOR) {
      RenderTask renderTask = myRenderTaskSupplier.get();
      RenderTaskContext context = renderTask.getContext();
      ResourceResolver resolver = context.getConfiguration().getResourceResolver();
      assert resolver != null;
      List<Color> colors = ResourceHelper.resolveMultipleColors(resolver, resourceValue, context.getModule().getProject());
      if (colors.size() == 1) {
        icon = new ResourceChooserColorIcon(size, colors.get(0), checkerboardSize);
      }
      else if (colors.size() > 1) {
        ResourceChooserColorIcon[] colorIcons = new ResourceChooserColorIcon[colors.size()];
        for (int i = 0; i < colors.size(); i++) {
          int sectionSize = size / colors.size();
          if (i == colors.size() - 1) {
            sectionSize = size - sectionSize * (colors.size() - 1);
          }
          colorIcons[i] = new ResourceChooserColorIcon(sectionSize, size, colors.get(i), checkerboardSize);
        }
        icon = IconManager.getInstance().createRowIcon(colorIcons);
      }
    }

    // Either we couldn't load the icon or the icon was loaded synchronously so return the icon immediately
    if (icon == null) {
      Logger.getInstance(IconFactory.class).warn("Unable to load AsyncIcon for value" + resourceValue);
      icon = placeHolderIcon;
    }
    if (onIconLoaded != null) {
      onIconLoaded.run();
    }
    return icon;
  }
}
