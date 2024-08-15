/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.dart;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.google.idea.blaze.base.model.primitives.RuleType;

class DartBlazeRules implements Kind.Provider {

  @Override
  public ImmutableSet<Kind> getTargetKinds() {
    return ImmutableSet.of(
        Kind.Provider.create("dart_proto_library", LanguageClass.DART, RuleType.LIBRARY),
        Kind.Provider.create("_dart_library", LanguageClass.DART, RuleType.LIBRARY),
        Kind.Provider.create("dart_vm_test", LanguageClass.DART, RuleType.TEST),
        Kind.Provider.create("dart_web_test", LanguageClass.DART, RuleType.TEST));
  }
}
