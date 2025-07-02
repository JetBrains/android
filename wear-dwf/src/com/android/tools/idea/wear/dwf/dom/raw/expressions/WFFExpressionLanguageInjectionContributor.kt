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
package com.android.tools.idea.wear.dwf.dom.raw.expressions

import com.android.tools.idea.flags.StudioFlags
import com.intellij.lang.injection.general.Injection
import com.intellij.lang.injection.general.LanguageInjectionContributor
import com.intellij.lang.injection.general.SimpleInjection
import com.intellij.lang.xml.XMLLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import org.jetbrains.android.dom.isDeclarativeWatchFaceFile

private val EXPRESSION_ATTRIBUTES_BY_TAG =
  mapOf(
    "Transform" to setOf("value"),
    "Gyro" to setOf("x", "y", "scaleX", "scaleY", "angle", "alpha"),
    "Variant" to setOf("value"),
    "Parameter" to setOf("expression"),
  )

private const val EXPRESSION_TAG = "Expression"

/**
 * [LanguageInjectionContributor] that injects the [WFFExpressionLanguage] into attributes and
 * [XmlText] in Declarative Watch Face files.
 *
 * The expected attributes that support [WFFExpressionLanguage] and their associated parent XML Tag
 * name are listed in [EXPRESSION_ATTRIBUTES_BY_TAG].
 *
 * Otherwise, we expect the language to be present in the [XmlText] of XML Tags with the
 * [EXPRESSION_TAG] tag name.
 */
class WFFExpressionLanguageInjectionContributor : LanguageInjectionContributor {

  override fun getInjection(context: PsiElement): Injection? {
    if (!StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.get()) return null
    if (context.language != XMLLanguage.INSTANCE) return null
    val xmlFile = context.containingFile as? XmlFile ?: return null
    if (!isDeclarativeWatchFaceFile(xmlFile)) return null

    return when (context) {
      is XmlAttributeValue -> getAttributeInjection(context)
      is XmlText -> getTextInjection(context)
      else -> null
    }
  }

  private fun getAttributeInjection(attributeValue: XmlAttributeValue): Injection? {
    val attribute = attributeValue.parent as? XmlAttribute ?: return null
    val expressionAttributes = EXPRESSION_ATTRIBUTES_BY_TAG[attribute.parent.name] ?: return null
    if (attribute.name !in expressionAttributes) return null
    return SimpleInjection(WFFExpressionLanguage, "", "", null)
  }

  private fun getTextInjection(xmlText: XmlText): Injection? {
    if (xmlText.parentOfType<XmlTag>()?.name != EXPRESSION_TAG) return null
    return SimpleInjection(WFFExpressionLanguage, "", "", null)
  }
}
