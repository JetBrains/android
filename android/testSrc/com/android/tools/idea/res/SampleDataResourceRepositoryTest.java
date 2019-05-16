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

import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.tools.idea.util.FileExtensions.toVirtualFile;
import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.ide.common.rendering.api.SampleDataResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Test for {@link SampleDataResourceRepository}.
 */
public class SampleDataResourceRepositoryTest extends AndroidTestCase {
  private AndroidModuleSystem myModuleSystem;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myModuleSystem = ProjectSystemUtil.getModuleSystem(myModule);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      SampleDataResourceItem.invalidateCache();
    } finally {
      super.tearDown();
    }
  }

  @NotNull
  private static Collection<ResourceItem> getResources(@NotNull SampleDataResourceRepository repo) {
    return repo.getResources(RES_AUTO, ResourceType.SAMPLE_DATA).values();
  }

  @NotNull
  private static List<ResourceItem> getResources(@NotNull SampleDataResourceRepository repo, @NotNull String resName) {
    return repo.getResources(RES_AUTO, ResourceType.SAMPLE_DATA, resName);
  }

  public void testGetInstance() {
    SampleDataResourceRepository repository = SampleDataResourceRepository.getInstance(myFacet);
    // We should return a cached instance of the resource repository..
    assertThat(SampleDataResourceRepository.getInstance(myFacet)).isSameAs(repository);

    // ..until we refresh the layout or sync, at which point another call to
    // getInstance() should give us a newly-constructed repository and not the disposed one.
    SampleDataResourceRepository.SampleDataRepositoryManager.getInstance(myFacet).reset();
    assertThat(SampleDataResourceRepository.getInstance(myFacet)).isNotSameAs(repository);
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
    SampleDataResourceRepository repo = SampleDataResourceRepository.getInstance(myFacet);

    assertEquals(2, getResources(repo).size());
    assertEquals(1, getResources(repo, "strings").size());
    assertEquals(1, getResources(repo, "images").size());
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
                               // language="JSON"
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
    ResourceReference reference = new ResourceReference(RES_AUTO, ResourceType.SAMPLE_DATA, "strings");
    assertEquals("string2", resolver.getResolvedResource(reference).getValue());
    assertEquals("1", resolver.findResValue("@sample/ints", false).getValue());
    assertTrue(imagePaths.contains(resolver.findResValue("@sample/images", false).getValue()));

    // Check reference resolution
    assertEquals("Hello 1", resolver.resolveResValue(
        new ResourceValueImpl(RES_AUTO, ResourceType.STRING, "test", "@sample/refs")).getValue());
    // @string/invalid does not exist so the sample data will just return the unresolved reference
    assertEquals("@string/invalid", resolver.resolveResValue(
        new ResourceValueImpl(RES_AUTO, ResourceType.STRING, "test", "@sample/refs")).getValue());

    // Check indexing (all calls should return the same)
    assertEquals("Name2", resolver.findResValue("@sample/users.json/users/name[1]", false).getValue());
    assertEquals("Name2", resolver.findResValue("@sample/users.json/users/name[1]", false).getValue());


    assertNull(resolver.findResValue("@sample/invalid", false));

    ResourceReference elementRef = new ResourceReference(RES_AUTO, ResourceType.SAMPLE_DATA, "strings[1]");
    assertNotNull(resolver.getResolvedResource(elementRef));
  }

  public void testSampleDataFileInvalidation_addAndDeleteFile() throws IOException {
    SampleDataResourceRepository repo = SampleDataResourceRepository.getInstance(myFacet);
    assertTrue(getResources(repo).isEmpty());

    PsiFile strings = myFixture.addFileToProject("sampledata/strings",
                               "string1\n" +
                               "string2\n" +
                               "string3\n");
    assertEquals(1, getResources(repo).size());
    assertEquals(1, getResources(repo, "strings").size());

    WriteAction.runAndWait(() -> strings.getVirtualFile().delete(null));
    assertTrue(getResources(repo).isEmpty());
  }

  public void testSampleDataFileInvalidation_deleteSampleDataDirectory() throws IOException {
    SampleDataResourceRepository repo = SampleDataResourceRepository.getInstance(myFacet);

    myFixture.addFileToProject("sampledata/strings", "string1\n");
    VirtualFile sampleDir = toVirtualFile(myModuleSystem.getSampleDataDirectory());
    assertEquals(1, getResources(repo).size());

    WriteAction.runAndWait(() -> sampleDir.delete(null));
    assertTrue(getResources(repo).isEmpty());
  }

  public void testSampleDataFileInvalidation_moveFiles() throws IOException {
    SampleDataResourceRepository repo = SampleDataResourceRepository.getInstance(myFacet);

    VirtualFile sampleDir = toVirtualFile(
      WriteAction.computeAndWait(() -> myModuleSystem.getOrCreateSampleDataDirectory())
    );
    PsiFile stringsOutside = myFixture.addFileToProject("strings", "string1\n");

    // move strings into sample data directory
    WriteAction.runAndWait(() -> stringsOutside.getVirtualFile().move(null, sampleDir));
    assertEquals(1, getResources(repo).size());

    // move strings out of sample data directory
    VirtualFile stringsInside = sampleDir.findChild(stringsOutside.getName());
    WriteAction.runAndWait(() -> stringsInside.move(null, sampleDir.getParent()));
    assertTrue(getResources(repo).isEmpty());
  }

  public void testSampleDataFileInvalidation_moveSampleDataDirectory() throws IOException {
    SampleDataResourceRepository repo = SampleDataResourceRepository.getInstance(myFacet);

    VirtualFile sampleDir = toVirtualFile(
      WriteAction.computeAndWait(() -> myModuleSystem.getOrCreateSampleDataDirectory())
    );
    myFixture.addFileToProject("sampledata/strings", "string1\n");
    assertEquals(1, getResources(repo).size());

    WriteAction.runAndWait(() -> {
      VirtualFile newParent = sampleDir.getParent().createChildDirectory(null, "somewhere_else");
      sampleDir.move(null, newParent);
    });
    assertTrue(getResources(repo).isEmpty());
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
    SampleDataResourceRepository repo = SampleDataResourceRepository.getInstance(myFacet);

    // Three different items are expected, one for the users/name path, other for users/surname and a last one for users/phone
    assertEquals(3, getResources(repo).size());
    assertEquals(1, getResources(repo, "users.json/users/name").size());
  }

  public void testCsvSampleData() {
    myFixture.addFileToProject("sampledata/users.csv",
                               "name,surname,phone\n" +
                               "Name1,Surname1\n" +
                               "Name2,Surname2\n" +
                               "Name3,Surname3,555-00000");
    SampleDataResourceRepository repo = SampleDataResourceRepository.getInstance(myFacet);

    // Three different items are expected, one for the users/name path, other for users/surname and a last one for users/phone
    assertEquals(3, getResources(repo).size());
    assertEquals(1, getResources(repo, "users.csv/name").size());
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
    assertEquals("string2", resolver.findResValue("@sample/strings", false).getValue());
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

  public void testImageResources() {
    myFixture.addFileToProject("sampledata/images/image1.png", "\n");
    myFixture.addFileToProject("sampledata/images/image2.png", "\n");
    myFixture.addFileToProject("sampledata/images/image3.png", "\n");

    LocalResourceRepository repository = ResourceRepositoryManager.getAppResources(myFacet);
    Collection<ResourceItem> items = repository.getResources(RES_AUTO, ResourceType.SAMPLE_DATA).values();
    assertSize(1, items);
    SampleDataResourceItem item = (SampleDataResourceItem)Iterables.getOnlyElement(items);
    assertEquals("images", item.getName());
    assertEquals(SampleDataResourceItem.ContentType.IMAGE, item.getContentType());
    SampleDataResourceValue value = (SampleDataResourceValue)item.getResourceValue();
    List<String> fileNames = value.getValueAsLines().stream()
         .map(file -> new File(file).getName())
         .collect(Collectors.toList());
    assertContainsElements(fileNames, "image1.png", "image2.png", "image3.png");
  }

  public void testSubsetSampleData() {
    @Language("XML")
    String layoutText = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    android:layout_width=\"match_parent\"\n" +
                        "    android:layout_height=\"match_parent\" />";

    PsiFile layout = myFixture.addFileToProject("res/layout/layout.xml", layoutText);
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layout.getVirtualFile());
    ResourceResolver resolver = configuration.getResourceResolver();
    ResourceValue sampledLorem =
        new ResourceValueImpl(ResourceNamespace.TOOLS, ResourceType.SAMPLE_DATA, "lorem_data", "@sample/lorem[4:10]");
    assertEquals("Lorem ipsum dolor sit amet.", resolver.dereference(sampledLorem).getValue());
    assertEquals("Lorem ipsum dolor sit amet, consectetur.", resolver.dereference(sampledLorem).getValue());
  }

  public void testResetWithNoRepo() {
    @SuppressWarnings("unused") SampleDataResourceRepository.SampleDataRepositoryManager manager =
      SampleDataResourceRepository.SampleDataRepositoryManager.getInstance(myFacet);

    ResourceRepositoryManager.getInstance(myFacet).resetAllCaches();
  }
}
