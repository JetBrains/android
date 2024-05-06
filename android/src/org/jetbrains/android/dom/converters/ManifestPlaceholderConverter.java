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
package org.jetbrains.android.dom.converters;

import static com.android.SdkConstants.MANIFEST_PLACEHOLDER_PREFIX;

import com.android.tools.idea.model.ManifestPlaceholderResolver;
import com.google.common.collect.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link CustomReferenceConverter} used to resolve
 * <a href="https://developer.android.com/studio/build/manifest-build-variables.html">manifest merger placeholders</a>.
 *
 * <p>This Converter provides the completion options when it detects that there are placeholders in the reference and delegates to a
 * delegate converter otherwise.
 */
public class ManifestPlaceholderConverter extends ResolvingConverter implements CustomReferenceConverter<Object> {
  /**
   * Placeholder detector pattern. This pattern has an optional closing bracket to allow detection of placeholders that are not yet complete
   * (the user is still typing it).
   */
  private static Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{(\\w+)\\}?");

  private Converter myConverterDelegate;

  public ManifestPlaceholderConverter(@NotNull Converter converter) {
    myConverterDelegate = converter;
  }

  static class PlaceholderValue {
    String myValueWithPlaceholders;

    PlaceholderValue(String valueWithPlaceholders) {
      myValueWithPlaceholders = valueWithPlaceholders;
    }
  }

  @NotNull
  @Override
  public Collection<?> getVariants(ConvertContext context) {
    if (myConverterDelegate instanceof ResolvingConverter) {
      return ((ResolvingConverter)myConverterDelegate).getVariants(context);
    }

    return Collections.emptyList();
  }

  @Override
  public String getErrorMessage(@Nullable String s, ConvertContext context) {
    if (context.getModule() != null && s != null && s.contains(MANIFEST_PLACEHOLDER_PREFIX)) {
      ManifestPlaceholderResolver resolver = new ManifestPlaceholderResolver(context.getModule());
      s = resolver.resolve(s);
    }

    return myConverterDelegate.getErrorMessage(s, context);
  }

  @Nullable
  @Override
  public Object fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s != null && s.contains("${")) {
      // This string still contains placeholders
      return new PlaceholderValue(s);
    }

    return myConverterDelegate.fromString(s, context);
  }

  @Nullable
  @Override
  public String toString(@Nullable Object o, ConvertContext context) {
    if (o instanceof PlaceholderValue) {
      return ((PlaceholderValue)o).myValueWithPlaceholders;
    }

    //noinspection unchecked
    return myConverterDelegate != null ? myConverterDelegate.toString(o, context) : null;
  }

  /**
   * {@link PsiReference} returned for every placeholder found in the current element. This class is responsible of
   * providing the variants for completion.
   */
  private static class PlaceholderReference extends PsiReferenceBase<PsiElement> {

    // This placeholder is provided by default and does not come from ManifestPlaceholderResolver
    private static final String GRADLE_APPLICATION_ID = "applicationId";

    private final PsiElement myDummyElement = new FakePsiElement() {
      @Override
      public PsiElement getParent() {
        return getElement();
      }
    };
    private final String[] myValues;
    private final String myName;

    public PlaceholderReference(@NotNull PsiElement element, TextRange range, String[] manifestPlaceholderElements) {
      super(element, range, true);
      myName = range.substring(element.getText());

      // "applicationId" is there by default, whether or not a user supplies manifestPlaceholders in the build files.
      myValues = ArrayUtil.append(manifestPlaceholderElements, GRADLE_APPLICATION_ID);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
      return ArrayUtil.contains(myName, myValues) ? myDummyElement : null;
    }

    @NotNull
    @Override
    public String[] getVariants() {
      return myValues;
    }
  }

  @NotNull
  @Override
  public PsiReference[] createReferences(GenericDomValue<Object> value, PsiElement element, ConvertContext context) {
    if (context.getModule() == null || (!(value.getValue() instanceof PlaceholderValue) && myConverterDelegate instanceof CustomReferenceConverter)) {
      //noinspection unchecked
      return ((CustomReferenceConverter)myConverterDelegate).createReferences(value, element, context);
    }

    String stringValue = element.getText();
    if (stringValue == null) {
      return PsiReference.EMPTY_ARRAY;
    }

    ManifestPlaceholderResolver resolver = new ManifestPlaceholderResolver(context.getModule());
    Collection<String> placeholders = resolver.getPlaceholders().keySet();

    String[] placeholdersArray = ArrayUtil.toStringArray(placeholders);
    ArrayList<PsiReference> result = new ArrayList<>();
    Matcher matcher = PLACEHOLDER_PATTERN.matcher(stringValue);
    while (matcher.find()) {
      TextRange range = new TextRange(matcher.start(1), matcher.end(1));
      result.add(new PlaceholderReference(element, range, placeholdersArray));
    }

    return result.toArray(PsiReference.EMPTY_ARRAY);
  }
}
