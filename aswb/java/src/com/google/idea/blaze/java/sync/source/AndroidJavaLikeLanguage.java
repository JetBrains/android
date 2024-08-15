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
package com.google.idea.blaze.java.sync.source;

import com.google.common.collect.ImmutableSet;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.java.AndroidBlazeRules;

class AndroidJavaLikeLanguage implements JavaLikeLanguage {

  @Override
  public ImmutableSet<String> getFileExtensions() {
    return ImmutableSet.of(".java");
  }

  @Override
  public ImmutableSet<Kind> getDebuggableKinds() {
    return ImmutableSet.of(
        AndroidBlazeRules.RuleTypes.ANDROID_ROBOLECTRIC_TEST.getKind(),
        AndroidBlazeRules.RuleTypes.ANDROID_LOCAL_TEST.getKind(),
        AndroidBlazeRules.RuleTypes.KT_ANDROID_LOCAL_TEST.getKind());
  }

  @Override
  public ImmutableSet<Kind> getHandledTestKinds() {
    return ImmutableSet.of(
        AndroidBlazeRules.RuleTypes.ANDROID_ROBOLECTRIC_TEST.getKind(),
        AndroidBlazeRules.RuleTypes.ANDROID_LOCAL_TEST.getKind(),
        AndroidBlazeRules.RuleTypes.KT_ANDROID_LOCAL_TEST.getKind());
  }

  @Override
  public ImmutableSet<Kind> getNonSourceKinds() {
    return ImmutableSet.of(AndroidBlazeRules.RuleTypes.AAR_IMPORT.getKind());
  }
}
