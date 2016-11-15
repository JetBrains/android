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

import com.android.tools.idea.gradle.editor.entity.GradleEntityDeclarationValueLocationAware;
import com.android.tools.idea.gradle.editor.entity.GradleEditorEntity;
import com.android.tools.idea.gradle.editor.entity.GradleEditorSourceBinding;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static com.android.tools.idea.gradle.editor.parser.GradleEditorParserTestUtil.text;
import static org.junit.Assert.fail;

public abstract class AbstractPropertyChecker<T extends GradleEditorEntity> {

  @Nullable private List<String> myExpectedDefinitionValueBindings;
  @Nullable private String myExpectedValue;
  @Nullable private String myExpectedWholeEntityText;
  @Nullable private String myExpectedDeclarationValue;

  public void setExpectedDefinitionValueBindings(@NotNull String... bindings) {
    myExpectedDefinitionValueBindings = Arrays.asList(bindings);
  }

  public void setExpectedValue(@Nullable String expectedValue) {
    myExpectedValue = expectedValue;
  }

  public void setExpectedWholeEntityText(@Nullable String expectedWholeEntityText) {
    myExpectedWholeEntityText = expectedWholeEntityText;
  }

  public void setExpectedDeclarationValue(@Nullable String expectedDeclarationValue) {
    myExpectedDeclarationValue = expectedDeclarationValue;
  }

  public void check(@NotNull T entity) {
    String reason = apply(entity);
    if (reason != null) {
      fail(String.format("Entity '%s' doesn't match expectations '%s': %s", entity, this, reason));
    }
  }

  @Nullable
  public String apply(T entity) {
    String actualValue = deriveActualValue(entity);
    List<GradleEditorSourceBinding> actualDefinitionValueSourceBindings = deriveDefinitionValueSourceBindings(entity);
    if (myExpectedValue != null) {
      if (!myExpectedValue.equals(actualValue)) {
        return String.format("expected value mismatch - expected: '%s', actual: '%s'", myExpectedValue, actualValue);
      }
      if (!myExpectedValue.isEmpty()) {
        // We expect that there must be exactly one source binding which points to the target value if it's defined.
        if (actualDefinitionValueSourceBindings.size() == 1 && !myExpectedValue.equals(text(actualDefinitionValueSourceBindings.get(0)))) {
          return String.format("expected that definition value source binding points to the text '%s' but it points to '%s'",
                               myExpectedValue, text(actualDefinitionValueSourceBindings.get(0)));
        }
      }
    }

    if (myExpectedDefinitionValueBindings != null) {
      if (myExpectedDefinitionValueBindings.size() != actualDefinitionValueSourceBindings.size()) {
        return String.format("expected %d definition value bindings (%s) but got %d (%s)",
                             myExpectedDefinitionValueBindings.size(), myExpectedDefinitionValueBindings,
                             actualDefinitionValueSourceBindings.size(), actualDefinitionValueSourceBindings);
      }
      List<String> expected = Lists.newArrayList(myExpectedDefinitionValueBindings);
      for (GradleEditorSourceBinding sourceBinding : actualDefinitionValueSourceBindings) {
        expected.remove(text(sourceBinding));
      }
      if (!expected.isEmpty()) {
        return String.format("expected definition value binding(s) '%s' is not found at the target entity", expected);
      }
      if (myExpectedDefinitionValueBindings.size() > 1 && !Strings.isNullOrEmpty(actualValue)) {
        // We expect an entity to hold an empty value in case of multiple source bindings, i.e. when target variable's value
        // is changed more than once.
        return String.format("expected to find an empty value for the multiple definition value bindings (%s) but it's not - '%s'",
                             myExpectedDefinitionValueBindings, actualValue);
      }
    }

    if (myExpectedDeclarationValue != null) {
      if (!(entity instanceof GradleEntityDeclarationValueLocationAware)) {
        return String.format("expected target entity to be IS-A %s but it's not",
                             GradleEntityDeclarationValueLocationAware.class.getSimpleName());
      }
      if (!myExpectedDeclarationValue.equals(text(((GradleEntityDeclarationValueLocationAware)entity).getDeclarationValueLocation()))) {
        return String.format("expected declaration value '%s' but it's '%s'",
                             myExpectedDeclarationValue,
                             text(((GradleEntityDeclarationValueLocationAware)entity).getDeclarationValueLocation()));
      }
    }
    if (myExpectedWholeEntityText != null && !myExpectedWholeEntityText.equals(text(entity.getEntityLocation()))) {
      return String.format("expected whole entity text '%s' but it is '%s'", myExpectedWholeEntityText, text(entity.getEntityLocation()));
    }
    return null;
  }

  @Nullable
  protected abstract String deriveActualValue(@NotNull T entity);

  @NotNull
  protected abstract List<GradleEditorSourceBinding> deriveDefinitionValueSourceBindings(@NotNull T entity);

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    if (myExpectedValue != null) {
      buffer.append("[value: '").append(myExpectedValue).append("'");
    }
    if (myExpectedDefinitionValueBindings != null) {
      if (buffer.length() > 0) {
        buffer.append(", ");
      }
      else {
        buffer.append('[');
      }
      buffer.append("bindings: (").append(StringUtil.join(myExpectedDefinitionValueBindings, " | ")).append(")");
    }
    if (buffer.length() > 0) {
      buffer.append(']');
    }
    return buffer.length() > 0 ? buffer.toString() : "<any>";
  }
}
