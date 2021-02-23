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
package com.android.tools.idea.gradle.dsl.model.android.splits;

import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.RESET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_LIST;

import com.android.tools.idea.gradle.dsl.api.android.splits.BaseSplitOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import java.util.function.Predicate;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class BaseSplitOptionsModelImpl extends GradleDslBlockModel implements BaseSplitOptionsModel {
  @NonNls public static final String ENABLE = "mEnable";
  @NonNls public static final ModelPropertyDescription EXCLUDE = new ModelPropertyDescription("mExclude", MUTABLE_LIST);
  @NonNls public static final ModelPropertyDescription INCLUDE = new ModelPropertyDescription("mInclude", MUTABLE_LIST);

  private static final Predicate<GradleDslMethodCall> resetPredicate = e -> {
    ModelEffectDescription effect = e.getModelEffect();
    return effect != null && effect.property == INCLUDE && effect.semantics == RESET;
  };

  public BaseSplitOptionsModelImpl(@NotNull GradleDslBlockElement element) {
    super(element);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel enable() {
    return getModelForProperty(ENABLE);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel exclude() {
    return getModelForProperty(EXCLUDE);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel include() {
    return getModelForProperty(INCLUDE);
  }

  @Override
  public boolean reset() {
    List<GradleDslMethodCall> methodCalls = myDslElement.getPropertyElements(GradleDslMethodCall.class);
    return methodCalls.stream().anyMatch(resetPredicate);
  }

  @Override
  public void setReset(boolean addReset) {
    if (addReset) {
      GradleDslMethodCall resetMethod = new GradleDslMethodCall(myDslElement, GradleNameElement.empty(), "reset");
      resetMethod.setModelEffect(new ModelEffectDescription(INCLUDE, RESET));
      myDslElement.addNewElementAt(0, resetMethod);
    }
    else {
      myDslElement.getPropertyElements(GradleDslMethodCall.class).stream()
        .filter(resetPredicate).forEach(myDslElement::removeProperty);
    }
  }
}
