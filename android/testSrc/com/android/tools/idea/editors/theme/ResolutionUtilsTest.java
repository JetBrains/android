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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValueImpl;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

public class ResolutionUtilsTest extends AndroidTestCase {
  /*
   * The test SDK only includes some resources. It only includes a few incomplete styles.
   */

  public void testGetQualifiedName() {
    StyleResourceValue styleResourceValue = new StyleResourceValueImpl(new ResourceReference(ResourceNamespace.ANDROID,
                                                                                             ResourceType.STYLE,
                                                                                             "myStyle"),
                                                                       null,
                                                                       null);
    assertEquals("android:myStyle", ResolutionUtils.getQualifiedStyleName(styleResourceValue));

    styleResourceValue = new StyleResourceValueImpl(ResourceNamespace.RES_AUTO, "myStyle", null, null);
    assertEquals("myStyle", ResolutionUtils.getQualifiedStyleName(styleResourceValue));
  }

  /**
   * Tests {@link ResolutionUtils#getQualifiedNameFromResourceUrl(String)}
   */
  public void testGetQualifiedNameFromResourceUrl() {
    assertEquals("android:Theme", ResolutionUtils.getQualifiedNameFromResourceUrl("@android:style/Theme"));
    assertEquals("namespace:Theme", ResolutionUtils.getQualifiedNameFromResourceUrl("@namespace:style/Theme"));
    assertEquals("AppTheme", ResolutionUtils.getQualifiedNameFromResourceUrl("@style/AppTheme"));
    // TODO get it to also work for theme attr urls
    // for theme attributes, the "attr/" is optional: http://developer.android.com/guide/topics/resources/accessing-resources.html
  }

  /**
   * Tests {@link ResolutionUtils#getNameFromQualifiedName(String)}
   */
  public void testGetNameFromQualifiedName() {
    assertEquals("Theme", ResolutionUtils.getNameFromQualifiedName("app:Theme"));
    assertEquals("Theme", ResolutionUtils.getNameFromQualifiedName("android:Theme"));
    assertEquals("AppTheme", ResolutionUtils.getNameFromQualifiedName("AppTheme"));
  }

  public void testGetOriginalApiLevel() {
    assertEquals(-1, ResolutionUtils.getOriginalApiLevel(null, getProject()));

    // Testing Api of an attribute name
    assertEquals(21, ResolutionUtils.getOriginalApiLevel("android:colorAccent", getProject()));
    assertEquals(-1, ResolutionUtils.getOriginalApiLevel("colorAccent", getProject()));

    // Testing Api of an attribute value
    assertEquals(14, ResolutionUtils.getOriginalApiLevel("@android:color/holo_green_dark", getProject()));
    assertEquals(21, ResolutionUtils.getOriginalApiLevel("?android:attr/colorAccent", getProject()));
    assertEquals(3, ResolutionUtils.getOriginalApiLevel("@android:integer/config_longAnimTime", getProject()));
    assertEquals(4, ResolutionUtils.getOriginalApiLevel("@android:drawable/stat_sys_vp_phone_call", getProject()));
    assertEquals(1, ResolutionUtils.getOriginalApiLevel("@android:color/black", getProject()));
    assertEquals(-1, ResolutionUtils.getOriginalApiLevel("?attr/colorAccent", getProject()));
    assertEquals(-1, ResolutionUtils.getOriginalApiLevel("@color/holo_green_dark", getProject()));
  }

  /**
   * Tests {@link ResolutionUtils#getParentQualifiedName(StyleResourceValue)}
   */
  public void testGetParentQualifiedName() {
    VirtualFile file = myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles.xml", "res/values/styles.xml");
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file);
    ResourceResolver resolver = configuration.getResourceResolver();
    assertNotNull(resolver);
    StyleResourceValue style;

    style = resolver.getStyle("Theme", true);
    assertNotNull(style);
    assertEquals(null, ResolutionUtils.getParentQualifiedName(style));

    style = resolver.getStyle("Theme.Holo.Light", true);
    assertNotNull(style);
    assertEquals("android:Theme.Light", ResolutionUtils.getParentQualifiedName(style));

    style = resolver.getStyle("ATheme", false);
    assertNotNull(style);
    assertEquals("android:Theme", ResolutionUtils.getParentQualifiedName(style));

    style = resolver.getStyle("AppTheme", false);
    assertNotNull(style);
    assertEquals("ATheme", ResolutionUtils.getParentQualifiedName(style));

    style = resolver.getStyle("ATheme.Red", false);
    assertNotNull(style);
    assertEquals("ATheme", ResolutionUtils.getParentQualifiedName(style));
  }
}
