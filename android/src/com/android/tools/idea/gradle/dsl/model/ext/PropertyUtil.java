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
package com.android.tools.idea.gradle.dsl.model.ext;

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.DefaultTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.FileTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.PropertyTransform;
import com.android.tools.idea.gradle.dsl.parser.GradleReferenceInjection;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.REFERENCE;

public class PropertyUtil {
  @NonNls public static final String FILE_METHOD_NAME = "file";
  @NonNls public static final String FILE_CONSTRUCTOR_NAME = "File";

  @NotNull
  public static GradleDslSimpleExpression createOrReplaceBasicExpression(@NotNull GradleDslElement parent,
                                                                         @Nullable GradleDslElement oldElement,
                                                                         @NotNull Object value,
                                                                         @NotNull GradleNameElement name) {
    boolean isReference = value instanceof ReferenceTo;

    // Check if we can reuse the element.
    if (!isReference && oldElement instanceof GradleDslLiteral ||
        isReference && oldElement instanceof GradleDslReference) {
      GradleDslSimpleExpression expression = (GradleDslSimpleExpression)oldElement;
      expression.setValue(value);
      return expression;
    }
    else {
      if (oldElement != null) {
        name = oldElement.getNameElement();
      }

      return createBasicExpression(parent, value, name);
    }
  }

  @NotNull
  public static GradleDslSimpleExpression createBasicExpression(@NotNull GradleDslElement parent, @NotNull Object value, @NotNull GradleNameElement name) {
    GradleDslSimpleExpression newElement;
    if (value instanceof ReferenceTo) {
      newElement = new GradleDslReference(parent, name);
    }
    else {
      newElement = new GradleDslLiteral(parent, name);
    }

    newElement.setValue(value);
    return newElement;
  }

  public static void replaceElement(@NotNull GradleDslElement holder,
                                    @Nullable GradleDslElement oldElement,
                                    @NotNull GradleDslElement newElement) {
    if (holder instanceof GradlePropertiesDslElement) {
      if (oldElement != null) {
        ((GradlePropertiesDslElement)holder).replaceElement(oldElement, newElement);
      }
      else {
        ((GradlePropertiesDslElement)holder).setNewElement(newElement);
      }
    }
    else if (holder instanceof GradleDslExpressionList) {
      assert newElement instanceof GradleDslExpression;
      GradleDslExpressionList list = (GradleDslExpressionList)holder;
      if (oldElement != null) {
        assert oldElement instanceof GradleDslExpression;
        list.replaceExpression((GradleDslExpression)oldElement, (GradleDslExpression)newElement);
      }
      else {
        list.addNewExpression((GradleDslSimpleExpression)newElement, list.getExpressions().size());
      }
    }
    else if (holder instanceof GradleDslMethodCall) {
      assert newElement instanceof GradleDslExpression;
      GradleDslMethodCall methodCall = (GradleDslMethodCall)holder;
      if (oldElement != null) {
        assert oldElement instanceof GradleDslExpression;
        methodCall.replaceArgument((GradleDslExpression)oldElement, (GradleDslExpression)newElement);
      }
      else {
        methodCall.addNewArgument((GradleDslExpression)newElement);
      }
    }
    else {
      throw new IllegalStateException("Property holder has unknown type, " + holder);
    }
  }

  public static void removeElement(@NotNull GradleDslElement element) {
    GradleDslElement holder = element.getParent();
    if (holder == null) {
      // Element is already attached.
      return;
    }

    if (holder instanceof GradlePropertiesDslElement) {
      ((GradlePropertiesDslElement)holder).removeProperty(element);
    }
    else if (holder instanceof GradleDslExpressionList) {
      GradleDslExpressionList list = (GradleDslExpressionList)holder;
      list.removeElement(element);
    }
    else if (holder instanceof GradleDslMethodCall) {
      GradleDslMethodCall methodCall = (GradleDslMethodCall)holder;
      methodCall.remove(element);
    } else {
      throw new IllegalStateException("Property holder has unknown type, " + holder);
    }
  }

  @NotNull
  public static final PropertyTransform DEFAULT_TRANSFORM = new DefaultTransform();
  @NotNull
  public static final PropertyTransform FILE_TRANSFORM = new FileTransform();

  @NotNull
  public static GradlePropertyModelImpl resolveModel(@NotNull GradlePropertyModelImpl model) {
    Set<GradlePropertyModel> seenModels = new HashSet<>();

    while (model.getValueType() == REFERENCE && !seenModels.contains(model)) {
      if (model.getDependencies().isEmpty()) {
        return model;
      }
      seenModels.add(model);
      model = model.dependencies().get(0);
    }
    return model;
  }

  /**
   * Follows references as the DslElement level to obtain the resulting element.
   *
   * @param expression expression to start at
   * @return resolved expression
   */
  @NotNull
  public static GradleDslSimpleExpression resolveElement(@NotNull GradleDslSimpleExpression expression) {
    while (expression instanceof GradleDslReference && !expression.hasCycle()) {
      GradleReferenceInjection injection = ((GradleDslReference)expression).getReferenceInjection();
      if (injection == null) {
        return expression;
      }
      GradleDslSimpleExpression next = injection.getToBeInjectedExpression();
      if (next == null) {
        return expression;
      }
      expression = next;
    }
    return expression;
  }

  @Nullable
  public static String getFileValue(@NotNull GradleDslMethodCall methodCall) {
    if (!(methodCall.getMethodName().equals(FILE_METHOD_NAME) && !methodCall.isConstructor() ||
          methodCall.getMethodName().equals(FILE_CONSTRUCTOR_NAME) && methodCall.isConstructor())) {
      return null;
    }

    StringBuilder builder = new StringBuilder();
    for (GradleDslExpression expression : methodCall.getArguments()) {
      if (expression instanceof GradleDslSimpleExpression) {
        String value = ((GradleDslSimpleExpression)expression).getValue(String.class);
        if (value != null) {
          if (builder.length() != 0) {
            builder.append("/");
          }
          builder.append(value);
        }
      }
    }
    String result = builder.toString();
    return result.isEmpty() ? null : result;
  }
}
