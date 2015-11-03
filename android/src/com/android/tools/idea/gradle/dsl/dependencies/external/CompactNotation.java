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
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import static com.android.tools.idea.gradle.dsl.parser.PsiElements.getUnquotedText;
import static com.android.tools.idea.gradle.dsl.parser.PsiElements.setLiteralText;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

final class CompactNotation extends ExternalDependency {
  @NotNull private final GrLiteral myValueLiteral;

  @Nullable
  static CompactNotation parse(@NotNull Dependencies parent,
                               @NotNull GrMethodCall methodCall,
                               @NotNull String configurationName,
                               @NotNull GrLiteral valueLiteral) {
    String notation = getUnquotedText(valueLiteral);
    if (isNotEmpty(notation)) {
      ExternalDependencySpec spec = ExternalDependencySpec.create(notation);
      if (spec != null) {
        return new CompactNotation(parent, methodCall, configurationName, spec, valueLiteral);
      }
    }
    return null;
  }

  private CompactNotation(@NotNull Dependencies parent,
                          @NotNull GrMethodCall methodCall,
                          @NotNull String configurationName,
                          @NotNull ExternalDependencySpec spec,
                          @NotNull GrLiteral valueLiteral) {
    super(parent, methodCall, configurationName, spec);
    myValueLiteral = valueLiteral;
  }

  @Override
  protected void applyVersion(@NotNull String newVersion) {
    mySpec.version = newVersion;
    setLiteralText(myValueLiteral, mySpec.toString());
  }

  @Override
  protected void removeFromParent() {
    GrClosableBlock closureBlock = getParent().getClosureBlock();
    assert closureBlock != null;

    if (!removeArgumentIfMoreThanOne(myValueLiteral)) {
      closureBlock.removeElements(new PsiElement[]{getMethodCall()});
    }
  }
}
