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

import com.intellij.psi.PsiElement;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.converters.DelimitedListConverter;
import com.android.ide.common.rendering.api.AttributeFormat;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FormatConverter extends DelimitedListConverter<AttributeFormat> {
  public FormatConverter() {
    super("|");
  }

  @Override
  @Nullable
  protected AttributeFormat convertString(@Nullable String string, ConvertContext context) {
    return string == null ? null : AttributeFormat.fromXmlName(string);
  }

  @Override
  protected String toString(@Nullable AttributeFormat format) {
    return format == null ? null : format.getName();
  }

  @Override
  protected Object[] getReferenceVariants(ConvertContext context, GenericDomValue<? extends List<AttributeFormat>> value) {
    List<AttributeFormat> variants = new ArrayList<>(AttributeFormat.values().length);
    Collections.addAll(variants, AttributeFormat.values());
    filterVariants(variants, value);
    String[] stringVariants = new String[variants.size()];
    for (int i = 0, variantsSize = variants.size(); i < variantsSize; i++) {
      stringVariants[i] = variants.get(i).getName();
    }
    return stringVariants;
  }

  @Override
  protected PsiElement resolveReference(@Nullable AttributeFormat s, ConvertContext context) {
    return s == null ? null : context.getReferenceXmlElement();
  }

  @Override
  protected String getUnresolvedMessage(String value) {
    return MessageFormat.format(AndroidBundle.message("cannot.resolve.format.error"), value);
  }
}
