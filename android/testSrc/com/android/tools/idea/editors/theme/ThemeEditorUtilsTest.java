/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;

import java.util.Arrays;
import java.util.Collections;
import org.jetbrains.android.AndroidTestCase;

/**
 * Tests for {@link ThemeEditorUtils} and indirectly {@link com.android.tools.idea.javadoc.AndroidJavaDocRenderer}.
 */
public class ThemeEditorUtilsTest extends AndroidTestCase {

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  public void testMinApiLevel() {
    myFixture.copyFileToProject("themeEditor/manifestWithApi.xml", FN_ANDROID_MANIFEST_XML);
    assertEquals(11, ThemeEditorUtils.getMinApiLevel(myModule));
  }

  public void testGenerateWordEnumeration() {
    assertEquals("", ThemeEditorUtils.generateWordEnumeration(Collections.emptyList()));
    assertEquals("one", ThemeEditorUtils.generateWordEnumeration(Collections.singletonList("one")));
    assertEquals("one and two", ThemeEditorUtils.generateWordEnumeration(Arrays.asList("one", "two")));
    assertEquals("one, two and Three", ThemeEditorUtils.generateWordEnumeration(Arrays.asList("one", "two", "Three")));
  }
}
