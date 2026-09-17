/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.api.dependencies.PlatformDependencyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class PlatformArtifactDependencyModelImpl extends ArtifactDependencyModelImpl implements PlatformDependencyModel {
  public PlatformArtifactDependencyModelImpl(@Nullable GradleDslClosure configurationElement,
                                             @NotNull String configurationName,
                                             @NotNull Maintainer maintainer) {
    super(configurationElement, configurationName, maintainer);
  }

  private static @NotNull GradleDslMethodCall createMethodCall(@NotNull GradlePropertiesDslElement parent,
                                                               @NotNull String configurationName,
                                                               boolean enforced) {
    GradleNameElement name = GradleNameElement.create(configurationName);
    String methodName = enforced ? "enforcedPlatform" : "platform";
    return new GradleDslMethodCall(parent, name, methodName);
  }

  private static void initializeArgument(@NotNull GradleDslMethodCall methodCall, @NotNull Object value) {
    GradleDslLiteral argument = new GradleDslLiteral(methodCall, GradleNameElement.empty());
    methodCall.addNewArgument(argument);
    argument.setValue(value);
  }

  private static void initializeArgument(@NotNull GradleDslLiteral argument, @NotNull Object value) {
    argument.setValue(value);
  }

  static void createNew(@NotNull GradlePropertiesDslElement parent,
                        @NotNull String configurationName,
                        @NotNull ArtifactDependencySpec dependency,
                        boolean enforced) {
    GradleDslMethodCall methodCall = createMethodCall(parent, configurationName, enforced);
    initializeArgument(methodCall, createCompactNotationForLiterals(methodCall, dependency));
    parent.setNewElement(methodCall);
  }

  static void createNew(@NotNull GradlePropertiesDslElement parent,
                        @NotNull String configurationName,
                        @NotNull ReferenceTo reference,
                        boolean enforced) {
    GradleDslMethodCall methodCall = createMethodCall(parent, configurationName, enforced);
    initializeArgument(methodCall, reference);
    parent.setNewElement(methodCall);
  }

  static class DynamicNotation extends ArtifactDependencyModelImpl.DynamicNotation implements PlatformDependencyModel{
    String methodName;

    DynamicNotation(@NotNull String configurationName,
                @NotNull GradleDslExpression dslExpression,
                @Nullable GradleDslClosure configurationElement,
                @NotNull Maintainer maintainer,
                @NotNull String methodName) {
      super(configurationName, dslExpression, configurationElement, maintainer);
      this.methodName = methodName;
    }
    public boolean enforced() {
      return methodName.equals("enforcedPlatform");
    }
  }
}
