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

package org.jetbrains.android.dom.converters;

import com.google.common.base.Splitter;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class LightFlagConverter extends ResolvingConverter<String> {
  private final Set<String> myValues = new HashSet<String>();

  public LightFlagConverter(String... values) {
    Collections.addAll(myValues, values);
  }

  @Override
  @NotNull
  public Collection<? extends String> getVariants(ConvertContext context) {
    Set<String> result = new HashSet<String>();
    XmlElement element = context.getXmlElement();
    if (element == null) return result;
    String attrValue = ((XmlAttribute)element).getValue();
    List<String> flags = attrValue == null ? Collections.<String>emptyList() : Splitter.on('|').splitToList(attrValue);
    StringBuilder prefix = new StringBuilder();
    for (int i = 0; i < flags.size() - 1; i++) {
      String flag = flags.get(i);
      if (!myValues.contains(flag)) break;
      prefix.append(flag).append('|');
    }
    for (String value : myValues) {
      result.add(prefix.toString() + value);
    }
    return result;
  }

  @Override
  public String fromString(@Nullable String s, ConvertContext convertContext) {
    return s;
  }

  @Override
  public String toString(@Nullable String s, ConvertContext convertContext) {
    return s;
  }
}
