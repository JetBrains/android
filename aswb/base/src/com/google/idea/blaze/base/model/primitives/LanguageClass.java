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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import javax.annotation.Nullable;

/** Language classes. */
public enum LanguageClass {
  GENERIC("generic", ImmutableSet.of()),
  C("c", ImmutableSet.of("c", "cc", "cpp", "h", "hh", "hpp")),
  JAVA("java", ImmutableSet.of("java")),
  ANDROID("android", ImmutableSet.of("aidl")),
  JAVASCRIPT("javascript", ImmutableSet.of("js", "applejs")),
  TYPESCRIPT("typescript", ImmutableSet.of("ts", "ats")),
  DART("dart", ImmutableSet.of("dart")),
  GO("go", ImmutableSet.of("go")),
  PYTHON("python", ImmutableSet.of("py", "pyw")),
  SCALA("scala", ImmutableSet.of("scala")),
  KOTLIN("kotlin", ImmutableSet.of("kt")),
  ;

  private static final ImmutableMap<String, LanguageClass> RECOGNIZED_EXTENSIONS =
      extensionToClassMap();

  private static ImmutableMap<String, LanguageClass> extensionToClassMap() {
    ImmutableMap.Builder<String, LanguageClass> result = ImmutableMap.builder();
    for (LanguageClass lang : LanguageClass.values()) {
      for (String ext : lang.recognizedFilenameExtensions) {
        result.put(ext, lang);
      }
    }
    return result.build();
  }

  private final String name;
  private final ImmutableSet<String> recognizedFilenameExtensions;

  LanguageClass(String name, ImmutableSet<String> recognizedFilenameExtensions) {
    this.name = name;
    this.recognizedFilenameExtensions = recognizedFilenameExtensions;
  }

  public String getName() {
    return name;
  }

  public static LanguageClass fromString(String name) {
    for (LanguageClass ruleClass : LanguageClass.values()) {
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

  /** Returns the LanguageClass associated with the given filename extension, if it's recognized. */
  @Nullable
  public static LanguageClass fromExtension(String filenameExtension) {
    return RECOGNIZED_EXTENSIONS.get(filenameExtension);
  }
}
