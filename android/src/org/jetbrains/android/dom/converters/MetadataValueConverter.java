/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.jetbrains.android.dom.converters;

import com.android.xml.AndroidManifest;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.CustomReferenceConverter;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * A {@link com.intellij.util.xml.Converter} that references the right element based on the meta-data android:name attribute value.
 *
 * <p>
 * When the android:name is "android.support.PARENT_ACTIVITY" the value is handled as a package class. The default is handling the
 * android:value of meta-data as a ResourceValue.
 */
public class MetadataValueConverter extends ResolvingConverter<Object> implements CustomReferenceConverter<Object> {
  private ResourceReferenceConverter myResourceReferenceConverter = new ResourceReferenceConverter();
  private PackageClassConverter myPsiClassConverter = new PackageClassConverter();

  public MetadataValueConverter() {
    // Expanded suggestions list might be too long and take a while to load.
    myResourceReferenceConverter.setExpandedCompletionSuggestion(false);
  }

  private static boolean isClassContext(ConvertContext context) {
    XmlTag xmlTag = context.getTag();
    return (xmlTag != null && AndroidManifest.VALUE_PARENT_ACTIVITY.equals(xmlTag.getAttributeValue("android:name")));
  }

  @Nullable
  @Override
  public String toString(@Nullable Object object, ConvertContext context) {
    if (object == null) {
      return null;
    }

    return object.toString();
  }

  @NotNull
  @Override
  public Collection<Object> getVariants(ConvertContext context) {
    if (isClassContext(context)) {
      return Collections.emptyList();
    }

    ArrayList<Object> results = new ArrayList<>();
    results.addAll(myResourceReferenceConverter.getVariants(context));

    return results;
  }

  @Nullable
  @Override
  public Object fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (isClassContext(context)) {
      return myPsiClassConverter.fromString(s, context);
    }

    return myResourceReferenceConverter.fromString(s, context);
  }

  @NotNull
  @Override
  public PsiReference[] createReferences(GenericDomValue value, PsiElement element, ConvertContext context) {
    if (isClassContext(context)) {
      return myPsiClassConverter.createReferences(value, element, context);
    }

    return myResourceReferenceConverter.createReferences(value, element, context);
  }
}
