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
package com.android.tools.idea.lang.databinding;

import com.android.annotations.Nullable;
import com.android.tools.idea.lang.databinding.parser.DbParser;
import com.android.tools.idea.lang.databinding.psi.DbTokenType;
import com.android.tools.idea.lang.databinding.psi.DbTokenTypes;
import com.android.tools.idea.lang.databinding.psi.PsiDbConstantValue;
import com.android.tools.idea.lang.databinding.psi.PsiDbDefaults;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper methods for data binding.
 */
public class DbUtil {

  @Nullable
  public static String getBindingExprDefault(@NotNull XmlAttribute psiAttribute) {
    XmlAttributeValue attrValue = psiAttribute.getValueElement();
    if (attrValue instanceof PsiLanguageInjectionHost) {
      final Ref<PsiElement> injections = Ref.create();
      InjectedLanguageUtil.enumerate(attrValue, new PsiLanguageInjectionHost.InjectedPsiVisitor() {
        @Override
        public void visit(@NotNull PsiFile injectedPsi, @NotNull List<PsiLanguageInjectionHost.Shred> places) {
          if (injectedPsi instanceof DbFile) {
            injections.set(injectedPsi);
          }
        }
      });
      if (injections.get() != null) {
        PsiDbDefaults defaults = PsiTreeUtil.getChildOfType(injections.get(), PsiDbDefaults.class);
        if (defaults != null) {
          // TODO: extract value from literals and resolve variable values if needed.
          PsiDbConstantValue constantValue = defaults.getConstantValue();
          if (constantValue.getNode().getElementType() == DbTokenTypes.STRING_LITERAL) {
            String text = constantValue.getText();
            return text.substring(1, text.length() -1);  // return unquoted string literal.
          }
          return constantValue.getText();
        }
      }
    }
    return null;
  }

  /**
   * @param exprn Data binding expression enclosed in @{}
   * @return
   */
  @Nullable
  public static String getBindingExprDefault(@NotNull String exprn) {
    if (!exprn.contains(DbTokenTypes.DEFAULT_KEYWORD.toString())) {
      // A fast check since many expressions would likely not have a default.
      return null;
    }
    Pattern defaultCheck = Pattern.compile(",\\s*default\\s*=\\s*");
    int index = 0;
    Matcher matcher = defaultCheck.matcher(exprn);
    while (matcher.find()) {
      index = matcher.end();
    }
    String def = exprn.substring(index, exprn.length() - 1).trim();  // remove the trailing "}"
    if (def.startsWith("\"") && def.endsWith("\"")) {
      def = def.substring(1, def.length() - 1);       // Unquote the string.
    }
    return def;
  }

  /** Disallow instances. */
  private DbUtil() {
  }
}
