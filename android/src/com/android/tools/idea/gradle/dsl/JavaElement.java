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
package com.android.tools.idea.gradle.dsl;

import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * Holds the data in addition to the project element, which added by Java plugin
 */
public class JavaElement extends GradleDslPropertiesElement {
  public static final String NAME = "java";
  public static String SOURCE_COMPATIBILITY_FIELD = "sourceCompatibility";
  public static String TARGET_COMPATIBILITY_FIELD = "targetCompatibility";

  public JavaElement(@Nullable GradleDslElement parent) {
    super(parent, null, NAME);
  }

  @Nullable
  public LanguageLevel sourceCompatibility() {
    JavaVersionElement javaVersionElement = getProperty(SOURCE_COMPATIBILITY_FIELD, JavaVersionElement.class);
    return javaVersionElement == null ? null : javaVersionElement.getVersion();
  }

  public JavaElement setSourceCompatibility(@NotNull LanguageLevel languageLevel) {
    return setLanguageLevel(SOURCE_COMPATIBILITY_FIELD, languageLevel);
  }

  public void removeSourceCompatibility() {
    removeProperty(SOURCE_COMPATIBILITY_FIELD);
  }

  @Nullable
  public LanguageLevel targetCompatibility() {
    JavaVersionElement javaVersionElement = getProperty(TARGET_COMPATIBILITY_FIELD, JavaVersionElement.class);
    return javaVersionElement == null ? null : javaVersionElement.getVersion();
  }

  public JavaElement setTargetCompatibility(@NotNull LanguageLevel languageLevel) {
    return setLanguageLevel(TARGET_COMPATIBILITY_FIELD, languageLevel);
  }

  public void removeTargetCompatibility() {
    removeProperty(TARGET_COMPATIBILITY_FIELD);
  }

  private JavaElement setLanguageLevel(@NotNull String type, @NotNull LanguageLevel languageLevel) {
    JavaVersionElement element = getProperty(type, JavaVersionElement.class);
    if (element == null) {
      element = new JavaVersionElement(this, type);
      setNewElement(type, element);
    }
    element.setVersion(languageLevel);
    return this;
  }

  @Override
  protected GroovyPsiElement create() {
    GroovyPsiElement element = myParent != null ? myParent.create() : null;
    setPsiElement(element);
    return element;
  }
}
