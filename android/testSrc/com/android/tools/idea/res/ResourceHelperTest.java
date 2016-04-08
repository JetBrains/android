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
package com.android.tools.idea.res;

import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.AndroidTestCase;

import java.awt.*;
import java.util.List;

import static com.android.tools.idea.res.ResourceHelper.getResourceName;
import static com.android.tools.idea.res.ResourceHelper.getResourceUrl;
import static com.android.tools.idea.res.ResourceHelper.resolveColor;
import static com.google.common.truth.Truth.assertThat;

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
    assertTrue(ResourceHelper.isProjectStyle("@namespace:style/Theme"));
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

    // Test that spaces are correctly trimmed
    c = ResourceHelper.parseColor("#0f4 ");
    assert c != null;
    assertEquals(0xff00ff44, c.getRGB());

    c = ResourceHelper.parseColor(" #1237");
    assert c != null;
    assertEquals(0x11223377, c.getRGB());

    c = ResourceHelper.parseColor("#123456\n\n ");
    assert c != null;
    assertEquals(0xff123456, c.getRGB());

    assertNull(ResourceHelper.parseColor("#123 456"));
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

  public void testDisabledStateListStates() {
    ResourceHelper.StateListState disabled = new ResourceHelper.StateListState("value", ImmutableMap.of("state_enabled", false), null);
    ResourceHelper.StateListState disabledPressed =
      new ResourceHelper.StateListState("value", ImmutableMap.of("state_enabled", false, "state_pressed", true), null);
    ResourceHelper.StateListState pressed = new ResourceHelper.StateListState("value", ImmutableMap.of("state_pressed", true), null);
    ResourceHelper.StateListState enabledPressed =
      new ResourceHelper.StateListState("value", ImmutableMap.of("state_enabled", true, "state_pressed", true), null);
    ResourceHelper.StateListState enabled = new ResourceHelper.StateListState("value", ImmutableMap.of("state_enabled", true), null);
    ResourceHelper.StateListState selected = new ResourceHelper.StateListState("value", ImmutableMap.of("state_selected", true), null);
    ResourceHelper.StateListState selectedPressed =
      new ResourceHelper.StateListState("value", ImmutableMap.of("state_selected", true, "state_pressed", true), null);
    ResourceHelper.StateListState enabledSelectedPressed =
      new ResourceHelper.StateListState("value", ImmutableMap.of("state_enabled", true, "state_selected", true, "state_pressed", true),
                                        null);
    ResourceHelper.StateListState notFocused = new ResourceHelper.StateListState("value", ImmutableMap.of("state_focused", false), null);
    ResourceHelper.StateListState notChecked = new ResourceHelper.StateListState("value", ImmutableMap.of("state_checked", false), null);
    ResourceHelper.StateListState checkedNotPressed =
      new ResourceHelper.StateListState("value", ImmutableMap.of("state_checked", true, "state_pressed", false), null);

    ResourceHelper.StateList stateList = new ResourceHelper.StateList("stateList", "colors");
    stateList.addState(pressed);
    stateList.addState(disabled);
    stateList.addState(selected);
    assertThat(stateList.getDisabledStates()).containsExactly(disabled);

    stateList.addState(disabledPressed);
    assertThat(stateList.getDisabledStates()).containsExactly(disabled, disabledPressed);

    stateList = new ResourceHelper.StateList("stateList", "colors");
    stateList.addState(enabled);
    stateList.addState(pressed);
    stateList.addState(selected);
    stateList.addState(enabledPressed); // Not reachable
    stateList.addState(disabled);
    assertThat(stateList.getDisabledStates()).containsExactly(pressed, selected, enabledPressed, disabled);

    stateList = new ResourceHelper.StateList("stateList", "colors");
    stateList.addState(enabledPressed);
    stateList.addState(pressed);
    stateList.addState(selected);
    stateList.addState(disabled);
    assertThat(stateList.getDisabledStates()).containsExactly(pressed, disabled);

    stateList.addState(selectedPressed);
    assertThat(stateList.getDisabledStates()).containsExactly(pressed, disabled, selectedPressed);

    stateList = new ResourceHelper.StateList("stateList", "colors");
    stateList.addState(enabledSelectedPressed);
    stateList.addState(pressed);
    stateList.addState(selected);
    stateList.addState(disabled);
    stateList.addState(selectedPressed);
    assertThat(stateList.getDisabledStates()).containsExactly(disabled, selectedPressed);

    stateList = new ResourceHelper.StateList("stateList", "colors");
    stateList.addState(enabledPressed);
    stateList.addState(notChecked);
    stateList.addState(checkedNotPressed);
    stateList.addState(selected);
    stateList.addState(notFocused);
    assertThat(stateList.getDisabledStates()).containsExactly(selected, notFocused);
  }

  public void testGetCompletionFromTypes() {
    myFixture.copyFileToProject("resourceHelper/values.xml", "res/values/values.xml");
    myFixture.copyFileToProject("resourceHelper/my_state_list.xml", "res/color/my_state_list.xml");

    List<String> colorOnly = ResourceHelper.getCompletionFromTypes(myFacet, new ResourceType[]{ResourceType.COLOR});
    List<String> drawableOnly = ResourceHelper.getCompletionFromTypes(myFacet, new ResourceType[]{ResourceType.DRAWABLE});
    List<String> colorAndDrawable =
      ResourceHelper.getCompletionFromTypes(myFacet, new ResourceType[]{ResourceType.COLOR, ResourceType.DRAWABLE});
    List<String> dimenOnly = ResourceHelper.getCompletionFromTypes(myFacet, new ResourceType[]{ResourceType.DIMEN});

    assertThat(colorOnly).containsExactly("@android:color/primary_text_dark", "@color/myColor1", "@color/myColor2", "@color/my_state_list");
    assertThat(drawableOnly)
      .containsExactly("@android:color/primary_text_dark", "@color/myColor1", "@color/myColor2", "@android:drawable/menuitem_background");
    assertThat(colorAndDrawable)
      .containsExactly("@android:color/primary_text_dark", "@color/myColor1", "@color/myColor2", "@color/my_state_list",
                       "@android:drawable/menuitem_background");
    assertThat(dimenOnly).containsExactly("@dimen/myAlpha", "@dimen/myDimen");
  }

  public void testResolveEmptyStatelist() {
    VirtualFile file = myFixture.copyFileToProject("resourceHelper/empty_state_list.xml", "res/color/empty_state_list.xml");
    RenderResources rr = myFacet.getConfigurationManager().getConfiguration(file).getResourceResolver();
    assertNotNull(rr);
    ResourceValue rv = rr.getProjectResource(ResourceType.COLOR, "empty_state_list");
    assertNotNull(rv);
    assertNull(resolveColor(rr, rv, myModule.getProject()));
  }
}
