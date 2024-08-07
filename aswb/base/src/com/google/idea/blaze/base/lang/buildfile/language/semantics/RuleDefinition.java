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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;

/** Simple implementation of RuleDefinition, from build.proto */
public class RuleDefinition implements ProtoWrapper<Build.RuleDefinition> {

  private final String name;
  private final ImmutableMap<String, AttributeDefinition> attributes;
  private final ImmutableMap<String, AttributeDefinition> mandatoryAttributes;
  @Nullable private final String documentation;

  @VisibleForTesting
  public RuleDefinition(
      String name,
      ImmutableMap<String, AttributeDefinition> attributes,
      @Nullable String documentation) {
    this.name = name;
    this.attributes = attributes;
    this.mandatoryAttributes =
        ImmutableMap.copyOf(Maps.filterValues(this.attributes, AttributeDefinition::isMandatory));
    this.documentation = documentation;
  }

  public static RuleDefinition fromProto(Build.RuleDefinition proto) {
    Map<String, AttributeDefinition> attributes = new LinkedHashMap<>();
    for (Build.AttributeDefinition attr : proto.getAttributeList()) {
      attributes.put(attr.getName(), AttributeDefinition.fromProto(attr));
    }
    AttributeDefinition nameDef = attributes.get("name");
    if (nameDef != null && !nameDef.isMandatory()) {
      // blaze isn't correctly marking the 'name' attribute as mandatory
      attributes.put(
          "name",
          AttributeDefinition.fromProto(nameDef.toProto().toBuilder().setMandatory(true).build()));
    }
    attributes =
        attributes.entrySet().stream()
            .sorted(Entry.comparingByValue())
            .collect(ImmutableMap.toImmutableMap(Entry::getKey, Entry::getValue));
    return new RuleDefinition(
        proto.getName(),
        ImmutableMap.copyOf(attributes),
        proto.hasDocumentation() ? proto.getDocumentation() : null);
  }

  @Override
  public Build.RuleDefinition toProto() {
    Build.RuleDefinition.Builder builder =
        Build.RuleDefinition.newBuilder()
            .setName(name)
            .addAllAttribute(ProtoWrapper.mapToProtos(attributes.values()));
    ProtoWrapper.setIfNotNull(builder::setDocumentation, documentation);
    return builder.build();
  }

  public String getName() {
    return name;
  }

  /** This map is not exhaustive; it only contains documented attributes. */
  public ImmutableMap<String, AttributeDefinition> getAttributes() {
    return attributes;
  }

  public ImmutableMap<String, AttributeDefinition> getMandatoryAttributes() {
    return mandatoryAttributes;
  }

  @Nullable
  public String getDocumentation() {
    return documentation;
  }

  public ImmutableSet<String> getKnownAttributeNames() {
    return getAttributes().keySet();
  }

  @Nullable
  public AttributeDefinition getAttribute(@Nullable String attributeName) {
    return attributeName != null ? getAttributes().get(attributeName) : null;
  }
}
