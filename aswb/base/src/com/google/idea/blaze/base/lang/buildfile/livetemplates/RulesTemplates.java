/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.livetemplates;

import static com.google.idea.blaze.base.lang.buildfile.validation.AttributeTypeGroups.DICT_TYPES;
import static com.google.idea.blaze.base.lang.buildfile.validation.AttributeTypeGroups.LIST_TYPES;
import static com.google.idea.blaze.base.lang.buildfile.validation.AttributeTypeGroups.STRING_TYPES;
import static com.google.idea.blaze.base.lang.buildfile.validation.AttributeTypeGroups.uniqueTypesOfGroup;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.AttributeDefinition;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.BuildLanguageSpec;
import com.google.idea.blaze.base.lang.buildfile.language.semantics.RuleDefinition;
import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import java.util.Optional;

/** Class that provides templates for some build rules. */
public final class RulesTemplates {

  private static final class Wrap {
    final String left;
    final String right;

    Wrap(String left, String right) {
      this.left = left;
      this.right = right;
    }

    static final Wrap EMPTY = new Wrap("", "");
    static final Wrap QUOTES = new Wrap("\"", "\"");
    static final Wrap SQUARE_BRACKETS = new Wrap("[", "]");
    static final Wrap CURLY_BRACKETS = new Wrap("{", "}");
  }

  private static final ImmutableMap<Build.Attribute.Discriminator, Wrap> TYPE_TO_WRAP;

  static {
    ImmutableMap.Builder<Build.Attribute.Discriminator, Wrap> mapping = ImmutableMap.builder();
    uniqueTypesOfGroup(LIST_TYPES).forEach(t -> mapping.put(t, Wrap.SQUARE_BRACKETS));
    uniqueTypesOfGroup(DICT_TYPES).forEach(t -> mapping.put(t, Wrap.CURLY_BRACKETS));
    uniqueTypesOfGroup(STRING_TYPES).forEach(t -> mapping.put(t, Wrap.QUOTES));
    TYPE_TO_WRAP = Maps.immutableEnumMap(mapping.build());
  }

  private static final BoolExperiment srcsDepsExperiment =
      new BoolExperiment("blaze.SrcsDepsTemplate", true);

  private static final ImmutableList<String> FREQUENT_ATTRIBUTES = ImmutableList.of("srcs", "deps");

  private RulesTemplates() {}

  /** Returns template for a given rule name if available. */
  public static Optional<Template> templateForRule(String ruleName, BuildLanguageSpec spec) {
    RuleDefinition ruleDef = spec.getRule(ruleName);
    if (ruleDef == null || ruleDef.getMandatoryAttributes().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(mandatoryAndFrequentArgumentsTemplate(ruleDef));
  }

  private static Template mandatoryAndFrequentArgumentsTemplate(RuleDefinition ruleDef) {
    TemplateImpl template = new TemplateImpl("", "");
    template.addTextSegment("(");
    ImmutableMap<String, AttributeDefinition> attributes = ruleDef.getAttributes();
    ImmutableMap<String, AttributeDefinition> requiredAttributes = ruleDef.getMandatoryAttributes();
    requiredAttributes.values().forEach(d -> addAttributeToTemplate(template, d));
    if (srcsDepsExperiment.getValue()) {
      FREQUENT_ATTRIBUTES.stream()
          .filter(a -> attributes.containsKey(a) && !requiredAttributes.containsKey(a))
          .map(attributes::get)
          .forEach(d -> addAttributeToTemplate(template, d));
    }
    template.addEndVariable();
    template.addTextSegment("\n)");
    return template;
  }

  private static void addAttributeToTemplate(Template template, AttributeDefinition attribute) {
    String name = attribute.getName();
    Wrap wrap = TYPE_TO_WRAP.getOrDefault(attribute.getType(), Wrap.EMPTY);
    template.addTextSegment("\n    " + name + " = " + wrap.left);
    addVariableToTemplate(template, name);
    template.addTextSegment(wrap.right + ",");
  }

  private static void addVariableToTemplate(Template template, String variableName) {
    template.addVariable(
        variableName,
        /* expression= */ null,
        /* defaultValueExpression= */ null,
        /* isAlwaysStopAt= */ true,
        /* skipOnStart= */ false);
  }
}
