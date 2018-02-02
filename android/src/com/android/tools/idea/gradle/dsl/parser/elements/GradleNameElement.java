/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GradleNameElement {
  @Nullable
  private PsiElement myNameElement;
  @Nullable
  private String mySavedName;
  @Nullable
  private String myUnsavedName;

  @Nullable String myFakeName; // Used for names that do not require a file element.

  /**
   * Requires read access.
   */
  @NotNull
  public static GradleNameElement from(@NotNull PsiElement element) {
    return new GradleNameElement(element);
  }

  @NotNull
  public static GradleNameElement empty() {
    return new GradleNameElement(null);
  }

  @NotNull
  public static GradleNameElement create(@NotNull String name) {
    return new GradleNameElement(name, false);
  }

  @NotNull
  public static GradleNameElement fake(@NotNull String name) {
    return new GradleNameElement(name, true);
  }

  /**
   * Requires read access.
   */
  private GradleNameElement(@Nullable PsiElement element) {
    setUpFrom(element);
  }

  private GradleNameElement(@NotNull String name, boolean isFake) {
    if (isFake) {
      myFakeName = name;
    }
    else {
      myUnsavedName = name;
    }
  }

  /**
   * Changes this element to be backed by the given PsiElement. This method should not be called outside of
   * GradleWriter subclasses.
   */
  public void commitNameChange(@Nullable PsiElement nameElement) {
    setUpFrom(nameElement);
  }

  @NotNull
  public String fullName() {
    String name = findName();
    if (name == null) {
      return "";
    }
    return name;
  }

  @NotNull
  public List<String> qualifyingParts() {
    String name = findName();
    if (name == null) {
      return ImmutableList.of();
    }

    List<String> nameSegments = Splitter.on('.').splitToList(name);
    // Remove the last element, which is not a qualifying part;
    return nameSegments.subList(0, nameSegments.size() - 1);
  }

  public boolean isQualified() {
    String name = findName();
    if (name == null) {
      return false;
    }

    return name.contains(".");
  }

  @NotNull
  public String name() {
    String name = findName();
    if (name == null) {
      return "";
    }
    int lastDotIndex = name.lastIndexOf('.') + 1;
    return convertNameToKey(name.substring(lastDotIndex));
  }

  @Nullable
  public PsiElement getNamedPsiElement() {
    return myNameElement;
  }

  @Nullable
  public String getUnsavedName() {
    return myUnsavedName;
  }


  public void rename(@NotNull String newName) {
    if (!isFake()) {
      myUnsavedName = newName;
    }
    else {
      myFakeName = newName;
    }
  }

  public boolean isEmpty() {
    String name = findName();
    return name == null || name.isEmpty();
  }

  public boolean isFake() {
    return myNameElement == null && myFakeName != null;
  }

  @Override
  @NotNull
  public String toString() {
    return fullName();
  }

  @Nullable
  private String findName() {
    String name = null;
    if (mySavedName != null) {
      name = mySavedName;
    }
    else if (myUnsavedName != null) {
      name = myUnsavedName;
    }

    if (name == null && myFakeName != null) {
      name = myFakeName;
    }

    if (name != null) {
      // Remove whitespace
      name = name.replaceAll("\\s+", "");
    }

    return name;
  }

  @NotNull
  public static String convertNameToKey(@NotNull String str) {
    return StringUtil.unquoteString(str);
  }


  private void setUpFrom(@Nullable PsiElement element) {
    myNameElement = element;
    if (myNameElement instanceof PsiNamedElement) {
      mySavedName = ((PsiNamedElement)myNameElement).getName();
    }
    else if (myNameElement != null) {
      mySavedName = myNameElement.getText();
    }
  }
}
