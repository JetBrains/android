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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link RenderContext} can provide an optional configuration.
 * This is implemented by for example the Layout Preview window, and
 * the Layout Editor, and is used by the configuration toolbar actions
 * to find the surrounding context.
 */
public interface RenderContext {
  /**
   * Returns the current configuration, if any
   */
  @Nullable
  Configuration getConfiguration();

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
   */
  @Nullable
  VirtualFile getVirtualFile();

  /**
   * Returns the current module
   * @return the module
   */
  @NotNull
  Module getModule();

  /**
   * Types of uses of the {@link com.android.tools.idea.rendering.RenderService} which can drive some decisions, such as how and whether to report {@code <fragment/>}
   * tags without a known preview layout, and so on.
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
     * Some sort of thumnail preview, e.g. in a resource chooser
     */
    THUMBNAIL_PREVIEW
  }
}
