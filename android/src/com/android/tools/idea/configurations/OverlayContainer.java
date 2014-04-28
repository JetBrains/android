/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.idea.rendering.Overlay;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * Interface implemented by a render context that hosts overlays. Usually
 * painted by {@link Overlay#paintOverlays(OverlayContainer, Component, Graphics, int, int)}
 */
public interface OverlayContainer extends RenderContext {
  /** Returns a list of overlays to be shown in this container */
  @Nullable
  List<Overlay> getOverlays();

  /** Returns true if the given tag should be shown as selected */
  boolean isSelected(@NotNull XmlTag tag);

  /**
  * Converts the given rectangle (in model coordinates) to coordinates in the given
  * target component's coordinate system.
  * <p/>
  * Returns a new {@link Rectangle}, so callers are free to modify the result.
  *
  * @param target    the component whose coordinate system the rectangle should be
  *                  translated into
  * @param rectangle the model rectangle to convert
  * @return the rectangle converted to the coordinate system of the target
  */
  @NotNull
  Rectangle fromModel(@NotNull Component target, @NotNull Rectangle rectangle);

  /**
  * Converts the given rectangle (in coordinates relative to the given component)
  * into the equivalent rectangle in model coordinates.
  * <p/>
  * Returns a new {@link Rectangle}, so callers are free to modify the result.
  *
  * @param source    the component which defines the coordinate system of the rectangle
  * @param rectangle the rectangle to be converted into model coordinates
  * @return the rectangle converted to the model coordinate system
  * @see com.intellij.designer.model.RadComponent#toModel(Component, Rectangle)
  */
  @SuppressWarnings("UnusedDeclaration") // here for symmetry with fromModel(); seems likely that we might need this at some point
  @NotNull
  Rectangle toModel(@NotNull Component source, @NotNull Rectangle rectangle);
}
