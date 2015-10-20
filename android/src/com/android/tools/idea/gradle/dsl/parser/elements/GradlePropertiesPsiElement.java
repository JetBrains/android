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

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for {@link GradlePsiElement}s that represent a closure block or a map element. It provides the functionality to store the
 * data as key value pairs and convenient methods to access the data.
 */
public abstract class GradlePropertiesPsiElement extends GradlePsiElement {
  @NotNull protected Map<String, GradlePsiElement> myProperties = Maps.newHashMap();
  @NotNull protected Map<String, GradlePsiElement> myToBeAddedProperties = Maps.newHashMap();
  @NotNull protected Set<String> myToBeRemovedProperties = Sets.newHashSet();

  protected GradlePropertiesPsiElement(@Nullable GradlePsiElement parent, @Nullable GroovyPsiElement psiElement, @NotNull String name) {
    super(parent, psiElement, name);
  }

  /**
   * Sets or replaces the given {@code property} value with the give {@code element}.
   *
   * <p>This method should be used when the given {@code property} is defined using an assigned statement.
   */
  public void setPsiElement(@NotNull String property, @NotNull GradlePsiElement element) {
    myProperties.put(property, element);
  }

  /**
   * Sets or replaces the given {@code property} value with the given {@code element}.
   *
   * <p>This method should be used when the given {@code property} is defined using an application statement. As the application statements
   * can have different meanings like append vs replace for list elements, the sub classes can override this method to do the right thing
   * for any given property.
   */
  public void addPsiElement(@NotNull String property, @NotNull GradlePsiElement element) {
    myProperties.put(property, element);
  }

  protected void addAsPsiLiteralList(@NotNull String property, GradlePsiLiteral psiLiteral) {
    GroovyPsiElement psiElement = psiLiteral.getGroovyPsiElement();
    if (psiElement == null) {
      return;
    }
    GradlePsiLiteralList literalList = new GradlePsiLiteralList(this, psiElement, property, psiLiteral.getLiteral());
    myProperties.put(property, literalList);
  }

  public void addToPsiLiteralList(@NotNull String property, @NotNull GradlePsiElement element) {
    GroovyPsiElement psiElement = element.getGroovyPsiElement();
    if (psiElement == null) {
      return;
    }

    GrLiteral[] literalsToAdd =  null;
    if (element instanceof GradlePsiLiteral) {
      literalsToAdd = new GrLiteral[]{((GradlePsiLiteral)element).getLiteral()};
    } else if (element instanceof GradlePsiLiteralList) {
      List<GradlePsiLiteral> gradlePsiLiterals = ((GradlePsiLiteralList)element).getElements();
      literalsToAdd = new GrLiteral[gradlePsiLiterals.size()];
      for (int i = 0; i < gradlePsiLiterals.size(); i++) {
        literalsToAdd[i] = gradlePsiLiterals.get(i).getLiteral();
      }
    }
    if (literalsToAdd == null) {
      return;
    }

    GradlePsiLiteralList gradlePsiLiteralList = getProperty(property, GradlePsiLiteralList.class);
    if (gradlePsiLiteralList != null) {
      gradlePsiLiteralList.add(psiElement, property,literalsToAdd);
      return;
    }

    gradlePsiLiteralList = new GradlePsiLiteralList(this, psiElement, property, literalsToAdd);
    myProperties.put(property, gradlePsiLiteralList);
  }

  /**
   * Adds the given element to the to-be added elements list, which are applied when {@link #apply()} method is invoked
   * or discarded when the {@lik #resetState()} method is invoked.
   */
  @NotNull
  public GradlePropertiesPsiElement setNewElement(@NotNull String property, @NotNull GradlePsiElement newElement) {
    myToBeAddedProperties.put(property, newElement);
    setModified(true);
    return this;
  }

  @NotNull
  public Collection<String> getProperties() {
    Set<String> result = Sets.newHashSet();
    result.addAll(myProperties.keySet());
    result.addAll(myToBeAddedProperties.keySet());
    result.removeAll(myToBeRemovedProperties);
    return result;
  }

  /**
   * Returns the {@link GradlePsiElement} corresponding to the given {@code property}, or {@code null} if the given {@code property}
   * does not exist in this element.
   */
  @Nullable
  public GradlePsiElement getPropertyElement(@NotNull String property) {
    if (!property.contains(".")) {
      if (myToBeRemovedProperties.contains(property)) {
        return null;
      }
      GradlePsiElement toBeAddedElement = myToBeAddedProperties.get(property);
      return toBeAddedElement != null ? toBeAddedElement : myProperties.get(property);
    }
    List<String> propertyNameSegments = Splitter.on('.').splitToList(property);
    GradlePropertiesPsiElement nestedElement = this;
    for (int i = 0; i < propertyNameSegments.size() - 1; i++) {
      GradlePsiElement element = nestedElement.getPropertyElement(propertyNameSegments.get(i));
      if (element instanceof GradlePropertiesPsiElement) {
        nestedElement = (GradlePropertiesPsiElement)element;
      } else {
        return null;
      }
    }
    return nestedElement.getPropertyElement(propertyNameSegments.get(propertyNameSegments.size() - 1));
  }

