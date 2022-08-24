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

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementWalkingVisitor;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.awt.Color;
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Annotator which puts colors and image icons in the editor gutter when referenced in XML files.
 */
public class AndroidXMLResourceExternalAnnotator extends AndroidResourceExternalAnnotatorBase {

  @Override
  @Nullable
  protected FileAnnotationInfo collectInformation(@NotNull PsiFile file, @NotNull Editor editor) {
    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return null;
    }
    FileAnnotationInfo annotationInfo = new FileAnnotationInfo(facet, file, editor);
    if (IdeResourcesUtil.isInResourceSubdirectoryInAnyVariant(file, SdkConstants.FD_RES_VALUES)) {
      // Only look at XMLTag contents
      file.accept(new XmlRecursiveElementWalkingVisitor() {
        @Override
        public void visitXmlTag(@NotNull XmlTag tag) {
          super.visitXmlTag(tag);
          FileAnnotationInfo.AnnotatableElement annotatableElement = getAnnotatableElement(tag.getValue().getText().trim(), tag);
          if (annotatableElement != null) {
            annotationInfo.getElements().add(annotatableElement);
          }
        }
      });
    }
    else if (IdeResourcesUtil.isInResourceSubdirectoryInAnyVariant(file, null) || ManifestDomFileDescription.isManifestFile((XmlFile)file)) {
      // Only look at XMLAttributeValues
      file.accept(new XmlRecursiveElementWalkingVisitor() {
        @Override
        public void visitXmlAttributeValue(@NotNull XmlAttributeValue attributeValue) {
          super.visitXmlAttributeValue(attributeValue);
          FileAnnotationInfo.AnnotatableElement annotatableElement = getAnnotatableElement(attributeValue.getValue(), attributeValue);
          if (annotatableElement != null) {
            annotationInfo.getElements().add(annotatableElement);
          }
        }
      });
    }
    if (annotationInfo.getElements().isEmpty()) {
      return null;
    }
    return annotationInfo;
  }

  @Nullable
  private static FileAnnotationInfo.AnnotatableElement getAnnotatableElement(@NotNull String value, @NotNull XmlElement element) {
    value = value.trim();
    if (value.isEmpty()) {
      return null;
    }

    char startChar = value.charAt(0);
    if (startChar != '@' && startChar != '#' && startChar != '?') {
      return null;
    }
    ResourceUrl resourceUrl = ResourceUrl.parse(value);
    if (resourceUrl == null) {
      if (value.startsWith("#")) {
        // Is an inline color
        Color color = IdeResourcesUtil.parseColor(value);
        if (color == null) {
          return null;
        }
        return new FileAnnotationInfo.AnnotatableElement(color, element);
      }
    }
    else {
      ResourceReference reference = IdeResourcesUtil.resolve(resourceUrl, element);
      if (reference == null) {
        return null;
      }
      ResourceType type = reference.getResourceType();
      if (type != ResourceType.COLOR &&
          type != ResourceType.DRAWABLE &&
          type != ResourceType.MIPMAP &&
          type != ResourceType.ATTR &&
          type != ResourceType.MACRO) {
        return null;
      }
      return new FileAnnotationInfo.AnnotatableElement(reference, element);
    }
    return null;
  }
}
