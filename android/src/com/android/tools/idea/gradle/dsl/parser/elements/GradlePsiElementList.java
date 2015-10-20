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
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

import java.util.Collection;
import java.util.List;

/**
 * Represents a list of {@link GradlePsiElement}s.
 */
public class GradlePsiElementList extends GradlePsiElement {
  @NotNull private final List<GradlePsiElement> myElements = Lists.newArrayList();
  @NotNull private final List<GradlePsiElement> myToBeAddedElements = Lists.newArrayList();
  @NotNull private final List<GradlePsiElement> myToBeRemovedElements = Lists.newArrayList();

  public GradlePsiElementList(@NotNull GradlePsiElement parent, @NotNull String name) {
    super(parent, null, name);
  }

  public void addParsedElement(@NotNull GradlePsiElement element) {
    myElements.add(element);
  }

  public void addNewElement(@NotNull GradlePsiElement element) {
    myToBeAddedElements.add(element);
    setModified(true);
  }

  public void removeElement(@NotNull GradlePsiElement element) {
    if (myElements.contains(element)) {
      myToBeRemovedElements.add(element);
      setModified(true);
    }
  }

  @NotNull
  public List<GradlePsiElement> getElements() {
    if (myToBeAddedElements.isEmpty() && myToBeRemovedElements.isEmpty()) {
      return ImmutableList.copyOf(myElements);
    }

    List<GradlePsiElement> result = Lists.newArrayList();
    result.addAll(myElements);
    result.addAll(myToBeAddedElements);
    for (GradlePsiElement element : myToBeRemovedElements) {
      result.remove(element);
    }
    return result;
  }

  @NotNull
  public <E extends GradlePsiElement> List<E> getElements(Class<E> clazz) {
    List<E> result = Lists.newArrayList();
    for (GradlePsiElement element : getElements()) {
      if (clazz.isInstance(element)) {
        result.add(clazz.cast(element));
      }
    }
    return result;
  }

  @Override
  @Nullable
  public GroovyPsiElement getGroovyPsiElement() {
    return null; // This class just act as a group of elements and doesn't represent any real elements on the file.
  }

  @Override
  @Nullable
  public GroovyPsiElement create() {
    return myParent == null ? null : myParent.create();
  }

  @Override
  public void setGroovyPsiElement(@Nullable GroovyPsiElement psiElement) {
  }

  @Override
  @NotNull
  protected Collection<GradlePsiElement> getChildren() {
    return ImmutableList.copyOf(getElements());
  }

  @Override
  protected void apply() {
    for (GradlePsiElement element : myToBeAddedElements) {
      if (element.create() != null) {
        myElements.add(element);
      }
    }
    myToBeAddedElements.clear();

    for (GradlePsiElement element : myToBeRemovedElements) {
      if (myElements.remove(element)) {
        element.delete();
      }
    }
    myToBeRemovedElements.clear();

    for (GradlePsiElement element : myElements) {
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
