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
package com.android.tools.idea.gradle.dsl.model.ext;

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.java.LanguageLevelPropertyModel;
import com.android.tools.idea.gradle.dsl.model.java.LanguageLevelPropertyModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;

/**
 * Builder class for subclasses of GradlePropertyModelImpl.
 */
public class GradlePropertyModelBuilder {
  /**
   * Creates a builder.
   *
   * @param holder the GradlePropertiesDslElement that the property belongs to
   * @param name of the property that the model should be built for
   * @return new builder for model.
   */
  @NotNull
  public static GradlePropertyModelBuilder create(@NotNull GradlePropertiesDslElement holder, @NotNull String name) {
    return new GradlePropertyModelBuilder(holder, name);
  }

  @NotNull
  private final GradlePropertiesDslElement myHolder;
  @NotNull
  private final String myName;
  @NotNull
  private PropertyType myType = REGULAR;
  private boolean myIsMethod = false;
  @NotNull
  private List<PropertyTransform> myTransforms = new ArrayList<>();

  private GradlePropertyModelBuilder(@NotNull GradlePropertiesDslElement holder, @NotNull String name) {
    myHolder = holder;
    myName = name;
  }

  /**
   * Sets whether or not the property model should be represented as a method call or an assignment statement. The
   * difference is as follows:
   * {@code true}  -> Method Call -> propertyName propertyValue
   * {@code false} -> Assignment  -> propertyName = propertyValue
   *
   * This is only applied to new elements that are created via this model. If an element already exists on file and does not
   * use the form that is requested, changing its value may or may not cause the form to change. A form change will occur
   * if the underlying {@link GradleDslElement} can't be reused i.e if an existing literal property is being set to a reference.
   *
   * Defaults to {@code false}.
   *
   * @param bool whether to use the method call form.
   * @return this model builder
   */
  public GradlePropertyModelBuilder asMethod(boolean bool) {
    myIsMethod = bool;
    return this;
  }

  /**
   * Sets the type of this model, defaults to {@link PropertyType#REGULAR}
   *
   * @param type of the result model.
   * @return this model builder
   */
  public GradlePropertyModelBuilder withType(@NotNull PropertyType type) {
    myType = type;
    return this;
  }

  /**
   * Adds a transform to this property model. See {@link PropertyTransform} for details.
   *
   * @param transform to add
   * @return this model builder
   */
  public GradlePropertyModelBuilder addTransform(@NotNull PropertyTransform transform) {
    myTransforms.add(0, transform);
    return this;
  }

  /**
   * Builds a {@link GradlePropertyModel} with the properties defined by this builder
   *
   * @return the built model
   */
  public GradlePropertyModel build() {
    GradleDslElement currentElement = myHolder.getPropertyElement(myName);
    GradlePropertyModelImpl model = currentElement == null
                                    ? new GradlePropertyModelImpl(myHolder, myType, myName) : new GradlePropertyModelImpl(currentElement);
    if (myIsMethod) {
      model.markAsMethodCall();
    }

    for (PropertyTransform t : myTransforms) {
      model.addTransform(t);
    }

    return model;
  }

  /**
   * Builds a {@link ResolvedPropertyModel} with the properties defined by this builder.
   *
   * @return the built model
   */
  public ResolvedPropertyModel buildResolved() {
    return build().resolve();
  }

  /**
   * Builds a {@link LanguageLevelPropertyModel} with the properties defined by this builder.
   *
   * @return the built model
   */
  public LanguageLevelPropertyModel buildLanguage() {
    return new LanguageLevelPropertyModelImpl(build());
  }
}
