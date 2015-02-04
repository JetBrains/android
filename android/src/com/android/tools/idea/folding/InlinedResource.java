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

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.STRING_PREFIX;

/** A resource referenced in code (Java or XML) */
class InlinedResource implements ModificationTracker {
  static final InlinedResource NONE = new InlinedResource(ResourceType.STRING, "", null, null, null);
  private static final int FOLD_MAX_LENGTH = 60;

  /** Resource type, typically a string or dimension */
  private final ResourceType myType;

  /** The string key, such as "foo" in {@code @string/foo} or {@code R.string.foo} */
  @NotNull private String myKey;

  /**
   * The element absorbed by this string reference. For a parameter it might be just
   * {@code R.string.foo}, but in Java code it can sometimes also include a whole
   * string lookup call, such as {@code getResources().getString(R.string.foo, foo)}
   * */
  @Nullable private PsiElement myElement;

  /** The associated folding descriptor */
  @Nullable private FoldingDescriptor myDescriptor;

  /** The app resources for looking up resource strings lazily */
  @Nullable private LocalResourceRepository myResourceRepository;

  InlinedResource(@NotNull ResourceType type,
                  @NotNull String key,
                  @Nullable LocalResourceRepository resources,
                  @Nullable FoldingDescriptor descriptor,
                  @Nullable PsiElement element) {
    myType = type;
    myKey = key;
    myResourceRepository = resources;
    myDescriptor = descriptor;
    myElement = element;
  }

  @Nullable
  FoldingDescriptor getDescriptor() {
    return myDescriptor;
  }

  @Override
  public long getModificationCount() {
    // Return the project resource generation count; this ensures that when the project
    // resources are updated, the folding text is refreshed
    return myResourceRepository != null ? myResourceRepository.getModificationCount() : 0;
  }

  @Nullable
  public String getResolvedString() {
    if (myResourceRepository != null) {
      if (myResourceRepository.hasResourceItem(myType, myKey)) {
        FolderConfiguration referenceConfig = new FolderConfiguration();
        // Nonexistent language qualifier: trick it to fall back to the default locale
        referenceConfig.setLocaleQualifier(new LocaleQualifier("xx"));
        ResourceValue value = myResourceRepository.getConfiguredValue(myType, myKey, referenceConfig);
        if (value != null) {
          String text = value.getValue();
          if (text != null) {
            if (myElement instanceof PsiMethodCallExpression) {
              text = insertArguments((PsiMethodCallExpression)myElement, text);
            }
            if (myType == ResourceType.PLURALS && text.startsWith(STRING_PREFIX)) {
              value = myResourceRepository.getConfiguredValue(ResourceType.STRING, text.substring(STRING_PREFIX.length()),
                                                              referenceConfig);
              if (value != null && value.getValue() != null) {
                text = value.getValue();
                return '"' + StringUtil.shortenTextWithEllipsis(text, FOLD_MAX_LENGTH - 2, 0) + '"';
              }
            }
            if (myType == ResourceType.STRING || myElement instanceof XmlAttributeValue) {
              return '"' + StringUtil.shortenTextWithEllipsis(text, FOLD_MAX_LENGTH - 2, 0) + '"';
            } else if (text.length() <= 1) {
              // Don't just inline empty or one-character replacements: they can't be expanded by a mouse click
              // so are hard to use without knowing about the folding keyboard shortcut to toggle folding.
              // This is similar to how IntelliJ 14 handles call parameters
              return myKey + ": " + text;
            } else {
              return StringUtil.shortenTextWithEllipsis(text, FOLD_MAX_LENGTH, 0);
            }
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
    final PsiExpression[] args = methodCallExpression.getArgumentList().getExpressions();
    if (args.length == 0 || !args[0].isValid()) {
      return s;
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
