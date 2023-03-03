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

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ID_PREFIX;
import static com.android.SdkConstants.MotionSceneTags.CONSTRAINT;
import static com.android.SdkConstants.MotionSceneTags.CONSTRAINT_SET;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.SdkConstants.PREFIX_RESOURCE_REF;
import static com.android.ide.common.rendering.api.ResourceNamespace.ANDROID;
import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.resources.ResourceAsserts.assertThat;
import static com.android.tools.idea.concurrency.AsyncTestUtils.waitForCondition;
import static com.android.tools.idea.testing.AndroidTestUtils.moveCaret;
import static com.android.tools.idea.testing.AndroidTestUtils.waitForUpdates;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

import com.android.ide.common.rendering.api.ArrayResourceValue;
import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.DensityBasedResourceValue;
import com.android.ide.common.rendering.api.PluralsResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.rendering.api.StyleableResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceRepositoryUtil;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.testutils.TestUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.rendering.DrawableRenderer;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.daemon.impl.MockWolfTheProblemSolver;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link ResourceFolderRepository}.
 *
 * TODO: Add XmlTags with PSI events to check childAdded etc working correctly. Currently they mostly seem to generate big rescans.
 * TODO: Test moving from one resource folder to another. Should be simulated as an add in one folder and a remove in another.
 *       Check that in the ModuleResourceRepository test.
 * TODO: Test that adding and removing characters inside a {@code <string>} element does not cause a full rescan.
 */
@SuppressWarnings({"UnusedDeclaration", "SpellCheckingInspection", "RedundantThrows"})
public class ResourceFolderRepositoryTest extends AndroidTestCase {
  private static final String LAYOUT1 = "resourceRepository/layout.xml";
  private static final String LAYOUT2 = "resourceRepository/layout2.xml";
  private static final String LAYOUT_ID_SCAN = "resourceRepository/layout_for_id_scan.xml";
  private static final String LAYOUT_WITHOUT_IDS = "resourceRepository/layout_without_ids.xml";
  private static final String LAYOUT_WITH_ONE_ID = "resourceRepository/layout_with_one_id.xml";
  private static final String LAYOUT_WITH_DATA_BINDING = "resourceRepository/layout_with_data_binding.xml";
  private static final String VALUES1 = "resourceRepository/values.xml";
  private static final String VALUES_EMPTY = "resourceRepository/empty.xml";
  private static final String VALUES_WITH_DUPES = "resourceRepository/values_with_duplicates.xml";
  private static final String VALUES_WITH_BAD_NAME = "resourceRepository/values_with_bad_name.xml";
  private static final String VALUES_WITH_INCORRECT_ARRAY = "resourceRepository/array_without_name.xml";
  private static final String XLIFF = "resourceRepository/xliff.xml";
  private static final String STRINGS = "resourceRepository/strings.xml";
  private static final String DRAWABLE = "resourceRepository/logo.png";
  private static final String DRAWABLE_BLUE = "resourceRepository/blue.png";
  private static final String DRAWABLE_RED = "resourceRepository/red.png";
  private static final Dimension COLORED_DRAWABLE_SIZE = new Dimension(3, 3);
  private static final String DRAWABLE_ID_SCAN = "resourceRepository/drawable_for_id_scan.xml";
  private static final String COLOR_STATELIST = "resourceRepository/statelist.xml";
  private static final String MOTION_SCENE = "resourceRepository/motion_scene.xml";

