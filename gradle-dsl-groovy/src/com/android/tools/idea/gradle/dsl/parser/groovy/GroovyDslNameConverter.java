/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.groovy;

import static com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.ASSIGNMENT;
import static com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.AUGMENTED_ASSIGNMENT;
import static com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.METHOD;
import static com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo.ExternalNameSyntax.UNKNOWN;
import static com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter.Kind.GROOVY;
import static com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslUtil.convertToExternalTextValue;
import static com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslUtil.decodeStringLiteral;
import static com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslUtil.getGradleNameForPsiElement;
import static com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslUtil.gradleNameFor;
import static com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslUtil.isStringLiteral;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.ADD_AS_LIST;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.AUGMENT_LIST;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.AUGMENT_MAP;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.CLEAR_AND_AUGMENT_LIST;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.OTHER;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_LIST;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_MAP;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyType.MUTABLE_SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAL;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR_BUT_DO_NOT_USE_FOR_WRITING_IN_KTS;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VWO;

import com.android.tools.idea.gradle.dsl.model.BuildModelContext;
import com.android.tools.idea.gradle.dsl.parser.ExternalNameInfo;
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.SemanticsDescription;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class GroovyDslNameConverter implements GradleDslNameConverter {
  @NotNull private final BuildModelContext context;

  GroovyDslNameConverter(@NotNull BuildModelContext context) {
    this.context = context;
  }

  @NotNull
  @Override
  public Kind getKind() {
    return GROOVY;
  }

  @NotNull
  @Override
  public String psiToName(@NotNull PsiElement element) {
    // TODO(xof): I think that this might be unnecessary once psiToName is implemented in terms of gradleNameFor()
    //  because the treatment of GrReferenceExpressions may handle escapes in string identifiers
    //  automatically.
    if (isStringLiteral(element)) {
      StringBuilder sb = new StringBuilder();
      if (decodeStringLiteral(element, sb)) {
        return GradleNameElement.escape(sb.toString());
      }
    }
    // TODO(xof): the project-massaging in getGradleNameForPsiElement should be rewritten in gradleNameFor
    return getGradleNameForPsiElement(element);
  }

  @NotNull
  @Override
  public String convertReferenceText(@NotNull GradleDslElement context, @NotNull String referenceText) {
    String result = ApplicationManager.getApplication().runReadAction((Computable<String>)() -> {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.getDslFile().getProject());
      GrExpression expression = factory.createExpressionFromText(referenceText);
      return gradleNameFor(expression);
    });
    return result != null ? result : referenceText;
  }

  @Override
  public @NotNull String convertReferencePsi(@NotNull GradleDslElement context, @NotNull PsiElement element) {
    if (element instanceof GrExpression) {
      String result = gradleNameFor((GrExpression)element);
      if (result != null) return result;
    }
    return convertReferenceText(context, element.getText());
  }

  @NotNull
  @Override
  public String convertReferenceToExternalText(@NotNull GradleDslElement context,
                                               @NotNull String referenceText,
                                               boolean forInjection) {
    if (context instanceof GradleDslSimpleExpression) {
      return convertToExternalTextValue((GradleDslSimpleExpression)context, context.getDslFile(), referenceText, forInjection);
    }
    else {
      return referenceText;
    }
  }

  @NotNull
  @Override
  public String convertReferenceToExternalText(@NotNull GradleDslElement context,
                                               @NotNull GradleDslElement dslElement,
                                               boolean forInjection) {
    if (context instanceof GradleDslSimpleExpression) {
      String externalText = convertToExternalTextValue(dslElement, (GradleDslSimpleExpression)context, context.getDslFile(), forInjection);
      return externalText != null ? externalText : dslElement.getName();
    }
    else {
      return dslElement.getName();
    }
  }

  @NotNull
  @Override
  public ExternalNameInfo externalNameForParent(@NotNull String modelName, @NotNull GradleDslElement context) {
    @NotNull ExternalToModelMap map = context.getExternalToModelMap(this);
    ExternalNameInfo result = new ExternalNameInfo(modelName, UNKNOWN);
    for (ExternalToModelMap.Entry e : map.getEntrySet()) {
      if (e.modelEffectDescription.property.name.equals(modelName)) {
        if (e.versionConstraint != null && !e.versionConstraint.isOkWith(getContext().getAgpVersion())) continue;
        SemanticsDescription semantics = e.modelEffectDescription.semantics;
        if (Arrays.asList(SET, ADD_AS_LIST, AUGMENT_LIST, CLEAR_AND_AUGMENT_LIST, AUGMENT_MAP, OTHER).contains(semantics)) {
          return new ExternalNameInfo(e.surfaceSyntaxDescription.name, METHOD);
        }
        if (semantics == VAL && (e.modelEffectDescription.property.type == MUTABLE_SET ||
                                 e.modelEffectDescription.property.type == MUTABLE_LIST ||
                                 e.modelEffectDescription.property.type == MUTABLE_MAP)) {
          return new ExternalNameInfo(e.surfaceSyntaxDescription.name, AUGMENTED_ASSIGNMENT);
        }
        if (semantics == VAR || semantics == VWO || semantics == VAR_BUT_DO_NOT_USE_FOR_WRITING_IN_KTS) {
          result = new ExternalNameInfo(e.surfaceSyntaxDescription.name, ASSIGNMENT);
        }
      }
    }
    return result;
  }

  @NotNull
  @Override
  public Pattern getPatternForUnwrappedVariables() {
    return Pattern.compile("\\$(([a-zA-Z0-9_]\\w*)(\\.([a-zA-Z0-9_]\\w+))*)");
  }

  @NotNull
  @Override
  public Pattern getPatternForWrappedVariables() {
    return Pattern.compile("\\$\\{(.*)}");
  }

  @Nullable
  @Override
  public ModelPropertyDescription modelDescriptionForParent(@NotNull String externalName, @NotNull GradleDslElement context) {
    @NotNull ExternalToModelMap map = context.getExternalToModelMap(this);
    for (ExternalToModelMap.Entry e : map.getEntrySet()) {
      if (e.surfaceSyntaxDescription.name.equals(externalName)) {
        return e.modelEffectDescription.property;
      }
    }
    return null;
  }

  @Override
  public @NotNull BuildModelContext getContext() {
    return context;
  }
}
