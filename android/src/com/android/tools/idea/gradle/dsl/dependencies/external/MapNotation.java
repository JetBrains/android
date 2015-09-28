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
import com.android.tools.idea.gradle.dsl.dependencies.ExternalDependencySpec;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.Map;

import static com.android.tools.idea.gradle.dsl.parser.PsiElements.getUnquotedText;
import static com.android.tools.idea.gradle.dsl.parser.PsiElements.setLiteralText;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.psi.util.PsiTreeUtil.getChildOfType;

final class MapNotation extends ExternalDependency {
  @NonNls private static final String VERSION_PROPERTY = "version";

  @NotNull private final Map<String, GrLiteral> myValueLiteralsByName;

  @Nullable private final GrListOrMap myListOrMap;

  @Nullable
  static MapNotation parse(@NotNull Dependencies parent,
                           @NotNull GrMethodCall methodCall,
                           @NotNull String configurationName,
                           @NotNull GrNamedArgument[] namedArguments,
                           @Nullable GrListOrMap listOrMap) {
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
    ExternalDependencySpec spec = parse(valuesByName);
    if (spec != null) {
      return new MapNotation(parent, methodCall, configurationName, spec, valueLiteralsByName, listOrMap);
    }
    return null;

  }

  @VisibleForTesting
  @Nullable
  static ExternalDependencySpec parse(@NotNull Map<String, String> valuesByName) {
    String name = valuesByName.get("name");
    if (isNotEmpty(name)) {
      return new ExternalDependencySpec(name, valuesByName.get("group"), valuesByName.get(VERSION_PROPERTY), valuesByName.get("classifier"),
                      valuesByName.get("ext"));
    }
    return null;
  }

  private MapNotation(@NotNull Dependencies parent,
                      @NotNull GrMethodCall methodCall,
                      @NotNull String configurationName,
                      @NotNull ExternalDependencySpec spec,
                      @NotNull Map<String, GrLiteral> valueLiteralsByName,
                      @Nullable GrListOrMap listOrMap) {
    super(parent, methodCall, configurationName, spec);
    myValueLiteralsByName = valueLiteralsByName;
    myListOrMap = listOrMap;
  }

  @Override
  protected void applyVersion(@NotNull String newVersion) {
    GrLiteral literal = myValueLiteralsByName.get(VERSION_PROPERTY);
    mySpec.version = newVersion;
    if (literal != null) {
      GrLiteral newLiteral = setLiteralText(literal, newVersion);
      myValueLiteralsByName.put(VERSION_PROPERTY, newLiteral);
    }
    // TODO handle case where 'version' property is not defined, and needs to be added.
  }

  @Override
  protected void removeFromParent() {
    GrClosableBlock closureBlock = getParent().getClosureBlock();
    assert closureBlock != null;

    if (myListOrMap != null && removeArgumentIfMoreThanOne(myListOrMap)) {
      return;
    }
    closureBlock.removeElements(new PsiElement[] {getMethodCall()});
  }
}
