/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.intellij.openapi.module.Module;
import org.jetbrains.android.AndroidTestCase;

public class ApkFacetTest extends AndroidTestCase {

  private Module myDisposedModule;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDisposedModule = mock(Module.class);
    when(myDisposedModule.isDisposed()).thenReturn(true);
  }

  public void testDisposedModule() {
    assertNull(ApkFacet.getInstance(myDisposedModule));
  }
}