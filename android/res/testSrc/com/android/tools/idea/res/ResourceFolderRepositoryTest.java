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
import static com.android.testutils.AsyncTestUtils.waitForCondition;
import static com.android.tools.idea.testing.AndroidTestUtils.moveCaret;
import static com.android.tools.idea.testing.AndroidTestUtils.waitForUpdates;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.junit.Assert.fail;

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
import com.android.test.testutils.TestUtils;
import com.android.tools.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.rendering.DrawableRenderer;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.res.LocalResourceRepository;
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
import com.intellij.openapi.module.Module;
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
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.concurrency.ThreadingAssertions;
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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link ResourceFolderRepository}.
 *
 * TODO: Add XmlTags with PSI events to check childAdded etc working correctly. Currently they mostly seem to generate big rescans.
 * TODO: Test moving from one resource folder to another. Should be simulated as an add in one folder and a remove in another.
 *       Check that in the ModuleResourceRepository test.
 * TODO: Test that adding and removing characters inside a {@code <string>} element does not cause a full rescan.
 */
@SuppressWarnings({"UnusedDeclaration", "SpellCheckingInspection", "RedundantThrows"})
@RunWith(JUnit4.class)
@RunsInEdt
public class ResourceFolderRepositoryTest {
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

  private final AndroidProjectRule androidProjectRule = AndroidProjectRule.withSdk().initAndroid(true);

  @Rule
  public final RuleChain ruleChain = RuleChain.outerRule(androidProjectRule).around(new EdtRule());

  private CodeInsightTestFixture myFixture;
  private Project myProject;
  private Module myModule;
  private AndroidFacet myFacet;

