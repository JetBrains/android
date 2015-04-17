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

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.AndroidTestCase;

import java.awt.*;

import static com.android.tools.idea.rendering.ResourceHelper.getResourceName;
import static com.android.tools.idea.rendering.ResourceHelper.getResourceUrl;

public class ResourceHelperTest extends AndroidTestCase {
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

  @SuppressWarnings("ConstantConditions")
  public void testGetResourceNameAndUrl() throws Exception {
    PsiFile file1 = myFixture.addFileToProject("res/layout-land/foo1.xml", "<LinearLayout/>");
    PsiFile file2 = myFixture.addFileToProject("res/menu-en-rUS/foo2.xml", "<menu/>");
    // Not a proper PNG file, but we just need a .9.something path to verify basename handling is right
    // and it has to be an XML file to get a PSI file out of the fixture
    PsiFile file3 = myFixture.addFileToProject("res/drawable-hdpi/foo3.9.xml", "invalidImage");

    assertEquals("foo1", getResourceName(file1));
    assertEquals("foo2", getResourceName(file2));
    assertEquals("foo3", getResourceName(file3));
    assertEquals("foo1", getResourceName(file1.getVirtualFile()));
    assertEquals("foo2", getResourceName(file2.getVirtualFile()));
    assertEquals("foo3", getResourceName(file3.getVirtualFile()));
    assertEquals("@layout/foo1", getResourceUrl(file1.getVirtualFile()));
    assertEquals("@menu/foo2", getResourceUrl(file2.getVirtualFile()));
    assertEquals("@drawable/foo3", getResourceUrl(file3.getVirtualFile()));
  }

  @SuppressWarnings("ConstantConditions")
  public void testGetFolderConfiguration() throws Exception {
    PsiFile file1 = myFixture.addFileToProject("res/layout-land/foo1.xml", "<LinearLayout/>");
    PsiFile file2 = myFixture.addFileToProject("res/menu-en-rUS/foo2.xml", "<menu/>");

    assertEquals("layout-land", ResourceHelper.getFolderConfiguration(file1).getFolderName(ResourceFolderType.LAYOUT));
    assertEquals("menu-en-rUS", ResourceHelper.getFolderConfiguration(file2).getFolderName(ResourceFolderType.MENU));
    assertEquals("layout-land", ResourceHelper.getFolderConfiguration(file1.getVirtualFile()).getFolderName(ResourceFolderType.LAYOUT));
    assertEquals("menu-en-rUS", ResourceHelper.getFolderConfiguration(file2.getVirtualFile()).getFolderName(ResourceFolderType.MENU));
  }

  public void testRGB() {
    Color c = ResourceHelper.parseColor("#0f4");
    assert c != null;
    assertEquals(0xff00ff44, c.getRGB());

    c = ResourceHelper.parseColor("#1237");
    assert c != null;
    assertEquals(0x11223377, c.getRGB());

    c = ResourceHelper.parseColor("#123456");
    assert c != null;
    assertEquals(0xff123456, c.getRGB());

    c = ResourceHelper.parseColor("#08123456");
    assert c != null;
    assertEquals(0x08123456, c.getRGB());
  }

  public void testColorToString() {
    Color c = new Color(0x0fff0000, true);
    assertEquals("#0fff0000", ResourceHelper.colorToString(c));

    c = new Color(0x00ff00);
    assertEquals("#00ff00", ResourceHelper.colorToString(c));

    c = new Color(0x00000000, true);
    assertEquals("#00000000", ResourceHelper.colorToString(c));

    Color color = new Color(0x11, 0x22, 0x33, 0xf0);
    assertEquals("#f0112233", ResourceHelper.colorToString(color));

    color = new Color(0xff, 0xff, 0xff, 0x00);
    assertEquals("#00ffffff", ResourceHelper.colorToString(color));
  }
}
