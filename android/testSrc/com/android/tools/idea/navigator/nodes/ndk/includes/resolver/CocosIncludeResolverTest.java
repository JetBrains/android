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
package com.android.tools.idea.navigator.nodes.ndk.includes.resolver;

import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageType;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.SimpleIncludeValue;
import org.junit.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class CocosIncludeResolverTest {

  // The main purpose of this test is to catch new package formats added to Cocos.
  // When these find their way to RealWorldExamples class then this test is supposed to notice
  // them and fire an assert so that the new package can be added and a test written.
  @Test
  public void exerciseRealWorldExamples() {
    for (ResolverTests.ResolutionResult resolved : ResolverTests.resolveAllRealWorldExamples(new CocosIncludeResolver())) {
      if (resolved.myResolution == null) {
        assertThat(resolved.myOriginalPath).doesNotContain("cocos");
      }
      else {
        assertThat(resolved.myOriginalPath).contains("cocos");
      }
    }
  }

  @Test
  public void testTreatExternalAsPlainFolder() {
    List<SimpleIncludeValue> resolutions = ResolverTests.resolvedIncludes(
      new CocosIncludeResolver(),
      "-I/usr/local/google/home/cocos2d/external");
    assertThat(resolutions).hasSize(1);
    SimpleIncludeValue resolution = resolutions.get(0);
    assertThat(resolution).isNotNull();
    assertThat(resolution.getPackageType()).isEqualTo(PackageType.IncludeFolder);
    assertThat(resolution.mySimplePackageName).isEqualTo("external");
    assertThat(resolution.myRelativeIncludeSubFolder).isEqualTo(
      "/");
    assertThat(resolution.getPackageFamilyBaseFolder().getPath()).isEqualTo(
      "/usr/local/google/home/cocos2d/external");
  }

  @Test
  public void testTreatEditorSupportAsPlainFolder() {
    List<SimpleIncludeValue> resolutions = ResolverTests.resolvedIncludes(
      new CocosIncludeResolver(),
      "-I/usr/local/google/home/cocos2d/cocos/editor-support");
    assertThat(resolutions).hasSize(1);
    SimpleIncludeValue resolution = resolutions.get(0);
    assertThat(resolution).isNotNull();
    assertThat(resolution.getPackageType()).isEqualTo(PackageType.IncludeFolder);
    assertThat(resolution.mySimplePackageName).isEqualTo("editor-support");
    assertThat(resolution.myRelativeIncludeSubFolder).isEqualTo(
      "/");
    assertThat(resolution.getPackageFamilyBaseFolder().getPath()).isEqualTo(
      "/usr/local/google/home/cocos2d/cocos/editor-support");
  }

  @Test
  public void testTreatCocosRootAsPlainFolder() {
    List<SimpleIncludeValue> resolutions = ResolverTests.resolvedIncludes(
      new CocosIncludeResolver(),
      "-I/usr/local/google/home/cocos2d");
    assertThat(resolutions).hasSize(1);
    SimpleIncludeValue resolution = resolutions.get(0);
    assertThat(resolution).isNotNull();
    assertThat(resolution.getPackageType()).isEqualTo(PackageType.IncludeFolder);
    assertThat(resolution.mySimplePackageName).isEqualTo("cocos2d");
    assertThat(resolution.myRelativeIncludeSubFolder).isEqualTo(
      "/");
    assertThat(resolution.getPackageFamilyBaseFolder().getPath()).isEqualTo(
      "/usr/local/google/home/cocos2d");
  }

  @Test
  public void testTreatCocosRootCocosAsPlainFolder() {
    List<SimpleIncludeValue> resolutions = ResolverTests.resolvedIncludes(
      new CocosIncludeResolver(),
      "-I/usr/local/google/home/cocos2d/cocos");
    assertThat(resolutions).hasSize(1);
    SimpleIncludeValue resolution = resolutions.get(0);
    assertThat(resolution).isNotNull();
    assertThat(resolution.getPackageType()).isEqualTo(PackageType.IncludeFolder);
    assertThat(resolution.mySimplePackageName).isEqualTo("cocos");
    assertThat(resolution.myRelativeIncludeSubFolder).isEqualTo(
      "/");
    assertThat(resolution.getPackageFamilyBaseFolder().getPath()).isEqualTo(
      "/usr/local/google/home/cocos2d/cocos");
  }

  @Test
  public void testCocosEditorPackageResolves() {
    List<SimpleIncludeValue> resolutions = ResolverTests.resolvedIncludes(
      new CocosIncludeResolver(),
      "-I/usr/local/google/home/cocos2d/cocos/editor-support/some-package/b");
    assertThat(resolutions).hasSize(1);
    SimpleIncludeValue resolution = resolutions.get(0);
    assertThat(resolution).isNotNull();
    assertThat(resolution.getPackageType()).isEqualTo(PackageType.CocosEditorSupportModule);
    assertThat(resolution.mySimplePackageName).isEqualTo("some-package");
    assertThat(resolution.myRelativeIncludeSubFolder).isEqualTo(
      "/cocos/editor-support/some-package/b/");
    assertThat(resolution.getPackageFamilyBaseFolder().getPath()).isEqualTo(
      "/usr/local/google/home/cocos2d");
  }

  @Test
  public void testCocosPackageResolves() {
    List<SimpleIncludeValue> resolutions = ResolverTests.resolvedIncludes(
      new CocosIncludeResolver(),
      "-I/usr/local/google/home/cocos2d/cocos/some-package/b");
    assertThat(resolutions).hasSize(1);
    SimpleIncludeValue resolution = resolutions.get(0);
    assertThat(resolution).isNotNull();
    assertThat(resolution.getPackageType()).isEqualTo(PackageType.CocosFrameworkModule);
    assertThat(resolution.mySimplePackageName).isEqualTo("some-package");
    assertThat(resolution.myRelativeIncludeSubFolder).isEqualTo(
      "/cocos/some-package/b/");
    assertThat(resolution.getPackageFamilyBaseFolder().getPath()).isEqualTo(
      "/usr/local/google/home/cocos2d");
  }

  @Test
  public void testCocosExternalPackageResolves() {
    List<SimpleIncludeValue> resolutions = ResolverTests.resolvedIncludes(
      new CocosIncludeResolver(),
      "-I/usr/local/google/someuser/cocos2d-x/tests/cpp-empty-test/Classes");
    assertThat(resolutions).hasSize(1);
    SimpleIncludeValue resolution = resolutions.get(0);
    assertThat(resolution).isNotNull();
    assertThat(resolution.getPackageType()).isEqualTo(PackageType.CocosThirdPartyPackage);
    assertThat(resolution.mySimplePackageName).isEqualTo("tests");
    assertThat(resolution.myRelativeIncludeSubFolder).isEqualTo(
      "/tests/cpp-empty-test/Classes/");
    assertThat(resolution.getPackageFamilyBaseFolder().getPath()).isEqualTo(
      "/usr/local/google/someuser/cocos2d-x");
  }
}