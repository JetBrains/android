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
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.flags.StudioFlags;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;

public class SampleDataResourceRepositoryTest extends AndroidTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    StudioFlags.NELE_SAMPLE_DATA.override(true);
  }

  @NotNull
  private static Collection<ResourceItem> onlyProjectSources(@NotNull SampleDataResourceRepository repo) {
    return repo.getMap(RES_AUTO, ResourceType.SAMPLE_DATA, true).values();
  }

  @Nullable
  private static List<ResourceItem> onlyProjectSources(@NotNull SampleDataResourceRepository repo, @NotNull String resName) {
    return repo.getMap(RES_AUTO, ResourceType.SAMPLE_DATA, true).get(resName);
  }

  public void testDataLoad() {
    myFixture.addFileToProject("sampledata/strings",
                               "string1\n" +
                               "string2\n" +
                               "string3\n");
    myFixture.addFileToProject("sampledata/images/image1.png",
                               "Insert image here\n");
    myFixture.addFileToProject("sampledata/images/image2.jpg",
                               "Insert image here 2\n");
    myFixture.addFileToProject("sampledata/images/image3.png",
                               "Insert image here 3\n");
    SampleDataResourceRepository repo = new SampleDataResourceRepository(myFacet);

    assertEquals(2, onlyProjectSources(repo).size());
    assertEquals(1, onlyProjectSources(repo, "strings").size());
    assertEquals(1, onlyProjectSources(repo, "images").size());
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
    myFixture.addFileToProject("sampledata/users.json",
                               "{\n" +
                               "  \"users\": [\n" +
                               "    {\n" +
                               "      \"name\": \"Name1\",\n" +
                               "      \"surname\": \"Surname1\"\n" +
                               "    },\n" +
                               "    {\n" +
                               "      \"name\": \"Name2\",\n" +
                               "      \"surname\": \"Surname2\"\n" +
                               "    },\n" +
                               "    {\n" +
                               "      \"name\": \"Name3\",\n" +
                               "      \"surname\": \"Surname3\",\n" +
                               "      \"phone\": \"555-00000\"\n" +
                               "    }\n" +
                               "  ]\n" +
                               "}");
    PsiFile image1 = myFixture.addFileToProject("sampledata/images/image1.png",
                               "Insert image here\n");
    PsiFile image2 = myFixture.addFileToProject("sampledata/images/image2.jpg",
                               "Insert image here 2\n");
    myFixture.addFileToProject("res/values/strings.xml", stringsText);
    PsiFile layout = myFixture.addFileToProject("res/layout/layout.xml", layoutText);
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layout.getVirtualFile());
    ResourceResolver resolver = configuration.getResourceResolver();
    assertEquals("string1", resolver.findResValue("@sample/strings", false).getValue());
    assertEquals("1", resolver.findResValue("@sample/ints", false).getValue());
    assertEquals("string2", resolver.findResValue("@sample/strings", false).getValue());
    assertEquals("string3", resolver.findResValue("@sample/strings", false).getValue());
    assertEquals("2", resolver.findResValue("@sample/ints", false).getValue());

    // Test passing json references
    assertEquals("Name1", resolver.findResValue("@sample/users.json/users/name", false).getValue());

    // The order of the returned paths might depend on the file system
    Set<String> imagePaths = ImmutableSet.of(
      resolver.findResValue("@sample/images", false).getValue(),
      resolver.findResValue("@sample/images", false).getValue());
    assertTrue(imagePaths.contains(image1.getVirtualFile().getCanonicalPath()));
    assertTrue(imagePaths.contains(image2.getVirtualFile().getCanonicalPath()));

    // Check that we wrap around
    assertEquals("string1", resolver.findResValue("@sample/strings", false).getValue());
    assertEquals("1", resolver.findResValue("@sample/ints", false).getValue());
    assertTrue(imagePaths.contains(resolver.findResValue("@sample/images", false).getValue()));

    // Check reference resolution
    assertEquals("Hello 1", resolver.resolveResValue(
      new ResourceValue(RES_AUTO, ResourceType.STRING, "test", "@sample/refs")).getValue());
    // @string/invalid does not exist so the sample data will just return the unresolved reference
    assertEquals("@string/invalid", resolver.resolveResValue(
      new ResourceValue(RES_AUTO, ResourceType.STRING, "test", "@sample/refs")).getValue());

    // Check indexing (all calls should return the same)
    assertEquals("Name2", resolver.findResValue("@sample/users.json/users/name[1]", false).getValue());
    assertEquals("Name2", resolver.findResValue("@sample/users.json/users/name[1]", false).getValue());


    assertNull(resolver.findResValue("@sample/invalid", false));
  }

  public void testSampleDataFileInvalidation() throws IOException {
    SampleDataResourceRepository repo = new SampleDataResourceRepository(myFacet);

    assertTrue(onlyProjectSources(repo).isEmpty());

    myFixture.addFileToProject("sampledata/strings",
                               "string1\n" +
                               "string2\n" +
                               "string3\n");
    assertEquals(1, onlyProjectSources(repo).size());
    assertEquals(1, onlyProjectSources(repo, "strings").size());

    myFixture.addFileToProject("sampledata/strings2",
                               "string1\n");
    assertEquals(2, onlyProjectSources(repo).size());

    VirtualFile sampleDir = SampleDataResourceRepository.getSampleDataDir(myFacet, false);
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        sampleDir.delete(null);
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    });
    assertTrue(onlyProjectSources(repo).isEmpty());
  }

  public void testJsonSampleData() {
    myFixture.addFileToProject("sampledata/users.json",
                               "{\n" +
                               "  \"users\": [\n" +
                               "    {\n" +
                               "      \"name\": \"Name1\",\n" +
                               "      \"surname\": \"Surname1\"\n" +
                               "    },\n" +
                               "    {\n" +
                               "      \"name\": \"Name2\",\n" +
                               "      \"surname\": \"Surname2\"\n" +
                               "    },\n" +
                               "    {\n" +
                               "      \"name\": \"Name3\",\n" +
                               "      \"surname\": \"Surname3\",\n" +
                               "      \"phone\": \"555-00000\"\n" +
                               "    }\n" +
                               "  ]\n" +
                               "}");
    myFixture.addFileToProject("sampledata/invalid.json",
                               "{\n" +
                               "  \"users\": [\n" +
                               "    {\n" +
                               "      \"name\": \"Name1\",\n" +
                               "      \"surname\": \"Surname1\"\n" +
                               "    },\n");
    SampleDataResourceRepository repo = new SampleDataResourceRepository(myFacet);

    // Three different items are expected, one for the users/name path, other for users/surname and a last one for users/phone
    assertEquals(3, onlyProjectSources(repo).size());
    assertEquals(1, onlyProjectSources(repo,"users.json/users/name").size());
  }

  public void testCsvSampleData() {
    myFixture.addFileToProject("sampledata/users.csv",
                               "name,surname,phone\n" +
                               "Name1,Surname1\n" +
                               "Name2,Surname2\n" +
                               "Name3,Surname3,555-00000");
    SampleDataResourceRepository repo = new SampleDataResourceRepository(myFacet);

    // Three different items are expected, one for the users/name path, other for users/surname and a last one for users/phone
    assertEquals(3, onlyProjectSources(repo).size());
    assertEquals(1, onlyProjectSources(repo,"users.csv/name").size());
  }

  public void testResolverCacheInvalidation() {
    @Language("XML")
    String layoutText = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    android:layout_width=\"match_parent\"\n" +
                        "    android:layout_height=\"match_parent\" />";

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

  // Temporarily disabled to debug the failed leak test
  public void ignorePredefinedSources() {
    // No project sources defined so only predefined sources should be available
    SampleDataResourceRepository repo = new SampleDataResourceRepository(myFacet);

    assertFalse(repo.getMap(null, ResourceType.SAMPLE_DATA, false).isEmpty());

    // Check that none of the items are empty or fail
    assertFalse(repo.getMap(null, ResourceType.SAMPLE_DATA, false).values().stream()
      .anyMatch(item -> item.getValueText().isEmpty()));
  }
}
