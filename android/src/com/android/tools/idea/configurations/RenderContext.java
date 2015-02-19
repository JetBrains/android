/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.configurations;

import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.rendering.RenderedViewHierarchy;
import com.android.tools.idea.rendering.multi.RenderPreviewManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * A {@link RenderContext} can provide an optional configuration.
 * This is implemented by for example the Layout Preview window, and
 * the Layout Editor, and is used by the configuration toolbar actions
 * to find the surrounding context.
 */
public interface RenderContext {
  /**
   * Returns the current configuration, if any (should never return null when
   * a file is being rendered; can only be null if no file is showing)
   */
  @Nullable
  Configuration getConfiguration();

  /**
   * Sets the given configuration to be used for rendering
   *
   * @param configuration the configuration to use
   */
  void setConfiguration(@NotNull Configuration configuration);

  /**
   * Update the rendering
   */
  void requestRender();

  /**
   * The type of rendering context
   */
  @NotNull
  UsageType getType();

  /**
   * Returns the current XML file, if any
   */
  @Nullable
  XmlFile getXmlFile();

  /**
   * Returns the current virtual file, if any
   * <p>
   * TODO: Get rid of this now that configurations carry file info!
   */
  @Nullable
  VirtualFile getVirtualFile();

  /**
   * Returns the current module
   * @return the module
   */
  @Nullable
  Module getModule();

  /**
   * Returns whether the current scene has an alpha channel
   * @return true if the scene has an alpha channel, false if not or if unknown
   */
  boolean hasAlphaChannel();

  /**
   * Returns the Swing component the rendering is painted into
   * @return the component
   */
  @NotNull
  Component getComponent();

  /** No size, or unknown size */
  Dimension NO_SIZE = new Dimension(0, 0);

  /**
   * Returns the size of the full/original rendered image, without scaling applied
   *
   * @return the image size, or {@link #NO_SIZE}
   */
  @NotNull
  Dimension getFullImageSize();

  /**
   * Returns the size of the (possibly zoomed) image
   *
   * @return the scaled image, or {@link #NO_SIZE}
   */
  @NotNull
  Dimension getScaledImageSize();

  /**
   * Returns the rectangle which defines the client area (the space available for
   * the rendering)
   *
   * @return the client area
   */
  @NotNull
  Rectangle getClientArea();

  /**
   * Returns true if this render context supports render previews
   *
   * @return true if render previews are supported
   */
  boolean supportsPreviews();

  /**
   * Returns the preview manager for this render context, if any. Will only
   * be called if this context returns true from {@link #supportsPreviews()}.
   *
   * @param createIfNecessary if true, create the preview manager if it does not exist, otherwise
   *                          only return it if it has already been created
   * @return the preview manager, or null if it doesn't exist and {@code createIfNecessary} was false
   */
  @Nullable
  RenderPreviewManager getPreviewManager(boolean createIfNecessary);

  /**
   * Sets the rendering size to be at most the given width and the given height.
   *
   * @param width the maximum width, or 0 to use any size
   * @param height the maximum height, or 0 to use any height
   */
  void setMaxSize(int width, int height);

  /**
   * Perform a zoom to fit operation
   *
   * @param onlyZoomOut if true, only adjust the zoom if it would be zooming out
   * @param allowZoomIn if true, allow the zoom factor to be greater than 1 (e.g. bigger than real size)
   */
  void zoomFit(boolean onlyZoomOut, boolean allowZoomIn);

  /**
   * Called when the content of the rendering has changed, so the view
   * should update the layout (to for example recompute zoom fit, if applicable,
   * and to revalidate the components
   */
  void updateLayout();

  /**
   * Sets whether device frames should be shown in the main window. Note that this
   * is a flag which is and'ed with the user's own preference. If device frames are
   * turned off, this flag will have no effect. But if they are on, they can be
   * temporarily turned off with this method. This is used in render preview mode
   * for example to ensure that the previews and the main rendering both agree on
   * whether to show device frames.
   *
   * @param on whether device frames should be enabled or not
   */
  void setDeviceFramesEnabled(boolean on);

  /** Returns the most recent rendered image, if any */
  @Nullable
  BufferedImage getRenderedImage();

  /**
   * Returns the most recent rendered result, if any
   */
  @Nullable
  RenderResult getLastResult();

  @Nullable
  RenderedViewHierarchy getViewHierarchy();

  /**
   * Types of uses of the {@link RenderTask} which can drive some decisions, such as how and whether
   * to report {@code <fragment/>} tags without a known preview layout, and so on.
   */
  enum UsageType {
    /**
     * Not known
     */
    UNKNOWN,
    /**
     * Rendering a preview of the XML file shown in the editor
     */
    XML_PREVIEW,
    /**
     * Layout editor rendering
     */
    LAYOUT_EDITOR,
    /**
     * Some sort of thumbnail preview, e.g. in a resource chooser
     */
    THUMBNAIL_PREVIEW
  }
}
