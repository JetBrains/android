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
package com.google.idea.blaze.base.lang.buildfile.language.semantics;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import javax.annotation.Nullable;

/** Simple implementation of AttributeDefinition, from build.proto */
public class AttributeDefinition
    implements ProtoWrapper<Build.AttributeDefinition>, Comparable<AttributeDefinition> {

  private final String name;
  private final Build.Attribute.Discriminator type;
  private final boolean mandatory;
  @Nullable private final String documentation;

  // the names of rules allowed in this attribute, or null if all rules are allowed.
  @Nullable private final ImmutableList<String> allowedRuleClasses;

  @VisibleForTesting
  public AttributeDefinition(
      String name,
      Build.Attribute.Discriminator type,
      boolean mandatory,
      @Nullable String documentation,
      @Nullable ImmutableList<String> allowedRuleClasses) {
    this.name = name;
    this.type = type;
    this.mandatory = mandatory;
    this.documentation = documentation;
    this.allowedRuleClasses = allowedRuleClasses;
  }

  static AttributeDefinition fromProto(Build.AttributeDefinition proto) {
    return new AttributeDefinition(
        proto.getName(),
        proto.getType(),
        proto.getMandatory(),
        proto.hasDocumentation() ? proto.getDocumentation() : null,
        proto.hasAllowedRuleClasses()
            ? ImmutableList.copyOf(proto.getAllowedRuleClasses().getAllowedRuleClassList())
            : null);
  }

  @Override
  public Build.AttributeDefinition toProto() {
    Build.AttributeDefinition.Builder builder =
        Build.AttributeDefinition.newBuilder().setName(name).setType(type).setMandatory(mandatory);
    ProtoWrapper.setIfNotNull(builder::setDocumentation, documentation);
    if (allowedRuleClasses != null) {
      builder.setAllowedRuleClasses(
          Build.AllowedRuleClassInfo.newBuilder()
              .addAllAllowedRuleClass(allowedRuleClasses)
              .setPolicy(
                  Build.AllowedRuleClassInfo.AllowedRuleClasses.ANY)); // unnecessary, but mandatory
    }
    return builder.build();
  }

  public String getName() {
    return name;
  }

  public Build.Attribute.Discriminator getType() {
    return type;
  }

  public boolean isMandatory() {
    return mandatory;
  }

  @Nullable
  public String getDocumentation() {
    return documentation;
  }

  /**
   * Only relevant for attributes of type LABEL and LABEL_LIST. Some such attributes can only
   * contain certain rule types.
   */
  public boolean isRuleTypeAllowed(RuleDefinition rule) {
    return allowedRuleClasses == null || allowedRuleClasses.contains(rule.getName());
  }

  @Override
  public int compareTo(AttributeDefinition other) {
    if (isMandatory() != other.isMandatory()) {
      return isMandatory() ? -1 : 1;
    }
    // 'name' always goes first if present
    if (getName().equals("name")) {
      return other.getName().equals("name") ? 0 : -1;
    }
    if (other.getName().equals("name")) {
      return 1;
    }

    return getName().compareTo(other.getName());
  }
}
