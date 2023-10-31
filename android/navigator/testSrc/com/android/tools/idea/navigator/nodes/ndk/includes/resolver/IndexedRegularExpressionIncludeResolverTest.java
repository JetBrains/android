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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.navigator.nodes.ndk.includes.model.PackageType;
import com.android.tools.idea.navigator.nodes.ndk.includes.model.SimpleIncludeValue;
import java.io.File;
import org.junit.Test;

public class IndexedRegularExpressionIncludeResolverTest {

  @Test
  public void testLibraryVariationNoMatch() {
    IndexedRegularExpressionIncludeResolver resolver =
      new IndexedRegularExpressionIncludeResolver(PackageType.NdkComponent,
                                                  "^(/path/to/ndk-bundle)(/sources/android/native_app_glue(/.*))$",
                                                  "Native App Glue");
    SimpleIncludeValue result = resolver.resolve(new File("."));
    assertThat(result).isNull();
  }

  @Test
  public void testLibraryVariationMatch() {
    IndexedRegularExpressionIncludeResolver resolver =
      new IndexedRegularExpressionIncludeResolver(PackageType.NdkComponent,
                                                  "^(?<home>/path/to/ndk-bundle)(?<relative>/sources/android/native_app_glue(/.*))$",
                                                  "Native App Glue");
    SimpleIncludeValue result = resolver.resolve(new File("/path/to/ndk-bundle/sources/android/native_app_glue"));
    assertThat(result).isNotNull();
    assertThat(result.getSimplePackageName()).isEqualTo("Native App Glue");
    assertThat(result.getRelativeIncludeSubFolder()).isEqualTo("/sources/android/native_app_glue/");
    assertThat(result.getIncludeFolder()).isEqualTo(new File("/path/to/ndk-bundle/sources/android/native_app_glue"));
  }

  @Test
  public void testNoLibraryVariationNoMatch() {
    IndexedRegularExpressionIncludeResolver resolver =
      new IndexedRegularExpressionIncludeResolver(PackageType.ThirdParty, "^(.*)(/third[_-]party/(.*?)/.*)$", null);
    SimpleIncludeValue result = resolver.resolve(new File("."));
    assertThat(result).isNull();
  }

  @Test
  public void testNoLibraryVariationMatch() {
    IndexedRegularExpressionIncludeResolver resolver =
      new IndexedRegularExpressionIncludeResolver(PackageType.ThirdParty, "^(?<home>.*)(?<relative>/third[_-]party/(?<library>.*?)/.*)$", null);
    SimpleIncludeValue result = resolver.resolve(new File("/path/to/third_party/include"));
    assertThat(result).isNotNull();
    assertThat(result.getSimplePackageName()).isEqualTo("include");
    assertThat(result.getRelativeIncludeSubFolder()).isEqualTo("/third_party/include/");
    assertThat(result.getIncludeFolder()).isEqualTo(new File("/path/to/third_party/include"));
  }
}
