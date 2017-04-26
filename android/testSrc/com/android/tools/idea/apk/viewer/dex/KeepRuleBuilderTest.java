/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.apk.viewer.dex;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class KeepRuleBuilderTest {

  @Test
  public void generateRule_class_nofqn() {
    KeepRuleBuilder builder = new KeepRuleBuilder();
    builder.setPackage("");
    builder.setClass("MyClass");
    builder.setMember(KeepRuleBuilder.ANY_MEMBER);
    assertThat(builder.build(KeepRuleBuilder.KeepType.KEEP))
      .isEqualTo("-keep class MyClass { *; }");
  }

  @Test
  public void generateRule_package() {
    KeepRuleBuilder builder = new KeepRuleBuilder();
    builder.setPackage("my.package");
    assertThat(builder.build(KeepRuleBuilder.KeepType.KEEP))
      .isEqualTo("-keep class my.package.** { *; }");
  }

  @Test
  public void generateRule_class() {
    KeepRuleBuilder builder = new KeepRuleBuilder();
    builder.setPackage("my.package");
    builder.setClass("MyClass");
    builder.setMember(KeepRuleBuilder.ANY_MEMBER);
    assertThat(builder.build(KeepRuleBuilder.KeepType.KEEP))
      .isEqualTo("-keep class my.package.MyClass { *; }");
  }

  @Test
  public void generateRule_member() {
    KeepRuleBuilder builder = new KeepRuleBuilder();
    builder.setPackage("my.package");
    builder.setClass("MyClass");
    builder.setMember("<init>(java.lang.String aa)");
    assertThat(builder.build(KeepRuleBuilder.KeepType.KEEP))
      .isEqualTo("-keep class my.package.MyClass { <init>(java.lang.String aa); }");
  }

  @Test
  public void generateRule_types() {
    KeepRuleBuilder builder = new KeepRuleBuilder();
    builder.setPackage("my.package");
    builder.setClass("MyClass");
    assertThat(builder.build(KeepRuleBuilder.KeepType.KEEP))
      .isEqualTo("-keep class my.package.MyClass { *; }");
    assertThat(builder.build(KeepRuleBuilder.KeepType.KEEPNAMES))
      .isEqualTo("-keepnames class my.package.MyClass { *; }");
    assertThat(builder.build(KeepRuleBuilder.KeepType.KEEPCLASSMEMBERNAMES))
      .isEqualTo("-keepclassmembernames class my.package.MyClass { *; }");
    assertThat(builder.build(KeepRuleBuilder.KeepType.KEEPCLASSMEMBERS))
      .isEqualTo("-keepclassmembers class my.package.MyClass { *; }");
  }

}
