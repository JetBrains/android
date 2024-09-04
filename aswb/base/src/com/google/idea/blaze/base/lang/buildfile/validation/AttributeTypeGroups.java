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
package com.google.idea.blaze.base.lang.buildfile.validation;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Attribute.Discriminator;
import java.util.Set;

/**
 * Groups of attribute types of the BUILD language. The groups are not mutually exclusive; use
 * {@link #uniqueTypesOfGroup} to get elements that are unique to the given group.
 */
public final class AttributeTypeGroups {
  private AttributeTypeGroups() {}

  public static final ImmutableSet<Build.Attribute.Discriminator> LIST_TYPES =
      Sets.immutableEnumSet(
          Discriminator.STRING_LIST,
          Discriminator.DISTRIBUTION_SET,
          Discriminator.LABEL_LIST,
          Discriminator.OUTPUT_LIST,
          Discriminator.INTEGER_LIST,
          Discriminator.LICENSE,
          Discriminator.SELECTOR_LIST);

  public static final ImmutableSet<Build.Attribute.Discriminator> DICT_TYPES =
      Sets.immutableEnumSet(
          Discriminator.LABEL_LIST_DICT,
          Discriminator.LABEL_KEYED_STRING_DICT,
          Discriminator.STRING_DICT,
          Discriminator.STRING_LIST_DICT,
          Discriminator.LABEL_DICT_UNARY);

  public static final ImmutableSet<Build.Attribute.Discriminator> STRING_TYPES =
      Sets.immutableEnumSet(
          Discriminator.STRING,
          Discriminator.LABEL,
          Discriminator.OUTPUT,
          Discriminator.BOOLEAN,
          Discriminator.TRISTATE);

  public static final ImmutableSet<Build.Attribute.Discriminator> INTEGER_TYPES =
      Sets.immutableEnumSet(Discriminator.INTEGER, Discriminator.BOOLEAN, Discriminator.TRISTATE);

  /** Filters out the types that appear in more than one group. */
  public static Set<Discriminator> uniqueTypesOfGroup(Set<Discriminator> group) {
    return Sets.filter(
        group,
        t ->
            LIST_TYPES.contains(t)
                ^ DICT_TYPES.contains(t)
                ^ STRING_TYPES.contains(t)
                ^ INTEGER_TYPES.contains(t));
  }
}
