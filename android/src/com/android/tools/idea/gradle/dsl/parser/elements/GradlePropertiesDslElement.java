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
 * Base class for {@link GradleDslElement}s that represent a closure block or a map element. It provides the functionality to store the
 * data as key value pairs and convenient methods to access the data.
 */
public abstract class GradlePropertiesDslElement extends GradleDslElement {
  @NotNull protected Map<String, GradleDslElement> myProperties = Maps.newHashMap();
  @NotNull protected Map<String, GradleDslElement> myToBeAddedProperties = Maps.newHashMap();
  @NotNull protected Set<String> myToBeRemovedProperties = Sets.newHashSet();

  protected GradlePropertiesDslElement(@Nullable GradleDslElement parent, @Nullable GroovyPsiElement psiElement, @NotNull String name) {
    super(parent, psiElement, name);
  }

  /**
   * Sets or replaces the given {@code property} value with the give {@code element}.
   *
   * <p>This method should be used when the given {@code property} is defined using an assigned statement.
   */
  public void setDslElement(@NotNull String property, @NotNull GradleDslElement element) {
    myProperties.put(property, element);
  }

  /**
   * Sets or replaces the given {@code property} value with the given {@code element}.
   *
   * <p>This method should be used when the given {@code property} is defined using an application statement. As the application statements
   * can have different meanings like append vs replace for list elements, the sub classes can override this method to do the right thing
   * for any given property.
   */
  public void addDslElement(@NotNull String property, @NotNull GradleDslElement element) {
    myProperties.put(property, element);
  }

  protected void addAsDslLiteralList(@NotNull String property, GradleDslLiteral dslLiteral) {
    GroovyPsiElement psiElement = dslLiteral.getPsiElement();
    if (psiElement == null) {
      return;
    }
    GradleDslLiteralList literalList = new GradleDslLiteralList(this, psiElement, property, dslLiteral.getLiteral());
    myProperties.put(property, literalList);
  }

  public void addToDslLiteralList(@NotNull String property, @NotNull GradleDslElement element) {
    GroovyPsiElement psiElement = element.getPsiElement();
    if (psiElement == null) {
      return;
    }

    GrLiteral[] literalsToAdd =  null;
    if (element instanceof GradleDslLiteral) {
      literalsToAdd = new GrLiteral[]{((GradleDslLiteral)element).getLiteral()};
    } else if (element instanceof GradleDslLiteralList) {
      List<GradleDslLiteral> gradleDslLiterals = ((GradleDslLiteralList)element).getElements();
      literalsToAdd = new GrLiteral[gradleDslLiterals.size()];
      for (int i = 0; i < gradleDslLiterals.size(); i++) {
        literalsToAdd[i] = gradleDslLiterals.get(i).getLiteral();
      }
    }
    if (literalsToAdd == null) {
      return;
    }

    GradleDslLiteralList gradleDslLiteralList = getProperty(property, GradleDslLiteralList.class);
    if (gradleDslLiteralList != null) {
      gradleDslLiteralList.add(psiElement, property,literalsToAdd);
      return;
    }

    gradleDslLiteralList = new GradleDslLiteralList(this, psiElement, property, literalsToAdd);
    myProperties.put(property, gradleDslLiteralList);
  }