  private ResourceFolderRepositoryFileCache myOldFileCacheService;
  private ResourceFolderRegistry myRegistry;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // Use a file cache that has per-test root directories instead of sharing the system directory.
    ResourceFolderRepositoryFileCache cache = new ResourceFolderRepositoryFileCacheImpl(Paths.get(myFixture.getTempDirPath())) {
      @Override
      public @Nullable ResourceFolderRepositoryCachingData getCachingData(@NotNull Project project,
                                                                          @NotNull VirtualFile resourceDir,
                                                                          @Nullable Executor cacheCreationExecutor) {
        // Use direct executor to make cache creation synchronous.
        return super.getCachingData(project, resourceDir, directExecutor());
      }
    };
    ServiceContainerUtil.replaceService(getApplication(), ResourceFolderRepositoryFileCache.class, cache, getTestRootDisposable());
    myRegistry = ResourceFolderRegistry.getInstance(getProject());
    Path file = cache.getCachingData(getProject(), getResourceDirectory(), null).getCacheFile();
    Files.deleteIfExists(file);
  }

  private @NotNull VirtualFile getResourceDirectory() {
    List<VirtualFile> resourceDirectories = ResourceFolderManager.getInstance(myFacet).getFolders();
    assertNotNull(resourceDirectories);
    assertSize(1, resourceDirectories);
    return resourceDirectories.get(0);
  }

  private @NotNull ResourceFolderRepository createRepository(boolean createCache) {
    VirtualFile dir = getResourceDirectory();
    ResourceNamespace namespace = StudioResourceRepositoryManager.getInstance(myFacet).getNamespace();
    ResourceFolderRepositoryCachingData cachingData =
        ResourceFolderRepositoryFileCacheService.get().getCachingData(getProject(), dir, createCache ? directExecutor() : null);
    return ResourceFolderRepository.create(myFacet, dir, namespace, cachingData);
  }

  private @NotNull ResourceFolderRepository createRegisteredRepository() {
    VirtualFile dir = getResourceDirectory();
    return myRegistry.get(myFacet, dir);
  }

  private @NotNull VirtualFile getProjectDir() {
    return Objects.requireNonNull(ProjectUtil.guessProjectDir(getProject()));
  }

  /**
   * Checks that two repositories contain the same data.
   */
  private static void assertContainSameData(@NotNull AndroidFacet facet,
                                            @NotNull ResourceFolderRepository expected,
                                            @NotNull ResourceFolderRepository actual) {
    assertThat(expected.getResourceDir()).isEqualTo(actual.getResourceDir());

    List<ResourceItem> expectedItems = expected.getAllResources();
    List<ResourceItem> actualItems = actual.getAllResources();
    for (int i = 0; i < expectedItems.size() || i < actualItems.size(); i++) {
      if (i >= expectedItems.size()) {
        fail("Unexpected item " + actualItems.get(i));
      }
      if (i >= actualItems.size()) {
        fail("Missing item " + expectedItems.get(i));
      }
      ResourceItem expectedItem = expectedItems.get(i);
      ResourceItem actualItem = actualItems.get(i);
      assertThat(actualItem).isEquivalentTo(expectedItem);
      ResourceValue expectedValue = expectedItem.getResourceValue();
      ResourceValue actualValue = actualItem.getResourceValue();
      assertThat(actualValue).isEquivalentTo(expectedValue);
    }
  }

  private static @Nullable XmlTag findTagById(@NotNull PsiFile file, @NotNull String id) {
    assertFalse(id.startsWith(PREFIX_RESOURCE_REF)); // Just the id.
    String newId = NEW_ID_PREFIX + id;
    String oldId = ID_PREFIX + id;
    for (XmlTag tag : PsiTreeUtil.findChildrenOfType(file, XmlTag.class)) {
      String tagId = tag.getAttributeValue(ATTR_ID, ANDROID_URI);
      if (newId.equals(tagId) || oldId.equals(tagId)) {
        return tag;
      }
    }
    return null;
  }

  private static @Nullable XmlTag findTagByName(@NotNull PsiFile file, @SuppressWarnings("SameParameterValue") @NotNull String name) {
    for (XmlTag tag : PsiTreeUtil.findChildrenOfType(file, XmlTag.class)) {
      String tagName = tag.getAttributeValue(ATTR_NAME);
      if (name.equals(tagName)) {
        return tag;
      }
    }
    return null;
  }

  private static @Nullable AttrResourceValue findAttr(@NotNull StyleableResourceValue styleable,
                                                      @NotNull String attrName,
                                                      @NotNull ResourceRepository repository) {
    for (AttrResourceValue attr : styleable.getAllAttributes()) {
      if (attr.getName().equals(attrName)) {
        // An attribute inside styleable may be a reference, resolve it.
        List<ResourceItem> items = repository.getResources(attr.getNamespace(), attr.getResourceType(), attr.getName());
        if (items.isEmpty()) {
          return attr;
        }
        if (items.size() != 1) {
          return null;
        }
        return (AttrResourceValue)items.get(0).getResourceValue();
      }
    }
    return null;
  }

  private static void checkDefinedItems(@NotNull StyleResourceValue style, @NotNull String... attributes) {
    assertSameElements(Collections2.transform(style.getDefinedItems(), StyleItemResourceValue::getAttrName), attributes);
  }

  private void type(@NotNull String place, @NotNull String toType) {
    moveCaret(myFixture, place);
    myFixture.type(toType);
    PsiDocumentManager.getInstance(getProject()).commitDocument(myFixture.getEditor().getDocument());
  }

  /** Commits all documents and waits for the given resource repository to finish currently pending updates. */
  private void commitAndWaitForUpdates(@NotNull LocalResourceRepository repository) throws InterruptedException, TimeoutException {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    waitForUpdates(repository);
  }

  public void testComputeResourceStrings() {
    // Tests the handling of markup to raw strings
    // For example, for this strings definition
    //   <string name="title_template_step">Step <xliff:g id="step_number">%1$d</xliff:g>: Lorem Ipsum</string>
    // the resource value should be
    //   Step %1$d: Lorem Ipsum
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    List<ResourceItem> labelList = repository.getResources(RES_AUTO, ResourceType.STRING, "title_template_step");
    assertNotNull(labelList);
    assertEquals(1, labelList.size());
    ResourceItem label = labelList.get(0);
    ResourceValue resourceValue = label.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Step ${step_number}: Lorem Ipsum", resourceValue.getValue()); // In the file, there's whitespace unlike example above

    // Test unicode escape handling: <string name="ellipsis">Here it is: \u2026!</string>
    labelList = repository.getResources(RES_AUTO, ResourceType.STRING, "ellipsis");
    assertNotNull(labelList);
    assertEquals(1, labelList.size());
    label = labelList.get(0);
    resourceValue = label.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Here it is: \u2026!", resourceValue.getValue());

    // Make sure we pick up id's defined using types
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next2"));
  }

  /** Tests handling of xliff markup. */
  public void testXliff() {
    VirtualFile file1 = myFixture.copyFileToProject(XLIFF, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertEquals("Share your score of (1337) with (Bluetooth)!",
                 repository.getResources(RES_AUTO, ResourceType.STRING, "share_with_application").get(0).getResourceValue().getValue());
    assertEquals("Call ${name}",
                 repository.getResources(RES_AUTO, ResourceType.STRING, "description_call").get(0).getResourceValue().getValue());
    assertEquals("(42) mins (28) secs",
                 repository.getResources(RES_AUTO, ResourceType.STRING, "callDetailsDurationFormat").get(0).getResourceValue().getValue());
    assertEquals("${number_of_sessions} sessions removed from your schedule",
                 repository.getResources(RES_AUTO, ResourceType.STRING, "other").get(0).getResourceValue().getValue());
  }

  public void testInitialCreate() {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");

    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    Collection<String> layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(2, layouts.size());

    assertNotNull(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));
    assertNotNull(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout2"));
  }

  public void testArrayWithNoName() throws Exception {
    VirtualFile virtualFile = myFixture.copyFileToProject(VALUES_WITH_INCORRECT_ARRAY, "res/values/array_without_name.xml");

    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    Collection<String> arrays = repository.getResources(RES_AUTO, ResourceType.ARRAY).keySet();
    assertThat(arrays).isEmpty();

    Collection<String> strings = repository.getResources(RES_AUTO, ResourceType.STRING).keySet();
    assertThat(strings).isNotEmpty();

    // Add the name for the array and expect the repository to update.
    myFixture.openFileInEditor(virtualFile);

    long generation = repository.getModificationCount();
    long rescans = repository.getFileRescans();
    type("<array |", "name=\"fooArray\"");
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    arrays = repository.getResources(RES_AUTO, ResourceType.ARRAY).keySet();
    assertThat(arrays).containsExactly("fooArray");
  }

  public void testAddFile() throws Exception {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    VirtualFile layout2 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    Collection<String> layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(2, layouts.size());
    assertNotNull(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));
    assertNotNull(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout2"));

    long generation = repository.getModificationCount();
    // Change modification time of the layout2 file without changing its contents.
    Path file = Paths.get(layout2.getPath());
    Files.setLastModifiedTime(file, FileTime.from(Files.getLastModifiedTime(file).toInstant().plusSeconds(1)));
    VfsUtil.markDirtyAndRefresh(false, false, false, layout2);
    assertThat(repository.getModificationCount()).isEqualTo(generation); // no changes in file: no new generation

    generation = repository.getModificationCount();
    VirtualFile file3 = myFixture.copyFileToProject(LAYOUT1, "res/layout-xlarge-land/layout3.xml");
    PsiFile psiFile3 = PsiManager.getInstance(getProject()).findFile(file3);
    assertNotNull(psiFile3);
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(3, layouts.size());
  }

  public void testAddUnrelatedFile() throws Exception {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");
    ResourceFolderRepository repository = createRegisteredRepository();
    Multimap<String, ResourceItem> layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT);
    assertThat(layouts).hasSize(2);
    assertThat(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1")).hasSize(1);
    assertThat(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout2")).hasSize(1);

    long generation = repository.getModificationCount();
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.unrelated");
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isEqualTo(generation); // no changes in file: no new generation
    assertThat(layouts).hasSize(2);

    myFixture.copyFileToProject(LAYOUT1, "src/layout/layout2.xml"); // not a resource folder
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isEqualTo(generation); // no changes in file: no new generation
    assertThat(layouts).hasSize(2);
  }

  public void testDeleteResourceFile() throws Exception {
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    Collection<String> drawables = repository.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertEquals(drawables.toString(), 0, drawables.size());
    long generation = repository.getModificationCount();
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/drawable-mdpi/foo.png");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Delete a file and make sure the item is removed from the repository (and modification count bumped).
    drawables = repository.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertEquals(1, drawables.size());
    generation = repository.getModificationCount();
    assertEquals("foo", drawables.iterator().next());
    WriteCommandAction.runWriteCommandAction(null, psiFile1::delete);
    waitForUpdates(repository);
    drawables = repository.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertEquals(0, drawables.size());
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Try adding and then deleting a drawable file with IDs too.
    generation = repository.getModificationCount();

    // TODO: Make this work with copyFileToProject.
    // copyFileToProject now creates an empty file first which triggers a childAdded event w/ an empty file (so no IDs are parsed).
    // It then it copies the contents over and triggers a childrenChanged event but in a way that is unlike typing in content,
    // so we don't re-parse the XML for IDs.
    //VirtualFile file2 = myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/drawable-v21/drawable_with_ids.xml");
    File fromFile = new File(myFixture.getTestDataPath(), DRAWABLE_ID_SCAN);
    assertExists(fromFile);
    File targetFile = new File(myFixture.getTempDirFixture().getTempDirPath(), "res/drawable-v21/drawable_with_ids.xml");
    assertFalse(targetFile.exists());
    FileUtil.copy(fromFile, targetFile);
    VirtualFile file2 = VfsUtil.findFileByIoFile(targetFile, true);
    assertNotNull(file2);
    waitForUpdates(repository);

    PsiFile psiFile2 = PsiManager.getInstance(getProject()).findFile(file2);
    assertNotNull(psiFile2);
    drawables = repository.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertEquals(1, drawables.size());
    Collection<String> ids = repository.getResources(RES_AUTO, ResourceType.ID).keySet();
    assertContainsElements(ids, "focused_state", "default_state", "pressed_state");
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, psiFile2::delete);

    waitForUpdates(repository);
    drawables = repository.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertEquals(0, drawables.size());
    ids = repository.getResources(RES_AUTO, ResourceType.ID).keySet();
    assertEquals(0, ids.size());
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
  }

  public void testDeleteResourceDirectory() throws Exception {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");
    VirtualFile file3 = myFixture.copyFileToProject(LAYOUT1, "res/layout-xlarge-land/layout3.xml");
    PsiFile psiFile3 = PsiManager.getInstance(getProject()).findFile(file3);
    assertNotNull(psiFile3);

    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    // Try deleting a whole resource directory and ensure we remove the files within.
    long generation = repository.getModificationCount();
    Collection<String> layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(3, layouts.size());
    PsiDirectory directory = psiFile3.getContainingDirectory();
    assertNotNull(directory);
    WriteCommandAction.runWriteCommandAction(null, directory::delete);
    waitForUpdates(repository);
    layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(2, layouts.size());
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
  }

  public void testDeleteRemainderResourceIDs() throws Exception {
    VirtualFile file = myFixture.copyFileToProject(LAYOUT_ID_SCAN, "res/layout-xlarge-land/layout.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile1);

    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    long generation = repository.getModificationCount();
    Collection<String> layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(1, layouts.size());
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
    // nonExistent may be handled slightly different from other IDs, since it is introduced by a
    // non-ID-attribute="@+id/nonExistent", but the file does not define a tag with id="@id/nonExistent".
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "nonExistent"));

    WriteCommandAction.runWriteCommandAction(null, psiFile1::delete);
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEmpty(layouts);
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "nonExistent"));
  }

  public void testRenameLayoutFile() throws Exception {
    VirtualFile file2 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");

    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    // Check renames.
    //  Rename layout file.
    long generation = repository.getModificationCount();
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout2"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout2b"));

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          file2.rename(this, "layout2b.xml");
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout2b"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout2"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
  }

  public void testRenameLayoutFileToInvalid() throws Exception {
    VirtualFile file = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");

    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    // Check renames.
    //  Rename layout file.
    long generation = repository.getModificationCount();
    assertThat(repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet()).containsExactly("layout2");

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          file.rename(this, "layout-2.xml");
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    waitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet()).isEmpty();
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
  }

  public void testRenameDrawableFile() throws Exception {
    //  Rename drawable file.
    VirtualFile file5 = myFixture.copyFileToProject(LAYOUT1, "res/drawable-xhdpi/foo2.png");
    ResourceFolderRepository repository = createRegisteredRepository();

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo2"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo3"));
    ResourceItem item = getOnlyItem(repository, ResourceType.DRAWABLE, "foo2");
    assertTrue(item.getResourceValue() instanceof DensityBasedResourceValue);
    DensityBasedResourceValue rv = (DensityBasedResourceValue)item.getResourceValue();
    assertNotNull(rv);
    assertSame(Density.XHIGH, rv.getResourceDensity());

    long generation = repository.getModificationCount();
    int layoutlibCacheFlushes = repository.getLayoutlibCacheFlushes();

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          file5.rename(this, "foo3.png");
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo3"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo2"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getLayoutlibCacheFlushes()).isGreaterThan(layoutlibCacheFlushes);
  }

  public void testRenameResourceBackedByPsiResourceItem() throws Exception {
    // We first do a normal rename which will also convert the ResourceItem into a PsiResourceItem
    //  Rename drawable file.
    VirtualFile file5 = myFixture.copyFileToProject(LAYOUT1, "res/drawable-xhdpi/foo2.png");
    ResourceFolderRepository repository = createRegisteredRepository();

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo2"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo3"));
    ResourceItem item = getOnlyItem(repository, ResourceType.DRAWABLE, "foo2");
    assertTrue(item.getResourceValue() instanceof DensityBasedResourceValue);
    DensityBasedResourceValue rv = (DensityBasedResourceValue)item.getResourceValue();
    assertNotNull(rv);
    assertSame(Density.XHIGH, rv.getResourceDensity());

    long generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
          file5.rename(this, "foo3.png");
        }
        catch (IOException e) {
          fail(e.toString());
        }
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo3"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo2"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // At this point the item2 is a PsiResourceItem so we try to rename a second time
    // to check that the new name is propagated to the repository repositories.
    ResourceItem item2 = getOnlyItem(repository, ResourceType.DRAWABLE, "foo3");
    assertInstanceOf(item2, PsiResourceItem.class);
    assertTrue(item2.getResourceValue() instanceof DensityBasedResourceValue);

    generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
          file5.rename(this, "foo4.png");
        }
        catch (IOException e) {
          fail(e.toString());
      }
    });
    waitForUpdates(repository);
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo3"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo4"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
  }

  public void testRenameValueFile() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step"));

    List<ResourceItem> items = repository.getResources(RES_AUTO, ResourceType.STRING, "title_template_step");
    assertNotNull(items);
    assertEquals(1, items.size());
    ResourceItem item = items.get(0);
    assertEquals("myvalues.xml", item.getSource().getFileName());

    // We need to make sure there is a document. PsiVFSListener uses createFileCopyWithNewName
    // to populate the new Psi file with the old Psi file's content. However, by the time that runs,
    // the old file will already be physically moved to the new file. If it cannot find a cached
    // document for the old file, then it will make up an empty old Psi file and copy the empty content
    // to the new PsiFile.
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Renaming a value file should have no visible effect.
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          file1.rename(this, "renamedvalues.xml");
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step"));
    items = repository.getResources(RES_AUTO, ResourceType.STRING, "title_template_step");
    assertNotNull(items);
    assertEquals(1, items.size());
    item = items.get(0);
    assertEquals("renamedvalues.xml", item.getSource().getFileName());

    // TODO: Optimize this such that there's no modification change for this. It's tricky because
    //       for file names we get separate notification from the old file deletion (beforePropertyChanged)
    //       and the new file name (propertyChanged). (Note that I tried performing the rename via a
    //       setName operation on the PsiFile instead of at the raw VirtualFile level, but the resulting
    //       events were the same.)
    //assertThat(repository.getModificationCount()).isEqualTo(generation);
  }

  public void testRenameValueFileToInvalid() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step"));

    long generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          // After this rename, the values are no longer considered values since they're in an unrecognized file.
          file1.rename(this, "renamedvalues.badextension");
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step"));
  }

  private static ResourceItem getOnlyItem(@NotNull ResourceFolderRepository repository, @NotNull ResourceType type, @NotNull String name) {
    List<ResourceItem> items = repository.getResources(RES_AUTO, type, name);
    assertEquals(1, items.size());
    return items.get(0);
  }

  public void testMoveFileResourceFileToNewConfiguration() throws Exception {
    // Move a file-based resource file from one configuration to another; verify that
    // items are preserved, generation changed (since it can affect config matching),
    // and resource files updated.
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout-land/layout1.xml");
    VirtualFile file2 = myFixture.copyFileToProject(LAYOUT1, "res/layout-port/dummy.ignore");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    ResourceItem item = getOnlyItem(repository, ResourceType.LAYOUT, "layout1");
    assertEquals("land", item.getConfiguration().getQualifierString());
    ResourceItem idItem = getOnlyItem(repository, ResourceType.ID, "btn_title_refresh");
    assertEquals("layout-land", idItem.getSource().getParentFileName());

    long generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          // Move file from one location to another
          file1.move(this, file2.getParent());
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));
    item = getOnlyItem(repository, ResourceType.LAYOUT, "layout1");
    assertEquals("port", item.getConfiguration().getQualifierString());
    idItem = getOnlyItem(repository, ResourceType.ID, "btn_title_refresh");
    assertEquals("layout-port", idItem.getSource().getParentFileName());
  }

  public void testMoveValueResourceFileToNewConfiguration() throws Exception {
    // Move a value file from one configuration to another; verify that
    // items are preserved, generation changed (since it can affect config matching),
    // and resource files updated.
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values-en/layout1.xml");
    VirtualFile file2 = myFixture.copyFileToProject(VALUES1, "res/values-no/dummy.ignore");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    ResourceItem item = getOnlyItem(repository, ResourceType.STRING, "app_name");
    assertEquals("en", item.getConfiguration().getQualifierString());
    assertEquals("en", item.getConfiguration().getLocaleQualifier().getLanguage());
    assertEquals("Animations Demo", item.getResourceValue().getValue());

    long generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          // Move file from one location to another
          file1.move(this, file2.getParent());
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    item = getOnlyItem(repository, ResourceType.STRING, "app_name");
    assertEquals("no", item.getConfiguration().getQualifierString());
    assertEquals("no", item.getConfiguration().getLocaleQualifier().getLanguage());
    assertEquals("Animations Demo", item.getResourceValue().getValue());
  }

  public void testMoveResourceFileBetweenDensityFolders() throws Exception {
    // Regression test for https://code.google.com/p/android/issues/detail?id=61648
    // Make sure we flush resource values when reusing resource items incrementally

    // Move a file-based resource file from one configuration to another; verify that
    // items are preserved, generation changed (since it can affect config matching),
    // and resource files updated.
    VirtualFile file1 = myFixture.copyFileToProject(DRAWABLE, "res/drawable-mdpi/picture.png");
    VirtualFile file2 = myFixture.copyFileToProject(DRAWABLE, "res/drawable-hdpi/dummy.ignore");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    ResourceItem item = getOnlyItem(repository, ResourceType.DRAWABLE, "picture");
    assertEquals(Density.MEDIUM, item.getConfiguration().getDensityQualifier().getValue());
    ResourceValue resourceValue = item.getResourceValue();
    assertNotNull(resourceValue);
    String valuePath = resourceValue.getValue().replace(File.separatorChar, '/');
    assertTrue(valuePath, valuePath.endsWith("res/drawable-mdpi/picture.png"));

    long generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          // Move file from one location to another
          file1.move(this, file2.getParent());
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "picture"));
    item = getOnlyItem(repository, ResourceType.DRAWABLE, "picture");
    assertEquals(Density.HIGH, item.getConfiguration().getDensityQualifier().getValue());
    resourceValue = item.getResourceValue();
    assertNotNull(resourceValue);
    valuePath = resourceValue.getValue().replace(File.separatorChar, '/');
    assertTrue(valuePath, valuePath.endsWith("res/drawable-hdpi/picture.png"));
  }

  public void testMoveFileResourceFileToNewType() throws Exception {
    // Move a file resource file file from one folder to another, changing the type
    // (e.g. anim to animator), verify that resource types are updated
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    VirtualFile file2 = myFixture.copyFileToProject(LAYOUT1, "res/menu/dummy.ignore");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));

    long generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          file1.move(this, file2.getParent());
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.MENU, "layout1"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));
    ResourceItem item = getOnlyItem(repository, ResourceType.MENU, "layout1");
    assertSame(ResourceFolderType.MENU, ((PsiResourceItem)item).getSourceFile().getFolderType());
  }

  public void testMoveOutOfResourceFolder() throws Exception {
    // Move value files out of its resource folder; items should disappear
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    VirtualFile javaFile = myFixture.copyFileToProject(VALUES1, "src/my/pkg/Dummy.java");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step"));

    long generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          file1.move(this, javaFile.getParent());
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step"));
  }

  public void testMoveIntoResourceFolder() throws Exception {
    // Move value files out of its resource folder; items should disappear
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/dummy.ignore");
    VirtualFile xmlFile = myFixture.copyFileToProject(VALUES1, "src/my/pkg/values.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step"));

    long generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          xmlFile.move(this, file1.getParent());
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step"));
  }

  public void testReplaceResourceFile() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");

    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "btn_title_refresh"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "btn_title_refresh2"));

    long generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
        myFixture.copyFileToProject(LAYOUT2, "res/layout/layout1.xml");
      }
      catch (Exception e) {
        fail(e.toString());
      }
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "btn_title_refresh2"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "btn_title_refresh"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
  }

  public void testAddEmptyValueFile() {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    long generation = repository.getModificationCount();
    assertThat(repository.getModificationCount()).isEqualTo(generation);
  }

  public void testRawFolder() throws Exception {
    // In this folder, any file extension is allowed.
    myFixture.copyFileToProject(LAYOUT1, "res/raw/raw1.xml");
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    Collection<String> raw = repository.getResources(RES_AUTO, ResourceType.RAW).keySet();
    assertEquals(1, raw.size());
    long generation = repository.getModificationCount();
    VirtualFile file2 = myFixture.copyFileToProject(LAYOUT1, "res/raw/numbers.random");
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.RAW, "numbers"));
    raw = repository.getResources(RES_AUTO, ResourceType.RAW).keySet();
    assertEquals(2, raw.size());

    generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          file2.rename(this, "numbers2.whatever");
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.RAW, "numbers2"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.RAW, "numbers"));
  }

  public void testEditLayoutNoOp() throws Exception {
    // Make some miscellaneous edits in the file that have no bearing on the
    // project repository and therefore end up doing no work
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    assert(psiFile1 instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile1;
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    Collection<String> layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(1, layouts.size());
    assertNotNull(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Insert a comment at the beginning
    WriteCommandAction.runWriteCommandAction(null, () -> {
      XmlTag rootTag = xmlFile.getRootTag();
      assertNotNull(rootTag);
      int rootTagOffset = rootTag.getTextOffset();
      document.insertString(rootTagOffset, "<!-- This is a\nmultiline comment -->");
      documentManager.commitDocument(document);
      // Edit the comment some more
      document.deleteString(rootTagOffset + 8, rootTagOffset + 8 + 5);
      documentManager.commitDocument(document);
      document.insertString(rootTagOffset + 8, "Replacement");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    // Inserting the comment and editing it shouldn't have had any observable results on the resource repository.
    assertThat(repository.getModificationCount()).isEqualTo(generation);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
    XmlTag tag = findTagById(psiFile1, "noteArea");
    assertNotNull(tag);

    // Now insert some whitespace before a tag
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int indentAreaBeforeTag = tag.getTextOffset() - 1;
      document.insertString(indentAreaBeforeTag, "   ");
      documentManager.commitDocument(document);
      document.deleteString(indentAreaBeforeTag, indentAreaBeforeTag + 2);
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    // Space edits outside the tag shouldn't be observable
    assertThat(repository.getModificationCount()).isEqualTo(generation);

    // Edit text inside an element tag. No effect in value files!
    WriteCommandAction.runWriteCommandAction(null, () -> {
      XmlTag header = findTagById(xmlFile, "header");
      assertNotNull(header);
      int indentAreaBeforeTag = header.getSubTags()[0].getTextOffset();
      document.insertString(indentAreaBeforeTag, "   ");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    // Space edits inside the tag shouldn't be observable
    assertThat(repository.getModificationCount()).isEqualTo(generation);

    // Insert tag (without id) in layout file: ignored (only ids and file item matters)
    WriteCommandAction.runWriteCommandAction(null, () -> {
      XmlTag header = findTagById(xmlFile, "text2");
      assertNotNull(header);
      int indentAreaBeforeTag = header.getTextOffset() - 1;
      document.insertString(indentAreaBeforeTag, "<Button />");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    // Non-id new tags shouldn't be observable.
    assertThat(repository.getModificationCount()).isEqualTo(generation);

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);

    // Finally make an edit which *does* affect the project repository to ensure
    // that document edits actually *do* fire PSI events that are digested by
    // this repository.
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "newid"));
    String elementDeclaration = "<Button android:id=\"@+id/newid\" />\n";
    WriteCommandAction.runWriteCommandAction(null, () -> {
      XmlTag tag12 = findTagById(psiFile1, "noteArea");
      assertNotNull(tag12);
      document.insertString(tag12.getTextOffset() - 1, elementDeclaration);
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "newid"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "newid2"));
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Now try another edit, where things should be incremental now.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    String elementDeclaration2 = "<Button android:id=\"@+id/newid2\" />\n";
    WriteCommandAction.runWriteCommandAction(null, () -> {
      XmlTag tag1 = findTagById(psiFile1, "noteArea");
      assertNotNull(tag1);
      document.insertString(tag1.getTextOffset() - 1, elementDeclaration2);
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "newid"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "newid2"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans);

    generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int startOffset = document.getText().indexOf(elementDeclaration);
      document.deleteString(startOffset, startOffset + elementDeclaration.length());
      documentManager.commitDocument(document);
    });

    waitForUpdates(repository);
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "newid"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1);
  }

  public void testEditValueFileNoOp() {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    Collection<String> strings = repository.getResources(RES_AUTO, ResourceType.STRING).keySet();
    assertEquals(8, strings.size());
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.INTEGER, "card_flip_time_full"));

    long generation = repository.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Edit comment header; should be a no-op.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("Licensed under the");
      document.insertString(offset, "This code is ");
      documentManager.commitDocument(document);
    });
    assertThat(repository.getModificationCount()).isEqualTo(generation);

    // Test edit text NOT under an item: no-op.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf(" <item type=\"id\""); // insert BEFORE this
      document.insertString(offset, "Ignored text");
      documentManager.commitDocument(document);
    });
    assertThat(repository.getModificationCount()).isEqualTo(generation);

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(0);
  }

  public void testInsertNewElementWithId() throws Exception {
    // Make some miscellaneous edits in the file that have no bearing on the
    // project repository and therefore end up doing no work
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    assert(psiFile1 instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile1;
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    Collection<String> layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(1, layouts.size());
    assertNotNull(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Insert tag (with an id) in layout file: should incrementally update set of ids
    WriteCommandAction.runWriteCommandAction(null, () -> {
      XmlTag header = findTagById(xmlFile, "text2");
      assertNotNull(header);
      int indentAreaBeforeTag = header.getTextOffset() - 1;
      document.insertString(indentAreaBeforeTag,
                            "<LinearLayout android:id=\"@+id/newid1\"><Child android:id=\"@+id/newid2\"/></LinearLayout>");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "newid1"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "newid2"));

    // A second update should be incremental.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      XmlTag header = findTagById(xmlFile, "text2");
      assertNotNull(header);
      int indentAreaBeforeTag = header.getTextOffset() - 1;
      document.insertString(indentAreaBeforeTag,
                            "<LinearLayout android:id=\"@+id/newid3\"><Child android:id=\"@+id/newid4\"/></LinearLayout>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "newid1"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "newid2"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "newid3"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "newid4"));
  }

  public void testEditIdAttributeValue() throws Exception {
    // Edit the id attribute value of a layout item to change the set of available ids
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    Collection<String> layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(1, layouts.size());
    assertNotNull(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "note2Area"));

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    XmlTag tag = findTagById(psiFile1, "noteArea");
    assertNotNull(tag);
    WriteCommandAction.runWriteCommandAction(null, () -> {
      tag.setAttribute(ATTR_ID, ANDROID_URI, "@+id/note2Area");
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "note2Area"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));

    // A second update should be incremental.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      tag.setAttribute(ATTR_ID, ANDROID_URI, "@+id/note23Area");
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "note23Area"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "note2Area"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Check replacing @+id with a normal string.
    generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      tag.setAttribute(ATTR_ID, ANDROID_URI, "notId");
    });
    waitForUpdates(repository);
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "note23Area"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "notId"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testEditIdAttributeValue2() throws Exception {
    // Edit the id attribute value: rather than by making a full value replacement,
    // perform a tiny edit on the character content; this takes a different code
    // path in the incremental updater.
    // Edit the id attribute value of a layout item to change the set of available ids.
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    Collection<String> layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(1, layouts.size());
    assertNotNull(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "note2Area"));

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Edit value should cause update
    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("noteArea");
      document.insertString(offset + 4, "2");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "note2Area"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));

    // A second update should be incremental.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("note2Area");
      document.insertString(offset + 5, "3");
      documentManager.commitDocument(document);
    });

    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "note23Area"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "note2Area"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Also check that for IDs the ResourceValue is nothing of consequence.
    ResourceItem idItem = getOnlyItem(repository, ResourceType.ID, "note23Area");
    ResourceValue idValue = idItem.getResourceValue();
    assertNotNull(idValue);
    assertEquals("", idValue.getValue());

    // Check replacing @+id with a normal string.
    generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String attrValue = "@+id/note23Area";
      int offset = document.getText().indexOf(attrValue);
      document.replaceString(offset, offset + attrValue.length(), "notId");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "note23Area"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "notId"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }


  public void testEditIdFromDrawable() throws Exception {
    // Mix PNGs and XML in the same directory.
    myFixture.copyFileToProject(DRAWABLE, "res/drawable-v21/logo.png");
    VirtualFile file1 = myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/drawable-v21/drawable_with_ids.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    Collection<String> ids = repository.getResources(RES_AUTO, ResourceType.ID).keySet();
    assertNotNull(ids);
    assertContainsElements(ids, "focused_state", "default_state", "pressed_state");
    Collection<String> drawables = repository.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertContainsElements(drawables, "logo", "drawable_with_ids");

    // Now test an ID edit, to make sure that gets picked up too incrementally, just like layouts.
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Edit value should cause update
    long generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("focused_state");
      document.replaceString(offset, offset + 1, "l");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "locused_state"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "focused_state"));
    drawables = repository.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertContainsElements(drawables, "logo", "drawable_with_ids");
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Now try another edit, where things should be incremental now.
    generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("default_state");
      document.insertString(offset, "dd");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "dddefault_state"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "default_state"));
    drawables = repository.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertContainsElements(drawables, "logo", "drawable_with_ids");

    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  /**
   * We expect this change to update the counter (so the layout editor notices the change but it shouldn't
   * change any repository since it does not add or remove ids.
   */
  public void testEditNonIdFromDrawable() throws Exception {
    VirtualFile file = myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/drawable-v21/drawable_with_ids.xml");
    PsiFile psiFiles = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFiles);

    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    // Now test an ID edit, to make sure that gets picked up too incrementally, just like layouts.
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFiles);
    assertNotNull(document);

    // Edit attribute value.
    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("drawableP");
      int lineNumber = document.getLineNumber(offset);
      document.insertString(offset, "2");
      documentManager.commitDocument(document);
    });

    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.
  }

  /**
   * We expect this change to update the counter (so the layout editor notices the change but it shouldn't
   * change any repository since it does not add or remove ids.
   */
  public void testEditNonIdGeneratingXml() throws Exception {
    VirtualFile file = myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/xml/xml_file.xml");
    PsiFile psiFiles = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFiles);

    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    // Now test an ID edit, to make sure that gets picked up too incrementally, just like layouts.
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFiles);
    assertNotNull(document);

    // Edit attribute value.
    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("drawableP");
      int lineNumber = document.getLineNumber(offset);
      document.insertString(offset, "2");
      documentManager.commitDocument(document);
    });

    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.
  }

  public void testMotionScene() throws Exception {
    VirtualFile virtualFile = myFixture.copyFileToProject(MOTION_SCENE, "res/xml/motion_scene.xml");
    XmlFile file = (XmlFile)PsiManager.getInstance(getProject()).findFile(virtualFile);
    assertNotNull(file);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    XmlTag motionScene = file.getRootTag();
    XmlTag constraintSet = motionScene.findFirstSubTag(CONSTRAINT_SET);
    XmlTag constraint = constraintSet.findFirstSubTag(CONSTRAINT);

    // Change the attribute value of a tag in the motion scene.
    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      constraint.setAttribute("rotationX", ANDROID_URI, "95");
    });

    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.
  }

  public void testEditValueText() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    Collection<String> strings = repository.getResources(RES_AUTO, ResourceType.STRING).keySet();
    assertEquals(8, strings.size());
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.INTEGER, "card_flip_time_full"));

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Edit value should cause update.
    int screenSlideOffset = document.getText().indexOf("Screen Slide");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(screenSlideOffset + 3, screenSlideOffset + 3, "e");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1);

    // Now try another edit, where things should be incremental now.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    int screeenSlideOffset = document.getText().indexOf("Screeen Slide");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(screeenSlideOffset + 3, screeenSlideOffset + 3, "e");
      documentManager.commitDocument(document);
    });

    waitForUpdates(repository);
    // No revision bump yet, because the resource value hasn't been observed!
    assertThat(repository.getModificationCount()).isEqualTo(generation);

    // Now observe it, do another edit, and see what happens
    List<ResourceItem> labelList = repository.getResources(RES_AUTO, ResourceType.STRING, "title_screen_slide");
    assertNotNull(labelList);
    assertEquals(1, labelList.size());
    ResourceItem slideLabel = labelList.get(0);
    ResourceValue resourceValue = slideLabel.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Screeeen Slide", resourceValue.getValue());

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.deleteString(screenSlideOffset + 3, screenSlideOffset + 7);
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    resourceValue = slideLabel.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Scrn Slide", resourceValue.getValue());

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testNestedEditValueText() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    List<ResourceItem> labelList = repository.getResources(RES_AUTO, ResourceType.STRING, "title_template_step");
    assertNotNull(labelList);
    assertEquals(1, labelList.size());
    ResourceItem label = labelList.get(0);
    ResourceValue resourceValue = label.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Step ${step_number}: Lorem Ipsum", resourceValue.getValue());

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Edit value should cause update
    int textOffset = document.getText().indexOf("Lorem");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.insertString(textOffset + 1, "l");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    // Because of the file -> psi transition, we can't rely on the old ResourceItem references (replaced).
    // We need to ensure that callers don't hang on to the old ResourceItem, assuming it'll get
    // updated in place after an edit.
    labelList = repository.getResources(RES_AUTO, ResourceType.STRING, "title_template_step");
    assertNotNull(labelList);
    assertEquals(1, labelList.size());
    label = labelList.get(0);
    resourceValue = label.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Step ${step_number}: Llorem Ipsum", resourceValue.getValue());

    // Try a second edit
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int textOffset2 = document.getText().indexOf("Llorem");
      document.insertString(textOffset + 1, "l");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    List<ResourceItem> labelList2 = repository.getResources(RES_AUTO, ResourceType.STRING, "title_template_step");
    assertNotNull(labelList2);
    assertEquals(1, labelList2.size());
    ResourceItem label2 = labelList2.get(0);
    resourceValue = label2.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Step ${step_number}: Lllorem Ipsum", resourceValue.getValue());

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testEditValueName() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    Collection<String> strings = repository.getResources(RES_AUTO, ResourceType.STRING).keySet();
    assertEquals(8, strings.size());
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    int offset = document.getText().indexOf("app_name");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(offset, offset + 3, "tap");
      documentManager.commitDocument(document);
    });

    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "tap_name"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));

    // However, the second edit can then be incremental.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    int offset2 = document.getText().indexOf("tap_name");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(offset2, offset2 + 3, "rap");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "rap_name"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STRING, "tap_name"));

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testAddValue() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    Collection<String> strings = repository.getResources(RES_AUTO, ResourceType.STRING).keySet();
    assertEquals(8, strings.size());
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));

    // Incrementally add in a new item
    long generation = repository.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    int offset = document.getText().indexOf("    <item type");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String firstHalf = "<string name=\"new_s";
      String secondHalf = "tring\">New String</string>";
      document.insertString(offset, firstHalf);
      documentManager.commitDocument(document);
      document.insertString(offset + firstHalf.length(), secondHalf);
      documentManager.commitDocument(document);
    });

    // This currently doesn't work incrementally because we get psi events that do not contain
    // enough info to be handled incrementally, so instead we do an asynchronous update (such that
    // we can do a single update rather than rescanning the file 20 times).
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(1);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "new_string"));
    assertEquals("New String", repository.getResources(RES_AUTO, ResourceType.STRING, "new_string").get(0).getResourceValue().getValue());
  }

  public void testRemoveValue() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    Collection<String> strings = repository.getResources(RES_AUTO, ResourceType.STRING).keySet();
    assertEquals(8, strings.size());
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String textToRemove = "<string name=\"app_name\">Animations Demo</string>";
      int offset = document.getText().indexOf(textToRemove);
      document.deleteString(offset, offset + textToRemove.length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_zoom"));

    // Now try another edit that is also a delete, where things should be incremental now.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String textToRemove2 = "<string name=\"title_zoom\">Zoom</string>";
      int offset = document.getText().indexOf(textToRemove2);
      document.deleteString(offset, offset + textToRemove2.length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_zoom"));
    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testAddIdValue() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "action_prev"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "action_flip"));

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item type=\"id\" name=\"action_next\" />");
      document.insertString(offset, "<item type=\"id\" name=\"action_prev\" />");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "action_prev"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "action_flip"));

    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    // Try a second edit.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item type=\"id\" name=\"action_next\" />");
      document.insertString(offset, "<item type=\"id\" name=\"action_mid\" />");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "action_prev"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "action_mid"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "action_flip"));
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testRemoveIdValue() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "action_flip"));

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = "<item type=\"id\" name=\"action_next\" />";
      int offset = document.getText().indexOf(item);
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "action_flip"));

    // Try a second edit.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = "<item type=\"id\" name=\"action_flip\" />";
      int offset = document.getText().indexOf(item);
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "action_flip"));
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testChangeType() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.DIMEN, "action_next"));

    long generation = repository.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    int offset = document.getText().indexOf("\"id\" name=\"action_next\" />") + 1;
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(offset, offset + 2, "dimen");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getFileRescans()).isEqualTo(1);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.DIMEN, "action_next"));
  }

  public void testBreakNameAttribute() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));

    long generation = repository.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    int offset = document.getText().indexOf("name=\"app_name\">");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(offset + 2, offset + 3, "o"); // name => nome
      documentManager.commitDocument(document);
    });

    waitForUpdates(repository);
    assertThat(repository.getFileRescans()).isEqualTo(1);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
  }

  public void testChangeValueTypeByTagNameEdit() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.INTEGER, "card_flip_time_half"));

    long generation = repository.getModificationCount();
    XmlTag tag = findTagByName(psiFile1, "card_flip_time_half");
    assertNotNull(tag);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      tag.setName("dimen"); // Change <integer> to <dimen>
    });
    waitForUpdates(repository);
    assertThat(repository.getFileRescans()).isEqualTo(1);

    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.DIMEN, "card_flip_time_half"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.INTEGER, "card_flip_time_half"));
  }

  public void testEditStyleName() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));

    // Change style name.
    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("DarkTheme");
      document.replaceString(offset, offset + 4, "Grey");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLE, "GreyTheme"));

    // Now try another edit where things should be incremental now.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("GreyTheme");
      document.replaceString(offset, offset + 4, "Light");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLE, "LightTheme"));

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testEditStyleParent() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));

    // Change style parent
    long generation = repository.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // First edit won't be incremental (file -> Psi).
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("android:Theme.Holo");
      document.replaceString(offset, offset + "android:Theme.Holo".length(), "android:Theme.Light");
      documentManager.commitDocument(document);
    });

    waitForUpdates(repository);
    assertThat(repository.getFileRescans()).isEqualTo(1);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    ResourceItem style = getOnlyItem(repository, ResourceType.STYLE, "DarkTheme");
    ResourceValue resourceValue = style.getResourceValue();
    assertNotNull(resourceValue);
    assertTrue(resourceValue instanceof StyleResourceValue);
    StyleResourceValue styleValue = (StyleResourceValue)resourceValue;
    assertEquals("android:Theme.Light", styleValue.getParentStyleName());
    ResourceValue actionBarStyle = styleValue.getItem(ANDROID, "actionBarStyle");
    assertNotNull(actionBarStyle);
    assertEquals("@style/DarkActionBar", actionBarStyle.getValue());

    // Even on the second edit we don't expect editing the style parent to be incremental.
    generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("android:Theme.Light");
      document.replaceString(offset, offset + "android:Theme.Light".length(), "android:Theme.Material");
      documentManager.commitDocument(document);
    });

    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1);
    ResourceItem style2 = getOnlyItem(repository, ResourceType.STYLE, "DarkTheme");
    ResourceValue resourceValue2 = style2.getResourceValue();
    assertNotNull(resourceValue2);
    assertTrue(resourceValue2 instanceof StyleResourceValue);
    StyleResourceValue styleValue2 = (StyleResourceValue)resourceValue2;
    assertEquals("android:Theme.Material", styleValue2.getParentStyleName());
    ResourceValue actionBarStyle2 = styleValue2.getItem(ANDROID, "actionBarStyle");
    assertNotNull(actionBarStyle2);
    assertEquals("@style/DarkActionBar", actionBarStyle2.getValue());
  }

  public void testEditStyleItemText() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));
    ResourceItem style = getOnlyItem(repository, ResourceType.STYLE, "DarkTheme");
    StyleResourceValue styleValue = (StyleResourceValue)style.getResourceValue();
    assertNotNull(styleValue);
    ResourceValue actionBarStyle = styleValue.getItem(ANDROID, "actionBarStyle");
    assertNotNull(actionBarStyle);
    assertEquals("@style/DarkActionBar", actionBarStyle.getValue());

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("@style/DarkActionBar");
      document.replaceString(offset + 7, offset + 11, "Grey");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));
    style = getOnlyItem(repository, ResourceType.STYLE, "DarkTheme");
    styleValue = (StyleResourceValue)style.getResourceValue();
    assertNotNull(styleValue);
    actionBarStyle = styleValue.getItem(ANDROID, "actionBarStyle");
    assertNotNull(actionBarStyle);
    assertEquals("@style/GreyActionBar", actionBarStyle.getValue());

    // Now try another edit where things should be incremental now.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("@style/GreyActionBar");
      document.replaceString(offset + 7, offset + 11, "Light");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));

    style = getOnlyItem(repository, ResourceType.STYLE, "DarkTheme");
    styleValue = (StyleResourceValue)style.getResourceValue();
    assertNotNull(styleValue);
    actionBarStyle = styleValue.getItem(ANDROID, "actionBarStyle");
    assertNotNull(actionBarStyle);
    assertEquals("@style/LightActionBar", actionBarStyle.getValue());

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testEditStyleItemName() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));
    ResourceItem style = getOnlyItem(repository, ResourceType.STYLE, "DarkTheme");
    StyleResourceValue styleValue = (StyleResourceValue)style.getResourceValue();
    assertNotNull(styleValue);
    ResourceValue actionBarStyle = styleValue.getItem(ANDROID, "actionBarStyle");
    assertNotNull(actionBarStyle);
    assertEquals("@style/DarkActionBar", actionBarStyle.getValue());

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("android:actionBarStyle");
      document.insertString(offset + 8, "n");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));
    style = getOnlyItem(repository, ResourceType.STYLE, "DarkTheme");
    styleValue = (StyleResourceValue)style.getResourceValue();
    assertNotNull(styleValue);
    actionBarStyle = styleValue.getItem(ANDROID, "nactionBarStyle");
    assertNotNull(actionBarStyle);
    assertEquals("@style/DarkActionBar", actionBarStyle.getValue());

    // Now try another edit, where things should be incremental now.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("android:nactionBarStyle");
      document.insertString(offset + 8, "i");
      documentManager.commitDocument(document);
    });

    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));
    style = getOnlyItem(repository, ResourceType.STYLE, "DarkTheme");
    styleValue = (StyleResourceValue)style.getResourceValue();
    assertNotNull(styleValue);
    actionBarStyle = styleValue.getItem(ANDROID, "inactionBarStyle");
    assertNotNull(actionBarStyle);
    assertEquals("@style/DarkActionBar", actionBarStyle.getValue());

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testAddStyleItem() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar"));
    ResourceItem style = getOnlyItem(repository, ResourceType.STYLE, "DarkActionBar");
    StyleResourceValue styleValue = (StyleResourceValue)style.getResourceValue();
    assertNotNull(styleValue);
    checkDefinedItems(styleValue, "android:background", "android:textColor");
    ResourceValue background = styleValue.getItem(ANDROID, "background");
    assertNotNull(background);
    assertEquals("@android:color/transparent", background.getValue());
    ResourceValue textColor = styleValue.getItem(ANDROID, "textColor");
    assertNotNull(textColor);
    assertEquals("#008", textColor.getValue());

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item name=\"android:background\"");
      assertTrue(offset > 0);
      document.insertString(offset, "<item name=\"android:textSize\">20sp</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar"));
    style = getOnlyItem(repository, ResourceType.STYLE, "DarkActionBar");
    styleValue = (StyleResourceValue)style.getResourceValue();
    assertNotNull(styleValue);
    checkDefinedItems(styleValue, "android:background", "android:textSize", "android:textColor");
    ResourceValue textSize = styleValue.getItem(ANDROID, "textSize");
    assertNotNull(textSize);
    assertEquals("20sp", textSize.getValue());

    // Now try a second edit.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item name=\"android:background\"");
      assertTrue(offset > 0);
      document.insertString(offset, "<item name=\"android:typeface\">monospace</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar"));
    style = getOnlyItem(repository, ResourceType.STYLE, "DarkActionBar");
    styleValue = (StyleResourceValue)style.getResourceValue();
    assertNotNull(styleValue);
    checkDefinedItems(styleValue, "android:background", "android:typeface", "android:textSize", "android:textColor");
    ResourceValue typeface = styleValue.getItem(ANDROID, "typeface");
    assertNotNull(typeface);
    assertEquals("monospace", typeface.getValue());

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testRemoveStyleItem() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar"));
    ResourceItem style = getOnlyItem(repository, ResourceType.STYLE, "DarkActionBar");
    StyleResourceValue styleValue = (StyleResourceValue)style.getResourceValue();
    assertNotNull(styleValue);
    checkDefinedItems(styleValue, "android:background", "android:textColor");
    ResourceValue background = styleValue.getItem(ANDROID, "background");
    assertNotNull(background);
    assertEquals("@android:color/transparent", background.getValue());
    ResourceValue textColor = styleValue.getItem(ANDROID, "textColor");
    assertNotNull(textColor);
    assertEquals("#008", textColor.getValue());

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = ("<item name=\"android:textColor\">#008</item>");
      int offset = document.getText().indexOf(item);
      assertTrue(offset > 0);
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar"));
    style = getOnlyItem(repository, ResourceType.STYLE, "DarkActionBar");
    styleValue = (StyleResourceValue)style.getResourceValue();
    assertNotNull(styleValue);
    checkDefinedItems(styleValue, "android:background");
    background = styleValue.getItem(ANDROID, "background");
    assertNotNull(background);
    assertEquals("@android:color/transparent", background.getValue());

    // Try second edit, which is incremental.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = ("<item name=\"android:background\">@android:color/transparent</item>");
      int offset = document.getText().indexOf(item);
      assertTrue(offset > 0);
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar"));
    style = getOnlyItem(repository, ResourceType.STYLE, "DarkActionBar");
    styleValue = (StyleResourceValue)style.getResourceValue();
    assertNotNull(styleValue);
    checkDefinedItems(styleValue);

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testEditDeclareStyleableAttr() throws Exception {
    // Check edits of the name in a <declare-styleable> element.
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    myFixture.openFileInEditor(file1);
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ATTR, "watchType"));
    ResourceItem style = getOnlyItem(repository, ResourceType.STYLEABLE, "MyCustomView");
    StyleableResourceValue styleValue = (StyleableResourceValue)style.getResourceValue();
    assertNotNull(styleValue);
    assertEquals(5, styleValue.getAllAttributes().size());
    AttrResourceValue watchType = findAttr(styleValue, "watchType", repository);
    assertNotNull(watchType);
    assertEquals(2, watchType.getAttributeValues().size());
    assertEquals(Integer.valueOf(1), watchType.getAttributeValues().get("type_stopwatch"));
    assertEquals(Integer.valueOf(0), watchType.getAttributeValues().get("type_countdown"));
    AttrResourceValue crash = findAttr(styleValue, "crash", repository);
    assertNotNull(crash);
    assertTrue(crash.getAttributeValues().isEmpty());

    AttrResourceValue minWidth = findAttr(styleValue, "minWidth", repository);
    assertNotNull(minWidth);
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ATTR, "minWidth"));
    AttrResourceValue ignoredNoFormat = findAttr(styleValue, "ignore_no_format", repository);
    assertNotNull(ignoredNoFormat);
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ATTR, "ignore_no_format"));

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    type("MyCustom|View", "r");
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomrView"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ATTR, "watchType"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView"));

    // Now try another edit, where things should be incremental now.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    type("MyCustom|rView", "e");
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomerView"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ATTR, "watchType"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView"));
    style = getOnlyItem(repository, ResourceType.STYLEABLE, "MyCustomerView");
    styleValue = (StyleableResourceValue)style.getResourceValue();
    assertNotNull(styleValue);
    assertEquals(5, styleValue.getAllAttributes().size());
    watchType = findAttr(styleValue, "watchType", repository);
    assertNotNull(watchType);
    assertEquals(2, watchType.getAttributeValues().size());
    assertEquals(Integer.valueOf(1), watchType.getAttributeValues().get("type_stopwatch"));

    generation = repository.getModificationCount();
    type("watch|Type", "Change");
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    assertSame(style, getOnlyItem(repository, ResourceType.STYLEABLE, "MyCustomerView"));
    assertNotSame(styleValue, style.getResourceValue());
    styleValue = (StyleableResourceValue)style.getResourceValue();
    assertNull(findAttr(styleValue, "watchType", repository));
    assertNotNull(findAttr(styleValue, "watchChangeType", repository));

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testEditAttr() throws Exception {
    // Insert, remove and change <attr> attributes inside a <declare-styleable> and ensure that
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView"));
    // Fetch resource value to ensure it gets replaced after update.
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ATTR, "watchType"));
    ResourceItem style = getOnlyItem(repository, ResourceType.STYLEABLE, "MyCustomView");
    StyleableResourceValue styleValue = (StyleableResourceValue)style.getResourceValue();
    assertNotNull(styleValue);
    assertEquals(5, styleValue.getAllAttributes().size());
    AttrResourceValue watchType = findAttr(styleValue, "watchType", repository);
    assertNotNull(watchType);
    assertEquals(2, watchType.getAttributeValues().size());
    assertEquals(Integer.valueOf(1), watchType.getAttributeValues().get("type_stopwatch"));
    assertEquals(Integer.valueOf(0), watchType.getAttributeValues().get("type_countdown"));
    AttrResourceValue crash = findAttr(styleValue, "crash", repository);
    assertNotNull(crash);
    assertTrue(crash.getAttributeValues().isEmpty());

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("watchType");
      document.insertString(offset, "y");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ATTR, "watchType"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ATTR, "ywatchType"));

    // However, the second edit can then be incremental.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("ywatchType");
      document.replaceString(offset, offset + 1, "w");
      documentManager.commitDocument(document);
    });

    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ATTR, "watchType"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ATTR, "wwatchType"));
    style = getOnlyItem(repository, ResourceType.STYLEABLE, "MyCustomView");
    styleValue = (StyleableResourceValue)style.getResourceValue();
    assertNotNull(styleValue);
    assertEquals(5, styleValue.getAllAttributes().size());
    watchType = findAttr(styleValue, "wwatchType", repository);
    assertNotNull(watchType);
    assertEquals(2, watchType.getAttributeValues().size());
    assertEquals(Integer.valueOf(1), watchType.getAttributeValues().get("type_stopwatch"));

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);

    // Now insert a new item and delete one and make sure we're still okay
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String crashAttr = "<attr name=\"crash\" format=\"boolean\" />";
      int offset = document.getText().indexOf(crashAttr);
      document.deleteString(offset, offset + crashAttr.length());
      document.insertString(offset, "<attr name=\"newcrash\" format=\"integer\" />");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ATTR, "watchType"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ATTR, "wwatchType"));
    ResourceItem style1 = getOnlyItem(repository, ResourceType.STYLEABLE, "MyCustomView");
    StyleableResourceValue srv1 = (StyleableResourceValue)style1.getResourceValue();
    assertNotNull(srv1);
    assertEquals(5, srv1.getAllAttributes().size());
    AttrResourceValue watchType1 = findAttr(srv1, "wwatchType", repository);
    assertNotNull(watchType1);
    assertEquals(2, watchType1.getAttributeValues().size());
    assertEquals(Integer.valueOf(1), watchType1.getAttributeValues().get("type_stopwatch"));
    assertEquals(Integer.valueOf(0), watchType1.getAttributeValues().get("type_countdown"));
    AttrResourceValue crash1 = findAttr(srv1, "crash", repository);
    assertNull(crash1);
    AttrResourceValue newcrash = findAttr(srv1, "newcrash", repository);
    assertNotNull(newcrash);
    assertTrue(newcrash.getAttributeValues().isEmpty());
  }

  public void testEditDeclareStyleableFlag() throws Exception {
    // Rename, add and remove <flag> and <enum> nodes under a declare styleable and assert
    // that the declare styleable parent is updated
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView"));
    // Fetch resource value to ensure it gets replaced after update
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ATTR, "watchType"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ATTR, "ignore_no_format"));
    ResourceItem style = getOnlyItem(repository, ResourceType.STYLEABLE, "MyCustomView");
    StyleableResourceValue styleValue = (StyleableResourceValue)style.getResourceValue();
    assertNotNull(styleValue);
    assertEquals(5, styleValue.getAllAttributes().size());
    AttrResourceValue flagType = findAttr(styleValue, "flagType", repository);
    assertNotNull(flagType);
    assertEquals(2, flagType.getAttributeValues().size());
    assertEquals(Integer.valueOf(16), flagType.getAttributeValues().get("flag1"));
    assertEquals(Integer.valueOf(32), flagType.getAttributeValues().get("flag2"));

    long generation = repository.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("flag1");
      document.insertString(offset + 1, "l");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(1);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ATTR, "flagType"));
    ResourceItem style1 = getOnlyItem(repository, ResourceType.STYLEABLE, "MyCustomView");
    StyleableResourceValue styleableValue = (StyleableResourceValue)style1.getResourceValue();
    assertNotNull(styleableValue);
    assertEquals(5, styleableValue.getAllAttributes().size());
    AttrResourceValue flagType1 = findAttr(styleableValue, "flagType", repository);
    assertNotNull(flagType1);
    assertEquals(2, flagType1.getAttributeValues().size());
    assertNull(flagType1.getAttributeValues().get("flag1"));
    assertEquals(Integer.valueOf(16), flagType1.getAttributeValues().get("fllag1"));

    // Now insert a new enum and delete one and make sure we're still okay
    generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String enumAttr = "<enum name=\"type_stopwatch\" value=\"1\"/>";
      int offset = document.getText().indexOf(enumAttr);
      document.deleteString(offset, offset + enumAttr.length());
      String flagAttr = "<flag name=\"flag2\" value=\"0x20\"/>";
      offset = document.getText().indexOf(flagAttr);
      document.insertString(offset, "<flag name=\"flag3\" value=\"0x40\"/>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ATTR, "watchType"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ATTR, "flagType"));
    style1 = getOnlyItem(repository, ResourceType.STYLEABLE, "MyCustomView");
    styleableValue = (StyleableResourceValue)style1.getResourceValue();
    assertNotNull(styleableValue);
    assertEquals(5, styleableValue.getAllAttributes().size());
    flagType1 = findAttr(styleableValue, "flagType", repository);
    assertNotNull(flagType1);
    assertEquals(3, flagType1.getAttributeValues().size());
    assertEquals(Integer.valueOf(16), flagType1.getAttributeValues().get("fllag1"));
    assertEquals(Integer.valueOf(32), flagType1.getAttributeValues().get("flag2"));
    assertEquals(Integer.valueOf(64), flagType1.getAttributeValues().get("flag3"));

    AttrResourceValue watchType = findAttr(styleableValue, "watchType", repository);
    assertNotNull(watchType);
    assertEquals(1, watchType.getAttributeValues().size());
    assertEquals(Integer.valueOf(0), watchType.getAttributeValues().get("type_countdown"));
  }

  public void testEditPluralItems() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    // Test that our tools:quantity works correctly for getResourceValue()
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.PLURALS, "my_plural"));
    ResourceItem plural = getOnlyItem(repository, ResourceType.PLURALS, "my_plural");
    ResourceValue resourceValue = plural.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("@string/hello_two", resourceValue.getValue());

    // TODO: It would be nice to avoid updating the generation if you edit a different item
    //       than the one being picked (default or via tools:quantity) but for now we're not
    //       worrying about that optimization.

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("@string/hello_two");
      document.replaceString(offset + 9, offset + 10, "a");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    plural = getOnlyItem(repository, ResourceType.PLURALS, "my_plural");
    resourceValue = plural.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("@string/hallo_two", resourceValue.getValue());
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    // However, the second edit can then be incremental.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("@string/hallo_two");
      document.replaceString(offset + 9, offset + 10, "i");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    plural = getOnlyItem(repository, ResourceType.PLURALS, "my_plural");
    resourceValue = plural.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("@string/hillo_two", resourceValue.getValue());
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testAddPluralItems() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.PLURALS, "my_plural"));
    ResourceItem plural = getOnlyItem(repository, ResourceType.PLURALS, "my_plural");
    ResourceValue resourceValue = plural.getResourceValue();
    assertNotNull(resourceValue);
    assertInstanceOf(resourceValue, PluralsResourceValue.class);
    PluralsResourceValue prv = (PluralsResourceValue)resourceValue;
    assertEquals(3, prv.getPluralsCount());
    assertEquals("@string/hello_two", resourceValue.getValue());
    assertEquals("two", prv.getQuantity(1));

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item quantity=\"two\">@string/hello_two</item>");
      document.insertString(offset, "<item quantity=\"one_and_half\">@string/hello_one_and_half</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    plural = getOnlyItem(repository, ResourceType.PLURALS, "my_plural");
    resourceValue = plural.getResourceValue();
    assertNotNull(resourceValue);
    assertInstanceOf(resourceValue, PluralsResourceValue.class);
    prv = (PluralsResourceValue)resourceValue;
    assertEquals(4, prv.getPluralsCount());
    assertEquals("@string/hello_two", resourceValue.getValue());
    assertEquals("one_and_half", prv.getQuantity(1));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    // Now try a second edit.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item quantity=\"one_and_half\">@string/hello_one_and_half</item>");
      document.insertString(offset, "<item quantity=\"one_and_a_quarter\">@string/hello_one_and_a_quarter</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    plural = getOnlyItem(repository, ResourceType.PLURALS, "my_plural");
    resourceValue = plural.getResourceValue();
    assertNotNull(resourceValue);
    assertInstanceOf(resourceValue, PluralsResourceValue.class);
    prv = (PluralsResourceValue)resourceValue;
    assertEquals(5, prv.getPluralsCount());
    assertEquals("@string/hello_two", resourceValue.getValue());
    assertEquals("one_and_a_quarter", prv.getQuantity(1));
    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testRemovePluralItems() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.PLURALS, "my_plural"));
    ResourceItem plural = getOnlyItem(repository, ResourceType.PLURALS, "my_plural");
    ResourceValue resourceValue = plural.getResourceValue();
    assertNotNull(resourceValue);
    assertInstanceOf(resourceValue, PluralsResourceValue.class);
    PluralsResourceValue prv = (PluralsResourceValue)resourceValue;
    assertEquals(3, prv.getPluralsCount());
    assertEquals("@string/hello_two", resourceValue.getValue());
    assertEquals("one", prv.getQuantity(0));

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = "<item quantity=\"one\">@string/hello</item>";
      int offset = document.getText().indexOf(item);
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    plural = getOnlyItem(repository, ResourceType.PLURALS, "my_plural");
    resourceValue = plural.getResourceValue();
    assertNotNull(resourceValue);
    assertInstanceOf(resourceValue, PluralsResourceValue.class);
    prv = (PluralsResourceValue)resourceValue;
    assertEquals(2, prv.getPluralsCount());
    assertEquals("@string/hello_two", resourceValue.getValue());
    assertEquals("two", prv.getQuantity(0));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    // Try a second edit.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = "<item quantity=\"other\">@string/hello_many</item>";
      int offset = document.getText().indexOf(item);
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    plural = getOnlyItem(repository, ResourceType.PLURALS, "my_plural");
    resourceValue = plural.getResourceValue();
    assertNotNull(resourceValue);
    assertInstanceOf(resourceValue, PluralsResourceValue.class);
    prv = (PluralsResourceValue)resourceValue;
    assertEquals(1, prv.getPluralsCount());
    assertEquals("@string/hello_two", resourceValue.getValue());
    assertEquals("two", prv.getQuantity(0));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testEditArrayItemText() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    // Test that our tools:index and fallback handling for arrays works correctly
    // for getResourceValue()
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    ResourceItem array = getOnlyItem(repository, ResourceType.ARRAY, "security_questions");
    ResourceValue resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Question 4", resourceValue.getValue());

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "integers"));
    array = getOnlyItem(repository, ResourceType.ARRAY, "integers");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("10", resourceValue.getValue());

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("Question 4");
      document.insertString(offset, "Q");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    array = getOnlyItem(repository, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("QQuestion 4", resourceValue.getValue());
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    // However, the second edit can then be incremental.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("QQuestion 4");
      document.insertString(offset, "Q");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    array = getOnlyItem(repository, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("QQQuestion 4", resourceValue.getValue());
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testAddStringArrayItemElements() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    ResourceItem array = getOnlyItem(repository, ResourceType.ARRAY, "security_questions");
    ResourceValue resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Question 4", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    ArrayResourceValue arv = (ArrayResourceValue)resourceValue;
    assertEquals(5, arv.getElementCount());
    assertEquals("Question 2", arv.getElement(1));
    assertEquals("Question 3", arv.getElement(2));
    assertEquals("Question 4", arv.getElement(3));

    int rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item>Question 3</item>");
      document.insertString(offset, "<item>Question 2.5</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    array = getOnlyItem(repository, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Question 3", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(6, arv.getElementCount());
    assertEquals("Question 2", arv.getElement(1));
    assertEquals("Question 2.5", arv.getElement(2));
    assertEquals("Question 3", arv.getElement(3));
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    rescans = repository.getFileRescans();
    // However, the second edit can then be incremental.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item>Question 3</item>");
      document.insertString(offset, "<item>Question 2.75</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    array = getOnlyItem(repository, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Question 2.75", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(7, arv.getElementCount());
    assertEquals("Question 2", arv.getElement(1));
    assertEquals("Question 2.5", arv.getElement(2));
    assertEquals("Question 2.75", arv.getElement(3));
    assertEquals("Question 3", arv.getElement(4));

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testRemoveStringArrayItemElements() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    ResourceItem array = getOnlyItem(repository, ResourceType.ARRAY, "security_questions");
    ResourceValue resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Question 4", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    ArrayResourceValue arv = (ArrayResourceValue)resourceValue;
    assertEquals(5, arv.getElementCount());

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String elementString = "<item>Question 3</item>";
      int offset = document.getText().indexOf(elementString);
      document.deleteString(offset, offset + elementString.length());
      document.insertString(offset, "<item>Question X</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    array = getOnlyItem(repository, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Question 4", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(5, arv.getElementCount());
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    rescans = repository.getFileRescans();
    // Now try another edit that is also a delete item, where things should be incremental now.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String elementString = "<item>Question X</item>";
      int offset = document.getText().indexOf(elementString);
      document.deleteString(offset, offset + elementString.length());
      documentManager.commitDocument(document);
    });

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    array = getOnlyItem(repository, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Question 5", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(4, arv.getElementCount());

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testAddIntegerArrayItemElements() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "integers"));
    ResourceItem array = getOnlyItem(repository, ResourceType.ARRAY, "integers");
    ResourceValue resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("10", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    ArrayResourceValue arv = (ArrayResourceValue)resourceValue;
    assertEquals(2, arv.getElementCount());
    assertEquals("10", arv.getElement(0));
    assertEquals("20", arv.getElement(1));

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item>10</item>");
      document.insertString(offset, "<item>5</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "integers"));
    array = getOnlyItem(repository, ResourceType.ARRAY, "integers");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("5", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(3, arv.getElementCount());
    assertEquals("5", arv.getElement(0));
    assertEquals("10", arv.getElement(1));
    assertEquals("20", arv.getElement(2));
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    rescans = repository.getFileRescans();
    // Now try a second edit.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item>5</item>");
      document.insertString(offset, "<item>2</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "integers"));
    array = getOnlyItem(repository, ResourceType.ARRAY, "integers");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("2", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(4, arv.getElementCount());
    assertEquals("2", arv.getElement(0));
    assertEquals("5", arv.getElement(1));
    assertEquals("10", arv.getElement(2));
    assertEquals("20", arv.getElement(3));

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testRemoveIntegerArrayItemElements() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "integers"));
    ResourceItem array = getOnlyItem(repository, ResourceType.ARRAY, "integers");
    ResourceValue resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("10", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    ArrayResourceValue arv = (ArrayResourceValue)resourceValue;
    assertEquals(2, arv.getElementCount());
    assertEquals("10", arv.getElement(0));
    assertEquals("20", arv.getElement(1));

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = "<item>10</item>";
      int offset = document.getText().indexOf(item);
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "integers"));
    array = getOnlyItem(repository, ResourceType.ARRAY, "integers");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(1, arv.getElementCount());
    assertEquals("20", resourceValue.getValue());
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    rescans = repository.getFileRescans();
    // Try a second edit.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = "<item>20</item>";
      int offset = document.getText().indexOf(item);
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "integers"));
    array = getOnlyItem(repository, ResourceType.ARRAY, "integers");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(0, arv.getElementCount());

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testAddTypedArrayItemElements() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "my_colors"));
    ResourceItem array = getOnlyItem(repository, ResourceType.ARRAY, "my_colors");
    ResourceValue resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("#FFFF0000", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    ArrayResourceValue arv = (ArrayResourceValue)resourceValue;
    assertEquals(3, arv.getElementCount());
    assertEquals("#FFFF0000", arv.getElement(0));
    assertEquals("#FF00FF00", arv.getElement(1));
    assertEquals("#FF0000FF", arv.getElement(2));

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item>#FFFF0000</item>");
      document.insertString(offset, "<item>#FFFFFF00</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "my_colors"));
    array = getOnlyItem(repository, ResourceType.ARRAY, "my_colors");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("#FFFFFF00", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(4, arv.getElementCount());
    assertEquals("#FFFFFF00", arv.getElement(0));
    assertEquals("#FFFF0000", arv.getElement(1));
    assertEquals("#FF00FF00", arv.getElement(2));
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    rescans = repository.getFileRescans();
    // Now try a second edit.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item>#FFFFFF00</item>");
      document.insertString(offset, "<item>#FFFFFFFF</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "my_colors"));
    array = getOnlyItem(repository, ResourceType.ARRAY, "my_colors");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("#FFFFFFFF", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(5, arv.getElementCount());
    assertEquals("#FFFFFFFF", arv.getElement(0));
    assertEquals("#FFFFFF00", arv.getElement(1));
    assertEquals("#FFFF0000", arv.getElement(2));
    assertEquals("#FF00FF00", arv.getElement(3));

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testGradualEdits() throws Exception {
    // Gradually type in the contents of a value file and make sure we end up with a valid view of the world
    VirtualFile file1 = myFixture.copyFileToProject(VALUES_EMPTY, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.deleteString(0, document.getTextLength());
      documentManager.commitDocument(document);
    });

    String contents =
      "<!--\n" +
      "  -->\n" +
      "\n" +
      "<resources xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\">\n" +
      "\n" +
      "    <!-- Titles -->\n" +
      "    <string name=\"app_name\">Animations Demo</string>\n" +
      "    <string name=\"title_zoom\">Zoom</string>\n" +
      "    <string name=\"title_layout_changes\">Layout Changes</string>\n" +
      "    <string name=\"title_template_step\">Step <xliff:g id=\"step_number\">%1$d</xliff:g>: Lorem\n" +
      "        Ipsum</string>\n" +
      "    <string name=\"ellipsis\">Here it is: \\u2026!</string>\n" +
      "\n" +
      "    <item type=\"id\" name=\"action_next\" />\n" +
      "\n" +
      "    <style name=\"DarkActionBar\" parent=\"android:Widget.Holo.ActionBar\">\n" +
      "        <item name=\"android:background\">@android:color/transparent</item>\n" +
      "    </style>\n" +
      "\n" +
      "    <integer name=\"card_flip_time_half\">150</integer>\n" +
      "\n" +
      "    <declare-styleable name=\"MyCustomView\">\n" +
      "        <attr name=\"watchType\" format=\"enum\">\n" +
      "            <enum name=\"type_countdown\" value=\"0\"/>\n" +
      "        </attr>\n" +
      "       <attr name=\"crash\" format=\"boolean\" />\n" +
      "    </declare-styleable>\n" +
      "</resources>\n";
    for (int i = 0; i < contents.length(); i++) {
      int offset = i;
      char character = contents.charAt(i);
      WriteCommandAction.runWriteCommandAction(null, () -> {
        document.insertString(offset, String.valueOf(character));
        documentManager.commitDocument(document);
      });
    }

    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isGreaterThan(rescans);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar"));
    ResourceItem style = getOnlyItem(repository, ResourceType.STYLE, "DarkActionBar");
    StyleResourceValue styleValue = (StyleResourceValue)style.getResourceValue();
    assertNotNull(styleValue);
    ResourceValue actionBarStyle = styleValue.getItem(ANDROID, "background");
    assertNotNull(actionBarStyle);
    assertEquals("@android:color/transparent", actionBarStyle.getValue());
    assertEquals("Zoom", getOnlyItem(repository, ResourceType.STRING, "title_zoom").getResourceValue().getValue());
  }

  public void testInvalidValueName() throws Exception {
    VirtualFile file = myFixture.copyFileToProject(VALUES_EMPTY, "res/values/empty.xml");
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    assertNotNull(documentManager);
    Document document = documentManager.getDocument(psiFile);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(0, document.getTextLength() - 1, "<resources>\n<string name=\"\"\n</resources>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING).keySet()).isEmpty();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(0, document.getTextLength() - 1, "<resources>\n<string name=\"foo bar\"\n</resources>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING).keySet()).isEmpty();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(0,
                             document.getTextLength() - 1,
                             "<resources>\n<declare-styleable name=\"foo\"><attr format=\"boolean\" name=\"\"\n</declare-styleable></resources>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.ATTR).keySet()).isEmpty();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(0,
                             document.getTextLength() - 1,
                             "<resources>\n<declare-styleable name=\"foo\"><attr format=\"boolean\" name=\"foo bar\"\n</declare-styleable></resources>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.ATTR).keySet()).isEmpty();

    // Now exercise childAdded, without a full rescan.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(0, document.getTextLength() - 1, "<resources>\n\n</resources>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.insertString(document.getLineStartOffset(1), "<string name=\"\">foo</string>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING).keySet()).isEmpty();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.insertString(document.getLineStartOffset(1), "<string name=\"foo bar\">foo</string>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING).keySet()).isEmpty();
  }

  public void testInvalidId() throws Exception {
    VirtualFile file = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet())
        .containsExactly("btn_title_refresh", "header", "noteArea", "text2", "title_refresh_progress");
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    assertNotNull(documentManager);
    Document document = documentManager.getDocument(psiFile);
    assertNotNull(document);

    XmlTag tag = findTagById(psiFile, "noteArea");
    assertNotNull(tag);
    WriteCommandAction.runWriteCommandAction(null, () -> {
      // First edit runs the PSI parser, convering ResourceFile to PsiResourceFile.
      tag.setAttribute(ATTR_ID, ANDROID_URI, "@+id/forcePsiConversion");
    });

    // Check an empty resource name.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      tag.setAttribute(ATTR_ID, ANDROID_URI, "@+id/");
    });

    waitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet())
        .containsExactly("btn_title_refresh", "header", "text2", "title_refresh_progress");

    // Check an invalid resource name.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      tag.setAttribute(ATTR_ID, ANDROID_URI, "@+id/foo bar");
    });

    waitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet())
        .containsExactly("btn_title_refresh", "header", "text2", "title_refresh_progress");
  }

  public void testInvalidFileResourceName() {
    // Test generation loading.
    myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/drawable/foo.bar.xml");
    ResourceFolderRepository repository = createRepository(false);
    assertThat(repository.getResources(RES_AUTO, ResourceType.DRAWABLE)).isEmpty();
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID)).isEmpty();

    // Test adding a file after repository has been loaded.
    repository = createRegisteredRepository();
    myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/drawable/bar.baz.xml");
    assertThat(repository.getResources(RES_AUTO, ResourceType.DRAWABLE)).isEmpty();
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID)).isEmpty();
  }

  public void testIssue36973561() throws Exception {
    // Test deleting a string; ensure that the whole repository is updated correctly.
    // Regression test for https://issuetracker.google.com/36973561.
    VirtualFile file1 = myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name2"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String string = "    <string name=\"hello_world\">Hello world!</string>";
      int offset = document.getText().indexOf(string);
      assertTrue(offset != -1);
      document.deleteString(offset, offset + string.length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "action_settings"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    // However, the second edit can then be incremental.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String string = "    <string name=\"app_name\">My Application 574</string>";
      int offset = document.getText().indexOf(string);
      assertTrue(offset != -1);
      document.deleteString(offset, offset + string.length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "action_settings"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  public void testEditXmlProcessingInstructionAttrInValues() throws Exception {
    // Test editing an attribute in the XML prologue of a values file.
    VirtualFile file1 = myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name2"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String string = "utf-8";
      int offset = document.getText().indexOf(string);
      assertTrue(offset != -1);
      document.insertString(offset, "t");
      documentManager.commitDocument(document);
    });

    // Edits in XML processing instructions have no effect on the resource repository.
    assertThat(repository.getModificationCount()).isEqualTo(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.
    waitForUpdates(repository);
  }

  public void testEditXmlProcessingInstructionAttrInLayout() throws Exception {
    // Test editing an attribute in the XML prologue of a layout file with IDs.
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String string = "utf-8";
      int offset = document.getText().indexOf(string);
      assertTrue(offset != -1);
      document.insertString(offset, "t");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    // Edits in XML processing instructions have no effect on the resource repository.
    assertThat(repository.getModificationCount()).isEqualTo(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.
  }

  public void testIssue36986886() throws Exception {
    // Regression test for https://issuetracker.google.com/36986886.
    // If you duplicate a string, then change its contents (which still duplicated),
    // and then finally rename the string, then the value of the second clone will
    // continue to be referred from the first string:
    //    <string name="foo">value 1</foo>
    //    <string name="foo">value 2</foo>
    // then change the second string name to
    //    <string name="foo2">value 2</foo>
    // If you now evaluate the value of foo, you get "value 1". Basically while the
    // two strings are (illegally) aliasing, the value of the first string is replaced.

    // TODO: Test both *duplicating* a node, as well as manually typing in a brand new one with the same result.

    VirtualFile file1 = myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertEquals("My Application 574",
                 repository.getResources(RES_AUTO, ResourceType.STRING, "app_name").get(0).getResourceValue().getValue());

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    int offset = document.getText().indexOf("</resources>");
    assertTrue(offset != -1);
    String string = "<string name=\"app_name\">New Value</string>";

    // First duplicate the line:
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      document.insertString(offset, string);
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    // Second edit (duplicate again).
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    int offset2 = document.getText().indexOf("</resources>");
    String string2 = "<string name=\"app_name\">Another Value</string>";
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      document.insertString(offset2, string2);
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));

    // Then replace the name of the duplicated string.
    generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      int startOffset = offset + "<string name=\"".length();
      document.replaceString(startOffset, startOffset + "app_name".length(), "new_name");
      documentManager.commitDocument(document);
    });

    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "new_name"));

    assertEquals("New Value",
                 repository.getResources(RES_AUTO, ResourceType.STRING, "new_name").get(0).getResourceValue().getValue());
    assertEquals("My Application 574",
                 repository.getResources(RES_AUTO, ResourceType.STRING, "app_name").get(0).getResourceValue().getValue());

    // Replace the second duplicate.
    generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      int startOffset = offset2 + "<string name=\"".length();
      document.replaceString(startOffset, startOffset + "app_name".length(), "new_name2");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "new_name"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "new_name2"));

    assertEquals("New Value",
                 repository.getResources(RES_AUTO, ResourceType.STRING, "new_name").get(0).getResourceValue().getValue());
    assertEquals("Another Value",
                 repository.getResources(RES_AUTO, ResourceType.STRING, "new_name2").get(0).getResourceValue().getValue());
    assertEquals("My Application 574",
                 repository.getResources(RES_AUTO, ResourceType.STRING, "app_name").get(0).getResourceValue().getValue());
  }

  public void testLoadDuplicatedValues() throws Exception {
    // Test loading up a file that already illegally had duplicates.
    VirtualFile file1 = myFixture.copyFileToProject(VALUES_WITH_DUPES, "res/values/values.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STRING, "dupe_name"));
    assertEquals("Animations Demo",
                 repository.getResources(RES_AUTO, ResourceType.STRING, "app_name").get(0).getResourceValue().getValue());

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Try editting one of the duplicated string contents, and check that the copies are not tied together.
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      String origString = "<string name=\"app_name\">Animations Demo</string>";
      String newString = "<string name=\"dupe_name\">Duplicate Demo</string>";
      int offset = document.getText().indexOf(origString);
      document.replaceString(offset, offset + origString.length(), newString);
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "dupe_name"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertEquals("Duplicate Demo",
                 repository.getResources(RES_AUTO, ResourceType.STRING, "dupe_name").get(0).getResourceValue().getValue());
    assertEquals("Animations Demo",
                 repository.getResources(RES_AUTO, ResourceType.STRING, "app_name").get(0).getResourceValue().getValue());
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    // Try editting something else, like the ID item.
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "action_prev"));
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      String origString = "<item type=\"id\" name=\"action_next\" />";
      String newString = "<item type=\"id\" name=\"action_prev\" />";
      int offset = document.getText().indexOf(origString);
      document.replaceString(offset, offset + origString.length(), newString);
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "action_prev"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.
  }

  /** Regression test for b/115880623 */
  public void testRemoveDuplicate() throws Exception {
    VirtualFile valuesXml = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    ResourceFolderRepository repository = createRegisteredRepository();
    myFixture.openFileInEditor(valuesXml);

    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING, "app_name")).hasSize(1);

    moveCaret(myFixture, "<string name=\"|app_name");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DUPLICATE);
    commitAndWaitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING, "app_name")).hasSize(2);

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE_LINE);
    commitAndWaitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING, "app_name")).hasSize(1);

    moveCaret(myFixture, "<string name=\"|app_name");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DUPLICATE);
    commitAndWaitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING, "app_name")).hasSize(2);

    myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE);
    commitAndWaitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING, "app_name")).hasSize(1);
  }

  public void testLoadValuesWithBadName() throws Exception {
    // If a file had bad value names, test that it can still be parsed.
    VirtualFile file = myFixture.copyFileToProject(VALUES_WITH_BAD_NAME, "res/values/values.xml");
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING)).isEmpty();

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      String origString = "<string name=\"app*name\">Animations Demo</string>";
      String newString = "<string name=\"app_name\">Fixed Animations Demo</string>";
      int offset = document.getText().indexOf(origString);
      document.replaceString(offset, offset + origString.length(), newString);
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    List<ResourceItem> items = repository.getResources(RES_AUTO, ResourceType.STRING, "app_name");
    assertThat(items).hasSize(1);
    assertThat(items.get(0).getResourceValue().getValue()).isEqualTo("Fixed Animations Demo");
  }

  /**
   * Test for http://b/138841328.
   */
  public void testLoadAfterExternalChangeToLayout() throws Exception {
    myFixture.copyFileToProject(LAYOUT_WITHOUT_IDS, "res/layout/layout.xml");

    ResourceFolderRepository repository = createRepository(true);
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID)).isEmpty();
    assertThat(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout")).hasSize(1);

    TestUtils.waitForFileSystemTick();
    myFixture.copyFileToProject(LAYOUT_WITH_ONE_ID, "res/layout/layout.xml");

    ResourceFolderRepository resourcesReloaded = createRepository(false);
    assertThat(resourcesReloaded.getResources(RES_AUTO, ResourceType.ID, "foo")).hasSize(1);
    assertThat(resourcesReloaded.getResources(RES_AUTO, ResourceType.LAYOUT, "layout")).hasSize(1);
  }

  public void testIdScanFromLayout() {
    // Test for https://issuetracker.google.com/37044944.
    myFixture.copyFileToProject(LAYOUT_ID_SCAN, "res/layout/layout1.xml");
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    Collection<String> ids = repository.getResources(RES_AUTO, ResourceType.ID).keySet();
    assertNotNull(ids);
    assertContainsElements(ids, "header", "image", "styledView", "imageView", "imageView2", "imageButton", "nonExistent");
  }

  public void testInitFromHelperThread() throws Exception {
    // By default, unit tests run from the EDT thread, which automatically have read access. Try loading a repository from a
    // helper thread that doesn't have read access to make sure we grab the appropriate read locks.
    // Use a data binding file, which we currently know uses a PsiDataBindingResourceItem.
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT_WITH_DATA_BINDING, "res/layout-land/layout_with_data_binding.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    VirtualFile file2 = myFixture.copyFileToProject(VALUES_WITH_DUPES, "res/values-en/values_with_dupes.xml");
    ExecutorService executorService = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(getTestName(false));
    Future<ResourceFolderRepository> loadJob = executorService.submit(this::createRegisteredRepository);
    ResourceFolderRepository repository = loadJob.get();
    assertNotNull(repository);
    assertEquals("land", getOnlyItem(repository, ResourceType.LAYOUT, "layout_with_data_binding").getConfiguration().getQualifierString());
    ResourceItem dupedStringItem = repository.getResources(RES_AUTO, ResourceType.STRING, "app_name").get(0);
    assertNotNull(dupedStringItem);
    assertEquals("en", dupedStringItem.getConfiguration().getQualifierString());
  }

  public void testEditColorStateList() {
    VirtualFile file1 = myFixture.copyFileToProject(COLOR_STATELIST, "res/color/my_state_list.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.COLOR, "my_state_list"));

    // Edit comment
    long generation = repository.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf(" -->");
      document.replaceString(offset, offset, "more comment");
      documentManager.commitDocument(document);
    });

    // Shouldn't have caused any change.
    assertThat(repository.getModificationCount()).isEqualTo(generation);
    assertThat(repository.getFileRescans()).isEqualTo(0);

    // Edit processing instruction.
    generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("utf-8");
      document.replaceString(offset, offset + 5, "other encoding");
      documentManager.commitDocument(document);
    });

    // Shouldn't have caused any change.
    assertThat(repository.getModificationCount()).isEqualTo(generation);
    assertThat(repository.getFileRescans()).isEqualTo(0);

    // Edit state list
    generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("myColor");
      document.replaceString(offset, offset + 7, "myNewColor");
      documentManager.commitDocument(document);
    });

    // Should have caused a modification but not a rescan.
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(0);
  }

  /**
   * Checks that whitespace edits do not trigger a rescan.
   */
  public void testEmptyEdits() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Add a space to an attribute name.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("app_name");
      document.insertString(offset, "_");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "_app_name"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    // Try a second edit, adding another space.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("_app_name");
      document.insertString(offset, "_");
      documentManager.commitDocument(document);
    });

    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "__app_name"));
    ResourceItem item = getOnlyItem(repository, ResourceType.STRING, "title_zoom");
    assertEquals("Zoom", item.getResourceValue().getValue());
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.

    generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("Zoom");
      document.deleteString(offset, offset + "Zoom".length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.

    // Inserting spaces in the middle of a tag shouldn't trigger a rescan or even change the modification count.
    generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("Card Flip");
      document.insertString(offset, "   ");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isEqualTo(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  /**
   * Basic test to show that a load from an empty file cache doesn't pull things out of thin air
   * and that the file cache is considered stale (as a signal that updating would be good).
   */
  public void testFileCacheFreshness() {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout-xlarge-land/layout.xml");
    myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    ResourceFolderRepository repository = createRepository(true);
    assertNotNull(repository);
    assertFalse(repository.hasFreshFileCache());
    assertEquals(3, repository.getNumXmlFilesLoadedInitially());
    assertEquals(repository.getNumXmlFilesLoadedInitially(), repository.getNumXmlFilesLoadedInitiallyFromSources());

    ResourceFolderRepository resourcesReloaded = createRepository(false);
    assertNotSame(repository, resourcesReloaded);
    assertTrue(resourcesReloaded.hasFreshFileCache());
    assertEquals(3, resourcesReloaded.getNumXmlFilesLoadedInitially());
    assertEquals(0, resourcesReloaded.getNumXmlFilesLoadedInitiallyFromSources());
  }

  public void testSerialization() {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout-xlarge-land/layout.xml");
    myFixture.copyFileToProject(LAYOUT_WITH_DATA_BINDING, "res/layout/layout_with_data_binding.xml");
    myFixture.copyFileToProject(DRAWABLE, "res/drawable/logo.png");
    myFixture.copyFileToProject(DRAWABLE, "res/drawable-hdpi/logo.png");
    myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    myFixture.copyFileToProject(STRINGS, "res/values-fr/not_really_french_strings.xml");
    myFixture.copyFileToProject(XLIFF, "res/values/xliff.xml");
    ResourceFolderRepository repository = createRepository(true);
    assertNotNull(repository);
    assertFalse(repository.hasFreshFileCache());
    assertEquals(7, repository.getNumXmlFilesLoadedInitially());
    assertEquals(repository.getNumXmlFilesLoadedInitially(), repository.getNumXmlFilesLoadedInitiallyFromSources());

    ResourceFolderRepository fromCacheFile = createRepository(true);
    assertNotNull(fromCacheFile);
    // Check that fromCacheFile really avoided reparsing some XML files, before checking equivalence of items.
    assertTrue(fromCacheFile.hasFreshFileCache());
    assertEquals(7, fromCacheFile.getNumXmlFilesLoadedInitially());
    assertEquals(0, fromCacheFile.getNumXmlFilesLoadedInitiallyFromSources());

    assertNotSame(repository, fromCacheFile);
    assertContainSameData(myFacet, repository, fromCacheFile);
  }

  public void testInvalidateCache() {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout-xlarge-land/layout.xml");
    myFixture.copyFileToProject(LAYOUT_WITH_DATA_BINDING, "res/layout/layout_with_data_binding.xml");
    myFixture.copyFileToProject(DRAWABLE, "res/drawable/logo.png");
    myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    ResourceFolderRepository repository = createRepository(true);
    assertNotNull(repository);
    ResourceFolderRepositoryFileCacheService.get().invalidate();
    ResourceFolderRepository resourcesReloaded = createRepository(true);

    assertNotSame(0, resourcesReloaded.getNumXmlFilesLoadedInitiallyFromSources());
    assertEquals(resourcesReloaded.getNumXmlFilesLoadedInitially(), resourcesReloaded.getNumXmlFilesLoadedInitiallyFromSources());
  }

  public void testSerializationRemoveXmlFileAndLoad() {
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    myFixture.copyFileToProject(DRAWABLE, "res/drawable/logo.png");
    VirtualFile file2 = myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    ResourceFolderRepository repository = createRepository(true);
    assertNotNull(repository);

    // Check "repository" before deletion, and "fromCacheFile" after deletion.
    // Note that the in-memory "repository" also gets updated from a Psi listener
    // so save to blob before picking up the Psi change.
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));

    // Delete a non-value file.
    WriteCommandAction.runWriteCommandAction(null, psiFile1::delete);

    ResourceFolderRepository fromCacheFile = createRepository(true);
    assertNotNull(fromCacheFile);
    // Non-value files aren't counted in the cache, so deleting doesn't affect freshness.
    assertTrue(fromCacheFile.hasFreshFileCache());

    assertFalse(fromCacheFile.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout"));
    assertFalse(fromCacheFile.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
    assertTrue(fromCacheFile.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo"));
    assertTrue(fromCacheFile.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));

    // Delete a value file.
    PsiFile psiFile2 = PsiManager.getInstance(getProject()).findFile(file2);
    assertNotNull(psiFile2);
    WriteCommandAction.runWriteCommandAction(null, psiFile2::delete);

    ResourceFolderRepository fromCacheFile2 = createRepository(true);
    assertNotNull(fromCacheFile2);
    // Value files are counted in the cache, but we only count the percentage re-parsed for freshness.
    // We don't count extraneous cache entries (but perhaps we should).
    assertTrue(fromCacheFile2.hasFreshFileCache());

    assertFalse(fromCacheFile2.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout"));
    assertFalse(fromCacheFile2.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
    assertTrue(fromCacheFile2.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo"));
    assertFalse(fromCacheFile2.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));
  }

  public void testSerializationRemoveDrawableFileAndLoad() {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout.xml");
    VirtualFile file1 = myFixture.copyFileToProject(DRAWABLE, "res/drawable/logo.png");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    ResourceFolderRepository repository = createRepository(true);
    assertNotNull(repository);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));

    WriteCommandAction.runWriteCommandAction(null, psiFile1::delete);

    ResourceFolderRepository fromCacheFile = createRepository(true);
    assertNotNull(fromCacheFile);

    assertTrue(fromCacheFile.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout"));
    assertTrue(fromCacheFile.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
    assertFalse(fromCacheFile.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo"));
    assertTrue(fromCacheFile.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));
  }

  public void testSerializationEditXmlFileAndLoad() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    File file1AsFile = virtualToIoFile(file1);
    assertNotNull(file1AsFile);
    ResourceFolderRepository repository = createRepository(true);
    assertNotNull(repository);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));

    // Editing via the DocumentManager.commitDocument interface doesn't touch the lastModified
    // stamp of the actual file in time, so just edit the file directly and bump the lastModified
    // value higher just in case this test runs more quickly than the granularity of timestamps.
    String oldFileContent = FileUtilRt.loadFile(file1AsFile);
    String newFileContent = oldFileContent.replace("hello_world", "hello_there");
    FileUtil.writeToFile(file1AsFile, newFileContent);
    if (!file1AsFile.setLastModified(file1AsFile.lastModified() + 2000)) {
      // Not supported on this platform?
      return;
    }
    file1.refresh(false, false);

    ResourceFolderRepository fromCacheFile = createRepository(true);
    assertNotNull(fromCacheFile);
    assertFalse(fromCacheFile.hasFreshFileCache());

    assertTrue(fromCacheFile.hasResources(RES_AUTO, ResourceType.STRING, "hello_there"));
  }

  public void testSerializationAddXmlFileAndLoad() {
    myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    ResourceFolderRepository repository = createRepository(true);
    assertNotNull(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));

    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout.xml");

    ResourceFolderRepository fromCacheFile = createRepository(false);
    assertNotNull(fromCacheFile);
    // Freshness depends on a heurisitic, but now half the XML files are parsed.
    assertFalse(fromCacheFile.hasFreshFileCache());

    assertTrue(fromCacheFile.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));
    assertTrue(fromCacheFile.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout"));
    assertTrue(fromCacheFile.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
  }

  public void testSerializationAddDrawableFileAndLoad() {
    myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    ResourceFolderRepository repository = createRepository(true);
    assertNotNull(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo"));

    myFixture.copyFileToProject(DRAWABLE, "res/drawable/logo.png");

    ResourceFolderRepository fromCacheFile = createRepository(true);
    assertNotNull(fromCacheFile);
    // Freshness depends on a heurisitic, but we don't count PNG in the blob.
    assertTrue(fromCacheFile.hasFreshFileCache());

    assertTrue(fromCacheFile.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));
    assertTrue(fromCacheFile.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo"));
  }

  public void testSerializeLayoutAndIdResourceValues() {
    myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout/activity_foo.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout-xlarge-land/activity_foo.xml");
    ResourceFolderRepository repository = createRepository(true);
    assertNotNull(repository);
    FolderConfiguration config = FolderConfiguration.getConfigForFolder("layout-xlarge-land");
    assertNotNull(config);
    // For layouts, the ResourceValue#getValue is the file path.
    ResourceValue value = ResourceRepositoryUtil.getConfiguredValue(repository, ResourceType.LAYOUT, "activity_foo", config);
    assertNotNull(value);
    String valueString = value.getValue();
    assertNotNull(valueString);
    assertTrue(valueString.endsWith("activity_foo.xml"));
    assertTrue(valueString.contains("layout-xlarge-land"));

    ResourceFolderRepository fromCacheFile = createRepository(true);
    assertNotNull(fromCacheFile);
    assertTrue(fromCacheFile.hasFreshFileCache());

    assertNotSame(repository, fromCacheFile);
    assertContainSameData(myFacet, repository, fromCacheFile);
    value = ResourceRepositoryUtil.getConfiguredValue(fromCacheFile, ResourceType.LAYOUT, "activity_foo", config);
    assertNotNull(value);
    valueString = value.getValue();
    assertNotNull(valueString);
    assertTrue(valueString.endsWith("activity_foo.xml"));
    assertTrue(valueString.contains("layout-xlarge-land"));
  }

  public void testSerializeDensityBasedResourceValues() {
    myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/drawable-hdpi/drawable_foo.xml");
    myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/drawable-xhdpi/drawable_foo.xml");
    myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/drawable-fr/drawable_foo.xml");
    ResourceFolderRepository repository = createRepository(true);
    assertNotNull(repository);
    FolderConfiguration config = FolderConfiguration.getConfigForFolder("drawable-xhdpi");
    assertNotNull(config);
    // For drawable xml, the ResourceValue#getValue is the file path.
    ResourceValue value = ResourceRepositoryUtil.getConfiguredValue(repository, ResourceType.DRAWABLE, "drawable_foo", config);
    assertNotNull(value);
    String valueString = value.getValue();
    assertNotNull(valueString);
    assertTrue(valueString.endsWith("drawable_foo.xml"));
    assertTrue(valueString.contains("drawable-xhdpi"));
    DensityBasedResourceValue densityValue = (DensityBasedResourceValue)value;
    assertEquals(Density.XHIGH, densityValue.getResourceDensity());

    ResourceFolderRepository fromCacheFile = createRepository(true);
    assertNotNull(fromCacheFile);
    // We don't count files that we explicitly skip against freshness.
    assertTrue(fromCacheFile.hasFreshFileCache());

    assertNotSame(repository, fromCacheFile);
    assertContainSameData(myFacet, repository, fromCacheFile);
    value = ResourceRepositoryUtil.getConfiguredValue(fromCacheFile, ResourceType.DRAWABLE, "drawable_foo", config);
    assertNotNull(value);
    valueString = value.getValue();
    assertNotNull(valueString);
    assertTrue(valueString.endsWith("drawable_foo.xml"));
    assertTrue(valueString.contains("drawable-xhdpi"));
    // Make sure that the resource value is still of type DensityBasedResourceValue.
    densityValue = (DensityBasedResourceValue)value;
    assertEquals(Density.XHIGH, densityValue.getResourceDensity());
  }

  /**
   * Checks that we handle PSI invalidation behaviour for PsiResourceItem.
   * When getting out of dumb mode, if the file has been modified during the dumb mode,
   * the file (and tags) will be invalidated.
   * Regression test for http://b/73623886.
   */
  public void testFileInvalidationAfterDumbMode() throws Exception {
    DumbServiceImpl dumbService = (DumbServiceImpl)DumbService.getInstance(getProject());
    dumbService.setDumb(true);
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile);
    ResourceFolderRepository repository = createRegisteredRepository();
    assertNotNull(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));

    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("DarkTheme");
      document.replaceString(offset, offset + 4, "Grey");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    ResourceItem item = repository.getResources(RES_AUTO, ResourceType.STYLE, "GreyTheme").get(0);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    dumbService.setDumb(false);
    // Before the fix, item.getResourceValue would return null since the file is not invalid after getting out of dumb mode.
    assertNotNull(item.getResourceValue());
  }

  public void testAddingPlusToId() throws Exception {
    PsiFile layout = myFixture.addFileToProject("res/layout/my_layout.xml",
                                              // language=XML
                                              "<LinearLayout xmlns:android='http://schemas.android.com/apk/res/android'>" +
                                              "  <TextView android:id='@id/aaa' />" +
                                              "  <TextView android:id='@id/bbb' />" +
                                              "  <TextView android:id='@id/ccc' />" +
                                              "</LinearLayout>");
    myFixture.openFileInEditor(layout.getVirtualFile());

    ResourceFolderRepository repository = createRegisteredRepository();
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "aaa"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "bbb"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "ccc"));
    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();

    type("@|id/aaa", "+");
    waitForUpdates(repository);

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "aaa"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "bbb"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "ccc"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    type("@|id/bbb", "+");
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "aaa"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "bbb"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.ID, "ccc"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.

    // Now try setAttribute which triggers a different PsiEvent, similar to pasting.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    XmlTag cccTag = findTagById(layout, "ccc");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      cccTag.setAttribute(ATTR_ID, ANDROID_URI, "@+id/ccc");
    });

    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "aaa"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "bbb"));
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.ID, "ccc"));
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.
  }

  /**
   * This test checks that when the content of a bitmap is updated, the resource repository is notified.
   * <p>
   * We do that by checking that when an image content is changed from red to blue,
   * LayoutLib clears its caches.
   * <p>
   * b/129668736
   */
  public void testBitmapUpdated() throws Exception {
    VirtualFile logoFile = myFixture.copyFileToProject(DRAWABLE_RED, "res/drawable/logo.png");
    ResourceFolderRepository repository = createRegisteredRepository();
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo"));
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(logoFile);
    DrawableRenderer renderer = new DrawableRenderer(myFacet, configuration);

    String bitmapXml = "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                       "    <background android:drawable=\"@drawable/logo\"/>\n" +
                       "    <foreground android:drawable=\"@drawable/logo\"/>\n" +
                       "</adaptive-icon>";
    int red = renderer.renderDrawable(bitmapXml, COLORED_DRAWABLE_SIZE).join().getRGB(0, 0);

    // We don't check the alpha byte because its value is not FF as expected but
    // that is not significant for this test.
    assertEquals("ff0000", Integer.toHexString(red).substring(2));

    byte[] newContent = Files.readAllBytes(Paths.get(myFixture.getTestDataPath(), DRAWABLE_BLUE));
    WriteAction.run(() -> logoFile.setBinaryContent(newContent));

    waitForCondition(10, TimeUnit.SECONDS, () -> {
      int blue = renderer.renderDrawable(bitmapXml, COLORED_DRAWABLE_SIZE).join().getRGB(0, 0);
      return "0000ff".equals(Integer.toHexString(blue).substring(2));
    });
  }

  /**
   * When the IDE enters or exits dumb mode, the cache mapping the VirtualFile directories to PsiDirectory
   * ({@link FileManagerImpl#getVFileToPsiDirMap()}) is cleared from the {@link com.intellij.openapi.project.DumbService.DumbModeListener}.
   * <p>
   * Dumb mode is entered any time a file is added or deleted.
   * <p>
   * When a file is created in a directory that is not cached in this map,
   * {@link com.intellij.psi.impl.file.impl.PsiVFSListener#fileCreated} will trigger
   * a {@link com.intellij.psi.PsiTreeChangeEvent#PROP_UNLOADED_PSI} event, which is not handled by
   * the {@link ResourceFolderRepository.IncrementalUpdatePsiListener} so the new file is never added to the repository.
   * <p>
   * Instead of relying on the PSI system for file change event, we now rely on the VFS system which does not suffer from this
   * problem.
   * <p>
   * See http://b/130800515
   */
  public void testRepositoryUpdatedAfterDumbMode() throws Exception {
    ResourceFolderRepository repository = createRegisteredRepository();
    VirtualFile dir = myFixture.copyFileToProject(DRAWABLE, "res/drawable/image.png").getParent();
    VirtualFile file = VfsUtil.findFileByIoFile(new File(myFixture.getTestDataPath(), DRAWABLE), true);

    // Trigger dumb mode to clear the PsiDirectory cache.
    ((DumbServiceImpl)DumbService.getInstance(myModule.getProject())).setDumb(true);
    ((DumbServiceImpl)DumbService.getInstance(myModule.getProject())).setDumb(false);
    WriteCommandAction.runWriteCommandAction(
      myModule.getProject(), () -> {
        try {
          file.copy(this, dir, "image" + 0 + ".png");
        }
        catch (IOException e) {
          fail(e.getMessage());
        }
      }
    );
    waitForUpdates(repository);
    assertEquals(2, repository.getResources(RES_AUTO, ResourceType.DRAWABLE).size());
  }

  /**
   * Simulates a common case when git is used in background resulting in a VirtualFile with no Document being modified.
   */
  public void testFileWithNoDocument() throws Exception {
    VirtualFile valuesXmlVirtualFile = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    ResourceFolderRepository repository = createRegisteredRepository();

    File valuesXmlFile = virtualToIoFile(valuesXmlVirtualFile);
    FileUtil.writeToFile(valuesXmlFile, "<resources><string name='from_git'>git</string></resources>");
    LocalFileSystem.getInstance().refresh(false);
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "from_git"));
  }

  /**
   * Simulates what project templates do, modififying Documents that have no PSI (yet). Since PSI can
   * be garbage-collected at any point, this is possible in other cases as well.
   */
  public void testFileWithNoPsi() throws Exception {
    VirtualFile valuesXml = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    ResourceFolderRepository repository = createRegisteredRepository();

    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    Document document = fileDocumentManager.getDocument(valuesXml);

    // Sanity check:
    assertThat(PsiDocumentManager.getInstance(getProject()).getCachedPsiFile(document)).named("Cached PSI").isNull();

    WriteAction.run(() -> {
      document.setText("<resources><string name='from_templates'>git</string></resources>");
      fileDocumentManager.saveDocument(document);
    });
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "from_templates"));
  }

  public void testUnsavedDocument_noCache() throws Exception {
    myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    VirtualFile resourceDirectory = getResourceDirectory();
    VirtualFile stringsXml = VfsTestUtil.createFile(resourceDirectory,
                                                    "values/strings.xml",
                                                    // language=XML
                                                    "<resources>" +
                                                    "  <string name='onDisk'>foo bar</string>" +
                                                    "</resources>");
    myFixture.openFileInEditor(stringsXml);
    moveCaret(myFixture, "name='|onDisk'");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_END);
    myFixture.type("inDocument");

    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    assertTrue("Unsaved changes in Document", fileDocumentManager.isFileModified(stringsXml));
    ResourceFolderRepository repository = createRepository(true);
    waitForUpdates(repository);
    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "inDocument"));
    assertFalse(repository.hasResources(RES_AUTO, ResourceType.STRING, "onDisk"));
    assertFalse(repository.hasFreshFileCache()); // Make sure we write the cache.
    assertTrue("Unsaved changes in Document", fileDocumentManager.isFileModified(stringsXml));
  }

  public void testUnsavedDocument_cache() throws Exception {
    myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    VirtualFile resourceDirectory = getResourceDirectory();
    VirtualFile stringsXml = VfsTestUtil.createFile(resourceDirectory,
                                                    "values/strings.xml",
                                                    // language=XML
                                                    "<resources>" +
                                                    "  <string name='onDisk'>foo bar</string>" +
                                                    "</resources>");
    ResourceFolderRepository repository = createRepository(true);
    assertFalse(repository.hasFreshFileCache()); // Make sure we write the cache.

    myFixture.openFileInEditor(stringsXml);
    moveCaret(myFixture, "name='|onDisk'");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_END);
    myFixture.type("inDocument");

    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    assertTrue("Unsaved changes in Document", fileDocumentManager.isFileModified(stringsXml));

    ResourceFolderRepository repository2 = createRepository(true);
    waitForUpdates(repository2);
    assertTrue(repository2.hasResources(RES_AUTO, ResourceType.STRING, "inDocument"));
    assertFalse(repository2.hasResources(RES_AUTO, ResourceType.STRING, "onDisk"));
    assertTrue("Unsaved changes in Document", fileDocumentManager.isFileModified(stringsXml));
  }

  public void testIdDeclarationInNonIdAttribute() throws Exception {
    PsiFile layout = myFixture.addFileToProject(
      "res/layout/my_layout.xml",
      // language=XML
      "<LinearLayout\n" +
      "     xmlns:android='http://schemas.android.com/apk/res/android'\n" +
      "     xmlns:app='http://schemas.android.com/apk/res-auto'>\n" +
      "  <TextView android:id='@id/a' />\n" +
      "  <TextView app:layout_constraintTop_toTopOf='@+id/a' />\n" +
      "</LinearLayout>\n");
    myFixture.openFileInEditor(layout.getVirtualFile());

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a");

    repository.scheduleScan(layout.getVirtualFile());
    waitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a");

    type("@+id/a|", "a");
    commitAndWaitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("aa");
  }

  /**
   * Regression test for b/138007389.
   */
  public void testDuplicateAndroidIdLine() throws Exception {
    ResourceFolderRepository repository = createRegisteredRepository();

    PsiFile layout = myFixture.addFileToProject(
      "res/layout/my_layout.xml",
      // language=XML
      "<LinearLayout xmlns:android='http://schemas.android.com/apk/res/android'>\n" +
      "  <TextView \n" +
      "     android:id='@+id/a'\n" +
      "     android:text='Hello' />\n" +
      "  <TextView android:id='@+id/b' />\n" +
      "</LinearLayout>\n");
    myFixture.openFileInEditor(layout.getVirtualFile());
    commitAndWaitForUpdates(repository);

    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");
    // Make sure we've switched to PSI.
    assertThat(Iterables.getOnlyElement(repository.getResources(RES_AUTO, ResourceType.ID, "a"))).isInstanceOf(PsiResourceItem.class);

    int rescans = repository.getFileRescans();
    moveCaret(myFixture, "@+id|/a");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DUPLICATE);
    commitAndWaitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.

    myFixture.type('x');
    commitAndWaitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.

    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getCaretOffset() - 8);
    myFixture.type('x');
    commitAndWaitForUpdates(repository);
    // Check the edit above is what we wanted.
    assertThat(myFixture.getFile().findElementAt(myFixture.getCaretOffset()).getText()).isEqualTo("android:ixd");
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.
  }

  public void testDuplicatePlusIdLine() throws Exception {
    ResourceFolderRepository repository = createRegisteredRepository();

    PsiFile layout = myFixture.addFileToProject(
      "res/layout/my_layout.xml",
      // language=XML
      "<LinearLayout\n" +
      "     xmlns:android='http://schemas.android.com/apk/res/android'\n" +
      "     xmlns:app='http://schemas.android.com/apk/res-auto'>\n" +
      "  <TextView\n" +
      "     app:layout_constraintTop_toTopOf='@+id/a'\n" +
      "     android:text='Hello' />\n" +
      "  <TextView android:id='@+id/b' />\n" +
      "</LinearLayout>\n");
    myFixture.openFileInEditor(layout.getVirtualFile());
    commitAndWaitForUpdates(repository);

    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");
    // Make sure we've switched to PSI.
    assertThat(Iterables.getOnlyElement(repository.getResources(RES_AUTO, ResourceType.ID, "a"))).isInstanceOf(PsiResourceItem.class);

    int rescans = repository.getFileRescans();
    moveCaret(myFixture, "@+id|/a");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DUPLICATE);
    commitAndWaitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.

    myFixture.type('x');
    commitAndWaitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.

    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getCaretOffset() - 8);
    myFixture.type('x');
    commitAndWaitForUpdates(repository);
    // Check the edit above is what we wanted.
    assertThat(myFixture.getFile().findElementAt(myFixture.getCaretOffset()).getText()).isEqualTo("app:layout_constraintTop_toTopOxf");
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.
  }

  public void testDuplicatePlusIdLineNotConverted() throws Exception {
    PsiFile layout = myFixture.addFileToProject(
      "res/layout/my_layout.xml",
      // language=XML
      "<LinearLayout\n" +
      "     xmlns:android='http://schemas.android.com/apk/res/android'\n" +
      "     xmlns:app='http://schemas.android.com/apk/res-auto'>\n" +
      "  <TextView\n" +
      "     app:layout_constraintTop_toTopOf='@+id/a'\n" +
      "     android:text='Hello' />\n" +
      "  <TextView android:id='@+id/b' />\n" +
      "</LinearLayout>\n");

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");
    assertThat(Iterables.getOnlyElement(repository.getResources(RES_AUTO, ResourceType.ID, "a"))).isNotInstanceOf(PsiResourceItem.class);

    int rescans = repository.getFileRescans();
    myFixture.openFileInEditor(layout.getVirtualFile());
    moveCaret(myFixture, "@+id|/a");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DUPLICATE);
    PsiDocumentManager.getInstance(getProject()).commitDocument(myFixture.getEditor().getDocument());
    commitAndWaitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");
    assertThat(Iterables.getOnlyElement(repository.getResources(RES_AUTO, ResourceType.ID, "b"))).isInstanceOf(PsiResourceItem.class);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
  }

  public void testAddUnrelatedAttribute() throws Exception {
    PsiFile layout = myFixture.addFileToProject(
      "res/layout/my_layout.xml",
      // language=XML
      "<LinearLayout\n" +
      "     xmlns:android='http://schemas.android.com/apk/res/android'\n" +
      "     xmlns:app='http://schemas.android.com/apk/res-auto'>\n" +
      "  <TextView\n" +
      "     app:layout_constraintTop_toTopOf='@+id/a'\n" +
      "     android:text='Hello' />\n" +
      "  <TextView android:id='@+id/b' />\n" +
      "</LinearLayout>\n");

    ResourceFolderRepository repository = createRegisteredRepository();
    myFixture.openFileInEditor(layout.getVirtualFile());
    type("@+id/b' |/>", "foo='bar'");
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    waitForUpdates(repository);
    assertThat(repository.getFileRescans()).isEqualTo(0);
  }

  public void testFontChanged() throws Exception {
    VirtualFile ttfFile = VfsTestUtil.createFile(getProjectDir(), "res/font/myfont.ttf", new byte[] { 1 });
    ResourceFolderRepository repository = createRegisteredRepository();

    getApplication().runWriteAction(() -> {
      try {
        ttfFile.setBinaryContent(new byte[] { 2 });
      }
      catch (IOException e) {
        throw new AssertionError(e);
      }
    });
    commitAndWaitForUpdates(repository);
    assertThat(repository.getLayoutlibCacheFlushes()).isGreaterThan(0);
  }

  public void testInvalidFilenames() throws Exception {
    MockWolfTheProblemSolver wolfTheProblemSolver = new MockWolfTheProblemSolver() {
      final Set<VirtualFile> problemFiles = Sets.newConcurrentHashSet();

      @Override
      public void reportProblemsFromExternalSource(@NotNull VirtualFile file, @NotNull Object source) {
        problemFiles.add(file);
      }

      @Override
      public void clearProblemsFromExternalSource(@NotNull VirtualFile file, @NotNull Object source) {
        problemFiles.remove(file);
      }

      @Override
      public boolean isProblemFile(@NotNull VirtualFile virtualFile) {
        return problemFiles.contains(virtualFile);
      }
    };
    ServiceContainerUtil.registerComponentInstance(getProject(), WolfTheProblemSolver.class, wolfTheProblemSolver, getTestRootDisposable());

    VirtualFile projectDir = getProjectDir();
    VirtualFile valid = VfsTestUtil.createFile(projectDir, "res/drawable/valid.png", new byte[] { 1 });
    VirtualFile invalidButIdentifier = VfsTestUtil.createFile(projectDir, "res/drawable/FooBar.png", new byte[] { 1 });
    VirtualFile invalid = VfsTestUtil.createFile(projectDir, "res/drawable/1st.png", new byte[] { 1 });
    ResourceFolderRepository repository = createRegisteredRepository();

    assertThat(repository.getResourceNames(RES_AUTO, ResourceType.DRAWABLE)).containsExactly("valid", "FooBar");
    assertThat(wolfTheProblemSolver.isProblemFile(valid)).isFalse();
    assertThat(wolfTheProblemSolver.isProblemFile(invalid)).isTrue();
    assertThat(wolfTheProblemSolver.isProblemFile(invalidButIdentifier)).isTrue();

    WriteAction.run(() -> {
      invalid.rename(this, "fixed.png");
      invalidButIdentifier.rename(this, "also_fixed.png");
    });
    commitAndWaitForUpdates(repository);

    assertThat(repository.getResourceNames(RES_AUTO, ResourceType.DRAWABLE)).containsExactly("valid", "fixed", "also_fixed");
    assertThat(wolfTheProblemSolver.isProblemFile(valid)).isFalse();
    assertThat(wolfTheProblemSolver.isProblemFile(invalid)).isFalse();
    assertThat(wolfTheProblemSolver.isProblemFile(invalidButIdentifier)).isFalse();
  }
}
