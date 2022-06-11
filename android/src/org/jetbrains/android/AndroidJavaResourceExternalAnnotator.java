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
import com.intellij.openapi.editor.Editor;
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
  protected FileAnnotationInfo collectInformation(@NotNull PsiFile file, @NotNull Editor editor) {
    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return null;
    }
    FileAnnotationInfo annotationInfo = new FileAnnotationInfo(facet, file, editor);
    file.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement element) {
        ResourceType type = AndroidPsiUtils.getResourceType(element);
        if (type == ResourceType.COLOR || type == ResourceType.DRAWABLE || type == ResourceType.MIPMAP) {
          AndroidPsiUtils.ResourceReferenceType referenceType = AndroidPsiUtils.getResourceReferenceType(element);
          ResourceNamespace namespace =
            referenceType == AndroidPsiUtils.ResourceReferenceType.FRAMEWORK ? ResourceNamespace.ANDROID : ResourceNamespace.RES_AUTO;
          String name = AndroidPsiUtils.getResourceName(element);
          ResourceReference reference = new ResourceReference(namespace, type, name);
          annotationInfo.getElements().add(new FileAnnotationInfo.AnnotatableElement(reference, element));
        }
      }
    });
    if (annotationInfo.getElements().isEmpty()) {
      return null;
    }
    return annotationInfo;
  }
}
