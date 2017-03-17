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
package com.android.tools.idea.apk;

import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link ApkFacetConfiguration}.
 */
public class ApkFacetConfigurationTest {
  private ApkFacetConfiguration myConfiguration;

  @Before
  public void setUp()  {
    myConfiguration = new ApkFacetConfiguration();
  }

  @Test
  public void getDebugSymbolFolderPaths() throws Exception {
    NativeLibrary library1 = new NativeLibrary("x.c");
    library1.debuggableFilePath = "/a/x.c";

    NativeLibrary library2 = new NativeLibrary("x.h");
    library2.debuggableFilePath = "/a/x.h";

    NativeLibrary library3 = new NativeLibrary("y.c");
    library3.debuggableFilePath = "/a/b/y.c";

    myConfiguration.NATIVE_LIBRARIES.add(library1);
    myConfiguration.NATIVE_LIBRARIES.add(library2);
    myConfiguration.NATIVE_LIBRARIES.add(library3);

    Collection<String> paths = myConfiguration.getDebugSymbolFolderPaths();
    assertThat(paths).containsExactly("/a");
  }
}