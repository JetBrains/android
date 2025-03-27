/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.dom.raw

import com.android.SdkConstants
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.res.resolve
import com.intellij.ide.EssentialHighlightingMode
import com.intellij.openapi.editor.Editor
import com.intellij.patterns.XmlAttributeValuePattern
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlRecursiveElementWalkingVisitor
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.AndroidResourceExternalAnnotatorBase
import org.jetbrains.android.AndroidResourceExternalAnnotatorBase.FileAnnotationInfo.AnnotatableElement
import org.jetbrains.android.dom.isDeclarativeWatchFaceFile
import org.jetbrains.android.facet.AndroidFacet

/**
 * An Annotator that puts icons in the gutter for drawables referenced in a Declarative Watch Face
 * file (in res/raw) to help the user recognise which drawables are being used. The drawables
 * referenced in the XML file do not have a `@drawable` prefix. Furthermore, the attributes are not
 * defined through [org.jetbrains.android.dom.AndroidDomElement]s as we use an
 * [com.intellij.xml.XmlSchemaProvider].
 *
 * Drawable resources can be referenced in the [DRAWABLE_RESOURCE_ATTRIBUTES] attributes. These
 * attributes can be used by multiple different tags.
 *
 * @see RawWatchfaceXmlSchemaProvider
 * @see <a href="https://developer.android.com/reference/wear-os/wff/watch-face?version=1">Watch
 *   Face Format reference</a>
 */
class RawWatchFaceDrawableResourceExternalAnnotator : AndroidResourceExternalAnnotatorBase() {
  override fun collectInformation(file: PsiFile, editor: Editor): FileAnnotationInfo? {
    if (!StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.get()) {
      return null
    }
    if (EssentialHighlightingMode.isEnabled()) {
      return null
    }
    val xmlFile = file as? XmlFile ?: return null
    if (!isDeclarativeWatchFaceFile(xmlFile)) {
      return null
    }

    val facet = AndroidFacet.getInstance(file) ?: return null
    val annotationInfo = FileAnnotationInfo(facet, file, editor)

    // Only look at XMLAttributeValues
    file.accept(
      object : XmlRecursiveElementWalkingVisitor() {
        override fun visitXmlAttributeValue(attributeValue: XmlAttributeValue) {
          super.visitXmlAttributeValue(attributeValue)
          if (attributeValue.value.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
            // if the resource starts with @drawable, it will be picked up by
            // AndroidXMLResourceExternalAnnotator
            return
          }
          if (attributeValue.value.startsWith("[")) {
            // This attribute references a data source, for example [COMPLICATION.SMALL_IMAGE],
            // in this case we don't have an image we can show
            return
          }

          val attributeName = XmlAttributeValuePattern.getLocalName(attributeValue)
          if (attributeName !in DRAWABLE_RESOURCE_ATTRIBUTES) {
            return
          }
          // The namespace is null as declarative watch faces can only reference project resources
          val resourceUrl =
            ResourceUrl.create(/* namespace */ null, ResourceType.DRAWABLE, attributeValue.value)
          val reference = resourceUrl.resolve(attributeValue) ?: return
          if (reference.resourceType == ResourceType.DRAWABLE) {
            annotationInfo.elements += AnnotatableElement(reference, attributeValue)
          }
        }
      }
    )

    return annotationInfo.takeIf { it.elements.isNotEmpty() }
  }
}
