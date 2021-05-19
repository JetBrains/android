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
package org.jetbrains.android;

import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.GutterIconCache;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for external annotators that place resource icons in the gutter of the editor.
 */
public abstract class AndroidResourceExternalAnnotatorBase
  extends ExternalAnnotator<AndroidResourceExternalAnnotatorBase.FileAnnotationInfo, Map<PsiElement, GutterIconRenderer>> {

  private static final Logger LOG = Logger.getInstance(AndroidResourceExternalAnnotatorBase.class);

  @Nullable
  @Override
  public final FileAnnotationInfo collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
    return collectInformation(file, editor);
  }

  @Nullable
  protected abstract FileAnnotationInfo collectInformation(@NotNull PsiFile file, @NotNull Editor editor);

  @Nullable
  @Override
  public final FileAnnotationInfo collectInformation(@NotNull PsiFile file) {
    // External annotators can also be run in batch mode for analysis, but we do nothing if there's no Editor.
    return null;
  }

  @Nullable
  @Override
  public Map<PsiElement, GutterIconRenderer> doAnnotate(@NotNull FileAnnotationInfo fileAnnotationsInfo) {
    AndroidFacet facet = fileAnnotationsInfo.getFacet();
    Editor editor = fileAnnotationsInfo.getEditor();
    long timestamp = fileAnnotationsInfo.getTimestamp();
    Document document = editor.getDocument();

    Map<PsiElement, GutterIconRenderer> rendererMap = new HashMap<>();
    Configuration configuration = AndroidAnnotatorUtil.pickConfiguration(fileAnnotationsInfo.getFile(), facet);
    if (configuration == null) {
      return null;
    }
    ResourceResolver resolver = configuration.getResourceResolver();
    for (FileAnnotationInfo.AnnotatableElement element : fileAnnotationsInfo.getElements()) {
      ProgressManager.checkCanceled();
      if (editor.isDisposed() || document.getModificationStamp() > timestamp) {
        return null;
      }

      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("Rendering icon for %s in %s.", element.getResource(), fileAnnotationsInfo.getFile()));
      }

      GutterIconRenderer gutterIconRenderer;
      if (element.getResource() != null) {
        gutterIconRenderer =
          getResourceGutterIconRender(element.getResource(), element.getPsiElement(), resolver, facet, configuration);
      }
      else {
        // Inline color
        assert (element.getColor() != null);
        Color color = element.getColor();
        gutterIconRenderer = new AndroidAnnotatorUtil.ColorRenderer(element.getPsiElement(), color, resolver, null, true, configuration);
      }
      if (gutterIconRenderer != null) {
        rendererMap.put(element.getPsiElement(), gutterIconRenderer);
      }
    }
    return rendererMap;
  }

  @Nullable
  private static GutterIconRenderer getResourceGutterIconRender(@NotNull ResourceReference reference,
                                                                @NotNull PsiElement element,
                                                                @NotNull ResourceResolver resolver,
                                                                @NotNull AndroidFacet facet,
                                                                @NotNull Configuration configuration) {
    ResourceType type = reference.getResourceType();
    if (type == ResourceType.COLOR) {
      return getColorGutterIconRenderer(resolver, reference, facet, element, configuration);
    }
    else {
      assert type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP || type == ResourceType.ATTR;
      return getDrawableGutterIconRenderer(element, resolver, reference, facet, configuration);
    }
  }

  @Nullable
  private static GutterIconRenderer getDrawableGutterIconRenderer(@NotNull PsiElement element,
                                                                  @NotNull ResourceResolver resourceResolver,
                                                                  @NotNull ResourceReference reference,
                                                                  @NotNull AndroidFacet facet,
                                                                  @NotNull Configuration configuration) {
    ResourceValue drawable = null;
    if (reference.getResourceType() == ResourceType.ATTR) {
      // Resolve the theme attribute
      ResourceValue resolvedAttribute = resourceResolver.findItemInTheme(reference);
      if (resolvedAttribute == null || resolvedAttribute.getValue() == null) {
        return null;
      }
      ResourceUrl resolvedAttributeUrl = ResourceUrl.parse(resolvedAttribute.getValue());
      if (resolvedAttributeUrl == null) {
        return null;
      }
      ResourceType resolvedAttributeType = resolvedAttributeUrl.type;
      if (resolvedAttributeType == ResourceType.DRAWABLE || resolvedAttributeType == ResourceType.MIPMAP) {
        drawable = resolvedAttribute;
      }
    }
    else if (reference.getResourceType() == ResourceType.DRAWABLE || reference.getResourceType() == ResourceType.MIPMAP) {
      // Resolve the Drawable/MipMap
      drawable = resourceResolver.getResolvedResource(reference);
    }
    if (drawable == null) {
      return null;
    }
    VirtualFile bitmap = AndroidAnnotatorUtil.resolveDrawableFile(drawable, resourceResolver, facet);
    bitmap = AndroidAnnotatorUtil.pickBestBitmap(bitmap);
    if (bitmap != null) {
      // Updating the GutterIconCache in the background thread to include the icon.
      GutterIconCache.getInstance().getIcon(bitmap, resourceResolver, facet);
    }
    return new com.android.tools.idea.rendering.GutterIconRenderer(element, resourceResolver, facet, bitmap, configuration);
  }

  @Nullable
  private static GutterIconRenderer getColorGutterIconRenderer(@NotNull ResourceResolver resourceResolver,
                                                               @NotNull ResourceReference reference,
                                                               @NotNull AndroidFacet facet,
                                                               @NotNull PsiElement element,
                                                               @NotNull Configuration configuration) {
    ResourceValue colorValue = resourceResolver.getResolvedResource(reference);
    Color color = IdeResourcesUtil.resolveColor(resourceResolver, colorValue, facet.getModule().getProject());
    if (color == null) {
      return null;
    }
    // This adds the gutter icon for color reference in xml, java, and kotlin files.
    // For java and kotlin files, it opens color resource picker only and set R.color.[resource_name] or android.R.color.[resource_name].
    // For xml files, it opens custom color palette and color resource picker. (which shows as 2 tabs)
    boolean withCustomColorPalette = AndroidAnnotatorUtil.getFileType(element) == XmlFileType.INSTANCE;
    return new AndroidAnnotatorUtil.ColorRenderer(element, color, resourceResolver, reference, withCustomColorPalette, configuration);
  }

  @Override
  public void apply(@NotNull PsiFile file,
                    @NotNull Map<PsiElement, GutterIconRenderer> iconRendererMap,
                    @NotNull AnnotationHolder holder) {
    iconRendererMap.forEach((k, v) -> {
      if (k.isValid()) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(k).gutterIconRenderer(v).create();
      }
    });
  }

  protected static class FileAnnotationInfo {
    @NotNull private final AndroidFacet myFacet;
    @NotNull private final PsiFile myFile;
    @NotNull private final Editor myEditor;
    private final long myTimestamp;
    @NotNull private final List<AnnotatableElement> myElements;

    public FileAnnotationInfo(@NotNull AndroidFacet facet, @NotNull PsiFile file, @NotNull Editor editor) {
      myFacet = facet;
      myFile = file;
      myEditor = editor;
      myTimestamp = myEditor.getDocument().getModificationStamp();
      myElements = new ArrayList<>();
    }

    @NotNull
    public AndroidFacet getFacet() {
      return myFacet;
    }

    @NotNull
    public PsiFile getFile() {
      return myFile;
    }

    @NotNull
    public List<AnnotatableElement> getElements() {
      return myElements;
    }

    @NotNull
    public Editor getEditor() {
      return myEditor;
    }

    public long getTimestamp() {
      return myTimestamp;
    }

    public static class AnnotatableElement {
      @Nullable private final ResourceReference myReference;
      @NotNull private final PsiElement myPsiElement;
      @Nullable private final Color myColor;

      public AnnotatableElement(@NotNull ResourceReference reference, @NotNull PsiElement element) {
        myReference = reference;
        myPsiElement = element;
        myColor = null;
      }

      AnnotatableElement(@NotNull Color color, @NotNull PsiElement element) {
        myReference = null;
        myPsiElement = element;
        myColor = color;
      }

      @Nullable
      public ResourceReference getResource() {
        return myReference;
      }

      @NotNull
      public PsiElement getPsiElement() {
        return myPsiElement;
      }

      @Nullable
      public Color getColor() {
        return myColor;
      }
    }
  }
}