  /**
   * Returns the value of the given {@code property} of the type {@code clazz}, or {@code null} when either the given {@code property} does
   * not exists in this element or the given {@code property} value is not of the type {@code clazz}.
   */
  @Nullable
  public <T> T getProperty(@NotNull String property, @NotNull Class<T> clazz) {
    GradlePsiElement propertyElement = getPropertyElement(property);
    if (propertyElement != null) {
      if (clazz.isInstance(propertyElement)) {
        return clazz.cast(propertyElement);
      }
      else if (propertyElement instanceof GradlePsiLiteral) {
        return ((GradlePsiLiteral)propertyElement).getValue(clazz);
      }
    }
    return null;
  }

  @NotNull
  public GradlePropertiesPsiElement setLiteralProperty(@NotNull String property, @NotNull String value) {
    return setLiteralPropertyImpl(property, value);
  }

  @NotNull
  public GradlePropertiesPsiElement setLiteralProperty(@NotNull String property, @NotNull Integer value) {
    return setLiteralPropertyImpl(property, value);
  }

  @NotNull
  public GradlePropertiesPsiElement setLiteralProperty(@NotNull String property, @NotNull Boolean value) {
    return setLiteralPropertyImpl(property, value);
  }

  @NotNull
  private GradlePropertiesPsiElement setLiteralPropertyImpl(@NotNull String property, @NotNull Object value) {
    GradlePsiLiteral literalElement = getProperty(property, GradlePsiLiteral.class);
    if (literalElement == null) {
      literalElement = new GradlePsiLiteral(this, property);
      myToBeAddedProperties.put(property, literalElement);
    }
    literalElement.setValue(value);
    return this;
  }

  @NotNull
  public GradlePropertiesPsiElement addToListProperty(@NotNull String property, @NotNull String value) {
    return addToListPropertyImpl(property, value);
  }

  @NotNull
  public GradlePropertiesPsiElement removeFromListProperty(@NotNull String property, @NotNull String value) {
    return removeFromListPropertyImpl(property, value);
  }

  @NotNull
  public GradlePropertiesPsiElement replaceInListProperty(@NotNull String property, @NotNull String oldValue, @NotNull String newValue) {
    return replaceInListPropertyImpl(property, oldValue, newValue);
  }

  @NotNull
  private GradlePropertiesPsiElement addToListPropertyImpl(@NotNull String property, @NotNull Object value) {
    GradlePsiLiteralList gradlePsiLiteralList = getProperty(property, GradlePsiLiteralList.class);
    if (gradlePsiLiteralList == null) {
      gradlePsiLiteralList = new GradlePsiLiteralList(this, property);
      myToBeAddedProperties.put(property, gradlePsiLiteralList);
    }
    gradlePsiLiteralList.add(value);
    return this;
  }

  @NotNull
  private GradlePropertiesPsiElement removeFromListPropertyImpl(@NotNull String property, @NotNull Object value) {
    GradlePsiLiteralList gradlePsiLiteralList = getProperty(property, GradlePsiLiteralList.class);
    if (gradlePsiLiteralList != null) {
      gradlePsiLiteralList.remove(value);
    }
    return this;
  }

  @NotNull
  private GradlePropertiesPsiElement replaceInListPropertyImpl(@NotNull String property,
                                                               @NotNull Object oldValue, @NotNull Object newValue) {
    GradlePsiLiteralList gradlePsiLiteralList = getProperty(property, GradlePsiLiteralList.class);
    if (gradlePsiLiteralList != null) {
      gradlePsiLiteralList.replace(oldValue, newValue);
    }
    return this;
  }

  @NotNull
  public GradlePropertiesPsiElement setInMapProperty(@NotNull String property, @NotNull String name, @NotNull String value) {
    return setInMapPropertyImpl(property, name, value);
  }

  @NotNull
  public GradlePropertiesPsiElement setInMapProperty(@NotNull String property, @NotNull String name, @NotNull Integer value) {
    return setInMapPropertyImpl(property, name, value);
  }

