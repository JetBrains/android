/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.jetbrains.android.dom.structure.manifest;

import com.android.SdkConstants;
import com.intellij.ide.presentation.PresentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.manifest.ManifestElementWithName;
import org.jetbrains.annotations.Nullable;

public class GenericNamedPresentationProvider<T extends ManifestElementWithName> extends PresentationProvider<T> {
  @Nullable
  @Override
  public String getName(T t) {
    final XmlTag tag = t.getXmlTag();
    if (tag == null) {
      return null;
    }

    final XmlAttribute attribute = tag.getAttribute("name", SdkConstants.NS_RESOURCES);
    final String value = attribute == null ? null : attribute.getValue();
    if (value == null) {
      return null;
    }

    return String.format("%s (%s)", getTypeName(t), value);
  }

  @Nullable
  @Override
  public String getTypeName(T element) {
    return StringUtil.capitalizeWords(element.getNameStrategy().splitIntoWords(element.getXmlElementName()), true);
  }
}