  @Before
  public void setUp() throws Exception {
    myFixture = androidProjectRule.getFixture();
    myFixture.setTestDataPath(TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData").toString());

    myProject = myFixture.getProject();
    myModule = myFixture.getModule();
    myFacet = AndroidFacet.getInstance(myModule);

    myFixture.addFileToProject("res/empty", "");

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
    ServiceContainerUtil.replaceService(getApplication(), ResourceFolderRepositoryFileCache.class, cache, myFixture.getTestRootDisposable());
    myRegistry = ResourceFolderRegistry.getInstance(myProject);
    Path file = cache.getCachingData(myProject, getResourceDirectory(), null).getCacheFile();
    Files.deleteIfExists(file);
  }

  private @NotNull VirtualFile getResourceDirectory() {
    List<VirtualFile> resourceDirectories = ResourceFolderManager.getInstance(myFacet).getFolders();
    assertThat(resourceDirectories).isNotNull();
    assertThat(resourceDirectories).hasSize(1);
    return resourceDirectories.get(0);
  }

  private @NotNull ResourceFolderRepository createRepository(boolean createCache) {
    VirtualFile dir = getResourceDirectory();
    ResourceNamespace namespace = StudioResourceRepositoryManager.getInstance(myFacet).getNamespace();
    ResourceFolderRepositoryCachingData cachingData =
        ResourceFolderRepositoryFileCacheService.get().getCachingData(myProject, dir, createCache ? directExecutor() : null);

    return ResourceFolderRepository.create(myFacet, dir, namespace, cachingData).ensureLoaded();
  }

  private @NotNull ResourceFolderRepository createRegisteredRepository() {
    VirtualFile dir = getResourceDirectory();
    return myRegistry.get(myFacet, dir);
  }

  private @NotNull VirtualFile getProjectDir() {
    return Objects.requireNonNull(ProjectUtil.guessProjectDir(myProject));
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
    // Just the id.
    assertThat(id.startsWith(PREFIX_RESOURCE_REF)).isFalse();
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
    assertThat(Collections2.transform(style.getDefinedItems(), StyleItemResourceValue::getAttrName)).containsExactlyElementsIn(attributes);
  }

  private void type(@NotNull String place, @NotNull String toType) {
    moveCaret(myFixture, place);
    myFixture.type(toType);
    PsiDocumentManager.getInstance(myProject).commitDocument(myFixture.getEditor().getDocument());
  }

  /** Commits all documents and waits for the given resource repository to finish currently pending updates. */
  private void commitAndWaitForUpdates(@NotNull LocalResourceRepository<VirtualFile> repository) throws InterruptedException, TimeoutException {
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    waitForUpdates(repository);
  }

  @Test
  public void computeResourceStrings() {
    // Tests the handling of markup to raw strings
    // For example, for this strings definition
    //   <string name="title_template_step">Step <xliff:g id="step_number">%1$d</xliff:g>: Lorem Ipsum</string>
    // the resource value should be
    //   Step %1$d: Lorem Ipsum
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    List<ResourceItem> labelList = repository.getResources(RES_AUTO, ResourceType.STRING, "title_template_step");
    assertThat(labelList).isNotNull();
    assertThat(labelList.size()).isEqualTo(1);
    ResourceItem label = labelList.get(0);
    ResourceValue resourceValue = label.getResourceValue();
    assertThat(resourceValue).isNotNull();
    // In the file, there's whitespace unlike example above
    assertThat(resourceValue.getValue()).isEqualTo("Step ${step_number}: Lorem Ipsum");

    // Test unicode escape handling: <string name="ellipsis">Here it is: \u2026!</string>
    labelList = repository.getResources(RES_AUTO, ResourceType.STRING, "ellipsis");
    assertThat(labelList).isNotNull();
    assertThat(labelList.size()).isEqualTo(1);
    label = labelList.get(0);
    resourceValue = label.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("Here it is: \u2026!");

    // Make sure we pick up id's defined using types
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next2")).isFalse();
  }

  /** Tests handling of xliff markup. */
  @Test
  public void xliff() {
    VirtualFile file1 = myFixture.copyFileToProject(XLIFF, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(
      (Object)repository.getResources(RES_AUTO, ResourceType.STRING, "share_with_application").get(0).getResourceValue().getValue()).isEqualTo(
      "Share your score of (1337) with (Bluetooth)!");
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING, "description_call").get(0).getResourceValue().getValue()).isEqualTo(
      "Call ${name}");
    assertThat(
      (Object)repository.getResources(RES_AUTO, ResourceType.STRING, "callDetailsDurationFormat").get(0).getResourceValue().getValue()).isEqualTo(
      "(42) mins (28) secs");
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING, "other").get(0).getResourceValue().getValue()).isEqualTo(
      "${number_of_sessions} sessions removed from your schedule");
  }

  @Test
  public void initialCreate() {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    Collection<String> layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertThat(layouts.size()).isEqualTo(2);

    assertThat(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1")).isNotNull();
    assertThat(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout2")).isNotNull();
  }

  @Test
  public void arrayWithNoName() throws Exception {
    VirtualFile virtualFile = myFixture.copyFileToProject(VALUES_WITH_INCORRECT_ARRAY, "res/values/array_without_name.xml");

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
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

  @Test
  public void addFile() throws Exception {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    VirtualFile layout2 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    Collection<String> layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertThat(layouts.size()).isEqualTo(2);
    assertThat(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1")).isNotNull();
    assertThat(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout2")).isNotNull();

    long generation = repository.getModificationCount();
    // Change modification time of the layout2 file without changing its contents.
    Path file = Paths.get(layout2.getPath());
    Files.setLastModifiedTime(file, FileTime.from(Files.getLastModifiedTime(file).toInstant().plusSeconds(1)));
    VfsUtil.markDirtyAndRefresh(false, false, false, layout2);
    assertThat(repository.getModificationCount()).isEqualTo(generation); // no changes in file: no new generation

    generation = repository.getModificationCount();
    VirtualFile file3 = myFixture.copyFileToProject(LAYOUT1, "res/layout-xlarge-land/layout3.xml");
    PsiFile psiFile3 = PsiManager.getInstance(myProject).findFile(file3);
    assertThat(psiFile3).isNotNull();
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertThat(layouts.size()).isEqualTo(3);
  }

  @Test
  public void addUnrelatedFile() throws Exception {
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

  @Test
  public void deleteResourceFile() throws Exception {
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    Collection<String> drawables = repository.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    String message = drawables.toString();
    assertWithMessage(message).that((Object)drawables.size()).isEqualTo(0);
    long generation = repository.getModificationCount();
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/drawable-mdpi/foo.png");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Delete a file and make sure the item is removed from the repository (and modification count bumped).
    drawables = repository.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertThat(drawables.size()).isEqualTo(1);
    generation = repository.getModificationCount();
    assertThat(drawables.iterator().next()).isEqualTo("foo");
    WriteCommandAction.runWriteCommandAction(null, psiFile1::delete);
    waitForUpdates(repository);
    drawables = repository.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertThat(drawables.size()).isEqualTo(0);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Try adding and then deleting a drawable file with IDs too.
    generation = repository.getModificationCount();

    // TODO: Make this work with copyFileToProject.
    // copyFileToProject now creates an empty file first which triggers a childAdded event w/ an empty file (so no IDs are parsed).
    // It then it copies the contents over and triggers a childrenChanged event but in a way that is unlike typing in content,
    // so we don't re-parse the XML for IDs.
    //VirtualFile file2 = myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/drawable-v21/drawable_with_ids.xml");
    File fromFile = new File(myFixture.getTestDataPath(), DRAWABLE_ID_SCAN);
    assertThat(fromFile.exists()).isTrue();
    File targetFile = new File(myFixture.getTempDirFixture().getTempDirPath(), "res/drawable-v21/drawable_with_ids.xml");
    assertThat(targetFile.exists()).isFalse();
    FileUtil.copy(fromFile, targetFile);
    VirtualFile file2 = VfsUtil.findFileByIoFile(targetFile, true);
    assertThat(file2).isNotNull();
    waitForUpdates(repository);

    PsiFile psiFile2 = PsiManager.getInstance(myProject).findFile(file2);
    assertThat(psiFile2).isNotNull();
    drawables = repository.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertThat(drawables.size()).isEqualTo(1);
    Collection<String> ids = repository.getResources(RES_AUTO, ResourceType.ID).keySet();
    assertThat(ids).containsExactly("focused_state", "default_state", "pressed_state");
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, psiFile2::delete);

    waitForUpdates(repository);
    drawables = repository.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertThat(drawables.size()).isEqualTo(0);
    ids = repository.getResources(RES_AUTO, ResourceType.ID).keySet();
    assertThat(ids.size()).isEqualTo(0);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
  }

  @Test
  public void deleteResourceDirectory() throws Exception {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");
    VirtualFile file3 = myFixture.copyFileToProject(LAYOUT1, "res/layout-xlarge-land/layout3.xml");
    PsiFile psiFile3 = PsiManager.getInstance(myProject).findFile(file3);
    assertThat(psiFile3).isNotNull();

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    // Try deleting a whole resource directory and ensure we remove the files within.
    long generation = repository.getModificationCount();
    Collection<String> layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertThat(layouts.size()).isEqualTo(3);
    PsiDirectory directory = psiFile3.getContainingDirectory();
    assertThat(directory).isNotNull();
    WriteCommandAction.runWriteCommandAction(null, directory::delete);
    waitForUpdates(repository);
    layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertThat(layouts.size()).isEqualTo(2);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
  }

  @Test
  public void deleteMultipleResourceDirectories() throws Exception {
    VirtualFile strings = myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    VirtualFile stringsDe = myFixture.copyFileToProject(STRINGS, "res/values-de/strings.xml");
    VirtualFile stringsEs = myFixture.copyFileToProject(STRINGS, "res/values-es/strings.xml");
    PsiFile psiFileDe = PsiManager.getInstance(myProject).findFile(stringsDe);
    PsiFile psiFileEs = PsiManager.getInstance(myProject).findFile(stringsEs);

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    // Try deleting a two resource directories and ensure we remove the resources within.
    long generation = repository.getModificationCount();
    Collection<ResourceItem> stringItems = repository.getResources(RES_AUTO, ResourceType.STRING).values();

    assertThat(stringItems.size()).isEqualTo(9);
    PsiDirectory directoryDe = psiFileDe.getContainingDirectory();
    PsiDirectory directoryEs = psiFileEs.getContainingDirectory();
    assertThat(directoryDe).isNotNull();
    assertThat(directoryEs).isNotNull();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      directoryDe.delete();
      directoryEs.delete();
    });
    waitForUpdates(repository);

    stringItems = repository.getResources(RES_AUTO, ResourceType.STRING).values();
    assertThat(stringItems.size()).isEqualTo(3);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
  }

  @Test
  public void deleteRemainderResourceIDs() throws Exception {
    VirtualFile file = myFixture.copyFileToProject(LAYOUT_ID_SCAN, "res/layout-xlarge-land/layout.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file);
    assertThat(psiFile1).isNotNull();

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    long generation = repository.getModificationCount();
    Collection<String> layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertThat(layouts.size()).isEqualTo(1);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea")).isTrue();
    // nonExistent may be handled slightly different from other IDs, since it is introduced by a
    // non-ID-attribute="@+id/nonExistent", but the file does not define a tag with id="@id/nonExistent".
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "nonExistent")).isTrue();

    WriteCommandAction.runWriteCommandAction(null, psiFile1::delete);
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertThat(layouts).isEmpty();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "nonExistent")).isFalse();
  }

  @Test
  public void renameLayoutFile() throws Exception {
    VirtualFile file2 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    // Check renames.
    //  Rename layout file.
    long generation = repository.getModificationCount();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout2")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout2b")).isFalse();

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout2b")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout2")).isFalse();
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
  }

  @Test
  public void renameLayoutFileToInvalid() throws Exception {
    VirtualFile file = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

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

  @Test
  public void renameDrawableFile() throws Exception {
    //  Rename drawable file.
    VirtualFile file5 = myFixture.copyFileToProject(LAYOUT1, "res/drawable-xhdpi/foo2.png");
    ResourceFolderRepository repository = createRegisteredRepository();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo2")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo3")).isFalse();
    ResourceItem item = getOnlyItem(repository, ResourceType.DRAWABLE, "foo2");
    assertThat(item.getResourceValue() instanceof DensityBasedResourceValue).isTrue();
    DensityBasedResourceValue rv = (DensityBasedResourceValue)item.getResourceValue();
    assertThat(rv).isNotNull();
    assertThat(rv.getResourceDensity()).isSameAs(Density.XHIGH);

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo3")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo2")).isFalse();
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getLayoutlibCacheFlushes()).isGreaterThan(layoutlibCacheFlushes);
  }

  @Test
  public void renameResourceBackedByPsiResourceItem() throws Exception {
    // We first do a normal rename which will also convert the ResourceItem into a PsiResourceItem
    //  Rename drawable file.
    VirtualFile file5 = myFixture.copyFileToProject(LAYOUT1, "res/drawable-xhdpi/foo2.png");
    ResourceFolderRepository repository = createRegisteredRepository();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo2")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo3")).isFalse();
    ResourceItem item = getOnlyItem(repository, ResourceType.DRAWABLE, "foo2");
    assertThat(item.getResourceValue() instanceof DensityBasedResourceValue).isTrue();
    DensityBasedResourceValue rv = (DensityBasedResourceValue)item.getResourceValue();
    assertThat(rv).isNotNull();
    assertThat(rv.getResourceDensity()).isSameAs(Density.XHIGH);

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo3")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo2")).isFalse();
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // At this point the item2 is a PsiResourceItem so we try to rename a second time
    // to check that the new name is propagated to the repository repositories.
    ResourceItem item2 = getOnlyItem(repository, ResourceType.DRAWABLE, "foo3");
    assertThat(item2).isInstanceOf(PsiResourceItem.class);
    assertThat(item2.getResourceValue() instanceof DensityBasedResourceValue).isTrue();

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo3")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo4")).isTrue();
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
  }

  @Test
  public void renameValueFile() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step")).isTrue();

    List<ResourceItem> items = repository.getResources(RES_AUTO, ResourceType.STRING, "title_template_step");
    assertThat(items).isNotNull();
    assertThat(items.size()).isEqualTo(1);
    ResourceItem item = items.get(0);
    assertThat(item.getSource().getFileName()).isEqualTo("myvalues.xml");

    // We need to make sure there is a document. PsiVFSListener uses createFileCopyWithNewName
    // to populate the new Psi file with the old Psi file's content. However, by the time that runs,
    // the old file will already be physically moved to the new file. If it cannot find a cached
    // document for the old file, then it will make up an empty old Psi file and copy the empty content
    // to the new PsiFile.
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step")).isTrue();
    items = repository.getResources(RES_AUTO, ResourceType.STRING, "title_template_step");
    assertThat(items).isNotNull();
    assertThat(items.size()).isEqualTo(1);
    item = items.get(0);
    assertThat(item.getSource().getFileName()).isEqualTo("renamedvalues.xml");

    // TODO: Optimize this such that there's no modification change for this. It's tricky because
    //       for file names we get separate notification from the old file deletion (beforePropertyChanged)
    //       and the new file name (propertyChanged). (Note that I tried performing the rename via a
    //       setName operation on the PsiFile instead of at the raw VirtualFile level, but the resulting
    //       events were the same.)
    //assertThat(repository.getModificationCount()).isEqualTo(generation);
  }

  @Test
  public void renameValueFileToInvalid() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step")).isTrue();

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step")).isFalse();
  }

  private static ResourceItem getOnlyItem(@NotNull ResourceFolderRepository repository, @NotNull ResourceType type, @NotNull String name) {
    List<ResourceItem> items = repository.getResources(RES_AUTO, type, name);
    assertThat(items.size()).isEqualTo(1);
    return items.get(0);
  }

  @Test
  public void moveFileResourceFileToNewConfiguration() throws Exception {
    // Move a file-based resource file from one configuration to another; verify that
    // items are preserved, generation changed (since it can affect config matching),
    // and resource files updated.
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout-land/layout1.xml");
    VirtualFile file2 = myFixture.copyFileToProject(LAYOUT1, "res/layout-port/dummy.ignore");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    ResourceItem item = getOnlyItem(repository, ResourceType.LAYOUT, "layout1");
    assertThat(item.getConfiguration().getQualifierString()).isEqualTo("land");
    ResourceItem idItem = getOnlyItem(repository, ResourceType.ID, "btn_title_refresh");
    assertThat(idItem.getSource().getParentFileName()).isEqualTo("layout-land");

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1")).isTrue();
    item = getOnlyItem(repository, ResourceType.LAYOUT, "layout1");
    assertThat(item.getConfiguration().getQualifierString()).isEqualTo("port");
    idItem = getOnlyItem(repository, ResourceType.ID, "btn_title_refresh");
    assertThat(idItem.getSource().getParentFileName()).isEqualTo("layout-port");
  }

  @Test
  public void moveValueResourceFileToNewConfiguration() throws Exception {
    // Move a value file from one configuration to another; verify that
    // items are preserved, generation changed (since it can affect config matching),
    // and resource files updated.
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values-en/layout1.xml");
    VirtualFile file2 = myFixture.copyFileToProject(VALUES1, "res/values-no/dummy.ignore");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    ResourceItem item = getOnlyItem(repository, ResourceType.STRING, "app_name");
    assertThat(item.getConfiguration().getQualifierString()).isEqualTo("en");
    assertThat(item.getConfiguration().getLocaleQualifier().getLanguage()).isEqualTo("en");
    assertThat(item.getResourceValue().getValue()).isEqualTo("Animations Demo");

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
    assertThat(item.getConfiguration().getQualifierString()).isEqualTo("no");
    assertThat(item.getConfiguration().getLocaleQualifier().getLanguage()).isEqualTo("no");
    assertThat(item.getResourceValue().getValue()).isEqualTo("Animations Demo");
  }

  @Test
  public void moveResourceFileBetweenDensityFolders() throws Exception {
    // Regression test for https://code.google.com/p/android/issues/detail?id=61648
    // Make sure we flush resource values when reusing resource items incrementally

    // Move a file-based resource file from one configuration to another; verify that
    // items are preserved, generation changed (since it can affect config matching),
    // and resource files updated.
    VirtualFile file1 = myFixture.copyFileToProject(DRAWABLE, "res/drawable-mdpi/picture.png");
    VirtualFile file2 = myFixture.copyFileToProject(DRAWABLE, "res/drawable-hdpi/dummy.ignore");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    ResourceItem item = getOnlyItem(repository, ResourceType.DRAWABLE, "picture");
    assertThat(item.getConfiguration().getDensityQualifier().getValue()).isEqualTo(Density.MEDIUM);
    ResourceValue resourceValue = item.getResourceValue();
    assertThat(resourceValue).isNotNull();
    String valuePath = resourceValue.getValue().replace(File.separatorChar, '/');
    assertWithMessage(valuePath).that(valuePath.endsWith("res/drawable-mdpi/picture.png")).isTrue();

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "picture")).isTrue();
    item = getOnlyItem(repository, ResourceType.DRAWABLE, "picture");
    assertThat(item.getConfiguration().getDensityQualifier().getValue()).isEqualTo(Density.HIGH);
    resourceValue = item.getResourceValue();
    assertThat(resourceValue).isNotNull();
    valuePath = resourceValue.getValue().replace(File.separatorChar, '/');
    assertWithMessage(valuePath).that(valuePath.endsWith("res/drawable-hdpi/picture.png")).isTrue();
  }

  @Test
  public void moveFileResourceFileToNewType() throws Exception {
    // Move a file resource file file from one folder to another, changing the type
    // (e.g. anim to animator), verify that resource types are updated
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    VirtualFile file2 = myFixture.copyFileToProject(LAYOUT1, "res/menu/dummy.ignore");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1")).isTrue();

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.MENU, "layout1")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1")).isFalse();
    ResourceItem item = getOnlyItem(repository, ResourceType.MENU, "layout1");
    assertThat(((PsiResourceItem)item).getSourceFile().getFolderType()).isSameAs(ResourceFolderType.MENU);
  }

  @Test
  public void moveOutOfResourceFolder() throws Exception {
    // Move value files out of its resource folder; items should disappear
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    VirtualFile javaFile = myFixture.copyFileToProject(VALUES1, "src/my/pkg/Dummy.java");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step")).isTrue();

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step")).isFalse();
  }

  @Test
  public void moveIntoResourceFolder() throws Exception {
    // Move value files out of its resource folder; items should disappear
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/dummy.ignore");
    VirtualFile xmlFile = myFixture.copyFileToProject(VALUES1, "src/my/pkg/values.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step")).isFalse();

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step")).isTrue();
  }

  @Test
  public void replaceResourceFile() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "btn_title_refresh")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "btn_title_refresh2")).isFalse();

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "btn_title_refresh2")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "btn_title_refresh")).isFalse();
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
  }

  @Test
  public void addEmptyValueFile() {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    long generation = repository.getModificationCount();
    assertThat(repository.getModificationCount()).isEqualTo(generation);
  }

  @Test
  public void rawFolder() throws Exception {
    // In this folder, any file extension is allowed.
    myFixture.copyFileToProject(LAYOUT1, "res/raw/raw1.xml");
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    Collection<String> raw = repository.getResources(RES_AUTO, ResourceType.RAW).keySet();
    assertThat(raw.size()).isEqualTo(1);
    long generation = repository.getModificationCount();
    VirtualFile file2 = myFixture.copyFileToProject(LAYOUT1, "res/raw/numbers.random");
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.RAW, "numbers")).isTrue();
    raw = repository.getResources(RES_AUTO, ResourceType.RAW).keySet();
    assertThat(raw.size()).isEqualTo(2);

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.RAW, "numbers2")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.RAW, "numbers")).isFalse();
  }

  @Test
  public void editLayoutNoOp() throws Exception {
    // Make some miscellaneous edits in the file that have no bearing on the
    // project repository and therefore end up doing no work
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    assert(psiFile1 instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile1;
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    Collection<String> layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertThat(layouts.size()).isEqualTo(1);
    assertThat(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1")).isNotNull();

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    // Insert a comment at the beginning
    WriteCommandAction.runWriteCommandAction(null, () -> {
      XmlTag rootTag = xmlFile.getRootTag();
      assertThat(rootTag).isNotNull();
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

    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea")).isTrue();
    XmlTag tag = findTagById(psiFile1, "noteArea");
    assertThat(tag).isNotNull();

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
      assertThat(header).isNotNull();
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
      assertThat(header).isNotNull();
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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "newid")).isFalse();
    String elementDeclaration = "<Button android:id=\"@+id/newid\" />\n";
    WriteCommandAction.runWriteCommandAction(null, () -> {
      XmlTag tag12 = findTagById(psiFile1, "noteArea");
      assertThat(tag12).isNotNull();
      document.insertString(tag12.getTextOffset() - 1, elementDeclaration);
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "newid")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "newid2")).isFalse();
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Now try another edit, where things should be incremental now.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    String elementDeclaration2 = "<Button android:id=\"@+id/newid2\" />\n";
    WriteCommandAction.runWriteCommandAction(null, () -> {
      XmlTag tag1 = findTagById(psiFile1, "noteArea");
      assertThat(tag1).isNotNull();
      document.insertString(tag1.getTextOffset() - 1, elementDeclaration2);
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "newid")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "newid2")).isTrue();
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans);

    generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int startOffset = document.getText().indexOf(elementDeclaration);
      document.deleteString(startOffset, startOffset + elementDeclaration.length());
      documentManager.commitDocument(document);
    });

    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "newid")).isFalse();
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1);
  }

  @Test
  public void editValueFileNoOp() {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    Collection<String> strings = repository.getResources(RES_AUTO, ResourceType.STRING).keySet();
    assertThat(strings.size()).isEqualTo(8);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.INTEGER, "card_flip_time_full")).isTrue();

    long generation = repository.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

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

  @Test
  public void insertNewElementWithId() throws Exception {
    // Make some miscellaneous edits in the file that have no bearing on the
    // project repository and therefore end up doing no work
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    assert(psiFile1 instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile1;
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    Collection<String> layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertThat(layouts.size()).isEqualTo(1);
    assertThat(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1")).isNotNull();

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    // Insert tag (with an id) in layout file: should incrementally update set of ids
    WriteCommandAction.runWriteCommandAction(null, () -> {
      XmlTag header = findTagById(xmlFile, "text2");
      assertThat(header).isNotNull();
      int indentAreaBeforeTag = header.getTextOffset() - 1;
      document.insertString(indentAreaBeforeTag,
                            "<LinearLayout android:id=\"@+id/newid1\"><Child android:id=\"@+id/newid2\"/></LinearLayout>");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "newid1")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "newid2")).isTrue();

    // A second update should be incremental.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      XmlTag header = findTagById(xmlFile, "text2");
      assertThat(header).isNotNull();
      int indentAreaBeforeTag = header.getTextOffset() - 1;
      document.insertString(indentAreaBeforeTag,
                            "<LinearLayout android:id=\"@+id/newid3\"><Child android:id=\"@+id/newid4\"/></LinearLayout>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "newid1")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "newid2")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "newid3")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "newid4")).isTrue();
  }

  @Test
  public void editIdAttributeValue() throws Exception {
    // Edit the id attribute value of a layout item to change the set of available ids
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    Collection<String> layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertThat(layouts.size()).isEqualTo(1);
    assertThat(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1")).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "note2Area")).isFalse();

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    XmlTag tag = findTagById(psiFile1, "noteArea");
    assertThat(tag).isNotNull();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      tag.setAttribute(ATTR_ID, ANDROID_URI, "@+id/note2Area");
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "note2Area")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea")).isFalse();

    // A second update should be incremental.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      tag.setAttribute(ATTR_ID, ANDROID_URI, "@+id/note23Area");
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "note23Area")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "note2Area")).isFalse();
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Check replacing @+id with a normal string.
    generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      tag.setAttribute(ATTR_ID, ANDROID_URI, "notId");
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "note23Area")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "notId")).isFalse();
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void editIdAttributeValue2() throws Exception {
    // Edit the id attribute value: rather than by making a full value replacement,
    // perform a tiny edit on the character content; this takes a different code
    // path in the incremental updater.
    // Edit the id attribute value of a layout item to change the set of available ids.
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    Collection<String> layouts = repository.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertThat(layouts.size()).isEqualTo(1);
    assertThat(repository.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1")).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "note2Area")).isFalse();

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "note2Area")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea")).isFalse();

    // A second update should be incremental.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("note2Area");
      document.insertString(offset + 5, "3");
      documentManager.commitDocument(document);
    });

    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "note23Area")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "note2Area")).isFalse();
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Also check that for IDs the ResourceValue is nothing of consequence.
    ResourceItem idItem = getOnlyItem(repository, ResourceType.ID, "note23Area");
    ResourceValue idValue = idItem.getResourceValue();
    assertThat(idValue).isNotNull();
    assertThat(idValue.getValue()).isEqualTo("");

    // Check replacing @+id with a normal string.
    generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String attrValue = "@+id/note23Area";
      int offset = document.getText().indexOf(attrValue);
      document.replaceString(offset, offset + attrValue.length(), "notId");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "note23Area")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "notId")).isFalse();
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void commentAndUncommentTag() throws Exception {
    // Regression test for https://issuetracker.google.com/302262716.
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    myFixture.configureFromExistingVirtualFile(file1);

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea")).isTrue();

    // Locate the `noteArea` ID, and then find and select its surrounding tag.
    String documentText = myFixture.getEditor().getDocument().getText();
    int idIndex = documentText.indexOf("android:id=\"@+id/noteArea\"");
    assertThat(idIndex).isAtLeast(0);
    int startTagIndex = documentText.substring(0, idIndex).lastIndexOf("<LinearLayout");
    assertThat(startTagIndex).isAtLeast(0);
    int endTagIndex = documentText.indexOf("</LinearLayout>", idIndex);
    assertThat(endTagIndex).isAtLeast(0);
    myFixture.getEditor().getSelectionModel().setSelection(
      startTagIndex,
      endTagIndex + "</LinearLayout>".length());

    // Comment out the `noteArea` tag.
    myFixture.performEditorAction(IdeActions.ACTION_COMMENT_BLOCK);
    commitAndWaitForUpdates(repository);

    // The repository should no longer have the `noteArea` id.
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea")).isFalse();

    // Select and uncomment out the `noteArea` tag.
    myFixture.getEditor().getSelectionModel().setSelection(
      startTagIndex,
      endTagIndex + "</LinearLayout>".length() + "<!---->".length());
    myFixture.performEditorAction(IdeActions.ACTION_COMMENT_BLOCK);
    commitAndWaitForUpdates(repository);

    // The `noteArea` id should be back again.
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea")).isTrue();
  }


  @Test
  public void editIdFromDrawable() throws Exception {
    // Mix PNGs and XML in the same directory.
    myFixture.copyFileToProject(DRAWABLE, "res/drawable-v21/logo.png");
    VirtualFile file1 = myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/drawable-v21/drawable_with_ids.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    Collection<String> ids = repository.getResources(RES_AUTO, ResourceType.ID).keySet();
    assertThat(ids).isNotNull();
    assertThat(ids).containsExactly("focused_state", "default_state", "pressed_state");
    Collection<String> drawables = repository.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertThat(drawables).containsExactly("logo", "drawable_with_ids");

    // Now test an ID edit, to make sure that gets picked up too incrementally, just like layouts.
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    // Edit value should cause update
    long generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("focused_state");
      document.replaceString(offset, offset + 1, "l");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "locused_state")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "focused_state")).isFalse();
    drawables = repository.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertThat(drawables).containsExactly("logo", "drawable_with_ids");
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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "dddefault_state")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "default_state")).isFalse();
    drawables = repository.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertThat(drawables).containsExactly("logo", "drawable_with_ids");

    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  /**
   * We expect this change to update the counter (so the layout editor notices the change but it shouldn't
   * change any repository since it does not add or remove ids.
   */
  @Test
  public void editNonIdFromDrawable() throws Exception {
    VirtualFile file = myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/drawable-v21/drawable_with_ids.xml");
    PsiFile psiFiles = PsiManager.getInstance(myProject).findFile(file);
    assertThat(psiFiles).isNotNull();

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    // Now test an ID edit, to make sure that gets picked up too incrementally, just like layouts.
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFiles);
    assertThat(document).isNotNull();

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
  @Test
  public void editNonIdGeneratingXml() throws Exception {
    VirtualFile file = myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/xml/xml_file.xml");
    PsiFile psiFiles = PsiManager.getInstance(myProject).findFile(file);
    assertThat(psiFiles).isNotNull();

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    // Now test an ID edit, to make sure that gets picked up too incrementally, just like layouts.
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFiles);
    assertThat(document).isNotNull();

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

  @Test
  public void motionScene() throws Exception {
    VirtualFile virtualFile = myFixture.copyFileToProject(MOTION_SCENE, "res/xml/motion_scene.xml");
    XmlFile file = (XmlFile)PsiManager.getInstance(myProject).findFile(virtualFile);
    assertThat(file).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

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

  @Test
  public void editValueText() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    Collection<String> strings = repository.getResources(RES_AUTO, ResourceType.STRING).keySet();
    assertThat(strings.size()).isEqualTo(8);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.INTEGER, "card_flip_time_full")).isTrue();

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

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
    assertThat(labelList).isNotNull();
    assertThat(labelList.size()).isEqualTo(1);
    ResourceItem slideLabel = labelList.get(0);
    ResourceValue resourceValue = slideLabel.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("Screeeen Slide");

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.deleteString(screenSlideOffset + 3, screenSlideOffset + 7);
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    resourceValue = slideLabel.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("Scrn Slide");

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void nestedEditValueText() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    List<ResourceItem> labelList = repository.getResources(RES_AUTO, ResourceType.STRING, "title_template_step");
    assertThat(labelList).isNotNull();
    assertThat(labelList.size()).isEqualTo(1);
    ResourceItem label = labelList.get(0);
    ResourceValue resourceValue = label.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("Step ${step_number}: Lorem Ipsum");

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

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
    assertThat(labelList).isNotNull();
    assertThat(labelList.size()).isEqualTo(1);
    label = labelList.get(0);
    resourceValue = label.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("Step ${step_number}: Llorem Ipsum");

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
    assertThat(labelList2).isNotNull();
    assertThat(labelList2.size()).isEqualTo(1);
    ResourceItem label2 = labelList2.get(0);
    resourceValue = label2.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("Step ${step_number}: Lllorem Ipsum");

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void editValueName() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    Collection<String> strings = repository.getResources(RES_AUTO, ResourceType.STRING).keySet();
    assertThat(strings.size()).isEqualTo(8);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isTrue();

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    int offset = document.getText().indexOf("app_name");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(offset, offset + 3, "tap");
      documentManager.commitDocument(document);
    });

    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "tap_name")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isFalse();

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "rap_name")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "tap_name")).isFalse();

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void addValue() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    Collection<String> strings = repository.getResources(RES_AUTO, ResourceType.STRING).keySet();
    assertThat(strings.size()).isEqualTo(8);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isTrue();

    // Incrementally add in a new item
    long generation = repository.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "new_string")).isTrue();
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING, "new_string").get(0).getResourceValue().getValue()).isEqualTo(
      "New String");
  }

  @Test
  public void removeValue() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    Collection<String> strings = repository.getResources(RES_AUTO, ResourceType.STRING).keySet();
    assertThat(strings.size()).isEqualTo(8);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isTrue();

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String textToRemove = "<string name=\"app_name\">Animations Demo</string>";
      int offset = document.getText().indexOf(textToRemove);
      document.deleteString(offset, offset + textToRemove.length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_zoom")).isTrue();

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "title_zoom")).isFalse();
    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void addIdValue() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_prev")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_flip")).isTrue();

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item type=\"id\" name=\"action_next\" />");
      document.insertString(offset, "<item type=\"id\" name=\"action_prev\" />");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_prev")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_flip")).isTrue();

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_prev")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_mid")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_flip")).isTrue();
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void removeIdValue() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_flip")).isTrue();

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = "<item type=\"id\" name=\"action_next\" />";
      int offset = document.getText().indexOf(item);
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_flip")).isTrue();

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_flip")).isFalse();
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void changeType() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.DIMEN, "action_next")).isFalse();

    long generation = repository.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    int offset = document.getText().indexOf("\"id\" name=\"action_next\" />") + 1;
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(offset, offset + 2, "dimen");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getFileRescans()).isEqualTo(1);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.DIMEN, "action_next")).isTrue();
  }

  @Test
  public void breakNameAttribute() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isTrue();

    long generation = repository.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    int offset = document.getText().indexOf("name=\"app_name\">");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(offset + 2, offset + 3, "o"); // name => nome
      documentManager.commitDocument(document);
    });

    waitForUpdates(repository);
    assertThat(repository.getFileRescans()).isEqualTo(1);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isFalse();
  }

  @Test
  public void changeValueTypeByTagNameEdit() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.INTEGER, "card_flip_time_half")).isTrue();

    long generation = repository.getModificationCount();
    XmlTag tag = findTagByName(psiFile1, "card_flip_time_half");
    assertThat(tag).isNotNull();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      tag.setName("dimen"); // Change <integer> to <dimen>
    });
    waitForUpdates(repository);
    assertThat(repository.getFileRescans()).isEqualTo(1);

    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.DIMEN, "card_flip_time_half")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.INTEGER, "card_flip_time_half")).isFalse();
  }

  @Test
  public void editStyleName() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme")).isTrue();

    // Change style name.
    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("DarkTheme");
      document.replaceString(offset, offset + 4, "Grey");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "GreyTheme")).isTrue();

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "LightTheme")).isTrue();

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void editStyleParent() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme")).isTrue();

    // Change style parent
    long generation = repository.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

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
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue instanceof StyleResourceValue).isTrue();
    StyleResourceValue styleValue = (StyleResourceValue)resourceValue;
    assertThat(styleValue.getParentStyleName()).isEqualTo("android:Theme.Light");
    ResourceValue actionBarStyle = styleValue.getItem(ANDROID, "actionBarStyle");
    assertThat(actionBarStyle).isNotNull();
    assertThat(actionBarStyle.getValue()).isEqualTo("@style/DarkActionBar");

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
    assertThat(resourceValue2).isNotNull();
    assertThat(resourceValue2 instanceof StyleResourceValue).isTrue();
    StyleResourceValue styleValue2 = (StyleResourceValue)resourceValue2;
    assertThat(styleValue2.getParentStyleName()).isEqualTo("android:Theme.Material");
    ResourceValue actionBarStyle2 = styleValue2.getItem(ANDROID, "actionBarStyle");
    assertThat(actionBarStyle2).isNotNull();
    assertThat(actionBarStyle2.getValue()).isEqualTo("@style/DarkActionBar");
  }

  @Test
  public void editStyleItemText() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme")).isTrue();
    ResourceItem style = getOnlyItem(repository, ResourceType.STYLE, "DarkTheme");
    StyleResourceValue styleValue = (StyleResourceValue)style.getResourceValue();
    assertThat(styleValue).isNotNull();
    ResourceValue actionBarStyle = styleValue.getItem(ANDROID, "actionBarStyle");
    assertThat(actionBarStyle).isNotNull();
    assertThat(actionBarStyle.getValue()).isEqualTo("@style/DarkActionBar");

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("@style/DarkActionBar");
      document.replaceString(offset + 7, offset + 11, "Grey");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme")).isTrue();
    style = getOnlyItem(repository, ResourceType.STYLE, "DarkTheme");
    styleValue = (StyleResourceValue)style.getResourceValue();
    assertThat(styleValue).isNotNull();
    actionBarStyle = styleValue.getItem(ANDROID, "actionBarStyle");
    assertThat(actionBarStyle).isNotNull();
    assertThat(actionBarStyle.getValue()).isEqualTo("@style/GreyActionBar");

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme")).isTrue();

    style = getOnlyItem(repository, ResourceType.STYLE, "DarkTheme");
    styleValue = (StyleResourceValue)style.getResourceValue();
    assertThat(styleValue).isNotNull();
    actionBarStyle = styleValue.getItem(ANDROID, "actionBarStyle");
    assertThat(actionBarStyle).isNotNull();
    assertThat(actionBarStyle.getValue()).isEqualTo("@style/LightActionBar");

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void editStyleItemName() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme")).isTrue();
    ResourceItem style = getOnlyItem(repository, ResourceType.STYLE, "DarkTheme");
    StyleResourceValue styleValue = (StyleResourceValue)style.getResourceValue();
    assertThat(styleValue).isNotNull();
    ResourceValue actionBarStyle = styleValue.getItem(ANDROID, "actionBarStyle");
    assertThat(actionBarStyle).isNotNull();
    assertThat(actionBarStyle.getValue()).isEqualTo("@style/DarkActionBar");

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("android:actionBarStyle");
      document.insertString(offset + 8, "n");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme")).isTrue();
    style = getOnlyItem(repository, ResourceType.STYLE, "DarkTheme");
    styleValue = (StyleResourceValue)style.getResourceValue();
    assertThat(styleValue).isNotNull();
    actionBarStyle = styleValue.getItem(ANDROID, "nactionBarStyle");
    assertThat(actionBarStyle).isNotNull();
    assertThat(actionBarStyle.getValue()).isEqualTo("@style/DarkActionBar");

    // Now try another edit, where things should be incremental now.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("android:nactionBarStyle");
      document.insertString(offset + 8, "i");
      documentManager.commitDocument(document);
    });

    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme")).isTrue();
    style = getOnlyItem(repository, ResourceType.STYLE, "DarkTheme");
    styleValue = (StyleResourceValue)style.getResourceValue();
    assertThat(styleValue).isNotNull();
    actionBarStyle = styleValue.getItem(ANDROID, "inactionBarStyle");
    assertThat(actionBarStyle).isNotNull();
    assertThat(actionBarStyle.getValue()).isEqualTo("@style/DarkActionBar");

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void addStyleItem() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar")).isTrue();
    ResourceItem style = getOnlyItem(repository, ResourceType.STYLE, "DarkActionBar");
    StyleResourceValue styleValue = (StyleResourceValue)style.getResourceValue();
    assertThat(styleValue).isNotNull();
    checkDefinedItems(styleValue, "android:background", "android:textColor");
    ResourceValue background = styleValue.getItem(ANDROID, "background");
    assertThat(background).isNotNull();
    assertThat(background.getValue()).isEqualTo("@android:color/transparent");
    ResourceValue textColor = styleValue.getItem(ANDROID, "textColor");
    assertThat(textColor).isNotNull();
    assertThat(textColor.getValue()).isEqualTo("#008");

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item name=\"android:background\"");
      assertThat(offset > 0).isTrue();
      document.insertString(offset, "<item name=\"android:textSize\">20sp</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar")).isTrue();
    style = getOnlyItem(repository, ResourceType.STYLE, "DarkActionBar");
    styleValue = (StyleResourceValue)style.getResourceValue();
    assertThat(styleValue).isNotNull();
    checkDefinedItems(styleValue, "android:background", "android:textSize", "android:textColor");
    ResourceValue textSize = styleValue.getItem(ANDROID, "textSize");
    assertThat(textSize).isNotNull();
    assertThat(textSize.getValue()).isEqualTo("20sp");

    // Now try a second edit.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item name=\"android:background\"");
      assertThat(offset > 0).isTrue();
      document.insertString(offset, "<item name=\"android:typeface\">monospace</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar")).isTrue();
    style = getOnlyItem(repository, ResourceType.STYLE, "DarkActionBar");
    styleValue = (StyleResourceValue)style.getResourceValue();
    assertThat(styleValue).isNotNull();
    checkDefinedItems(styleValue, "android:background", "android:typeface", "android:textSize", "android:textColor");
    ResourceValue typeface = styleValue.getItem(ANDROID, "typeface");
    assertThat(typeface).isNotNull();
    assertThat(typeface.getValue()).isEqualTo("monospace");

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void removeStyleItem() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar")).isTrue();
    ResourceItem style = getOnlyItem(repository, ResourceType.STYLE, "DarkActionBar");
    StyleResourceValue styleValue = (StyleResourceValue)style.getResourceValue();
    assertThat(styleValue).isNotNull();
    checkDefinedItems(styleValue, "android:background", "android:textColor");
    ResourceValue background = styleValue.getItem(ANDROID, "background");
    assertThat(background).isNotNull();
    assertThat(background.getValue()).isEqualTo("@android:color/transparent");
    ResourceValue textColor = styleValue.getItem(ANDROID, "textColor");
    assertThat(textColor).isNotNull();
    assertThat(textColor.getValue()).isEqualTo("#008");

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = ("<item name=\"android:textColor\">#008</item>");
      int offset = document.getText().indexOf(item);
      assertThat(offset > 0).isTrue();
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar")).isTrue();
    style = getOnlyItem(repository, ResourceType.STYLE, "DarkActionBar");
    styleValue = (StyleResourceValue)style.getResourceValue();
    assertThat(styleValue).isNotNull();
    checkDefinedItems(styleValue, "android:background");
    background = styleValue.getItem(ANDROID, "background");
    assertThat(background).isNotNull();
    assertThat(background.getValue()).isEqualTo("@android:color/transparent");

    // Try second edit, which is incremental.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = ("<item name=\"android:background\">@android:color/transparent</item>");
      int offset = document.getText().indexOf(item);
      assertThat(offset > 0).isTrue();
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar")).isTrue();
    style = getOnlyItem(repository, ResourceType.STYLE, "DarkActionBar");
    styleValue = (StyleResourceValue)style.getResourceValue();
    assertThat(styleValue).isNotNull();
    checkDefinedItems(styleValue);

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void editDeclareStyleableAttr() throws Exception {
    // Check edits of the name in a <declare-styleable> element.
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    myFixture.openFileInEditor(file1);
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ATTR, "watchType")).isTrue();
    ResourceItem style = getOnlyItem(repository, ResourceType.STYLEABLE, "MyCustomView");
    StyleableResourceValue styleValue = (StyleableResourceValue)style.getResourceValue();
    assertThat(styleValue).isNotNull();
    assertThat(styleValue.getAllAttributes().size()).isEqualTo(5);
    AttrResourceValue watchType = findAttr(styleValue, "watchType", repository);
    assertThat(watchType).isNotNull();
    assertThat(watchType.getAttributeValues().size()).isEqualTo(2);
    assertThat(watchType.getAttributeValues().get("type_stopwatch")).isEqualTo(Integer.valueOf(1));
    assertThat(watchType.getAttributeValues().get("type_countdown")).isEqualTo(Integer.valueOf(0));
    AttrResourceValue crash = findAttr(styleValue, "crash", repository);
    assertThat(crash).isNotNull();
    assertThat(crash.getAttributeValues().isEmpty()).isTrue();

    AttrResourceValue minWidth = findAttr(styleValue, "minWidth", repository);
    assertThat(minWidth).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ATTR, "minWidth")).isFalse();
    AttrResourceValue ignoredNoFormat = findAttr(styleValue, "ignore_no_format", repository);
    assertThat(ignoredNoFormat).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ATTR, "ignore_no_format")).isFalse();

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    type("MyCustom|View", "r");
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomrView")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ATTR, "watchType")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView")).isFalse();

    // Now try another edit, where things should be incremental now.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    type("MyCustom|rView", "e");
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomerView")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ATTR, "watchType")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView")).isFalse();
    style = getOnlyItem(repository, ResourceType.STYLEABLE, "MyCustomerView");
    styleValue = (StyleableResourceValue)style.getResourceValue();
    assertThat(styleValue).isNotNull();
    assertThat(styleValue.getAllAttributes().size()).isEqualTo(5);
    watchType = findAttr(styleValue, "watchType", repository);
    assertThat(watchType).isNotNull();
    assertThat(watchType.getAttributeValues().size()).isEqualTo(2);
    assertThat(watchType.getAttributeValues().get("type_stopwatch")).isEqualTo(Integer.valueOf(1));

    generation = repository.getModificationCount();
    type("watch|Type", "Change");
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    assertThat(getOnlyItem(repository, ResourceType.STYLEABLE, "MyCustomerView")).isSameAs(style);
    assertThat(style.getResourceValue()).isNotSameAs(styleValue);
    styleValue = (StyleableResourceValue)style.getResourceValue();
    assertThat(findAttr(styleValue, "watchType", repository)).isNull();
    assertThat(findAttr(styleValue, "watchChangeType", repository)).isNotNull();

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void editAttr() throws Exception {
    // Insert, remove and change <attr> attributes inside a <declare-styleable> and ensure that
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView")).isTrue();
    // Fetch resource value to ensure it gets replaced after update.
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ATTR, "watchType")).isTrue();
    ResourceItem style = getOnlyItem(repository, ResourceType.STYLEABLE, "MyCustomView");
    StyleableResourceValue styleValue = (StyleableResourceValue)style.getResourceValue();
    assertThat(styleValue).isNotNull();
    assertThat(styleValue.getAllAttributes().size()).isEqualTo(5);
    AttrResourceValue watchType = findAttr(styleValue, "watchType", repository);
    assertThat(watchType).isNotNull();
    assertThat(watchType.getAttributeValues().size()).isEqualTo(2);
    assertThat(watchType.getAttributeValues().get("type_stopwatch")).isEqualTo(Integer.valueOf(1));
    assertThat(watchType.getAttributeValues().get("type_countdown")).isEqualTo(Integer.valueOf(0));
    AttrResourceValue crash = findAttr(styleValue, "crash", repository);
    assertThat(crash).isNotNull();
    assertThat(crash.getAttributeValues().isEmpty()).isTrue();

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("watchType");
      document.insertString(offset, "y");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ATTR, "watchType")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ATTR, "ywatchType")).isTrue();

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ATTR, "watchType")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ATTR, "wwatchType")).isTrue();
    style = getOnlyItem(repository, ResourceType.STYLEABLE, "MyCustomView");
    styleValue = (StyleableResourceValue)style.getResourceValue();
    assertThat(styleValue).isNotNull();
    assertThat(styleValue.getAllAttributes().size()).isEqualTo(5);
    watchType = findAttr(styleValue, "wwatchType", repository);
    assertThat(watchType).isNotNull();
    assertThat(watchType.getAttributeValues().size()).isEqualTo(2);
    assertThat(watchType.getAttributeValues().get("type_stopwatch")).isEqualTo(Integer.valueOf(1));

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ATTR, "watchType")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ATTR, "wwatchType")).isTrue();
    ResourceItem style1 = getOnlyItem(repository, ResourceType.STYLEABLE, "MyCustomView");
    StyleableResourceValue srv1 = (StyleableResourceValue)style1.getResourceValue();
    assertThat(srv1).isNotNull();
    assertThat(srv1.getAllAttributes().size()).isEqualTo(5);
    AttrResourceValue watchType1 = findAttr(srv1, "wwatchType", repository);
    assertThat(watchType1).isNotNull();
    assertThat(watchType1.getAttributeValues().size()).isEqualTo(2);
    assertThat(watchType1.getAttributeValues().get("type_stopwatch")).isEqualTo(Integer.valueOf(1));
    assertThat(watchType1.getAttributeValues().get("type_countdown")).isEqualTo(Integer.valueOf(0));
    AttrResourceValue crash1 = findAttr(srv1, "crash", repository);
    assertThat(crash1).isNull();
    AttrResourceValue newcrash = findAttr(srv1, "newcrash", repository);
    assertThat(newcrash).isNotNull();
    assertThat(newcrash.getAttributeValues().isEmpty()).isTrue();
  }

  @Test
  public void editDeclareStyleableFlag() throws Exception {
    // Rename, add and remove <flag> and <enum> nodes under a declare styleable and assert
    // that the declare styleable parent is updated
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView")).isTrue();
    // Fetch resource value to ensure it gets replaced after update
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ATTR, "watchType")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ATTR, "ignore_no_format")).isFalse();
    ResourceItem style = getOnlyItem(repository, ResourceType.STYLEABLE, "MyCustomView");
    StyleableResourceValue styleValue = (StyleableResourceValue)style.getResourceValue();
    assertThat(styleValue).isNotNull();
    assertThat(styleValue.getAllAttributes().size()).isEqualTo(5);
    AttrResourceValue flagType = findAttr(styleValue, "flagType", repository);
    assertThat(flagType).isNotNull();
    assertThat(flagType.getAttributeValues().size()).isEqualTo(2);
    assertThat(flagType.getAttributeValues().get("flag1")).isEqualTo(Integer.valueOf(16));
    assertThat(flagType.getAttributeValues().get("flag2")).isEqualTo(Integer.valueOf(32));

    long generation = repository.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("flag1");
      document.insertString(offset + 1, "l");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(1);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ATTR, "flagType")).isTrue();
    ResourceItem style1 = getOnlyItem(repository, ResourceType.STYLEABLE, "MyCustomView");
    StyleableResourceValue styleableValue = (StyleableResourceValue)style1.getResourceValue();
    assertThat(styleableValue).isNotNull();
    assertThat(styleableValue.getAllAttributes().size()).isEqualTo(5);
    AttrResourceValue flagType1 = findAttr(styleableValue, "flagType", repository);
    assertThat(flagType1).isNotNull();
    assertThat(flagType1.getAttributeValues().size()).isEqualTo(2);
    assertThat(flagType1.getAttributeValues().get("flag1")).isNull();
    assertThat(flagType1.getAttributeValues().get("fllag1")).isEqualTo(Integer.valueOf(16));

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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ATTR, "watchType")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ATTR, "flagType")).isTrue();
    style1 = getOnlyItem(repository, ResourceType.STYLEABLE, "MyCustomView");
    styleableValue = (StyleableResourceValue)style1.getResourceValue();
    assertThat(styleableValue).isNotNull();
    assertThat(styleableValue.getAllAttributes().size()).isEqualTo(5);
    flagType1 = findAttr(styleableValue, "flagType", repository);
    assertThat(flagType1).isNotNull();
    assertThat(flagType1.getAttributeValues().size()).isEqualTo(3);
    assertThat(flagType1.getAttributeValues().get("fllag1")).isEqualTo(Integer.valueOf(16));
    assertThat(flagType1.getAttributeValues().get("flag2")).isEqualTo(Integer.valueOf(32));
    assertThat(flagType1.getAttributeValues().get("flag3")).isEqualTo(Integer.valueOf(64));

    AttrResourceValue watchType = findAttr(styleableValue, "watchType", repository);
    assertThat(watchType).isNotNull();
    assertThat(watchType.getAttributeValues().size()).isEqualTo(1);
    assertThat(watchType.getAttributeValues().get("type_countdown")).isEqualTo(Integer.valueOf(0));
  }

  @Test
  public void editPluralItems() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    // Test that our tools:quantity works correctly for getResourceValue()
    assertThat(repository.hasResources(RES_AUTO, ResourceType.PLURALS, "my_plural")).isTrue();
    ResourceItem plural = getOnlyItem(repository, ResourceType.PLURALS, "my_plural");
    ResourceValue resourceValue = plural.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("@string/hello_two");

    // TODO: It would be nice to avoid updating the generation if you edit a different item
    //       than the one being picked (default or via tools:quantity) but for now we're not
    //       worrying about that optimization.

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("@string/hello_two");
      document.replaceString(offset + 9, offset + 10, "a");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    plural = getOnlyItem(repository, ResourceType.PLURALS, "my_plural");
    resourceValue = plural.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("@string/hallo_two");
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
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("@string/hillo_two");
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void addPluralItems() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.PLURALS, "my_plural")).isTrue();
    ResourceItem plural = getOnlyItem(repository, ResourceType.PLURALS, "my_plural");
    ResourceValue resourceValue = plural.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue).isInstanceOf(PluralsResourceValue.class);
    PluralsResourceValue prv = (PluralsResourceValue)resourceValue;
    assertThat(prv.getPluralsCount()).isEqualTo(3);
    assertThat(resourceValue.getValue()).isEqualTo("@string/hello_two");
    assertThat(prv.getQuantity(1)).isEqualTo("two");

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item quantity=\"two\">@string/hello_two</item>");
      document.insertString(offset, "<item quantity=\"one_and_half\">@string/hello_one_and_half</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    plural = getOnlyItem(repository, ResourceType.PLURALS, "my_plural");
    resourceValue = plural.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue).isInstanceOf(PluralsResourceValue.class);
    prv = (PluralsResourceValue)resourceValue;
    assertThat(prv.getPluralsCount()).isEqualTo(4);
    assertThat(resourceValue.getValue()).isEqualTo("@string/hello_two");
    assertThat(prv.getQuantity(1)).isEqualTo("one_and_half");
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
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue).isInstanceOf(PluralsResourceValue.class);
    prv = (PluralsResourceValue)resourceValue;
    assertThat(prv.getPluralsCount()).isEqualTo(5);
    assertThat(resourceValue.getValue()).isEqualTo("@string/hello_two");
    assertThat(prv.getQuantity(1)).isEqualTo("one_and_a_quarter");
    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void removePluralItems() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.PLURALS, "my_plural")).isTrue();
    ResourceItem plural = getOnlyItem(repository, ResourceType.PLURALS, "my_plural");
    ResourceValue resourceValue = plural.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue).isInstanceOf(PluralsResourceValue.class);
    PluralsResourceValue prv = (PluralsResourceValue)resourceValue;
    assertThat(prv.getPluralsCount()).isEqualTo(3);
    assertThat(resourceValue.getValue()).isEqualTo("@string/hello_two");
    assertThat(prv.getQuantity(0)).isEqualTo("one");

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = "<item quantity=\"one\">@string/hello</item>";
      int offset = document.getText().indexOf(item);
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    plural = getOnlyItem(repository, ResourceType.PLURALS, "my_plural");
    resourceValue = plural.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue).isInstanceOf(PluralsResourceValue.class);
    prv = (PluralsResourceValue)resourceValue;
    assertThat(prv.getPluralsCount()).isEqualTo(2);
    assertThat(resourceValue.getValue()).isEqualTo("@string/hello_two");
    assertThat(prv.getQuantity(0)).isEqualTo("two");
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
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue).isInstanceOf(PluralsResourceValue.class);
    prv = (PluralsResourceValue)resourceValue;
    assertThat(prv.getPluralsCount()).isEqualTo(1);
    assertThat(resourceValue.getValue()).isEqualTo("@string/hello_two");
    assertThat(prv.getQuantity(0)).isEqualTo("two");
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void editArrayItemText() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    // Test that our tools:index and fallback handling for arrays works correctly
    // for getResourceValue()
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions")).isTrue();
    ResourceItem array = getOnlyItem(repository, ResourceType.ARRAY, "security_questions");
    ResourceValue resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("Question 4");

    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "integers")).isTrue();
    array = getOnlyItem(repository, ResourceType.ARRAY, "integers");
    resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("10");

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("Question 4");
      document.insertString(offset, "Q");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions")).isTrue();
    array = getOnlyItem(repository, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("QQuestion 4");
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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions")).isTrue();
    array = getOnlyItem(repository, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("QQQuestion 4");
    assertThat(repository.getModificationCount()).isGreaterThan(generation);

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void addStringArrayItemElements() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions")).isTrue();
    ResourceItem array = getOnlyItem(repository, ResourceType.ARRAY, "security_questions");
    ResourceValue resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("Question 4");
    assertThat(resourceValue instanceof ArrayResourceValue).isTrue();
    ArrayResourceValue arv = (ArrayResourceValue)resourceValue;
    assertThat(arv.getElementCount()).isEqualTo(5);
    assertThat(arv.getElement(1)).isEqualTo("Question 2");
    assertThat(arv.getElement(2)).isEqualTo("Question 3");
    assertThat(arv.getElement(3)).isEqualTo("Question 4");

    int rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item>Question 3</item>");
      document.insertString(offset, "<item>Question 2.5</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions")).isTrue();
    array = getOnlyItem(repository, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("Question 3");
    assertThat(resourceValue instanceof ArrayResourceValue).isTrue();
    arv = (ArrayResourceValue)resourceValue;
    assertThat(arv.getElementCount()).isEqualTo(6);
    assertThat(arv.getElement(1)).isEqualTo("Question 2");
    assertThat(arv.getElement(2)).isEqualTo("Question 2.5");
    assertThat(arv.getElement(3)).isEqualTo("Question 3");
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    rescans = repository.getFileRescans();
    // However, the second edit can then be incremental.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item>Question 3</item>");
      document.insertString(offset, "<item>Question 2.75</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions")).isTrue();
    array = getOnlyItem(repository, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("Question 2.75");
    assertThat(resourceValue instanceof ArrayResourceValue).isTrue();
    arv = (ArrayResourceValue)resourceValue;
    assertThat(arv.getElementCount()).isEqualTo(7);
    assertThat(arv.getElement(1)).isEqualTo("Question 2");
    assertThat(arv.getElement(2)).isEqualTo("Question 2.5");
    assertThat(arv.getElement(3)).isEqualTo("Question 2.75");
    assertThat(arv.getElement(4)).isEqualTo("Question 3");

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void removeStringArrayItemElements() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions")).isTrue();
    ResourceItem array = getOnlyItem(repository, ResourceType.ARRAY, "security_questions");
    ResourceValue resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("Question 4");
    assertThat(resourceValue instanceof ArrayResourceValue).isTrue();
    ArrayResourceValue arv = (ArrayResourceValue)resourceValue;
    assertThat(arv.getElementCount()).isEqualTo(5);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String elementString = "<item>Question 3</item>";
      int offset = document.getText().indexOf(elementString);
      document.deleteString(offset, offset + elementString.length());
      document.insertString(offset, "<item>Question X</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions")).isTrue();
    array = getOnlyItem(repository, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("Question 4");
    assertThat(resourceValue instanceof ArrayResourceValue).isTrue();
    arv = (ArrayResourceValue)resourceValue;
    assertThat(arv.getElementCount()).isEqualTo(5);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    rescans = repository.getFileRescans();
    // Now try another edit that is also a delete item, where things should be incremental now.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String elementString = "<item>Question X</item>";
      int offset = document.getText().indexOf(elementString);
      document.deleteString(offset, offset + elementString.length());
      documentManager.commitDocument(document);
    });

    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions")).isTrue();
    array = getOnlyItem(repository, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("Question 5");
    assertThat(resourceValue instanceof ArrayResourceValue).isTrue();
    arv = (ArrayResourceValue)resourceValue;
    assertThat(arv.getElementCount()).isEqualTo(4);

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void addIntegerArrayItemElements() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "integers")).isTrue();
    ResourceItem array = getOnlyItem(repository, ResourceType.ARRAY, "integers");
    ResourceValue resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("10");
    assertThat(resourceValue instanceof ArrayResourceValue).isTrue();
    ArrayResourceValue arv = (ArrayResourceValue)resourceValue;
    assertThat(arv.getElementCount()).isEqualTo(2);
    assertThat(arv.getElement(0)).isEqualTo("10");
    assertThat(arv.getElement(1)).isEqualTo("20");

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item>10</item>");
      document.insertString(offset, "<item>5</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "integers")).isTrue();
    array = getOnlyItem(repository, ResourceType.ARRAY, "integers");
    resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("5");
    assertThat(resourceValue instanceof ArrayResourceValue).isTrue();
    arv = (ArrayResourceValue)resourceValue;
    assertThat(arv.getElementCount()).isEqualTo(3);
    assertThat(arv.getElement(0)).isEqualTo("5");
    assertThat(arv.getElement(1)).isEqualTo("10");
    assertThat(arv.getElement(2)).isEqualTo("20");
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    rescans = repository.getFileRescans();
    // Now try a second edit.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item>5</item>");
      document.insertString(offset, "<item>2</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "integers")).isTrue();
    array = getOnlyItem(repository, ResourceType.ARRAY, "integers");
    resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("2");
    assertThat(resourceValue instanceof ArrayResourceValue).isTrue();
    arv = (ArrayResourceValue)resourceValue;
    assertThat(arv.getElementCount()).isEqualTo(4);
    assertThat(arv.getElement(0)).isEqualTo("2");
    assertThat(arv.getElement(1)).isEqualTo("5");
    assertThat(arv.getElement(2)).isEqualTo("10");
    assertThat(arv.getElement(3)).isEqualTo("20");

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void removeIntegerArrayItemElements() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "integers")).isTrue();
    ResourceItem array = getOnlyItem(repository, ResourceType.ARRAY, "integers");
    ResourceValue resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("10");
    assertThat(resourceValue instanceof ArrayResourceValue).isTrue();
    ArrayResourceValue arv = (ArrayResourceValue)resourceValue;
    assertThat(arv.getElementCount()).isEqualTo(2);
    assertThat(arv.getElement(0)).isEqualTo("10");
    assertThat(arv.getElement(1)).isEqualTo("20");

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = "<item>10</item>";
      int offset = document.getText().indexOf(item);
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "integers")).isTrue();
    array = getOnlyItem(repository, ResourceType.ARRAY, "integers");
    resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue instanceof ArrayResourceValue).isTrue();
    arv = (ArrayResourceValue)resourceValue;
    assertThat(arv.getElementCount()).isEqualTo(1);
    assertThat(resourceValue.getValue()).isEqualTo("20");
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    rescans = repository.getFileRescans();
    // Try a second edit.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = "<item>20</item>";
      int offset = document.getText().indexOf(item);
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "integers")).isTrue();
    array = getOnlyItem(repository, ResourceType.ARRAY, "integers");
    resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue instanceof ArrayResourceValue).isTrue();
    arv = (ArrayResourceValue)resourceValue;
    assertThat(arv.getElementCount()).isEqualTo(0);

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void addTypedArrayItemElements() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "my_colors")).isTrue();
    ResourceItem array = getOnlyItem(repository, ResourceType.ARRAY, "my_colors");
    ResourceValue resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("#FFFF0000");
    assertThat(resourceValue instanceof ArrayResourceValue).isTrue();
    ArrayResourceValue arv = (ArrayResourceValue)resourceValue;
    assertThat(arv.getElementCount()).isEqualTo(3);
    assertThat(arv.getElement(0)).isEqualTo("#FFFF0000");
    assertThat(arv.getElement(1)).isEqualTo("#FF00FF00");
    assertThat(arv.getElement(2)).isEqualTo("#FF0000FF");

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item>#FFFF0000</item>");
      document.insertString(offset, "<item>#FFFFFF00</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "my_colors")).isTrue();
    array = getOnlyItem(repository, ResourceType.ARRAY, "my_colors");
    resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("#FFFFFF00");
    assertThat(resourceValue instanceof ArrayResourceValue).isTrue();
    arv = (ArrayResourceValue)resourceValue;
    assertThat(arv.getElementCount()).isEqualTo(4);
    assertThat(arv.getElement(0)).isEqualTo("#FFFFFF00");
    assertThat(arv.getElement(1)).isEqualTo("#FFFF0000");
    assertThat(arv.getElement(2)).isEqualTo("#FF00FF00");
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    rescans = repository.getFileRescans();
    // Now try a second edit.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item>#FFFFFF00</item>");
      document.insertString(offset, "<item>#FFFFFFFF</item>");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ARRAY, "my_colors")).isTrue();
    array = getOnlyItem(repository, ResourceType.ARRAY, "my_colors");
    resourceValue = array.getResourceValue();
    assertThat(resourceValue).isNotNull();
    assertThat(resourceValue.getValue()).isEqualTo("#FFFFFFFF");
    assertThat(resourceValue instanceof ArrayResourceValue).isTrue();
    arv = (ArrayResourceValue)resourceValue;
    assertThat(arv.getElementCount()).isEqualTo(5);
    assertThat(arv.getElement(0)).isEqualTo("#FFFFFFFF");
    assertThat(arv.getElement(1)).isEqualTo("#FFFFFF00");
    assertThat(arv.getElement(2)).isEqualTo("#FFFF0000");
    assertThat(arv.getElement(3)).isEqualTo("#FF00FF00");

    // Shouldn't have done any full file rescans during the above edits.
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void gradualEdits() throws Exception {
    // Gradually type in the contents of a value file and make sure we end up with a valid view of the world
    VirtualFile file1 = myFixture.copyFileToProject(VALUES_EMPTY, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();
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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar")).isTrue();
    ResourceItem style = getOnlyItem(repository, ResourceType.STYLE, "DarkActionBar");
    StyleResourceValue styleValue = (StyleResourceValue)style.getResourceValue();
    assertThat(styleValue).isNotNull();
    ResourceValue actionBarStyle = styleValue.getItem(ANDROID, "background");
    assertThat(actionBarStyle).isNotNull();
    assertThat(actionBarStyle.getValue()).isEqualTo("@android:color/transparent");
    assertThat(getOnlyItem(repository, ResourceType.STRING, "title_zoom").getResourceValue().getValue()).isEqualTo("Zoom");
  }

  @Test
  public void invalidValueName() throws Exception {
    VirtualFile file = myFixture.copyFileToProject(VALUES_EMPTY, "res/values/empty.xml");
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    assertThat(psiFile).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    assertThat(documentManager).isNotNull();
    Document document = documentManager.getDocument(psiFile);
    assertThat(document).isNotNull();

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

  @Test
  public void invalidId() throws Exception {
    VirtualFile file = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    assertThat(psiFile).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet())
        .containsExactly("btn_title_refresh", "header", "noteArea", "text2", "title_refresh_progress");
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    assertThat(documentManager).isNotNull();
    Document document = documentManager.getDocument(psiFile);
    assertThat(document).isNotNull();

    XmlTag tag = findTagById(psiFile, "noteArea");
    assertThat(tag).isNotNull();
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

  @Test
  public void invalidFileResourceName() {
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

  @Test
  public void issue36973561() throws Exception {
    // Test deleting a string; ensure that the whole repository is updated correctly.
    // Regression test for https://issuetracker.google.com/36973561.
    VirtualFile file1 = myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name2")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "hello_world")).isTrue();

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String string = "    <string name=\"hello_world\">Hello world!</string>";
      int offset = document.getText().indexOf(string);
      assertThat(offset != -1).isTrue();
      document.deleteString(offset, offset + string.length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "action_settings")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "hello_world")).isFalse();
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    // However, the second edit can then be incremental.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String string = "    <string name=\"app_name\">My Application 574</string>";
      int offset = document.getText().indexOf(string);
      assertThat(offset != -1).isTrue();
      document.deleteString(offset, offset + string.length());
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "action_settings")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "hello_world")).isFalse();
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
  }

  @Test
  public void editXmlProcessingInstructionAttrInValues() throws Exception {
    // Test editing an attribute in the XML prologue of a values file.
    VirtualFile file1 = myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name2")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "hello_world")).isTrue();

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String string = "utf-8";
      int offset = document.getText().indexOf(string);
      assertThat(offset != -1).isTrue();
      document.insertString(offset, "t");
      documentManager.commitDocument(document);
    });

    // Edits in XML processing instructions have no effect on the resource repository.
    assertThat(repository.getModificationCount()).isEqualTo(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.
    waitForUpdates(repository);
  }

  @Test
  public void editXmlProcessingInstructionAttrInLayout() throws Exception {
    // Test editing an attribute in the XML prologue of a layout file with IDs.
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea")).isTrue();

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String string = "utf-8";
      int offset = document.getText().indexOf(string);
      assertThat(offset != -1).isTrue();
      document.insertString(offset, "t");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    // Edits in XML processing instructions have no effect on the resource repository.
    assertThat(repository.getModificationCount()).isEqualTo(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.
  }

  @Test
  public void issue36986886() throws Exception {
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
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isTrue();
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING, "app_name").get(0).getResourceValue().getValue()).isEqualTo(
      "My Application 574");

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    int offset = document.getText().indexOf("</resources>");
    assertThat(offset != -1).isTrue();
    String string = "<string name=\"app_name\">New Value</string>";

    // First duplicate the line:
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      document.insertString(offset, string);
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isTrue();
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    // Second edit (duplicate again).
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    int offset2 = document.getText().indexOf("</resources>");
    String string2 = "<string name=\"app_name\">Another Value</string>";
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      document.insertString(offset2, string2);
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isTrue();

    // Then replace the name of the duplicated string.
    generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      int startOffset = offset + "<string name=\"".length();
      document.replaceString(startOffset, startOffset + "app_name".length(), "new_name");
      documentManager.commitDocument(document);
    });

    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "new_name")).isTrue();

    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING, "new_name").get(0).getResourceValue().getValue()).isEqualTo(
      "New Value");
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING, "app_name").get(0).getResourceValue().getValue()).isEqualTo(
      "My Application 574");

    // Replace the second duplicate.
    generation = repository.getModificationCount();
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      int startOffset = offset2 + "<string name=\"".length();
      document.replaceString(startOffset, startOffset + "app_name".length(), "new_name2");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "new_name")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "new_name2")).isTrue();

    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING, "new_name").get(0).getResourceValue().getValue()).isEqualTo(
      "New Value");
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING, "new_name2").get(0).getResourceValue().getValue()).isEqualTo(
      "Another Value");
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING, "app_name").get(0).getResourceValue().getValue()).isEqualTo(
      "My Application 574");
  }

  @Test
  public void loadDuplicatedValues() throws Exception {
    // Test loading up a file that already illegally had duplicates.
    VirtualFile file1 = myFixture.copyFileToProject(VALUES_WITH_DUPES, "res/values/values.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "dupe_name")).isFalse();
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING, "app_name").get(0).getResourceValue().getValue()).isEqualTo(
      "Animations Demo");

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    // Try editting one of the duplicated string contents, and check that the copies are not tied together.
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      String origString = "<string name=\"app_name\">Animations Demo</string>";
      String newString = "<string name=\"dupe_name\">Duplicate Demo</string>";
      int offset = document.getText().indexOf(origString);
      document.replaceString(offset, offset + origString.length(), newString);
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "dupe_name")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isTrue();
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING, "dupe_name").get(0).getResourceValue().getValue()).isEqualTo(
      "Duplicate Demo");
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING, "app_name").get(0).getResourceValue().getValue()).isEqualTo(
      "Animations Demo");
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    // Try editting something else, like the ID item.
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_prev")).isFalse();
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      String origString = "<item type=\"id\" name=\"action_next\" />";
      String newString = "<item type=\"id\" name=\"action_prev\" />";
      int offset = document.getText().indexOf(origString);
      document.replaceString(offset, offset + origString.length(), newString);
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_next")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "action_prev")).isTrue();
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.
  }

  /** Regression test for b/115880623 */
  @Test
  public void removeDuplicate() throws Exception {
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

  @Test
  public void loadValuesWithBadName() throws Exception {
    // If a file had bad value names, test that it can still be parsed.
    VirtualFile file = myFixture.copyFileToProject(VALUES_WITH_BAD_NAME, "res/values/values.xml");
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    assertThat(psiFile).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    assertThat(repository.getResources(RES_AUTO, ResourceType.STRING)).isEmpty();

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile);
    assertThat(document).isNotNull();

    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      String origString = "<string name=\"app*name\">Animations Demo</string>";
      String newString = "<string name=\"app_name\">Fixed Animations Demo</string>";
      int offset = document.getText().indexOf(origString);
      document.replaceString(offset, offset + origString.length(), newString);
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.getFileRescans()).isAtLeast(rescans + 1);
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    List<ResourceItem> items = repository.getResources(RES_AUTO, ResourceType.STRING, "app_name");
    assertThat(items).hasSize(1);
    assertThat(items.get(0).getResourceValue().getValue()).isEqualTo("Fixed Animations Demo");
  }

  /**
   * Test for http://b/138841328.
   */
  @Test
  public void loadAfterExternalChangeToLayout() throws Exception {
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

  @Test
  public void idScanFromLayout() {
    // Test for https://issuetracker.google.com/37044944.
    myFixture.copyFileToProject(LAYOUT_ID_SCAN, "res/layout/layout1.xml");
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();
    Collection<String> ids = repository.getResources(RES_AUTO, ResourceType.ID).keySet();
    assertThat(ids).isNotNull();
    assertThat(ids).containsAllOf("header", "image", "styledView", "imageView", "imageView2", "imageButton", "nonExistent");
  }

  @Test
  public void initFromHelperThread() throws Exception {
    // By default, unit tests run from the EDT thread, which automatically have read access. Try loading a repository from a
    // helper thread that doesn't have read access to make sure we grab the appropriate read locks.
    // Use a data binding file, which we currently know uses a PsiDataBindingResourceItem.
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT_WITH_DATA_BINDING, "res/layout-land/layout_with_data_binding.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    VirtualFile file2 = myFixture.copyFileToProject(VALUES_WITH_DUPES, "res/values-en/values_with_dupes.xml");
    ExecutorService executorService = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("InitFromHelperThread");
    Future<ResourceFolderRepository> loadJob = executorService.submit(this::createRegisteredRepository);
    ResourceFolderRepository repository = loadJob.get();
    assertThat(repository).isNotNull();
    assertThat(getOnlyItem(repository, ResourceType.LAYOUT, "layout_with_data_binding").getConfiguration().getQualifierString()).isEqualTo(
      "land");
    ResourceItem dupedStringItem = repository.getResources(RES_AUTO, ResourceType.STRING, "app_name").get(0);
    assertThat(dupedStringItem).isNotNull();
    assertThat(dupedStringItem.getConfiguration().getQualifierString()).isEqualTo("en");
  }

  @Test
  public void editColorStateList() {
    VirtualFile file1 = myFixture.copyFileToProject(COLOR_STATELIST, "res/color/my_state_list.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.COLOR, "my_state_list")).isTrue();

    // Edit comment
    long generation = repository.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

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
  @Test
  public void emptyEdits() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
    Document document = documentManager.getDocument(psiFile1);
    assertThat(document).isNotNull();

    // Add a space to an attribute name.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("app_name");
      document.insertString(offset, "_");
      documentManager.commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "app_name")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "_app_name")).isTrue();
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
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "__app_name")).isTrue();
    ResourceItem item = getOnlyItem(repository, ResourceType.STRING, "title_zoom");
    assertThat(item.getResourceValue().getValue()).isEqualTo("Zoom");
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
  @Test
  public void fileCacheFreshness() {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout-xlarge-land/layout.xml");
    myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    ResourceFolderRepository repository = createRepository(true);
    assertThat(repository).isNotNull();
    assertThat(repository.hasFreshFileCache()).isFalse();
    assertThat(repository.getNumXmlFilesLoadedInitially()).isEqualTo(3);
    assertThat(repository.getNumXmlFilesLoadedInitiallyFromSources()).isEqualTo(repository.getNumXmlFilesLoadedInitially());

    ResourceFolderRepository resourcesReloaded = createRepository(false);
    assertThat(resourcesReloaded).isNotSameAs(repository);
    assertThat(resourcesReloaded.hasFreshFileCache()).isTrue();
    assertThat(resourcesReloaded.getNumXmlFilesLoadedInitially()).isEqualTo(3);
    assertThat(resourcesReloaded.getNumXmlFilesLoadedInitiallyFromSources()).isEqualTo(0);
  }

  @Test
  public void serialization() {
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
    assertThat(repository).isNotNull();
    assertThat(repository.hasFreshFileCache()).isFalse();
    assertThat(repository.getNumXmlFilesLoadedInitially()).isEqualTo(7);
    assertThat(repository.getNumXmlFilesLoadedInitiallyFromSources()).isEqualTo(repository.getNumXmlFilesLoadedInitially());

    ResourceFolderRepository fromCacheFile = createRepository(true);
    assertThat(fromCacheFile).isNotNull();
    // Check that fromCacheFile really avoided reparsing some XML files, before checking equivalence of items.
    assertThat(fromCacheFile.hasFreshFileCache()).isTrue();
    assertThat(fromCacheFile.getNumXmlFilesLoadedInitially()).isEqualTo(7);
    assertThat(fromCacheFile.getNumXmlFilesLoadedInitiallyFromSources()).isEqualTo(0);

    assertThat(fromCacheFile).isNotSameAs(repository);
    assertContainSameData(myFacet, repository, fromCacheFile);
  }

  @Test
  public void invalidateCache() {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout-xlarge-land/layout.xml");
    myFixture.copyFileToProject(LAYOUT_WITH_DATA_BINDING, "res/layout/layout_with_data_binding.xml");
    myFixture.copyFileToProject(DRAWABLE, "res/drawable/logo.png");
    myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    ResourceFolderRepository repository = createRepository(true);
    assertThat(repository).isNotNull();
    ResourceFolderRepositoryFileCacheService.get().invalidate();
    ResourceFolderRepository resourcesReloaded = createRepository(true);

    assertThat(resourcesReloaded.getNumXmlFilesLoadedInitiallyFromSources()).isNotSameAs(0);
    assertThat(resourcesReloaded.getNumXmlFilesLoadedInitiallyFromSources()).isEqualTo(
      resourcesReloaded.getNumXmlFilesLoadedInitially());
  }

  @Test
  public void serializationRemoveXmlFileAndLoad() {
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout.xml");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    myFixture.copyFileToProject(DRAWABLE, "res/drawable/logo.png");
    VirtualFile file2 = myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    ResourceFolderRepository repository = createRepository(true);
    assertThat(repository).isNotNull();

    // Check "repository" before deletion, and "fromCacheFile" after deletion.
    // Note that the in-memory "repository" also gets updated from a Psi listener
    // so save to blob before picking up the Psi change.
    assertThat(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "hello_world")).isTrue();

    // Delete a non-value file.
    WriteCommandAction.runWriteCommandAction(null, psiFile1::delete);

    ResourceFolderRepository fromCacheFile = createRepository(true);
    assertThat(fromCacheFile).isNotNull();
    // Non-value files aren't counted in the cache, so deleting doesn't affect freshness.
    assertThat(fromCacheFile.hasFreshFileCache()).isTrue();

    assertThat(fromCacheFile.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout")).isFalse();
    assertThat(fromCacheFile.hasResources(RES_AUTO, ResourceType.ID, "noteArea")).isFalse();
    assertThat(fromCacheFile.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo")).isTrue();
    assertThat(fromCacheFile.hasResources(RES_AUTO, ResourceType.STRING, "hello_world")).isTrue();

    // Delete a value file.
    PsiFile psiFile2 = PsiManager.getInstance(myProject).findFile(file2);
    assertThat(psiFile2).isNotNull();
    WriteCommandAction.runWriteCommandAction(null, psiFile2::delete);

    ResourceFolderRepository fromCacheFile2 = createRepository(true);
    assertThat(fromCacheFile2).isNotNull();
    // Value files are counted in the cache, but we only count the percentage re-parsed for freshness.
    // We don't count extraneous cache entries (but perhaps we should).
    assertThat(fromCacheFile2.hasFreshFileCache()).isTrue();

    assertThat(fromCacheFile2.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout")).isFalse();
    assertThat(fromCacheFile2.hasResources(RES_AUTO, ResourceType.ID, "noteArea")).isFalse();
    assertThat(fromCacheFile2.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo")).isTrue();
    assertThat(fromCacheFile2.hasResources(RES_AUTO, ResourceType.STRING, "hello_world")).isFalse();
  }

  @Test
  public void serializationRemoveDrawableFileAndLoad() {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout.xml");
    VirtualFile file1 = myFixture.copyFileToProject(DRAWABLE, "res/drawable/logo.png");
    PsiFile psiFile1 = PsiManager.getInstance(myProject).findFile(file1);
    assertThat(psiFile1).isNotNull();
    myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    ResourceFolderRepository repository = createRepository(true);
    assertThat(repository).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "hello_world")).isTrue();

    WriteCommandAction.runWriteCommandAction(null, psiFile1::delete);

    ResourceFolderRepository fromCacheFile = createRepository(true);
    assertThat(fromCacheFile).isNotNull();

    assertThat(fromCacheFile.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout")).isTrue();
    assertThat(fromCacheFile.hasResources(RES_AUTO, ResourceType.ID, "noteArea")).isTrue();
    assertThat(fromCacheFile.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo")).isFalse();
    assertThat(fromCacheFile.hasResources(RES_AUTO, ResourceType.STRING, "hello_world")).isTrue();
  }

  @Test
  public void serializationEditXmlFileAndLoad() throws Exception {
    VirtualFile file1 = myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    File file1AsFile = virtualToIoFile(file1);
    assertThat(file1AsFile).isNotNull();
    ResourceFolderRepository repository = createRepository(true);
    assertThat(repository).isNotNull();

    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "hello_world")).isTrue();

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
    assertThat(fromCacheFile).isNotNull();
    assertThat(fromCacheFile.hasFreshFileCache()).isFalse();

    assertThat(fromCacheFile.hasResources(RES_AUTO, ResourceType.STRING, "hello_there")).isTrue();
  }

  @Test
  public void serializationAddXmlFileAndLoad() {
    myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    ResourceFolderRepository repository = createRepository(true);
    assertThat(repository).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "hello_world")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "noteArea")).isFalse();

    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout.xml");

    ResourceFolderRepository fromCacheFile = createRepository(false);
    assertThat(fromCacheFile).isNotNull();
    // Freshness depends on a heurisitic, but now half the XML files are parsed.
    assertThat(fromCacheFile.hasFreshFileCache()).isFalse();

    assertThat(fromCacheFile.hasResources(RES_AUTO, ResourceType.STRING, "hello_world")).isTrue();
    assertThat(fromCacheFile.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout")).isTrue();
    assertThat(fromCacheFile.hasResources(RES_AUTO, ResourceType.ID, "noteArea")).isTrue();
  }

  @Test
  public void serializationAddDrawableFileAndLoad() {
    myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    ResourceFolderRepository repository = createRepository(true);
    assertThat(repository).isNotNull();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "hello_world")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo")).isFalse();

    myFixture.copyFileToProject(DRAWABLE, "res/drawable/logo.png");

    ResourceFolderRepository fromCacheFile = createRepository(true);
    assertThat(fromCacheFile).isNotNull();
    // Freshness depends on a heurisitic, but we don't count PNG in the blob.
    assertThat(fromCacheFile.hasFreshFileCache()).isTrue();

    assertThat(fromCacheFile.hasResources(RES_AUTO, ResourceType.STRING, "hello_world")).isTrue();
    assertThat(fromCacheFile.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo")).isTrue();
  }

  @Test
  public void serializeLayoutAndIdResourceValues() {
    myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout/activity_foo.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout-xlarge-land/activity_foo.xml");
    ResourceFolderRepository repository = createRepository(true);
    assertThat(repository).isNotNull();
    FolderConfiguration config = FolderConfiguration.getConfigForFolder("layout-xlarge-land");
    assertThat(config).isNotNull();
    // For layouts, the ResourceValue#getValue is the file path.
    ResourceValue value = ResourceRepositoryUtil.getConfiguredValue(repository, ResourceType.LAYOUT, "activity_foo", config);
    assertThat(value).isNotNull();
    String valueString = value.getValue();
    assertThat(valueString).isNotNull();
    assertThat(valueString.endsWith("activity_foo.xml")).isTrue();
    assertThat(valueString.contains("layout-xlarge-land")).isTrue();

    ResourceFolderRepository fromCacheFile = createRepository(true);
    assertThat(fromCacheFile).isNotNull();
    assertThat(fromCacheFile.hasFreshFileCache()).isTrue();

    assertThat(fromCacheFile).isNotSameAs(repository);
    assertContainSameData(myFacet, repository, fromCacheFile);
    value = ResourceRepositoryUtil.getConfiguredValue(fromCacheFile, ResourceType.LAYOUT, "activity_foo", config);
    assertThat(value).isNotNull();
    valueString = value.getValue();
    assertThat(valueString).isNotNull();
    assertThat(valueString.endsWith("activity_foo.xml")).isTrue();
    assertThat(valueString.contains("layout-xlarge-land")).isTrue();
  }

  @Test
  public void serializeDensityBasedResourceValues() {
    myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/drawable-hdpi/drawable_foo.xml");
    myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/drawable-xhdpi/drawable_foo.xml");
    myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/drawable-fr/drawable_foo.xml");
    ResourceFolderRepository repository = createRepository(true);
    assertThat(repository).isNotNull();
    FolderConfiguration config = FolderConfiguration.getConfigForFolder("drawable-xhdpi");
    assertThat(config).isNotNull();
    // For drawable xml, the ResourceValue#getValue is the file path.
    ResourceValue value = ResourceRepositoryUtil.getConfiguredValue(repository, ResourceType.DRAWABLE, "drawable_foo", config);
    assertThat(value).isNotNull();
    String valueString = value.getValue();
    assertThat(valueString).isNotNull();
    assertThat(valueString.endsWith("drawable_foo.xml")).isTrue();
    assertThat(valueString.contains("drawable-xhdpi")).isTrue();
    DensityBasedResourceValue densityValue = (DensityBasedResourceValue)value;
    assertThat(densityValue.getResourceDensity()).isEqualTo(Density.XHIGH);

    ResourceFolderRepository fromCacheFile = createRepository(true);
    assertThat(fromCacheFile).isNotNull();
    // We don't count files that we explicitly skip against freshness.
    assertThat(fromCacheFile.hasFreshFileCache()).isTrue();

    assertThat(fromCacheFile).isNotSameAs(repository);
    assertContainSameData(myFacet, repository, fromCacheFile);
    value = ResourceRepositoryUtil.getConfiguredValue(fromCacheFile, ResourceType.DRAWABLE, "drawable_foo", config);
    assertThat(value).isNotNull();
    valueString = value.getValue();
    assertThat(valueString).isNotNull();
    assertThat(valueString.endsWith("drawable_foo.xml")).isTrue();
    assertThat(valueString.contains("drawable-xhdpi")).isTrue();
    // Make sure that the resource value is still of type DensityBasedResourceValue.
    densityValue = (DensityBasedResourceValue)value;
    assertThat(densityValue.getResourceDensity()).isEqualTo(Density.XHIGH);
  }

  /**
   * Checks that we handle PSI invalidation behaviour for PsiResourceItem.
   * When getting out of dumb mode, if the file has been modified during the dumb mode,
   * the file (and tags) will be invalidated.
   * Regression test for http://b/73623886.
   */
  @Test
  public void fileInvalidationAfterDumbMode() throws Exception {
    ResourceItem resourceItem = DumbModeTestUtils.computeInDumbModeSynchronously(myProject, () -> {
      VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
      PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file1);
      assertThat(psiFile).isNotNull();
      ResourceFolderRepository repository = createRegisteredRepository();
      assertThat(repository).isNotNull();
      assertThat(repository.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme")).isTrue();

      int rescans = repository.getFileRescans();
      PsiDocumentManager documentManager = PsiDocumentManager.getInstance(myProject);
      Document document = documentManager.getDocument(psiFile);
      assertThat(document).isNotNull();

      WriteCommandAction.runWriteCommandAction(null, () -> {
        int offset = document.getText().indexOf("DarkTheme");
        document.replaceString(offset, offset + 4, "Grey");
        documentManager.commitDocument(document);
      });
      waitForUpdates(repository);
      ResourceItem item = repository.getResources(RES_AUTO, ResourceType.STYLE, "GreyTheme").get(0);
      assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
      return item;
    });
    // Before the fix, item.getResourceValue would return null since the file is not invalid after getting out of dumb mode.
    assertThat(resourceItem.getResourceValue()).isNotNull();
  }

  @Test
  public void addingPlusToId() throws Exception {
    PsiFile layout = myFixture.addFileToProject("res/layout/my_layout.xml",
                                              // language=XML
                                              "<LinearLayout xmlns:android='http://schemas.android.com/apk/res/android'>" +
                                              "  <TextView android:id='@id/aaa' />" +
                                              "  <TextView android:id='@id/bbb' />" +
                                              "  <TextView android:id='@id/ccc' />" +
                                              "</LinearLayout>");
    myFixture.openFileInEditor(layout.getVirtualFile());

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "aaa")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "bbb")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "ccc")).isFalse();
    long generation = repository.getModificationCount();
    int rescans = repository.getFileRescans();

    type("@|id/aaa", "+");
    waitForUpdates(repository);

    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "aaa")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "bbb")).isFalse();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "ccc")).isFalse();
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    type("@|id/bbb", "+");
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "aaa")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "bbb")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "ccc")).isFalse();
    assertThat(repository.getModificationCount()).isGreaterThan(generation);
    assertThat(repository.getFileRescans()).isEqualTo(rescans); // The change does not need a rescan.

    // Now try setAttribute which triggers a different PsiEvent, similar to pasting.
    generation = repository.getModificationCount();
    rescans = repository.getFileRescans();
    XmlTag cccTag = findTagById(layout, "ccc");
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      cccTag.setAttribute(ATTR_ID, ANDROID_URI, "@+id/ccc");
    });

    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "aaa")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "bbb")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.ID, "ccc")).isTrue();
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
  @Test
  public void bitmapUpdated() throws Exception {
    VirtualFile logoFile = myFixture.copyFileToProject(DRAWABLE_RED, "res/drawable/logo.png");
    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo")).isTrue();
    DrawableRenderer renderer = new DrawableRenderer(myFacet, logoFile);

    String bitmapXml = "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                       "    <background android:drawable=\"@drawable/logo\"/>\n" +
                       "    <foreground android:drawable=\"@drawable/logo\"/>\n" +
                       "</adaptive-icon>";
    int red = renderer.renderDrawable(bitmapXml, COLORED_DRAWABLE_SIZE).join().getRGB(0, 0);

    // We don't check the alpha byte because its value is not FF as expected but
    // that is not significant for this test.
    assertThat(Integer.toHexString(red).substring(2)).isEqualTo("ff0000");

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
  @Test
  public void repositoryUpdatedAfterDumbMode() throws Exception {
    ResourceFolderRepository repository = createRegisteredRepository();
    VirtualFile dir = myFixture.copyFileToProject(DRAWABLE, "res/drawable/image.png").getParent();
    VirtualFile file = VfsUtil.findFileByIoFile(new File(myFixture.getTestDataPath(), DRAWABLE), true);

    // Trigger dumb mode to clear the PsiDirectory cache.
    DumbModeTestUtils.runInDumbModeSynchronously(myModule.getProject(), () -> {});
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
    assertThat(repository.getResources(RES_AUTO, ResourceType.DRAWABLE).size()).isEqualTo(2);
  }

  /**
   * Simulates a common case when git is used in background resulting in a VirtualFile with no Document being modified.
   */
  @Test
  public void fileWithNoDocument() throws Exception {
    VirtualFile valuesXmlVirtualFile = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    ResourceFolderRepository repository = createRegisteredRepository();

    File valuesXmlFile = virtualToIoFile(valuesXmlVirtualFile);
    FileUtil.writeToFile(valuesXmlFile, "<resources><string name='from_git'>git</string></resources>");
    LocalFileSystem.getInstance().refresh(false);
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "from_git")).isTrue();
  }

  /**
   * Simulates what project templates do, modififying Documents that have no PSI (yet). Since PSI can
   * be garbage-collected at any point, this is possible in other cases as well.
   */
  @Test
  public void fileWithNoPsi() throws Exception {
    VirtualFile valuesXml = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    ResourceFolderRepository repository = createRegisteredRepository();

    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    Document document = fileDocumentManager.getDocument(valuesXml);

    // Sanity check:
    assertThat(PsiDocumentManager.getInstance(myProject).getCachedPsiFile(document)).named("Cached PSI").isNull();

    WriteAction.run(() -> {
      document.setText("<resources><string name='from_templates'>git</string></resources>");
      fileDocumentManager.saveDocument(document);

      // in production the document will be committed asynchroniously by DocumentCommitThread
      PsiDocumentManager.getInstance(myProject).commitDocument(document);
    });
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "from_templates")).isTrue();
  }

  @Test
  public void unsavedDocument_noCache() throws Exception {
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
    boolean b1 = fileDocumentManager.isFileModified(stringsXml);
    assertWithMessage("Unsaved changes in Document").that(b1).isTrue();
    ResourceFolderRepository repository = createRepository(true);
    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "inDocument")).isTrue();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.STRING, "onDisk")).isFalse();
    // Make sure we write the cache.
    assertThat(repository.hasFreshFileCache()).isFalse();
    boolean b = fileDocumentManager.isFileModified(stringsXml);
    assertWithMessage("Unsaved changes in Document").that(b).isTrue();
  }

  @Test
  public void unsavedDocument_cache() throws Exception {
    myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    VirtualFile resourceDirectory = getResourceDirectory();
    VirtualFile stringsXml = VfsTestUtil.createFile(resourceDirectory,
                                                    "values/strings.xml",
                                                    // language=XML
                                                    "<resources>" +
                                                    "  <string name='onDisk'>foo bar</string>" +
                                                    "</resources>");
    ResourceFolderRepository repository = createRepository(true);
    // Make sure we write the cache.
    assertThat(repository.hasFreshFileCache()).isFalse();

    myFixture.openFileInEditor(stringsXml);
    moveCaret(myFixture, "name='|onDisk'");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_END);
    myFixture.type("inDocument");

    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    boolean b1 = fileDocumentManager.isFileModified(stringsXml);
    assertWithMessage("Unsaved changes in Document").that(b1).isTrue();

    ResourceFolderRepository repository2 = createRepository(true);
    waitForUpdates(repository2);
    assertThat(repository2.hasResources(RES_AUTO, ResourceType.STRING, "inDocument")).isTrue();
    assertThat(repository2.hasResources(RES_AUTO, ResourceType.STRING, "onDisk")).isFalse();
    boolean b = fileDocumentManager.isFileModified(stringsXml);
    assertWithMessage("Unsaved changes in Document").that(b).isTrue();
  }

  @Test
  public void idDeclarationInNonIdAttribute() throws Exception {
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
  @Test
  public void duplicateAndroidIdLine() throws Exception {
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

  @Test
  public void duplicatePlusIdLine() throws Exception {
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

  @Test
  public void duplicatePlusIdLineNotConverted() throws Exception {
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
    PsiDocumentManager.getInstance(myProject).commitDocument(myFixture.getEditor().getDocument());
    commitAndWaitForUpdates(repository);
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");
    assertThat(Iterables.getOnlyElement(repository.getResources(RES_AUTO, ResourceType.ID, "b"))).isInstanceOf(PsiResourceItem.class);
    assertThat(repository.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
  }

  @Test
  public void addUnrelatedAttribute() throws Exception {
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

  @Test
  public void fontChanged() throws Exception {
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

  @Test
  public void invalidFilenames() throws Exception {
    // Semaphore to use as a barrier to ensure the expected number of updates happen before continuing.
    Semaphore wolfUpdated = new Semaphore(0);
    MockWolfTheProblemSolver wolfTheProblemSolver = new MockWolfTheProblemSolver() {
      final Set<VirtualFile> problemFiles = Sets.newConcurrentHashSet();

      @Override
      public void reportProblemsFromExternalSource(@NotNull VirtualFile file, @NotNull Object source) {
        ThreadingAssertions.assertBackgroundThread();
        problemFiles.add(file);
        wolfUpdated.release();
      }

      @Override
      public void clearProblemsFromExternalSource(@NotNull VirtualFile file, @NotNull Object source) {
        ThreadingAssertions.assertBackgroundThread();
        problemFiles.remove(file);
        wolfUpdated.release();
      }

      @Override
      public boolean isProblemFile(@NotNull VirtualFile virtualFile) {
        return problemFiles.contains(virtualFile);
      }
    };
    ServiceContainerUtil.registerOrReplaceServiceInstance(myProject, WolfTheProblemSolver.class, wolfTheProblemSolver,
                                                          myFixture.getTestRootDisposable());

    VirtualFile projectDir = getProjectDir();
    VirtualFile valid = VfsTestUtil.createFile(projectDir, "res/drawable/valid.png", new byte[] { 1 });
    VirtualFile invalidButIdentifier = VfsTestUtil.createFile(projectDir, "res/drawable/FooBar.png", new byte[] { 1 });
    VirtualFile invalid = VfsTestUtil.createFile(projectDir, "res/drawable/1st.png", new byte[] { 1 });
    ResourceFolderRepository repository = createRegisteredRepository();
    // Wait for the two initial updates
    wolfUpdated.acquire(2);

    assertThat(repository.getResourceNames(RES_AUTO, ResourceType.DRAWABLE)).containsExactly("valid", "FooBar");
    assertThat(wolfTheProblemSolver.isProblemFile(valid)).isFalse();
    assertThat(wolfTheProblemSolver.isProblemFile(invalid)).isTrue();
    assertThat(wolfTheProblemSolver.isProblemFile(invalidButIdentifier)).isTrue();

    WriteAction.run(() -> {
      invalid.rename(this, "fixed.png");
      invalidButIdentifier.rename(this, "also_fixed.png");
    });
    commitAndWaitForUpdates(repository);
    // Wait for the two initial updates to be cleared
    wolfUpdated.acquire(2);

    assertThat(repository.getResourceNames(RES_AUTO, ResourceType.DRAWABLE)).containsExactly("valid", "fixed", "also_fixed");
    assertThat(wolfTheProblemSolver.isProblemFile(valid)).isFalse();
    assertThat(wolfTheProblemSolver.isProblemFile(invalid)).isFalse();
    assertThat(wolfTheProblemSolver.isProblemFile(invalidButIdentifier)).isFalse();
  }

  @Test
  public void getFolderConfigurations() throws Exception {
    VirtualFile stringsFile = myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    assertThat(PsiManager.getInstance(myProject).findFile(stringsFile)).isNotNull();

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    assertThat(repository.getFolderConfigurations(ResourceType.STRING)).containsExactly(new FolderConfiguration());
    assertThat(repository.getFolderConfigurations(ResourceType.LAYOUT)).isEmpty();

    // Add three new string files with locales, but only the non-empty files should be returned below.
    VirtualFile stringsDeFile = myFixture.copyFileToProject(STRINGS, "res/values-de/strings.xml");
    VirtualFile stringsFrFile = myFixture.copyFileToProject(STRINGS, "res/values-fr/strings.xml");
    VirtualFile stringsEsFile = myFixture.copyFileToProject(VALUES_EMPTY, "res/values-es/strings.xml");
    assertThat(PsiManager.getInstance(myProject).findFile(stringsDeFile)).isNotNull();
    assertThat(PsiManager.getInstance(myProject).findFile(stringsFrFile)).isNotNull();
    assertThat(PsiManager.getInstance(myProject).findFile(stringsEsFile)).isNotNull();
    commitAndWaitForUpdates(repository);

    assertThat(repository.getFolderConfigurations(ResourceType.STRING)).containsExactly(
      new FolderConfiguration(),
      FolderConfiguration.getConfig(new String[] { "values", "de" }),
      FolderConfiguration.getConfig(new String[] { "values", "fr" })
    );
    assertThat(repository.getFolderConfigurations(ResourceType.LAYOUT)).isEmpty();

    // Add two layout files. This should affect the folders returned for layouts, but not strings.
    VirtualFile layoutFile = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    VirtualFile layoutFileFr = myFixture.copyFileToProject(LAYOUT1, "res/layout-fr/layout1.xml");
    assertThat(PsiManager.getInstance(myProject).findFile(layoutFile)).isNotNull();
    assertThat(PsiManager.getInstance(myProject).findFile(layoutFileFr)).isNotNull();
    commitAndWaitForUpdates(repository);

    assertThat(repository.getFolderConfigurations(ResourceType.STRING)).containsExactly(
      new FolderConfiguration(),
      FolderConfiguration.getConfig(new String[] { "values", "de" }),
      FolderConfiguration.getConfig(new String[] { "values", "fr" })
    );
    assertThat(repository.getFolderConfigurations(ResourceType.LAYOUT)).containsExactly(
      new FolderConfiguration(),
      FolderConfiguration.getConfig(new String[] { "values", "fr" })
    );
  }

  @Test
  public void reparseLayoutFile() throws Exception {
    VirtualFile file = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");

    ResourceFolderRepository repository = createRegisteredRepository();
    assertThat(repository).isNotNull();

    long generation = repository.getModificationCount();
    assertThat(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1")).isTrue();

    PsiDocumentManager.getInstance(myProject).reparseFiles(List.of(file), true);

    waitForUpdates(repository);
    assertThat(repository.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1")).isTrue();
    assertThat(repository.getModificationCount()).isEqualTo(generation);
  }
}
