/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.common;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

/** Utility class for information about specific bazel rule kinds (java, cpp, etc.) */
public final class RuleKinds {
  private RuleKinds() {}

  /** Java rule kinds */
  public static final ImmutableSet<String> JAVA_RULE_KINDS =
      ImmutableSet.of(
          "java_library",
          "java_binary",
          "kt_jvm_library",
          "kt_jvm_binary",
          "kt_jvm_library_helper",
          "java_test",
          "java_proto_library",
          "java_lite_proto_library",
          "java_mutable_proto_library",
          "_java_grpc_library",
          "_kotlin_library",
          "_java_lite_grpc_library",
          "_iml_module_");

  /** Android rule kinds */
  public static final ImmutableSet<String> ANDROID_RULE_KINDS =
      ImmutableSet.of(
          "android_library",
          "android_binary",
          "android_local_test",
          "android_instrumentation_test",
          "kt_android_library_helper");

  /** C++ rule kinds */
  public static final ImmutableSet<String> CC_RULE_KINDS =
      ImmutableSet.of("cc_library", "cc_binary", "cc_shared_library", "cc_test");

  /** Rule kinds that have proto files for sources. */
  public static final ImmutableSet<String> PROTO_SOURCE_RULE_KINDS =
      ImmutableSet.of("proto_library");

  public static boolean isJava(String ruleClass) {
    return JAVA_RULE_KINDS.contains(ruleClass) || ANDROID_RULE_KINDS.contains(ruleClass);
  }

  public static boolean isAndroid(String ruleClass) {
    return ANDROID_RULE_KINDS.contains(ruleClass);
  }

  public static boolean isCc(String ruleClass) {
    return CC_RULE_KINDS.contains(ruleClass);
  }

  public static boolean isProtoSource(String ruleClass) {
    return PROTO_SOURCE_RULE_KINDS.contains(ruleClass);
  }
}
