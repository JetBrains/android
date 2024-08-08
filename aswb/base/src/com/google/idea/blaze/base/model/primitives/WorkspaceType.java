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
package com.google.idea.blaze.base.model.primitives;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;

/**
 * Workspace types.
 *
 * <p>If the user doesn't specify a workspace, she gets the supported workspace type with lowest
 * enum ordinal.
 */
public enum WorkspaceType implements ProtoWrapper<String> {
  ANDROID("android", LanguageClass.ANDROID, LanguageClass.JAVA),
  C("c", LanguageClass.C),
  JAVA("java", LanguageClass.JAVA),
  DART("dart", LanguageClass.DART),
  PYTHON("python", LanguageClass.PYTHON),
  JAVASCRIPT("javascript", LanguageClass.JAVASCRIPT),
  GO("go", LanguageClass.GO),
  INTELLIJ_PLUGIN("intellij_plugin", LanguageClass.JAVA),
  NONE("none", LanguageClass.GENERIC);

  private final String name;
  // the languages active by default for this WorkspaceType
  private final ImmutableSet<LanguageClass> languages;

  WorkspaceType(String name, LanguageClass... languages) {
    this.name = name;
    this.languages = ImmutableSet.copyOf(languages);
  }

  public String getName() {
    return name;
  }

  public ImmutableSet<LanguageClass> getLanguages() {
    return languages;
  }

  public static WorkspaceType fromString(String name) {
    for (WorkspaceType ruleClass : WorkspaceType.values()) {
      if (ruleClass.name.equals(name)) {
        return ruleClass;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public String toProto() {
    return name;
  }
}
