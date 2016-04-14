/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.*;
import com.android.ide.common.res2.DataBindingResourceType;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.databinding.DataBindingUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.rendering.ResourceFolderRepository.ourFullRescans;

/**
 * TODO: Add XmlTags with Psi events to check childAdded etc working correctly! Currently they mostly seem to generate big rescans.
 * TODO: Test moving from one resource folder to another; should be simulated as an add in one folder and a remove in another;
 *       check that in the ModuleResourceRepository test!
 * TODO: Test that adding and removing characters inside a {@code <string>} element does not cause a full rescan
 */
@SuppressWarnings({"UnusedDeclaration", "SpellCheckingInspection"})
public class ResourceFolderRepositoryTest extends AndroidTestCase {
  private static final String LAYOUT1 = "resourceRepository/layout.xml";
  private static final String LAYOUT2 = "resourceRepository/layout2.xml";
  private static final String LAYOUT_ID_SCAN = "resourceRepository/layout_for_id_scan.xml";
  private static final String LAYOUT_WITH_DATA_BINDING = "resourceRepository/layout_with_data_binding.xml";
  private static final String VALUES1 = "resourceRepository/values.xml";
  private static final String VALUES_EMPTY = "resourceRepository/empty.xml";
  private static final String XLIFF = "resourceRepository/xliff.xml";
  private static final String STRINGS = "resourceRepository/strings.xml";
  private static final String DRAWABLE = "resourceRepository/logo.png";
  private static final String COLOR_STATELIST = "resourceRepository/statelist.xml";

  @Override
  public void tearDown() throws Exception {
    ResourceFolderRegistry.reset();
    super.tearDown();
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ResourceFolderRegistry.reset();
  }

  private static void resetScanCounter() {
    ourFullRescans = 0;
  }

  private static void ensureIncremental() {
    assertEquals(0, ourFullRescans);
  }

  private static void ensureSingleScan() {
    assertEquals(1, ourFullRescans);
  }

  private ResourceFolderRepository createRepository() {
    List<VirtualFile> resourceDirectories = myFacet.getAllResourceDirectories();
    assertNotNull(resourceDirectories);
    assertSize(1, resourceDirectories);
    VirtualFile dir = resourceDirectories.get(0);
    return ResourceFolderRegistry.get(myFacet, dir);
  }

  public void testComputeResourceStrings() throws Exception {
    // Tests the handling of markup to raw strings
    // For example, for this strings definition
    //   <string name="title_template_step">Step <xliff:g id="step_number">%1$d</xliff:g>: Lorem Ipsum</string>
    // the resource value should be
    //   Step %1$d: Lorem Ipsum
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    List<ResourceItem> labelList = resources.getResourceItem(ResourceType.STRING, "title_template_step");
    assertNotNull(labelList);
    assertEquals(1, labelList.size());
    ResourceItem label = labelList.get(0);
    ResourceValue resourceValue = label.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("Step ${step_number}: Lorem Ipsum", resourceValue.getValue()); // In the file, there's whitespace unlike example above

    // Test unicode escape handling: <string name="ellipsis">Here it is: \u2026!</string>
    labelList = resources.getResourceItem(ResourceType.STRING, "ellipsis");
    assertNotNull(labelList);
    assertEquals(1, labelList.size());
    label = labelList.get(0);
    resourceValue = label.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("Here it is: \u2026!", resourceValue.getValue());

    // Make sure we pick up id's defined using types
    assertTrue(resources.hasResourceItem(ResourceType.ID, "action_next"));
    assertFalse(resources.hasResourceItem(ResourceType.ID, "action_next2"));
  }

