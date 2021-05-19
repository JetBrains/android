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
package com.android.tools.idea.gradle.dsl.api.ext;

import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel;
import com.android.tools.idea.gradle.dsl.api.util.GradleNameElementUtil;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.android.SigningConfigsDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a reference to another property or variable.
 */
public final class ReferenceTo {
  @NotNull private static final String SIGNING_CONFIGS = "signingConfigs";
  @NotNull private String myReferenceText;  // The internal fully qualified name of the DSL reference.
  @NotNull private GradlePropertyModel propertyModel;

  /**
   * Create a reference to a {@link GradlePropertyModel}.
   * @param model the model we want to refer to.
   */
  public ReferenceTo(@NotNull GradlePropertyModel model) {
    myReferenceText = model.getFullyQualifiedName();
    propertyModel = model;
  }

  /**
   * Create a reference to a {@link SigningConfigModel}.
   * In this method the reference is set to a model, so we are dealing with internal names.
   * @param model the signingConfigModel we are trying to refer to.
   */
  public ReferenceTo(@NotNull SigningConfigModel model) {
    myReferenceText = SIGNING_CONFIGS + "." + GradleNameElementUtil.escape(model.name());
    propertyModel = GradlePropertyModelBuilder.createModelFromDslElement(model.getDslElement());
  }

  /**
   * Create a reference to a dsl element given its name.
   * Please only consider using this function if you cannot fetch the {@link GradlePropertyModel} or the {@link SigningConfigModel} of the
   * element you want to refer to, as this function only guarantees a correct result if the {@param referredElementName} is in the expected
   * syntax.
   * @param referredElementName the name of the dslElement we are trying to set a reference to. This name should be the canonical name in
   *                            the external build language.
   * @param propertyContext the context where we are setting the reference. This is very important to determine the scoping of the lookup
   *                   in the dsl tree.
   * @return a referenceTo referring to a {@link GradlePropertyModel} if found, or null.
   */
  @Nullable
  public static ReferenceTo createReferenceFromText(@NotNull String referredElementName, @NotNull GradlePropertyModel propertyContext) {
    GradlePropertyModel referenceModel =
      GradlePropertyModelBuilder.getModelFromExternalText(referredElementName, propertyContext.getRawPropertyHolder());
    if (referenceModel == null) {
      return null;
    }
    return new ReferenceTo(referenceModel);
  }

  @Nullable
  public GradleDslElement getReferredElement() {
    return propertyModel.getRawElement();
  }

  /**
   * Get the reference fully qualified dsl name.
   * @return the reference text.
   */
  @NotNull
  public String getText() {
    return myReferenceText;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ReferenceTo text = (ReferenceTo)o;
    return Objects.equal(myReferenceText, text.myReferenceText);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(myReferenceText);
  }

  @Override
  @NotNull
  public String toString() {
    // Special treatment for signingConfigs.
    if (propertyModel.getRawPropertyHolder() instanceof SigningConfigsDslElement) return SIGNING_CONFIGS + "." + propertyModel.getName();
    return propertyModel.getName();
  }
}
