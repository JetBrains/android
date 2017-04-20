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
package com.android.tools.idea.res;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.intellij.psi.PsiFile;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;

public class MockDataResourceRepositoryTest extends AndroidTestCase {
  public void testDataLoad() {
    myFixture.addFileToProject("mocks/strings",
                               "string1\n" +
                               "string2\n" +
                               "string3\n");
    MockDataResourceRepository repo = new MockDataResourceRepository(myFacet);

    assertEquals(1, repo.getMap(null, ResourceType.MOCK, true).size());
    assertEquals(1, repo.getMap(null, ResourceType.MOCK, true).get("strings").size());
  }

  public void testResolver() {
    @Language("XML")
    String layoutText = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    android:layout_width=\"match_parent\"\n" +
                        "    android:layout_height=\"match_parent\" />";
    @Language("XML")
    String stringsText = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                         "<resources>\n" +
                         "  <string name=\"test1\">Hello 1</string>\n" +
                         "  <string name=\"test2\">Hello 2</string>\n" +
                         "</resources>";

    myFixture.addFileToProject("mocks/strings",
                               "string1\n" +
                               "string2\n" +
                               "string3\n");
    myFixture.addFileToProject("mocks/ints",
                               "1\n" +
                               "2\n");
    myFixture.addFileToProject("mocks/refs",
                               "@string/test1\n" +
                               "@string/invalid\n");
    myFixture.addFileToProject("res/values/strings.xml", stringsText);
    PsiFile layout = myFixture.addFileToProject("res/layout/layout.xml", layoutText);
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layout.getVirtualFile());
    ResourceResolver resolver = configuration.getResourceResolver();
    assertEquals("string1", resolver.findResValue("@mock/strings", false).getValue());
    assertEquals("1", resolver.findResValue("@mock/ints", false).getValue());
    assertEquals("string2", resolver.findResValue("@mock/strings", false).getValue());
    assertEquals("string3", resolver.findResValue("@mock/strings", false).getValue());
    assertEquals("2", resolver.findResValue("@mock/ints", false).getValue());

    // Check that we wrap around
    assertEquals("string1", resolver.findResValue("@mock/strings", false).getValue());
    assertEquals("1", resolver.findResValue("@mock/ints", false).getValue());


    // Check reference resolution
    assertEquals("Hello 1", resolver.resolveResValue(
      new ResourceValue(ResourceUrl.create(null, ResourceType.STRING, "test"), "@mock/refs")).getValue());
    // @string/invalid does not exist so the mock will just return the unresolved reference
    assertEquals("@string/invalid", resolver.resolveResValue(
      new ResourceValue(ResourceUrl.create(null, ResourceType.STRING, "test"), "@mock/refs")).getValue());

    assertNull(resolver.findResValue("@mock/invalid", false));
  }
}