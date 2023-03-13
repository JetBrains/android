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

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LogAnonymizerUtilTest {
  @Test
  public void testIsGoogleClass() {
    List<String> nonGoogleClasses = ImmutableList.of(
      "com.google",
      "com.googletest.Class",
      "android2",
      "android2.test.Class",
      "android2.test.Class$Inner"
    );

    List<String> googleClasses = ImmutableList.of(
      "com.google.Class",
      "android.test",
      "android.google.test.Class",
      "android.google.test.Class$Inner"
    );

    for (String c : nonGoogleClasses) {
      assertFalse(LogAnonymizerUtil.isPublicClass(c));
      assertFalse(LogAnonymizerUtil.isPublicClass(c.replace(".", "/")));
    }

    for (String c : googleClasses) {
      assertTrue(LogAnonymizerUtil.isPublicClass(c));
      assertTrue(LogAnonymizerUtil.isPublicClass(c.replace(".", "/")));
    }
  }

  @Test
  public void testAnonymizeClassName() {
    assertEquals("com.google.Class", LogAnonymizerUtil.anonymizeClassName("com.google.Class"));

    String hashedClassName = LogAnonymizerUtil.anonymizeClassName("com.myapp.Class");
    assertNotEquals("com.myapp.Class", hashedClassName);
    assertEquals(hashedClassName, LogAnonymizerUtil.anonymizeClassName("com.myapp.Class"));
    assertEquals(hashedClassName, LogAnonymizerUtil.anonymizeClassName("com.myapp.Class"));
  }

  @Test
  public void testAnonymizeModuleName() {
    Module module = mock(Module.class);

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