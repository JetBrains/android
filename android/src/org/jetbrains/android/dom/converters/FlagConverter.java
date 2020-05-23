// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.dom.converters;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xml.converters.DelimitedListConverter;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;

public class FlagConverter extends DelimitedListConverter<String> {
  private final Set<String> myValues = new HashSet<>();
  private final ResolvingConverter<String> additionalConverter;

  public FlagConverter(@Nullable ResolvingConverter<String> additionalConverter, @NotNull String... values) {
    super("|");
    this.additionalConverter = additionalConverter;
    Collections.addAll(myValues, values);
  }

  @NotNull
  @Override
  public Collection<? extends List<String>> getVariants(ConvertContext context) {
    if (additionalConverter == null) {
      return super.getVariants(context);
    }
    Collection<? extends String> variants = additionalConverter.getVariants(context);
    List<List<String>> result = new ArrayList<>();
    for (String variant : variants) {
      result.add(Collections.singletonList(variant));
    }
    return result;
  }

  @Override
  protected String convertString(final @Nullable String s, final ConvertContext context) {
    if (s == null || myValues.contains(s)) return s;
    return additionalConverter != null ? additionalConverter.fromString(s, context) : null;
  }

  @Override
  protected String toString(final @Nullable String s) {
    return s;
  }

  @Override
  protected Object[] getReferenceVariants(final ConvertContext context, final GenericDomValue<? extends List<String>> value) {
    List<String> variants = new ArrayList<>(myValues);
    filterVariants(variants, value);
    return ArrayUtil.toStringArray(variants);
  }

  @Override
  protected PsiElement resolveReference(@Nullable final String s, final ConvertContext context) {
    return s == null ? null : context.getReferenceXmlElement();
  }

  @Override
  protected String getUnresolvedMessage(final String value) {
    return MessageFormat.format(AndroidBundle.message("cannot.resolve.flag.error"), value);
  }

  @NotNull
  public Set<String> getValues() {
    return myValues;
  }
}
