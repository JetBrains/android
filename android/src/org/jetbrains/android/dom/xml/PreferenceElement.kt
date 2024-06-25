/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.dom.xml

import com.intellij.psi.PsiClass
import com.intellij.util.xml.Attribute
import com.intellij.util.xml.Convert
import com.intellij.util.xml.CustomChildren
import com.intellij.util.xml.DefinesXml
import com.intellij.util.xml.PsiPackageConverter
import com.intellij.util.xml.Required
import com.intellij.util.xml.converters.ClassValueConverterImpl
import org.jetbrains.android.dom.AndroidAttributeValue
import org.jetbrains.android.dom.LookupClass
import org.jetbrains.android.dom.LookupPrefix
import org.jetbrains.android.dom.Styleable
import org.jetbrains.android.dom.converters.ConstantFieldConverter

@DefinesXml
interface PreferenceElement : PreferenceElementBase {
  @CustomChildren fun getSubPreferences(): List<PreferenceElement>
}

interface PreferenceElementBase : XmlResourceElement {
  val intents: List<Intent>

  @DefinesXml
  @Styleable("Intent")
  interface Intent : XmlResourceElement {
    @LookupPrefix("android.intent.action")
    @LookupClass("android.content.Intent")
    @Convert(ConstantFieldConverter::class)
    fun getAction(): AndroidAttributeValue<String>

    @Convert(ClassValueConverterImpl::class)
    @Attribute("targetClass")
    fun getTargetClass(): AndroidAttributeValue<PsiClass>

    @Convert(PsiPackageConverter::class)
    @Attribute("targetPackage")
    fun getTargetPackage(): AndroidAttributeValue<String>

    val extras: List<Extra>
    val categories: List<Category>
  }

  @DefinesXml
  @Styleable("Extra")
  interface Extra : XmlResourceElement {
    @Required fun getName(): AndroidAttributeValue<String>
  }

  @DefinesXml
  @Styleable("IntentCategory")
  interface Category : XmlResourceElement {
    @Required fun getName(): AndroidAttributeValue<String>
  }
}
