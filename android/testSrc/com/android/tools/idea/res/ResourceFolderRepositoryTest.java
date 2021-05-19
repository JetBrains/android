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
import static com.android.tools.idea.res.ResourceAsserts.assertThat;
import static com.android.tools.idea.testing.AndroidTestUtils.moveCaret;
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
import com.google.common.collect.Sets;
import com.intellij.codeInsight.problems.MockWolfTheProblemSolver;
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
import com.intellij.util.WaitFor;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.ui.UIUtil;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
  private static final String XLIFF = "resourceRepository/xliff.xml";
  private static final String STRINGS = "resourceRepository/strings.xml";
  private static final String DRAWABLE = "resourceRepository/logo.png";
  private static final String DRAWABLE_BLUE = "resourceRepository/blue.png";
  private static final String DRAWABLE_RED = "resourceRepository/red.png";
  private static final Dimension COLORED_DRAWABLE_SIZE = new Dimension(3, 3);
  private static final String DRAWABLE_ID_SCAN = "resourceRepository/drawable_for_id_scan.xml";
  private static final String COLOR_STATELIST = "resourceRepository/statelist.xml";
  private static final String MOTION_SCENE = "resourceRepository/motion_scene.xml";

  private ResourceFolderRegistry myRegistry;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    // Use a file cache that has per-test root directories instead of sharing the system directory.
    ResourceFolderRepositoryFileCache cache = new ResourceFolderRepositoryFileCacheImpl(Paths.get(myFixture.getTempDirPath())) {
      @Override
      @Nullable
      public ResourceFolderRepositoryCachingData getCachingData(@NotNull Project project,
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

  private static void resetCounters() {
    ResourceFolderRepository.ourFullRescans = 0;
    ResourceFolderRepository.ourLayoutlibCacheFlushes = 0;
  }

  private static void ensureIncremental() {
    assertEquals(0, ResourceFolderRepository.ourFullRescans);
  }

  private static void ensureSingleScan() {
    assertEquals(1, ResourceFolderRepository.ourFullRescans);
  }

  private static void ensureLayoutlibCachesFlushed() {
    assertThat(ResourceFolderRepository.ourLayoutlibCacheFlushes).named("times layoutlib caches flushed").isGreaterThan(0);
  }

  @NotNull
  private VirtualFile getResourceDirectory() {
    List<VirtualFile> resourceDirectories = ResourceFolderManager.getInstance(myFacet).getFolders();
    assertNotNull(resourceDirectories);
    assertSize(1, resourceDirectories);
    return resourceDirectories.get(0);
  }

  @NotNull
  private ResourceFolderRepository createRepository(boolean createCache) {
    VirtualFile dir = getResourceDirectory();
    ResourceNamespace namespace = ResourceRepositoryManager.getInstance(myFacet).getNamespace();
    ResourceFolderRepositoryCachingData cachingData =
        ResourceFolderRepositoryFileCacheService.get().getCachingData(getProject(), dir, createCache ? directExecutor() : null);
    return ResourceFolderRepository.create(myFacet, dir, namespace, cachingData);
  }

  @NotNull
  private ResourceFolderRepository createRegisteredRepository() {
    VirtualFile dir = getResourceDirectory();
    return myRegistry.get(myFacet, dir);
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

  public void testComputeResourceStrings() {
    // Tests the handling of markup to raw strings
    // For example, for this strings definition
    //   <string name="title_template_step">Step <xliff:g id="step_number">%1$d</xliff:g>: Lorem Ipsum</string>
    // the resource value should be
    //   Step %1$d: Lorem Ipsum
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    List<ResourceItem> labelList = resources.getResources(RES_AUTO, ResourceType.STRING, "title_template_step");
    assertNotNull(labelList);
    assertEquals(1, labelList.size());
    ResourceItem label = labelList.get(0);
    ResourceValue resourceValue = label.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Step ${step_number}: Lorem Ipsum", resourceValue.getValue()); // In the file, there's whitespace unlike example above

    // Test unicode escape handling: <string name="ellipsis">Here it is: \u2026!</string>
    labelList = resources.getResources(RES_AUTO, ResourceType.STRING, "ellipsis");
    assertNotNull(labelList);
    assertEquals(1, labelList.size());
    label = labelList.get(0);
    resourceValue = label.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Here it is: \u2026!", resourceValue.getValue());

    // Make sure we pick up id's defined using types
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "action_next2"));
  }

  /** Tests handling of xliff markup. */
  public void testXliff() {
    VirtualFile file1 = myFixture.copyFileToProject(XLIFF, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertEquals("Share your score of (1337) with (Bluetooth)!",
                 resources.getResources(RES_AUTO, ResourceType.STRING, "share_with_application").get(0).getResourceValue().getValue());
    assertEquals("Call ${name}",
                 resources.getResources(RES_AUTO, ResourceType.STRING, "description_call").get(0).getResourceValue().getValue());
    assertEquals("(42) mins (28) secs",
                 resources.getResources(RES_AUTO, ResourceType.STRING, "callDetailsDurationFormat").get(0).getResourceValue().getValue());
    assertEquals("${number_of_sessions} sessions removed from your schedule",
                 resources.getResources(RES_AUTO, ResourceType.STRING, "other").get(0).getResourceValue().getValue());
  }

  public void testInitialCreate() {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");

    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    Collection<String> layouts = resources.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(2, layouts.size());

    assertNotNull(resources.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));
    assertNotNull(resources.getResources(RES_AUTO, ResourceType.LAYOUT, "layout2"));
  }

  public void testAddFile() {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    Collection<String> layouts = resources.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(2, layouts.size());
    assertNotNull(resources.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));
    assertNotNull(resources.getResources(RES_AUTO, ResourceType.LAYOUT, "layout2"));

    long generation = resources.getModificationCount();
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");
    assertEquals(generation, resources.getModificationCount()); // no changes in file: no new generation

    generation = resources.getModificationCount();
    VirtualFile file3 = myFixture.copyFileToProject(LAYOUT1, "res/layout-xlarge-land/layout3.xml");
    PsiFile psiFile3 = PsiManager.getInstance(getProject()).findFile(file3);
    assertNotNull(psiFile3);
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(resources.getModificationCount() > generation);

    layouts = resources.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(3, layouts.size());
  }

  public void testAddUnrelatedFile() {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    Collection<String> layouts = resources.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(2, layouts.size());
    assertNotNull(resources.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));
    assertNotNull(resources.getResources(RES_AUTO, ResourceType.LAYOUT, "layout2"));

    long generation = resources.getModificationCount();
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.unrelated");
    assertEquals(generation, resources.getModificationCount()); // no changes in file: no new generation
    assertEquals(2, layouts.size());

    myFixture.copyFileToProject(LAYOUT1, "src/layout/layout2.xml"); // not a resource folder
    assertEquals(generation, resources.getModificationCount()); // no changes in file: no new generation
    assertEquals(2, layouts.size());
  }

  public void testDeleteResourceFile() throws Exception {
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    Collection<String> drawables = resources.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertEquals(drawables.toString(), 0, drawables.size());
    long generation = resources.getModificationCount();
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/drawable-mdpi/foo.png");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(resources.getModificationCount() > generation);

    // Delete a file and make sure the item is removed from the repository (and modification count bumped).
    drawables = resources.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertEquals(1, drawables.size());
    generation = resources.getModificationCount();
    assertEquals("foo", drawables.iterator().next());
    WriteCommandAction.runWriteCommandAction(null, () -> psiFile1.delete());
    drawables = resources.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertEquals(0, drawables.size());
    assertTrue(resources.getModificationCount() > generation);

    // Try adding and then deleting a drawable file with IDs too.
    generation = resources.getModificationCount();

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
    UIUtil.dispatchAllInvocationEvents();

    PsiFile psiFile2 = PsiManager.getInstance(getProject()).findFile(file2);
    assertNotNull(psiFile2);
    drawables = resources.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertEquals(1, drawables.size());
    Collection<String> ids = resources.getResources(RES_AUTO, ResourceType.ID).keySet();
    assertContainsElements(ids, "focused_state", "default_state", "pressed_state");
    assertTrue(resources.getModificationCount() > generation);

    generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> psiFile2.delete());

    drawables = resources.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertEquals(0, drawables.size());
    ids = resources.getResources(RES_AUTO, ResourceType.ID).keySet();
    assertEquals(0, ids.size());
    assertTrue(resources.getModificationCount() > generation);
  }

  public void testDeleteResourceDirectory() {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");
    VirtualFile file3 = myFixture.copyFileToProject(LAYOUT1, "res/layout-xlarge-land/layout3.xml");
    PsiFile psiFile3 = PsiManager.getInstance(getProject()).findFile(file3);
    assertNotNull(psiFile3);

    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    // Try deleting a whole resource directory and ensure we remove the files within.
    long generation = resources.getModificationCount();
    Collection<String> layouts = resources.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(3, layouts.size());
    PsiDirectory directory = psiFile3.getContainingDirectory();
    assertNotNull(directory);
    WriteCommandAction.runWriteCommandAction(null, () -> directory.delete());
    layouts = resources.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(2, layouts.size());
    assertTrue(resources.getModificationCount() > generation);
  }

  public void testDeleteRemainderResourceIDs() {
    VirtualFile file = myFixture.copyFileToProject(LAYOUT_ID_SCAN, "res/layout-xlarge-land/layout.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile1);

    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    long generation = resources.getModificationCount();
    Collection<String> layouts = resources.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(1, layouts.size());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
    // nonExistent may be handled slightly different from other IDs, since it is introduced by a
    // non-ID-attribute="@+id/nonExistent", but the file does not define a tag with id="@id/nonExistent".
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "nonExistent"));

    WriteCommandAction.runWriteCommandAction(null, () -> psiFile1.delete());
    assertTrue(resources.getModificationCount() > generation);
    layouts = resources.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEmpty(layouts);
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "nonExistent"));
  }

  public void testRenameLayoutFile() {
    VirtualFile file2 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");

    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    // Check renames.
    //  Rename layout file.
    long generation = resources.getModificationCount();
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout2"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout2b"));

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
    UIUtil.dispatchAllInvocationEvents();

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout2b"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout2"));
    assertTrue(resources.getModificationCount() > generation);
  }

  public void testRenameLayoutFileToInvalid() {
    VirtualFile file = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");

    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    // Check renames.
    //  Rename layout file.
    long generation = resources.getModificationCount();
    assertThat(resources.getResources(RES_AUTO, ResourceType.LAYOUT).keySet()).containsExactly("layout2");

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

    assertThat(resources.getResources(RES_AUTO, ResourceType.LAYOUT).keySet()).isEmpty();
    assertTrue(resources.getModificationCount() > generation);
  }

  public void testRenameDrawableFile() {
    //  Rename drawable file.
    VirtualFile file5 = myFixture.copyFileToProject(LAYOUT1, "res/drawable-xhdpi/foo2.png");
    ResourceFolderRepository resources = createRegisteredRepository();

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo2"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo3"));
    ResourceItem item = getOnlyItem(resources, ResourceType.DRAWABLE, "foo2");
    assertTrue(item.getResourceValue() instanceof DensityBasedResourceValue);
    DensityBasedResourceValue rv = (DensityBasedResourceValue)item.getResourceValue();
    assertNotNull(rv);
    assertSame(Density.XHIGH, rv.getResourceDensity());

    long generation = resources.getModificationCount();
    resetCounters();

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
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo3"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo2"));
    assertTrue(resources.getModificationCount() > generation);
    ensureLayoutlibCachesFlushed();
  }

  public void testRenameResourceBackedByPsiResourceItem() {
    // We first do a normal rename which will also convert the ResourceItem into a PsiResourceItem
    //  Rename drawable file.
    VirtualFile file5 = myFixture.copyFileToProject(LAYOUT1, "res/drawable-xhdpi/foo2.png");
    ResourceFolderRepository resources = createRegisteredRepository();

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo2"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo3"));
    ResourceItem item = getOnlyItem(resources, ResourceType.DRAWABLE, "foo2");
    assertTrue(item.getResourceValue() instanceof DensityBasedResourceValue);
    DensityBasedResourceValue rv = (DensityBasedResourceValue)item.getResourceValue();
    assertNotNull(rv);
    assertSame(Density.XHIGH, rv.getResourceDensity());

    long generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
          file5.rename(this, "foo3.png");
        }
        catch (IOException e) {
          fail(e.toString());
        }
    });
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo3"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo2"));
    assertTrue(resources.getModificationCount() > generation);

    // At this point the item2 is a PsiResourceItem so we try to rename a second time
    // to check that the new name is propagated to the resources repositories.
    ResourceItem item2 = getOnlyItem(resources, ResourceType.DRAWABLE, "foo3");
    assertInstanceOf(item2, PsiResourceItem.class);
    assertTrue(item2.getResourceValue() instanceof DensityBasedResourceValue);

    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
          file5.rename(this, "foo4.png");
        }
        catch (IOException e) {
          fail(e.toString());
      }
    });
    UIUtil.dispatchAllInvocationEvents();
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo3"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.DRAWABLE, "foo4"));
    assertTrue(resources.getModificationCount() > generation2);
  }

  public void testRenameValueFile() {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step"));

    List<ResourceItem> items = resources.getResources(RES_AUTO, ResourceType.STRING, "title_template_step");
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
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step"));
    items = resources.getResources(RES_AUTO, ResourceType.STRING, "title_template_step");
    assertNotNull(items);
    assertEquals(1, items.size());
    item = items.get(0);
    assertEquals("renamedvalues.xml", item.getSource().getFileName());

    // TODO: Optimize this such that there's no modification change for this. It's tricky because
    //       for file names we get separate notification from the old file deletion (beforePropertyChanged)
    //       and the new file name (propertyChanged). (Note that I tried performing the rename via a
    //       setName operation on the PsiFile instead of at the raw VirtualFile level, but the resulting
    //       events were the same.)
    //assertEquals(generation, resources.getModificationCount());
  }

  public void testRenameValueFileToInvalid() {
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step"));

    long generation = resources.getModificationCount();
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
    assertTrue(generation < resources.getModificationCount());
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step"));
  }

  private static ResourceItem getOnlyItem(@NotNull ResourceFolderRepository repository, @NotNull ResourceType type, @NotNull String name) {
    List<ResourceItem> items = repository.getResources(RES_AUTO, type, name);
    assertEquals(1, items.size());
    return items.get(0);
  }

  public void testMoveFileResourceFileToNewConfiguration() {
    // Move a file-based resource file from one configuration to another; verify that
    // items are preserved, generation changed (since it can affect config matching),
    // and resource files updated.
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout-land/layout1.xml");
    VirtualFile file2 = myFixture.copyFileToProject(LAYOUT1, "res/layout-port/dummy.ignore");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    ResourceItem item = getOnlyItem(resources, ResourceType.LAYOUT, "layout1");
    assertEquals("land", item.getConfiguration().getQualifierString());
    ResourceItem idItem = getOnlyItem(resources, ResourceType.ID, "btn_title_refresh");
    assertEquals("layout-land", idItem.getSource().getParentFileName());

    long generation = resources.getModificationCount();
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
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));
    item = getOnlyItem(resources, ResourceType.LAYOUT, "layout1");
    assertEquals("port", item.getConfiguration().getQualifierString());
    idItem = getOnlyItem(resources, ResourceType.ID, "btn_title_refresh");
    assertEquals("layout-port", idItem.getSource().getParentFileName());
  }

  public void testMoveValueResourceFileToNewConfiguration() {
    // Move a value file from one configuration to another; verify that
    // items are preserved, generation changed (since it can affect config matching),
    // and resource files updated.
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values-en/layout1.xml");
    VirtualFile file2 = myFixture.copyFileToProject(VALUES1, "res/values-no/dummy.ignore");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    ResourceItem item = getOnlyItem(resources, ResourceType.STRING, "app_name");
    assertEquals("en", item.getConfiguration().getQualifierString());
    assertEquals("en", item.getConfiguration().getLocaleQualifier().getLanguage());
    assertEquals("Animations Demo", item.getResourceValue().getValue());

    long generation = resources.getModificationCount();
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
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    item = getOnlyItem(resources, ResourceType.STRING, "app_name");
    assertEquals("no", item.getConfiguration().getQualifierString());
    assertEquals("no", item.getConfiguration().getLocaleQualifier().getLanguage());
    assertEquals("Animations Demo", item.getResourceValue().getValue());
  }

  public void testMoveResourceFileBetweenDensityFolders() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=61648
    // Make sure we flush resource values when reusing resource items incrementally

    // Move a file-based resource file from one configuration to another; verify that
    // items are preserved, generation changed (since it can affect config matching),
    // and resource files updated.
    VirtualFile file1 = myFixture.copyFileToProject(DRAWABLE, "res/drawable-mdpi/picture.png");
    VirtualFile file2 = myFixture.copyFileToProject(DRAWABLE, "res/drawable-hdpi/dummy.ignore");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    ResourceItem item = getOnlyItem(resources, ResourceType.DRAWABLE, "picture");
    assertEquals(Density.MEDIUM, item.getConfiguration().getDensityQualifier().getValue());
    ResourceValue resourceValue = item.getResourceValue();
    assertNotNull(resourceValue);
    String valuePath = resourceValue.getValue().replace(File.separatorChar, '/');
    assertTrue(valuePath, valuePath.endsWith("res/drawable-mdpi/picture.png"));

    long generation = resources.getModificationCount();
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
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.DRAWABLE, "picture"));
    item = getOnlyItem(resources, ResourceType.DRAWABLE, "picture");
    assertEquals(Density.HIGH, item.getConfiguration().getDensityQualifier().getValue());
    resourceValue = item.getResourceValue();
    assertNotNull(resourceValue);
    valuePath = resourceValue.getValue().replace(File.separatorChar, '/');
    assertTrue(valuePath, valuePath.endsWith("res/drawable-hdpi/picture.png"));
  }

  public void testMoveFileResourceFileToNewType() {
    // Move a file resource file file from one folder to another, changing the type
    // (e.g. anim to animator), verify that resource types are updated
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    VirtualFile file2 = myFixture.copyFileToProject(LAYOUT1, "res/menu/dummy.ignore");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));

    long generation = resources.getModificationCount();
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
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.MENU, "layout1"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));
    ResourceItem item = getOnlyItem(resources, ResourceType.MENU, "layout1");
    assertSame(ResourceFolderType.MENU, ((PsiResourceItem)item).getSourceFile().getFolderType());
  }

  public void testMoveOutOfResourceFolder() {
    // Move value files out of its resource folder; items should disappear
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    VirtualFile javaFile = myFixture.copyFileToProject(VALUES1, "src/my/pkg/Dummy.java");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step"));

    long generation = resources.getModificationCount();
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
    assertTrue(generation < resources.getModificationCount());
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step"));
  }

  public void testMoveIntoResourceFolder() {
    // Move value files out of its resource folder; items should disappear
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/dummy.ignore");
    VirtualFile xmlFile = myFixture.copyFileToProject(VALUES1, "src/my/pkg/values.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step"));

    long generation = resources.getModificationCount();
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
    UIUtil.dispatchAllInvocationEvents();

    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "title_template_step"));
  }

  public void testReplaceResourceFile() {
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");

    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "btn_title_refresh"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "btn_title_refresh2"));

    WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
        myFixture.copyFileToProject(LAYOUT2, "res/layout/layout1.xml");
      }
      catch (Exception e) {
        fail(e.toString());
      }
    });

    // TODO: Find out how I can work around this!
    // This doesn't work because copyFileToProject does not trigger PSI file notifications!
    //assertTrue(resources.hasResourceItem(ResourceType.ID, "btn_title_refresh2"));
    //assertFalse(resources.hasResourceItem(ResourceType.ID, "btn_title_refresh"));
    //assertTrue(generation < resources.getModificationCount());
  }

  public void testAddEmptyValueFile() {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    long generation = resources.getModificationCount();
    assertEquals(generation, resources.getModificationCount());
  }

  public void testRawFolder() throws Exception {
    // In this folder, any file extension is allowed
    myFixture.copyFileToProject(LAYOUT1, "res/raw/raw1.xml");
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    Collection<String> raw = resources.getResources(RES_AUTO, ResourceType.RAW).keySet();
    assertEquals(1, raw.size());
    long generation1 = resources.getModificationCount();
    VirtualFile file2 = myFixture.copyFileToProject(LAYOUT1, "res/raw/numbers.random");
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(resources.getModificationCount() > generation1);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.RAW, "numbers"));
    raw = resources.getResources(RES_AUTO, ResourceType.RAW).keySet();
    assertEquals(2, raw.size());

    long generation2 = resources.getModificationCount();
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
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(resources.getModificationCount() > generation2);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.RAW, "numbers2"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.RAW, "numbers"));
  }

  public void testEditLayoutNoOp() {
    resetCounters();

    // Make some miscellaneous edits in the file that have no bearing on the
    // project resources and therefore end up doing no work
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    assert(psiFile1 instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile1;
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    Collection<String> layouts = resources.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(1, layouts.size());
    assertNotNull(resources.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));

    long initial = resources.getModificationCount();
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
    // Inserting the comment and editing it shouldn't have had any observable results on the resource repository
    assertEquals(initial, resources.getModificationCount());

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
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
    // Space edits outside the tag shouldn't be observable
    assertEquals(initial, resources.getModificationCount());

    // Edit text inside an element tag. No effect in value files!
    WriteCommandAction.runWriteCommandAction(null, () -> {
      XmlTag header = findTagById(xmlFile, "header");
      assertNotNull(header);
      int indentAreaBeforeTag = header.getSubTags()[0].getTextOffset();
      document.insertString(indentAreaBeforeTag, "   ");
      documentManager.commitDocument(document);
    });
    // Space edits inside the tag shouldn't be observable
    assertEquals(initial, resources.getModificationCount());

    // Insert tag (without id) in layout file: ignored (only ids and file item matters)
    WriteCommandAction.runWriteCommandAction(null, () -> {
      XmlTag header = findTagById(xmlFile, "text2");
      assertNotNull(header);
      int indentAreaBeforeTag = header.getTextOffset() - 1;
      document.insertString(indentAreaBeforeTag, "<Button />");
      documentManager.commitDocument(document);
    });
    // Non-id new tags shouldn't be observable.
    assertEquals(initial, resources.getModificationCount());

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();

    // Finally make an edit which *does* affect the project resources to ensure
    // that document edits actually *do* fire PSI events that are digested by
    // this repository.
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "newid"));
    String elementDeclaration = "<Button android:id=\"@+id/newid\" />\n";
    WriteCommandAction.runWriteCommandAction(null, () -> {
      XmlTag tag12 = findTagById(psiFile1, "noteArea");
      assertNotNull(tag12);
      document.insertString(tag12.getTextOffset() - 1, elementDeclaration);
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "newid"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "newid2"));
    assertTrue(resources.getModificationCount() > initial);
    resetCounters();

    // Now try another edit, where things should be incremental now.
    long generation = resources.getModificationCount();
    String elementDeclaration2 = "<Button android:id=\"@+id/newid2\" />\n";
    WriteCommandAction.runWriteCommandAction(null, () -> {
      XmlTag tag1 = findTagById(psiFile1, "noteArea");
      assertNotNull(tag1);
      document.insertString(tag1.getTextOffset() - 1, elementDeclaration2);
      documentManager.commitDocument(document);
    });
    assertFalse(resources.isScanPending(psiFile1));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "newid"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "newid2"));
    assertTrue(resources.getModificationCount() > generation);
    ensureIncremental();

    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int startOffset = document.getText().indexOf(elementDeclaration);
      document.deleteString(startOffset, startOffset + elementDeclaration.length());
      documentManager.commitDocument(document);
    });

    assertTrue(resources.isScanPending(psiFile1));
    resetCounters();
    getApplication().invokeLater(() -> {
      ensureSingleScan();
      assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "newid"));
      assertTrue(resources.getModificationCount() > generation2);
    });
    UIUtil.dispatchAllInvocationEvents();
  }

  public void testEditValueFileNoOp() {
    resetCounters();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    Collection<String> strings = resources.getResources(RES_AUTO, ResourceType.STRING).keySet();
    assertEquals(8, strings.size());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.INTEGER, "card_flip_time_full"));

    long initial = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Edit comment header; should be a no-op.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("Licensed under the");
      document.insertString(offset, "This code is ");
      documentManager.commitDocument(document);
    });
    assertEquals(initial, resources.getModificationCount());

    // Test edit text NOT under an item: no-op.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf(" <item type=\"id\""); // insert BEFORE this
      document.insertString(offset, "Ignored text");
      documentManager.commitDocument(document);
    });
    assertEquals(initial, resources.getModificationCount());

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  public void testInsertNewElementWithId() {
    resetCounters();

    // Make some miscellaneous edits in the file that have no bearing on the
    // project resources and therefore end up doing no work
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    assert(psiFile1 instanceof XmlFile);
    XmlFile xmlFile = (XmlFile)psiFile1;
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    Collection<String> layouts = resources.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(1, layouts.size());
    assertNotNull(resources.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));

    long initial = resources.getModificationCount();
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
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(initial < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "newid1"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "newid2"));
    resetCounters();

    // A second update should be incremental.
    long generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      XmlTag header = findTagById(xmlFile, "text2");
      assertNotNull(header);
      int indentAreaBeforeTag = header.getTextOffset() - 1;
      document.insertString(indentAreaBeforeTag,
                            "<LinearLayout android:id=\"@+id/newid3\"><Child android:id=\"@+id/newid4\"/></LinearLayout>");
      documentManager.commitDocument(document);
    });
    assertFalse(resources.isScanPending(psiFile1));
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "newid1"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "newid2"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "newid3"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "newid4"));
  }

  public void testEditIdAttributeValue() {
    resetCounters();
    // Edit the id attribute value of a layout item to change the set of available ids
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    Collection<String> layouts = resources.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(1, layouts.size());
    assertNotNull(resources.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "note2Area"));

    long generation = resources.getModificationCount();
    XmlTag tag = findTagById(psiFile1, "noteArea");
    assertNotNull(tag);
    WriteCommandAction.runWriteCommandAction(null, () -> {
      tag.setAttribute(ATTR_ID, ANDROID_URI, "@+id/note2Area");
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "note2Area"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
    resetCounters();

    // A second update should be incremental.
    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      tag.setAttribute(ATTR_ID, ANDROID_URI, "@+id/note23Area");
    });
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "note23Area"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "note2Area"));
    assertTrue(resources.getModificationCount() > generation2);

    // Check replacing @+id with a normal string.
    long generation3 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      tag.setAttribute(ATTR_ID, ANDROID_URI, "notId");
    });
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "note23Area"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "notId"));
    assertTrue(resources.getModificationCount() > generation3);

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  public void testEditIdAttributeValue2() {
    // Edit the id attribute value: rather than by making a full value replacement,
    // perform a tiny edit on the character content; this takes a different code
    // path in the incremental updater.

    resetCounters();
    // Edit the id attribute value of a layout item to change the set of available ids.
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    Collection<String> layouts = resources.getResources(RES_AUTO, ResourceType.LAYOUT).keySet();
    assertEquals(1, layouts.size());
    assertNotNull(resources.getResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "note2Area"));

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Edit value should cause update
    long generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("noteArea");
      document.insertString(offset + 4, "2");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "note2Area"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
    resetCounters();

    // A second update should be incremental.
    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("note2Area");
      document.insertString(offset + 5, "3");
      documentManager.commitDocument(document);
    });

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "note23Area"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "note2Area"));
    assertTrue(resources.getModificationCount() > generation2);

    // Also check that for IDs the ResourceValue is nothing of consequence.
    ResourceItem idItem = getOnlyItem(resources, ResourceType.ID, "note23Area");
    ResourceValue idValue = idItem.getResourceValue();
    assertNotNull(idValue);
    assertEquals("", idValue.getValue());

    // Check replacing @+id with a normal string.
    long generation3 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String attrValue = "@+id/note23Area";
      int offset = document.getText().indexOf(attrValue);
      document.replaceString(offset, offset + attrValue.length(), "notId");
      documentManager.commitDocument(document);
    });
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "note23Area"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "notId"));
    assertTrue(resources.getModificationCount() > generation3);

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }


  public void testEditIdFromDrawable() {
    resetCounters();

    // Mix PNGs and XML in the same directory.
    myFixture.copyFileToProject(DRAWABLE, "res/drawable-v21/logo.png");
    VirtualFile file1 = myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/drawable-v21/drawable_with_ids.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    Collection<String> ids = resources.getResources(RES_AUTO, ResourceType.ID).keySet();
    assertNotNull(ids);
    assertContainsElements(ids, "focused_state", "default_state", "pressed_state");
    Collection<String> drawables = resources.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertContainsElements(drawables, "logo", "drawable_with_ids");

    // Now test an ID edit, to make sure that gets picked up too incrementally, just like layouts.
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Edit value should cause update
    long generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("focused_state");
      document.replaceString(offset, offset + 1, "l");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    resetCounters();

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "locused_state"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "focused_state"));
    drawables = resources.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertContainsElements(drawables, "logo", "drawable_with_ids");

    // Now try another edit, where things should be incremental now.
    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("default_state");
      document.insertString(offset, "dd");
      documentManager.commitDocument(document);
    });
    assertFalse(resources.isScanPending(psiFile1));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "dddefault_state"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "default_state"));
    drawables = resources.getResources(RES_AUTO, ResourceType.DRAWABLE).keySet();
    assertContainsElements(drawables, "logo", "drawable_with_ids");

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  /**
   * We expect this change to update the counter (so the layout editor notices the change but it shouldn't
   * change any resources since it does not add or remove ids.
   */
  public void testEditNonIdFromDrawable() {
    resetCounters();

    VirtualFile file = myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/drawable-v21/drawable_with_ids.xml");
    PsiFile psiFiles = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFiles);

    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    // Now test an ID edit, to make sure that gets picked up too incrementally, just like layouts.
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFiles);
    assertNotNull(document);

    // Edit attribute value
    long generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("drawableP");
      int lineNumber = document.getLineNumber(offset);
      document.insertString(offset, "2");
      documentManager.commitDocument(document);
    });

    // The change does not need a rescan
    assertFalse(resources.isScanPending(psiFiles));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
  }

  /**
   * We expect this change to update the counter (so the layout editor notices the change but it shouldn't
   * change any resources since it does not add or remove ids.
   */
  public void testEditNonIdGeneratingXml() {
    resetCounters();

    VirtualFile file = myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/xml/xml_file.xml");
    PsiFile psiFiles = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFiles);

    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    // Now test an ID edit, to make sure that gets picked up too incrementally, just like layouts.
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFiles);
    assertNotNull(document);

    // Edit attribute value
    long generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("drawableP");
      int lineNumber = document.getLineNumber(offset);
      document.insertString(offset, "2");
      documentManager.commitDocument(document);
    });

    // The change does not need a rescan
    assertFalse(resources.isScanPending(psiFiles));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
  }

  public void testMotionScene() {
    resetCounters();

    VirtualFile virtualFile = myFixture.copyFileToProject(MOTION_SCENE, "res/xml/motion_scene.xml");
    XmlFile file = (XmlFile)PsiManager.getInstance(getProject()).findFile(virtualFile);
    assertNotNull(file);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    XmlTag motionScene = file.getRootTag();
    XmlTag constraintSet = motionScene.findFirstSubTag(CONSTRAINT_SET);
    XmlTag constraint = constraintSet.findFirstSubTag(CONSTRAINT);

    // Change the attribute value of a tag in the motion scene
    long generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      constraint.setAttribute("rotationX", ANDROID_URI, "95");
    });

    // The change does not need a rescan
    UIUtil.dispatchAllInvocationEvents();
    assertFalse(resources.isScanPending(file));
    assertTrue(generation != resources.getModificationCount());
  }

  public void testEditValueText() {
    resetCounters();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    Collection<String> strings = resources.getResources(RES_AUTO, ResourceType.STRING).keySet();
    assertEquals(8, strings.size());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.INTEGER, "card_flip_time_full"));

    long generation = resources.getModificationCount();

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
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    resetCounters();

    // Now try another edit, where things should be incremental now.
    int screeenSlideOffset = document.getText().indexOf("Screeen Slide");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(screeenSlideOffset + 3, screeenSlideOffset + 3, "e");
      documentManager.commitDocument(document);
    });

    long generation2 = resources.getModificationCount();
    // No revision bump yet, because the resource value hasn't been observed!
    assertEquals(generation2, resources.getModificationCount());

    // Now observe it, do another edit, and see what happens
    List<ResourceItem> labelList = resources.getResources(RES_AUTO, ResourceType.STRING, "title_screen_slide");
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
    assertTrue(generation2 < resources.getModificationCount());
    resourceValue = slideLabel.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Scrn Slide", resourceValue.getValue());

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  public void testNestedEditValueText() {
    resetCounters();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    List<ResourceItem> labelList = resources.getResources(RES_AUTO, ResourceType.STRING, "title_template_step");
    assertNotNull(labelList);
    assertEquals(1, labelList.size());
    ResourceItem label = labelList.get(0);
    ResourceValue resourceValue = label.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Step ${step_number}: Lorem Ipsum", resourceValue.getValue());

    long generation = resources.getModificationCount();
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
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    // Because of the file -> psi transition, we can't rely on the old ResourceItem references (replaced).
    // We need to ensure that callers don't hang on to the old ResourceItem, assuming it'll get
    // updated in place after an edit.
    labelList = resources.getResources(RES_AUTO, ResourceType.STRING, "title_template_step");
    assertNotNull(labelList);
    assertEquals(1, labelList.size());
    label = labelList.get(0);
    resourceValue = label.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Step ${step_number}: Llorem Ipsum", resourceValue.getValue());
    resetCounters();

    // Try a second edit
    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int textOffset2 = document.getText().indexOf("Llorem");
      document.insertString(textOffset + 1, "l");
      documentManager.commitDocument(document);
    });

    assertTrue(generation2 < resources.getModificationCount());
    List<ResourceItem> labelList2 = resources.getResources(RES_AUTO, ResourceType.STRING, "title_template_step");
    assertNotNull(labelList2);
    assertEquals(1, labelList2.size());
    ResourceItem label2 = labelList2.get(0);
    resourceValue = label2.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Step ${step_number}: Lllorem Ipsum", resourceValue.getValue());

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  public void testEditValueName() {
    resetCounters();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    Collection<String> strings = resources.getResources(RES_AUTO, ResourceType.STRING).keySet();
    assertEquals(8, strings.size());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));

    long generation = resources.getModificationCount();

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    int offset = document.getText().indexOf("app_name");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(offset, offset + 3, "tap");
      documentManager.commitDocument(document);
    });

    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "tap_name"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    resetCounters();

    // However, the second edit can then be incremental.
    long generation2 = resources.getModificationCount();
    int offset2 = document.getText().indexOf("tap_name");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(offset2, offset2 + 3, "rap");
      documentManager.commitDocument(document);
    });
    assertTrue(generation2 < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "rap_name"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STRING, "tap_name"));

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  public void testAddValue() {
    resetCounters();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    Collection<String> strings = resources.getResources(RES_AUTO, ResourceType.STRING).keySet();
    assertEquals(8, strings.size());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));

    // Incrementally add in a new item
    long generation = resources.getModificationCount();
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
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      ensureSingleScan();
      assertTrue(generation < resources.getModificationCount());
      assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "new_string"));
      assertEquals("New String", resources.getResources(RES_AUTO, ResourceType.STRING, "new_string").get(0).getResourceValue().getValue());
    });
  }

  public void testRemoveValue() {
    resetCounters();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    Collection<String> strings = resources.getResources(RES_AUTO, ResourceType.STRING).keySet();
    assertEquals(8, strings.size());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));

    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String textToRemove = "<string name=\"app_name\">Animations Demo</string>";
      int offset = document.getText().indexOf(textToRemove);
      document.deleteString(offset, offset + textToRemove.length());
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "title_zoom"));
    resetCounters();

    // Now try another edit that is also a delete, where things should be incremental now.
    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String textToRemove2 = "<string name=\"title_zoom\">Zoom</string>";
      int offset = document.getText().indexOf(textToRemove2);
      document.deleteString(offset, offset + textToRemove2.length());
      documentManager.commitDocument(document);
    });
    assertTrue(generation2 < resources.getModificationCount());
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STRING, "title_zoom"));

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  public void testAddIdValue() {
    resetCounters();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "action_prev"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_flip"));

    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item type=\"id\" name=\"action_next\" />");
      document.insertString(offset, "<item type=\"id\" name=\"action_prev\" />");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_prev"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_flip"));
    resetCounters();

    // Try a second edit.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item type=\"id\" name=\"action_next\" />");
      document.insertString(offset, "<item type=\"id\" name=\"action_mid\" />");
      documentManager.commitDocument(document);
    });
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_prev"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_mid"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_flip"));
    ensureIncremental();
  }

  public void testRemoveIdValue() {
    resetCounters();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_flip"));

    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = "<item type=\"id\" name=\"action_next\" />";
      int offset = document.getText().indexOf(item);
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_flip"));
    resetCounters();

    // Try a second edit.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = "<item type=\"id\" name=\"action_flip\" />";
      int offset = document.getText().indexOf(item);
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });

    assertTrue(generation < resources.getModificationCount());
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "action_flip"));
    ensureIncremental();
  }

  public void testChangeType() {
    resetCounters();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.DIMEN, "action_next"));

    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    int offset = document.getText().indexOf("\"id\" name=\"action_next\" />") + 1;
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(offset, offset + 2, "dimen");
      documentManager.commitDocument(document);
    });
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      ensureSingleScan();
      assertTrue(generation < resources.getModificationCount());
      assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
      assertTrue(resources.hasResources(RES_AUTO, ResourceType.DIMEN, "action_next"));
    });
  }

  public void testBreakNameAttribute() {
    resetCounters();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));

    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    int offset = document.getText().indexOf("name=\"app_name\">");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(offset + 2, offset + 3, "o"); // name => nome
      documentManager.commitDocument(document);
    });

    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      ensureSingleScan();
      assertTrue(generation < resources.getModificationCount());
      assertFalse(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    });
  }

  public void testChangeValueTypeByTagNameEdit() {
    resetCounters();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.INTEGER, "card_flip_time_half"));

    long generation = resources.getModificationCount();
    XmlTag tag = findTagByName(psiFile1, "card_flip_time_half");
    assertNotNull(tag);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      tag.setName("dimen"); // Change <integer> to <dimen>
    });
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      ensureSingleScan();

      assertTrue(generation < resources.getModificationCount());
      assertTrue(resources.hasResources(RES_AUTO, ResourceType.DIMEN, "card_flip_time_half"));
      assertFalse(resources.hasResources(RES_AUTO, ResourceType.INTEGER, "card_flip_time_half"));
    });
  }

  public void testEditStyleName() {
    resetCounters();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));

    // Change style name
    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("DarkTheme");
      document.replaceString(offset, offset + 4, "Grey");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "GreyTheme"));
    resetCounters();

    // Now try another edit where things should be incremental now.
    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("GreyTheme");
      document.replaceString(offset, offset + 4, "Light");
      documentManager.commitDocument(document);
    });

    assertTrue(generation2 < resources.getModificationCount());
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "LightTheme"));

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  public void testEditStyleParent() {
    resetCounters();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));

    // Change style parent
    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // First edit won't be incremental (file -> Psi).
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("android:Theme.Holo");
      document.replaceString(offset, offset + "android:Theme.Holo".length(), "android:Theme.Light");
      documentManager.commitDocument(document);
    });

    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      ensureSingleScan();
      assertTrue(generation < resources.getModificationCount());
      ResourceItem style = getOnlyItem(resources, ResourceType.STYLE, "DarkTheme");
      ResourceValue resourceValue = style.getResourceValue();
      assertNotNull(resourceValue);
      assertTrue(resourceValue instanceof StyleResourceValue);
      StyleResourceValue srv = (StyleResourceValue)resourceValue;
      assertEquals("android:Theme.Light", srv.getParentStyleName());
      ResourceValue actionBarStyle = srv.getItem(ANDROID, "actionBarStyle");
      assertNotNull(actionBarStyle);
      assertEquals("@style/DarkActionBar", actionBarStyle.getValue());
      resetCounters();
    });

    // Even on the second edit we don't expect editing the style parent to be incremental.
    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("android:Theme.Light");
      document.replaceString(offset, offset + "android:Theme.Light".length(), "android:Theme.Material");
      documentManager.commitDocument(document);
    });

    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      ensureSingleScan();
      assertTrue(generation2 < resources.getModificationCount());
      ResourceItem style = getOnlyItem(resources, ResourceType.STYLE, "DarkTheme");
      ResourceValue resourceValue = style.getResourceValue();
      assertNotNull(resourceValue);
      assertTrue(resourceValue instanceof StyleResourceValue);
      StyleResourceValue srv = (StyleResourceValue)resourceValue;
      assertEquals("android:Theme.Material", srv.getParentStyleName());
      ResourceValue actionBarStyle = srv.getItem(ANDROID, "actionBarStyle");
      assertNotNull(actionBarStyle);
      assertEquals("@style/DarkActionBar", actionBarStyle.getValue());
    });
  }

  public void testEditStyleItemText() {
    resetCounters();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));
    ResourceItem style = getOnlyItem(resources, ResourceType.STYLE, "DarkTheme");
    StyleResourceValue srv = (StyleResourceValue)style.getResourceValue();
    assertNotNull(srv);
    ResourceValue actionBarStyle = srv.getItem(ANDROID, "actionBarStyle");
    assertNotNull(actionBarStyle);
    assertEquals("@style/DarkActionBar", actionBarStyle.getValue());

    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("@style/DarkActionBar");
      document.replaceString(offset + 7, offset + 11, "Grey");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));
    style = getOnlyItem(resources, ResourceType.STYLE, "DarkTheme");
    srv = (StyleResourceValue)style.getResourceValue();
    assertNotNull(srv);
    actionBarStyle = srv.getItem(ANDROID, "actionBarStyle");
    assertNotNull(actionBarStyle);
    assertEquals("@style/GreyActionBar", actionBarStyle.getValue());
    resetCounters();

    // Now try another edit where things should be incremental now.
    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("@style/GreyActionBar");
      document.replaceString(offset + 7, offset + 11, "Light");
      documentManager.commitDocument(document);
    });
    assertTrue(generation2 < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));

    style = getOnlyItem(resources, ResourceType.STYLE, "DarkTheme");
    srv = (StyleResourceValue)style.getResourceValue();
    assertNotNull(srv);
    actionBarStyle = srv.getItem(ANDROID, "actionBarStyle");
    assertNotNull(actionBarStyle);
    assertEquals("@style/LightActionBar", actionBarStyle.getValue());

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  public void testEditStyleItemName() {
    resetCounters();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));
    ResourceItem style = getOnlyItem(resources, ResourceType.STYLE, "DarkTheme");
    StyleResourceValue srv = (StyleResourceValue)style.getResourceValue();
    assertNotNull(srv);
    ResourceValue actionBarStyle = srv.getItem(ANDROID, "actionBarStyle");
    assertNotNull(actionBarStyle);
    assertEquals("@style/DarkActionBar", actionBarStyle.getValue());

    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("android:actionBarStyle");
      document.insertString(offset + 8, "n");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));
    style = getOnlyItem(resources, ResourceType.STYLE, "DarkTheme");
    srv = (StyleResourceValue)style.getResourceValue();
    assertNotNull(srv);
    actionBarStyle = srv.getItem(ANDROID, "nactionBarStyle");
    assertNotNull(actionBarStyle);
    assertEquals("@style/DarkActionBar", actionBarStyle.getValue());
    resetCounters();

    // Now try another edit, where things should be incremental now.
    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("android:nactionBarStyle");
      document.insertString(offset + 8, "i");
      documentManager.commitDocument(document);
    });

    assertTrue(generation2 < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));
    style = getOnlyItem(resources, ResourceType.STYLE, "DarkTheme");
    srv = (StyleResourceValue)style.getResourceValue();
    assertNotNull(srv);
    actionBarStyle = srv.getItem(ANDROID, "inactionBarStyle");
    assertNotNull(actionBarStyle);
    assertEquals("@style/DarkActionBar", actionBarStyle.getValue());

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  public void testAddStyleItem() {
    resetCounters();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar"));
    ResourceItem style = getOnlyItem(resources, ResourceType.STYLE, "DarkActionBar");
    StyleResourceValue srv = (StyleResourceValue)style.getResourceValue();
    assertNotNull(srv);
    checkDefinedItems(srv, "android:background", "android:textColor");
    ResourceValue background = srv.getItem(ANDROID, "background");
    assertNotNull(background);
    assertEquals("@android:color/transparent", background.getValue());
    ResourceValue textColor = srv.getItem(ANDROID, "textColor");
    assertNotNull(textColor);
    assertEquals("#008", textColor.getValue());

    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item name=\"android:background\"");
      assertTrue(offset > 0);
      document.insertString(offset, "<item name=\"android:textSize\">20sp</item>");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar"));
    style = getOnlyItem(resources, ResourceType.STYLE, "DarkActionBar");
    srv = (StyleResourceValue)style.getResourceValue();
    assertNotNull(srv);
    checkDefinedItems(srv, "android:background", "android:textSize", "android:textColor");
    ResourceValue textSize = srv.getItem(ANDROID, "textSize");
    assertNotNull(textSize);
    assertEquals("20sp", textSize.getValue());
    resetCounters();

    // Now try a second edit.
    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item name=\"android:background\"");
      assertTrue(offset > 0);
      document.insertString(offset, "<item name=\"android:typeface\">monospace</item>");
      documentManager.commitDocument(document);
    });
    assertTrue(generation2 < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar"));
    style = getOnlyItem(resources, ResourceType.STYLE, "DarkActionBar");
    srv = (StyleResourceValue)style.getResourceValue();
    assertNotNull(srv);
    checkDefinedItems(srv, "android:background", "android:typeface", "android:textSize", "android:textColor");
    ResourceValue typeface = srv.getItem(ANDROID, "typeface");
    assertNotNull(typeface);
    assertEquals("monospace", typeface.getValue());

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  public void testRemoveStyleItem() {
    resetCounters();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar"));
    ResourceItem style = getOnlyItem(resources, ResourceType.STYLE, "DarkActionBar");
    StyleResourceValue srv = (StyleResourceValue)style.getResourceValue();
    assertNotNull(srv);
    checkDefinedItems(srv, "android:background", "android:textColor");
    ResourceValue background = srv.getItem(ANDROID, "background");
    assertNotNull(background);
    assertEquals("@android:color/transparent", background.getValue());
    ResourceValue textColor = srv.getItem(ANDROID, "textColor");
    assertNotNull(textColor);
    assertEquals("#008", textColor.getValue());

    long generation = resources.getModificationCount();
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
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar"));
    style = getOnlyItem(resources, ResourceType.STYLE, "DarkActionBar");
    srv = (StyleResourceValue)style.getResourceValue();
    assertNotNull(srv);
    checkDefinedItems(srv, "android:background");
    background = srv.getItem(ANDROID, "background");
    assertNotNull(background);
    assertEquals("@android:color/transparent", background.getValue());
    resetCounters();

    // Try second edit, which is incremental.
    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = ("<item name=\"android:background\">@android:color/transparent</item>");
      int offset = document.getText().indexOf(item);
      assertTrue(offset > 0);
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    assertTrue(generation2 < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar"));
    style = getOnlyItem(resources, ResourceType.STYLE, "DarkActionBar");
    srv = (StyleResourceValue)style.getResourceValue();
    assertNotNull(srv);
    checkDefinedItems(srv);

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  public void testEditDeclareStyleableAttr() {
    // Check edits of the name in a <declare-styleable> element.
    resetCounters();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    myFixture.openFileInEditor(file1);
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ATTR, "watchType"));
    ResourceItem style = getOnlyItem(resources, ResourceType.STYLEABLE, "MyCustomView");
    StyleableResourceValue srv = (StyleableResourceValue)style.getResourceValue();
    assertNotNull(srv);
    assertEquals(5, srv.getAllAttributes().size());
    AttrResourceValue watchType = findAttr(srv, "watchType", resources);
    assertNotNull(watchType);
    assertEquals(2, watchType.getAttributeValues().size());
    assertEquals(Integer.valueOf(1), watchType.getAttributeValues().get("type_stopwatch"));
    assertEquals(Integer.valueOf(0), watchType.getAttributeValues().get("type_countdown"));
    AttrResourceValue crash = findAttr(srv, "crash", resources);
    assertNotNull(crash);
    assertTrue(crash.getAttributeValues().isEmpty());

    AttrResourceValue minWidth = findAttr(srv, "minWidth", resources);
    assertNotNull(minWidth);
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ATTR, "minWidth"));
    AttrResourceValue ignoredNoFormat = findAttr(srv, "ignore_no_format", resources);
    assertNotNull(ignoredNoFormat);
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ATTR, "ignore_no_format"));

    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    type("MyCustom|View", "r");
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomrView"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ATTR, "watchType"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView"));
    resetCounters();

    // Now try another edit, where things should be incremental now.
    long generation2 = resources.getModificationCount();
    type("MyCustom|rView", "e");
    assertTrue(generation2 < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomerView"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ATTR, "watchType"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView"));
    style = getOnlyItem(resources, ResourceType.STYLEABLE, "MyCustomerView");
    srv = (StyleableResourceValue)style.getResourceValue();
    assertNotNull(srv);
    assertEquals(5, srv.getAllAttributes().size());
    watchType = findAttr(srv, "watchType", resources);
    assertNotNull(watchType);
    assertEquals(2, watchType.getAttributeValues().size());
    assertEquals(Integer.valueOf(1), watchType.getAttributeValues().get("type_stopwatch"));

    long generation3 = resources.getModificationCount();
    type("watch|Type", "Change");
    assertTrue(generation3 < resources.getModificationCount());

    assertSame(style, getOnlyItem(resources, ResourceType.STYLEABLE, "MyCustomerView"));
    assertNotSame(srv, style.getResourceValue());
    srv = (StyleableResourceValue)style.getResourceValue();
    assertNull(findAttr(srv, "watchType", resources));
    assertNotNull(findAttr(srv, "watchChangeType", resources));

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  public void testEditAttr() {
    // Insert, remove and change <attr> attributes inside a <declare-styleable> and ensure that
    resetCounters();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView"));
    // Fetch resource value to ensure it gets replaced after update.
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ATTR, "watchType"));
    ResourceItem style = getOnlyItem(resources, ResourceType.STYLEABLE, "MyCustomView");
    StyleableResourceValue srv = (StyleableResourceValue)style.getResourceValue();
    assertNotNull(srv);
    assertEquals(5, srv.getAllAttributes().size());
    AttrResourceValue watchType = findAttr(srv, "watchType", resources);
    assertNotNull(watchType);
    assertEquals(2, watchType.getAttributeValues().size());
    assertEquals(Integer.valueOf(1), watchType.getAttributeValues().get("type_stopwatch"));
    assertEquals(Integer.valueOf(0), watchType.getAttributeValues().get("type_countdown"));
    AttrResourceValue crash = findAttr(srv, "crash", resources);
    assertNotNull(crash);
    assertTrue(crash.getAttributeValues().isEmpty());

    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("watchType");
      document.insertString(offset, "y");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ATTR, "watchType"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ATTR, "ywatchType"));
    resetCounters();

    // However, the second edit can then be incremental.
    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("ywatchType");
      document.replaceString(offset, offset + 1, "w");
      documentManager.commitDocument(document);
    });

    assertFalse(resources.isScanPending(psiFile1));
    assertTrue(generation2 < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ATTR, "watchType"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ATTR, "wwatchType"));
    style = getOnlyItem(resources, ResourceType.STYLEABLE, "MyCustomView");
    srv = (StyleableResourceValue)style.getResourceValue();
    assertNotNull(srv);
    assertEquals(5, srv.getAllAttributes().size());
    watchType = findAttr(srv, "wwatchType", resources);
    assertNotNull(watchType);
    assertEquals(2, watchType.getAttributeValues().size());
    assertEquals(Integer.valueOf(1), watchType.getAttributeValues().get("type_stopwatch"));

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();

    // Now insert a new item and delete one and make sure we're still okay
    resetCounters();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String crashAttr = "<attr name=\"crash\" format=\"boolean\" />";
      int offset = document.getText().indexOf(crashAttr);
      document.deleteString(offset, offset + crashAttr.length());
      document.insertString(offset, "<attr name=\"newcrash\" format=\"integer\" />");
      documentManager.commitDocument(document);
    });
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      ensureSingleScan();
      assertTrue(generation2 < resources.getModificationCount());
      assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView"));
      assertFalse(resources.hasResources(RES_AUTO, ResourceType.ATTR, "watchType"));
      assertTrue(resources.hasResources(RES_AUTO, ResourceType.ATTR, "wwatchType"));
      ResourceItem style1 = getOnlyItem(resources, ResourceType.STYLEABLE, "MyCustomView");
      StyleableResourceValue srv1 = (StyleableResourceValue)style1.getResourceValue();
      assertNotNull(srv1);
      assertEquals(5, srv1.getAllAttributes().size());
      AttrResourceValue watchType1 = findAttr(srv1, "wwatchType", resources);
      assertNotNull(watchType1);
      assertEquals(2, watchType1.getAttributeValues().size());
      assertEquals(Integer.valueOf(1), watchType1.getAttributeValues().get("type_stopwatch"));
      assertEquals(Integer.valueOf(0), watchType1.getAttributeValues().get("type_countdown"));
      AttrResourceValue crash1 = findAttr(srv1, "crash", resources);
      assertNull(crash1);
      AttrResourceValue newcrash = findAttr(srv1, "newcrash", resources);
      assertNotNull(newcrash);
      assertTrue(newcrash.getAttributeValues().isEmpty());
    });
  }

  public void testEditDeclareStyleableFlag() {
    // Rename, add and remove <flag> and <enum> nodes under a declare styleable and assert
    // that the declare styleable parent is updated
    resetCounters();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView"));
    // Fetch resource value to ensure it gets replaced after update
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ATTR, "watchType"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ATTR, "ignore_no_format"));
    ResourceItem style = getOnlyItem(resources, ResourceType.STYLEABLE, "MyCustomView");
    StyleableResourceValue srv = (StyleableResourceValue)style.getResourceValue();
    assertNotNull(srv);
    assertEquals(5, srv.getAllAttributes().size());
    AttrResourceValue flagType = findAttr(srv, "flagType", resources);
    assertNotNull(flagType);
    assertEquals(2, flagType.getAttributeValues().size());
    assertEquals(Integer.valueOf(16), flagType.getAttributeValues().get("flag1"));
    assertEquals(Integer.valueOf(32), flagType.getAttributeValues().get("flag2"));

    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("flag1");
      document.insertString(offset + 1, "l");
      documentManager.commitDocument(document);
    });
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      ensureSingleScan();
      assertTrue(generation < resources.getModificationCount());
      assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView"));
      assertTrue(resources.hasResources(RES_AUTO, ResourceType.ATTR, "flagType"));
      ResourceItem style1 = getOnlyItem(resources, ResourceType.STYLEABLE, "MyCustomView");
      StyleableResourceValue srv1 = (StyleableResourceValue)style1.getResourceValue();
      assertNotNull(srv1);
      assertEquals(5, srv1.getAllAttributes().size());
      AttrResourceValue flagType1 = findAttr(srv1, "flagType", resources);
      assertNotNull(flagType1);
      assertEquals(2, flagType1.getAttributeValues().size());
      assertNull(flagType1.getAttributeValues().get("flag1"));
      assertEquals(Integer.valueOf(16), flagType1.getAttributeValues().get("fllag1"));

      // Now insert a new enum and delete one and make sure we're still okay
      resetCounters();
      long nextGeneration = resources.getModificationCount();
      WriteCommandAction.runWriteCommandAction(null, () -> {
        String enumAttr = "<enum name=\"type_stopwatch\" value=\"1\"/>";
        int offset = document.getText().indexOf(enumAttr);
        document.deleteString(offset, offset + enumAttr.length());
        String flagAttr = "<flag name=\"flag2\" value=\"0x20\"/>";
        offset = document.getText().indexOf(flagAttr);
        document.insertString(offset, "<flag name=\"flag3\" value=\"0x40\"/>");
        documentManager.commitDocument(document);
      });
      assertTrue(nextGeneration < resources.getModificationCount());
      assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLEABLE, "MyCustomView"));
      assertTrue(resources.hasResources(RES_AUTO, ResourceType.ATTR, "watchType"));
      assertTrue(resources.hasResources(RES_AUTO, ResourceType.ATTR, "flagType"));
      style1 = getOnlyItem(resources, ResourceType.STYLEABLE, "MyCustomView");
      srv1 = (StyleableResourceValue)style1.getResourceValue();
      assertNotNull(srv1);
      assertEquals(5, srv1.getAllAttributes().size());
      flagType1 = findAttr(srv1, "flagType", resources);
      assertNotNull(flagType1);
      assertEquals(3, flagType1.getAttributeValues().size());
      assertEquals(Integer.valueOf(16), flagType1.getAttributeValues().get("fllag1"));
      assertEquals(Integer.valueOf(32), flagType1.getAttributeValues().get("flag2"));
      assertEquals(Integer.valueOf(64), flagType1.getAttributeValues().get("flag3"));

      AttrResourceValue watchType = findAttr(srv1, "watchType", resources);
      assertNotNull(watchType);
      assertEquals(1, watchType.getAttributeValues().size());
      assertEquals(Integer.valueOf(0), watchType.getAttributeValues().get("type_countdown"));
    });
  }

  public void testEditPluralItems() {
    resetCounters();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    // Test that our tools:quantity works correctly for getResourceValue()
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.PLURALS, "my_plural"));
    ResourceItem plural = getOnlyItem(resources, ResourceType.PLURALS, "my_plural");
    ResourceValue resourceValue = plural.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("@string/hello_two", resourceValue.getValue());

    // TODO: It would be nice to avoid updating the generation if you edit a different item
    //       than the one being picked (default or via tools:quantity) but for now we're not
    //       worrying about that optimization.

    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("@string/hello_two");
      document.replaceString(offset + 9, offset + 10, "a");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    plural = getOnlyItem(resources, ResourceType.PLURALS, "my_plural");
    resourceValue = plural.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("@string/hallo_two", resourceValue.getValue());
    assertTrue(generation < resources.getModificationCount());
    resetCounters();

    // However, the second edit can then be incremental.
    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("@string/hallo_two");
      document.replaceString(offset + 9, offset + 10, "i");
      documentManager.commitDocument(document);
    });

    plural = getOnlyItem(resources, ResourceType.PLURALS, "my_plural");
    resourceValue = plural.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("@string/hillo_two", resourceValue.getValue());
    assertTrue(generation2 < resources.getModificationCount());

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  public void testAddPluralItems() {
    resetCounters();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.PLURALS, "my_plural"));
    ResourceItem plural = getOnlyItem(resources, ResourceType.PLURALS, "my_plural");
    ResourceValue resourceValue = plural.getResourceValue();
    assertNotNull(resourceValue);
    assertInstanceOf(resourceValue, PluralsResourceValue.class);
    PluralsResourceValue prv = (PluralsResourceValue)resourceValue;
    assertEquals(3, prv.getPluralsCount());
    assertEquals("@string/hello_two", resourceValue.getValue());
    assertEquals("two", prv.getQuantity(1));

    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item quantity=\"two\">@string/hello_two</item>");
      document.insertString(offset, "<item quantity=\"one_and_half\">@string/hello_one_and_half</item>");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    plural = getOnlyItem(resources, ResourceType.PLURALS, "my_plural");
    resourceValue = plural.getResourceValue();
    assertNotNull(resourceValue);
    assertInstanceOf(resourceValue, PluralsResourceValue.class);
    prv = (PluralsResourceValue)resourceValue;
    assertEquals(4, prv.getPluralsCount());
    assertEquals("@string/hello_two", resourceValue.getValue());
    assertEquals("one_and_half", prv.getQuantity(1));
    assertTrue(resources.getModificationCount() > generation);
    resetCounters();

    // Now try a second edit.
    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item quantity=\"one_and_half\">@string/hello_one_and_half</item>");
      document.insertString(offset, "<item quantity=\"one_and_a_quarter\">@string/hello_one_and_a_quarter</item>");
      documentManager.commitDocument(document);
    });
    assertTrue(resources.getModificationCount() > generation2);
    plural = getOnlyItem(resources, ResourceType.PLURALS, "my_plural");
    resourceValue = plural.getResourceValue();
    assertNotNull(resourceValue);
    assertInstanceOf(resourceValue, PluralsResourceValue.class);
    prv = (PluralsResourceValue)resourceValue;
    assertEquals(5, prv.getPluralsCount());
    assertEquals("@string/hello_two", resourceValue.getValue());
    assertEquals("one_and_a_quarter", prv.getQuantity(1));
    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  public void testRemovePluralItems() {
    resetCounters();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.PLURALS, "my_plural"));
    ResourceItem plural = getOnlyItem(resources, ResourceType.PLURALS, "my_plural");
    ResourceValue resourceValue = plural.getResourceValue();
    assertNotNull(resourceValue);
    assertInstanceOf(resourceValue, PluralsResourceValue.class);
    PluralsResourceValue prv = (PluralsResourceValue)resourceValue;
    assertEquals(3, prv.getPluralsCount());
    assertEquals("@string/hello_two", resourceValue.getValue());
    assertEquals("one", prv.getQuantity(0));

    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = "<item quantity=\"one\">@string/hello</item>";
      int offset = document.getText().indexOf(item);
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    plural = getOnlyItem(resources, ResourceType.PLURALS, "my_plural");
    resourceValue = plural.getResourceValue();
    assertNotNull(resourceValue);
    assertInstanceOf(resourceValue, PluralsResourceValue.class);
    prv = (PluralsResourceValue)resourceValue;
    assertEquals(2, prv.getPluralsCount());
    assertEquals("@string/hello_two", resourceValue.getValue());
    assertEquals("two", prv.getQuantity(0));
    assertTrue(generation < resources.getModificationCount());
    resetCounters();
    // Try a second edit.
    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = "<item quantity=\"other\">@string/hello_many</item>";
      int offset = document.getText().indexOf(item);
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    plural = getOnlyItem(resources, ResourceType.PLURALS, "my_plural");
    resourceValue = plural.getResourceValue();
    assertNotNull(resourceValue);
    assertInstanceOf(resourceValue, PluralsResourceValue.class);
    prv = (PluralsResourceValue)resourceValue;
    assertEquals(1, prv.getPluralsCount());
    assertEquals("@string/hello_two", resourceValue.getValue());
    assertEquals("two", prv.getQuantity(0));
    assertTrue(generation2 < resources.getModificationCount());

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  public void testEditArrayItemText() {
    resetCounters();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    // Test that our tools:index and fallback handling for arrays works correctly
    // for getResourceValue()
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    ResourceItem array = getOnlyItem(resources, ResourceType.ARRAY, "security_questions");
    ResourceValue resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Question 4", resourceValue.getValue());

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "integers"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "integers");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("10", resourceValue.getValue());

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("Question 4");
      document.insertString(offset, "Q");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("QQuestion 4", resourceValue.getValue());
    resetCounters();

    // However, the second edit can then be incremental.
    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("QQuestion 4");
      document.insertString(offset, "Q");
      documentManager.commitDocument(document);
    });

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("QQQuestion 4", resourceValue.getValue());

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  public void testAddStringArrayItemElements() {
    resetCounters();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    ResourceItem array = getOnlyItem(resources, ResourceType.ARRAY, "security_questions");
    ResourceValue resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Question 4", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    ArrayResourceValue arv = (ArrayResourceValue)resourceValue;
    assertEquals(5, arv.getElementCount());
    assertEquals("Question 2", arv.getElement(1));
    assertEquals("Question 3", arv.getElement(2));
    assertEquals("Question 4", arv.getElement(3));

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item>Question 3</item>");
      document.insertString(offset, "<item>Question 2.5</item>");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Question 3", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(6, arv.getElementCount());
    assertEquals("Question 2", arv.getElement(1));
    assertEquals("Question 2.5", arv.getElement(2));
    assertEquals("Question 3", arv.getElement(3));
    resetCounters();

    // However, the second edit can then be incremental.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item>Question 3</item>");
      document.insertString(offset, "<item>Question 2.75</item>");
      documentManager.commitDocument(document);
    });

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "security_questions");
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
    ensureIncremental();
  }

  public void testRemoveStringArrayItemElements() {
    resetCounters();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    ResourceItem array = getOnlyItem(resources, ResourceType.ARRAY, "security_questions");
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
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Question 4", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(5, arv.getElementCount());
    resetCounters();

    // Now try another edit that is also a delete item, where things should be incremental now.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String elementString = "<item>Question X</item>";
      int offset = document.getText().indexOf(elementString);
      document.deleteString(offset, offset + elementString.length());
      documentManager.commitDocument(document);
    });

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "security_questions"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("Question 5", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(4, arv.getElementCount());

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  public void testAddIntegerArrayItemElements() {
    resetCounters();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "integers"));
    ResourceItem array = getOnlyItem(resources, ResourceType.ARRAY, "integers");
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
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "integers"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "integers");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("5", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(3, arv.getElementCount());
    assertEquals("5", arv.getElement(0));
    assertEquals("10", arv.getElement(1));
    assertEquals("20", arv.getElement(2));
    resetCounters();

    // Now try a second edit.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item>5</item>");
      document.insertString(offset, "<item>2</item>");
      documentManager.commitDocument(document);
    });
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "integers"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "integers");
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
    ensureIncremental();
  }

  public void testRemoveIntegerArrayItemElements() {
    resetCounters();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "integers"));
    ResourceItem array = getOnlyItem(resources, ResourceType.ARRAY, "integers");
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
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "integers"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "integers");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(1, arv.getElementCount());
    assertEquals("20", resourceValue.getValue());
    resetCounters();

    // Try a second edit.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String item = "<item>20</item>";
      int offset = document.getText().indexOf(item);
      document.deleteString(offset, offset + item.length());
      documentManager.commitDocument(document);
    });
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "integers"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "integers");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(0, arv.getElementCount());

    // Shouldn't have done any full file rescans during the above edits.
    ensureIncremental();
  }

  public void testAddTypedArrayItemElements() {
    resetCounters();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "my_colors"));
    ResourceItem array = getOnlyItem(resources, ResourceType.ARRAY, "my_colors");
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
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "my_colors"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "my_colors");
    resourceValue = array.getResourceValue();
    assertNotNull(resourceValue);
    assertEquals("#FFFFFF00", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(4, arv.getElementCount());
    assertEquals("#FFFFFF00", arv.getElement(0));
    assertEquals("#FFFF0000", arv.getElement(1));
    assertEquals("#FF00FF00", arv.getElement(2));
    resetCounters();

    // Now try a second edit.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("<item>#FFFFFF00</item>");
      document.insertString(offset, "<item>#FFFFFFFF</item>");
      documentManager.commitDocument(document);
    });
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ARRAY, "my_colors"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "my_colors");
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
    ensureIncremental();
  }

  public void testGradualEdits() {
    resetCounters();

    // Gradually type in the contents of a value file and make sure we end up with a valid view of the world
    VirtualFile file1 = myFixture.copyFileToProject(VALUES_EMPTY, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    long generation = resources.getModificationCount();
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

    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      ensureSingleScan();
      assertTrue(generation < resources.getModificationCount());
      assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar"));

      assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkActionBar"));
      ResourceItem style = getOnlyItem(resources, ResourceType.STYLE, "DarkActionBar");
      StyleResourceValue srv = (StyleResourceValue)style.getResourceValue();
      assertNotNull(srv);
      ResourceValue actionBarStyle = srv.getItem(ANDROID, "background");
      assertNotNull(actionBarStyle);
      assertEquals("@android:color/transparent", actionBarStyle.getValue());
      assertEquals("Zoom", getOnlyItem(resources, ResourceType.STRING, "title_zoom").getResourceValue().getValue());
    });
  }

  public void testInvalidValueName() {
    VirtualFile file = myFixture.copyFileToProject(VALUES_EMPTY, "res/values/empty.xml");
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    assertNotNull(documentManager);
    Document document = documentManager.getDocument(psiFile);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(0, document.getTextLength() - 1, "<resources>\n<string name=\"\"\n</resources>");
      documentManager.commitDocument(document);
    });
    UIUtil.dispatchAllInvocationEvents();
    assertThat(resources.getResources(RES_AUTO, ResourceType.STRING).keySet()).isEmpty();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(0, document.getTextLength() - 1, "<resources>\n<string name=\"foo bar\"\n</resources>");
      documentManager.commitDocument(document);
    });
    UIUtil.dispatchAllInvocationEvents();
    assertThat(resources.getResources(RES_AUTO, ResourceType.STRING).keySet()).isEmpty();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(0,
                             document.getTextLength() - 1,
                             "<resources>\n<declare-styleable name=\"foo\"><attr format=\"boolean\" name=\"\"\n</declare-styleable></resources>");
      documentManager.commitDocument(document);
    });
    UIUtil.dispatchAllInvocationEvents();
    assertThat(resources.getResources(RES_AUTO, ResourceType.ATTR).keySet()).isEmpty();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(0,
                             document.getTextLength() - 1,
                             "<resources>\n<declare-styleable name=\"foo\"><attr format=\"boolean\" name=\"foo bar\"\n</declare-styleable></resources>");
      documentManager.commitDocument(document);
    });
    UIUtil.dispatchAllInvocationEvents();
    assertThat(resources.getResources(RES_AUTO, ResourceType.ATTR).keySet()).isEmpty();

    // Now exercise childAdded, without a full rescan.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.replaceString(0, document.getTextLength() - 1, "<resources>\n\n</resources>");
      documentManager.commitDocument(document);
    });
    UIUtil.dispatchAllInvocationEvents();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.insertString(document.getLineStartOffset(1), "<string name=\"\">foo</string>");
      documentManager.commitDocument(document);
    });
    UIUtil.dispatchAllInvocationEvents();
    assertThat(resources.getResources(RES_AUTO, ResourceType.STRING).keySet()).isEmpty();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.insertString(document.getLineStartOffset(1), "<string name=\"foo bar\">foo</string>");
      documentManager.commitDocument(document);
    });
    UIUtil.dispatchAllInvocationEvents();
    assertThat(resources.getResources(RES_AUTO, ResourceType.STRING).keySet()).isEmpty();
  }

  public void testInvalidId() {
    VirtualFile file = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertThat(resources.getResources(RES_AUTO, ResourceType.ID).keySet())
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

    UIUtil.dispatchAllInvocationEvents();
    assertThat(resources.getResources(RES_AUTO, ResourceType.ID).keySet())
        .containsExactly("btn_title_refresh", "header", "text2", "title_refresh_progress");

    // Check an invalid resource name.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      tag.setAttribute(ATTR_ID, ANDROID_URI, "@+id/foo bar");
    });

    UIUtil.dispatchAllInvocationEvents();
    assertThat(resources.getResources(RES_AUTO, ResourceType.ID).keySet())
        .containsExactly("btn_title_refresh", "header", "text2", "title_refresh_progress");
  }

  public void testInvalidFileResourceName() {
    // Test initial loading.
    myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/drawable/foo.bar.xml");
    ResourceFolderRepository resources = createRepository(false);
    assertThat(resources.getResources(RES_AUTO, ResourceType.DRAWABLE)).isEmpty();
    assertThat(resources.getResources(RES_AUTO, ResourceType.ID)).isEmpty();

    // Test adding a file after repository has been loaded.
    resources = createRegisteredRepository();
    myFixture.copyFileToProject(DRAWABLE_ID_SCAN, "res/drawable/bar.baz.xml");
    assertThat(resources.getResources(RES_AUTO, ResourceType.DRAWABLE)).isEmpty();
    assertThat(resources.getResources(RES_AUTO, ResourceType.ID)).isEmpty();
  }

  public void testIssue36973561() {
    // Test deleting a string; ensure that the whole repository is updated correctly.
    // Regression test for https://issuetracker.google.com/36973561.
    VirtualFile file1 = myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name2"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));

    long generation = resources.getModificationCount();
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
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "action_settings"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));
    resetCounters();

    // However, the second edit can then be incremental.
    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      String string = "    <string name=\"app_name\">My Application 574</string>";
      int offset = document.getText().indexOf(string);
      assertTrue(offset != -1);
      document.deleteString(offset, offset + string.length());
      documentManager.commitDocument(document);
    });

    assertFalse(resources.isScanPending(psiFile1));
    assertTrue(generation2 < resources.getModificationCount());
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "action_settings"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));
    ensureIncremental();
  }

  public void testEditXmlProcessingInstructionAttrInValues() {
    // Test editing an attribute in the XML prologue of a values file.
    VirtualFile file1 = myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name2"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));

    long generation = resources.getModificationCount();
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

    // Edits in XML processing instructions have no effect on the resource repository
    assertEquals(generation, resources.getModificationCount());
    assertFalse(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
  }

  public void testEditXmlProcessingInstructionAttrInLayout() {
    // Test editing an attribute in the XML prologue of a layout file with IDs.
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));

    long generation = resources.getModificationCount();
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
    assertEquals(generation, resources.getModificationCount());
    assertFalse(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
  }

  public void testIssue36986886() {
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
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertEquals("My Application 574",
                 resources.getResources(RES_AUTO, ResourceType.STRING, "app_name").get(0).getResourceValue().getValue());

    long generation = resources.getModificationCount();
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
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    resetCounters();

    // Second edit (duplicate again)
    long generation2 = resources.getModificationCount();
    int offset2 = document.getText().indexOf("</resources>");
    String string2 = "<string name=\"app_name\">Another Value</string>";
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      document.insertString(offset2, string2);
      documentManager.commitDocument(document);
    });

    assertTrue(generation2 < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));

    // Then replace the name of the duplicated string.
    long generation3 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      int startOffset = offset + "<string name=\"".length();
      document.replaceString(startOffset, startOffset + "app_name".length(), "new_name");
      documentManager.commitDocument(document);
    });

    assertFalse(resources.isScanPending(psiFile1));
    assertTrue(generation3 < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "new_name"));

    assertEquals("New Value",
                 resources.getResources(RES_AUTO, ResourceType.STRING, "new_name").get(0).getResourceValue().getValue());
    assertEquals("My Application 574",
                 resources.getResources(RES_AUTO, ResourceType.STRING, "app_name").get(0).getResourceValue().getValue());

    // Replace the second duplicate.
    long generation4 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      int startOffset = offset2 + "<string name=\"".length();
      document.replaceString(startOffset, startOffset + "app_name".length(), "new_name2");
      documentManager.commitDocument(document);
    });
    assertFalse(resources.isScanPending(psiFile1));
    assertTrue(generation4 < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "new_name"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "new_name2"));

    assertEquals("New Value",
                 resources.getResources(RES_AUTO, ResourceType.STRING, "new_name").get(0).getResourceValue().getValue());
    assertEquals("Another Value",
                 resources.getResources(RES_AUTO, ResourceType.STRING, "new_name2").get(0).getResourceValue().getValue());
    assertEquals("My Application 574",
                 resources.getResources(RES_AUTO, ResourceType.STRING, "app_name").get(0).getResourceValue().getValue());
    ensureIncremental();
  }

  public void testLoadDuplicatedValues() {
    resetCounters();

    // Test loading up a file that already illegally had duplicates.
    VirtualFile file1 = myFixture.copyFileToProject(VALUES_WITH_DUPES, "res/values/values.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STRING, "dupe_name"));
    assertEquals("Animations Demo",
                 resources.getResources(RES_AUTO, ResourceType.STRING, "app_name").get(0).getResourceValue().getValue());

    long generation = resources.getModificationCount();
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
    UIUtil.dispatchAllInvocationEvents();
    assertFalse(resources.isScanPending(psiFile1));
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "dupe_name"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertEquals("Duplicate Demo",
                 resources.getResources(RES_AUTO, ResourceType.STRING, "dupe_name").get(0).getResourceValue().getValue());
    assertEquals("Animations Demo",
                 resources.getResources(RES_AUTO, ResourceType.STRING, "app_name").get(0).getResourceValue().getValue());

    // Try editting something else, like the ID item.
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "action_prev"));
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      String origString = "<item type=\"id\" name=\"action_next\" />";
      String newString = "<item type=\"id\" name=\"action_prev\" />";
      int offset = document.getText().indexOf(origString);
      document.replaceString(offset, offset + origString.length(), newString);
      documentManager.commitDocument(document);
    });
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_next"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_prev"));

    ensureSingleScan();
  }

  /** Regression test for b/115880623 */
  public void testRemoveDuplicate() {
    VirtualFile valuesXml = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    ResourceFolderRepository resources = createRegisteredRepository();
    myFixture.openFileInEditor(valuesXml);

    assertThat(resources.getResources(RES_AUTO, ResourceType.STRING, "app_name")).hasSize(1);

    moveCaret(myFixture, "<string name=\"|app_name");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DUPLICATE);
    runListeners();
    assertThat(resources.getResources(RES_AUTO, ResourceType.STRING, "app_name")).hasSize(2);

    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DELETE_LINE);
    runListeners();
    assertThat(resources.getResources(RES_AUTO, ResourceType.STRING, "app_name")).hasSize(1);

    moveCaret(myFixture, "<string name=\"|app_name");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DUPLICATE);
    runListeners();
    assertThat(resources.getResources(RES_AUTO, ResourceType.STRING, "app_name")).hasSize(2);

    myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE);
    runListeners();
    assertThat(resources.getResources(RES_AUTO, ResourceType.STRING, "app_name")).hasSize(1);
  }

  public void testLoadValuesWithBadName() {
    resetCounters();

    // If a file had bad value names, test that it can still be parsed.
    VirtualFile file = myFixture.copyFileToProject(VALUES_WITH_BAD_NAME, "res/values/values.xml");
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertThat(resources.getResources(RES_AUTO, ResourceType.STRING)).isEmpty();

    long generation = resources.getModificationCount();
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
    assertThat(resources.isScanPending(psiFile)).isTrue();
    assertThat(resources.getModificationCount()).isEqualTo(generation);
    UIUtil.dispatchAllInvocationEvents();
    List<ResourceItem> items = resources.getResources(RES_AUTO, ResourceType.STRING, "app_name");
    assertThat(items).hasSize(1);
    assertThat(items.get(0).getResourceValue().getValue()).isEqualTo("Fixed Animations Demo");
  }

  /**
   * Test for http://b/138841328.
   */
  public void testLoadAfterExternalChangeToLayout() throws Exception {
    myFixture.copyFileToProject(LAYOUT_WITHOUT_IDS, "res/layout/layout.xml");

    ResourceFolderRepository resources = createRepository(true);
    assertThat(resources.getResources(RES_AUTO, ResourceType.ID)).isEmpty();
    assertThat(resources.getResources(RES_AUTO, ResourceType.LAYOUT, "layout")).hasSize(1);

    TestUtils.waitForFileSystemTick();
    myFixture.copyFileToProject(LAYOUT_WITH_ONE_ID, "res/layout/layout.xml");

    ResourceFolderRepository resourcesReloaded = createRepository(false);
    assertThat(resourcesReloaded.getResources(RES_AUTO, ResourceType.ID, "foo")).hasSize(1);
    assertThat(resourcesReloaded.getResources(RES_AUTO, ResourceType.LAYOUT, "layout")).hasSize(1);
  }

  public void testIdScanFromLayout() {
    // Test for https://issuetracker.google.com/37044944.
    myFixture.copyFileToProject(LAYOUT_ID_SCAN, "res/layout/layout1.xml");
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    Collection<String> ids = resources.getResources(RES_AUTO, ResourceType.ID).keySet();
    assertNotNull(ids);
    assertContainsElements(ids, "header", "image", "styledView", "imageView", "imageView2", "imageButton", "nonExistent");
  }

  public void testSync() {
    // Regression test for https://issuetracker.google.com/37010548.
    // Ensure that sync() handles rescanning immediately.
    VirtualFile file1 = myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name2"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));

    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
      // The sync() call must be called from the dispatch thread
      WriteCommandAction.runWriteCommandAction(null, () -> {
        String string = "    <string name=\"hello_world\">Hello world!</string>";
        int offset = document.getText().indexOf(string);
        assertTrue(offset != -1);

        // Simulate an edit event that triggers the incremental updater to
        // give up and schedule a subsequent update instead. (This used to be
        // the case here, but as of IntelliJ 14.1 it's now delivering more
        // accurate events for the below edits, which made the sync-test
        // fail because it would already have correct results *before* the
        // sync. Therefore, we simply trigger a pending scan (which stops
        // subequent incremental events from being processed).
        resources.scheduleScan(file1);

        document.deleteString(offset, offset + string.length());
        documentManager.commitDocument(document);
      });

      // The strings file contains definitions for app_name, action_settings and hello_world.
      // We've manually deleted the hello_world string. We now check that app_name remains
      // in the resource set, and that hello_world is removed. (We check that hello_world
      // is there before the sync, and gone after.)
      assertTrue(resources.isScanPending(psiFile1));
      assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
      assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));

      assertTrue(getApplication().isDispatchThread());
      resources.sync();
      assertTrue(generation < resources.getModificationCount());
      assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
      assertFalse(resources.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));
      assertFalse(resources.isScanPending(psiFile1));
    });
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
    Future<ResourceFolderRepository> loadJob = executorService.submit(() -> createRegisteredRepository());
    ResourceFolderRepository resources = loadJob.get();
    assertNotNull(resources);
    assertEquals("land", getOnlyItem(resources, ResourceType.LAYOUT, "layout_with_data_binding").getConfiguration().getQualifierString());
    ResourceItem dupedStringItem = resources.getResources(RES_AUTO, ResourceType.STRING, "app_name").get(0);
    assertNotNull(dupedStringItem);
    assertEquals("en", dupedStringItem.getConfiguration().getQualifierString());
  }

  public void testEditColorStateList() {
    resetCounters();

    VirtualFile file1 = myFixture.copyFileToProject(COLOR_STATELIST, "res/color/my_state_list.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.COLOR, "my_state_list"));

    // Edit comment
    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf(" -->");
      document.replaceString(offset, offset, "more comment");
      documentManager.commitDocument(document);
    });

    // Shouldn't have caused any change.
    assertEquals(generation, resources.getModificationCount());
    ensureIncremental();

    // Edit processing instruction.
    generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("utf-8");
      document.replaceString(offset, offset + 5, "other encoding");
      documentManager.commitDocument(document);
    });

    // Shouldn't have caused any change.
    assertEquals(generation, resources.getModificationCount());
    ensureIncremental();

    // Edit state list
    generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("myColor");
      document.replaceString(offset, offset + 7, "myNewColor");
      documentManager.commitDocument(document);
    });

    // Should have caused a modification but not a rescan.
    assertTrue(generation < resources.getModificationCount());
    ensureIncremental();
  }

  /**
   * Check that whitespace edits do not trigger a rescan
   */
  public void testEmptyEdits() {
    resetCounters();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);

    long generation = resources.getModificationCount();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Add a space to an attribute name.
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("app_name");
      document.insertString(offset, "_");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(generation < resources.getModificationCount());
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.STRING, "app_name"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "_app_name"));
    resetCounters();

    // Try a second edit, adding another space.
    long generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("_app_name");
      document.insertString(offset, "_");
      documentManager.commitDocument(document);
    });

    assertFalse(resources.isScanPending(psiFile1));
    assertTrue(generation2 < resources.getModificationCount());
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "__app_name"));
    generation2 = resources.getModificationCount();

    ResourceItem item = getOnlyItem(resources, ResourceType.STRING, "title_zoom");
    assertEquals("Zoom", item.getResourceValue().getValue());
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("Zoom");
      document.deleteString(offset, offset + "Zoom".length());
      documentManager.commitDocument(document);
    });
    assertFalse(resources.isScanPending(psiFile1));
    assertTrue(generation2 < resources.getModificationCount());

    // Inserting spaces in the middle of a tag shouldn't trigger a rescan or even change the modification count
    generation2 = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("Card Flip");
      document.insertString(offset, "   ");
      documentManager.commitDocument(document);
    });
    assertFalse(resources.isScanPending(psiFile1));
    assertEquals(generation2, resources.getModificationCount());

    ensureIncremental();
  }

  /**
   * Basic test to show that a load from an empty file cache doesn't pull things out of thin air
   * and that the file cache is considered stale (as a signal that updating would be good).
   */
  public void testFileCacheFreshness() {
    resetCounters();

    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout-xlarge-land/layout.xml");
    myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    ResourceFolderRepository resources = createRepository(true);
    assertNotNull(resources);
    assertFalse(resources.hasFreshFileCache());
    assertEquals(3, resources.getNumXmlFilesLoadedInitially());
    assertEquals(resources.getNumXmlFilesLoadedInitially(), resources.getNumXmlFilesLoadedInitiallyFromSources());

    ResourceFolderRepository resourcesReloaded = createRepository(false);
    assertNotSame(resources, resourcesReloaded);
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
    ResourceFolderRepository resources = createRepository(true);
    assertNotNull(resources);
    assertFalse(resources.hasFreshFileCache());
    assertEquals(7, resources.getNumXmlFilesLoadedInitially());
    assertEquals(resources.getNumXmlFilesLoadedInitially(), resources.getNumXmlFilesLoadedInitiallyFromSources());

    ResourceFolderRepository fromCacheFile = createRepository(true);
    assertNotNull(fromCacheFile);
    // Check that fromCacheFile really avoided reparsing some XML files, before checking equivalence of items.
    assertTrue(fromCacheFile.hasFreshFileCache());
    assertEquals(7, fromCacheFile.getNumXmlFilesLoadedInitially());
    assertEquals(0, fromCacheFile.getNumXmlFilesLoadedInitiallyFromSources());

    assertNotSame(resources, fromCacheFile);
    assertContainSameData(myFacet, resources, fromCacheFile);
  }

  public void testInvalidateCache() {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout-xlarge-land/layout.xml");
    myFixture.copyFileToProject(LAYOUT_WITH_DATA_BINDING, "res/layout/layout_with_data_binding.xml");
    myFixture.copyFileToProject(DRAWABLE, "res/drawable/logo.png");
    myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    ResourceFolderRepository resources = createRepository(true);
    assertNotNull(resources);
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
    ResourceFolderRepository resources = createRepository(true);
    assertNotNull(resources);

    // Check "resources" before deletion, and "fromCacheFile" after deletion.
    // Note that the in-memory "resources" also gets updated from a Psi listener
    // so save to blob before picking up the Psi change.
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));

    // Delete a non-value file.
    WriteCommandAction.runWriteCommandAction(null, () -> psiFile1.delete());

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
    WriteCommandAction.runWriteCommandAction(null, () -> psiFile2.delete());

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
    ResourceFolderRepository resources = createRepository(true);
    assertNotNull(resources);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));

    WriteCommandAction.runWriteCommandAction(null, () -> psiFile1.delete());

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
    ResourceFolderRepository resources = createRepository(true);
    assertNotNull(resources);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));

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
    ResourceFolderRepository resources = createRepository(true);
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "noteArea"));

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
    ResourceFolderRepository resources = createRepository(true);
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STRING, "hello_world"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo"));

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
    ResourceFolderRepository resources = createRepository(true);
    assertNotNull(resources);
    FolderConfiguration config = FolderConfiguration.getConfigForFolder("layout-xlarge-land");
    assertNotNull(config);
    // For layouts, the ResourceValue#getValue is the file path.
    ResourceValue value = ResourceRepositoryUtil.getConfiguredValue(resources, ResourceType.LAYOUT, "activity_foo", config);
    assertNotNull(value);
    String valueString = value.getValue();
    assertNotNull(valueString);
    assertTrue(valueString.endsWith("activity_foo.xml"));
    assertTrue(valueString.contains("layout-xlarge-land"));

    ResourceFolderRepository fromCacheFile = createRepository(true);
    assertNotNull(fromCacheFile);
    assertTrue(fromCacheFile.hasFreshFileCache());

    assertNotSame(resources, fromCacheFile);
    assertContainSameData(myFacet, resources, fromCacheFile);
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
    ResourceFolderRepository resources = createRepository(true);
    assertNotNull(resources);
    FolderConfiguration config = FolderConfiguration.getConfigForFolder("drawable-xhdpi");
    assertNotNull(config);
    // For drawable xml, the ResourceValue#getValue is the file path.
    ResourceValue value = ResourceRepositoryUtil.getConfiguredValue(resources, ResourceType.DRAWABLE, "drawable_foo", config);
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

    assertNotSame(resources, fromCacheFile);
    assertContainSameData(myFacet, resources, fromCacheFile);
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
  public void testFileInvalidationAfterDumbMode() {
    DumbServiceImpl dumbService = (DumbServiceImpl)DumbService.getInstance(getProject());
    dumbService.setDumb(true);
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile);
    ResourceFolderRepository resources = createRegisteredRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.STYLE, "DarkTheme"));

    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiFile);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("DarkTheme");
      document.replaceString(offset, offset + 4, "Grey");
      documentManager.commitDocument(document);
    });
    // First edit won't be incremental (file -> Psi).
    assertTrue(resources.isScanPending(psiFile));
    UIUtil.dispatchAllInvocationEvents();

    ResourceItem item = resources.getResources(RES_AUTO, ResourceType.STYLE, "GreyTheme").get(0);
    dumbService.setDumb(false);
    // Before the fix, item.getResourceValue would return null since the file is not invalid after getting out of dumb mode.
    assertNotNull(item.getResourceValue());
  }

  public void testAddingPlusToId() {
    PsiFile layout = myFixture.addFileToProject("res/layout/my_layout.xml",
                                              // language=XML
                                              "<LinearLayout xmlns:android='http://schemas.android.com/apk/res/android'>" +
                                              "  <TextView android:id='@id/aaa' />" +
                                              "  <TextView android:id='@id/bbb' />" +
                                              "  <TextView android:id='@id/ccc' />" +
                                              "</LinearLayout>");
    myFixture.openFileInEditor(layout.getVirtualFile());

    ResourceFolderRepository resources = createRegisteredRepository();
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "aaa"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "bbb"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "ccc"));
    long timestamp = resources.getModificationCount();

    type("@|id/aaa", "+");
    assertTrue(resources.isScanPending(layout));
    UIUtil.dispatchAllInvocationEvents();

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "aaa"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "bbb"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "ccc"));
    assertThat(resources.getModificationCount()).named("New modification count").isGreaterThan(timestamp);

    timestamp = resources.getModificationCount();
    type("@|id/bbb", "+");
    assertFalse(resources.isScanPending(layout)); // Should be incremental
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "aaa"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "bbb"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.ID, "ccc"));
    assertThat(resources.getModificationCount()).named("New modification count").isGreaterThan(timestamp);

    // Now try setAttribute which triggers a different PsiEvent, similar to pasting.
    timestamp = resources.getModificationCount();
    XmlTag cccTag = findTagById(layout, "ccc");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      cccTag.setAttribute(ATTR_ID, ANDROID_URI, "@+id/ccc");
    });

    assertFalse(resources.isScanPending(layout)); // Should be incremental
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "aaa"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "bbb"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "ccc"));
    assertThat(resources.getModificationCount()).named("New modification count").isGreaterThan(timestamp);
  }

  /**
   * This test checks that when the content of a bitmap is updated, the resource repository is notified.
   * <p>
   * We do that by checking that when an image content is changed from red to blue,
   * LayoutLib clears its caches.
   * <p>
   * b/129668736
   */
  public void testBitmapUpdated() throws IOException {
    VirtualFile logoFile = myFixture.copyFileToProject(DRAWABLE_RED, "res/drawable/logo.png");
    ResourceFolderRepository resources = createRegisteredRepository();
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.DRAWABLE, "logo"));
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myFacet).getConfiguration(logoFile);
    DrawableRenderer renderer = new DrawableRenderer(myFacet, configuration);

    String bitmapXml = "<adaptive-icon xmlns:android=\"http://schemas.android.com/apk/res/android\">\n" +
                       "    <background android:drawable=\"@drawable/logo\"/>\n" +
                       "    <foreground android:drawable=\"@drawable/logo\"/>\n" +
                       "</adaptive-icon>";
    int red = renderer.renderDrawable(bitmapXml, COLORED_DRAWABLE_SIZE).join().getRGB(0, 0);

    // We don't check the alpha byte because its value is not FF as expected but
    // that is not significant for this test.
    assertEquals("ff0000", Integer.toHexString(red).substring(2));

    byte[] newContent = Files.readAllBytes(new File(myFixture.getTestDataPath(), DRAWABLE_BLUE).toPath());
    WriteAction.run(() -> logoFile.setBinaryContent(newContent));
    WaitFor waitFor = new WaitFor((int)TimeUnit.SECONDS.toMillis(10), 500) {
      @Override
      protected boolean condition() {
        UIUtil.dispatchAllInvocationEvents();
        int blue = renderer.renderDrawable(bitmapXml, COLORED_DRAWABLE_SIZE).join().getRGB(0, 0);
        return "0000ff".equals(Integer.toHexString(blue).substring(2));
      }
    };

    int blue = renderer.renderDrawable(bitmapXml, COLORED_DRAWABLE_SIZE).join().getRGB(0, 0);
    assertTrue("The layout cache has never been cleared in the given timeout duration.\n" +
               "Current value: " + Integer.toHexString(blue).substring(2),  waitFor.isConditionRealized());
  }

  /**
   * When the IDE enters or exits dumb mode, the cache mapping the VirtualFile directories to PsiDirectory
   * ({@link FileManagerImpl#getVFileToPsiDirMap()}) is cleared from the {@link com.intellij.openapi.project.DumbService.DumbModeListener}.
   * <p>
   * Dumb mode is entered any time a file is added or deleted.
   * <p>
   * When a file is created in a directory that is not cached in this map, {@link com.intellij.psi.impl.file.impl.PsiVFSListener#fileCreated}
   * will trigger a {@link com.intellij.psi.PsiTreeChangeEvent#PROP_UNLOADED_PSI}
   * event which is not handled by the {@link com.android.tools.idea.res.ResourceFolderRepository.IncrementalUpdatePsiListener}
   * so the new file is never added to the repository.
   * <p>
   * Instead of relying on the Psi system for file change event, we now rely on the VFS system which does not suffer from this
   * problem.
   * <p>
   * See http://b/130800515
   */
  public void testRepositoryUpdatedAfterDumbMode() {
    ResourceFolderRepository repository = createRegisteredRepository();
    VirtualFile dir = myFixture.copyFileToProject(DRAWABLE, "res/drawable/image.png").getParent();
    VirtualFile file = VfsUtil.findFileByIoFile(new File(myFixture.getTestDataPath(), DRAWABLE), true);

    // Trigger Dumbmode to clear the PsiDirectory cache.
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
    UIUtil.dispatchAllInvocationEvents();
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
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

    assertTrue(repository.hasResources(RES_AUTO, ResourceType.STRING, "from_git"));
  }

  /**
   * Simulates what our templates do, modififying Documents that have no PSI (yet). Since PSI can be garbage-collected at any point, this
   * is possible in other cases as well.
   *
   * @see com.android.tools.idea.templates.TemplateUtils#writeTextFile(Object, String, File)
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
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

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
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
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

    ResourceFolderRepository secondRepo = createRepository(true);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    assertTrue(secondRepo.hasResources(RES_AUTO, ResourceType.STRING, "inDocument"));
    assertFalse(secondRepo.hasResources(RES_AUTO, ResourceType.STRING, "onDisk"));
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
    runListeners();
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a");

    type("@+id/a|", "a");
    runListeners();
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
    runListeners();

    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");
    // Make sure we've switched to PSI.
    assertThat(Iterables.getOnlyElement(repository.getResources(RES_AUTO, ResourceType.ID, "a")))
      .isInstanceOf(PsiResourceItem.class);

    moveCaret(myFixture, "@+id|/a");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DUPLICATE);
    assertFalse(repository.isScanPending(layout));
    runListeners();
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");

    myFixture.type('x');
    assertFalse(repository.isScanPending(layout));
    runListeners();
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");

    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getCaretOffset() - 8);
    myFixture.type('x');
    assertFalse(repository.isScanPending(layout));
    runListeners();
    // Check the edit above is what we wanted.
    assertThat(myFixture.getFile().findElementAt(myFixture.getCaretOffset()).getText()).isEqualTo("android:ixd");
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");
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
    runListeners();

    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");
    // Make sure we've switched to PSI.
    assertThat(Iterables.getOnlyElement(repository.getResources(RES_AUTO, ResourceType.ID, "a")))
      .isInstanceOf(PsiResourceItem.class);

    moveCaret(myFixture, "@+id|/a");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DUPLICATE);
    assertFalse(repository.isScanPending(layout));
    runListeners();
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");

    myFixture.type('x');
    assertFalse(repository.isScanPending(layout));
    runListeners();
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");

    myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getCaretOffset() - 8);
    myFixture.type('x');
    assertFalse(repository.isScanPending(layout));
    runListeners();
    // Check the edit above is what we wanted.
    assertThat(myFixture.getFile().findElementAt(myFixture.getCaretOffset()).getText())
      .isEqualTo("app:layout_constraintTop_toTopOxf");
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");
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
    assertThat(Iterables.getOnlyElement(repository.getResources(RES_AUTO, ResourceType.ID, "a")))
      .isNotInstanceOf(PsiResourceItem.class);

    myFixture.openFileInEditor(layout.getVirtualFile());
    moveCaret(myFixture, "@+id|/a");
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DUPLICATE);
    PsiDocumentManager.getInstance(getProject()).commitDocument(myFixture.getEditor().getDocument());
    assertTrue(repository.isScanPending(layout));
    runListeners();
    assertThat(repository.getResources(RES_AUTO, ResourceType.ID).keySet()).containsExactly("a", "b");
    assertThat(Iterables.getOnlyElement(repository.getResources(RES_AUTO, ResourceType.ID, "b")))
      .isInstanceOf(PsiResourceItem.class);
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
    assertFalse(repository.isScanPending(layout));
  }

  public void testFontChanged() throws Exception {
    VirtualFile ttfFile = VfsTestUtil.createFile(ProjectUtil.guessProjectDir(getProject()),
                                                 "res/font/myfont.ttf",
                                                 new byte[] { 1 });
    ResourceFolderRepository repository = createRegisteredRepository();

    resetCounters();
    getApplication().runWriteAction(() -> {
      try {
        ttfFile.setBinaryContent(new byte[] { 2 });
      }
      catch (IOException e) {
        throw new AssertionError(e);
      }
    });
    runListeners();
    ensureLayoutlibCachesFlushed();
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

    VirtualFile valid = VfsTestUtil.createFile(ProjectUtil.guessProjectDir(getProject()),
                                               "res/drawable/valid.png",
                                               new byte[] { 1 });
    VirtualFile invalidButIdentifier = VfsTestUtil.createFile(ProjectUtil.guessProjectDir(getProject()),
                                                              "res/drawable/FooBar.png",
                                                              new byte[] { 1 });
    VirtualFile invalid = VfsTestUtil.createFile(ProjectUtil.guessProjectDir(getProject()),
                                                 "res/drawable/1st.png",
                                                 new byte[] { 1 });
    ResourceFolderRepository repository = createRegisteredRepository();

    assertThat(repository.getResourceNames(RES_AUTO, ResourceType.DRAWABLE)).containsExactly("valid", "FooBar");
    assertThat(wolfTheProblemSolver.isProblemFile(valid)).isFalse();
    assertThat(wolfTheProblemSolver.isProblemFile(invalid)).isTrue();
    assertThat(wolfTheProblemSolver.isProblemFile(invalidButIdentifier)).isTrue();

    WriteAction.run(() -> {
      invalid.rename(this, "fixed.png");
      invalidButIdentifier.rename(this, "also_fixed.png");
    });
    runListeners();

    assertThat(repository.getResourceNames(RES_AUTO, ResourceType.DRAWABLE)).containsExactly("valid", "fixed", "also_fixed");
    assertThat(wolfTheProblemSolver.isProblemFile(valid)).isFalse();
    assertThat(wolfTheProblemSolver.isProblemFile(invalid)).isFalse();
    assertThat(wolfTheProblemSolver.isProblemFile(invalidButIdentifier)).isFalse();
  }

  @Nullable
  private static XmlTag findTagById(@NotNull PsiFile file, @NotNull String id) {
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

  @Nullable
  private static XmlTag findTagByName(@NotNull PsiFile file, @SuppressWarnings("SameParameterValue") @NotNull String name) {
    for (XmlTag tag : PsiTreeUtil.findChildrenOfType(file, XmlTag.class)) {
      String tagName = tag.getAttributeValue(ATTR_NAME);
      if (name.equals(tagName)) {
        return tag;
      }
    }
    return null;
  }

  @Nullable
  private static AttrResourceValue findAttr(@NotNull StyleableResourceValue styleable,
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

  private void runListeners() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
  }
}
