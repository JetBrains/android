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
package com.google.idea.blaze.base.query;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link LineProcessingOutputStream.LineProcessor} which collects the blaze targets output by
 * 'blaze query --output=label_kind "targets"'
 */
public class BlazeQueryLabelKindParser implements LineProcessingOutputStream.LineProcessor {

  /** A rule name and target label output by 'blaze query'. */
  public static class RuleTypeAndLabel {
    public final String ruleType;
    public final String label;

    private RuleTypeAndLabel(String ruleType, String label) {
      this.ruleType = ruleType;
      this.label = label;
    }
  }

  private static final Pattern RULE_PATTERN = Pattern.compile("^([^\\s]*) rule ([^\\s]*)$");

  private final ImmutableList.Builder<TargetInfo> outputList;
  private final Predicate<RuleTypeAndLabel> targetFilter;

  /** @param targetFilter Ignore targets failing this predicate. */
  public BlazeQueryLabelKindParser(Predicate<RuleTypeAndLabel> targetFilter) {
    this.outputList = ImmutableList.builder();
    this.targetFilter = targetFilter;
  }

  @Override
  public boolean processLine(String line) {
    Matcher match = RULE_PATTERN.matcher(line);
    if (!match.find()) {
      return true;
    }
    String ruleType = match.group(1);
    String labelString = match.group(2);
    if (targetFilter.test(new RuleTypeAndLabel(ruleType, labelString))) {
      Label label = Label.createIfValid(labelString);
      if (label != null) {
        outputList.add(TargetInfo.builder(label, ruleType).build());
      }
    }
    return true;
  }

  /** Returns all targets parsed to this point. */
  public ImmutableList<TargetInfo> getTargets() {
    return outputList.build();
  }

  public ImmutableList<TargetExpression> getTargetLabels() {
    return outputList.build().stream().map(info -> info.label).collect(toImmutableList());
  }
}
