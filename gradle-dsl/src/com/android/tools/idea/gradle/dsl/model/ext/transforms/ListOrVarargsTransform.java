/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.createBasicExpression;

import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelSemanticsDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This transform allows the system to select the correct operator for giving values to some properties which
 * have distinct operator names for augmenting/setting elements of a collection, and setting the collection's
 * contents directly from an iterable; the only current example are those of the proguardFiles / setProguardFiles
 * family, where there is no proguardFiles(Iterable) method.
 */
public class ListOrVarargsTransform extends PropertyTransform {
  @NotNull private final ModelPropertyDescription propertyDescription;
  @NotNull private final String setter;
  @NotNull private final String varargs;

  // TODO(xof): This transform could be removed if operator selection at the GradleDslNameConverter level were
  //  more able to take Dsl contextual information (such as an argument's type) into consideration.
  public ListOrVarargsTransform(
    @NotNull ModelPropertyDescription propertyDescription,
    @NotNull String setter,
    @NotNull String varargs
  ) {
    super();
    this.propertyDescription = propertyDescription;
    this.setter = setter;
    this.varargs = varargs;
  }

  @Override
  public boolean test(@Nullable GradleDslElement e, @NotNull GradleDslElement holder) {
    return e == null || e.getModelProperty() == propertyDescription;
  }

  @Override
  public @Nullable GradleDslElement transform(@Nullable GradleDslElement e) {
    if (e == null) return null;
    return e;
  }

  @Override
  public @NotNull GradleDslExpression bind(
    @NotNull GradleDslElement holder,
    @Nullable GradleDslElement oldElement,
    @NotNull Object value,
    @NotNull String name
  ) {
    // This gets called if we do e.g. proguardFile().setValue(v) where v is *not* a List -- if it is a list, it goes through
    // setListValue(), which ends up calling bindList().  The most common case where this will happen is if v is a ReferenceTo
    // a list.
    String operatorName = setter;
    ExternalNameInfo.ExternalNameSyntax syntax = ExternalNameInfo.ExternalNameSyntax.METHOD;
    GradleDslSimpleExpression expression = createBasicExpression(holder, value, GradleNameElement.create(operatorName));
    expression.setModelEffect(new ModelEffectDescription(propertyDescription, ModelSemanticsDescription.CREATE_WITH_VALUE));
    expression.setExternalSyntax(syntax);
    return expression;
  }

  @Override
  public @NotNull GradleDslExpression replace(
    @NotNull GradleDslElement holder,
    @Nullable GradleDslElement oldElement,
    @NotNull GradleDslExpression newElement,
    @NotNull String name
  ) {
    GradlePropertiesDslElement propertiesHolder = (GradlePropertiesDslElement) holder;
    if (oldElement != null) {
      propertiesHolder.replaceElement(oldElement, newElement);
    }
    else {
      propertiesHolder.setNewElement(newElement);
    }
    return newElement;
  }
}
