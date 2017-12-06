// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.dsl.api.ext;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.util.TypeReference;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * This class represents a property or variable declared or referenced by the ExtraPropertiesExtension
 * of the projects Gradle build. It allows access to the properties name, values and dependencies.
 */
public interface GradlePropertyModel {
  // The following are TypeReferences used in calls to getValue and getUnresolvedValue.
  TypeReference<String> STRING_TYPE = new TypeReference<String>() {};
  TypeReference<Integer> INTEGER_TYPE = new TypeReference<Integer>() {};
  TypeReference<Boolean> BOOLEAN_TYPE = new TypeReference<Boolean>() {};
  TypeReference<List<GradlePropertyModel>> LIST_TYPE = new TypeReference<List<GradlePropertyModel>>() {};
  TypeReference<Map<String, GradlePropertyModel>> MAP_TYPE = new TypeReference<Map<String, GradlePropertyModel>>() {};

  /**
   * Represents the type of the value stored by this property, or when a type can't be found
   * {@code UNKNOWN}. These value types provide a guarantee about the type of value
   * that the property contains:
   * <ul>
   *   <li>{@code STRING} - Pass {@link STRING_TYPE} to {@link #getValue(TypeReference)}</li>
   *   <li>{@code INTEGER} - Pass {@link INTEGER_TYPE} to {@link #getValue(TypeReference)}</li>
   *   <li>{@code BOOLEAN} - Pass {@link BOOLEAN_TYPE} to {@link #getValue(TypeReference)}</li>
   *   <li>{@code MAP} - Pass {@link MAP_TYPE} to {@link #getValue(TypeReference)}</li>
   *   <li>{@code LIST} - Pass {@link LIST_TYPE} to {@link #getValue(TypeReference)}</li>
   *   <li>{@code UNKNOWN} - No guarantees about the type of this element can be made}</li>
   * </ul>
   */
  enum ValueType {
    STRING,
    INTEGER,
    BOOLEAN,
    MAP,
    LIST,
    UNKNOWN,
  }

  /**
   * Represents the type of this property.
   * <ul>
   *   <li>{@code REGULAR} - this is a Gradle property, e.g "ext.prop1 = 'value'"</li>
   *   <li>{@code VARIABLE} - this is a DSL variable, e.g "def prop1 = 'value'"</li>
   *   <li>{@code DERIVED} - this is a internal property derived from values an a map of list, e.g property "key"
   *                          in "prop1 = ["key" : 'value']"</li>
   *   <li>{@code GLOBAL}   - this is a global property defined by Gradle e.g projectDir</li>
   * </ul>
   */
  enum PropertyType {
    REGULAR,
    VARIABLE,
    DERIVED,
    GLOBAL,
  }

  /**
   * @return the {@link ValueType} of the property. For references, this method returns the type of the referred to
   * property.
   */
  @NotNull
  ValueType getValueType();

  /**
   * @return the {@link PropertyType} of the property.
   */
  @NotNull
  PropertyType getPropertyType();

  /**
   * @return the value that is held be this element, if it can be assigned from the given {@code TypeReference}. Otherwise
   * this method returns null.
   */
  @Nullable
  <T> T getValue(@NotNull TypeReference<T> typeReference);

  /**
   * Gets the value of the unresolved property, this returns the value without attempting to resolve string injections
   * or references. For example in:
   * <pre>
   * <code>ext {
   *   prop1 = 'val'
   *   prop2 = prop1
   *   prop3 = "Hello ${prop1}"
   * }
   * </code>
   * </pre>
   * Getting the unresolved value of "prop2" will return "prop1" and for "prop3" it will return "Hello ${prop1}".
   * Otherwise if the property has no string injections or is not a reference this method will return the same value
   * as {@link #getValue(Class)}.
   */
  @Nullable
  <T> T getUnresolvedValue(@NotNull TypeReference<T> clazz);

  /**
   * Returns a list of all immediate dependencies for the property. This includes references and string injections within
   * values, lists and map values.
   */
  @NotNull
  List<GradlePropertyModel> getDependencies();

  /**
   * Returns the name of the property.
   */
  @NotNull
  String getName();

  /**
   * Returns the name of the property including any enclosing blocks, e.g "ext.deps.prop1".
   */
  @NotNull
  String getFullyQualifiedName();

  /**
   * Returns the Gradle file where this gradle property lives.
   */
  @NotNull
  VirtualFile getGradleFile();

  /**
   * Sets the value on this property to the given {@code value}.
   * Note: This method currently DOES NOT change the value of the property when {@link GradleBuildModel#applyChanges()} is
   * called. TODO: Fix this
   */
  void setValue(@NotNull Object value);
}
