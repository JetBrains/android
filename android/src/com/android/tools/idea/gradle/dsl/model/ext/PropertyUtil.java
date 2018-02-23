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

import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyTransform.*;

public class PropertyUtil {
  @NotNull
  public static final TransformCondition defaultTransformCondition = e -> true;

  @NotNull
  public static final ElementTransform defaultElementTransform = e -> e;

  @NotNull
  public static final ElementBindingFunction defaultBindingFunction =
    (holder, oldElement, value, name) -> createOrReplaceBasicExpression(holder, oldElement, value, GradleNameElement.create(name));

  @NotNull
  public static GradleDslExpression createOrReplaceBasicExpression(@NotNull GradleDslElement parent,
                                                                   @Nullable GradleDslElement oldElement,
                                                                   @NotNull Object value,
                                                                   @NotNull GradleNameElement name) {
    boolean isReference = value instanceof ReferenceTo;

    // Check if we can reuse the element.
    if (!isReference && oldElement instanceof GradleDslLiteral ||
        isReference && oldElement instanceof GradleDslReference) {
      GradleDslExpression expression = (GradleDslExpression)oldElement;
      expression.setValue(value);
      return expression;
    }
    else {
      GradleDslExpression newElement;
      if (!isReference) {
        newElement = new GradleDslLiteral(parent, name);
      }
      else {
        newElement = new GradleDslReference(parent, name);
      }

      newElement.setValue(value);
      return newElement;
    }
  }

  public static void replaceElement(@NotNull GradleDslElement holder,
                                    @Nullable GradleDslElement oldElement,
                                    @NotNull GradleDslElement newElement,
                                    @NotNull String name) {
    if (holder instanceof GradlePropertiesDslElement) {
      if (oldElement != null) {
        ((GradlePropertiesDslElement)holder).replaceElement(name, oldElement, newElement);
      }
      else {
        ((GradlePropertiesDslElement)holder).setNewElement(name, newElement);
      }
    }
    else if (holder instanceof GradleDslExpressionList) {
      int index = removeElement(oldElement);
      GradleDslExpressionList list = (GradleDslExpressionList)holder;
      assert index != -1; // Can't bind with an invalid index.
      // TODO: Remove this assertion
      assert newElement instanceof GradleDslExpression;
      list.addNewExpression((GradleDslExpression)newElement, index);
    }
    else if (holder instanceof GradleDslElementList) {
      removeElement(oldElement);
      GradleDslElementList list = (GradleDslElementList)holder;
      list.addNewElement(newElement);
    }
    else {
      throw new IllegalStateException("Property holder has unknown type, " + holder);
    }
  }

  public static int removeElement(@Nullable GradleDslElement element) {
    int index = -1;
    if (element == null) {
      return index;
    }

    GradleDslElement holder = element.getParent();

    if (holder instanceof GradlePropertiesDslElement) {
      ((GradlePropertiesDslElement)holder).removeProperty(element);
    }
    else if (holder instanceof GradleDslExpressionList) {
      GradleDslExpressionList list = (GradleDslExpressionList)holder;
      index = list.findIndexOf(element);
      ((GradleDslExpressionList)holder).removeElement(element);
    }
    else {
      assert holder instanceof GradleDslElementList;
      GradleDslElementList elementList = (GradleDslElementList)holder;
      elementList.removeElement(element);
    }
    return index;
  }

  public static final PropertyTransform defaultTransform =
    new PropertyTransform(defaultTransformCondition, defaultElementTransform, defaultBindingFunction);
}