  @SuppressWarnings("ConstantConditions")
  public void testXliff() throws Exception {
    // Tests the handling of xliff markup
    VirtualFile file1 = myFixture.copyFileToProject(XLIFF, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    assertEquals("Share your score of (1337) with (Bluetooth)!",
                 resources.getResourceItem(ResourceType.STRING, "share_with_application").get(0).getResourceValue(false).getValue());
    assertEquals("Call ${name}",
                 resources.getResourceItem(ResourceType.STRING, "description_call").get(0).getResourceValue(false).getValue());
    assertEquals("(42) mins (28) secs",
                 resources.getResourceItem(ResourceType.STRING, "callDetailsDurationFormat").get(0).getResourceValue(false).getValue());
    assertEquals("${number_of_sessions} sessions removed from your schedule",
                 resources.getResourceItem(ResourceType.STRING, "other").get(0).getResourceValue(false).getValue());
  }

  public void testInitialCreate() throws Exception {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");

    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    Collection<String> layouts = resources.getItemsOfType(ResourceType.LAYOUT);
    assertEquals(2, layouts.size());

    assertNotNull(resources.getResourceItem(ResourceType.LAYOUT, "layout1"));
    assertNotNull(resources.getResourceItem(ResourceType.LAYOUT, "layout2"));
  }

  public void testAddFile() throws Exception {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    Collection<String> layouts = resources.getItemsOfType(ResourceType.LAYOUT);
    assertEquals(2, layouts.size());
    assertNotNull(resources.getResourceItem(ResourceType.LAYOUT, "layout1"));
    assertNotNull(resources.getResourceItem(ResourceType.LAYOUT, "layout2"));

    long generation = resources.getModificationCount();
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");
    assertEquals(generation, resources.getModificationCount()); // no changes in file: no new generation

    generation = resources.getModificationCount();
    VirtualFile file3 = myFixture.copyFileToProject(LAYOUT1, "res/layout-xlarge-land/layout3.xml");
    PsiFile psiFile3 = PsiManager.getInstance(getProject()).findFile(file3);
    assertNotNull(psiFile3);
    assertTrue(resources.getModificationCount() > generation);

    layouts = resources.getItemsOfType(ResourceType.LAYOUT);
    assertEquals(3, layouts.size());
  }

  public void testAddUnrelatedFile() throws Exception {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    Collection<String> layouts = resources.getItemsOfType(ResourceType.LAYOUT);
    assertEquals(2, layouts.size());
    assertNotNull(resources.getResourceItem(ResourceType.LAYOUT, "layout1"));
    assertNotNull(resources.getResourceItem(ResourceType.LAYOUT, "layout2"));

    long generation = resources.getModificationCount();
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.unrelated");
    assertEquals(generation, resources.getModificationCount()); // no changes in file: no new generation
    assertEquals(2, layouts.size());

    myFixture.copyFileToProject(LAYOUT1, "src/layout/layout2.xml"); // not a resource folder
    assertEquals(generation, resources.getModificationCount()); // no changes in file: no new generation
    assertEquals(2, layouts.size());
  }

  public void testDeleteResourceFile() throws Exception {
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    Collection<String> drawables = resources.getItemsOfType(ResourceType.DRAWABLE);
    assertEquals(drawables.toString(), 0, drawables.size());
    long generation = resources.getModificationCount();
    VirtualFile file4 = myFixture.copyFileToProject(LAYOUT1, "res/drawable-mdpi/foo.png");
    final PsiFile psiFile4 = PsiManager.getInstance(getProject()).findFile(file4);
    assertNotNull(psiFile4);
    assertTrue(resources.getModificationCount() > generation);

    // Delete a file and make sure the item is removed from the repository (and modification count bumped)
    drawables = resources.getItemsOfType(ResourceType.DRAWABLE);
    assertEquals(1, drawables.size());
    generation = resources.getModificationCount();
    assertEquals("foo", drawables.iterator().next());
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        psiFile4.delete();
      }
    });
    drawables = resources.getItemsOfType(ResourceType.DRAWABLE);
    assertEquals(0, drawables.size());
    assertTrue(resources.getModificationCount() > generation);
  }

  public void testDeleteResourceDirectory() throws Exception {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");
    final VirtualFile file3 = myFixture.copyFileToProject(LAYOUT1, "res/layout-xlarge-land/layout3.xml");
    PsiFile psiFile3 = PsiManager.getInstance(getProject()).findFile(file3);
    assertNotNull(psiFile3);

    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    // Try deleting a whole resource directory and ensure we remove the files within
    long generation = resources.getModificationCount();
    Collection<String> layouts = resources.getItemsOfType(ResourceType.LAYOUT);
    assertEquals(3, layouts.size());
    final PsiDirectory directory = psiFile3.getContainingDirectory();
    assertNotNull(directory);
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        directory.delete();
      }
    });
    layouts = resources.getItemsOfType(ResourceType.LAYOUT);
    assertEquals(2, layouts.size());
    assertTrue(resources.getModificationCount() > generation);
  }

  public void testDeleteRemainderResourceIDs() throws Exception {
    final VirtualFile file = myFixture.copyFileToProject(LAYOUT_ID_SCAN, "res/layout-xlarge-land/layout.xml");
    final PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile1);

    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    long generation = resources.getModificationCount();
    Collection<String> layouts = resources.getItemsOfType(ResourceType.LAYOUT);
    assertEquals(1, layouts.size());
    assertTrue(resources.hasResourceItem(ResourceType.ID, "noteArea"));
    // nonExistent may be handled slightly different from other IDs, since it is introduced by a
    // non-ID-attribute="@+id/nonExistent", but the file does not define a tag with id="@id/nonExistent".
    assertTrue(resources.hasResourceItem(ResourceType.ID, "nonExistent"));

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        psiFile1.delete();
      }
    });
    assertTrue(resources.getModificationCount() > generation);
    layouts = resources.getItemsOfType(ResourceType.LAYOUT);
    assertEmpty(layouts);
    assertFalse(resources.hasResourceItem(ResourceType.ID, "noteArea"));
    assertFalse(resources.hasResourceItem(ResourceType.ID, "nonExistent"));
  }

  public void testRenameLayoutFile() throws Exception {
    final VirtualFile file2 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout2.xml");

    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    // Check renames
    //  rename layout file
    long generation = resources.getModificationCount();
    assertTrue(resources.hasResourceItem(ResourceType.LAYOUT, "layout2"));
    assertFalse(resources.hasResourceItem(ResourceType.LAYOUT, "layout2b"));

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

    assertTrue(resources.hasResourceItem(ResourceType.LAYOUT, "layout2b"));
    assertFalse(resources.hasResourceItem(ResourceType.LAYOUT, "layout2"));
    assertTrue(resources.getModificationCount() > generation);
  }

  public void testRenameDrawableFile() throws Exception {
    //  rename drawable file
    final VirtualFile file5 = myFixture.copyFileToProject(LAYOUT1, "res/drawable-xhdpi/foo2.png");
    ResourceFolderRepository resources = createRepository();

    assertTrue(resources.hasResourceItem(ResourceType.DRAWABLE, "foo2"));
    assertFalse(resources.hasResourceItem(ResourceType.DRAWABLE, "foo3"));
    ResourceItem item = getOnlyItem(resources, ResourceType.DRAWABLE, "foo2");
    assertTrue(item.getResourceValue(false) instanceof DensityBasedResourceValue);
    DensityBasedResourceValue rv = (DensityBasedResourceValue)item.getResourceValue(false);
    assertNotNull(rv);
    assertSame(Density.XHIGH, rv.getResourceDensity());

    long generation = resources.getModificationCount();
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
    assertTrue(resources.hasResourceItem(ResourceType.DRAWABLE, "foo3"));
    assertFalse(resources.hasResourceItem(ResourceType.DRAWABLE, "foo2"));
    assertTrue(resources.getModificationCount() > generation);
  }

  public void testRenameValueFile() throws Exception {
    final VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "title_template_step"));

    List<ResourceItem> items = resources.getResourceItem(ResourceType.STRING, "title_template_step");
    assertNotNull(items);
    assertEquals(1, items.size());
    ResourceItem item = items.get(0);
    assertEquals("myvalues.xml", item.getSource().getFile().getName());

    // Renaming a value file should have no visible effect

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
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "title_template_step"));
    items = resources.getResourceItem(ResourceType.STRING, "title_template_step");
    assertNotNull(items);
    assertEquals(1, items.size());
    item = items.get(0);
    assertEquals("renamedvalues.xml", item.getSource().getFile().getName());

    // TODO: Optimize this such that there's no modification change for this. It's tricky because
    // for file names we get separate notification from the old file deletion (beforePropertyChanged)
    // and the new file name (propertyChanged). (Note that I tried performing the rename via a
    // setName operation on the PsiFile instead of at the raw VirtualFile level, but the resulting
    // events were the same.)
    //assertEquals(generation, resources.getModificationCount());
  }

  public void testRenameValueFileToInvalid() throws Exception {
    final VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "title_template_step"));

    long generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          // After this rename, the values are no longer considered values since they're in an unrecognized file
          file1.rename(this, "renamedvalues.badextension");
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    assertTrue(generation < resources.getModificationCount());
    assertFalse(resources.hasResourceItem(ResourceType.STRING, "title_template_step"));
  }

  private static ResourceItem getOnlyItem(ResourceFolderRepository repository, ResourceType type, String name) {
    List<ResourceItem> item = repository.getResourceItem(type, name);
    assertNotNull(item);
    assertEquals(1, item.size());
    return item.get(0);
  }

  public void testMoveFileResourceFileToNewConfiguration() throws Exception {
    // Move a file-based resource file from one configuration to another; verify that
    // items are preserved, generation changed (since it can affect config matching),
    // and resource files updated.
    final VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout-land/layout1.xml");
    final VirtualFile file2 = myFixture.copyFileToProject(LAYOUT1, "res/layout-port/dummy.ignore");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    ResourceItem item = getOnlyItem(resources, ResourceType.LAYOUT, "layout1");
    assertEquals("land", item.getSource().getQualifiers());
    ResourceItem idItem = getOnlyItem(resources, ResourceType.ID, "btn_title_refresh");
    assertEquals("layout-land", idItem.getSource().getFile().getParentFile().getName());

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
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResourceItem(ResourceType.LAYOUT, "layout1"));
    item = getOnlyItem(resources, ResourceType.LAYOUT, "layout1");
    assertEquals("port", item.getSource().getQualifiers());
    idItem = getOnlyItem(resources, ResourceType.ID, "btn_title_refresh");
    assertEquals("layout-port", idItem.getSource().getFile().getParentFile().getName());
  }

  public void testMoveValueResourceFileToNewConfiguration() throws Exception {
    // Move a value file from one configuration to another; verify that
    // items are preserved, generation changed (since it can affect config matching),
    // and resource files updated.
    final VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values-en/layout1.xml");
    final VirtualFile file2 = myFixture.copyFileToProject(VALUES1, "res/values-no/dummy.ignore");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    ResourceItem item = getOnlyItem(resources, ResourceType.STRING, "app_name");
    assertEquals("en", item.getSource().getQualifiers());
    assertEquals("en", item.getConfiguration().getLocaleQualifier().getLanguage());
    //noinspection ConstantConditions
    assertEquals("Animations Demo", item.getResourceValue(false).getValue());

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
    assertTrue(generation < resources.getModificationCount());
    item = getOnlyItem(resources, ResourceType.STRING, "app_name");
    assertEquals("no", item.getSource().getQualifiers());
    assertEquals("no", item.getConfiguration().getLocaleQualifier().getLanguage());
    //noinspection ConstantConditions
    assertEquals("Animations Demo", item.getResourceValue(false).getValue());
  }

  public void testMoveResourceFileBetweenDensityFolders() throws Exception {
    // Regression test for https://code.google.com/p/android/issues/detail?id=61648
    // Make sure we flush resource values when reusing resource items incrementally

    // Move a file-based resource file from one configuration to another; verify that
    // items are preserved, generation changed (since it can affect config matching),
    // and resource files updated.
    final VirtualFile file1 = myFixture.copyFileToProject(DRAWABLE, "res/drawable-mdpi/picture.png");
    final VirtualFile file2 = myFixture.copyFileToProject(DRAWABLE, "res/drawable-hdpi/dummy.ignore");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    ResourceItem item = getOnlyItem(resources, ResourceType.DRAWABLE, "picture");
    ResourceFile source = item.getSource();
    assertNotNull(source);
    assertEquals("mdpi", source.getQualifiers());
    ResourceValue resourceValue = item.getResourceValue(false);
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
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResourceItem(ResourceType.DRAWABLE, "picture"));
    item = getOnlyItem(resources, ResourceType.DRAWABLE, "picture");
    source = item.getSource();
    assertNotNull(source);
    assertEquals("hdpi", source.getQualifiers());
    resourceValue = item.getResourceValue(false);
    assertNotNull(resourceValue);
    valuePath = resourceValue.getValue().replace(File.separatorChar, '/');
    assertTrue(valuePath, valuePath.endsWith("res/drawable-hdpi/picture.png"));
  }

  public void testMoveFileResourceFileToNewType() throws Exception {
    // Move a file resource file file from one folder to another, changing the type
    // (e.g. anim to animator), verify that resource types are updated
    final VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    final VirtualFile file2 = myFixture.copyFileToProject(LAYOUT1, "res/menu/dummy.ignore");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResourceItem(ResourceType.LAYOUT, "layout1"));

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
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResourceItem(ResourceType.MENU, "layout1"));
    assertFalse(resources.hasResourceItem(ResourceType.LAYOUT, "layout1"));
    ResourceItem item = getOnlyItem(resources, ResourceType.MENU, "layout1");
    assertSame(ResourceFolderType.MENU, ((PsiResourceFile)item.getSource()).getFolderType());
  }

  public void testMoveOutOfResourceFolder() throws Exception {
    // Move value files out of its resource folder; items should disappear
    final VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    final VirtualFile javaFile = myFixture.copyFileToProject(VALUES1, "src/my/pkg/Dummy.java");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "title_template_step"));

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
    assertFalse(resources.hasResourceItem(ResourceType.STRING, "title_template_step"));
  }

  public void testMoveIntoResourceFolder() throws Exception {
    // Move value files out of its resource folder; items should disappear
    final VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/dummy.ignore");
    final VirtualFile xmlFile = myFixture.copyFileToProject(VALUES1, "src/my/pkg/values.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    assertFalse(resources.hasResourceItem(ResourceType.STRING, "title_template_step"));

    final long generation = resources.getModificationCount();
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

    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "title_template_step"));
  }

  public void testReplaceResourceFile() throws Exception {
    final VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");

    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResourceItem(ResourceType.LAYOUT, "layout1"));
    assertTrue(resources.hasResourceItem(ResourceType.ID, "btn_title_refresh"));
    assertFalse(resources.hasResourceItem(ResourceType.ID, "btn_title_refresh2"));

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          myFixture.copyFileToProject(LAYOUT2, "res/layout/layout1.xml");
        }
        catch (Exception e) {
          fail(e.toString());
        }
      }
    });

    // TODO: Find out how I can work around this!
    // This doesn't work because copyFileToProject does not trigger PSI file notifications!
    //assertTrue(resources.hasResourceItem(ResourceType.ID, "btn_title_refresh2"));
    //assertFalse(resources.hasResourceItem(ResourceType.ID, "btn_title_refresh"));
    //assertTrue(generation < resources.getModificationCount());
  }

  public void testAddEmptyValueFile() throws Exception {
    myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    long generation = resources.getModificationCount();
    assertEquals(generation, resources.getModificationCount());
  }

  public void testRawFolder() throws Exception {
    // In this folder, any file extension is allowed
    myFixture.copyFileToProject(LAYOUT1, "res/raw/raw1.xml");
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    Collection<String> raw = resources.getItemsOfType(ResourceType.RAW);
    assertEquals(1, raw.size());
    long generation = resources.getModificationCount();
    final VirtualFile file2 = myFixture.copyFileToProject(LAYOUT1, "res/raw/numbers.random");
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResourceItem(ResourceType.RAW, "numbers"));
    raw = resources.getItemsOfType(ResourceType.RAW);
    assertEquals(2, raw.size());

    generation = resources.getModificationCount();
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
    assertTrue(resources.getModificationCount() > generation);
    assertTrue(resources.hasResourceItem(ResourceType.RAW, "numbers2"));
    assertFalse(resources.hasResourceItem(ResourceType.RAW, "numbers"));
  }

  public void testEditLayoutNoOp() throws Exception {
    resetScanCounter();

    // Make some miscellaneous edits in the file that have no bearing on the
    // project resources and therefore end up doing no work
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    final PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    assert(psiFile1 instanceof XmlFile);
    final XmlFile xmlFile = (XmlFile)psiFile1;
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    Collection<String> layouts = resources.getItemsOfType(ResourceType.LAYOUT);
    assertEquals(1, layouts.size());
    assertNotNull(resources.getResourceItem(ResourceType.LAYOUT, "layout1"));

    final long initial = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Insert a comment at the beginning
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
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
      }
    });
    // Inserting the comment and editing it shouldn't have had any observable results on the resource repository
    assertEquals(initial, resources.getModificationCount());

    assertTrue(resources.hasResourceItem(ResourceType.ID, "noteArea"));
    final XmlTag tag = findTagById(psiFile1, "noteArea");
    assertNotNull(tag);

    // Now insert some whitespace before a tag
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        int indentAreaBeforeTag = tag.getTextOffset() - 1;
        document.insertString(indentAreaBeforeTag, "   ");
        documentManager.commitDocument(document);
        document.deleteString(indentAreaBeforeTag, indentAreaBeforeTag + 2);
        documentManager.commitDocument(document);
      }
    });
    // Space edits outside the tag shouldn't be observable
    assertEquals(initial, resources.getModificationCount());

    // Edit text inside an element tag. No effect in value files!
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final XmlTag header = findTagById(xmlFile, "header");
        assertNotNull(header);
        int indentAreaBeforeTag = header.getSubTags()[0].getTextOffset();
        document.insertString(indentAreaBeforeTag, "   ");
        documentManager.commitDocument(document);
      }
    });
    // Space edits inside the tag shouldn't be observable
    assertEquals(initial, resources.getModificationCount());

    // Insert tag (without id) in layout file: ignored (only ids and file item matters)
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final XmlTag header = findTagById(xmlFile, "text2");
        assertNotNull(header);
        int indentAreaBeforeTag = header.getTextOffset() - 1;
        document.insertString(indentAreaBeforeTag, "<Button />");
        documentManager.commitDocument(document);
      }
    });
    // Non-id new tags shouldn't be observable
    assertEquals(initial, resources.getModificationCount());

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();

    // Finally make an edit which *does* affect the project resources to ensure
    // that document edits actually *do* fire PSI events that are digested by
    // this repository
    final String elementDeclaration = "<Button android:id=\"@+id/newid\" />\n";
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final XmlTag tag = findTagById(psiFile1, "noteArea");
        assertNotNull(tag);
        document.insertString(tag.getTextOffset() - 1, elementDeclaration);
        documentManager.commitDocument(document);
      }
    });
    assertFalse(resources.isScanPending(psiFile1));
    assertTrue(resources.hasResourceItem(ResourceType.ID, "newid"));
    assertTrue(resources.getModificationCount() > initial);

    final long generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        int startOffset = document.getText().indexOf(elementDeclaration);
        document.deleteString(startOffset, startOffset + elementDeclaration.length());
        documentManager.commitDocument(document);
      }
    });

    assertTrue(resources.isScanPending(psiFile1));
    resetScanCounter();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        ensureSingleScan();
        assertFalse(resources.hasResourceItem(ResourceType.ID, "newid"));
        assertTrue(resources.getModificationCount() > generation);
      }
    });
    UIUtil.dispatchAllInvocationEvents();
  }

  public void testEditValueFileNoOp() throws Exception {
    resetScanCounter();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    Collection<String> strings = resources.getItemsOfType(ResourceType.STRING);
    assertEquals(8, strings.size());
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "app_name"));
    assertTrue(resources.hasResourceItem(ResourceType.INTEGER, "card_flip_time_full"));

    long initial = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Edit comment header; should be a no-op
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        int offset = document.getText().indexOf("Licensed under the");
        document.insertString(offset, "This code is ");
        documentManager.commitDocument(document);
      }
    });
    assertEquals(initial, resources.getModificationCount());

    // Test edit text NOT under an item: no-op
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        int offset = document.getText().indexOf(" <item type=\"id\""); // insert BEFORE this
        document.insertString(offset, "Ignored text");
        documentManager.commitDocument(document);
      }
    });
    assertEquals(initial, resources.getModificationCount());

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testInsertNewElementWithId() throws Exception {
    resetScanCounter();

    // Make some miscellaneous edits in the file that have no bearing on the
    // project resources and therefore end up doing no work
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    final PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    assert(psiFile1 instanceof XmlFile);
    final XmlFile xmlFile = (XmlFile)psiFile1;
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    Collection<String> layouts = resources.getItemsOfType(ResourceType.LAYOUT);
    assertEquals(1, layouts.size());
    assertNotNull(resources.getResourceItem(ResourceType.LAYOUT, "layout1"));

    final long initial = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Insert tag (with an id) in layout file: should incrementally update set of ids
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final XmlTag header = findTagById(xmlFile, "text2");
        assertNotNull(header);
        int indentAreaBeforeTag = header.getTextOffset() - 1;
        document.insertString(indentAreaBeforeTag,
                              "<LinearLayout android:id=\"@+id/newid1\"><Child android:id=\"@+id/newid2\"/></LinearLayout>");
        documentManager.commitDocument(document);
      }
    });
    assertFalse(resources.isScanPending(psiFile1));
    assertTrue(initial < resources.getModificationCount());
    assertTrue(resources.hasResourceItem(ResourceType.ID, "newid1"));
    assertTrue(resources.hasResourceItem(ResourceType.ID, "newid2"));
  }

  public void testEditIdAttributeValue() throws Exception {
    resetScanCounter();
    // Edit the id attribute value of a layout item to change the set of available ids
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    Collection<String> layouts = resources.getItemsOfType(ResourceType.LAYOUT);
    assertEquals(1, layouts.size());
    assertNotNull(resources.getResourceItem(ResourceType.LAYOUT, "layout1"));

    assertTrue(resources.hasResourceItem(ResourceType.ID, "noteArea"));
    assertFalse(resources.hasResourceItem(ResourceType.ID, "note2Area"));

    long generation = resources.getModificationCount();
    final XmlTag tag = findTagById(psiFile1, "noteArea");
    assertNotNull(tag);
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        tag.setAttribute(ATTR_ID, ANDROID_URI, "@+id/note2Area");
      }
    });
    assertTrue(resources.hasResourceItem(ResourceType.ID, "note2Area"));
    assertFalse(resources.hasResourceItem(ResourceType.ID, "noteArea"));
    assertTrue(resources.getModificationCount() > generation);

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testEditIdAttributeValue2() throws Exception {
    // Edit the id attribute value: rather than by making a full value replacement,
    // perform a tiny edit on the character content; this takes a different code
    // path in the incremental updater

    resetScanCounter();
    // Edit the id attribute value of a layout item to change the set of available ids
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT1, "res/layout/layout1.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    Collection<String> layouts = resources.getItemsOfType(ResourceType.LAYOUT);
    assertEquals(1, layouts.size());
    assertNotNull(resources.getResourceItem(ResourceType.LAYOUT, "layout1"));

    assertTrue(resources.hasResourceItem(ResourceType.ID, "noteArea"));
    assertFalse(resources.hasResourceItem(ResourceType.ID, "note2Area"));

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Edit value should cause update
    long generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("noteArea");
        document.insertString(offset + 4, "2");
        documentManager.commitDocument(document);
      }
    });
    assertTrue(resources.hasResourceItem(ResourceType.ID, "note2Area"));
    assertFalse(resources.hasResourceItem(ResourceType.ID, "noteArea"));
    assertTrue(resources.getModificationCount() > generation);

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testEditValueText() throws Exception {
    resetScanCounter();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    Collection<String> strings = resources.getItemsOfType(ResourceType.STRING);
    assertEquals(8, strings.size());
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "app_name"));
    assertTrue(resources.hasResourceItem(ResourceType.INTEGER, "card_flip_time_full"));

    long generation = resources.getModificationCount();

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Edit value should cause update
    final int screenSlideOffset = document.getText().indexOf("Screen Slide");
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        document.replaceString(screenSlideOffset + 3, screenSlideOffset + 3, "e");
        documentManager.commitDocument(document);
      }
    });
    // NO revision bump yet, because the resource value hasn't been observed!
    assertEquals(generation, resources.getModificationCount());

    // Now observe it, do another edit, and see what happens
    List<ResourceItem> labelList = resources.getResourceItem(ResourceType.STRING, "title_screen_slide");
    assertNotNull(labelList);
    assertEquals(1, labelList.size());
    ResourceItem slideLabel = labelList.get(0);
    ResourceValue resourceValue = slideLabel.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("Screeen Slide", resourceValue.getValue());

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        document.deleteString(screenSlideOffset + 3, screenSlideOffset + 6);
        documentManager.commitDocument(document);
      }
    });
    assertTrue(generation < resources.getModificationCount());
    resourceValue = slideLabel.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("Scrn Slide", resourceValue.getValue());

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testNestedEditValueText() throws Exception {
    resetScanCounter();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    List<ResourceItem> labelList = resources.getResourceItem(ResourceType.STRING, "title_template_step");
    assertNotNull(labelList);
    assertEquals(1, labelList.size());
    ResourceItem label = labelList.get(0);
    ResourceValue resourceValue = label.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("Step ${step_number}: Lorem Ipsum", resourceValue.getValue());

    long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Edit value should cause update
    final int textOffset = document.getText().indexOf("Lorem");
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        document.insertString(textOffset + 1, "l");
        documentManager.commitDocument(document);
      }
    });
    assertTrue(generation < resources.getModificationCount());
    resourceValue = label.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("Step ${step_number}: Llorem Ipsum", resourceValue.getValue());

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testEditValueName() throws Exception {
    resetScanCounter();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    Collection<String> strings = resources.getItemsOfType(ResourceType.STRING);
    assertEquals(8, strings.size());
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "app_name"));

    long generation = resources.getModificationCount();

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    final int offset = document.getText().indexOf("app_name");
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        document.replaceString(offset, offset + 3, "rap");
        documentManager.commitDocument(document);
      }
    });
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "rap_name"));
    assertFalse(resources.hasResourceItem(ResourceType.STRING, "app_name"));

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testAddValue() throws Exception {
    resetScanCounter();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    Collection<String> strings = resources.getItemsOfType(ResourceType.STRING);
    assertEquals(8, strings.size());
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "app_name"));

    // Incrementally add in a new item
    final long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    final int offset = document.getText().indexOf("    <item type");
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        String firstHalf = "<string name=\"new_s";
        String secondHalf = "tring\">New String</string>";
        document.insertString(offset, firstHalf);
        documentManager.commitDocument(document);
        document.insertString(offset + firstHalf.length(), secondHalf);
        documentManager.commitDocument(document);
      }
    });

    // This currently doesn't work incrementally because we get psi events that do not contain
    // enough info to be handled incrementally, so instead we do an asynchronous update (such that
    // we can do a single update rather than rescanning the file 20 times)
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ensureSingleScan();
        assertTrue(generation < resources.getModificationCount());
        assertTrue(resources.hasResourceItem(ResourceType.STRING, "new_string"));
        //noinspection ConstantConditions
        assertEquals("New String", resources.getResourceItem(ResourceType.STRING, "new_string").get(0).getResourceValue(false).getValue());
      }
    });
  }

  public void testRemoveValue() throws Exception {
    resetScanCounter();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);

    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    Collection<String> strings = resources.getItemsOfType(ResourceType.STRING);
    assertEquals(8, strings.size());
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "app_name"));

    long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    final String textToRemove = "<string name=\"app_name\">Animations Demo</string>";
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        int offset = document.getText().indexOf(textToRemove);
        document.deleteString(offset, offset + textToRemove.length());
        documentManager.commitDocument(document);
      }
    });
    assertTrue(generation < resources.getModificationCount());
    assertFalse(resources.hasResourceItem(ResourceType.STRING, "app_name"));

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testAddIdValue() throws Exception {
    resetScanCounter();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResourceItem(ResourceType.ID, "action_next"));
    assertFalse(resources.hasResourceItem(ResourceType.ID, "action_prev"));
    assertTrue(resources.hasResourceItem(ResourceType.ID, "action_flip"));

    final long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("<item type=\"id\" name=\"action_next\" />");
        document.insertString(offset, "<item type=\"id\" name=\"action_prev\" />");
        documentManager.commitDocument(document);
      }
    });
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResourceItem(ResourceType.ID, "action_next"));
    assertTrue(resources.hasResourceItem(ResourceType.ID, "action_prev"));
    assertTrue(resources.hasResourceItem(ResourceType.ID, "action_flip"));
    ensureIncremental();
  }

  public void testRemoveIdValue() throws Exception {
    resetScanCounter();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResourceItem(ResourceType.ID, "action_next"));
    assertTrue(resources.hasResourceItem(ResourceType.ID, "action_flip"));

    final long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final String item = "<item type=\"id\" name=\"action_next\" />";
        final int offset = document.getText().indexOf(item);
        document.deleteString(offset, offset + item.length());
        documentManager.commitDocument(document);
      }
    });
    assertTrue(generation < resources.getModificationCount());
    assertFalse(resources.hasResourceItem(ResourceType.ID, "action_next"));
    assertTrue(resources.hasResourceItem(ResourceType.ID, "action_flip"));
    ensureIncremental();
  }

  public void testChangeType() throws Exception {
    resetScanCounter();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResourceItem(ResourceType.ID, "action_next"));
    assertFalse(resources.hasResourceItem(ResourceType.DIMEN, "action_next"));

    final long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    final int offset = document.getText().indexOf("\"id\" name=\"action_next\" />") + 1;
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        document.replaceString(offset, offset + 2, "dimen");
        documentManager.commitDocument(document);
      }
    });
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ensureSingleScan();
        assertTrue(generation < resources.getModificationCount());
        assertFalse(resources.hasResourceItem(ResourceType.ID, "action_next"));
        assertTrue(resources.hasResourceItem(ResourceType.DIMEN, "action_next"));
      }
    });
  }

  public void testBreakNameAttribute() throws Exception {
    resetScanCounter();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "app_name"));

    final long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    final int offset = document.getText().indexOf("name=\"app_name\">");
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        document.replaceString(offset + 2, offset + 3, "o"); // name => nome
        documentManager.commitDocument(document);
      }
    });

    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ensureSingleScan();
        assertTrue(generation < resources.getModificationCount());
        assertFalse(resources.hasResourceItem(ResourceType.STRING, "app_name"));
      }
    });
  }

  public void testChangeValueTypeByTagNameEdit() throws Exception {
    resetScanCounter();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResourceItem(ResourceType.INTEGER, "card_flip_time_half"));

    final long generation = resources.getModificationCount();
    final XmlTag tag = findTagByName(psiFile1, "card_flip_time_half");
    assertNotNull(tag);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        tag.setName("dimen"); // Change <integer> to <dimen>
      }
    });
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ensureSingleScan();

        assertTrue(generation < resources.getModificationCount());
        assertTrue(resources.hasResourceItem(ResourceType.DIMEN, "card_flip_time_half"));
        assertFalse(resources.hasResourceItem(ResourceType.INTEGER, "card_flip_time_half"));
      }
    });
  }

  public void testEditStyleName() throws Exception {
    resetScanCounter();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResourceItem(ResourceType.STYLE, "DarkTheme"));

    // Change style name
    long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("DarkTheme");
        document.replaceString(offset, offset + 4, "Light");
        documentManager.commitDocument(document);
      }
    });
    assertTrue(generation < resources.getModificationCount());
    assertFalse(resources.hasResourceItem(ResourceType.STYLE, "DarkTheme"));
    assertTrue(resources.hasResourceItem(ResourceType.STYLE, "LightTheme"));

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();

    // Change style parent
    generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("android:Theme.Holo");
        document.replaceString(offset, offset + "android:Theme.Holo".length(), "android:Theme.Light");
        documentManager.commitDocument(document);
      }
    });

    assertTrue(resources.isScanPending(psiFile1));
    final long finalGeneration = generation;
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ensureSingleScan();
        assertTrue(finalGeneration < resources.getModificationCount());
        ResourceItem style = getOnlyItem(resources, ResourceType.STYLE, "LightTheme");
        ResourceValue resourceValue = style.getResourceValue(false);
        assertNotNull(resourceValue);
        assertTrue(resourceValue instanceof StyleResourceValue);
        StyleResourceValue srv = (StyleResourceValue)resourceValue;
        assertEquals("android:Theme.Light", srv.getParentStyle());
        ResourceValue actionBarStyle = srv.getItem("actionBarStyle", true);
        assertNotNull(actionBarStyle);
        assertEquals("@style/DarkActionBar", actionBarStyle.getValue());

        // (We don't expect editing the style parent to be incremental)
      }
    });
  }

  public void testEditStyleItemText() throws Exception {
    resetScanCounter();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResourceItem(ResourceType.STYLE, "DarkTheme"));
    ResourceItem style = getOnlyItem(resources, ResourceType.STYLE, "DarkTheme");
    StyleResourceValue srv = (StyleResourceValue)style.getResourceValue(false);
    assertNotNull(srv);
    ResourceValue actionBarStyle = srv.getItem("actionBarStyle", true);
    assertNotNull(actionBarStyle);
    assertEquals("@style/DarkActionBar", actionBarStyle.getValue());

    long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("@style/DarkActionBar");
        document.replaceString(offset + 7, offset + 11, "Light");
        documentManager.commitDocument(document);
      }
    });
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResourceItem(ResourceType.STYLE, "DarkTheme"));

    style = getOnlyItem(resources, ResourceType.STYLE, "DarkTheme");
    srv = (StyleResourceValue)style.getResourceValue(false);
    assertNotNull(srv);
    actionBarStyle = srv.getItem("actionBarStyle", true);
    assertNotNull(actionBarStyle);
    assertEquals("@style/LightActionBar", actionBarStyle.getValue());

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testEditStyleItemName() throws Exception {
    resetScanCounter();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResourceItem(ResourceType.STYLE, "DarkTheme"));
    ResourceItem style = getOnlyItem(resources, ResourceType.STYLE, "DarkTheme");
    StyleResourceValue srv = (StyleResourceValue)style.getResourceValue(false);
    assertNotNull(srv);
    ResourceValue actionBarStyle = srv.getItem("actionBarStyle", true);
    assertNotNull(actionBarStyle);
    assertEquals("@style/DarkActionBar", actionBarStyle.getValue());

    long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("android:actionBarStyle");
        document.insertString(offset + 8, "in");
        documentManager.commitDocument(document);
      }
    });
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResourceItem(ResourceType.STYLE, "DarkTheme"));

    style = getOnlyItem(resources, ResourceType.STYLE, "DarkTheme");
    srv = (StyleResourceValue)style.getResourceValue(false);
    assertNotNull(srv);
    actionBarStyle = srv.getItem("inactionBarStyle", true);
    assertNotNull(actionBarStyle);
    assertEquals("@style/DarkActionBar", actionBarStyle.getValue());

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testAddStyleItem() throws Exception {
    resetScanCounter();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResourceItem(ResourceType.STYLE, "DarkActionBar"));
    ResourceItem style = getOnlyItem(resources, ResourceType.STYLE, "DarkActionBar");
    StyleResourceValue srv = (StyleResourceValue)style.getResourceValue(false);
    assertNotNull(srv);
    assertSameElements(srv.getNames(), "android:background", "android:textColor");
    ResourceValue background = srv.getItem("background", true);
    assertNotNull(background);
    assertEquals("@android:color/transparent", background.getValue());
    ResourceValue textColor = srv.getItem("textColor", true);
    assertNotNull(textColor);
    assertEquals("#008", textColor.getValue());

    long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("<item name=\"android:background\"");
        document.insertString(offset, "<item name=\"android:textSize\">20sp</item>");
        documentManager.commitDocument(document);
      }
    });
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResourceItem(ResourceType.STYLE, "DarkActionBar"));

    style = getOnlyItem(resources, ResourceType.STYLE, "DarkActionBar");
    srv = (StyleResourceValue)style.getResourceValue(false);
    assertNotNull(srv);
    assertSameElements(srv.getNames(), "android:background", "android:textSize", "android:textColor");
    background = srv.getItem("background", true);
    assertNotNull(background);
    assertEquals("@android:color/transparent", background.getValue());
    textColor = srv.getItem("textColor", true);
    assertNotNull(textColor);
    assertEquals("#008", textColor.getValue());
    ResourceValue textSize = srv.getItem("textSize", true);
    assertNotNull(textSize);
    assertEquals("20sp", textSize.getValue());

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testRemoveStyleItem() throws Exception {
    resetScanCounter();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResourceItem(ResourceType.STYLE, "DarkActionBar"));
    ResourceItem style = getOnlyItem(resources, ResourceType.STYLE, "DarkActionBar");
    StyleResourceValue srv = (StyleResourceValue)style.getResourceValue(false);
    assertNotNull(srv);
    assertSameElements(srv.getNames(), "android:background", "android:textColor");
    ResourceValue background = srv.getItem("background", true);
    assertNotNull(background);
    assertEquals("@android:color/transparent", background.getValue());
    ResourceValue textColor = srv.getItem("textColor", true);
    assertNotNull(textColor);
    assertEquals("#008", textColor.getValue());

    long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final String item = ("<item name=\"android:textColor\">#008</item>");
        final int offset = document.getText().indexOf(item);
        document.deleteString(offset, offset + item.length());
        documentManager.commitDocument(document);
      }
    });
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResourceItem(ResourceType.STYLE, "DarkActionBar"));

    style = getOnlyItem(resources, ResourceType.STYLE, "DarkActionBar");
    srv = (StyleResourceValue)style.getResourceValue(false);
    assertNotNull(srv);
    assertSameElements(srv.getNames(), "android:background");
    background = srv.getItem("background", true);
    assertNotNull(background);
    assertEquals("@android:color/transparent", background.getValue());

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testEditDeclareStyleableAttr() throws Exception {
    // Check edits of the name in a <declare-styleable> element.
    resetScanCounter();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResourceItem(ResourceType.DECLARE_STYLEABLE, "MyCustomView"));
    assertTrue(resources.hasResourceItem(ResourceType.ATTR, "watchType"));
    ResourceItem style = getOnlyItem(resources, ResourceType.DECLARE_STYLEABLE, "MyCustomView");
    DeclareStyleableResourceValue srv = (DeclareStyleableResourceValue)style.getResourceValue(false);
    assertNotNull(srv);
    assertEquals(5, srv.getAllAttributes().size());
    AttrResourceValue watchType = findAttr(srv.getAllAttributes(), "watchType");
    assertNotNull(watchType);
    assertEquals(2, watchType.getAttributeValues().size());
    assertEquals(Integer.valueOf(1), watchType.getAttributeValues().get("type_stopwatch"));
    assertEquals(Integer.valueOf(0), watchType.getAttributeValues().get("type_countdown"));
    AttrResourceValue crash = findAttr(srv.getAllAttributes(), "crash");
    assertNotNull(crash);
    assertNull(crash.getAttributeValues());

    AttrResourceValue minWidth = findAttr(srv.getAllAttributes(), "minWidth");
    assertNotNull(minWidth);
    assertFalse(resources.hasResourceItem(ResourceType.ATTR, "minWidth"));

    long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("MyCustomView");
        document.insertString(offset + 8, "er");
        documentManager.commitDocument(document);
      }
    });
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResourceItem(ResourceType.DECLARE_STYLEABLE, "MyCustomerView"));
    assertTrue(resources.hasResourceItem(ResourceType.ATTR, "watchType"));
    assertFalse(resources.hasResourceItem(ResourceType.DECLARE_STYLEABLE, "MyCustomView"));
    style = getOnlyItem(resources, ResourceType.DECLARE_STYLEABLE, "MyCustomerView");
    srv = (DeclareStyleableResourceValue)style.getResourceValue(false);
    assertNotNull(srv);
    assertEquals(5, srv.getAllAttributes().size());
    watchType = findAttr(srv.getAllAttributes(), "watchType");
    assertNotNull(watchType);
    assertEquals(2, watchType.getAttributeValues().size());
    assertEquals(Integer.valueOf(1), watchType.getAttributeValues().get("type_stopwatch"));

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testEditAttr() throws Exception {
    // Insert, remove and change <attr> attributes inside a <declare-styleable> and ensure that
    resetScanCounter();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResourceItem(ResourceType.DECLARE_STYLEABLE, "MyCustomView"));
    // Fetch resource value to ensure it gets replaced after update
    assertTrue(resources.hasResourceItem(ResourceType.ATTR, "watchType"));
    ResourceItem style = getOnlyItem(resources, ResourceType.DECLARE_STYLEABLE, "MyCustomView");
    DeclareStyleableResourceValue srv = (DeclareStyleableResourceValue)style.getResourceValue(false);
    assertNotNull(srv);
    assertEquals(5, srv.getAllAttributes().size());
    AttrResourceValue watchType = findAttr(srv.getAllAttributes(), "watchType");
    assertNotNull(watchType);
    assertEquals(2, watchType.getAttributeValues().size());
    assertEquals(Integer.valueOf(1), watchType.getAttributeValues().get("type_stopwatch"));
    assertEquals(Integer.valueOf(0), watchType.getAttributeValues().get("type_countdown"));
    AttrResourceValue crash = findAttr(srv.getAllAttributes(), "crash");
    assertNotNull(crash);
    assertNull(crash.getAttributeValues());

    final long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("watchType");
        document.insertString(offset, "w");
        documentManager.commitDocument(document);
      }
    });
    assertFalse(resources.isScanPending(psiFile1));
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResourceItem(ResourceType.DECLARE_STYLEABLE, "MyCustomView"));
    assertFalse(resources.hasResourceItem(ResourceType.ATTR, "watchType"));
    assertTrue(resources.hasResourceItem(ResourceType.ATTR, "wwatchType"));
    style = getOnlyItem(resources, ResourceType.DECLARE_STYLEABLE, "MyCustomView");
    srv = (DeclareStyleableResourceValue)style.getResourceValue(false);
    assertNotNull(srv);
    assertEquals(5, srv.getAllAttributes().size());
    watchType = findAttr(srv.getAllAttributes(), "wwatchType");
    assertNotNull(watchType);
    assertEquals(2, watchType.getAttributeValues().size());
    assertEquals(Integer.valueOf(1), watchType.getAttributeValues().get("type_stopwatch"));

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();

    // Now insert a new item and delete one and make sure we're still okay
    resetScanCounter();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        String crashAttr = "<attr name=\"crash\" format=\"boolean\" />";
        final int offset = document.getText().indexOf(crashAttr);
        document.deleteString(offset, offset + crashAttr.length());
        document.insertString(offset, "<attr name=\"newcrash\" format=\"integer\" />");
        documentManager.commitDocument(document);
      }
    });
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ensureSingleScan();
        assertTrue(generation < resources.getModificationCount());
        assertTrue(resources.hasResourceItem(ResourceType.DECLARE_STYLEABLE, "MyCustomView"));
        assertFalse(resources.hasResourceItem(ResourceType.ATTR, "watchType"));
        assertTrue(resources.hasResourceItem(ResourceType.ATTR, "wwatchType"));
        ResourceItem style = getOnlyItem(resources, ResourceType.DECLARE_STYLEABLE, "MyCustomView");
        DeclareStyleableResourceValue srv = (DeclareStyleableResourceValue)style.getResourceValue(false);
        assertNotNull(srv);
        assertEquals(5, srv.getAllAttributes().size());
        AttrResourceValue watchType = findAttr(srv.getAllAttributes(), "wwatchType");
        assertNotNull(watchType);
        assertEquals(2, watchType.getAttributeValues().size());
        assertEquals(Integer.valueOf(1), watchType.getAttributeValues().get("type_stopwatch"));
        assertEquals(Integer.valueOf(0), watchType.getAttributeValues().get("type_countdown"));
        AttrResourceValue crash = findAttr(srv.getAllAttributes(), "crash");
        assertNull(crash);
        AttrResourceValue newcrash = findAttr(srv.getAllAttributes(), "newcrash");
        assertNotNull(newcrash);
        assertNull(newcrash.getAttributeValues());
      }
    });
  }

  public void testEditDeclareStyleableFlag() throws Exception {
    // Rename, add and remove <flag> and <enum> nodes under a declare styleable and assert
    // that the declare styleable parent is updated
    resetScanCounter();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    final PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResourceItem(ResourceType.DECLARE_STYLEABLE, "MyCustomView"));
    // Fetch resource value to ensure it gets replaced after update
    assertTrue(resources.hasResourceItem(ResourceType.ATTR, "watchType"));
    assertFalse(resources.hasResourceItem(ResourceType.ATTR, "ignore_no_format"));
    final ResourceItem style = getOnlyItem(resources, ResourceType.DECLARE_STYLEABLE, "MyCustomView");
    final DeclareStyleableResourceValue srv = (DeclareStyleableResourceValue)style.getResourceValue(false);
    assertNotNull(srv);
    assertEquals(5, srv.getAllAttributes().size());
    final AttrResourceValue flagType = findAttr(srv.getAllAttributes(), "flagType");
    assertNotNull(flagType);
    assertEquals(2, flagType.getAttributeValues().size());
    assertEquals(Integer.valueOf(16), flagType.getAttributeValues().get("flag1"));
    assertEquals(Integer.valueOf(32), flagType.getAttributeValues().get("flag2"));

    final long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("flag1");
        document.insertString(offset + 1, "l");
        documentManager.commitDocument(document);
      }
    });
    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ensureSingleScan();
        assertTrue(generation < resources.getModificationCount());
        assertTrue(resources.hasResourceItem(ResourceType.DECLARE_STYLEABLE, "MyCustomView"));
        assertTrue(resources.hasResourceItem(ResourceType.ATTR, "flagType"));
        ResourceItem style = getOnlyItem(resources, ResourceType.DECLARE_STYLEABLE, "MyCustomView");
        DeclareStyleableResourceValue srv = (DeclareStyleableResourceValue)style.getResourceValue(false);
        assertNotNull(srv);
        assertEquals(5, srv.getAllAttributes().size());
        AttrResourceValue flagType = findAttr(srv.getAllAttributes(), "flagType");
        assertNotNull(flagType);
        assertEquals(2, flagType.getAttributeValues().size());
        assertNull(flagType.getAttributeValues().get("flag1"));
        assertEquals(Integer.valueOf(16), flagType.getAttributeValues().get("fllag1"));

        // Now insert a new enum and delete one and make sure we're still okay
        resetScanCounter();
        long nextGeneration = resources.getModificationCount();
        WriteCommandAction.runWriteCommandAction(null, new Runnable() {
          @Override
          public void run() {
            String enumAttr = "<enum name=\"type_stopwatch\" value=\"1\"/>";
            int offset = document.getText().indexOf(enumAttr);
            document.deleteString(offset, offset + enumAttr.length());
            String flagAttr = "<flag name=\"flag2\" value=\"0x20\"/>";
            offset = document.getText().indexOf(flagAttr);
            document.insertString(offset, "<flag name=\"flag3\" value=\"0x40\"/>");
            documentManager.commitDocument(document);
          }
        });
        assertTrue(nextGeneration < resources.getModificationCount());
        assertTrue(resources.hasResourceItem(ResourceType.DECLARE_STYLEABLE, "MyCustomView"));
        assertTrue(resources.hasResourceItem(ResourceType.ATTR, "watchType"));
        assertTrue(resources.hasResourceItem(ResourceType.ATTR, "flagType"));
        style = getOnlyItem(resources, ResourceType.DECLARE_STYLEABLE, "MyCustomView");
        srv = (DeclareStyleableResourceValue)style.getResourceValue(false);
        assertNotNull(srv);
        assertEquals(5, srv.getAllAttributes().size());
        flagType = findAttr(srv.getAllAttributes(), "flagType");
        assertNotNull(flagType);
        assertEquals(3, flagType.getAttributeValues().size());
        assertEquals(Integer.valueOf(16), flagType.getAttributeValues().get("fllag1"));
        assertEquals(Integer.valueOf(32), flagType.getAttributeValues().get("flag2"));
        assertEquals(Integer.valueOf(64), flagType.getAttributeValues().get("flag3"));

        AttrResourceValue watchType = findAttr(srv.getAllAttributes(), "watchType");
        assertNotNull(watchType);
        assertEquals(1, watchType.getAttributeValues().size());
        assertEquals(Integer.valueOf(0), watchType.getAttributeValues().get("type_countdown"));
      }
    });
  }

  public void testEditPluralItems() throws Exception {
    resetScanCounter();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    // Test that our tools:quantity works correctly for getResourceValue()
    assertTrue(resources.hasResourceItem(ResourceType.PLURALS, "my_plural"));
    ResourceItem plural = getOnlyItem(resources, ResourceType.PLURALS, "my_plural");
    ResourceValue resourceValue = plural.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("@string/hello_two", resourceValue.getValue());

    // TODO: It would be nice to avoid updating the generation if you
    // edit a different item than the one being picked (default or via
    // tools:quantity) but for now we're not worrying about that optimization

    long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("@string/hello_two");
        document.replaceString(offset + 9, offset + 10, "a");
        documentManager.commitDocument(document);
      }
    });
    plural = getOnlyItem(resources, ResourceType.PLURALS, "my_plural");
    resourceValue = plural.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("@string/hallo_two", resourceValue.getValue());
    assertTrue(generation < resources.getModificationCount());

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testAddPluralItems() throws Exception {
    resetScanCounter();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResourceItem(ResourceType.PLURALS, "my_plural"));
    ResourceItem plural = getOnlyItem(resources, ResourceType.PLURALS, "my_plural");
    ResourceValue resourceValue = plural.getResourceValue(false);
    assertNotNull(resourceValue);
    assertInstanceOf(resourceValue, PluralsResourceValue.class);
    PluralsResourceValue prv = (PluralsResourceValue)resourceValue;
    assertEquals(3, prv.getPluralsCount());
    assertEquals("@string/hello_two", resourceValue.getValue());
    assertEquals("two", prv.getQuantity(1));

    long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("<item quantity=\"two\">@string/hello_two");
        document.insertString(offset, "<item quantity=\"one_and_half\">@string/hello_one_and_half</item>");
        documentManager.commitDocument(document);
      }
    });
    plural = getOnlyItem(resources, ResourceType.PLURALS, "my_plural");
    resourceValue = plural.getResourceValue(false);
    assertNotNull(resourceValue);
    assertInstanceOf(resourceValue, PluralsResourceValue.class);
    prv = (PluralsResourceValue)resourceValue;
    assertEquals(4, prv.getPluralsCount());
    assertEquals("@string/hello_two", resourceValue.getValue());
    assertEquals("one_and_half", prv.getQuantity(1));
    assertTrue(generation < resources.getModificationCount());

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testRemovePluralItems() throws Exception {
    resetScanCounter();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResourceItem(ResourceType.PLURALS, "my_plural"));
    ResourceItem plural = getOnlyItem(resources, ResourceType.PLURALS, "my_plural");
    ResourceValue resourceValue = plural.getResourceValue(false);
    assertNotNull(resourceValue);
    assertInstanceOf(resourceValue, PluralsResourceValue.class);
    PluralsResourceValue prv = (PluralsResourceValue)resourceValue;
    assertEquals(3, prv.getPluralsCount());
    assertEquals("@string/hello_two", resourceValue.getValue());
    assertEquals("one", prv.getQuantity(0));

    long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final String item = "<item quantity=\"one\">@string/hello</item>";
        final int offset = document.getText().indexOf(item);
        document.deleteString(offset, offset + item.length());
        documentManager.commitDocument(document);
      }
    });
    plural = getOnlyItem(resources, ResourceType.PLURALS, "my_plural");
    resourceValue = plural.getResourceValue(false);
    assertNotNull(resourceValue);
    assertInstanceOf(resourceValue, PluralsResourceValue.class);
    prv = (PluralsResourceValue)resourceValue;
    assertEquals(2, prv.getPluralsCount());
    assertEquals("@string/hello_two", resourceValue.getValue());
    assertEquals("two", prv.getQuantity(0));
    assertTrue(generation < resources.getModificationCount());

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testEditArrayItemText() throws Exception {
    resetScanCounter();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    // Test that our tools:index and fallback handling for arrays works correctly
    // for getResourceValue()
    assertTrue(resources.hasResourceItem(ResourceType.ARRAY, "security_questions"));
    ResourceItem array = getOnlyItem(resources, ResourceType.ARRAY, "security_questions");
    ResourceValue resourceValue = array.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("Question 4", resourceValue.getValue());

    assertTrue(resources.hasResourceItem(ResourceType.ARRAY, "integers"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "integers");
    resourceValue = array.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("10", resourceValue.getValue());

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("Question 4");
        document.insertString(offset, "Q");
        documentManager.commitDocument(document);
      }
    });

    assertTrue(resources.hasResourceItem(ResourceType.ARRAY, "security_questions"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("QQuestion 4", resourceValue.getValue());

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testAddStringArrayItemElements() throws Exception {
    resetScanCounter();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    assertTrue(resources.hasResourceItem(ResourceType.ARRAY, "security_questions"));
    ResourceItem array = getOnlyItem(resources, ResourceType.ARRAY, "security_questions");
    ResourceValue resourceValue = array.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("Question 4", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    ArrayResourceValue arv = (ArrayResourceValue)resourceValue;
    assertEquals(5, arv.getElementCount());
    assertEquals("Question 2", arv.getElement(1));
    assertEquals("Question 3", arv.getElement(2));
    assertEquals("Question 4", arv.getElement(3));

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("<item>Question 3</item>");
        document.insertString(offset, "<item>Question 2.5</item>");
        documentManager.commitDocument(document);
      }
    });

    assertTrue(resources.hasResourceItem(ResourceType.ARRAY, "security_questions"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("Question 3", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(6, arv.getElementCount());
    assertEquals("Question 2", arv.getElement(1));
    assertEquals("Question 2.5", arv.getElement(2));
    assertEquals("Question 3", arv.getElement(3));

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testRemoveStringArrayItemElements() throws Exception {
    resetScanCounter();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    assertTrue(resources.hasResourceItem(ResourceType.ARRAY, "security_questions"));
    ResourceItem array = getOnlyItem(resources, ResourceType.ARRAY, "security_questions");
    ResourceValue resourceValue = array.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("Question 4", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    ArrayResourceValue arv = (ArrayResourceValue)resourceValue;
    assertEquals(5, arv.getElementCount());

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        String elementString = "<item>Question 3</item>";
        final int offset = document.getText().indexOf(elementString);
        document.deleteString(offset, offset + elementString.length());
        documentManager.commitDocument(document);
      }
    });

    assertTrue(resources.hasResourceItem(ResourceType.ARRAY, "security_questions"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "security_questions");
    resourceValue = array.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("Question 5", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(4, arv.getElementCount());

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testAddIntegerArrayItemElements() throws Exception {
    resetScanCounter();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    assertTrue(resources.hasResourceItem(ResourceType.ARRAY, "integers"));
    ResourceItem array = getOnlyItem(resources, ResourceType.ARRAY, "integers");
    ResourceValue resourceValue = array.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("10", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    ArrayResourceValue arv = (ArrayResourceValue)resourceValue;
    assertEquals(2, arv.getElementCount());
    assertEquals("10", arv.getElement(0));
    assertEquals("20", arv.getElement(1));

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("<item>10</item>");
        document.insertString(offset, "<item>5</item>");
        documentManager.commitDocument(document);
      }
    });

    assertTrue(resources.hasResourceItem(ResourceType.ARRAY, "integers"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "integers");
    resourceValue = array.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("5", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(3, arv.getElementCount());
    assertEquals("5", arv.getElement(0));
    assertEquals("10", arv.getElement(1));
    assertEquals("20", arv.getElement(2));

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testRemoveIntegerArrayItemElements() throws Exception {
    resetScanCounter();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    assertTrue(resources.hasResourceItem(ResourceType.ARRAY, "integers"));
    ResourceItem array = getOnlyItem(resources, ResourceType.ARRAY, "integers");
    ResourceValue resourceValue = array.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("10", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    ArrayResourceValue arv = (ArrayResourceValue)resourceValue;
    assertEquals(2, arv.getElementCount());
    assertEquals("10", arv.getElement(0));
    assertEquals("20", arv.getElement(1));

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final String item = "<item>10</item>";
        final int offset = document.getText().indexOf(item);
        document.deleteString(offset, offset + item.length());
        documentManager.commitDocument(document);
      }
    });

    assertTrue(resources.hasResourceItem(ResourceType.ARRAY, "integers"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "integers");
    resourceValue = array.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("20", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(1, arv.getElementCount());

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testAddTypedArrayItemElements() throws Exception {
    resetScanCounter();
    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    assertTrue(resources.hasResourceItem(ResourceType.ARRAY, "my_colors"));
    ResourceItem array = getOnlyItem(resources, ResourceType.ARRAY, "my_colors");
    ResourceValue resourceValue = array.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("#FFFF0000", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    ArrayResourceValue arv = (ArrayResourceValue)resourceValue;
    assertEquals(3, arv.getElementCount());
    assertEquals("#FFFF0000", arv.getElement(0));
    assertEquals("#FF00FF00", arv.getElement(1));
    assertEquals("#FF0000FF", arv.getElement(2));

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("<item>#FFFF0000</item>");
        document.insertString(offset, "<item>#FFFFFF00</item>");
        documentManager.commitDocument(document);
      }
    });

    assertTrue(resources.hasResourceItem(ResourceType.ARRAY, "my_colors"));
    array = getOnlyItem(resources, ResourceType.ARRAY, "my_colors");
    resourceValue = array.getResourceValue(false);
    assertNotNull(resourceValue);
    assertEquals("#FFFFFF00", resourceValue.getValue());
    assertTrue(resourceValue instanceof ArrayResourceValue);
    arv = (ArrayResourceValue)resourceValue;
    assertEquals(4, arv.getElementCount());
    assertEquals("#FFFFFF00", arv.getElement(0));
    assertEquals("#FFFF0000", arv.getElement(1));
    assertEquals("#FF00FF00", arv.getElement(2));

    // Shouldn't have done any full file rescans during the above edits
    ensureIncremental();
  }

  public void testGradualEdits() throws Exception {
    resetScanCounter();

    // Gradually type in the contents of a value file and make sure we end up with a valid view of the world
    VirtualFile file1 = myFixture.copyFileToProject(VALUES_EMPTY, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    final long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        document.deleteString(0, document.getTextLength());
        documentManager.commitDocument(document);
      }
    });

    final String contents =
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
      final int offset = i;
      final char character = contents.charAt(i);
      WriteCommandAction.runWriteCommandAction(null, new Runnable() {
        @Override
        public void run() {
          document.insertString(offset, String.valueOf(character));
          documentManager.commitDocument(document);
        }
      });
    }

    assertTrue(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        ensureSingleScan();
        assertTrue(generation < resources.getModificationCount());
        assertTrue(resources.hasResourceItem(ResourceType.STYLE, "DarkActionBar"));

        assertTrue(resources.hasResourceItem(ResourceType.STYLE, "DarkActionBar"));
        ResourceItem style = getOnlyItem(resources, ResourceType.STYLE, "DarkActionBar");
        StyleResourceValue srv = (StyleResourceValue)style.getResourceValue(false);
        assertNotNull(srv);
        ResourceValue actionBarStyle = srv.getItem("background", true);
        assertNotNull(actionBarStyle);
        assertEquals("@android:color/transparent", actionBarStyle.getValue());
        //noinspection ConstantConditions
        assertEquals("Zoom", getOnlyItem(resources, ResourceType.STRING, "title_zoom").getResourceValue(false).getValue());
      }
    });
  }

  public void testIssue56799() throws Exception {
    // Test deleting a string; ensure that the whole repository is updated correctly.
    // Regression test for
    //   https://code.google.com/p/android/issues/detail?id=56799
    VirtualFile file1 = myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "app_name"));
    assertFalse(resources.hasResourceItem(ResourceType.STRING, "app_name2"));
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "hello_world"));

    final long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        String string = "    <string name=\"hello_world\">Hello world!</string>";
        final int offset = document.getText().indexOf(string);
        assertTrue(offset != -1);
        document.deleteString(offset, offset + string.length());
        documentManager.commitDocument(document);
      }
    });

    assertFalse(resources.isScanPending(psiFile1));
    assertTrue(generation < resources.getModificationCount());
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "app_name"));
    assertFalse(resources.hasResourceItem(ResourceType.STRING, "hello_world"));
  }

  public void testEditXmlProcessingInstructionAttr() throws Exception {
    // Test editing an attribute in the XML prologue
    VirtualFile file1 = myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "app_name"));
    assertFalse(resources.hasResourceItem(ResourceType.STRING, "app_name2"));
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "hello_world"));


    final long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        String string = "utf-8";
        final int offset = document.getText().indexOf(string);
        assertTrue(offset != -1);
        document.insertString(offset, "t");
        documentManager.commitDocument(document);
      }
    });

    // Edits in XML processing instructions have no effect on the resource repository
    assertEquals(generation, resources.getModificationCount());
    assertFalse(resources.isScanPending(psiFile1));
    UIUtil.dispatchAllInvocationEvents();
  }

  public void testIssue64239() throws Exception {
    // If you duplicate a string, then change its contents (which still duplicated),
    // and then finally rename the string, then the value of the second clone will
    // continue to be referred from the first string:
    //    <string name="foo">value 1</foo>
    //    <string name="foo">value 2</foo>
    // then change the second string name to
    //    <string name="foo2">value 2</foo>
    // If you now evaluate the value of foo, you get "value 1". Basically while the
    // two strings are (illegally) aliasing, the value of the first string is replaced.

    // TODO: Test both *duplicating* a node, as well as manually typing in a brand
    // new one with the same result

    VirtualFile file1 = myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "app_name"));
    //noinspection ConstantConditions
    assertEquals("My Application 574",
                 resources.getResourceItem(ResourceType.STRING, "app_name").get(0).getResourceValue(false).getValue());

    final long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    final int offset = document.getText().indexOf("</resources>");
    assertTrue(offset != -1);
    final String string = "<string name=\"app_name\">New Value</string>";

    // First duplicate the line:
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        document.insertString(offset, string);
        documentManager.commitDocument(document);
      }
    });

    assertTrue(generation < resources.getModificationCount());
    resetScanCounter();
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "app_name"));

    // Then replace the name of the duplicated string
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        int startOffset = offset + "<string name=\"".length();
        document.replaceString(startOffset, startOffset + "app_name".length(), "new_name");
        documentManager.commitDocument(document);
      }
    });

    assertFalse(resources.isScanPending(psiFile1));
    assertTrue(generation < resources.getModificationCount());
    resetScanCounter();
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "app_name"));
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "new_name"));

    //noinspection ConstantConditions
    assertEquals("New Value",
                 resources.getResourceItem(ResourceType.STRING, "new_name").get(0).getResourceValue(false).getValue());
    //noinspection ConstantConditions
    assertEquals("My Application 574",
                 resources.getResourceItem(ResourceType.STRING, "app_name").get(0).getResourceValue(false).getValue());
  }

  public void testIdScanFromLayout() throws Exception {
    // Test for http://b.android.com/172239
    myFixture.copyFileToProject(LAYOUT_ID_SCAN, "res/layout/layout1.xml");
    ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    Collection<String> ids = resources.getItemsOfType(ResourceType.ID);
    assertNotNull(ids);
    assertContainsElements(ids, "header", "image", "styledView", "imageView", "imageView2", "imageButton", "nonExistent");
  }

  public void testSync() throws Exception {
    // Regression test for https://code.google.com/p/android/issues/detail?id=79629
    // Ensure that sync() handles rescanning immediately
    VirtualFile file1 = myFixture.copyFileToProject(STRINGS, "res/values/strings.xml");
    final PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "app_name"));
    assertFalse(resources.hasResourceItem(ResourceType.STRING, "app_name2"));
    assertTrue(resources.hasResourceItem(ResourceType.STRING, "hello_world"));

    final long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        // The sync() call must be called from the dispatch thread
        WriteCommandAction.runWriteCommandAction(null, new Runnable() {
          @Override
          public void run() {
            String string = "    <string name=\"hello_world\">Hello world!</string>";
            final int offset = document.getText().indexOf(string);
            assertTrue(offset != -1);

            // Simulate an edit event that triggers the incremental updater to
            // give up and schedule a subsequent update instead. (This used to be
            // the case here, but as of IntelliJ 14.1 it's now delivering more
            // accurate events for the below edits, which made the sync-test
            // fail because it would already have correct results *before* the
            // sync. Therefore, we simply trigger a pending scan (which stops
            // subequent incremental events from being processed).
            resources.rescan(psiFile1, ResourceFolderType.VALUES);

            document.deleteString(offset, offset + string.length());
            documentManager.commitDocument(document);
          }
        });

        // The strings file contains definitions for app_name, action_settings and hello_world.
        // We've manually deleted the hello_world string. We now check that app_name remains
        // in the resource set, and that hello_world is removed. (We check that hello_world
        // is there before the sync, and gone after.)
        assertTrue(resources.isScanPending(psiFile1));
        assertTrue(resources.hasResourceItem(ResourceType.STRING, "app_name"));
        assertTrue(resources.hasResourceItem(ResourceType.STRING, "hello_world"));

        assertTrue(ApplicationManager.getApplication().isDispatchThread());
        resources.sync();
        assertTrue(generation < resources.getModificationCount());
        assertTrue(resources.hasResourceItem(ResourceType.STRING, "app_name"));
        assertFalse(resources.hasResourceItem(ResourceType.STRING, "hello_world"));
        assertFalse(resources.isScanPending(psiFile1));
      }
    });
  }

  public void testDataBindingVariables() {
    VirtualFile file1 = myFixture.copyFileToProject(LAYOUT_WITH_DATA_BINDING, "res/layout/layout_with_data_binding.xml");
    final PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);
    AndroidFacet facet = resources.getFacet();
    assertEquals(1, resources.getDataBindingResourceFiles().size());
    final String appPackage = DataBindingUtil.getGeneratedPackageName(facet);
    final DataBindingInfo info = resources.getDataBindingResourceFiles().get(appPackage + ".databinding.LayoutWithDataBindingBinding");
    assertNotNull(info);
    List<PsiDataBindingResourceItem> variables = info.getItems(DataBindingResourceType.VARIABLE);
    assertEquals(1, variables.size());
    final PsiDataBindingResourceItem variable1 = variables.get(0);
    assertEquals("variable1", variable1.getName());
    assertEquals("String", variable1.getExtra(SdkConstants.ATTR_TYPE));
    assertNotNull(variable1.getXmlTag());

    List<PsiDataBindingResourceItem> imports = new ArrayList<PsiDataBindingResourceItem>();// clone to be able to sort
    imports.addAll(info.getItems(DataBindingResourceType.IMPORT));
    assertEquals(2, imports.size());

    Collections.sort(imports, new Comparator<PsiDataBindingResourceItem>() {
      @Override
      public int compare(PsiDataBindingResourceItem item1, PsiDataBindingResourceItem item2) {
        return item1.getExtra(SdkConstants.ATTR_TYPE).compareTo(item2.getExtra(SdkConstants.ATTR_TYPE));
      }
    });

    PsiDataBindingResourceItem import1 = imports.get(0);
    assertEquals("p1.p2.import1", import1.getExtra(SdkConstants.ATTR_TYPE));
    assertNull(import1.getExtra(SdkConstants.ATTR_ALIAS));
    assertNotNull(import1.getXmlTag());

    PsiDataBindingResourceItem import2 = imports.get(1);
    assertEquals("p1.p2.import2", import2.getExtra(SdkConstants.ATTR_TYPE));
    assertEquals("i2", import2.getExtra(SdkConstants.ATTR_ALIAS));
    assertNotNull(import2.getXmlTag());

    List<DataBindingInfo.ViewWithId> viewsWithIds = info.getViewsWithIds();
    assertEquals(6, viewsWithIds.size());
    Collections.sort(viewsWithIds, new Comparator<DataBindingInfo.ViewWithId>() {
      @Override
      public int compare(DataBindingInfo.ViewWithId v1, DataBindingInfo.ViewWithId v2) {
        return v1.name.compareTo(v2.name);
      }
    });
    validateViewWithId(facet, viewsWithIds.get(0), "foo.bar.Magic", "magicView");
    validateViewWithId(facet, viewsWithIds.get(1), "android.view.View", "normalViewTag");
    validateViewWithId(facet, viewsWithIds.get(2), "android.view.SurfaceView", "surfaceView1");
    validateViewWithId(facet, viewsWithIds.get(3), "android.widget.TextView", "textView1");
    validateViewWithId(facet, viewsWithIds.get(4), "android.view.ViewGroup", "viewTag");
    validateViewWithId(facet, viewsWithIds.get(5), "android.webkit.WebView", "webView1");
  }

  public void testEditColorStateList() throws Exception {
    resetScanCounter();

    VirtualFile file1 = myFixture.copyFileToProject(COLOR_STATELIST, "res/color/my_state_list.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    assertTrue(resources.hasResourceItem(ResourceType.COLOR, "my_state_list"));

    // Edit comment
    long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf(" -->");
        document.replaceString(offset, offset, "more comment");
        documentManager.commitDocument(document);
      }
    });

    // Shouldn't have caused any change
    assertTrue(generation == resources.getModificationCount());
    ensureIncremental();

    // Edit processing instruction
    generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("utf-8");
        document.replaceString(offset, offset + 5, "other encoding");
        documentManager.commitDocument(document);
      }
    });

    // Shouldn't have caused any change
    assertTrue(generation == resources.getModificationCount());
    ensureIncremental();

    // Edit state list
    generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("myColor");
        document.replaceString(offset, offset + 7, "myNewColor");
        documentManager.commitDocument(document);
      }
    });

    // Should have caused a modification but not a rescan
    assertTrue(generation < resources.getModificationCount());
    ensureIncremental();
  }

  /**
   * Check that whitespace edits do not trigger a rescan
   */
  public void testEmptyEdits() throws Exception {
    resetScanCounter();

    VirtualFile file1 = myFixture.copyFileToProject(VALUES1, "res/values/myvalues.xml");
    PsiFile psiFile1 = PsiManager.getInstance(getProject()).findFile(file1);
    assertNotNull(psiFile1);
    final ResourceFolderRepository resources = createRepository();
    assertNotNull(resources);

    long generation = resources.getModificationCount();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile1);
    assertNotNull(document);

    // Add a space to an attribute name.
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("app_name");
        document.insertString(offset, " ");
        documentManager.commitDocument(document);
      }
    });
    assertFalse(resources.isScanPending(psiFile1));
    assertTrue(generation < resources.getModificationCount());
    generation = resources.getModificationCount();

    ResourceItem item = getOnlyItem(resources, ResourceType.STRING, "title_zoom");
    //noinspection ConstantConditions
    assertEquals("Zoom", item.getResourceValue(false).getValue());
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("Zoom");
        document.deleteString(offset, offset + "Zoom".length());
        documentManager.commitDocument(document);
      }
    });
    assertFalse(resources.isScanPending(psiFile1));
    assertTrue(generation < resources.getModificationCount());

    // Inserting spaces in the middle of a tag shouldn't trigger a rescan or even change the modification count
    generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        final int offset = document.getText().indexOf("Card Flip");
        document.insertString(offset, "   ");
        documentManager.commitDocument(document);
      }
    });
    assertFalse(resources.isScanPending(psiFile1));
    assertEquals(generation, resources.getModificationCount());

    ensureIncremental();
  }

  private void validateViewWithId(AndroidFacet facet, DataBindingInfo.ViewWithId viewWithId, String qualified, String variableName) {
    assertTrue(DataBindingUtil.resolveViewPsiType(viewWithId, facet).equalsToText(qualified));
    assertEquals(variableName, viewWithId.name);
  }

  @Nullable
  private static XmlTag findTagById(@NotNull PsiFile file, @NotNull String id) {
    assertFalse(id.startsWith(PREFIX_RESOURCE_REF)); // just the id
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
  private static XmlTag findTagByName(@NotNull PsiFile file, @NotNull String name) {
    for (XmlTag tag : PsiTreeUtil.findChildrenOfType(file, XmlTag.class)) {
      String tagName = tag.getAttributeValue(ATTR_NAME);
      if (name.equals(tagName)) {
        return tag;
      }
    }
    return null;
  }

  @Nullable
  private static AttrResourceValue findAttr(@NotNull List<AttrResourceValue> attrs, @NotNull String name) {
    for (AttrResourceValue attr : attrs) {
      if (attr.getName().equals(name)) {
        return attr;
      }
    }
    return null;
  }
}
