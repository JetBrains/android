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

import static com.android.SdkConstants.ATTR_SHOW_IN;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.TOOLS_URI;

import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.rendering.parsers.PsiXmlFile;
import com.android.tools.idea.rendering.parsers.PsiXmlTag;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.rendering.api.IncludeReference;
import com.android.tools.rendering.parsers.RenderXmlFile;
import com.android.tools.rendering.parsers.RenderXmlTag;
import com.android.utils.SdkUtils;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A studio-specific PSI and VFS backed implementation of {@link IncludeReference}.
 */
public class PsiIncludeReference implements IncludeReference {
  /**
   * The source file of the reference (included from).
   */
  @NotNull
  private final VirtualFile myFromFile;

  /**
   * Creates a new include reference.
   */
  PsiIncludeReference(@NotNull VirtualFile fromFile) {
    myFromFile = fromFile;
  }

  /**
   * Returns the file for the include reference
   *
   * @return the file
   */
  @Override
  @Nullable
  public RenderXmlFile getFromXmlFile(@NotNull Project project) {
    PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(project, myFromFile);
    return psiFile instanceof XmlFile ? new PsiXmlFile((XmlFile)psiFile) : null;
  }

  /**
   * Returns the path for the include reference.
   *
   * @return the path
   */
  @Override
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
  @VisibleForTesting
  String getFromResourceName() {
    return SdkUtils.fileNameToResourceName(myFromFile.getName());
  }

  /**
   * Returns the resource URL for this layout, such as {@code @layout/foo}.
   *
   * @return the resource URL
   */
  @Override
  @NotNull
  public String getFromResourceUrl() {
    return LAYOUT_RESOURCE_PREFIX + getFromResourceName();
  }

  /**
   * Returns an {@link PsiIncludeReference} specified for the given file, or {@link #NONE} if no include should be performed from
   * the given file.
   */
  @NotNull
  public static IncludeReference get(@NotNull RenderXmlFile file, @NotNull RenderResources resolver) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ApplicationManager.getApplication().runReadAction((Computable<IncludeReference>)() -> get(file, resolver));
    }

    ApplicationManager.getApplication().assertReadAccessAllowed();
    RenderXmlTag rootTag = file.getRootTag();
    if (rootTag != null && rootTag.isValid()) {
      String layoutRef = rootTag.getAttributeValue(ATTR_SHOW_IN, TOOLS_URI);
      if (layoutRef != null) {
        ResourceUrl layoutUrl = ResourceUrl.parse(layoutRef);
        if (layoutUrl != null) {
          ResourceValue resValue = IdeResourcesUtil.resolve(resolver, layoutUrl, ((PsiXmlTag)rootTag).getPsiXmlTag());
          if (resValue != null) {
            // TODO: Do some sort of picking based on best configuration.
            // I should make sure I also get a configuration that is compatible with
            // my target include. I could stash it in the include reference.
            VirtualFile source = IdeResourcesUtil.resolveLayout(resolver, resValue);
            if (source != null) {
              return new PsiIncludeReference(source);
            }
          }
        }
      }
    }

    return NONE;
  }
}
