/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.npw;

import com.android.tools.idea.gradle.project.ModuleToImport;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

import java.io.IOException;
import java.util.Collection;

import static com.android.tools.idea.npw.ModuleListModel.ModuleValidationState.*;

public final class ModulesListModelTest extends AndroidTestCase {
  public static final String NEW_NAME = "a new name";
  public static final String EXISTING_MODULE = "existing_module";
  public static final Supplier<? extends Iterable<String>> NO_DEPS = Suppliers.ofInstance(ImmutableSet.<String>of());

  private VirtualFile myTempDir;
  private ModuleToImport myModule1;
  private ModuleToImport myModule2;
  private ModuleListModel myModel;

  private static void assertContainsAll(ModuleListModel moduleListModel, ModuleToImport... expectedModules) {
    Collection<ModuleToImport> modules = moduleListModel.getSelectedModules();
    assertEquals(expectedModules.length, modules.size());
    for (ModuleToImport module : expectedModules) {
      assertTrue(String.format("Does not contain %s", module.name), modules.contains(module));
    }
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<Void, IOException>() {
      @Override
      public Void compute() throws IOException {
        myTempDir = VfsUtil.findFileByIoFile(Files.createTempDir(), true);
        assert myTempDir != null;
        VirtualFile module1vf = VfsUtil.createDirectoryIfMissing(myTempDir, "module1");
        VirtualFile module2vf = VfsUtil.createDirectoryIfMissing(myTempDir, "module2");
        assert module1vf != null && module2vf != null;
        myModule1 = new ModuleToImport(module1vf.getName(), module1vf, NO_DEPS);
        myModule2 =
          new ModuleToImport(module2vf.getName(), module2vf, Suppliers.ofInstance(ImmutableSet.of(module1vf.getName())));
        VirtualFile existingModule = VfsUtil.createDirectoryIfMissing(getProject().getBaseDir(), EXISTING_MODULE);
        if (existingModule == null) {
          throw new IOException("Unable to create fake module directory");
        }
        existingModule.createChildData(this, "empty_file");
        return null;
      }
    });
    myModel = new ModuleListModel(getProject());
  }

  @Override
  public void tearDown() throws Exception {
    ApplicationManager.getApplication().runWriteAction(new ThrowableComputable<Void, IOException>() {
      @Override
      public Void compute() throws IOException {
        myTempDir.delete(this);
        return null;
      }
    });
    super.tearDown();
  }

  private void setModules() {
    setModules(myModule1, myModule2);
  }

  private void setModules(ModuleToImport... modules) {
    myModel.setContents(myTempDir, ImmutableSet.copyOf(modules));
  }

  public void testDependencyValidation() {
    setModules();
    assertContainsAll(myModel, myModule1, myModule2);
    assertEquals(myModule1.name, myModel.getModuleName(myModule1));
    assertEquals(myModule2.name, myModel.getModuleName(myModule2));
    assertEquals(OK, myModel.getModuleState(myModule2));
    assertEquals(REQUIRED, myModel.getModuleState(myModule1));
    assertFalse(myModel.hasPrimary());
  }

  public void testDependencyValidationUncheckedModule() {
    setModules();
    myModel.setSelected(myModule2, false);
    assertContainsAll(myModel, myModule1);
    assertEquals(OK, myModel.getModuleState(myModule1));
  }

  public void testCantUncheckRequiredModule() {
    setModules();
    myModel.setSelected(myModule1, false);
    assertContainsAll(myModel, myModule1, myModule2);
    myModel.setSelected(myModule2, false);
    myModel.setSelected(myModule1, false);
    assertContainsAll(myModel);
    myModel.setSelected(myModule2, true);
    assertContainsAll(myModel, myModule1, myModule2);
  }

  public void testPrimaryModuleRecognized() {
    myModel.setContents(myModule2.location, ImmutableSet.of(myModule1, myModule2));
    assertEquals(myModel.getPrimary(), myModule2);
    assertEquals(REQUIRED, myModel.getModuleState(myModule1));
    assertTrue(myModel.hasPrimary());
    myModel.setSelected(myModule2, false);
    assertContainsAll(myModel, myModule1, myModule2);
  }

  public void testOverrideModuleName() {
    setModules();
    myModel.setModuleName(myModule2, NEW_NAME);
    assertEquals(NEW_NAME, myModel.getModuleName(myModule2));
    myModel.setModuleName(myModule1, NEW_NAME);
    assertEquals(myModule1.name, myModel.getModuleName(myModule1));
  }

  public void testNameRevertsIfModuleIsRequired() {
    setModules();
    myModel.setSelected(myModule2, false);
    myModel.setModuleName(myModule1, NEW_NAME);
    assertEquals(NEW_NAME, myModel.getModuleName(myModule1));
    myModel.setSelected(myModule2, true);
    assertEquals(myModule1.name, myModel.getModuleName(myModule1));
    myModel.setSelected(myModule2, false);
    assertEquals(myModule1.name, myModel.getModuleName(myModule1));
  }

  public void testCantRenameUncheckedModule() {
    setModules();
    myModel.setSelected(myModule2, false);
    myModel.setModuleName(myModule2, NEW_NAME);
    assertEquals(myModule2.name, myModel.getModuleName(myModule2));
  }

  public void testNameNotResetWhenToggling() {
    setModules();
    myModel.setModuleName(myModule2, NEW_NAME);
    myModel.setSelected(myModule2, false);
    myModel.setSelected(myModule2, true);
    assertEquals(NEW_NAME, myModel.getModuleName(myModule2));
  }

  public void testModuleExistsValidation() {
    ModuleToImport conflicting = new ModuleToImport(EXISTING_MODULE, myModule2.location, NO_DEPS);
    setModules(myModule1, conflicting);
    assertEquals(ALREADY_EXISTS, myModel.getModuleState(conflicting));
    myModel.setModuleName(conflicting, NEW_NAME);
    assertEquals(OK, myModel.getModuleState(conflicting));
  }

  public void testNameCollision() {
    ModuleToImport anotherModule = new ModuleToImport("m1", myModule2.location, NO_DEPS);
    setModules(myModule1, anotherModule);
    myModel.setModuleName(myModule1, anotherModule.name);
    assertEquals(DUPLICATE_MODULE_NAME, myModel.getModuleState(anotherModule));
    assertEquals(DUPLICATE_MODULE_NAME, myModel.getModuleState(myModule1));
    myModel.setSelected(anotherModule, false);
    assertEquals(OK, myModel.getModuleState(anotherModule));
    assertEquals(OK, myModel.getModuleState(myModule1));
    myModel.setSelected(anotherModule, true);
    assertEquals(DUPLICATE_MODULE_NAME, myModel.getModuleState(anotherModule));
    assertEquals(DUPLICATE_MODULE_NAME, myModel.getModuleState(myModule1));
    myModel.setSelected(myModule1, false);
    assertEquals(OK, myModel.getModuleState(anotherModule));
    assertEquals(OK, myModel.getModuleState(myModule1));
  }

  public void testModuleExistsSeverity() {
    ModuleToImport existing = new ModuleToImport(EXISTING_MODULE, myModule2.location, NO_DEPS);
    setModules(existing, myModule1);
    assertEquals(MessageType.WARNING, myModel.getStatusSeverity(existing));
    myModel.setSelected(existing, true);
    assertEquals(MessageType.ERROR, myModel.getStatusSeverity(existing));
  }

  public void testCanUncheckPrimaryModuleAndNotRequired() {
    myModel.setContents(myModule2.location, ImmutableSet.of(myModule1, myModule2));
    myModel.setSelected(myModule1, false);
    assertContainsAll(myModel, myModule1, myModule2);
  }

  public void testSelectedByDefault() {
    ModuleToImport nullLocation = new ModuleToImport("somename", null, NO_DEPS);
    ModuleToImport existing = new ModuleToImport(EXISTING_MODULE, myModule2.location, NO_DEPS);
    setModules(myModule1, nullLocation, existing);
    assertTrue(myModel.isSelected(myModule1));
    assertFalse(myModel.isSelected(nullLocation));
    assertFalse(myModel.isSelected(existing));
  }

  public void testCanOverrideDefaultSelection() {
    ModuleToImport nullLocation = new ModuleToImport("somename", null, NO_DEPS);
    ModuleToImport existing = new ModuleToImport(EXISTING_MODULE, myModule2.location, NO_DEPS);
    setModules(myModule1, nullLocation, existing);
    myModel.setSelected(nullLocation, true);
    myModel.setSelected(existing, true);
    assertTrue(myModel.isSelected(myModule1));
    assertFalse(myModel.isSelected(nullLocation));
    assertTrue(myModel.isSelected(existing));
  }

  public void testUncheckedByDefaultModuleDependencies() {
    ModuleToImport existing = new ModuleToImport(EXISTING_MODULE, myModule2.location,
                                                 Suppliers.ofInstance(ImmutableSet.of(myModule1.name)));
    setModules(myModule1, existing);
    assertEquals(OK, myModel.getModuleState(myModule1));
    myModel.setSelected(existing, true);
    assertEquals(REQUIRED, myModel.getModuleState(myModule1));
  }

  public void testNameSetting() {
    setModules(myModule1);
    myModel.setModuleName(myModule1, NEW_NAME);
    assertEquals(NEW_NAME, myModel.getModuleName(myModule1));
    assertEquals(OK, myModel.getModuleState(myModule1));
    myModel.setModuleName(myModule1, null);
    assertEquals(myModule1.name, myModel.getModuleName(myModule1));
    myModel.setModuleName(myModule1, "");
    assertEquals(INVALID_NAME, myModel.getModuleState(myModule1));
    myModel.setModuleName(myModule1, null);
    assertEquals(OK, myModel.getModuleState(myModule1));
    myModel.setModuleName(myModule1, ":");
    assertEquals(INVALID_NAME, myModel.getModuleState(myModule1));
    myModel.setModuleName(myModule1, ":a");
    assertEquals(OK, myModel.getModuleState(myModule1));
    myModel.setModuleName(myModule1, "a:");
    assertEquals(INVALID_NAME, myModel.getModuleState(myModule1));
    myModel.setModuleName(myModule1, "a:b");
    assertEquals(OK, myModel.getModuleState(myModule1));
    myModel.setModuleName(myModule1, "a:b/a");
    assertEquals(INVALID_NAME, myModel.getModuleState(myModule1));
    myModel.setModuleName(myModule1, "a::b");
    assertEquals(INVALID_NAME, myModel.getModuleState(myModule1));
  }

  public void testTwoEmptyNamesAreNotDuplicates() {
    ModuleToImport m2 = new ModuleToImport("m2", myModule2.location, NO_DEPS);
    setModules(myModule1, m2);
    myModel.setModuleName(myModule1, "");
    myModel.setModuleName(m2, "");
    assertEquals(INVALID_NAME, myModel.getModuleState(myModule1));
    assertEquals(INVALID_NAME, myModel.getModuleState(m2));
  }

  public void testCanRename() {
    setModules();
    assertTrue(myModel.canRename(myModule2));
    assertFalse(myModel.canRename(myModule1));
    myModel.setSelected(myModule2, false);
    assertFalse(myModel.canRename(myModule2));
    assertTrue(myModel.canRename(myModule1));
  }

  public void testCanNeverRenameRequiredModule() {
    setModules();
    myModel.setModuleName(myModule2, myModule1.name);
    assertFalse(myModel.canRename(myModule1));
  }
}
