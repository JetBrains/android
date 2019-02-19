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
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.GutterIconCache;
import com.android.tools.idea.res.ResourceHelper;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import java.awt.Color;
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
        LOG.debug(String.format("Rendering icon for %s in %s.", element.getReference(), fileAnnotationsInfo.getFile()));
      }

      ResourceType type = element.getReference().getResourceType();
      GutterIconRenderer gutterIconRenderer;
      if (type == ResourceType.COLOR) {
        gutterIconRenderer = getColorGutterIconRenderer(resolver, element.getReference(), facet, element.getPsiElement());
      }
      else {
        assert type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP;
        gutterIconRenderer =
          getDrawableGutterIconRenderer(resolver, element.getReference(), facet.getModule().getProject(), facet, configuration);
      }
      if (gutterIconRenderer != null) {
        rendererMap.put(element.getPsiElement(), gutterIconRenderer);
      }
    }
    return rendererMap;
  }

  @Nullable
  private static GutterIconRenderer getDrawableGutterIconRenderer(@NotNull ResourceResolver resourceResolver,
                                                                  @NotNull ResourceReference reference,
                                                                  @NotNull Project project,
                                                                  @NotNull AndroidFacet facet,
                                                                  @NotNull Configuration configuration) {
    ResourceValue drawable = resourceResolver.getResolvedResource(reference);
    VirtualFile bitmap = ResourceHelper.resolveDrawable(resourceResolver, drawable, project);
    bitmap = AndroidAnnotatorUtil.pickBestBitmap(bitmap);
    if (bitmap == null) {
      return null;
    }
    // Updating the GutterIconCache in the background thread to include the icon.
    GutterIconCache.getInstance().getIcon(bitmap, resourceResolver, facet);
    return new com.android.tools.idea.rendering.GutterIconRenderer(resourceResolver, facet, bitmap,
                                                                   configuration);
  }

  @Nullable
  private static GutterIconRenderer getColorGutterIconRenderer(@NotNull ResourceResolver resourceResolver,
                                                               @NotNull ResourceReference reference,
                                                               @NotNull AndroidFacet facet,
                                                               @NotNull PsiElement element) {
    ResourceValue colorValue = resourceResolver.getResolvedResource(reference);
    Color color = ResourceHelper.resolveColor(resourceResolver, colorValue, facet.getModule().getProject());
    if (color == null) {
      return null;
    }
    return new AndroidAnnotatorUtil.ColorRenderer(element, color);
  }

  @Override
  public void apply(@NotNull PsiFile file,
                    @NotNull Map<PsiElement, GutterIconRenderer> iconRendererMap,
                    @NotNull AnnotationHolder holder) {
    iconRendererMap.forEach((k, v) -> {
      if (k.isValid()) {
        holder.createInfoAnnotation(k, null).setGutterIconRenderer(v);
      }
    });
  }

  protected static class FileAnnotationInfo {
    @NotNull private final AndroidFacet myFacet;
    @NotNull private final PsiFile myFile;
    @NotNull private final Editor myEditor;
    private final long myTimestamp;
    @NotNull private final List<AnnotatableElement> myElements;

    FileAnnotationInfo(@NotNull AndroidFacet facet, @NotNull PsiFile file, @NotNull Editor editor) {
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

    static class AnnotatableElement {
      private final ResourceReference myReference;
      private final PsiElement myPsiElement;

      AnnotatableElement(@NotNull ResourceReference reference, @NotNull PsiElement element) {
        myReference = reference;
        myPsiElement = element;
      }

      @NotNull
      public ResourceReference getReference() {
        return myReference;
      }

      @NotNull
      public PsiElement getPsiElement() {
        return myPsiElement;
      }
    }
  }
}
