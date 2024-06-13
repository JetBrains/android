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

import static com.android.tools.idea.gradle.dsl.model.PluginModelImpl.KOTLIN;
import static com.android.tools.idea.gradle.dsl.model.PluginModelImpl.PLUGIN;
import static com.android.tools.idea.gradle.dsl.model.PluginModelImpl.ID;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.createBasicExpression;

import com.android.tools.idea.gradle.dsl.model.PluginModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslInfixExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This transform implements all the ways that the name of a plugin can be represented within an expression in an `apply()` call,
 * `apply { }` block or `plugins { }` block.  The complexity comes from needing to support all these forms with an approximately
 * uniform interface.
 *
 * Currently supported:
 * - apply plugin: 'foo'
 * - apply(plugin="foo")
 * - apply { plugin 'foo' }
 * - id 'foo'
 * - id('foo')
 * - plugins { id 'foo' }
 * - plugins { id 'foo' apply false }
 * - plugins { id 'foo' version '1.2.3' apply true }
 * - plugins { kotlin("foo") }
 * - plugins { alias(libs.plugins.foo) }, handled by {@link PluginAliasTransform}
 * - plugins { alias(libs.plugins.foo) apply true }, handled by {@link PluginAliasTransform}
 * (and a list form that I haven't yet found a concrete example of, handled by {@link PluginModelImpl#create}
 */
public class PluginNameTransform extends PropertyTransform {
  public PluginNameTransform() {
    super();
  }

  @Override
  public boolean test(@Nullable GradleDslElement e, @NotNull GradleDslElement holder) {
    return e instanceof GradleDslSimpleExpression || e instanceof GradleDslInfixExpression
           || (e instanceof GradleDslExpressionMap && "apply".equals(e.getName()));
  }

  @Override
  public @Nullable GradleDslElement transform(@Nullable GradleDslElement e) {
    return findNameDslElement(e);
  }

  @Override
  public @NotNull GradleDslExpression bind(@NotNull GradleDslElement holder,
                                           @Nullable GradleDslElement oldElement,
                                           @NotNull Object value,
                                           @NotNull String name) {
    return createBasicExpression(holder, value, GradleNameElement.create(ID));
  }

  @Override
  public @NotNull GradleDslElement replace(@NotNull GradleDslElement holder,
                                           @Nullable GradleDslElement oldElement,
                                           @NotNull GradleDslExpression newElement,
                                           @NotNull String name) {
    if (oldElement == null) return newElement;
    GradleDslSimpleExpression nameElement = findNameDslElement(oldElement);
    if (nameElement == null) return oldElement;
    if (newElement instanceof GradleDslSimpleExpression) {
      GradleDslSimpleExpression expression = (GradleDslSimpleExpression)newElement;
      Object value = expression.getValue();
      if (value != null) {
        nameElement.setValue(value);
      }
    }
    return nameElement;
  }

  private static @Nullable GradleDslSimpleExpression findNameDslElement(GradleDslElement element) {
    if (element instanceof GradleDslSimpleExpression) {
      if (element instanceof GradleDslMethodCall) {
        // The implementation that this replaces added a plugin model for each argument.  It is my belief that this was overgeneral, and
        // we should only support a single argument.
        GradleDslMethodCall methodCall = (GradleDslMethodCall)element;
        List<GradleDslExpression> argumentList = methodCall.getArguments();
        if (argumentList.size() == 1) {
          GradleDslExpression argument = argumentList.get(0);
          if (argument instanceof GradleDslSimpleExpression) {
            return (GradleDslSimpleExpression)argument;
          }
        }
        return null;
      }
      return (GradleDslSimpleExpression)element;
    }
    else if (element instanceof GradleDslExpressionMap) {
      GradleDslSimpleExpression result = ((GradleDslExpressionMap)element).getPropertyElement(PLUGIN, GradleDslSimpleExpression.class);
      if(result == null){
        return ((GradleDslExpressionMap)element).getPropertyElement(ID, GradleDslSimpleExpression.class);
      }
      return result;
    }
    else if (element instanceof GradleDslInfixExpression) {
      GradleDslSimpleExpression idElement = ((GradleDslInfixExpression)element).getPropertyElement(ID, GradleDslSimpleExpression.class);
      if (idElement != null) return idElement;
      return ((GradleDslInfixExpression)element).getPropertyElement(KOTLIN, GradleDslSimpleExpression.class);
    }
    else {
      // should never happen, based on test
      return null;
    }
  }
}
