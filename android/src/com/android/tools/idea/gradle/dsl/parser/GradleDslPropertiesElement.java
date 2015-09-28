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
package com.android.tools.idea.gradle.dsl.parser;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Base class for {@link GradleDslElement}s that represent a closure block or a map element. It provides the functionality to store the
 * data as key value pairs and convenient methods to access the data.
 */
public abstract class GradleDslPropertiesElement extends GradleDslElement {
  protected GrClosableBlock myPsiElement;
  protected Map<String, GradleDslElement> myProperties = Maps.newHashMap();

  protected GradleDslPropertiesElement(@Nullable GradleDslElement parent) {
    super(parent);
  }

  public void setBlockElement(@NotNull GrClosableBlock psiElement) {
    myPsiElement = psiElement;
  }

  /**
   * Sets or replaces the given {@code property} value with the give {@code element}.
   *
   * <p>This method should be used when the given {@code property} is defined using an assigned statement.
   */
  public void setProperty(@NotNull String property, @NotNull GradleDslElement element) {
    myProperties.put(property, element);
  }

  /**
   * Sets or replaces the given {@code property} value with the give {@code element}.
   *
   * <p>This method should be used when the given {@code property} is defined using an application statement. As the application statements
   * can have different meanings like append vs replace for list elements, the sub classes can override this method to do the right thing
   * for any given property.
   */
  public void addProperty(@NotNull String property, @NotNull GradleDslElement element) {
    myProperties.put(property, element);
  }

  @NotNull
  public Collection<String> getProperties() {
    return myProperties.keySet();
  }

  /**
   * Returns the {@link GradleDslElement} corresponding to the given {@code property}, or {@code null} if the given {@code property}
   * does not exist in this element.
   */
  @Nullable
  public GradleDslElement getPropertyElement(@NotNull String property) {
    if (!property.contains(".")) {
      return myProperties.get(property);
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
  public <T> T getProperty(@NotNull String property, @NotNull Class<T> clazz) {
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

  /**
   * Returns the list of values of type {@code clazz} when the given {@code property} corresponds to a {@link ListElement}.
   *
   * <p>Returns {@code null} when either the given {@code property} does not exists in this element or does not corresponds to a
   * {@link ListElement}.
   *
   * <p>Returns an empty list when the given {@code property} exists in this element and corresponds to a {@link ListElement}, but either
   * that list is empty or does not contain any element of type {@code clazz}.
   */
  @Nullable
  public <E> List<E> getListProperty(@NotNull String property, @NotNull Class<E> clazz) {
    ListElement listElement = getProperty(property, ListElement.class);
    if (listElement != null) {
      return listElement.getValues(clazz);
    }
    return null;
  }

  /**
   * Returns the map from properties of the type {@link String} to the values of the type {@code clazz} when the given {@code property}
   * corresponds to a {@link MapElement}.
   *
   * <p>Returns {@code null} when either the given {@code property} does not exists in this element or does not corresponds to a
   * {@link MapElement}.
   *
   * <p>Returns an empty map when the given {@code property} exists in this element and corresponds to a {@link MapElement}, but either that
   * map is empty or does not contain any values of type {@code clazz}.
   */
  @Nullable
  public <V> Map<String, V> getMapProperty(@NotNull String property, @NotNull Class<V> clazz) {
    MapElement mapElement = getProperty(property, MapElement.class);
    if (mapElement != null) {
      return mapElement.getValues(clazz);
    }
    return null;
  }

  @Override
  protected void apply() {
    // TODO: implement.
  }

  @Override
  protected void reset() {
    // TODO: implement.
  }
}
