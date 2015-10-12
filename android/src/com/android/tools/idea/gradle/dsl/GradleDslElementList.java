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
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

import java.util.Collection;
import java.util.List;

/**
 * Represents a list of {@link GradleDslElement}s.
 */
class GradleDslElementList extends GradleDslElement {
  @NotNull private final List<GradleDslElement> myElements = Lists.newArrayList();
  @NotNull private final List<GradleDslElement> myToBeAddedElements = Lists.newArrayList();
  @NotNull private final List<GradleDslElement> myToBeRemovedElements = Lists.newArrayList();

  GradleDslElementList(@NotNull GradleDslElement parent, @NotNull String name) {
    super(parent, null, name);
  }

  @NotNull
  String getName() {
    return myName;
  }

  void addParsedElement(@NotNull GradleDslElement element) {
    myElements.add(element);
  }

  void addNewElement(@NotNull GradleDslElement element) {
    myToBeAddedElements.add(element);
    setModified(true);
  }

  void removeElement(@NotNull GradleDslElement element) {
    if (myElements.contains(element)) {
      myToBeRemovedElements.add(element);
      setModified(true);
    }
  }

  @NotNull
  List<GradleDslElement> getElements() {
    if (myToBeAddedElements.isEmpty() && myToBeRemovedElements.isEmpty()) {
      return ImmutableList.copyOf(myElements);
    }

    List<GradleDslElement> result = Lists.newArrayList();
    result.addAll(myElements);
    result.addAll(myToBeAddedElements);
    for (GradleDslElement element : myToBeRemovedElements) {
      result.remove(element);
    }
    return result;
  }

  @NotNull
  <E extends GradleDslElement> List<E> getElements(Class<E> clazz) {
    List<E> result = Lists.newArrayList();
    for (GradleDslElement element : getElements()) {
      if (clazz.isInstance(element)) {
        result.add(clazz.cast(element));
      }
    }
    return result;
  }

  @Override
  @Nullable
  public GroovyPsiElement getPsiElement() {
    return null; // This class just act as a group of elements and doesn't represent any real elements on the file.
  }

  @Override
  @Nullable
  protected GroovyPsiElement create() {
    return myParent == null ? null : myParent.create();
  }

  @Override
  protected void setPsiElement(@Nullable GroovyPsiElement psiElement) {
  }

  @Override
  @NotNull
  protected Collection<GradleDslElement> getChildren() {
    return ImmutableList.copyOf(getElements());
  }

  @Override
  protected void apply() {
    for (GradleDslElement element : myToBeAddedElements) {
      if (element.create() != null) {
        myElements.add(element);
      }
    }
    myToBeAddedElements.clear();

    for (GradleDslElement element : myToBeRemovedElements) {
      if (myElements.remove(element)) {
        element.delete();
      }
    }
    myToBeRemovedElements.clear();

    for (GradleDslElement element : myElements) {
      if (element.isModified()) {
        element.applyChanges();
      }
    }
  }

  @Override
  protected void reset() {
    myToBeAddedElements.clear();
    myToBeRemovedElements.clear();
  }
}
