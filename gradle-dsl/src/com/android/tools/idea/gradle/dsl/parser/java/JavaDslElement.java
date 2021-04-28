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
package com.android.tools.idea.gradle.dsl.parser.java;

import static com.android.tools.idea.gradle.dsl.model.BaseCompileOptionsModelImpl.SOURCE_COMPATIBILITY;
import static com.android.tools.idea.gradle.dsl.model.BaseCompileOptionsModelImpl.TARGET_COMPATIBILITY;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelEffectDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.ModelPropertyDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.android.tools.idea.gradle.dsl.parser.semantics.SurfaceSyntaxDescription;
import com.google.common.collect.ImmutableMap;
import com.intellij.psi.PsiElement;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the data in addition to the project element, which added by Java plugin
 */
public class JavaDslElement extends BaseCompileOptionsDslElement {
  public static final PropertiesElementDescription<JavaDslElement> JAVA =
    new PropertiesElementDescription<>("java", JavaDslElement.class, JavaDslElement::new);

  // The Java Dsl element has a different mapping of external names to functionality than the BaseCompileOptionsDslElement, even though
  // the corresponding models are identical.  This suggests that JavaDslElement should probably not in fact be a
  // BaseCompileOptionsDslElement, or else that our modelling is wrong.
  //
  // It is also a bit odd in that in Groovy the java block need not be explicitly present -- sourceCompatibility and targetCompatibility
  // properties set at top-level are treated as altering the java block properties.  (I think).
  public static final ImmutableMap<SurfaceSyntaxDescription, ModelEffectDescription> ktsToModelNameMap = Stream.of(new Object[][]{
    {"sourceCompatibility", property, SOURCE_COMPATIBILITY, VAR},
    {"targetCompatibility", property, TARGET_COMPATIBILITY, VAR}
  }).collect(toModelMap());

  public static final ImmutableMap<SurfaceSyntaxDescription, ModelEffectDescription> groovyToModelNameMap = Stream.of(new Object[][]{
    // some versions of Gradle support setting these properties in Groovy through the standard setter method at top level.  We handle
    // that manually in addParsedElement/setParsedElement in order not to write out syntax that the project does not
    // understand.
    //
    // TODO(xof): a version-sensitive mechanism would improve this considerably.
    {"sourceCompatibility", property, SOURCE_COMPATIBILITY, VAR},
    {"targetCompatibility", property, TARGET_COMPATIBILITY, VAR}
  }).collect(toModelMap());

  @Override
  public @NotNull ImmutableMap<SurfaceSyntaxDescription, ModelEffectDescription> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    if (converter instanceof KotlinDslNameConverter) {
      return ktsToModelNameMap;
    }
    else if (converter instanceof GroovyDslNameConverter) {
      return groovyToModelNameMap;
    }
    else {
      return super.getExternalToModelMap(converter);
    }
  }

  public JavaDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  @Override
  @Nullable
  public PsiElement create() {
    GradleDslNameConverter converter = getDslFile().getWriter();
    if (converter instanceof KotlinDslNameConverter) {
      return super.create();
    }
    else if (converter instanceof GroovyDslNameConverter) {
      if (myParent == null) {
        return null;
      }
      else {
        return myParent.create();
      }
    }
    else {
      return super.create();
    }
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    // The java { ... } block, though not the top-level where this is reused, supports the normal setter methods in Groovy.  We
    // can't add those to the model description, as otherwise when writing we will write out invalid top-level configuration;
    // we therefore handle parsing of non-toplevel application statements by hand, here.
    if (element instanceof GradleDslLiteral && element.getDslFile().getParser() instanceof GroovyDslNameConverter) {
      String name = element.getName();
      if (name.equals("sourceCompatibility") || name.equals("targetCompatibility")) {
        ModelEffectDescription effect = null;
        if (name.equals("sourceCompatibility")) {
          effect = new ModelEffectDescription(new ModelPropertyDescription(SOURCE_COMPATIBILITY), SET);
        }
        else {
          effect = new ModelEffectDescription(new ModelPropertyDescription(TARGET_COMPATIBILITY), SET);
        }
        element.setModelEffect(effect);
      }
    }
    super.addParsedElement(element);
  }

  @Override
  public void setPsiElement(@Nullable PsiElement psiElement) {
    GradleDslNameConverter converter = getDslFile().getWriter();
    if (converter instanceof KotlinDslNameConverter) {
      super.setPsiElement(psiElement);
    }
    else if (converter instanceof GroovyDslNameConverter) {
      // do nothing
    }
    else {
      super.setPsiElement(psiElement);
    }
  }
}
