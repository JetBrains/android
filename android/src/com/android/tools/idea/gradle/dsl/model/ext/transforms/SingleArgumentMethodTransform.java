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
package com.android.tools.idea.gradle.dsl.model.ext.transforms;

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.*;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.createOrReplaceBasicExpression;

/**
 * <p>This transform used for single argument method calls which have a preceding property name.</p>
 *
 * <p>For example this transforms will allow a {@link GradlePropertyModel} to work on the &lt;value&gt; within:</p>
 *   <code>storeFile file(&lt;value&gt;)</code><br>
 * or<br>
 *   <code>storePassword System.console().readLine(&lt;value&gt;)</code><br>
 *
 * <p>Note: It does not work when there is no preceding property name such as:</p>
 *   <code>jcenter()</code><br>
 *
 * <p>When no arguments are present the resulting {@link ValueType} of the model will be {@link ValueType#NONE}.</p>
 */
public class SingleArgumentMethodTransform extends PropertyTransform {
  @NotNull
  private String myMethodName;

  public SingleArgumentMethodTransform(@NotNull String methodName) {
    myMethodName = methodName;
  }

  @Override
  public boolean test(@Nullable GradleDslElement e) {
    // We can deal with a null element, we will just create one.
    if (e == null) {
      return true;
    }

    if (!(e instanceof GradleDslMethodCall)) {
      return false;
    }

    GradleDslMethodCall methodCall = (GradleDslMethodCall)e;
    if (!methodCall.getMethodName().equals(myMethodName) ||
        methodCall.getArguments().isEmpty()) {
      return false;
    }

    return true;
  }

  @Nullable
  @Override
  public GradleDslElement transform(@NotNull GradleDslElement e) {
    // This cast is safe, we are guaranteed to have test(e) return true.
    GradleDslMethodCall methodCall = (GradleDslMethodCall)e;
    GradleDslElement arg = methodCall.getArguments().get(0);
    if (arg instanceof GradleDslExpression) {
      return arg;
    }
    return null;
  }

  /**
   *
   * @param holder     the parent of the property being represented by the {@link GradlePropertyModel}
   * @param oldElement the old element being represented by the {@link GradlePropertyModel}, if this is {@code null} then the
   *                   {@link GradleDslElement} returned will have to be created, otherwise it may be possible to reuse some elements
   * @param value      the new value that the property should be set to.
   * @param name       the name of the property, this is ONLY used when creating new properties,
   *                   all other names are kept the same.
   * @return the new element to be bound to the property.
   */
  @NotNull
  @Override
  public GradleDslElement bind(@NotNull GradleDslElement holder,
                               @Nullable GradleDslElement oldElement,
                               @NotNull Object value,
                               @NotNull String name) {
    GradleDslMethodCall methodCall;
    GradleNameElement nameElement = GradleNameElement.create(name);
    if (oldElement != null && oldElement instanceof GradleDslMethodCall) {
      // This cast is safe, we are guaranteed to have test(e) return true.
      methodCall = (GradleDslMethodCall)oldElement;
      if (methodCall.getMethodName().equals(myMethodName)) {
        GradleDslElement baseElement = transform(oldElement);
        if (baseElement != null) {
          GradleDslExpression newBaseElement = createOrReplaceBasicExpression(methodCall, baseElement, value, nameElement);
          if (baseElement != newBaseElement) {
            methodCall.remove(baseElement);
            methodCall.addNewArgument(newBaseElement);
          }
          return oldElement;
        }
      }
    }

    methodCall = new GradleDslMethodCall(holder, nameElement, myMethodName);
    GradleDslExpression argument = createOrReplaceBasicExpression(methodCall, null, value, nameElement);
    methodCall.addNewArgument(argument);
    return methodCall;
  }
}
