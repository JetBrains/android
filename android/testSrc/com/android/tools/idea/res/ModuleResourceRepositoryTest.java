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

import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.tools.idea.res.ResourcesTestsUtil.getSingleItem;
import static com.android.tools.idea.testing.AndroidTestUtils.waitForUpdates;
import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepositoryUtil;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Lint;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.util.containers.ContainerUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

/** Tests for {@link ModuleResourceRepository}. */
@SuppressWarnings("SpellCheckingInspection")
public class ModuleResourceRepositoryTest extends AndroidTestCase {
  private static final String LAYOUT = "resourceRepository/layout.xml";
  private static final String LAYOUT_OVERLAY = "resourceRepository/layoutOverlay.xml";
  private static final String LAYOUT_IDS_1 = "resourceRepository/layout_ids1.xml";
  private static final String LAYOUT_IDS_2 = "resourceRepository/layout_ids2.xml";
  private static final String VALUES = "resourceRepository/values.xml";
  private static final String VALUES_OVERLAY1 = "resourceRepository/valuesOverlay1.xml";
  private static final String VALUES_OVERLAY2 = "resourceRepository/valuesOverlay2.xml";
  private static final String VALUES_OVERLAY2_NO = "resourceRepository/valuesOverlay2No.xml";

  public void testStable() {
    assertSame(StudioResourceRepositoryManager.getModuleResources(myFacet), StudioResourceRepositoryManager.getModuleResources(myFacet));
    assertSame(StudioResourceRepositoryManager.getModuleResources(myFacet), StudioResourceRepositoryManager.getModuleResources(myModule));
  }

  public void testOverlays() {
    myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT_OVERLAY, "res2/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT_IDS_1, "res2/layout/layout_ids1.xml");
    myFixture.copyFileToProject(LAYOUT_IDS_2, "res2/layout/layout_ids2.xml");
    VirtualFile res1 = myFixture.copyFileToProject(VALUES_OVERLAY2, "res1/values/nameDoesNotMatter.xml").getParent().getParent();
    VirtualFile res2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "res2/values/values.xml").getParent().getParent();
    VirtualFile res3 = myFixture.copyFileToProject(VALUES, "res/values/values.xml").getParent().getParent();
    myFixture.copyFileToProject(VALUES_OVERLAY2_NO, "res1/values-no/values.xml");

    assertNotSame(res2, res1);
    assertNotSame(res3, res2);
    assertNotSame(res3, res1);

    ModuleResourceRepository resources =
        ModuleResourceRepository.createForTest(myFacet, ImmutableList.of(res1, res2, res3), RES_AUTO, null);

    // Check that values are handled correctly. First a plain value (not overridden anywhere).
    assertStringIs(resources, "title_layout_changes", "Layout Changes");

    // Check that an overridden key (overridden in just one flavor) is picked up
    assertStringIs(resources, "title_crossfade", "Complex Crossfade"); // Overridden in res2
    assertStringIs(resources, "title_zoom", "Zoom!"); // Overridden in res1

    // Make sure that new/unique strings from flavors are available
    assertStringIs(resources, "unique_string", "Unique"); // Overridden in res2
    assertStringIs(resources, "another_unique_string", "Another Unique", res -> res.getConfiguration().isDefault()); // Overridden in res1

    // Check that an overridden key (overridden in multiple flavors) picks the last one.
    // res1 (not unique because we have a values-no item too)
    assertStringIs(resources, "app_name", "Very Different App Name", item -> item.getConfiguration().isDefault());

