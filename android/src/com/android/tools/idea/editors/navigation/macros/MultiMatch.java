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
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class MultiMatch {
  public final CodeTemplate macro;
  public final Map<String, CodeTemplate> subMacros = new LinkedHashMap<String, CodeTemplate>(); // make deterministic while prototyping

  public MultiMatch(CodeTemplate macro) {
    this.macro = macro;
  }

  public void addSubMacro(String name, CodeTemplate macro) {
    subMacros.put(name, macro);
  }

  @Nullable
  public Bindings<PsiElement> match(PsiElement element) {
    Map<String, PsiElement> bindings = Unifier.match(macro, element);
    if (bindings == null) {
      return null;
    }
    Map<String, Map<String, PsiElement>> subBindings = new HashMap<String, Map<String, PsiElement>>();
    for (Map.Entry<String, CodeTemplate> entry : subMacros.entrySet()) {
      String name = entry.getKey();
      CodeTemplate template = entry.getValue();
      Map<String, PsiElement> subBinding = Unifier.match(template, bindings.get(name));
      if (subBinding == null) {
        return null;
      }
      subBindings.put(name, subBinding);
    }
    return new Bindings<PsiElement>(bindings, subBindings);
  }

  public static class Bindings<T> {
    public final Map<String, T> bindings;
    public final Map<String, Map<String, T>> subBindings;

    Bindings(Map<String, T> bindings, Map<String, Map<String, T>> subBindings) {
      this.bindings = bindings;
      this.subBindings = subBindings;
    }

    public T get(String key) {
      return bindings.get(key);
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
