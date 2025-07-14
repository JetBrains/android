// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.dsl.parser.java;

import static com.android.tools.idea.gradle.dsl.model.java.JavaDeclarativeModelImpl.JAVA_VERSION;
import static com.android.tools.idea.gradle.dsl.model.java.JavaDeclarativeModelImpl.MAIN_CLASS;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;
import static com.google.common.collect.ImmutableMap.toImmutableMap;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.google.common.collect.ImmutableMap;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class JavaDclElement extends GradleDslBlockElement {
  public static final PropertiesElementDescription<JavaDclElement> JAVA_APPLICATION =
    new PropertiesElementDescription<>("javaApplication", JavaDclElement.class, JavaDclElement::new);
  public static final PropertiesElementDescription<JavaDclElement> JAVA_LIBRARY =
    new PropertiesElementDescription<>("javaLibrary", JavaDclElement.class, JavaDclElement::new);

  public static final ImmutableMap<String,PropertiesElementDescription<?>> CHILD_PROPERTIES_ELEMENTS_MAP = Stream.of(new Object[][]{
    {"dependencies", DependenciesDslElement.DEPENDENCIES},
    {"testing", TestingDclElement.TESTING}
  }).collect(toImmutableMap(data -> (String) data[0], data -> (PropertiesElementDescription) data[1]));

  public static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
    {"javaVersion", property, JAVA_VERSION, VAR},
    {"mainClass", property, MAIN_CLASS, VAR},
  }).collect(toModelMap());

  @Override
  public @NotNull ImmutableMap<String, PropertiesElementDescription<?>> getChildPropertiesElementsDescriptionMap(GradleDslNameConverter.Kind kind) {
    return CHILD_PROPERTIES_ELEMENTS_MAP;
  }

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return declarativeToModelNameMap;
  }

  public JavaDclElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }
}
