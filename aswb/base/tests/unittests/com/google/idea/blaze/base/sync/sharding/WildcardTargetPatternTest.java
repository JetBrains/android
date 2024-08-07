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
package com.google.idea.blaze.base.sync.sharding;

import static com.google.common.truth.Truth.assertThat;

import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WildcardTargetPattern;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.google.idea.common.experiments.ExperimentService;
import com.google.idea.common.experiments.MockExperimentService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link WildcardTargetPattern}. */
@RunWith(JUnit4.class)
public class WildcardTargetPatternTest extends BlazeTestCase {

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    applicationServices.register(ExperimentService.class, new MockExperimentService());
  }

  @Test
  public void testRecursiveWildcardPattern() {
    TargetExpression target = TargetExpression.fromStringSafe("//java/com/google/...");
    WildcardTargetPattern wildcardPattern = WildcardTargetPattern.fromExpression(target);
    assertThat(wildcardPattern).isNotNull();
    assertThat(wildcardPattern.coversPackage(new WorkspacePath("java/com/google"))).isTrue();
    assertThat(wildcardPattern.coversPackage(new WorkspacePath("java/com/google/foo"))).isTrue();
    assertThat(wildcardPattern.isRecursive()).isTrue();
    assertThat(wildcardPattern.getBasePackage()).isEqualTo(new WorkspacePath("java/com/google"));
    assertThat(wildcardPattern.rulesOnly()).isTrue();
  }

  @Test
  public void testRecursiveWildcardPatternAlternativeFormat() {
    TargetExpression target = TargetExpression.fromStringSafe("//java/com/google/...:all");
    WildcardTargetPattern wildcardPattern = WildcardTargetPattern.fromExpression(target);
    assertThat(wildcardPattern).isNotNull();
    assertThat(wildcardPattern.coversPackage(new WorkspacePath("java/com/google"))).isTrue();
    assertThat(wildcardPattern.coversPackage(new WorkspacePath("java/com/google/foo"))).isTrue();
    assertThat(wildcardPattern.isRecursive()).isTrue();
    assertThat(wildcardPattern.getBasePackage()).isEqualTo(new WorkspacePath("java/com/google"));
    assertThat(wildcardPattern.rulesOnly()).isTrue();
  }

  @Test
  public void testRecursiveWildcardPatternAllTargets() {
    TargetExpression target = TargetExpression.fromStringSafe("//java/com/google/...:all-targets");
    WildcardTargetPattern wildcardPattern = WildcardTargetPattern.fromExpression(target);
    assertThat(wildcardPattern).isNotNull();
    assertThat(wildcardPattern.coversPackage(new WorkspacePath("java/com/google"))).isTrue();
    assertThat(wildcardPattern.coversPackage(new WorkspacePath("java/com/google/foo"))).isTrue();
    assertThat(wildcardPattern.isRecursive()).isTrue();
    assertThat(wildcardPattern.getBasePackage()).isEqualTo(new WorkspacePath("java/com/google"));
    assertThat(wildcardPattern.rulesOnly()).isFalse();
  }

  @Test
  public void testRecursiveWildcardPatternAllTargetsAlternativeFormat() {
    TargetExpression target = TargetExpression.fromStringSafe("//java/com/google/...:*");
    WildcardTargetPattern wildcardPattern = WildcardTargetPattern.fromExpression(target);
    assertThat(wildcardPattern).isNotNull();
    assertThat(wildcardPattern.coversPackage(new WorkspacePath("java/com/google"))).isTrue();
    assertThat(wildcardPattern.coversPackage(new WorkspacePath("java/com/google/foo"))).isTrue();
    assertThat(wildcardPattern.isRecursive()).isTrue();
    assertThat(wildcardPattern.getBasePackage()).isEqualTo(new WorkspacePath("java/com/google"));
    assertThat(wildcardPattern.rulesOnly()).isFalse();
  }

  @Test
  public void testNonRecursiveAllTargetsWildcardPattern() {
    TargetExpression target = TargetExpression.fromStringSafe("//java/com/google:*");
    WildcardTargetPattern wildcardPattern = WildcardTargetPattern.fromExpression(target);
    assertThat(wildcardPattern).isNotNull();
    assertThat(wildcardPattern.coversPackage(new WorkspacePath("java/com/google"))).isTrue();
    assertThat(wildcardPattern.coversPackage(new WorkspacePath("java/com/google/foo"))).isFalse();
    assertThat(wildcardPattern.isRecursive()).isFalse();
    assertThat(wildcardPattern.getBasePackage()).isEqualTo(new WorkspacePath("java/com/google"));
    assertThat(wildcardPattern.rulesOnly()).isFalse();
  }

  @Test
  public void testNonRecursiveWildcardPattern() {
    TargetExpression target = TargetExpression.fromStringSafe("//java/com/google:all");
    WildcardTargetPattern wildcardPattern = WildcardTargetPattern.fromExpression(target);
    assertThat(wildcardPattern).isNotNull();
    assertThat(wildcardPattern.coversPackage(new WorkspacePath("java/com/google"))).isTrue();
    assertThat(wildcardPattern.coversPackage(new WorkspacePath("java/com/google/foo"))).isFalse();
    assertThat(wildcardPattern.isRecursive()).isFalse();
    assertThat(wildcardPattern.getBasePackage()).isEqualTo(new WorkspacePath("java/com/google"));
    assertThat(wildcardPattern.rulesOnly()).isTrue();
  }

  @Test
  public void testNonWildcardPattern() {
    TargetExpression target = TargetExpression.fromStringSafe("//java/com/google:single_target");
    WildcardTargetPattern wildcardPattern = WildcardTargetPattern.fromExpression(target);
    assertThat(wildcardPattern).isNull();
  }

  @Test
  public void testNonWildcardImplicitTargetName() {
    TargetExpression target = TargetExpression.fromStringSafe("//java/com/google/foo");
    WildcardTargetPattern wildcardPattern = WildcardTargetPattern.fromExpression(target);
    assertThat(wildcardPattern).isNull();
  }

  @Test
  public void testStripWildcardSuffix() {
    // wildcard target patterns
    assertThat(WildcardTargetPattern.stripWildcardSuffix("//package_path:all"))
        .isEqualTo("//package_path");
    assertThat(WildcardTargetPattern.stripWildcardSuffix(":all")).isEmpty();
    assertThat(WildcardTargetPattern.stripWildcardSuffix("//foo:*")).isEqualTo("//foo");
    assertThat(WildcardTargetPattern.stripWildcardSuffix("//foo/...:all-targets"))
        .isEqualTo("//foo");
    assertThat(WildcardTargetPattern.stripWildcardSuffix("//foo/...")).isEqualTo("//foo");

    // non-wildcard target patterns
    assertThat(WildcardTargetPattern.stripWildcardSuffix("//package_path:target"))
        .isEqualTo("//package_path:target");
    assertThat(WildcardTargetPattern.stripWildcardSuffix("//package_path:.all"))
        .isEqualTo("//package_path:.all");
    assertThat(WildcardTargetPattern.stripWildcardSuffix("foo/bar")).isEqualTo("foo/bar");
  }
}
