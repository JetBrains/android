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
package com.android.tools.idea.gradle.dsl.dependencies.external;

import com.android.tools.idea.gradle.dsl.dependencies.Dependencies;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.Map;

import static com.android.tools.idea.gradle.dsl.parser.PsiElements.getUnquotedText;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.psi.util.PsiTreeUtil.getChildOfType;

public class MapNotation extends ExternalDependency {
  @NonNls private static final String VERSION_PROPERTY = "version";

  @NotNull private final Map<String, GrLiteral> myValueLiteralsByName;

  @Nullable
  static MapNotation parse(@NotNull Dependencies parent, @NotNull String configurationName, @NotNull GrNamedArgument[] namedArguments) {
    Map<String, GrLiteral> valueLiteralsByName = Maps.newHashMap();
    Map<String, String> valuesByName = Maps.newHashMap();
    for (GrNamedArgument argument : namedArguments) {
      GrLiteral literal = getChildOfType(argument, GrLiteral.class);
      if (literal != null) {
        String name = argument.getLabelName();
        valueLiteralsByName.put(name, literal);
        valuesByName.put(name, getUnquotedText(literal));
      }
    }
    Spec spec = parse(valuesByName);
    if (spec != null) {
      return new MapNotation(parent, configurationName, spec, valueLiteralsByName);
    }
    return null;

  }

  @VisibleForTesting
  @Nullable
  static Spec parse(@NotNull Map<String, String> valuesByName) {
    String name = valuesByName.get("name");
    if (isNotEmpty(name)) {
      return new Spec(name, valuesByName.get("group"), valuesByName.get(VERSION_PROPERTY), valuesByName.get("classifier"),
                      valuesByName.get("ext"));
    }
    return null;
  }

  protected MapNotation(@NotNull Dependencies parent,
                        @NotNull String configurationName,
                        @NotNull Spec spec,
                        @NotNull Map<String, GrLiteral> valueLiteralsByName) {
    super(parent, configurationName, spec);
    myValueLiteralsByName = valueLiteralsByName;
  }

  @Override
  protected void applyVersion(@NotNull String newVersion) {
    GrLiteral literal = myValueLiteralsByName.get(VERSION_PROPERTY);
    if (literal != null) {
      Project project = literal.getProject();
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

      mySpec.version = newVersion;
      GrLiteral newCoordinatePsiLiteral = factory.createLiteralFromValue(newVersion);

      literal.replace(newCoordinatePsiLiteral);
    }
    // TODO handle case where 'version' property is not defined, and needs to be added.
  }
}
