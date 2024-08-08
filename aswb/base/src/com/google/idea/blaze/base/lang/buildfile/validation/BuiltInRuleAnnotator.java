/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.lang.buildfile.validation;

import com.google.common.base.Joiner;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.AttributeDefinition;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpec;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpecProvider;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.RuleDefinition;
import com.google.idea.blaze.base.lang.buildfile.psi.Argument;
import com.google.idea.blaze.base.lang.buildfile.psi.Expression;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.intellij.psi.PsiElement;
import java.util.Set;
import java.util.TreeSet;

/** Validation of known rule types. */
public class BuiltInRuleAnnotator extends BuildAnnotator {

  @Override
  public void visitFuncallExpression(FuncallExpression node) {
    BuildLanguageSpec spec =
        BuildLanguageSpecProvider.getInstance(node.getProject()).getLanguageSpec();
    if (spec == null) {
      return;
    }
    String ruleName = node.getFunctionName();
    RuleDefinition rule = spec.getRule(ruleName);
    if (rule == null) {
      return;
    }
    if (node.getReferencedElement() != null) {
      // this has been locally overridden, so don't attempt validation
      return;
    }
    Set<String> missingAttributes = new TreeSet<>(rule.getMandatoryAttributes().keySet());
    for (Argument arg : node.getArguments()) {
      if (arg instanceof Argument.StarStar) {
        missingAttributes.clear();
        continue;
      }
      String name = arg.getName();
      if (name == null) {
        continue;
      }
      AttributeDefinition attribute = rule.getAttribute(name);
      if (attribute == null) {
        markError(
            arg, String.format("Unrecognized attribute '%s' for rule type '%s'", name, ruleName));
        continue;
      }
      missingAttributes.remove(name);
      Expression argValue = arg.getValue();
      if (argValue == null) {
        continue;
      }
      PsiElement rootElement = PsiUtils.getReferencedTargetValue(argValue);
      if (!BuildElementValidation.possiblyValidType(rootElement, attribute.getType())) {
        markError(
            arg,
            String.format(
                "Invalid value for attribute '%s'. Expected a value of type '%s'",
                name, attribute.getType()));
      }
    }
    if (!missingAttributes.isEmpty()) {
      markError(
          node,
          String.format(
              "Target missing required attribute(s): %s", Joiner.on(',').join(missingAttributes)));
    }
  }
}
