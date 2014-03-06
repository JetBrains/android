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
package com.android.tools.idea.editors.navigation.macros;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class MultiMatch {
  public final PsiMethod macro;
  public final Map<String, PsiMethod> subMacros = new LinkedHashMap<String, PsiMethod>(); // make deterministic while prototyping

  public MultiMatch(PsiMethod macro) {
    this.macro = macro;
  }

  public void addSubMacro(String name, PsiMethod macro) {
    subMacros.put(name, macro);
  }

  @Nullable
  public Bindings<PsiElement> match(PsiElement element) {
    Map<String, PsiElement> bindings = Unifier.match(macro, element);
    if (bindings == null) {
      return null;
    }
    Map<String, Map<String, PsiElement>> subBindings = new HashMap<String, Map<String, PsiElement>>();
    for (Map.Entry<String, PsiMethod> entry : subMacros.entrySet()) {
      String name = entry.getKey();
      PsiMethod template = entry.getValue();
      Map<String, PsiElement> subBinding = Unifier.match(template, bindings.get(name));
      if (subBinding == null) {
        return null;
      }
      subBindings.put(name, subBinding);
    }
    return new Bindings<PsiElement>(bindings, subBindings);
  }

  public String instantiate(Bindings<String> bindings) {
    Map<String, String> bb = bindings.bindings;

    for (Map.Entry<String, PsiMethod> entry : subMacros.entrySet()) {
      String name = entry.getKey();
      PsiMethod template = entry.getValue();
      bb.put(name, Instantiation.instantiate2(template, bindings.subBindings.get(name)));
    }

    return Instantiation.instantiate2(macro, bb);
  }

  public static class Bindings<T> {
    public final Map<String, T> bindings;
    public final Map<String, Map<String, T>> subBindings;

    Bindings(Map<String, T> bindings, Map<String, Map<String, T>> subBindings) {
      this.bindings = bindings;
      this.subBindings = subBindings;
    }

    Bindings() {
      this(new HashMap<String, T>(), new HashMap<String, Map<String, T>>());
    }

    public T get(String key) {
      return bindings.get(key);
    }

    public void put(String key, T value) {
      bindings.put(key, value);
    }

    public T get(String key1, String key2) {
      Map<String, T> subBinding = subBindings.get(key1);
      return subBinding == null ? null : subBinding.get(key2);
    }

    public void put(String key1, String key2, T value) {
      Map<String, T> subBinding = subBindings.get(key1);
      if (subBinding == null) {
        subBindings.put(key1, subBinding = new HashMap<String, T>());
      }
      subBinding.put(key2, value);
    }

    @Override
    public String toString() {
      return "Bindings{" +
             "bindings=" + bindings +
             ", subBindings=" + subBindings +
             '}';
    }
  }
}