  @NotNull
  public GradlePropertiesPsiElement setInMapProperty(@NotNull String property, @NotNull String name, @NotNull Boolean value) {
    return setInMapPropertyImpl(property, name, value);
  }

  @NotNull
  private GradlePropertiesPsiElement setInMapPropertyImpl(@NotNull String property, @NotNull String name, @NotNull Object value) {
    GradlePsiLiteralMap gradlePsiLiteralMap = getProperty(property, GradlePsiLiteralMap.class);
    if (gradlePsiLiteralMap == null) {
      gradlePsiLiteralMap = new GradlePsiLiteralMap(this, property);
      myToBeAddedProperties.put(property, gradlePsiLiteralMap);
    }
    gradlePsiLiteralMap.put(name, value);
    return this;
  }

  @NotNull
  public GradlePropertiesPsiElement removeFromMapProperty(@NotNull String property, @NotNull String name) {
    GradlePsiLiteralMap gradlePsiLiteralMap = getProperty(property, GradlePsiLiteralMap.class);
    if (gradlePsiLiteralMap != null) {
      gradlePsiLiteralMap.removeProperty(name);
    }
    return this;
  }

  /**
   * Marks the given {@code property} for removal.
   *
   * <p>The actual property will be removed from Gradle file when {@link #apply()} method is invoked.
   *
   * <p>The property will be un-marked for removal when {@link #reset()} method is invoked.
   */
  public void removeProperty(@NotNull String property) {
    myToBeRemovedProperties.add(property);
    setModified(true);
  }

  /**
   * Returns the list of values of type {@code clazz} when the given {@code property} corresponds to a {@link GradlePsiLiteralList}.
   *
   * <p>Returns {@code null} when either the given {@code property} does not exists in this element or does not corresponds to a
   * {@link GradlePsiLiteralList}.
   *
   * <p>Returns an empty list when the given {@code property} exists in this element and corresponds to a {@link GradlePsiLiteralList}, but either
   * that list is empty or does not contain any element of type {@code clazz}.
   */
  @Nullable
  public <E> List<E> getListProperty(@NotNull String property, @NotNull Class<E> clazz) {
    GradlePsiLiteralList gradlePsiLiteralList = getProperty(property, GradlePsiLiteralList.class);
    if (gradlePsiLiteralList != null) {
      return gradlePsiLiteralList.getValues(clazz);
    }
    return null;
  }

  /**
   * Returns the map from properties of the type {@link String} to the values of the type {@code clazz} when the given {@code property}
   * corresponds to a {@link GradlePsiLiteralMap}.
   *
   * <p>Returns {@code null} when either the given {@code property} does not exists in this element or does not corresponds to a
   * {@link GradlePsiLiteralMap}.
   *
   * <p>Returns an empty map when the given {@code property} exists in this element and corresponds to a {@link GradlePsiLiteralMap}, but either that
   * map is empty or does not contain any values of type {@code clazz}.
   */
  @Nullable
  public <V> Map<String, V> getMapProperty(@NotNull String property, @NotNull Class<V> clazz) {
    GradlePsiLiteralMap gradlePsiLiteralMap = getProperty(property, GradlePsiLiteralMap.class);
    if (gradlePsiLiteralMap != null) {
      return gradlePsiLiteralMap.getValues(clazz);
    }
    return null;
  }

  @Override
  @NotNull
  protected Collection<GradlePsiElement> getChildren() {
    List<GradlePsiElement> children = Lists.newArrayList();
    for (String property : getProperties()) {
      GradlePsiElement element = getPropertyElement(property);
      if (element != null) {
        children.add(element);
      }
    }
    return children;
  }

  @Override
  protected void apply() {
    for (Map.Entry<String, GradlePsiElement> entry : myToBeAddedProperties.entrySet()) {
      String property = entry.getKey();
      GradlePsiElement element = entry.getValue();
      if (element.create() != null) {
        setPsiElement(property, element);
      }
    }
    myToBeAddedProperties.clear();

    for (String property : myToBeRemovedProperties) {
      GradlePsiElement element = myProperties.remove(property);
      if (element != null) {
        element.delete();
      }
    }
    myToBeRemovedProperties.clear();

    for (GradlePsiElement element : myProperties.values()) {
      if (element.isModified()) {
        element.applyChanges();
      }
    }
  }

  @Override
  protected void reset() {
    myToBeRemovedProperties.clear();
    myToBeAddedProperties.clear();
    for (GradlePsiElement element : myProperties.values()) {
      if (element.isModified()) {
        element.resetState();
      }
    }
  }

  protected void clear() {
    myToBeRemovedProperties.clear();
    myToBeAddedProperties.clear();
    myProperties.clear();
  }
}
