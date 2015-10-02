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

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class for {@link GradleDslElement}s that represent a closure block or a map element. It provides the functionality to store the
 * data as key value pairs and convenient methods to access the data.
 */
abstract class GradleDslPropertiesElement extends GradleDslElement {
  @Nullable private GrClosableBlock myPsiElement;

  @NotNull private Map<String, GradleDslElement> myProperties = Maps.newHashMap();
  @NotNull private Map<String, GradleDslElement> myToBeAddedProperties = Maps.newHashMap();
  @NotNull private Set<String> myToBeRemovedProperties = Sets.newHashSet();

  protected GradleDslPropertiesElement(@Nullable GradleDslElement parent) {
    super(parent);
  }

  void setBlockElement(@NotNull GrClosableBlock psiElement) {
    myPsiElement = psiElement;
  }

  /**
   * Sets or replaces the given {@code property} value with the give {@code element}.
   *
   * <p>This method should be used when the given {@code property} is defined using an assigned statement.
   */
  void setParsedElement(@NotNull String property, @NotNull GradleDslElement element) {
    // TODO: Add assertion statement to allow only elements with valid PsiElement.
    myProperties.put(property, element);
  }

  /**
   * Sets or replaces the given {@code property} value with the given {@code element}.
   *
   * <p>This method should be used when the given {@code property} is defined using an application statement. As the application statements
   * can have different meanings like append vs replace for list elements, the sub classes can override this method to do the right thing
   * for any given property.
   */
  void addParsedElement(@NotNull String property, @NotNull GradleDslElement element) {
    // TODO: Add assertion statement to allow only elements with valid PsiElement.
    myProperties.put(property, element);
  }

  /**
   * Adds the given element to the to-be added elements list, which are applied when {@link #apply()} method is invoked
   * or discarded when the {@lik #resetState()} method is invoked.
   */
  @NotNull
  GradleDslPropertiesElement setNewElement(@NotNull String property, @NotNull GradleDslElement newElement) {
    myToBeAddedProperties.put(property, newElement);
    setModified(true);
    return this;
  }

