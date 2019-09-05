/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding.util;

import static com.android.tools.idea.databinding.util.DataBindingUtil.isBooleanGetter;
import static com.android.tools.idea.databinding.util.DataBindingUtil.isGetter;
import static com.android.tools.idea.databinding.util.DataBindingUtil.isSetter;
import static com.android.tools.idea.databinding.util.DataBindingUtil.stripPrefixFromField;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
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
  public static Set<String> collectIds(@NotNull Collection<? extends PsiModifierListOwner> psiElements) {
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
}
