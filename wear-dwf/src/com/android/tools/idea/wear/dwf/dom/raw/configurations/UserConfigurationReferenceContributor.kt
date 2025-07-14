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
package com.android.tools.idea.wear.dwf.dom.raw.configurations

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.wear.dwf.WFFConstants.ATTRIBUTE_SOURCE
import com.android.tools.idea.wear.dwf.WFFConstants.COLOR_ATTRIBUTES
import com.android.tools.idea.wear.dwf.WFFConstants.TAG_PHOTOS
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.patterns.XmlAttributeValuePattern
import com.intellij.patterns.XmlPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ProcessingContext
import org.jetbrains.android.dom.isDeclarativeWatchFaceFile

/**
 * Creates [UserConfigurationReference]s from Declarative Watch Face attributes. Both
 * [COLOR_ATTRIBUTES] and the [ATTRIBUTE_SOURCE] attribute source under the [TAG_PHOTOS] tag can
 * reference user configurations.
 *
 * @see <a
 *   href="https://developer.android.com/training/wearables/wff/personalization/user-configurations">WFF
 *   User configurations</a>
 */
class UserConfigurationReferenceContributor : PsiReferenceContributor() {
  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(
      XmlPatterns.xmlAttributeValue(),
      object : PsiReferenceProvider() {
        override fun getReferencesByElement(
          element: PsiElement,
          context: ProcessingContext,
        ): Array<out PsiReference?> {
          if (!StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.get())
            return PsiReference.EMPTY_ARRAY
          val xmlFile = element.containingFile as? XmlFile ?: return PsiReference.EMPTY_ARRAY
          if (!isDeclarativeWatchFaceFile(xmlFile)) return PsiReference.EMPTY_ARRAY

          val attributeValue = element as XmlAttributeValue
          val value = attributeValue.value
          if (value.isEmpty()) return PsiReference.EMPTY_ARRAY
          if (value != CompletionUtil.DUMMY_IDENTIFIER_TRIMMED && !value.startsWith("["))
            return PsiReference.EMPTY_ARRAY

          val attributeName = XmlAttributeValuePattern.getLocalName(attributeValue)
          if (attributeName in COLOR_ATTRIBUTES) {
            return arrayOf(
              UserConfigurationReference(
                attributeValue,
                xmlFile,
                referenceValue = value,
                filter = { it is ColorConfiguration },
              )
            )
          }

          if (
            attributeName == ATTRIBUTE_SOURCE &&
              attributeValue.parentOfType<XmlTag>()?.name == TAG_PHOTOS
          ) {
            return arrayOf(
              UserConfigurationReference(
                attributeValue,
                xmlFile,
                referenceValue = value,
                filter = { it is PhotosConfiguration },
              )
            )
          }

          return PsiReference.EMPTY_ARRAY
        }
      },
    )
  }
}