  /**
   * Adds the given element to the to-be added elements list, which are applied when {@link #apply()} method is invoked
   * or discarded when the {@lik #resetState()} method is invoked.
   */
  @NotNull
  public GradlePropertiesDslElement setNewElement(@NotNull String property, @NotNull GradleDslElement newElement) {
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
   * Returns the {@link GradleDslElement} corresponding to the given {@code property}, or {@code null} if the given {@code property}
   * does not exist in this element.
   */
  @Nullable
  public GradleDslElement getPropertyElement(@NotNull String property) {
    if (!property.contains(".")) {
      if (myToBeRemovedProperties.contains(property)) {
        return null;
      }
      GradleDslElement toBeAddedElement = myToBeAddedProperties.get(property);
      return toBeAddedElement != null ? toBeAddedElement : myProperties.get(property);
    }
    List<String> propertyNameSegments = Splitter.on('.').splitToList(property);
    GradlePropertiesDslElement nestedElement = this;
    for (int i = 0; i < propertyNameSegments.size() - 1; i++) {
      GradleDslElement element = nestedElement.getPropertyElement(propertyNameSegments.get(i));
      if (element instanceof GradlePropertiesDslElement) {
        nestedElement = (GradlePropertiesDslElement)element;
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
    GradleDslElement propertyElement = getPropertyElement(property);
    if (propertyElement != null) {
      if (clazz.isInstance(propertyElement)) {
        return clazz.cast(propertyElement);
      }
      else if (propertyElement instanceof GradleDslLiteral) {
        return ((GradleDslLiteral)propertyElement).getValue(clazz);
      }
    }
    return null;
  }

  @NotNull
  public GradlePropertiesDslElement setLiteralProperty(@NotNull String property, @NotNull String value) {
    return setLiteralPropertyImpl(property, value);
  }

  @NotNull
  public GradlePropertiesDslElement setLiteralProperty(@NotNull String property, @NotNull Integer value) {
    return setLiteralPropertyImpl(property, value);
  }

  @NotNull
  public GradlePropertiesDslElement setLiteralProperty(@NotNull String property, @NotNull Boolean value) {
    return setLiteralPropertyImpl(property, value);
  }

  @NotNull
  private GradlePropertiesDslElement setLiteralPropertyImpl(@NotNull String property, @NotNull Object value) {
    GradleDslLiteral literalElement = getProperty(property, GradleDslLiteral.class);
    if (literalElement == null) {
      literalElement = new GradleDslLiteral(this, property);
      myToBeAddedProperties.put(property, literalElement);
    }
    literalElement.setValue(value);
    return this;
  }

  @NotNull
  public GradlePropertiesDslElement addToListProperty(@NotNull String property, @NotNull String value) {
    return addToListPropertyImpl(property, value);
  }

  @NotNull
  public GradlePropertiesDslElement removeFromListProperty(@NotNull String property, @NotNull String value) {
    return removeFromListPropertyImpl(property, value);
  }

  @NotNull
  public GradlePropertiesDslElement replaceInListProperty(@NotNull String property, @NotNull String oldValue, @NotNull String newValue) {
    return replaceInListPropertyImpl(property, oldValue, newValue);
  }

  @NotNull
  private GradlePropertiesDslElement addToListPropertyImpl(@NotNull String property, @NotNull Object value) {
    GradleDslLiteralList gradleDslLiteralList = getProperty(property, GradleDslLiteralList.class);
    if (gradleDslLiteralList == null) {
      gradleDslLiteralList = new GradleDslLiteralList(this, property);
      myToBeAddedProperties.put(property, gradleDslLiteralList);
    }
    gradleDslLiteralList.add(value);
    return this;
  }

  @NotNull
  private GradlePropertiesDslElement removeFromListPropertyImpl(@NotNull String property, @NotNull Object value) {
    GradleDslLiteralList gradleDslLiteralList = getProperty(property, GradleDslLiteralList.class);
    if (gradleDslLiteralList != null) {
      gradleDslLiteralList.remove(value);
    }
    return this;
  }

  @NotNull
  private GradlePropertiesDslElement replaceInListPropertyImpl(@NotNull String property,
                                                               @NotNull Object oldValue, @NotNull Object newValue) {
    GradleDslLiteralList gradleDslLiteralList = getProperty(property, GradleDslLiteralList.class);
    if (gradleDslLiteralList != null) {
      gradleDslLiteralList.replace(oldValue, newValue);
    }
    return this;
  }

  @NotNull
  public GradlePropertiesDslElement setInMapProperty(@NotNull String property, @NotNull String name, @NotNull String value) {
    return setInMapPropertyImpl(property, name, value);
  }

  @NotNull
  public GradlePropertiesDslElement setInMapProperty(@NotNull String property, @NotNull String name, @NotNull Integer value) {
    return setInMapPropertyImpl(property, name, value);
  }

  @NotNull
  public GradlePropertiesDslElement setInMapProperty(@NotNull String property, @NotNull String name, @NotNull Boolean value) {
    return setInMapPropertyImpl(property, name, value);
  }

  @NotNull
  private GradlePropertiesDslElement setInMapPropertyImpl(@NotNull String property, @NotNull String name, @NotNull Object value) {
    GradleDslLiteralMap gradleDslLiteralMap = getProperty(property, GradleDslLiteralMap.class);
    if (gradleDslLiteralMap == null) {
      gradleDslLiteralMap = new GradleDslLiteralMap(this, property);
      myToBeAddedProperties.put(property, gradleDslLiteralMap);
    }
    gradleDslLiteralMap.put(name, value);
    return this;
  }

  @NotNull
  public GradlePropertiesDslElement removeFromMapProperty(@NotNull String property, @NotNull String name) {
    GradleDslLiteralMap gradleDslLiteralMap = getProperty(property, GradleDslLiteralMap.class);
    if (gradleDslLiteralMap != null) {
      gradleDslLiteralMap.removeProperty(name);
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
   * Returns the list of values of type {@code clazz} when the given {@code property} corresponds to a {@link GradleDslLiteralList}.
   *
   * <p>Returns {@code null} when either the given {@code property} does not exists in this element or does not corresponds to a
   * {@link GradleDslLiteralList}.
   *
   * <p>Returns an empty list when the given {@code property} exists in this element and corresponds to a {@link GradleDslLiteralList}, but either
   * that list is empty or does not contain any element of type {@code clazz}.
   */
  @Nullable
  public <E> List<E> getListProperty(@NotNull String property, @NotNull Class<E> clazz) {
    GradleDslLiteralList gradleDslLiteralList = getProperty(property, GradleDslLiteralList.class);
    if (gradleDslLiteralList != null) {
      return gradleDslLiteralList.getValues(clazz);
    }
    return null;
  }

  /**
   * Returns the map from properties of the type {@link String} to the values of the type {@code clazz} when the given {@code property}
   * corresponds to a {@link GradleDslLiteralMap}.
   *
   * <p>Returns {@code null} when either the given {@code property} does not exists in this element or does not corresponds to a
   * {@link GradleDslLiteralMap}.
   *
   * <p>Returns an empty map when the given {@code property} exists in this element and corresponds to a {@link GradleDslLiteralMap}, but either that
   * map is empty or does not contain any values of type {@code clazz}.
   */
  @Nullable
  public <V> Map<String, V> getMapProperty(@NotNull String property, @NotNull Class<V> clazz) {
    GradleDslLiteralMap gradleDslLiteralMap = getProperty(property, GradleDslLiteralMap.class);
    if (gradleDslLiteralMap != null) {
      return gradleDslLiteralMap.getValues(clazz);
    }
    return null;
  }

  @Override
  @NotNull
  protected Collection<GradleDslElement> getChildren() {
    List<GradleDslElement> children = Lists.newArrayList();
    for (String property : getProperties()) {
      GradleDslElement element = getPropertyElement(property);
      if (element != null) {
        children.add(element);
      }
    }
    return children;
  }

  @Override
  protected void apply() {
    for (Map.Entry<String, GradleDslElement> entry : myToBeAddedProperties.entrySet()) {
      String property = entry.getKey();
      GradleDslElement element = entry.getValue();
      if (element.create() != null) {
        setDslElement(property, element);
      }
    }
    myToBeAddedProperties.clear();

    for (String property : myToBeRemovedProperties) {
      GradleDslElement element = myProperties.remove(property);
      if (element != null) {
        element.delete();
      }
    }
    myToBeRemovedProperties.clear();

    for (GradleDslElement element : myProperties.values()) {
      if (element.isModified()) {
        element.applyChanges();
      }
    }
  }

  @Override
  protected void reset() {
    myToBeRemovedProperties.clear();
    myToBeAddedProperties.clear();
    for (GradleDslElement element : myProperties.values()) {
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
