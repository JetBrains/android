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
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.groovy.GroovyDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.semantics.SemanticsDescription;
import com.google.common.collect.ImmutableMap;
import com.intellij.psi.PsiElement;
import java.util.stream.Stream;
import kotlin.Pair;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the data in addition to the project element, which added by Java plugin
 */
public class JavaDslElement extends BaseCompileOptionsDslElement {
  @NonNls public static final String JAVA_BLOCK_NAME = "java";

  // Even though the JavaDslElement -- which is really not a DslElement at all: it has no physical existence in a Dsl file -- has the same
  // properties as its superclass (hence the inheritance), its behaviour is different in that the accessors at this level are different
  // between an explicit compileOptions block and at top level.  In Groovy; there is no one-argument setter method with the same name;
  // the property syntax is used and supported.  In KotlinScript these properties appear not to be supported at all.
  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, Pair<String, SemanticsDescription>> ktsToModelNameMap = Stream.of(new Object[][]{
  }).collect(toImmutableMap(data -> new Pair<>((String) data[0], (Integer) data[1]),
                            data -> new Pair<>((String) data[2], (SemanticsDescription) data[3])));

  @NotNull
  public static final ImmutableMap<Pair<String,Integer>, Pair<String,SemanticsDescription>> groovyToModelNameMap = Stream.of(new Object[][]{
    {"sourceCompatibility", property, SOURCE_COMPATIBILITY, VAR},
    {"targetCompatibility", property, TARGET_COMPATIBILITY, VAR}
  }).collect(toImmutableMap(data -> new Pair<>((String) data[0], (Integer) data[1]),
                            data -> new Pair<>((String) data[2], (SemanticsDescription) data[3])));

  @Override
  @NotNull
  public ImmutableMap<Pair<String,Integer>, Pair<String,SemanticsDescription>> getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
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

  public JavaDslElement(@NotNull GradleDslElement parent) {
    super(parent, GradleNameElement.create(JAVA_BLOCK_NAME));
  }

  @Override
  @Nullable
  public PsiElement getPsiElement() {
    return null; // This class just act as an intermediate class for java properties and doesn't represent any real element on the file.
  }

  @Override
  @Nullable
  public PsiElement create() {
    return myParent == null ? null : myParent.create();
  }

  @Override
  public void setPsiElement(@Nullable PsiElement psiElement) {
  }
}
