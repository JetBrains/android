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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.rendering.GutterIconCache;
import com.android.tools.idea.res.ResourceHelper;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReferenceExpression;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Annotator which puts colors and image icons in the editor gutter when referenced in Java files.
 */
public class AndroidJavaResourceExternalAnnotator extends
                                                  ExternalAnnotator<AndroidJavaResourceExternalAnnotator.FileAnnotationInfo,
                                                    Map<PsiElement, GutterIconRenderer>> {

  @Nullable
  @Override
  public FileAnnotationInfo collectInformation(@NotNull PsiFile file) {
    if (!StudioFlags.GUTTER_ICON_ANNOTATOR_IN_BACKGROUND_ENABLED.get()) {
      return null;
    }
    Ref<FileAnnotationInfo> annotationInfoRef = new Ref<>();
    file.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        if (element instanceof PsiReferenceExpression) {
          ResourceType type = AndroidPsiUtils.getResourceType(element);
          if (type == ResourceType.COLOR || type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP) {
            AndroidFacet facet = AndroidFacet.getInstance(element);
            if (facet == null) {
              return;
            }
            annotationInfoRef.setIfNull(new FileAnnotationInfo(facet, element.getContainingFile()));
            AndroidPsiUtils.ResourceReferenceType referenceType = AndroidPsiUtils.getResourceReferenceType(element);
            ResourceNamespace namespace =
              referenceType == AndroidPsiUtils.ResourceReferenceType.FRAMEWORK ? ResourceNamespace.ANDROID : ResourceNamespace.RES_AUTO;
            String name = AndroidPsiUtils.getResourceName(element);
            ResourceReference reference = new ResourceReference(namespace, type, name);
            annotationInfoRef.get().myElements.add(new FileAnnotationInfo.AnnotatableElement(reference, element));
          }
        }
      }
    });
    return annotationInfoRef.get();
  }

  @Nullable
  @Override
  public Map<PsiElement, GutterIconRenderer> doAnnotate(FileAnnotationInfo fileAnnotationsInfo) {
    Map<PsiElement, GutterIconRenderer> rendererMap = new HashMap<>();
    Configuration configuration =
      AndroidAnnotatorUtil.pickConfiguration(fileAnnotationsInfo.getFile(), fileAnnotationsInfo.getFacet());
    if (configuration == null) {
      return null;
    }
    ResourceResolver resolver = configuration.getResourceResolver();
    for (FileAnnotationInfo.AnnotatableElement element : fileAnnotationsInfo.myElements) {
      ProgressManager.checkCanceled();
      ResourceType type = element.myReference.getResourceType();
      GutterIconRenderer gutterIconRenderer;
      if (type == ResourceType.COLOR) {
        gutterIconRenderer = getColorGutterIconRenderer(resolver, element.myReference, fileAnnotationsInfo.getFacet(), element.myPsiElement);
      }
      else {
        assert type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP;
        gutterIconRenderer =
          getDrawableGutterIconRenderer(resolver, element.myReference, fileAnnotationsInfo.getFacet().getModule().getProject(),
                                        fileAnnotationsInfo.getFacet(), configuration);
      }
      if (gutterIconRenderer != null) {
        rendererMap.put(element.myPsiElement, gutterIconRenderer);
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
                    Map<PsiElement, GutterIconRenderer> iconRendererMap,
                    @NotNull AnnotationHolder holder) {
    iconRendererMap.forEach((k, v) -> {
      if (k.isValid()) {
        holder.createInfoAnnotation(k, null).setGutterIconRenderer(v);
      }
    });
  }

  static class FileAnnotationInfo {
    private final AndroidFacet myFacet;
    private final PsiFile myFile;
    final List<AnnotatableElement> myElements;

    FileAnnotationInfo(AndroidFacet facet, PsiFile file) {
      myFacet = facet;
      myFile = file;
      myElements = new ArrayList<>();
    }

    public AndroidFacet getFacet() {
      return myFacet;
    }

    public PsiFile getFile() {
      return myFile;
    }

    static class AnnotatableElement {
      final ResourceReference myReference;
      final PsiElement myPsiElement;

      AnnotatableElement(ResourceReference reference, PsiElement element) {
        myReference = reference;
        myPsiElement = element;
      }
    }
  }
}
