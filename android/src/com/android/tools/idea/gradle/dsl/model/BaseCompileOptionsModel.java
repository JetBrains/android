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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.java.JavaVersionDslElement;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement.SOURCE_COMPATIBILITY_FIELD;
import static com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement.TARGET_COMPATIBILITY_FIELD;

/**
 * Base compile options model that only have sourceCompatibility / targetCompatibility fields.
 */
public abstract class BaseCompileOptionsModel {

  protected final BaseCompileOptionsDslElement myDslElement;
  private final boolean myUseAssignment;

  public BaseCompileOptionsModel(@NotNull BaseCompileOptionsDslElement dslElement, boolean useAssignment) {
    myDslElement = dslElement;
    myUseAssignment = useAssignment;
  }

  @Nullable
  public LanguageLevel sourceCompatibility() {
    JavaVersionDslElement javaVersionElement = myDslElement.getProperty(SOURCE_COMPATIBILITY_FIELD, JavaVersionDslElement.class);
    return javaVersionElement == null ? null : javaVersionElement.getVersion();
  }

  @NotNull
  public BaseCompileOptionsModel setSourceCompatibility(@NotNull LanguageLevel languageLevel) {
    return setLanguageLevel(SOURCE_COMPATIBILITY_FIELD, languageLevel);
  }

  @NotNull
  public BaseCompileOptionsModel removeSourceCompatibility() {
    myDslElement.removeProperty(SOURCE_COMPATIBILITY_FIELD);
    return this;
  }

  @Nullable
  public LanguageLevel targetCompatibility() {
    JavaVersionDslElement javaVersionElement = myDslElement.getProperty(TARGET_COMPATIBILITY_FIELD, JavaVersionDslElement.class);
    return javaVersionElement == null ? null : javaVersionElement.getVersion();
  }

  @NotNull
  public BaseCompileOptionsModel setTargetCompatibility(@NotNull LanguageLevel languageLevel) {
    return setLanguageLevel(TARGET_COMPATIBILITY_FIELD, languageLevel);
  }

  @NotNull
  public BaseCompileOptionsModel removeTargetCompatibility() {
    myDslElement.removeProperty(TARGET_COMPATIBILITY_FIELD);
    return this;
  }

  @NotNull
  private BaseCompileOptionsModel setLanguageLevel(@NotNull String type, @NotNull LanguageLevel languageLevel) {
    JavaVersionDslElement element = myDslElement.getProperty(type, JavaVersionDslElement.class);
    if (element == null) {
      element = new JavaVersionDslElement(myDslElement, type, myUseAssignment);
      myDslElement.setNewElement(type, element);
    }
    element.setVersion(languageLevel);
    return this;
  }
}
