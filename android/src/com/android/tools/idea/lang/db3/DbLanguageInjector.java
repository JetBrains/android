/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.lang.db3;

import com.android.SdkConstants;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.InjectedLanguagePlaces;
import com.intellij.psi.LanguageInjector;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

public class DbLanguageInjector implements LanguageInjector {
  @Override
  public void getLanguagesToInject(@NotNull PsiLanguageInjectionHost host, @NotNull InjectedLanguagePlaces injectionPlacesRegistrar) {
    if (!(host instanceof XmlAttributeValue)) {
      return;
    }
    String valueText = ((XmlAttributeValue)host).getValue();
    if (!valueText.startsWith(SdkConstants.PREFIX_BINDING_EXPRN) || valueText.length() < 3) {
      return;
    }
    String unescapedValue = host.getText();
    int startIndex = unescapedValue.indexOf('@') + 2;
    int endIndex;
    if (valueText.endsWith("}")) {
      endIndex = unescapedValue.lastIndexOf('}');
    } else {
      if (host.getNode().getLastChildNode().getElementType() == XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER) {
        endIndex = host.getLastChild().getStartOffsetInParent();
      } else {
        endIndex = unescapedValue.length();
      }
    }
    injectionPlacesRegistrar.addPlace(DbLanguage.INSTANCE, TextRange.from(startIndex, endIndex-startIndex), null, null);
  }
}
