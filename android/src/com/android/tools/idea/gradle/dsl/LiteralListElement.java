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
package com.android.tools.idea.gradle.dsl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.List;

/**
 * Represents an element which consists a list of {@link LiteralElement}s.
 */
final class LiteralListElement extends GradleDslElement {
  @NotNull private final String myName;
  @NotNull private final List<LiteralElement> myElements = Lists.newArrayList();
  @NotNull private final List<LiteralElement> myToBeAddedElements = Lists.newArrayList();
  @NotNull private final List<LiteralElement> myToBeRemovedElements = Lists.newArrayList();

  LiteralListElement(@Nullable GradleDslElement parent, @NotNull String name) {
    super(parent);
    myName = name;
  }

  LiteralListElement(@Nullable GradleDslElement parent, @NotNull String name, @NotNull GrListOrMap list) {
    super(parent);
    assert !list.isMap();
    myName = name;
    for (GrExpression exp : list.getInitializers()) {
      if (exp instanceof GrLiteral) {
        myElements.add(new LiteralElement(this, name, (GrLiteral)exp));
      }
    }
  }

  LiteralListElement(@Nullable GradleDslElement parent, @NotNull String name, @NotNull GrLiteral... literals) {
    super(parent);
    myName = name;
    for (GrLiteral literal : literals) {
      myElements.add(new LiteralElement(this, name, literal));
    }
  }

  void add(@NotNull String name, @NotNull GrLiteral... literals) {
    for (GrLiteral literal : literals) {
      myElements.add(new LiteralElement(this, name, literal));
    }
  }

  void add(@NotNull LiteralElement... elements) {
    for (LiteralElement element : elements) {
      myElements.add(element);
    }
  }

  @NotNull
  String getName() {
    return myName;
  }

  @NotNull
  List<LiteralElement> getElements() {
    if (myToBeAddedElements.isEmpty() && myToBeRemovedElements.isEmpty()) {
      return ImmutableList.copyOf(myElements);
    }

    List<LiteralElement> result = Lists.newArrayList();
    result.addAll(myElements);
    result.addAll(myToBeAddedElements);
    for (LiteralElement element : myToBeRemovedElements) {
      result.remove(element);
    }
    return result;
  }

  void add(@NotNull Object elementValue) {
    LiteralElement element = new LiteralElement(this, myName);
    element.setValue(elementValue);
    myToBeAddedElements.add(element);
  }

  void remove(@NotNull Object elementValue) {
    for (LiteralElement element : getElements()) {
      if (elementValue.equals(element.getValue())) {
        myToBeRemovedElements.add(element);
        setModified(true);
        return;
      }
    }
  }

  void replace(@NotNull Object oldElementValue, @NotNull Object newElementValue) {
    for (LiteralElement element : getElements()) {
      if (oldElementValue.equals(element.getValue())) {
        element.setValue(newElementValue);
        return;
      }
    }
  }

  /**
   * Returns the list of values of type {@code clazz}.
   *
   * <p>Returns an empty list when there are no elements of type {@code clazz}.
   */
  @NotNull
  <E> List<E> getValues(Class<E> clazz) {
    List<E> result = Lists.newArrayList();
    for (LiteralElement element : getElements()) {
      E value = element.getValue(clazz);
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  @Override
  protected void apply() {
  }

  @Override
  protected void reset() {
    myToBeAddedElements.clear();
    myToBeRemovedElements.clear();
    for (LiteralElement element : myElements) {
      if (element.isModified()) {
        element.resetState();
      }
    }
  }
}
