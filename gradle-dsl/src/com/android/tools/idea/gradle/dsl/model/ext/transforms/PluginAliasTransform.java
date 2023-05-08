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
package com.android.tools.idea.gradle.dsl.model.ext.transforms;

import static com.android.tools.idea.gradle.dsl.model.PluginModelImpl.ALIAS;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.createBasicExpression;

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencySpecImpl;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.dependencies.FakeArtifactElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslInfixExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSettableExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PluginAliasTransform extends PropertyTransform {
  @NotNull String myName;
  @NotNull Function<ArtifactDependencySpec, String> myGetter;
  @NotNull BiConsumer<ArtifactDependencySpecImpl, String> mySetter;

  public PluginAliasTransform(
    @NotNull String name,
    @NotNull Function<ArtifactDependencySpec, String> getter,
    @NotNull BiConsumer<ArtifactDependencySpecImpl, String> setter
  ) {
    super();
    myName = name;
    myGetter = getter;
    mySetter = setter;
  }

  @Override
  public boolean test(@Nullable GradleDslElement e, @NotNull GradleDslElement holder) {
    return (e instanceof GradleDslInfixExpression && ((GradleDslInfixExpression)e).getPropertyElement(ALIAS) != null ||
            e instanceof GradleDslLiteral && e.getName().equals(ALIAS) ||
            e instanceof GradleDslMethodCall && e.getName().equals(ALIAS));
  }

  @Override
  public @Nullable GradleDslElement transform(@Nullable GradleDslElement e) {
    if (e instanceof GradleDslInfixExpression) e = ((GradleDslInfixExpression) e).getPropertyElement(ALIAS);
    if (e == null) return null;
    GradleDslExpressionMap map;
    List<GradleReferenceInjection> dependencies = null;
    if (e instanceof GradleDslLiteral) {
      dependencies = e.getDependencies();
    }
    else if (e instanceof GradleDslMethodCall) {
      List<GradleDslExpression> arguments = ((GradleDslMethodCall)e).getArguments();
      if (arguments.size() == 1) {
        dependencies = arguments.get(0).getDependencies();
      }
    }
    if (dependencies == null || dependencies.size() != 1) return null;
    GradleDslElement reference = dependencies.get(0).getToBeInjected();
    if (reference instanceof GradleDslExpressionMap) {
      map = (GradleDslExpressionMap)reference;
      return map.getPropertyElement(myName);
    }
    else if (reference instanceof GradleDslLiteral) {
      return new FakeArtifactElement(e, GradleNameElement.fake(myName), ((GradleDslLiteral)reference), myGetter, mySetter, false);
    }
    else {
      return null;
    }
  }

  @Override
  public @NotNull GradleDslExpression bind(@NotNull GradleDslElement holder,
                                           @Nullable GradleDslElement oldElement,
                                           @NotNull Object value,
                                           @NotNull String name) {
    GradleDslElement transformed = transform(oldElement);
    if (transformed instanceof FakeArtifactElement) {
      ((FakeArtifactElement)transformed).setValue(value);
      return (FakeArtifactElement)transformed;
    }
    if (transformed instanceof GradleDslSettableExpression) {
      ((GradleDslSettableExpression)transformed).setValue(value);
      return (GradleDslSettableExpression)transformed;
    }
    if (transformed != null) {
      GradleDslElement parent = transformed.getParent();
      if (parent != null) {
        return createBasicExpression(parent, value, GradleNameElement.create(myName));
      }
    }
    return createBasicExpression(holder, value, GradleNameElement.create(myName));
  }

  @Override
  public @NotNull GradleDslElement replace(@NotNull GradleDslElement holder,
                                           @Nullable GradleDslElement oldElement,
                                           @NotNull GradleDslExpression newElement,
                                           @NotNull String name) {
    GradleDslElement transformed = transform(oldElement);
    if (newElement == transformed) {
      // We made no change other than possibly setting the value of the transformed element: no further action needed.
      return oldElement;
    }
    GradleDslElement aliasElement = oldElement;
    if (aliasElement instanceof GradleDslInfixExpression) {
      aliasElement = ((GradleDslInfixExpression)oldElement).getPropertyElement(ALIAS);
    }
    if (aliasElement instanceof GradleDslMethodCall) {
      GradleDslMethodCall methodCall = (GradleDslMethodCall) aliasElement;
      if (methodCall.getArguments().size() == 1) {
        aliasElement = methodCall.getArguments().get(0);
      }
      else {
        aliasElement = null;
      }
    }
    if (aliasElement != null) {
      GradleDslExpressionMap map;
      GradleDslElement reference = null;
      if (aliasElement.getDependencies().size() == 1) {
         reference = aliasElement.getDependencies().get(0).getToBeInjected();
      }
      if (reference instanceof GradleDslExpressionMap) {
        map = (GradleDslExpressionMap)reference;
        GradleDslElement existing = map.getPropertyElement(myName);
        if (existing != null) {
          map.removeProperty(existing);
        }
        map.setNewElement(newElement);
      }
    }
    return oldElement;
  }
}
