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

import com.android.tools.idea.navigator.nodes.ndk.includes.model.SimpleIncludeValue;
import org.junit.Test;

import java.io.File;

import static com.google.common.truth.Truth.assertThat;

public class PlainFolderRegularExpressionIncludeResolverTest {
  @Test
  public void test() {
    IncludeResolver resolver = PlainFolderRegularExpressionIncludeResolver.include("^.*/cocos2d[^//]*/external/$");
    SimpleIncludeValue result = resolver.resolve(new File("/path/to/cocos2d/external"));
    assertThat(result).isNotNull();
    assertThat(result.mySimplePackageName).isEqualTo("external");
    assertThat(result.myRelativeIncludeSubFolder).isEqualTo("/");
    assertThat(result.myIncludeFolder).isEqualTo(new File("/path/to/cocos2d/external"));
  }
}
