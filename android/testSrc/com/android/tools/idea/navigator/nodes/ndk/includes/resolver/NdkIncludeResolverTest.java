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

import com.android.tools.idea.navigator.nodes.ndk.includes.RealWorldExamples;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.IncludeValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.IncludeValues;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageType;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.SimpleIncludeValue;
import com.android.tools.idea.navigator.nodes.ndk.includes.utils.IncludeSet;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.navigator.nodes.ndk.includes.resolver.ResolverTests.PATH_TO_NDK;
import static com.android.tools.idea.navigator.nodes.ndk.includes.resolver.ResolverTests.ROOT_OF_RELATIVE_INCLUDE_PATHS;
import static com.google.common.truth.Truth.assertThat;

public class NdkIncludeResolverTest {
  // The main purpose of this test is to catch new components that may have been added to the NDK.
  // When these find their way to RealWorldExamples class then this test is supposed to notice
  // them and fire an assert so that the new package can be added and a test written.
  @Test
  public void exerciseRealWorldExamples() {
    for (ResolverTests.ResolutionResult resolved : ResolverTests.resolveAllRealWorldExamples(new NdkIncludeResolver(new File(PATH_TO_NDK)))) {
      if (resolved.myResolution == null) {
        if (!resolved.myOriginalPath.contains("/samples/")) {
          assertThat(resolved.myOriginalPath).doesNotContain("ndk-bundle");
        }
      } else {
        assertThat(resolved.myOriginalPath).contains("ndk-bundle");
      }
    }
  }

  @Test
  public void testNdkPlatformFolderResolves() {
    List<SimpleIncludeValue> resolutions = ResolverTests.resolvedIncludes(
      new NdkIncludeResolver(new File(PATH_TO_NDK)),
      "-I{ndkPath}/platforms/android-21/arch-arm64/usr/include");
    assertThat(resolutions).hasSize(1);
    SimpleIncludeValue resolution = resolutions.get(0);
    assertThat(resolution).isNotNull();
    assertThat(resolution.getPackageType()).isEqualTo(PackageType.NdkComponent);
    assertThat(resolution.mySimplePackageName).isEqualTo("android-21");
    assertThat(resolution.myRelativeIncludeSubFolder).isEqualTo("/platforms/android-21/arch-arm64/usr/include/");
    assertThat(resolution.getPackageFamilyBaseFolder().getPath()).isEqualTo(PATH_TO_NDK);
  }

  @Test
  public void testNdkStlportResolves() {
    List<SimpleIncludeValue> resolutions = ResolverTests.resolvedIncludes(
      new NdkIncludeResolver(new File(PATH_TO_NDK)),
      "-I{ndkPath}/sources/cxx-stl/stlport/stlport");
    assertThat(resolutions).hasSize(1);
    SimpleIncludeValue resolution = resolutions.get(0);
    assertThat(resolution).isNotNull();
    assertThat(resolution.getPackageType()).isEqualTo(PackageType.NdkComponent);
    assertThat(resolution.mySimplePackageName).isEqualTo("stlport");
    assertThat(resolution.myRelativeIncludeSubFolder).isEqualTo("/sources/cxx-stl/stlport/stlport/");
    assertThat(resolution.getPackageFamilyBaseFolder().getPath()).isEqualTo(PATH_TO_NDK);
  }

  @Test
  public void testNdkGnuLibstdResolves() {
    List<SimpleIncludeValue> resolutions = ResolverTests.resolvedIncludes(
      new NdkIncludeResolver(new File(PATH_TO_NDK)),
      "-I{ndkPath}/sources/cxx-stl/gnu-libstdc++/4.9/libs/armeabi/include");
    assertThat(resolutions).hasSize(1);
    SimpleIncludeValue resolution = resolutions.get(0);
    assertThat(resolution).isNotNull();
    assertThat(resolution.getPackageType()).isEqualTo(PackageType.NdkComponent);
    assertThat(resolution.mySimplePackageName).isEqualTo("gnu-libstdc++");
    assertThat(resolution.myRelativeIncludeSubFolder).isEqualTo("/sources/cxx-stl/gnu-libstdc++/4.9/libs/armeabi/include/");
    assertThat(resolution.getPackageFamilyBaseFolder().getPath()).isEqualTo(PATH_TO_NDK);
  }

  @Test
  public void testNdkThirdPartyToolsResolve() {
    List<SimpleIncludeValue> resolutions = ResolverTests.resolvedIncludes(
      new NdkIncludeResolver(new File(PATH_TO_NDK)),
      "-I{ndkPath}/sources/third_party/googletest/googletest/include");
    assertThat(resolutions).hasSize(1);
    SimpleIncludeValue resolution = resolutions.get(0);
    assertThat(resolution).isNotNull();
    assertThat(resolution.getPackageType()).isEqualTo(PackageType.NdkComponent);
    assertThat(resolution.mySimplePackageName).isEqualTo("googletest");
    assertThat(resolution.myRelativeIncludeSubFolder).isEqualTo("/sources/third_party/googletest/googletest/include/");
    assertThat(resolution.getPackageFamilyBaseFolder().getPath()).isEqualTo(PATH_TO_NDK);
  }

