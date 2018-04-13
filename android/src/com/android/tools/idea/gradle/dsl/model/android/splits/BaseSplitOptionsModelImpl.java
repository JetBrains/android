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

import com.android.tools.idea.gradle.dsl.api.android.splits.BaseSplitOptionsModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class BaseSplitOptionsModelImpl extends GradleDslBlockModel implements BaseSplitOptionsModel {
  @NonNls private static final String ENABLE = "enable";
  @NonNls private static final String EXCLUDE = "exclude";
  @NonNls private static final String INCLUDE = "include";
  @NonNls private static final String RESET = "reset";

  public BaseSplitOptionsModelImpl(@NotNull GradleDslBlockElement element) {
    super(element);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel enable() {
    return GradlePropertyModelBuilder.create(myDslElement, ENABLE).asMethod(true).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel exclude() {
    return GradlePropertyModelBuilder.create(myDslElement, EXCLUDE).asMethod(true).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel include() {
    return GradlePropertyModelBuilder.create(myDslElement, INCLUDE).asMethod(true).buildResolved();
  }

  @Override
  public boolean reset() {
    List<GradleDslMethodCall> methodCalls = myDslElement.getPropertyElements(GradleDslMethodCall.class);
    return methodCalls.stream().anyMatch(e -> e.getMethodName().equals(RESET));
  }

  @Override
  public void setReset(boolean addReset) {
    if (addReset) {
      GradleDslMethodCall resetMethod = new GradleDslMethodCall(myDslElement, GradleNameElement.empty(), RESET);
      myDslElement.addNewElementAt(0, resetMethod);
    }
    else {
      myDslElement.getPropertyElements(GradleDslMethodCall.class).stream().filter(e -> e.getMethodName().equals(RESET))
                  .forEach(myDslElement::removeProperty);
    }
  }

}
