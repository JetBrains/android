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
package com.android.tools.idea.gradle.dsl.model.ext.transforms;

import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p>Defines a transform that can be used to allow a {@link GradlePropertyModel} to represent complex properties.
 * Simple properties will be in the form of:</p>
 * <code>propertyName = propertyValue</code><br>
 *
 * <p>However sometimes properties will need to be represented in other ways such as {@link SigningConfigModel#storeFile()}.
 * The type of this property is a file and as such need to be shown in the gradle file as:</p>
 * <code>propertyName = file(propertyValue)</code><br>
 * <p>See {@link SingleArgumentMethodTransform} as an example transform for this.</p>
 *
 * <p>A {@link PropertyTransform} allows us to define extra formats for properties which give us access to the value that we are
 * interested in. A {@link GradlePropertyModel} can have any number of transforms associated with it.</p>
 *
 * <p>If no transforms are added via {@link GradlePropertyModelImpl#addTransform(PropertyTransform)} then the default transform
 * {@link PropertyUtil#DEFAULT_TRANSFORM} is used.</p>
 */
public abstract class PropertyTransform {
  public PropertyTransform() {
  }

  /**
   * Function for testing the properties {@link GradleDslElement} to see whether this transform should be activated.
   *
   * @param e the element contained by a property, null if the property has no element.
   * @return whether or not this transform should be activated
   */
  public abstract boolean test(@Nullable GradleDslElement e);

  /**
   * A function that transforms the properties {@link GradleDslElement} into one that should be used as the value.
   *
   * @param e the element contained by a property. When used in a {@link PropertyTransform} this argument is
   *          guaranteed to have had a previous call to {@link PropertyTransform#test(GradleDslElement)} return {@code true}.
   * @return the element that should be used to represent the property's value if this transform is active,
   * if the transform can't correctly transform the element then {@code null} should be returned.
   */
  @Nullable
  public abstract GradleDslElement transform(@NotNull GradleDslElement e);

  /**
   * A function used to bind a new value to a {@link GradlePropertyModel}.
   *
   * @param holder     the parent of the property being represented by the {@link GradlePropertyModel}
   * @param oldElement the old element being represented by the {@link GradlePropertyModel}, if this is {@code null} then the
   *                   {@link GradleDslElement} returned will be attached to the {@code holder}.
   * @param value      the new value that the property should be set to.
   * @param name       the name of the property, this may be useful in replacing existing elements or creating new ones.
   * @return the new element to bind. If this is not the same object as oldElement then GradlePropertyModel will handle
   * ensuring that the property element is correctly replaced, otherwise it is assumed that this method has already
   * created the correct tree structure.
   */
  @NotNull
  public abstract GradleDslElement bind(@NotNull GradleDslElement holder,
                                        @Nullable GradleDslElement oldElement,
                                        @NotNull Object value,
                                        @NotNull String name);
}
