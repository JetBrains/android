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
import com.intellij.psi.*;
import com.intellij.util.containers.HashSet;

import java.util.Collection;
import java.util.Set;

/**
 * This class replicates some logic inside data binding compiler.
 * <p>
 * If we import data binding library as is in the future, we can get rid of this.
 * <p>
 * We do not use {@linkplain com.intellij.psi.util.PropertyUtil} on purpose to avoid any inconsistencies
 * between Data Binding's compiler code and this.
 */
public class BrUtil {
  private static final Logger LOG = Logger.getInstance(BrUtil.class);
  static Set<String> collectIds(Collection<? extends PsiModifierListOwner> psiElements) {
    Set<String> properties = new HashSet<String>();
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

  static String stripPrefixFromMethod(PsiMethod psiMethod) {
    String name = psiMethod.getName();
    CharSequence propertyName;
    if (isGetter(psiMethod) || isSetter(psiMethod)) {
      propertyName = name.subSequence(3, name.length());
    } else if (isBooleanGetter(psiMethod)) {
      propertyName = name.subSequence(2, name.length());
    } else {
      LOG.warn("@Bindable associated with a method must follow JavaBeans convention: " + psiMethod.getName());
      return null;
    }
    char firstChar = propertyName.charAt(0);
    return String.valueOf(Character.toLowerCase(firstChar)) + propertyName.subSequence(1, propertyName.length());
  }

  public static boolean isGetter(PsiMethod psiMethod) {
    String name = psiMethod.getName();
    return prefixes(name, "get") &&
           Character.isJavaIdentifierStart(name.charAt(3)) &&
           psiMethod.getParameterList().getParametersCount() == 0 &&
           !PsiType.VOID.equals(psiMethod.getReturnType()) ;
  }

  public static boolean isSetter(PsiMethod psiMethod) {
    String name = psiMethod.getName();
    return prefixes(name, "set") &&
           Character.isJavaIdentifierStart(name.charAt(3)) &&
           psiMethod.getParameterList().getParametersCount() == 1 &&
           PsiType.VOID.equals(psiMethod.getReturnType());
  }

  public static boolean isBooleanGetter(PsiMethod psiMethod) {
    String name = psiMethod.getName();
    return prefixes(name, "is") &&
           Character.isJavaIdentifierStart(name.charAt(2)) &&
           psiMethod.getParameterList().getParametersCount() == 0 &&
           PsiType.BOOLEAN.equals(psiMethod.getReturnType()) ;
  }

  static String stripPrefixFromField(PsiField psiField) {
    return stripPrefixFromField(psiField.getName());
  }

  static boolean prefixes(CharSequence sequence, String prefix) {
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

  static String stripPrefixFromField(String name) {
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
