/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.lang.buildfile.completion;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.FuncallExpression;
import com.google.idea.blaze.base.lang.buildfile.references.LabelUtils;
import com.google.idea.blaze.base.lang.buildfile.references.QuoteType;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.swing.Icon;

/**
 * Given a label fragment containing a (possibly implicit) package path, provides a lookup element
 * to a rule target in that package.
 */
public class LabelRuleLookupElement extends BuildLookupElement {

  public static BuildLookupElement[] collectAllRules(
      BuildFile file,
      String originalString,
      String packagePrefix,
      @Nullable String excluded,
      QuoteType quoteType) {
    if (packagePrefix.startsWith("//") || originalString.startsWith(":")) {
      packagePrefix += ":";
    }

    String ruleFragment = LabelUtils.getRuleComponent(originalString);
    List<BuildLookupElement> lookups = Lists.newArrayList();
    for (FuncallExpression target : file.findChildrenByClass(FuncallExpression.class)) {
      String targetName = target.getName();
      if (targetName == null
          || Objects.equals(target.getName(), excluded)
          || !targetName.startsWith(ruleFragment)) {
        continue;
      }
      String ruleType = target.getFunctionName();
      if (ruleType == null) {
        continue;
      }
      lookups.add(
          new LabelRuleLookupElement(packagePrefix, target, targetName, ruleType, quoteType));
    }
    return lookups.isEmpty()
        ? BuildLookupElement.EMPTY_ARRAY
        : lookups.toArray(new BuildLookupElement[lookups.size()]);
  }

  private final FuncallExpression target;
  private final String targetName;
  private final String ruleType;

  private LabelRuleLookupElement(
      String packagePrefix,
      FuncallExpression target,
      String targetName,
      String ruleType,
      QuoteType quoteType) {
    super(packagePrefix + targetName, quoteType);
    this.target = target;
    this.targetName = targetName;
    this.ruleType = ruleType;

    assert (packagePrefix.isEmpty() || packagePrefix.endsWith(":"));
  }

  @Override
  public Icon getIcon() {
    return target.getIcon(0);
  }

  @Override
  protected String getTypeText() {
    return ruleType;
  }

  @Override
  protected String getItemText() {
    return targetName;
  }
}
