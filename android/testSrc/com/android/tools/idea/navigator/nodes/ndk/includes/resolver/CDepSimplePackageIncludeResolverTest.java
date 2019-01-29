/*
 * Copyright (C) 2018 The Android Open Source Project
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

public class CDepSimplePackageIncludeResolverTest {
  @Test
  public void testSimplePackageIncludeResolver() {
    List<SimpleIncludeValue> resolutions = ResolverTests.resolvedIncludes(
      new CDepIncludeResolver(),
      "-I/usr/local/google/home/jomof/.cdep/exploded/com.github.jomof/mathfu/1.1.0-rev3/mathfu-headers.zip/include");
    assertThat(resolutions).hasSize(1);
    SimpleIncludeValue resolution = resolutions.get(0);
    assertThat(resolution).isNotNull();
    assertThat(resolution.getPackageType()).isEqualTo(PackageType.CDepPackage);
    assertThat(resolution.mySimplePackageName).isEqualTo("mathfu");
    assertThat(resolution.myRelativeIncludeSubFolder).isEqualTo(
      "/com.github.jomof/mathfu/1.1.0-rev3/mathfu-headers.zip/include/");
    assertThat(resolution.getPackageFamilyBaseFolder().getPath()).isEqualTo(
      "/usr/local/google/home/jomof/.cdep/exploded");
  }
}