  @NotNull
  Collection<String> getProperties() {
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
  GradleDslElement getPropertyElement(@NotNull String property) {
    if (!property.contains(".")) {
      if (myToBeRemovedProperties.contains(property)) {
        return null;
      }
      GradleDslElement toBeAddedElement = myToBeAddedProperties.get(property);
      return toBeAddedElement != null ? toBeAddedElement : myProperties.get(property);
    }
    List<String> propertyNameSegments = Splitter.on('.').splitToList(property);
    GradleDslPropertiesElement nestedElement = this;
    for (int i = 0; i < propertyNameSegments.size() - 1; i++) {
      GradleDslElement element = nestedElement.getPropertyElement(propertyNameSegments.get(i));
      if (element instanceof GradleDslPropertiesElement) {
        nestedElement = (GradleDslPropertiesElement)element;
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
  <T> T getProperty(@NotNull String property, @NotNull Class<T> clazz) {
    GradleDslElement propertyElement = getPropertyElement(property);
    if (propertyElement != null) {
      if (clazz.isInstance(propertyElement)) {
        return clazz.cast(propertyElement);
      }
      else if (propertyElement instanceof LiteralElement) {
        return ((LiteralElement)propertyElement).getValue(clazz);
      }
    }
    return null;
  }

  @NotNull
  protected GradleDslPropertiesElement setLiteralProperty(@NotNull String property, @NotNull String value) {
    return setLiteralPropertyImpl(property, value);
  }

  @NotNull
  protected GradleDslPropertiesElement setLiteralProperty(@NotNull String property, @NotNull Integer value) {
    return setLiteralPropertyImpl(property, value);
  }

  @NotNull
  protected GradleDslPropertiesElement setLiteralProperty(@NotNull String property, @NotNull Boolean value) {
    return setLiteralPropertyImpl(property, value);
  }

  @NotNull
  private GradleDslPropertiesElement setLiteralPropertyImpl(@NotNull String property, @NotNull Object value) {
    LiteralElement literalElement = getProperty(property, LiteralElement.class);
    if (literalElement == null) {
      literalElement = new LiteralElement(this, property);
      myToBeAddedProperties.put(property, literalElement);
    }
    literalElement.setValue(value);
    return this;
  }

  @NotNull
  protected GradleDslPropertiesElement addToListProperty(@NotNull String property, @NotNull String value) {
    return addToListPropertyImpl(property, value);
  }

  @NotNull
  protected GradleDslPropertiesElement removeFromListProperty(@NotNull String property, @NotNull String value) {
    return removeFromListPropertyImpl(property, value);
  }

  @NotNull
  protected GradleDslPropertiesElement replaceInListProperty(@NotNull String property, @NotNull String oldValue, @NotNull String newValue) {
    return replaceInListPropertyImpl(property, oldValue, newValue);
  }

  @NotNull
  private GradleDslPropertiesElement addToListPropertyImpl(@NotNull String property, @NotNull Object value) {
    LiteralListElement literalListElement = getProperty(property, LiteralListElement.class);
    if (literalListElement == null) {
      literalListElement = new LiteralListElement(this, property);
      myToBeAddedProperties.put(property, literalListElement);
    }
    literalListElement.add(value);
    return this;
  }

  @NotNull
  private GradleDslPropertiesElement removeFromListPropertyImpl(@NotNull String property, @NotNull Object value) {
    LiteralListElement literalListElement = getProperty(property, LiteralListElement.class);
    if (literalListElement != null) {
      literalListElement.remove(value);
    }
    return this;
  }

  @NotNull
  private GradleDslPropertiesElement replaceInListPropertyImpl(@NotNull String property,
                                                               @NotNull Object oldValue, @NotNull Object newValue) {
    LiteralListElement literalListElement = getProperty(property, LiteralListElement.class);
    if (literalListElement != null) {
      literalListElement.replace(oldValue, newValue);
    }
    return this;
  }

  @NotNull
  protected GradleDslPropertiesElement setInMapProperty(@NotNull String property, @NotNull String name, @NotNull String value) {
    return setInMapPropertyImpl(property, name, value);
  }

  @NotNull
  protected GradleDslPropertiesElement setInMapProperty(@NotNull String property, @NotNull String name, @NotNull Integer value) {
    return setInMapPropertyImpl(property, name, value);
  }

  @NotNull
  protected GradleDslPropertiesElement setInMapProperty(@NotNull String property, @NotNull String name, @NotNull Boolean value) {
    return setInMapPropertyImpl(property, name, value);
  }

  @NotNull
  private GradleDslPropertiesElement setInMapPropertyImpl(@NotNull String property, @NotNull String name, @NotNull Object value) {
    LiteralMapElement literalMapElement = getProperty(property, LiteralMapElement.class);
    if (literalMapElement == null) {
      literalMapElement = new LiteralMapElement(this, property);
      myToBeAddedProperties.put(property, literalMapElement);
    }
    literalMapElement.put(name, value);
    return this;
  }

  @NotNull
  protected GradleDslPropertiesElement removeFromMapProperty(@NotNull String property, @NotNull String name) {
    LiteralMapElement literalMapElement = getProperty(property, LiteralMapElement.class);
    if (literalMapElement != null) {
      literalMapElement.removeProperty(name);
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
  void removeProperty(@NotNull String property) {
    myToBeRemovedProperties.add(property);
    setModified(true);
  }

  /**
   * Returns the list of values of type {@code clazz} when the given {@code property} corresponds to a {@link LiteralListElement}.
   *
   * <p>Returns {@code null} when either the given {@code property} does not exists in this element or does not corresponds to a
   * {@link LiteralListElement}.
   *
   * <p>Returns an empty list when the given {@code property} exists in this element and corresponds to a {@link LiteralListElement}, but either
   * that list is empty or does not contain any element of type {@code clazz}.
   */
  @Nullable
  protected <E> List<E> getListProperty(@NotNull String property, @NotNull Class<E> clazz) {
    LiteralListElement literalListElement = getProperty(property, LiteralListElement.class);
    if (literalListElement != null) {
      return literalListElement.getValues(clazz);
    }
    return null;
  }

  /**
   * Returns the map from properties of the type {@link String} to the values of the type {@code clazz} when the given {@code property}
   * corresponds to a {@link LiteralMapElement}.
   *
   * <p>Returns {@code null} when either the given {@code property} does not exists in this element or does not corresponds to a
   * {@link LiteralMapElement}.
   *
   * <p>Returns an empty map when the given {@code property} exists in this element and corresponds to a {@link LiteralMapElement}, but either that
   * map is empty or does not contain any values of type {@code clazz}.
   */
  @Nullable
  protected <V> Map<String, V> getMapProperty(@NotNull String property, @NotNull Class<V> clazz) {
    LiteralMapElement literalMapElement = getProperty(property, LiteralMapElement.class);
    if (literalMapElement != null) {
      return literalMapElement.getValues(clazz);
    }
    return null;
  }

  @Override
  protected void apply() {
    // TODO: implement.
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
}
