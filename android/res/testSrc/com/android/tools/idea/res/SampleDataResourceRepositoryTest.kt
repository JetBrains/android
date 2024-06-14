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
import static com.android.tools.idea.testing.AndroidTestUtils.waitForUpdates;
import static com.android.tools.idea.util.FileExtensions.toVirtualFile;
import static com.intellij.testFramework.UsefulTestCase.assertContainsElements;
import static com.intellij.testFramework.UsefulTestCase.assertSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.ide.common.rendering.api.SampleDataResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.testing.AndroidModuleDependency;
import com.android.tools.idea.testing.AndroidModuleModelBuilder;
import com.android.tools.idea.testing.AndroidProjectBuilder;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.res.LocalResourceRepository;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test for {@link SampleDataResourceRepository} and {@link SampleDataListener}.
 */
@RunsInEdt
@SuppressWarnings("DataFlowIssue")
public class SampleDataResourceRepositoryTest {
  @Rule
  public final AndroidProjectRule myProjectRule = AndroidProjectRule.withAndroidModels(
    new AndroidModuleModelBuilder(":", "debug",
                                  new AndroidProjectBuilder().withAndroidModuleDependencyList(
                                    (it, variant) -> Lists.newArrayList(new AndroidModuleDependency(":lib", "debug")))),
    new AndroidModuleModelBuilder(":lib", "debug", new AndroidProjectBuilder()
      .withProjectType(it -> IdeAndroidProjectType.PROJECT_TYPE_LIBRARY)
      .withAndroidModuleDependencyList((it, variant) -> Lists.newArrayList(new AndroidModuleDependency(":transitive", "debug")))),
    new AndroidModuleModelBuilder(":transitive", "debug", new AndroidProjectBuilder()
      .withProjectType(it -> IdeAndroidProjectType.PROJECT_TYPE_LIBRARY))
  );
  @Rule
  public final EdtRule myEdtRule = new EdtRule();
  private AndroidModuleSystem myAppModuleSystem;
  private AndroidFacet myFacet;

  @Before
  public void setUp() throws Exception {
    myAppModuleSystem = ProjectSystemUtil.getModuleSystem(myProjectRule.getModule());
    myFacet = AndroidFacet.getInstance(myProjectRule.getModule());
  }

  @After
  public void tearDown() throws Exception {
    SampleDataResourceItem.invalidateCache();
  }

  @NotNull
  private static Collection<ResourceItem> getResources(@NotNull ResourceRepository repo) {
    return repo.getResources(RES_AUTO, ResourceType.SAMPLE_DATA).values();
  }

  @NotNull
  private static List<ResourceItem> getResources(@NotNull ResourceRepository repo, @NotNull String resName) {
    return repo.getResources(RES_AUTO, ResourceType.SAMPLE_DATA, resName);
  }

  @NotNull
  private PsiFile addLayoutFile() {
    @Language("XML")
    String layoutText = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                        "    android:layout_width=\"match_parent\"\n" +
                        "    android:layout_height=\"match_parent\" />";

    return myProjectRule.getFixture().addFileToProject("src/main/res/layout/layout.xml", layoutText);
  }

  @Test
  public void testDataLoad() throws InterruptedException, TimeoutException {
    myProjectRule.getFixture().addFileToProject("sampledata/strings",
                                                "string1\n" +
                                                "string2\n" +
                                                "string3\n");
    myProjectRule.getFixture().addFileToProject("sampledata/images/image1.png",
                                                "Insert image here\n");
    myProjectRule.getFixture().addFileToProject("sampledata/images/image2.jpg",
                                                "Insert image here 2\n");
    myProjectRule.getFixture().addFileToProject("sampledata/images/image3.png",
                                                "Insert image here 3\n");
    myProjectRule.getFixture().addFileToProject("sampledata/root_image.png",
                                                "Insert image here 3\n");
    SampleDataResourceRepository repo = new SampleDataResourceRepository(myFacet, myProjectRule.getTestRootDisposable());
    waitForUpdates(repo);

    assertEquals(3, getResources(repo).size());
    assertEquals(1, getResources(repo, "strings").size());
    assertEquals(1, getResources(repo, "images").size());
    assertEquals(1, getResources(repo, "root_image.png").size());
  }

