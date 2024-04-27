/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax;
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleScriptFile;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.SemanticsDescription;
import com.intellij.psi.PsiElement;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT;
import static com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.AUGMENTED_ASSIGNMENT;
import static com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement.APPLY_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.ADD_AS_LIST;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.AUGMENT_LIST;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.AUGMENT_MAP;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.CLEAR_AND_AUGMENT_LIST;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_LIST;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_MAP;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR_BUT_DO_NOT_USE_FOR_WRITING_IN_KTS;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VWO;

/**
 * Base class for all the {@link GradleDslElement}s that represent blocks like android, productFlavors, buildTypes etc.
 */
public class GradleDslBlockElement extends GradlePropertiesDslElement {
  protected GradleDslBlockElement(@Nullable GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, null, name);
  }

  // hasBraces is a reflection of the surface condition of an underlying condition, which might be more accurately described as whether the
  // PsiElement of this DslElement in a suitable condition to add properties to.  The primary (currently, as of 2020-Jan-15, only) way in
  // which this can happen is for a Dsl block to be defined without braces, for example the `foo` configuration in:
  //
  // configurations {
  //   foo
  // }
  //
  // or the `jcenter()` repository in:
  //
  // repositories {
  //   jcenter()
  // }
  //
  // in both cases these are not syntactic "blocks" (in that they don't have a brace-delimited closure/action argument) but are represented
  // by GradleDslBlockElements.
  private boolean hasBraces = true;

  public void setHasBraces(boolean newValue) {
    this.hasBraces = newValue;
  }

  public boolean getHasBraces() {
    return this.hasBraces;
  }

  @Nullable
  @Override
  public PsiElement create() {
    if (!hasBraces) delete();
    return super.create();
  }

  @Override
  public boolean isBlockElement() {
    return true;
  }

  private ModelEffectDescription getModelEffect(@NotNull GradleDslElement element) {
    String name = element.getName();
    @NotNull ExternalToModelMap nameMapper = getExternalToModelMap(element.getDslFile().getParser());
    ExternalNameSyntax syntax = element.getExternalSyntax();
    if (syntax == ASSIGNMENT || syntax == AUGMENTED_ASSIGNMENT) {
      for (ExternalToModelMap.Entry entry : nameMapper.getEntrySet()) {
        String entryName = entry.surfaceSyntaxDescription.name;
        Integer arity = entry.surfaceSyntaxDescription.arity;
        if (entryName.equals(name) && Objects.equals(arity, property)) {
          return entry.modelEffectDescription;
        }
      }
    }
    else {
      for (ExternalToModelMap.Entry entry : nameMapper.getEntrySet()) {
        String entryName = entry.surfaceSyntaxDescription.name;
        Integer arity = entry.surfaceSyntaxDescription.arity;
        // TODO(xof): distinguish between semantics based on expressed arities (at the moment we return the first method entry we find,
        //  whether or not the arity is compatible.
        if (entryName.equals(name) && !Objects.equals(arity, property)) {
          return entry.modelEffectDescription;
        }
      }
    }
    return null;
  }

  /**
   * The contract of this method is to arrange that an element parsed from Dsl source corresponding to setting a model property is
   * recognized as that model property.  This is needed because in general there are multiple ways of setting model properties, even
   * within one Dsl language (Groovy setter methods visible and setVisible) let alone between multiple Dsl languages (Groovy visible vs
   * Kotlin isVisible).  If we are dealing with a model property, we annotate the Dsl element with a description of its effect on the
   * model.
   *
   * @param element a Dsl element potentially representing a model property
   */
  private void maybeCanonizeElement(@NotNull GradleDslElement element) { // NOTYPO
    ModelEffectDescription effect = getModelEffect(element);
    if (effect == null) return;
    SemanticsDescription description = effect.semantics;
    ExternalNameSyntax syntax = element.getExternalSyntax();
    if (syntax == ASSIGNMENT) {
      if (description != VAR && description != VWO && description != VAR_BUT_DO_NOT_USE_FOR_WRITING_IN_KTS) {
        // we are maybe-renaming a property involved in an assignment, which only makes sense if the property has a writer (i.e.
        // it is a property and not a read-only VAL)
        return;
      }
    }
    else if (syntax == AUGMENTED_ASSIGNMENT) {
      if (effect.property.type != MUTABLE_SET && effect.property.type != MUTABLE_LIST && effect.property.type != MUTABLE_MAP) {
        return;
      }
    }
    // TODO(xof): for methods, we should eventually only canonize (NOTYPO) if we have a SET.  Until the semantics are fully encoded,
    //  though, there are other (description == OTHER) methods which end up here.
    element.setModelEffect(effect);
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    if (APPLY_BLOCK_NAME.equals(element.getFullName()) && element.getDslFile() instanceof GradleScriptFile) {
      GradleScriptFile scriptFile = (GradleScriptFile) element.getDslFile();
      ApplyDslElement applyDslElement = getPropertyElement(APPLY_BLOCK_NAME, ApplyDslElement.class);
      if (applyDslElement == null) {
        applyDslElement = new ApplyDslElement(this, scriptFile);
        super.addParsedElement(applyDslElement);
      }
      applyDslElement.addParsedElement(element);
      return;
    }
    ModelEffectDescription effect = getModelEffect(element);
    if (effect != null) {
      SemanticsDescription description = effect.semantics;
      if (description == ADD_AS_LIST && element instanceof GradleDslSimpleExpression) {
        addAsParsedDslExpressionList(effect, (GradleDslSimpleExpression) element);
        return;
      }
      if (description == AUGMENT_LIST || description == CLEAR_AND_AUGMENT_LIST) {
        if (description == CLEAR_AND_AUGMENT_LIST) {
          removeProperty(effect.property.name);
        }
        addToParsedExpressionList(effect, element);
        return;
      }
      if (description == AUGMENT_MAP) {
        addToParsedExpressionMap(effect, element);
        return;
      }
      maybeCanonizeElement(element); // NOTYPO
    }
    super.addParsedElement(element);
  }

  @Override
  public void augmentParsedElement(@NotNull GradleDslElement element) {
    maybeCanonizeElement(element);
    super.augmentParsedElement(element);
  }

  @Override
  public void setParsedElement(@NotNull GradleDslElement element) {
    maybeCanonizeElement(element); // NOTYPO
    super.setParsedElement(element);
  }

  @Override
  public void setNewElement(@NotNull GradleDslElement element) {
    maybeCanonizeElement(element); // NOTYPO
    super.setNewElement(element);
  }
}