    // Layouts: Should only be offered id's from the overriding layout (plus those defined in values.xml).
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_next")); // from values.xml
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "noteArea")); // from res2 layout1.xml

    // Layout masking does not currently work. I'm not 100% certain what the intended behavior is
    // here (e.g. res3's layout1 contains @+id/button1, res2's layout1 does not; should @+id/button1 be visible?)
    //assertFalse(resources.hasResourceItem(ResourceType.ID, "btn_title_refresh")); // masked in res3 by res2's layout replacement

    // Check that localized lookup (qualifier matching works)
    List<ResourceItem> stringList = resources.getResources(RES_AUTO, ResourceType.STRING, "another_unique_string");
    assertNotNull(stringList);
    assertSize(2, stringList);
    FolderConfiguration valueConfig = FolderConfiguration.getConfigForFolder("values-no");
    assertNotNull(valueConfig);
    ResourceValue stringValue = ResourceRepositoryUtil.getConfiguredResources(resources, RES_AUTO, ResourceType.STRING, valueConfig)
                                                      .get("another_unique_string");
    assertNotNull(stringValue);
    assertEquals("En Annen", stringValue.getValue());

    // Change flavor order and make sure things are updated and work correctly.
    resources.updateRoots(ImmutableList.of(res2, res1, res3));

    // Should now be picking app_name from res2 rather than res1 since it's now first.
    assertStringIs(resources, "app_name", "Different App Name", res -> res.getConfiguration().isDefault()); // res2

    // Sanity check other merging
    assertStringIs(resources, "title_layout_changes", "Layout Changes");
    assertStringIs(resources, "title_crossfade", "Complex Crossfade"); // Overridden in res2
    assertStringIs(resources, "title_zoom", "Zoom!"); // Overridden in res1
    assertStringIs(resources, "unique_string", "Unique"); // Overridden in res2
    assertStringIs(resources, "another_unique_string", "Another Unique", res -> res.getConfiguration().isDefault()); // Overridden in res1

    // Hide a resource root (res2)
    resources.updateRoots(ImmutableList.of(res1, res3));

    // No longer aliasing the main layout
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "btn_title_refresh")); // res3 layout1.xml
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "noteArea")); // from res3 layout1.xml
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.ID, "action_next")); // from values.xml

    assertStringIs(resources, "title_crossfade", "Simple Crossfade"); // No longer overridden in res2

    // Finally ensure that we can switch roots repeatedly (had some earlier bugs related to root unregistration).
    resources.updateRoots(ImmutableList.of(res2, res1, res3));
    resources.updateRoots(ImmutableList.of(res3));
    resources.updateRoots(ImmutableList.of(res2, res1, res3));
    resources.updateRoots(ImmutableList.of(res3));
    resources.updateRoots(ImmutableList.of(res2, res1, res3));
    resources.updateRoots(ImmutableList.of(res2));
    resources.updateRoots(ImmutableList.of(res3));
    resources.updateRoots(ImmutableList.of(res1, res2, res3));
    assertStringIs(resources, "title_layout_changes", "Layout Changes");

    // Make sure I get all the resource ids (there can be multiple; these are not replaced via overlays)
    List<ResourceItem> ids = resources.getResources(RES_AUTO, ResourceType.ID, "my_id");
    assertNotNull(ids);
    assertSize(2, ids);
    List<ResourceItem> sorted = new ArrayList<>(ids);
    sorted.sort(Comparator.comparing(item -> item.getSource().getFileName()));
    //noinspection ConstantConditions
    assertEquals("layout_ids1.xml", sorted.get(0).getSource().getFileName());
    //noinspection ConstantConditions
    assertEquals("layout_ids2.xml", sorted.get(1).getSource().getFileName());
  }

  public void testOverlayUpdates1() throws Exception {
    VirtualFile layout = myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");
    VirtualFile layoutOverlay = myFixture.copyFileToProject(LAYOUT_OVERLAY, "res2/layout/layout1.xml");
    VirtualFile res1 = myFixture.copyFileToProject(VALUES_OVERLAY2, "res1/values/nameDoesNotMatter.xml").getParent().getParent();
    VirtualFile res2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "res2/values/values.xml").getParent().getParent();
    VirtualFile res3 = myFixture.copyFileToProject(VALUES, "res/values/values.xml").getParent().getParent();
    myFixture.copyFileToProject(VALUES_OVERLAY2_NO, "res1/values-no/values.xml");
    ModuleResourceRepository resources =
        ModuleResourceRepository.createForTest(myFacet, ImmutableList.of(res1, res2, res3), RES_AUTO, null);
    assertStringIs(resources, "title_layout_changes", "Layout Changes"); // sanity check

    // Layout resource check:
    // Check that our @/layout/layout1 resource currently refers to res2 override,
    // then rename it to @layout/layout2, and verify that we have both, and then
    // rename base to @layout/layout2 and verify that we are back to overriding.

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout2"));
    ResourceItem layout1 = getSingleItem(resources, ResourceType.LAYOUT, "layout1");
    assertItemIsInDir(res2, layout1);

    long generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          layoutOverlay.rename(this, "layout2.xml");
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    waitForUpdates(resources);
    assertTrue(resources.getModificationCount() > generation);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout2"));
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));

    // Layout should now be coming through from res3 since res2 is no longer overriding it
    layout1 = getSingleItem(resources, ResourceType.LAYOUT, "layout1");
    assertItemIsInDir(res3, layout1);

    ResourceItem layout2 = getSingleItem(resources, ResourceType.LAYOUT, "layout2");
    assertItemIsInDir(res2, layout2);

    // Now rename layout1 to layout2 to hide it again
    generation = resources.getModificationCount();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          layout.rename(this, "layout2.xml");
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    waitForUpdates(resources);
    assertTrue(resources.getModificationCount() > generation);

    assertTrue(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout2"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "layout1"));

    layout2 = getSingleItem(resources, ResourceType.LAYOUT, "layout2");
    assertItemIsInDir(res2, layout2);
  }

  public void testOverlayUpdates2() throws Exception {
    // Like testOverlayUpdates1, but rather than testing changes to layout resources (file-based resource)
    // perform document edits in value-documents

    myFixture.copyFileToProject(LAYOUT, "res/layout/layout1.xml");
    myFixture.copyFileToProject(LAYOUT_OVERLAY, "res2/layout/layout1.xml");
    VirtualFile values1 = myFixture.copyFileToProject(VALUES_OVERLAY2, "res1/values/nameDoesNotMatter.xml");
    VirtualFile values1No = myFixture.copyFileToProject(VALUES_OVERLAY2_NO, "res1/values-no/values.xml");
    VirtualFile values2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "res2/values/values.xml");
    VirtualFile values3 = myFixture.copyFileToProject(VALUES, "res/values/values.xml");
    VirtualFile res1 = values1.getParent().getParent();
    VirtualFile res2 = values2.getParent().getParent();
    VirtualFile res3 = values3.getParent().getParent();
    ModuleResourceRepository resources =
        ModuleResourceRepository.createForTest(myFacet, ImmutableList.of(res1, res2, res3), RES_AUTO, null);
    PsiFile psiValues1 = PsiManager.getInstance(getProject()).findFile(values1);
    assertNotNull(psiValues1);
    PsiFile psiValues1No = PsiManager.getInstance(getProject()).findFile(values1No);
    assertNotNull(psiValues1No);
    PsiFile psiValues2 = PsiManager.getInstance(getProject()).findFile(values2);
    assertNotNull(psiValues2);
    PsiFile psiValues3 = PsiManager.getInstance(getProject()).findFile(values3);
    assertNotNull(psiValues3);

    // Initial state; sanity check from #testOverlays()
    assertStringIs(resources, "title_layout_changes", "Layout Changes");
    assertStringIs(resources, "title_crossfade", "Complex Crossfade"); // Overridden in res2
    assertStringIs(resources, "title_zoom", "Zoom!"); // Overridden in res1
    assertStringIs(resources, "unique_string", "Unique"); // Overridden in res2
    assertStringIs(resources, "another_unique_string", "Another Unique", res -> res.getConfiguration().isDefault()); // Overridden in res1
    assertStringIs(resources, "app_name", "Very Different App Name", res -> res.getConfiguration().isDefault()); // res1 (not unique because we have a values-no item too)

    // Value resource check:
    // Verify that an edit in a value file, both in a non-overridden and an overridden
    // value, is observed; and that an override in an overridden value is not observed.

    assertThat(resources.getResources(RES_AUTO, ResourceType.STRING, "app_name")).hasSize(2);
    assertThat(resources.getResources(RES_AUTO, ResourceType.STRING, "title_layout_changes")).hasSize(1);
    ResourceItem appName = getSingleItem(resources, ResourceType.STRING, "app_name", item -> item.getConfiguration().isDefault());
    assertItemIsInDir(res1, appName);
    assertThat(appName.getResourceValue().getValue()).isEqualTo("Very Different App Name");

    long generation = resources.getModificationCount();
    int rescans = resources.getFileRescans();
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    Document document = documentManager.getDocument(psiValues1);
    assertNotNull(document);
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("Very Different App Name");
      document.insertString(offset, "Not ");
      documentManager.commitDocument(document);
    });
    waitForUpdates(resources);
    assertThat(resources.getModificationCount()).isGreaterThan(generation);
    assertThat(resources.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).

    // Should still be defined in res1 but have new value.
    // The order of items may have swapped if a full rescan is done.
    List<ResourceItem> list = resources.getResources(RES_AUTO, ResourceType.STRING, "app_name");
    assertThat(list).hasSize(2);
    appName = ContainerUtil.find(list, resourceItem -> resourceItem.getConfiguration().isDefault());
    assertNotNull(appName);
    assertItemIsInDir(res1, appName);
    ResourceValue appNameResourceValue = appName.getResourceValue();
    assertNotNull(appNameResourceValue);
    assertEquals("Not Very Different App Name", appNameResourceValue.getValue());

    // Try renaming the item name.
    generation = resources.getModificationCount();
    rescans = resources.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document.getText().indexOf("app_name");
      document.insertString(offset, "r");
      documentManager.commitDocument(document);
    });
    waitForUpdates(resources);
    assertThat(resources.getModificationCount()).isGreaterThan(generation);
    assertThat(resources.getFileRescans()).isEqualTo(rescans);
    assertThat(resources.getResources(RES_AUTO, ResourceType.STRING, "rapp_name")).hasSize(1);

    assertThat(resources.getResources(RES_AUTO, ResourceType.STRING, "app_name")).hasSize(2);
    appName = getSingleItem(resources, ResourceType.STRING, "app_name", new DefinedInOrUnder(res1));
    // The item is still under res1, but now it's in the Norwegian translation.
    assertEquals("no", appName.getConfiguration().getQualifierString());
    assertStringIs(resources, "app_name", "Forskjellig Navn", new DefinedInOrUnder(res1));

    // Delete that file:
    generation = resources.getModificationCount();
    rescans = resources.getFileRescans();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          values1No.delete(this);
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    waitForUpdates(resources);
    assertThat(resources.getModificationCount()).isGreaterThan(generation);
    assertThat(resources.getFileRescans()).isEqualTo(rescans);

    // Now the item is no longer available in res1; should fallback to res2.
    appName = getSingleItem(resources, ResourceType.STRING, "app_name");
    assertItemIsInDir(res2, appName);
    assertStringIs(resources, "app_name", "Different App Name");

    generation = resources.getModificationCount();
    rescans = resources.getFileRescans();
    // Check that editing an overridden attribute does not count as a change
    Document document2 = documentManager.getDocument(psiValues3);
    assertNotNull(document2);
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document2.getText().indexOf("Animations Demo");
      document2.insertString(offset, "Cool ");
      documentManager.commitDocument(document2);
    });
    waitForUpdates(resources);
    assertThat(resources.getModificationCount()).isGreaterThan(generation);
    assertThat(resources.getFileRescans()).isEqualTo(rescans + 1); // First edit is not incremental (file -> Psi).
    // Unaffected by above change
    assertStringIs(resources, "app_name", "Different App Name");

    // Finally check that editing an non-overridden attribute also gets picked up as a change
    generation = resources.getModificationCount();
    rescans = resources.getFileRescans();
    // Observe after the rescan, so that an edit causes a generation bump.
    assertStringIs(resources, "title_layout_changes", "Layout Changes");
    WriteCommandAction.runWriteCommandAction(null, () -> {
      int offset = document2.getText().indexOf("Layout Changes");
      document2.insertString(offset, "New ");
      documentManager.commitDocument(document2);
    });
    waitForUpdates(resources);
    assertThat(resources.getModificationCount()).isGreaterThan(generation);
    assertThat(resources.getFileRescans()).isEqualTo(rescans);
    assertStringIs(resources, "title_layout_changes", "New Layout Changes");
  }

  public void testHasResourcesOfType() throws Exception {
    // Test hasResourcesOfType merging (which may be optimized to be lighter-weight than map merging).
    VirtualFile res1 = myFixture.copyFileToProject(LAYOUT, "res/layout/layout.xml").getParent().getParent();
    VirtualFile res2 = myFixture.copyFileToProject(VALUES_OVERLAY1, "res2/values/values.xml").getParent().getParent();

    assertNotSame(res1, res2);
    ModuleResourceRepository resources = ModuleResourceRepository.createForTest(myFacet, ImmutableList.of(res1, res2), RES_AUTO, null);
    Set<ResourceType> typesWithoutRes3 = EnumSet.of(ResourceType.ARRAY, ResourceType.ID, ResourceType.LAYOUT,
                                                    ResourceType.STRING, ResourceType.STYLE);

    assertHasExactResourceTypes(resources, typesWithoutRes3);
    // Now update the repo with additional files, to test that merging picks up the new types.
    VirtualFile values3 = myFixture.copyFileToProject(VALUES, "res3/values/many_more_values.xml");
    VirtualFile res3 = values3.getParent().getParent();
    assertNotSame(res1, res3);
    assertNotSame(res2, res3);
    resources.updateRoots(ImmutableList.of(res1, res2, res3));

    EnumSet<ResourceType> allTypes = EnumSet.copyOf(typesWithoutRes3);
    allTypes.addAll(ImmutableList.of(ResourceType.ATTR, ResourceType.INTEGER, ResourceType.STYLEABLE, ResourceType.PLURALS));
    assertHasExactResourceTypes(resources, allTypes);

    // Now delete the values file and check again.
    PsiFile psiValues3 = PsiManager.getInstance(getProject()).findFile(values3);
    assertNotNull(psiValues3);
    WriteCommandAction.runWriteCommandAction(null, psiValues3::delete);
    waitForUpdates(resources);
    assertHasExactResourceTypes(resources, typesWithoutRes3);
  }

  /**
   * This tests that even if we initialize ResourceFolderRepository with VirtualFiles and the test code is careful to only work with
   * VirtualFiles, we still get the PsiListener events.
   *
   * Namely, {@link com.intellij.psi.impl.file.impl.PsiVFSListener} skips notifying other listeners if the parent directory has never
   * been initialized as PSI.
   */
  public void testPsiListenerWithVirtualFiles() throws Exception {
    VirtualFile res1 = myFixture.copyFileToProject(LAYOUT, "res/layout/layout.xml").getParent().getParent();
    // Stash the resource directory somewhere deep. Sometimes the test framework + VFS listener does automatically create
    // a PsiDirectory for the top level. We only want a VirtualFile representation, and not the PsiDirectory representation.
    VirtualFile layout2 = myFixture.copyFileToProject(LAYOUT_OVERLAY, "foo/baz/bar/res/layout/foo_activity.xml");
    VirtualFile res2 = layout2.getParent().getParent();
    assertNotSame(res1, res2);
    // Check that we indeed don't have the PsiDirectory already cached, by poking at the implementation classes.
    PsiManagerEx psiManager = (PsiManagerEx)PsiManager.getInstance(getProject());
    FileManagerImpl fileManager = (FileManagerImpl)psiManager.getFileManager();
    assertNull(fileManager.getCachedDirectory(res2));
    assertNull(fileManager.getCachedPsiFile(layout2));

    ModuleResourceRepository resources = ModuleResourceRepository.createForTest(myFacet, ImmutableList.of(res1, res2), RES_AUTO, null);

    assertNotNull(fileManager.getCachedDirectory(res2));

    long generation = resources.getModificationCount();
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "foo_activity"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "bar_activity"));

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        try {
          layout2.rename(this, "bar_activity.xml");
        }
        catch (IOException e) {
          fail(e.toString());
        }
      }
    });
    waitForUpdates(resources);
    assertTrue(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "bar_activity"));
    assertFalse(resources.hasResources(RES_AUTO, ResourceType.LAYOUT, "foo_activity"));
    assertTrue(resources.getModificationCount() > generation);
  }

  // Unit test support methods

  static void assertItemIsInDir(VirtualFile dir, ResourceItem item) {
    VirtualFile source = IdeResourcesUtil.getSourceAsVirtualFile(item);
    assertNotNull(source);
    assertEquals(dir, source.getParent().getParent());
  }

  private static void assertStringIs(LocalResourceRepository repository, String key, String expected) {
    ResourceItem item = getSingleItem(repository, ResourceType.STRING, key);
    assertThat(item.getResourceValue().getValue()).isEqualTo(expected);
  }

  private static void assertStringIs(@NotNull LocalResourceRepository repository, @NotNull String key, @NotNull String expected,
                                     @NotNull Predicate<ResourceItem> filter) {
    ResourceItem item = getSingleItem(repository, ResourceType.STRING, key, filter);
    assertThat(item.getResourceValue().getValue()).isEqualTo(expected);
  }

  static void assertHasExactResourceTypes(@NotNull LocalResourceRepository resources, @NotNull Set<ResourceType> types) {
    for (ResourceType type : ResourceType.values()) {
      if (types.contains(type)) {
        assertTrue(type.getName(), resources.hasResources(RES_AUTO, type));
      }
      else {
        assertFalse(type.getName(), resources.hasResources(RES_AUTO, type));
      }
    }
  }

  public void testAllowEmpty() {
    assertTrue(Lint.assertionsEnabled()); // This test should be run with assertions enabled!
    LocalResourceRepository repository = ModuleResourceRepository.createForTest(myFacet, Collections.emptyList(), RES_AUTO, null);
    repository.getModificationCount();
    assertEmpty(repository.getResources(RES_AUTO, ResourceType.ID).keySet());
  }
}
