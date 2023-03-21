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
package com.android.tools.idea.rendering;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.utils.SdkUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.SdkConstants.*;

/**
 * A reference to a particular file in the project
 */
public class IncludeReference {
  @SuppressWarnings("ConstantConditions")
  public static final IncludeReference NONE = new IncludeReference(null);

  /**
   * The source file of the reference (included from).
   */
  @NotNull
  private final VirtualFile myFromFile;

  /**
   * Creates a new include reference.
   */
  private IncludeReference(@NonNull VirtualFile fromFile) {
    myFromFile = fromFile;
  }

  /**
   * Creates a new include reference.
   */
  public static IncludeReference create(@NonNull VirtualFile fromFile) {
    return new IncludeReference(fromFile);
  }

  /**
   * Returns the file for the include reference
   *
   * @return the file
   */
  @NotNull
  public VirtualFile getFromFile() {
    return myFromFile;
  }

  /**
   * Returns the path for the include reference.
   *
   * @return the path
   */
  @NotNull
  public File getFromPath() {
    return VfsUtilCore.virtualToIoFile(myFromFile);
  }

  /**
   * Returns the resource name of this layout.
   *
   * @return the resource name
   */
  @NotNull
  public String getFromResourceName() {
    return SdkUtils.fileNameToResourceName(myFromFile.getName());
  }

  /**
   * Returns the resource URL for this layout, such as {@code @layout/foo}.
   *
   * @return the resource URL
   */
  @NotNull
  public String getFromResourceUrl() {
    return LAYOUT_RESOURCE_PREFIX + getFromResourceName();
  }

  public static void setIncludingLayout(@NotNull Project project, @NotNull XmlFile xmlFile, @Nullable String layout) {
    XmlTag tag = xmlFile.getRootTag();
    if (tag != null) {
      SetAttributeFix fix = new SetAttributeFix(tag, ATTR_SHOW_IN, TOOLS_URI, layout);
      fix.executeCommand();
    }
  }

  /**
   * Returns an {@link IncludeReference} specified for the given file, or {@link #NONE} if no include should be performed from
   * the given file.
   */
  @NotNull
  public static IncludeReference get(@NotNull XmlFile file, @NotNull RenderResources resolver) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ApplicationManager.getApplication().runReadAction((Computable<IncludeReference>)() -> get(file, resolver));
    }

    ApplicationManager.getApplication().assertReadAccessAllowed();
    XmlTag rootTag = file.getRootTag();
    if (rootTag != null && rootTag.isValid()) {
      String layoutRef = rootTag.getAttributeValue(ATTR_SHOW_IN, TOOLS_URI);
      if (layoutRef != null) {
        ResourceUrl layoutUrl = ResourceUrl.parse(layoutRef);
        if (layoutUrl != null) {
          ResourceValue resValue = IdeResourcesUtil.resolve(resolver, layoutUrl, rootTag);
          if (resValue != null) {
            // TODO: Do some sort of picking based on best configuration.
            // I should make sure I also get a configuration that is compatible with
            // my target include. I could stash it in the include reference.
            VirtualFile source = IdeResourcesUtil.resolveLayout(resolver, resValue);
            if (source != null) {
              return create(source);
            }
          }
        }
      }
    }

    return NONE;
  }
}
