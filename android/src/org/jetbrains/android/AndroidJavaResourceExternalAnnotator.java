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
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Annotator which puts colors and image icons in the editor gutter when referenced in Java files.
 */
public class AndroidJavaResourceExternalAnnotator extends AndroidResourceExternalAnnotatorBase {

  @Nullable
  @Override
  public FileAnnotationInfo collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
    // Run even when hasErrors is true.
    return collectInformation(file, editor);
  }

  @Nullable
  @Override
  public FileAnnotationInfo collectInformation(@NotNull PsiFile file) {
    // External annotators can also be run in batch mode for analysis, but we do nothing if there's no Editor.
    return null;
  }

  @Nullable
  private static FileAnnotationInfo collectInformation(@NotNull PsiFile file, @NotNull Editor editor) {
    if (!StudioFlags.GUTTER_ICON_ANNOTATOR_IN_BACKGROUND_ENABLED.get()) {
      return null;
    }
    Ref<FileAnnotationInfo> annotationInfoRef = new Ref<>();
    file.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement element) {
        ResourceType type = AndroidPsiUtils.getResourceType(element);
        if (type == ResourceType.COLOR || type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP) {
          AndroidFacet facet = AndroidFacet.getInstance(element);
          if (facet == null) {
            return;
          }
          if (annotationInfoRef.isNull()) {
            annotationInfoRef.set(new FileAnnotationInfo(facet, element.getContainingFile(), editor));
          }
          AndroidPsiUtils.ResourceReferenceType referenceType = AndroidPsiUtils.getResourceReferenceType(element);
          ResourceNamespace namespace =
            referenceType == AndroidPsiUtils.ResourceReferenceType.FRAMEWORK ? ResourceNamespace.ANDROID : ResourceNamespace.RES_AUTO;
          String name = AndroidPsiUtils.getResourceName(element);
          ResourceReference reference = new ResourceReference(namespace, type, name);
          annotationInfoRef.get().getElements().add(new FileAnnotationInfo.AnnotatableElement(reference, element));
        }
      }
    });
    return annotationInfoRef.get();
  }
}
