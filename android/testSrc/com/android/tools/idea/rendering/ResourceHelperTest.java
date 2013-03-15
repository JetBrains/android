/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.resources.ResourceType;
import junit.framework.TestCase;

public class ResourceHelperTest extends TestCase {
  public void testIsFileBasedResourceType() throws Exception {
    assertTrue(ResourceHelper.isFileBasedResourceType(ResourceType.ANIMATOR));
    assertTrue(ResourceHelper.isFileBasedResourceType(ResourceType.LAYOUT));

    assertFalse(ResourceHelper.isFileBasedResourceType(ResourceType.STRING));
    assertFalse(ResourceHelper.isFileBasedResourceType(ResourceType.DIMEN));
    assertFalse(ResourceHelper.isFileBasedResourceType(ResourceType.ID));

    // Both:
    assertTrue(ResourceHelper.isFileBasedResourceType(ResourceType.DRAWABLE));
    assertTrue(ResourceHelper.isFileBasedResourceType(ResourceType.COLOR));
  }

  public void testIsValueBasedResourceType() throws Exception {
    assertTrue(ResourceHelper.isValueBasedResourceType(ResourceType.STRING));
    assertTrue(ResourceHelper.isValueBasedResourceType(ResourceType.DIMEN));
    assertTrue(ResourceHelper.isValueBasedResourceType(ResourceType.ID));

    assertFalse(ResourceHelper.isValueBasedResourceType(ResourceType.LAYOUT));

    // These can be both:
    assertTrue(ResourceHelper.isValueBasedResourceType(ResourceType.DRAWABLE));
    assertTrue(ResourceHelper.isValueBasedResourceType(ResourceType.COLOR));
  }

  public void testStyleToTheme() throws Exception {
    assertEquals("Foo", ResourceHelper.styleToTheme("Foo"));
    assertEquals("Theme", ResourceHelper.styleToTheme("@android:style/Theme"));
    assertEquals("LocalTheme", ResourceHelper.styleToTheme("@style/LocalTheme"));
    //assertEquals("LocalTheme", ResourceHelper.styleToTheme("@foo.bar:style/LocalTheme"));
  }

  public void testIsProjectStyle() throws Exception {
    assertFalse(ResourceHelper.isProjectStyle("@android:style/Theme"));
    assertTrue(ResourceHelper.isProjectStyle("@style/LocalTheme"));
  }
}
