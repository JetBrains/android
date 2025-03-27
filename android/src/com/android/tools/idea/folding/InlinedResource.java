/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.folding;

import static com.android.SdkConstants.STRING_PREFIX;

import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceRepositoryUtil;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.resources.ResourceType;
import com.android.tools.configurations.Configuration;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.xml.XmlAttributeValue;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.android.AndroidAnnotatorUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A resource referenced in code (Java or XML)
 */
class InlinedResource {
  private static final int FOLD_MAX_LENGTH = 60;

  /**
   * Reference to the resource being folded.
   */
  @NotNull private final ResourceReference myResourceReference;

  /**
   * Relevant resource repository that contains the resource to be folded, picked based on the resource namespace.
   */
  @NotNull private final ResourceRepository myResourceRepository;

  /**
   * The element absorbed by this string reference. For a parameter it might be just
   * {@code R.string.foo}, but in Java code it can sometimes also include a whole
   * string lookup call, such as {@code getResources().getString(R.string.foo, foo)}
   */
  @NotNull private final PsiElement myElement;

  /**
   * The associated folding descriptor
   */
  @NotNull private final FoldingDescriptor myDescriptor;

  InlinedResource(@NotNull ResourceReference resourceReference,
                  @NotNull ResourceRepository resourceRepository,
                  @NotNull FoldingDescriptor descriptor,
                  @NotNull PsiElement element) {
    myResourceRepository = resourceRepository;
    myResourceReference = resourceReference;
    myDescriptor = descriptor;
    myElement = element;
  }

  @NotNull
  FoldingDescriptor getDescriptor() {
    return myDescriptor;
  }

  @Nullable
  public String getResolvedString() {
    AndroidFacet facet = AndroidFacet.getInstance(myElement);
    if (facet == null) {
      return null;
    }
    if (myResourceRepository.hasResources(myResourceReference.getNamespace(), myResourceReference.getResourceType(),
                                          myResourceReference.getName())) {
      FolderConfiguration referenceConfig = new FolderConfiguration();
      // Nonexistent language qualifier: trick it to fall back to the default locale
      referenceConfig.setLocaleQualifier(new LocaleQualifier("xx"));
      Configuration configuration = AndroidAnnotatorUtil.pickConfiguration(myElement.getContainingFile(), facet);
      if (configuration == null) {
        return null;
      }
      ResourceResolver resourceResolver = configuration.getResourceResolver();
      ResourceValue value = resourceResolver.getResolvedResource(myResourceReference);
      if (value != null) {
        String text = value.getValue();
        if (text != null) {
          if (myElement instanceof PsiMethodCallExpression) {
            text = insertArguments((PsiMethodCallExpression)myElement, text);
          }
          if (myResourceReference.getResourceType() == ResourceType.BOOL) {
            return text;
          }
          if (myResourceReference.getResourceType() == ResourceType.PLURALS && text.startsWith(STRING_PREFIX)) {
            String name = text.substring(STRING_PREFIX.length());
            value = ResourceRepositoryUtil.getConfiguredValue(myResourceRepository, ResourceType.STRING, name, referenceConfig);
            if (value != null) {
              value = resourceResolver.resolveResValue(value);
              if (value != null) {
                text = value.getValue();
                if (text != null) {
                  return '"' + StringUtil.shortenTextWithEllipsis(text, FOLD_MAX_LENGTH - 2, 0) + '"';
                }
              }
            }
          }
          if (myResourceReference.getResourceType() == ResourceType.STRING || myElement instanceof XmlAttributeValue) {
            return '"' + StringUtil.shortenTextWithEllipsis(text, FOLD_MAX_LENGTH - 2, 0) + '"';
          }
          else if (myResourceReference.getResourceType() == ResourceType.INTEGER || text.length() <= 1) {
            // Don't just inline empty or one-character replacements: they can't be expanded by a mouse click
            // so are hard to use without knowing about the folding keyboard shortcut to toggle folding.
            // This is similar to how IntelliJ 14 handles call parameters
            // Integer resources have better context when the resource key is still included, similar to parameter hints.
            return myResourceReference.getName() + ": " + text;
          }
          else {
            return StringUtil.shortenTextWithEllipsis(text, FOLD_MAX_LENGTH, 0);
          }
        }
      }
    }

    return null;
  }

  // See lint's StringFormatDetector
  private static final Pattern FORMAT = Pattern.compile("%(\\d+\\$)?([-+#, 0(<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])");

  @NotNull
  private static String insertArguments(@NotNull PsiMethodCallExpression methodCallExpression, @NotNull String s) {
    if (s.indexOf('%') == -1) {
      return s;
    }
    PsiExpression[] args = methodCallExpression.getArgumentList().getExpressions();
    if (args.length == 0 || !args[0].isValid()) {
      return s;
    }

    if (args.length >= 3 && "getQuantityString".equals(methodCallExpression.getMethodExpression().getReferenceName())) {
      // There are two versions:
      // String getQuantityString (int id, int quantity)
      // String getQuantityString (int id, int quantity, Object... formatArgs)
      // In the second version formatArgs references (1$, 2$, etc) are "one off" (ie args[1] is "quantity" instead of formatArgs[0])
      // Ignore "quantity" argument for plurals since it's not used for formatting
      args = Arrays.copyOfRange(args, 1, args.length);
    }

    Matcher matcher = FORMAT.matcher(s);
    int index = 0;
    int prevIndex = 0;
    int nextNumber = 1;
    int start = 0;
    StringBuilder sb = new StringBuilder(2 * s.length());
    while (true) {
      if (matcher.find(index)) {
        if ("%".equals(matcher.group(6))) {
          index = matcher.end();
          continue;
        }
        int matchStart = matcher.start();
        // Make sure this is not an escaped '%'
        for (; prevIndex < matchStart; prevIndex++) {
          char c = s.charAt(prevIndex);
          if (c == '\\') {
            prevIndex++;
          }
        }
        if (prevIndex > matchStart) {
          // We're in an escape, ignore this result
          index = prevIndex;
          continue;
        }

        index = matcher.end();

        // Shouldn't throw a number format exception since we've already
        // matched the pattern in the regexp
        int number;
        String numberString = matcher.group(1);
        if (numberString != null) {
          // Strip off trailing $
          numberString = numberString.substring(0, numberString.length() - 1);
          number = Integer.parseInt(numberString);
          nextNumber = number + 1;
        } else {
          number = nextNumber++;
        }

        if (number > 0 && number < args.length) {
          PsiExpression argExpression = args[number];
          Object value = JavaConstantExpressionEvaluator.computeConstantExpression(argExpression, false);
          if (value == null) {
            value = args[number].getText();
          }
          for (int i = start; i < matchStart; i++) {
            sb.append(s.charAt(i));
          }
          sb.append('{');
          sb.append(value);
          sb.append('}');
          start = index;
        }
      } else {
        for (int i = start, n = s.length(); i < n; i++) {
          sb.append(s.charAt(i));
        }
        break;
      }
    }

    return sb.toString();
  }
}