  @Test
  public void testNdkToolchainResolve() {
    List<SimpleIncludeValue> resolutions = ResolverTests.resolvedIncludes(
      new NdkIncludeResolver(new File(PATH_TO_NDK)),
      "-I{ndkPath}/toolchains/renderscript/prebuilt/{platform}-x86_64/lib/clang/3.5/include");
    assertThat(resolutions).hasSize(1);
    SimpleIncludeValue resolution = resolutions.get(0);
    assertThat(resolution).isNotNull();
    assertThat(resolution.getPackageType()).isEqualTo(PackageType.NdkComponent);
    assertThat(resolution.mySimplePackageName).isEqualTo("renderscript");
    assertThat(resolution.myRelativeIncludeSubFolder).isEqualTo(
      "/toolchains/renderscript/prebuilt/android-21-x86_64/lib/clang/3.5/include/");
    assertThat(resolution.getPackageFamilyBaseFolder().getPath()).isEqualTo(PATH_TO_NDK);
  }

  @Test
  public void testNdkCpuFeaturesResolve() {
    List<SimpleIncludeValue> resolutions = ResolverTests.resolvedIncludes(
      new NdkIncludeResolver(new File(PATH_TO_NDK)),
      "-I{ndkPath}/sources/android/cpufeatures");
    assertThat(resolutions).hasSize(1);
    SimpleIncludeValue resolution = resolutions.get(0);
    assertThat(resolution).isNotNull();
    assertThat(resolution.getPackageType()).isEqualTo(PackageType.NdkComponent);
    assertThat(resolution.mySimplePackageName).isEqualTo("CPU Features");
    assertThat(resolution.myRelativeIncludeSubFolder).isEqualTo(
      "/sources/android/cpufeatures/");
    assertThat(resolution.getPackageFamilyBaseFolder().getPath()).isEqualTo(PATH_TO_NDK);
  }

  @Test
  public void testNdkNativeAppGlueResolve() {
    List<SimpleIncludeValue> resolutions = ResolverTests.resolvedIncludes(
      new NdkIncludeResolver(new File(PATH_TO_NDK)),
      "-I{ndkPath}/sources/android/native_app_glue");
    assertThat(resolutions).hasSize(1);
    SimpleIncludeValue resolution = resolutions.get(0);
    assertThat(resolution).isNotNull();
    assertThat(resolution.getPackageType()).isEqualTo(PackageType.NdkComponent);
    assertThat(resolution.mySimplePackageName).isEqualTo("Native App Glue");
    assertThat(resolution.myRelativeIncludeSubFolder).isEqualTo(
      "/sources/android/native_app_glue/");
    assertThat(resolution.getPackageFamilyBaseFolder().getPath()).isEqualTo(PATH_TO_NDK);
  }

  @Test
  public void testNdkHelperResolve() {
    List<SimpleIncludeValue> resolutions = ResolverTests.resolvedIncludes(
      new NdkIncludeResolver(new File(PATH_TO_NDK)),
      "-I{ndkPath}/sources/android/ndk_helper");
    assertThat(resolutions).hasSize(1);
    SimpleIncludeValue resolution = resolutions.get(0);
    assertThat(resolution).isNotNull();
    assertThat(resolution.getPackageType()).isEqualTo(PackageType.NdkComponent);
    assertThat(resolution.mySimplePackageName).isEqualTo("NDK Helper");
    assertThat(resolution.myRelativeIncludeSubFolder).isEqualTo(
      "/sources/android/ndk_helper/");
    assertThat(resolution.getPackageFamilyBaseFolder().getPath()).isEqualTo(PATH_TO_NDK);
  }

  @Test
  public void testGroupBySimpleNameKind() throws Exception {
    for (List<String> includes : RealWorldExamples.getConcreteCompilerIncludeFlags(PATH_TO_NDK) ) {
      List<SimpleIncludeValue> dependencies = new ArrayList<>();
      IncludeSet set = new IncludeSet();
      set.addIncludesFromCompilerFlags(includes, ROOT_OF_RELATIVE_INCLUDE_PATHS);
      for (File include : set.getIncludesInOrder()) {
        NdkIncludeResolver resolver = new NdkIncludeResolver(new File(PATH_TO_NDK));
        SimpleIncludeValue nativeDependency = resolver.resolve(include);
        if (nativeDependency != null) {
          dependencies.add(nativeDependency);
        }
      }
      List<IncludeValue> organizedIncludeValues = IncludeValues.organize(dependencies);
      assertThat(organizedIncludeValues).isNotEmpty();
    }
  }
}