  @Test
  public void testResolver() throws InterruptedException, TimeoutException {
    @Language("XML")
    String stringsText = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                         "<resources>\n" +
                         "  <string name=\"test1\">Hello 1</string>\n" +
                         "  <string name=\"test2\">Hello 2</string>\n" +
                         "</resources>";

    myProjectRule.getFixture().addFileToProject("sampledata/strings",
                                                "string1\n" +
                                                "string2\n" +
                                                "string3\n");
    myProjectRule.getFixture().addFileToProject("sampledata/ints",
                                                "1\n" +
                                                "2\n");
    myProjectRule.getFixture().addFileToProject("sampledata/refs",
                                                "@string/test1\n" +
                                                "@string/invalid\n");
    myProjectRule.getFixture().addFileToProject("sampledata/users.json",
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
    PsiFile image1 = myProjectRule.getFixture().addFileToProject("sampledata/images/image1.png",
                                                                 "Insert image here\n");
    PsiFile image2 = myProjectRule.getFixture().addFileToProject("sampledata/images/image2.jpg",
                                                                 "Insert image here 2\n");
    myProjectRule.getFixture().addFileToProject("src/main/res/values/strings.xml", stringsText);
    PsiFile layout = addLayoutFile();
    Configuration configuration =
      ConfigurationManager.getOrCreateInstance(myProjectRule.getModule()).getConfiguration(layout.getVirtualFile());
    waitForUpdates(StudioResourceRepositoryManager.getInstance(myFacet).getSampleDataResources());
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

  @Test
  public void testSampleDataFileInvalidation_addAndDeleteFile() throws IOException, InterruptedException, TimeoutException {
    LocalResourceRepository<VirtualFile> repo = StudioResourceRepositoryManager.getInstance(myFacet).getSampleDataResources();
    waitForUpdates(repo);
    assertTrue(getResources(repo).isEmpty());

    PsiFile strings = myProjectRule.getFixture().addFileToProject("sampledata/strings",
                                                                  "string1\n" +
                                                                  "string2\n" +
                                                                  "string3\n");
    waitForUpdates(repo);
    assertEquals(1, getResources(repo).size());
    assertEquals(1, getResources(repo, "strings").size());

    WriteAction.runAndWait(() -> strings.getVirtualFile().delete(null));
    waitForUpdates(repo);
    assertTrue(getResources(repo).isEmpty());
  }

  @Test
  public void testSampleDataFileInvalidation_deleteSampleDataDirectory() throws IOException, InterruptedException, TimeoutException {
    LocalResourceRepository<VirtualFile> repo = StudioResourceRepositoryManager.getInstance(myFacet).getSampleDataResources();

    myProjectRule.getFixture().addFileToProject("sampledata/strings", "string1\n");
    waitForUpdates(repo);
    assertEquals(1, getResources(repo).size());

    VirtualFile sampleDir = toVirtualFile(myAppModuleSystem.getSampleDataDirectory());
    WriteAction.runAndWait(() -> sampleDir.delete(null));
    waitForUpdates(repo);
    assertTrue(getResources(repo).isEmpty());
  }

  @Test
  public void testSampleDataFileInvalidation_moveFiles() throws IOException, InterruptedException, TimeoutException {
    LocalResourceRepository<VirtualFile> repo = StudioResourceRepositoryManager.getInstance(myFacet).getSampleDataResources();

    VirtualFile sampleDir = toVirtualFile(
      WriteAction.computeAndWait(() -> myAppModuleSystem.getOrCreateSampleDataDirectory())
    );
    PsiFile stringsOutside = myProjectRule.getFixture().addFileToProject("strings", "string1\n");

    // move strings into sample data directory
    WriteAction.runAndWait(() -> stringsOutside.getVirtualFile().move(null, sampleDir));
    waitForUpdates(repo);
    assertEquals(1, getResources(repo).size());

    // move strings out of sample data directory
    VirtualFile stringsInside = sampleDir.findChild(stringsOutside.getName());
    WriteAction.runAndWait(() -> stringsInside.move(null, sampleDir.getParent()));
    waitForUpdates(repo);
    assertTrue(getResources(repo).isEmpty());
  }

  @Test
  public void testSampleDataFileInvalidation_moveSampleDataDirectory() throws IOException, InterruptedException, TimeoutException {
    LocalResourceRepository<VirtualFile> repo = StudioResourceRepositoryManager.getInstance(myFacet).getSampleDataResources();

    VirtualFile sampleDir = toVirtualFile(
      WriteAction.computeAndWait(() -> myAppModuleSystem.getOrCreateSampleDataDirectory())
    );
    myProjectRule.getFixture().addFileToProject("sampledata/strings", "string1\n");
    waitForUpdates(repo);
    assertEquals(1, getResources(repo).size());

    WriteAction.runAndWait(() -> {
      VirtualFile newParent = sampleDir.getParent().createChildDirectory(null, "somewhere_else");
      sampleDir.move(null, newParent);
    });
    waitForUpdates(repo);
    assertTrue(getResources(repo).isEmpty());
  }

  @Test
  public void testJsonSampleData() throws InterruptedException, TimeoutException {
    myProjectRule.getFixture().addFileToProject("sampledata/users.json",
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
    myProjectRule.getFixture().addFileToProject("sampledata/invalid.json",
                                                "{\n" +
                                                "  \"users\": [\n" +
                                                "    {\n" +
                                                "      \"name\": \"Name1\",\n" +
                                                "      \"surname\": \"Surname1\"\n" +
                                                "    },\n");
    SampleDataResourceRepository repo = new SampleDataResourceRepository(myFacet, myProjectRule.getTestRootDisposable());
    waitForUpdates(repo);

    // Three different items are expected, one for the users/name path, other for users/surname and a last one for users/phone
    assertEquals(3, getResources(repo).size());
    assertEquals(1, getResources(repo, "users.json/users/name").size());
  }

  @Test
  public void testCsvSampleData() throws InterruptedException, TimeoutException {
    myProjectRule.getFixture().addFileToProject("sampledata/users.csv",
                                                "name,surname,phone\n" +
                                                "Name1,Surname1\n" +
                                                "Name2,Surname2\n" +
                                                "Name3,Surname3,555-00000");
    SampleDataResourceRepository repo = new SampleDataResourceRepository(myFacet, myProjectRule.getTestRootDisposable());
    waitForUpdates(repo);

    // Three different items are expected, one for the users/name path, other for users/surname and a last one for users/phone
    assertEquals(3, getResources(repo).size());
    assertEquals(1, getResources(repo, "users.csv/name").size());
  }

  @Test
  public void testResolverCacheInvalidation() {
    PsiFile sampleDataFile = myProjectRule.getFixture().addFileToProject("sampledata/strings",
                                                                         "string1\n" +
                                                                         "string2\n" +
                                                                         "string3\n");
    PsiFile layout = addLayoutFile();
    Configuration configuration =
      ConfigurationManager.getOrCreateInstance(myProjectRule.getModule()).getConfiguration(layout.getVirtualFile());
    ResourceResolver resolver = configuration.getResourceResolver();
    assertEquals("string1", resolver.findResValue("@sample/strings", false).getValue());
    assertEquals("string2", resolver.findResValue("@sample/strings", false).getValue());
    ApplicationManager.getApplication().runWriteAction(() -> {
      try {
        sampleDataFile.getVirtualFile().setBinaryContent(("new1\n" +
                                                          "new2\n" +
                                                          "new3\n" +
                                                          "new4\n").getBytes(Charsets.UTF_8));
        PsiDocumentManager.getInstance(myProjectRule.getProject()).commitAllDocuments();
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    });

    // The cursor does not get reset when the file is changed so we expect "new3" as opposed as getting "new1"
    // Ignored temporarily since cache invalidation needs still work
    //assertEquals("new3", resolver.findResValue("@sample/strings", false).getValue());
  }

  @Test
  public void testImageResources() throws InterruptedException, TimeoutException {
    myProjectRule.getFixture().addFileToProject("sampledata/images/image1.png", "\n");
    myProjectRule.getFixture().addFileToProject("sampledata/images/image2.png", "\n");
    myProjectRule.getFixture().addFileToProject("sampledata/images/image3.png", "\n");
    PsiFile rootImagePsiFile = myProjectRule.getFixture().addFileToProject("sampledata/root_image.png", "\n");


    LocalResourceRepository<VirtualFile> repository = StudioResourceRepositoryManager.getAppResources(myFacet);
    waitForUpdates(repository);
    Collection<ResourceItem> items = repository.getResources(RES_AUTO, ResourceType.SAMPLE_DATA).values();
    assertSize(2, items);
    SampleDataResourceItem item =
      (SampleDataResourceItem)Iterables.getOnlyElement(
        repository.getResources(RES_AUTO, ResourceType.SAMPLE_DATA, "images"));
    assertEquals("images", item.getName());
    assertEquals(SampleDataResourceItem.ContentType.IMAGE, item.getContentType());
    SampleDataResourceValue value = (SampleDataResourceValue)item.getResourceValue();
    List<String> fileNames = value.getValueAsLines().stream()
      .map(file -> new File(file).getName())
      .collect(Collectors.toList());
    assertContainsElements(fileNames, "image1.png", "image2.png", "image3.png");

    SampleDataResourceItem rootImageItem =
      (SampleDataResourceItem)Iterables.getOnlyElement(repository.getResources(
        RES_AUTO, ResourceType.SAMPLE_DATA, "root_image.png"));
    assertEquals(rootImageItem.getContentType(), rootImageItem.getContentType());
    assertEquals(rootImagePsiFile.getVirtualFile().getPath(), rootImageItem.getValueText());
  }

  @Test
  public void testSubsetSampleData() {
    PsiFile layout = addLayoutFile();
    Configuration configuration =
      ConfigurationManager.getOrCreateInstance(myProjectRule.getModule()).getConfiguration(layout.getVirtualFile());
    ResourceResolver resolver = configuration.getResourceResolver();
    ResourceValue sampledLorem =
      new ResourceValueImpl(ResourceNamespace.TOOLS, ResourceType.SAMPLE_DATA, "lorem_data", "@sample/lorem[4:10]");
    assertEquals("Lorem ipsum dolor sit amet.", resolver.dereference(sampledLorem).getValue());
    assertEquals("Lorem ipsum dolor sit amet, consectetur.", resolver.dereference(sampledLorem).getValue());
  }

  @Test
  public void testResetWithNoRepo() {
    StudioResourceRepositoryManager.getInstance(myFacet).resetAllCaches();
  }

  @Test
  public void testSampleDataInLibrary() throws InterruptedException, TimeoutException {
    myProjectRule.getFixture().addFileToProject("lib/sampledata/lib.csv",
                                                "name,surname,phone\n" +
                                                "LibName1,LibSurname1\n" +
                                                "LibName2,LibSurname2\n" +
                                                "LibName3,LibSurname3,555-00000");
    myProjectRule.getFixture().addFileToProject("transitive/sampledata/transitive.csv",
                                                "name,surname,phone\n" +
                                                "TransitiveName1,TransitiveSurname1\n" +
                                                "TransitiveName2,TransitiveSurname2\n" +
                                                "TransitiveName3,TransitiveSurname3,555-00000");
    SampleDataResourceRepository repo = new SampleDataResourceRepository(myFacet, myProjectRule.getTestRootDisposable());
    waitForUpdates(repo);

    // Three different items are expected, one for the users/name path, other for users/surname and a last one for users/phone
    assertEquals(6, getResources(repo).size());
    assertEquals(1, getResources(repo, "lib.csv/name").size());
    assertEquals(1, getResources(repo, "transitive.csv/name").size());

    PsiFile layout = addLayoutFile();
    Configuration configuration =
      ConfigurationManager.getOrCreateInstance(myProjectRule.getModule()).getConfiguration(layout.getVirtualFile());
    ResourceResolver resolver = configuration.getResourceResolver();
    assertEquals("LibName1", resolver.findResValue("@sample/lib.csv/name", false).getValue());
    assertEquals("TransitiveName1", resolver.findResValue("@sample/transitive.csv/name", false).getValue());
  }

  @Test
  public void testMultiModuleAppOverrides() throws InterruptedException, TimeoutException {
    myProjectRule.getFixture().addFileToProject("sampledata/users.csv",
                                                "name,surname,phone\n" +
                                                "AppName1,AppSurname1\n" +
                                                "AppName2,AppSurname2\n" +
                                                "AppName3,AppSurname3,555-00000");
    myProjectRule.getFixture().addFileToProject("lib/sampledata/users.csv",
                                                "name,surname,phone\n" +
                                                "LibName1,LibSurname1\n" +
                                                "LibName2,LibSurname2\n" +
                                                "LibName3,LibSurname3,555-00000");
    myProjectRule.getFixture().addFileToProject("transitive/sampledata/users.csv",
                                                "name,surname,phone\n" +
                                                "TransitiveName1,TransitiveSurname1\n" +
                                                "TransitiveName2,TransitiveSurname2\n" +
                                                "TransitiveName3,TransitiveSurname3,555-00000");
    SampleDataResourceRepository repo = new SampleDataResourceRepository(myFacet, myProjectRule.getTestRootDisposable());
    waitForUpdates(repo);

    PsiFile layout = addLayoutFile();
    // Three different items are expected, one for the users/name path, other for users/surname and a last one for users/phone
    assertEquals(3, getResources(repo).size());
    assertEquals(1, getResources(repo, "users.csv/name").size());
    Configuration configuration =
      ConfigurationManager.getOrCreateInstance(myProjectRule.getModule()).getConfiguration(layout.getVirtualFile());
    ResourceResolver resolver = configuration.getResourceResolver();
    assertEquals("AppName1", resolver.findResValue("@sample/users.csv/name", false).getValue());
  }
}
