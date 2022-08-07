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
package com.android.tools.idea.templates.live

import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.codeInsight.template.XmlContextType
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlAttribute

/**
 * Template context type used for LiveTemplate abbreviations that should only be expanded as Xml attributes.
 */
class XmlAttributeContextType : TemplateContextType("XML Attribute") {
  override fun isInContext(file: PsiFile, offset: Int): Boolean {
    return XmlContextType.isInXml(file, offset) && file.findElementAt(offset)?.parent is XmlAttribute
  }
}