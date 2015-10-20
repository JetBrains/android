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
package com.android.tools.idea.gradle.dsl.model.java;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.gradle.dsl.parser.java.JavaPsiElement;
import com.android.tools.idea.gradle.dsl.parser.java.JavaVersionPsiElement;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the data in addition to the project element, which added by Java plugin
 */
public class JavaModel {
  public static String SOURCE_COMPATIBILITY_FIELD = "sourceCompatibility";
  public static String TARGET_COMPATIBILITY_FIELD = "targetCompatibility";

  private final JavaPsiElement myPsiElement;

  public JavaModel(@NotNull JavaPsiElement psiElement) {
    myPsiElement = psiElement;
  }

  @Nullable
  public LanguageLevel sourceCompatibility() {
    JavaVersionPsiElement javaVersionElement = myPsiElement.getProperty(SOURCE_COMPATIBILITY_FIELD, JavaVersionPsiElement.class);
    return javaVersionElement == null ? null : javaVersionElement.getVersion();
  }

  public JavaModel setSourceCompatibility(@NotNull LanguageLevel languageLevel) {
    return setLanguageLevel(SOURCE_COMPATIBILITY_FIELD, languageLevel);
  }

  public JavaModel removeSourceCompatibility() {
    myPsiElement.removeProperty(SOURCE_COMPATIBILITY_FIELD);
    return this;
  }

  @Nullable
  public LanguageLevel targetCompatibility() {
    JavaVersionPsiElement javaVersionElement = myPsiElement.getProperty(TARGET_COMPATIBILITY_FIELD, JavaVersionPsiElement.class);
    return javaVersionElement == null ? null : javaVersionElement.getVersion();
  }

  public JavaModel setTargetCompatibility(@NotNull LanguageLevel languageLevel) {
    return setLanguageLevel(TARGET_COMPATIBILITY_FIELD, languageLevel);
  }

  public JavaModel removeTargetCompatibility() {
    myPsiElement.removeProperty(TARGET_COMPATIBILITY_FIELD);
    return this;
  }

  private JavaModel setLanguageLevel(@NotNull String type, @NotNull LanguageLevel languageLevel) {
    JavaVersionPsiElement element = myPsiElement.getProperty(type, JavaVersionPsiElement.class);
    if (element == null) {
      element = new JavaVersionPsiElement(myPsiElement, type);
      myPsiElement.setNewElement(type, element);
    }
    element.setVersion(languageLevel);
    return this;
  }

  @VisibleForTesting
  @NotNull
  JavaPsiElement getGradlePsiElement() {
    return myPsiElement;
  }
}
