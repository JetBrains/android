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
package com.android.tools.idea.gradle.editor.parser;

import com.android.tools.idea.gradle.editor.entity.AbstractSimpleGradleEditorEntity;
import com.android.tools.idea.gradle.editor.entity.GradleEditorSourceBinding;
import com.android.tools.idea.gradle.editor.metadata.GradleEditorEntityMetaData;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class SimpleEntityChecker<T extends AbstractSimpleGradleEditorEntity> extends AbstractPropertyChecker<T> {

  @NotNull private final Set<GradleEditorEntityMetaData> myExpectedMetaData = Sets.newHashSet();
  @NotNull private final String myTargetDescription;

  private SimpleEntityChecker(@NotNull String targetDescription) {
    myTargetDescription = targetDescription;
  }

  @NotNull
  public static <T extends AbstractSimpleGradleEditorEntity> SimpleEntityChecker<T> create(@NotNull String targetDescription) {
    return new SimpleEntityChecker<>(targetDescription);
  }

  @Nullable
  @Override
  protected String deriveActualValue(@NotNull T entity) {
    return entity.getCurrentValue();
  }

  @NotNull
  @Override
  protected List<GradleEditorSourceBinding> deriveDefinitionValueSourceBindings(@NotNull T entity) {
    return entity.getDefinitionValueSourceBindings();
  }

  @NotNull
  public SimpleEntityChecker<T> value(@NotNull String value) {
    setExpectedValue(value);
    return this;
  }

  @NotNull
  public SimpleEntityChecker<T> definitionValueBindings(@NotNull String... bindings) {
    setExpectedDefinitionValueBindings(bindings);
    return this;
  }

  @NotNull
  public SimpleEntityChecker<T> entityText(@NotNull String text) {
    setExpectedWholeEntityText(text);
    return this;
  }

  @NotNull
  public SimpleEntityChecker<T> declarationValue(@NotNull String text) {
    setExpectedDeclarationValue(text);
    return this;
  }

  @NotNull
  public SimpleEntityChecker<T> metaData(@NotNull GradleEditorEntityMetaData... metaData) {
    Collections.addAll(myExpectedMetaData, metaData);
    return this;
  }

  @Override
  public void check(@NotNull T entity) {
    super.check(entity);
    assertEquals("Meta-data for entity " + entity, myExpectedMetaData, entity.getMetaData());
  }

  @Override
  public String toString() {
    return myTargetDescription + " " + super.toString();
  }
}
