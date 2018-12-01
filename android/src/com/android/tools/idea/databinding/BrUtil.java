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
package com.android.tools.idea.databinding;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiType;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class replicates some logic inside data binding compiler.
 * <p>
 * If we import data binding library as is in the future, we can get rid of this.
 * <p>
 * We do not use {@linkplain com.intellij.psi.util.PropertyUtil} on purpose to avoid any inconsistencies
 * between Data Binding's compiler code and this.
 */
public final class BrUtil {
  @NotNull
  private static Logger getLog() { return Logger.getInstance(BrUtil.class); }

  /**
   * Given a list of PSI elements in a class, return a list of ids that can be used to reference
   * them from within a data binding expression.
   *
   * For example, {@code ViewModel { getData() { ... }}} could be accessed in a data binding layout
   * as {@code @{viewModel.data}}, because "getData" can be referenced as "data".
   */
  @NotNull
  static Set<String> collectIds(@NotNull Collection<? extends PsiModifierListOwner> psiElements) {
    Set<String> properties = new HashSet<>();
    for (PsiModifierListOwner owner : psiElements) {
      String key = null;
      if (owner instanceof PsiField) {
        key = stripPrefixFromField((PsiField)owner);
      } else if (owner instanceof PsiMethod) {
        key = stripPrefixFromMethod((PsiMethod)owner);
      }
      if (key != null) {
        properties.add(key);
      }
    }
    return properties;
  }


  /**
   * Given a method with a getter / setter prefix, return the method name with the prefix stripped,
   * or {@code null} otherwise.
   */
  @Nullable
  private static String stripPrefixFromMethod(@NotNull PsiMethod psiMethod) {
    String name = psiMethod.getName();
    CharSequence propertyName;
    if (isGetter(psiMethod) || isSetter(psiMethod)) {
      propertyName = name.subSequence(3, name.length());
    } else if (isBooleanGetter(psiMethod)) {
      propertyName = name.subSequence(2, name.length());
    } else {
      getLog().warn("@Bindable associated with a method must follow JavaBeans convention: " + psiMethod.getName());
      return null;
    }
    char firstChar = propertyName.charAt(0);
    return String.valueOf(Character.toLowerCase(firstChar)) + propertyName.subSequence(1, propertyName.length());
  }

  public static boolean isGetter(@NotNull PsiMethod psiMethod) {
    return matchesMethodPattern(psiMethod, "get", 0, type -> !PsiType.VOID.equals(type));
  }

  public static boolean isBooleanGetter(@NotNull PsiMethod psiMethod) {
    return matchesMethodPattern(psiMethod, "is", 0, type -> PsiType.BOOLEAN.equals(type));
  }

  public static boolean isSetter(@NotNull PsiMethod psiMethod) {
    return matchesMethodPattern(psiMethod, "set", 1, type -> PsiType.VOID.equals(type));
  }

  private static boolean matchesMethodPattern(@NotNull PsiMethod psiMethod,
                                              @NotNull String prefix,
                                              int parameterCount,
                                              @NotNull Predicate<PsiType> returnTypePredicate) {
    String name = psiMethod.getName();
    return isPrefix(name, prefix) &&
           Character.isJavaIdentifierStart(name.charAt(prefix.length())) &&
           psiMethod.getParameterList().getParametersCount() == parameterCount &&
           returnTypePredicate.test(psiMethod.getReturnType());
  }

  @NotNull
  private static String stripPrefixFromField(@NotNull PsiField psiField) {
    String fieldName = psiField.getName();
    assert fieldName != null;
    return stripPrefixFromField(fieldName);
  }

  private static boolean isPrefix(@NotNull CharSequence sequence, @NotNull String prefix) {
    boolean prefixes = false;
    if (sequence.length() > prefix.length()) {
      int count = prefix.length();
      prefixes = true;
      for (int i = 0; i < count; i++) {
        if (sequence.charAt(i) != prefix.charAt(i)) {
          prefixes = false;
          break;
        }
      }
    }
    return prefixes;
  }

  /**
   * Given an Android field of the format "m_field", "m_Field", "mField" or
   * "_field", return "field". Otherwise, just return the name itself back.
   */
  @NotNull
  private static String stripPrefixFromField(@NotNull String name) {
    if (name.length() >= 2) {
      char firstChar = name.charAt(0);
      char secondChar = name.charAt(1);
      if (name.length() > 2 && firstChar == 'm' && secondChar == '_') {
        char thirdChar = name.charAt(2);
        if (Character.isJavaIdentifierStart(thirdChar)) {
          return String.valueOf(Character.toLowerCase(thirdChar)) + name.subSequence(3, name.length());
        }
      } else if ((firstChar == 'm' && Character.isUpperCase(secondChar)) ||
                 (firstChar == '_' && Character.isJavaIdentifierStart(secondChar))) {
        return String.valueOf(Character.toLowerCase(secondChar)) + name.subSequence(2, name.length());
      }
    }
    return name;
  }
}
