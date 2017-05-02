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
import com.google.common.base.Charsets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;

import java.io.IOException;

public class SampleDataResourceRepositoryTest extends AndroidTestCase {
  public void testDataLoad() {
    myFixture.addFileToProject("sampledata/strings",
                               "string1\n" +
                               "string2\n" +
                               "string3\n");
    SampleDataResourceRepository repo = new SampleDataResourceRepository(myFacet);

    assertEquals(1, repo.getMap(null, ResourceType.SAMPLE_DATA, true).size());
    assertEquals(1, repo.getMap(null, ResourceType.SAMPLE_DATA, true).get("strings").size());
    Disposer.dispose(repo);
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

    myFixture.addFileToProject("sampledata/strings",
                               "string1\n" +
                               "string2\n" +
                               "string3\n");
    myFixture.addFileToProject("sampledata/ints",
                               "1\n" +
                               "2\n");
    myFixture.addFileToProject("sampledata/refs",
                               "@string/test1\n" +
                               "@string/invalid\n");
    myFixture.addFileToProject("res/values/strings.xml", stringsText);
    PsiFile layout = myFixture.addFileToProject("res/layout/layout.xml", layoutText);
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layout.getVirtualFile());
    ResourceResolver resolver = configuration.getResourceResolver();
    assertEquals("string1", resolver.findResValue("@sample/strings", false).getValue());
    assertEquals("1", resolver.findResValue("@sample/ints", false).getValue());
    assertEquals("string2", resolver.findResValue("@sample/strings", false).getValue());
    assertEquals("string3", resolver.findResValue("@sample/strings", false).getValue());
    assertEquals("2", resolver.findResValue("@sample/ints", false).getValue());

    // Check that we wrap around
    assertEquals("string1", resolver.findResValue("@sample/strings", false).getValue());
    assertEquals("1", resolver.findResValue("@sample/ints", false).getValue());


    // Check reference resolution
    assertEquals("Hello 1", resolver.resolveResValue(
      new ResourceValue(ResourceUrl.create(null, ResourceType.STRING, "test"), "@sample/refs")).getValue());
    // @string/invalid does not exist so the sample data will just return the unresolved reference
    assertEquals("@string/invalid", resolver.resolveResValue(
      new ResourceValue(ResourceUrl.create(null, ResourceType.STRING, "test"), "@sample/refs")).getValue());

    assertNull(resolver.findResValue("@sample/invalid", false));
  }

  public void testSampleDataFileInvalidation() throws IOException {
    SampleDataResourceRepository repo = new SampleDataResourceRepository(myFacet);

    assertNull(repo.getMap(null, ResourceType.SAMPLE_DATA, true));

    myFixture.addFileToProject("sampledata/strings",
                               "string1\n" +
                               "string2\n" +
                               "string3\n");
    assertEquals(1, repo.getMap(null, ResourceType.SAMPLE_DATA, true).size());
    assertEquals(1, repo.getMap(null, ResourceType.SAMPLE_DATA, true).get("strings").size());

    myFixture.addFileToProject("sampledata/strings2",
                               "string1\n");
    assertEquals(2, repo.getMap(null, ResourceType.SAMPLE_DATA, true).size());

    VirtualFile sampleDir = SampleDataResourceRepository.getSampleDataDir(myFacet, false);
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        sampleDir.delete(null);
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    });
    assertNull(repo.getMap(null, ResourceType.SAMPLE_DATA, true));
    Disposer.dispose(repo);
  }

  public void testResolverCacheInvalidation() {
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

    PsiFile sampleDataFile = myFixture.addFileToProject("sampledata/strings",
                               "string1\n" +
                               "string2\n" +
                               "string3\n");
    PsiFile layout = myFixture.addFileToProject("res/layout/layout.xml", layoutText);
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layout.getVirtualFile());
    ResourceResolver resolver = configuration.getResourceResolver();
    assertEquals("string1", resolver.findResValue("@sample/strings", false).getValue());
    assertEquals("string2",resolver.findResValue("@sample/strings", false).getValue());
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        sampleDataFile.getVirtualFile().setBinaryContent(("new1\n" +
                                                     "new2\n" +
                                                     "new3\n" +
                                                     "new4\n").getBytes(Charsets.UTF_8));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    });

    // The cursor does not get reset when the file is changed so we expect "new3" as opposed as getting "new1"
    // Ignored temporarily since cache invalidation needs still work
    //assertEquals("new3", resolver.findResValue("@sample/strings", false).getValue());
  }
}