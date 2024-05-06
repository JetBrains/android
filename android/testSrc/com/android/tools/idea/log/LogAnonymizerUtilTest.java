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
package com.android.tools.idea.log;

import com.android.tools.rendering.api.RenderModelModule;
import com.android.tools.rendering.log.LogAnonymizerUtil;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LogAnonymizerUtilTest {
  @Test
  public void testAnonymizeModuleName() {
    RenderModelModule module = mock(RenderModelModule.class);

    when(module.getName())
      .thenReturn("moduleName")
      .thenReturn("moduleName")
      .thenReturn("moduleName2");

    String moduleNameHashed = LogAnonymizerUtil.anonymize(module);
    assertNotEquals("moduleName", moduleNameHashed);
    assertEquals(moduleNameHashed, LogAnonymizerUtil.anonymize(module));

    String moduleName2Hashed = LogAnonymizerUtil.anonymize(module);
    assertNotEquals("moduleName2", moduleName2Hashed);
    assertNotEquals(moduleNameHashed, moduleName2Hashed);
  }
}