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
import com.android.tools.idea.gradle.dsl.model.ext.transforms.DefaultTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.PropertyTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.SingleArgumentMethodTransform;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PropertyUtil {
  @NonNls private static final String FILE_METHOD_NAME = "file";

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
      if (oldElement != null) {
        name = oldElement.getNameElement();
      }

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
        list.addNewExpression((GradleDslExpression)newElement, list.getExpressions().size());
      }
    }
    else if (holder instanceof GradleDslElementList) {
      GradleDslElementList list = (GradleDslElementList)holder;
      if (oldElement != null) {
        list.replaceElement(oldElement, newElement);
      }
      else {
        list.addNewElement(newElement);
      }
    }
    else {
      throw new IllegalStateException("Property holder has unknown type, " + holder);
    }
  }

  public static void removeElement(@NotNull GradleDslElement element) {
    GradleDslElement holder = element.getParent();

    if (holder instanceof GradlePropertiesDslElement) {
      ((GradlePropertiesDslElement)holder).removeProperty(element);
    }
    else if (holder instanceof GradleDslExpressionList) {
      GradleDslExpressionList list = (GradleDslExpressionList)holder;
      list.removeElement(element);
    }
    else {
      assert holder instanceof GradleDslElementList;
      GradleDslElementList elementList = (GradleDslElementList)holder;
      elementList.removeElement(element);
    }
  }

  public static final PropertyTransform DEFAULT_TRANSFORM = new DefaultTransform();
  public static final PropertyTransform FILE_TRANSFORM = new SingleArgumentMethodTransform(FILE_METHOD_NAME);
}
