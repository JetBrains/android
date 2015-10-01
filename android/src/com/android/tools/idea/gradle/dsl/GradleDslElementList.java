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

import java.util.List;

/**
 * Represents a list of {@link GradleDslElement}s.
 */
public class GradleDslElementList extends GradleDslElement {
  @NotNull private final String myName;
  @NotNull private final List<GradleDslElement> myElements = Lists.newArrayList();
  @NotNull private final List<GradleDslElement> myToBeAddedElements = Lists.newArrayList();
  @NotNull private final List<GradleDslElement> myToBeRemovedElements = Lists.newArrayList();

  public GradleDslElementList(@Nullable GradleDslElement parent, @NotNull String name) {
    super(parent);
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public void addParsedElement(@NotNull GradleDslElement element) {
    // TODO: Add assertion statement to allow only elements with valid PsiElement.
    myElements.add(element);
  }

  public void addNewElement(@NotNull GradleDslElement element) {
    myToBeAddedElements.add(element);
    setModified(true);
  }

  public void removeElement(@NotNull GradleDslElement element) {
    if (myElements.contains(element)) {
      myToBeRemovedElements.add(element);
      setModified(true);
    }
  }

  @NotNull
  public List<GradleDslElement> getElements() {
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
  public <E extends GradleDslElement> List<E> getElements(Class<E> clazz) {
    List<E> result = Lists.newArrayList();
    for (GradleDslElement element : getElements()) {
      if (clazz.isInstance(element)) {
        result.add(clazz.cast(element));
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
  }
}
