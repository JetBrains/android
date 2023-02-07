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

import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelSemanticsDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.VersionConstraint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * In AGP 7.0.0, some properties which previously had been set using a single operator with
 * overloads for multiple types (Integer or String) had their setters split into two distinct
 * operators, one for the Integer and one for the String.  The new operators are preferred, but
 * are only available from a certain version, and if there is uncertainty over the type of the
 * value we should probably continue to use the older generic setter.
 *
 * Note: despite its name, the MultiTypePropertyModel is not suitable for this, because that assumes
 * a single operator with multiple syntactic forms for the value.
 */
public class SdkOrPreviewTransform extends PropertyTransform {
  @NotNull private final ModelPropertyDescription propertyDescription;
  @NotNull private final String genericSetter;
  @NotNull private final String sdkSetter;
  @NotNull private final String previewSetter;
  /**
   * The constraint on versions for which the sdk- and previewSetters are available, or null (meaning
   * no constraint).
   */
  @Nullable private VersionConstraint versionConstraint;

  public SdkOrPreviewTransform(
    @NotNull ModelPropertyDescription propertyDescription,
    @NotNull String genericSetter,
    @NotNull String sdkSetter,
    @NotNull String previewSetter,
    @Nullable VersionConstraint versionConstraint
  ) {
    super();
    this.propertyDescription = propertyDescription;
    this.genericSetter = genericSetter;
    this.sdkSetter = sdkSetter;
    this.previewSetter = previewSetter;
    this.versionConstraint = versionConstraint;
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
    String operatorName = genericSetter;
    ExternalNameInfo.ExternalNameSyntax syntax = ExternalNameInfo.ExternalNameSyntax.METHOD;
    if (versionConstraint == null || versionConstraint.isOkWith(holder.getDslFile().getContext().getAgpVersion())) {
      Object resolvedValue = value;
      if (value instanceof ReferenceTo && ((ReferenceTo)value).getReferredElement() instanceof GradleDslSimpleExpression) {
        GradleDslSimpleExpression valueExpression = (GradleDslSimpleExpression)((ReferenceTo)value).getReferredElement();
        resolvedValue = valueExpression.getValue();
      }

      if (resolvedValue instanceof Integer) {
        operatorName = sdkSetter;
        syntax = holder.getDslFile().getWriter() instanceof GroovyDslNameConverter
                 ? ExternalNameInfo.ExternalNameSyntax.METHOD
                 : ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT;
      }
      else if (value instanceof String && ((String) value).startsWith("android-")) {
        operatorName = previewSetter;
        syntax = holder.getDslFile().getWriter() instanceof GroovyDslNameConverter
                 ? ExternalNameInfo.ExternalNameSyntax.METHOD
                 : ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT;
        value = ((String)value).substring("android-".length());
      }
      else if (resolvedValue instanceof String && !((String)resolvedValue).startsWith("android-")) {
        operatorName = previewSetter;
        syntax = holder.getDslFile().getWriter() instanceof GroovyDslNameConverter
                 ? ExternalNameInfo.ExternalNameSyntax.METHOD
                 : ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT;
      }
      else { // RawText, ReferenceTo things we can't prove are integer/string, non-literal strings beginning with android-
        // TODO(xof): when the genericSetter is removed, we will need to guess at this point.
        operatorName = genericSetter;
        syntax = ExternalNameInfo.ExternalNameSyntax.METHOD;
      }
    }
    GradleDslSimpleExpression expression = createBasicExpression(holder, value, GradleNameElement.create(operatorName));
    expression.setModelEffect(new ModelEffectDescription(propertyDescription, ModelSemanticsDescription.CREATE_WITH_VALUE));
    expression.setExternalSyntax(syntax);
    return expression;
  }

  @Override
  public @NotNull GradleDslElement replace(
    @NotNull GradleDslElement holder,
    @Nullable GradleDslElement oldElement,
    @NotNull GradleDslExpression newElement,
    @NotNull String name
  ) {
    // TODO(xof): shouldn't holder always be a GradlePropertiesDslElement?
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
