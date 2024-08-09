/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.strings;

import static com.android.testutils.AsyncTestUtils.waitForCondition;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents;
import static org.mockito.Mockito.when;

import com.android.tools.idea.editors.strings.action.AddKeyAction;
import com.android.tools.idea.editors.strings.action.AddLocaleAction;
import com.android.tools.idea.editors.strings.action.ReloadStringResourcesAction;
import com.android.tools.idea.editors.strings.action.RemoveKeysAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.android.ide.common.resources.ResourceItem;
import com.android.tools.adtui.swing.FakeKeyboardFocusManager;
import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.idea.actions.BrowserHelpAction;
import com.android.tools.idea.editors.strings.action.FilterKeysAction;
import com.android.tools.idea.editors.strings.action.FilterLocalesAction;
import com.android.tools.idea.editors.strings.model.StringResourceKey;
import com.android.tools.idea.editors.strings.table.StringResourceTable;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.editors.strings.table.StringTableCellEditor;
import com.android.tools.idea.editors.strings.table.filter.NeedsTranslationsRowFilter;
import com.android.tools.idea.res.ModuleResourceRepository;
import com.android.tools.res.LocalResourceRepository;
import com.android.tools.idea.res.StringResourceWriter;
import com.intellij.ide.impl.HeadlessDataManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.SameThreadExecutor;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.AbstractButton;
import javax.swing.CellEditor;
import javax.swing.DefaultCellEditor;
import javax.swing.SwingUtilities;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;

/**
 * Tests for {@link StringResourceViewPanel}.
 */
public final class StringResourceViewPanelTest extends AndroidTestCase {
  private StringResourceViewPanel myPanel;
  private StringResourceTable myTable;
  private LocalResourceRepository<VirtualFile> myRepository;
  private StringResourceWriter myStringResourceWriter;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myStringResourceWriter = StringResourceWriter.INSTANCE;
    myPanel = new StringResourceViewPanel(myFacet, getTestRootDisposable(), () -> myStringResourceWriter);
    myTable = myPanel.getTable();

    VirtualFile resourceDirectory = myFixture.copyDirectoryToProject("stringsEditor/base/res", "res");
    myRepository = ModuleResourceRepository.createForTest(myFacet, Collections.singletonList(resourceDirectory));
    myPanel.getTable().setModel(new StringResourceTableModel(Utils.createStringRepository(myRepository), myFacet.getModule().getProject()));
  }

  public void testSetShowingOnlyKeysNeedingTranslations() {
    Object expectedColumn = Arrays.asList(
      "key1",
      "key2",
      "key3",
      "key5",
      "key6",
      "key7",
      "key8",
      "key4",
      "key9",
      "key10");

    assertEquals(expectedColumn, myTable.getColumnAt(StringResourceTableModel.KEY_COLUMN));

    myTable.setRowFilter(new NeedsTranslationsRowFilter());

    expectedColumn = Arrays.asList(
      "key1",
      "key3",
      "key7",
      "key8",
      "key4",
      "key9",
      "key10");

    assertEquals(expectedColumn, myTable.getColumnAt(StringResourceTableModel.KEY_COLUMN));
  }

  public void testTableDoesntRefilterAfterEditingUntranslatableCell() throws Exception {
    myTable.setRowFilter(new NeedsTranslationsRowFilter());
    editCellAt(true, 0, StringResourceTableModel.UNTRANSLATABLE_COLUMN);

    Object expectedColumn = Arrays.asList(
      "key1",
      "key3",
      "key7",
      "key8",
      "key4",
      "key9",
      "key10");

    assertEquals(expectedColumn, myTable.getColumnAt(StringResourceTableModel.KEY_COLUMN));
  }

  public void testTableDoesntRefilterAfterEditingTranslationCell() throws Exception {
    myTable.setRowFilter(new NeedsTranslationsRowFilter());
    editCellAt("Key 3 en-rGB", 2, 6);

    assertThat(myTable.getColumnAt(StringResourceTableModel.KEY_COLUMN)).containsExactly(
      "key1",
      "key3",
      "key7",
      "key8",
      "key4",
      "key9",
      "key10").inOrder();
  }

  public void testSelectingCell() {
    myTable.setRowFilter(new NeedsTranslationsRowFilter());
    myTable.selectCellAt(1, StringResourceTableModel.DEFAULT_VALUE_COLUMN);

    assertEquals("Key 3 default", myPanel.myDefaultValueTextField.getTextField().getText());
    assertEquals("<string name=\"key3\" translatable=\"true\">Key 3 default</string>", myPanel.myXmlTextField.getText());
  }

  public void testXmlTag() {
    myTable.selectCellAt(0, StringResourceTableModel.DEFAULT_VALUE_COLUMN);
    assertEquals("<string name=\"key1\">Key 1 default</string>", myPanel.myXmlTextField.getText());

    myTable.selectCellAt(1, 6);
    assertEquals("<string name=\"key2\" >Key 2 en-rGB</string>", myPanel.myXmlTextField.getText());

    myTable.selectCellAt(2, 6);
    assertEquals("", myPanel.myXmlTextField.getText());

    myTable.selectCellAt(2, StringResourceTableModel.DEFAULT_VALUE_COLUMN);
    assertEquals("<string name=\"key3\" translatable=\"true\">Key 3 default</string>", myPanel.myXmlTextField.getText());
  };

  public void testReloadData() {
    VirtualFile resourceDirectory = myRepository.getResourceDirs().iterator().next();
    assertThat(StringResourceWriter.INSTANCE.addDefault(
      myFixture.getProject(),
      new StringResourceKey("test_reload", resourceDirectory),
      "Reload!", /* translatable = */ true)).isTrue();

    myPanel.reloadData();

    assertThat(myTable.getColumnAt(StringResourceTableModel.KEY_COLUMN)).contains("test_reload");
    assertThat(myTable.getColumnAt(StringResourceTableModel.DEFAULT_VALUE_COLUMN)).contains("Reload!");
  }

  public void testDeleteKeysWithoutSelectionHasNoEffect() {
    int beforeDelete = myTable.getModel().getRowCount();
    assertTrue("Number of rows should not be empty", beforeDelete > 0);
    myPanel.deleteSelectedKeys();
    assertEquals("Number of rows should not have changed", beforeDelete, myTable.getModel().getRowCount());
  }

  public void testDeleteKeys() {
    AtomicBoolean deleted = new AtomicBoolean(true);
    myStringResourceWriter = new StringResourceWriterDelegate(StringResourceWriter.INSTANCE) {
      @Override
      public void safeDelete(@NotNull Project project,
                             @NotNull Collection<? extends ResourceItem> items,
                             @NotNull Runnable successCallback) {
        deleted.set(true);
        delete(project, items);
        successCallback.run();
      }
    };

    myTable.selectCellAt(0, 0);
    int beforeDelete = myTable.getModel().getRowCount();
    assertTrue("Number of rows should not be empty", beforeDelete > 0);
    myPanel.deleteSelectedKeys();
    assertTrue(deleted.get());
    assertTrue("Number of rows should not have changed", myTable.getModel().getRowCount() < beforeDelete);
  }

  private void editCellAt(@NotNull Object value, int viewRowIndex, int viewColumnIndex) throws TimeoutException {
    myTable.selectCellAt(viewRowIndex, viewColumnIndex);
    myTable.editCellAt(viewRowIndex, viewColumnIndex);

    CellEditor cellEditor = myTable.getCellEditor();

    if (viewColumnIndex == StringResourceTableModel.UNTRANSLATABLE_COLUMN) {
      Object component = ((DefaultCellEditor)cellEditor).getComponent();
      ((AbstractButton)component).setSelected((Boolean)value);
    }
    else {
      ((StringTableCellEditor)cellEditor).setCellEditorValue(value);
    }

    cellEditor.stopCellEditing();

    AtomicBoolean done = new AtomicBoolean();
    myRepository.invokeAfterPendingUpdatesFinish(SameThreadExecutor.INSTANCE, () -> done.set(true));
    waitForCondition(2, TimeUnit.SECONDS, done::get);
    dispatchAllInvocationEvents();
  }

  public void testKeyboardNavigation() throws Exception {
    // Setup data provider for the Translation Editor actions on the toolbar:
    StringResourceEditor editor = Mockito.mock(StringResourceEditor.class);
    when(editor.getPanel()).thenReturn(myPanel);
    HeadlessDataManager dataManager = (HeadlessDataManager)HeadlessDataManager.getInstance();
    dataManager.setTestDataProvider(new DataProvider() {
      @Override
      public @Nullable Object getData(@NotNull @NonNls String dataId) {
        if (CommonDataKeys.PROJECT.is(dataId)) {
          return getProject();
        }
        if (PlatformDataKeys.FILE_EDITOR.is(dataId)) {
          return editor;
        }
        return null;
      }
    }, getTestRootDisposable());

    // Set the first table to opaque to avoid graphics interaction during scrolling:
    myTable.getFrozenTable().setOpaque(false);

    // Set a selection to enable more actions:
    myTable.getFrozenTable().changeSelection(2, 3, false, false);

    // Update all actions:
    notNull(myPanel.getToolbar()).updateActionsAsync().get();

    // StopLoading will also make the toolbar navigable:
    myPanel.stopLoading();

    myPanel.getLoadingPanel().setSize(1200, 2000);
    FakeUi ui = new FakeUi(myPanel.getLoadingPanel(), 1.0, true, getTestRootDisposable());

    FakeKeyboardFocusManager focusManager = new FakeKeyboardFocusManager(getTestRootDisposable());
    focusManager.setFocusOwner(myTable.getFrozenTable());

    // Tab jumps out of both tables:
    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    assertThat(notNull(focusManager.getFocusOwner()).getName()).isEqualTo("xmlTextField");

    // Back Tab jumps back to the frozen table:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(notNull(focusManager.getFocusOwner()).getName()).isEqualTo("frozenTable");

    // Back Tab jumps back to the last toolbar button:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(getFocusedActionButton()).isInstanceOf(BrowserHelpAction.class);

    // Tab jumps back to the frozen table:
    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    assertThat(notNull(focusManager.getFocusOwner()).getName()).isEqualTo("frozenTable");

    // Move focus to the scrollable table:
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT);
    assertThat(notNull(focusManager.getFocusOwner()).getName()).isEqualTo("scrollableTable");

    // Tab jumps out of both tables:
    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    assertThat(notNull(focusManager.getFocusOwner()).getName()).isEqualTo("xmlTextField");

    // Back Tab jumps back to the scrollable table (since it was the last focused table):
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(notNull(focusManager.getFocusOwner()).getName()).isEqualTo("scrollableTable");

    // Back Tab jumps back to the last toolbar button:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(getFocusedActionButton()).isInstanceOf(BrowserHelpAction.class);

    // Tab jumps back to the scrollable table:
    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    assertThat(notNull(focusManager.getFocusOwner()).getName()).isEqualTo("scrollableTable");

    // Tab jumps out of both tables:
    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    assertThat(notNull(focusManager.getFocusOwner()).getName()).isEqualTo("xmlTextField");

    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    assertThat(notNull(focusManager.getFocusOwner()).getName()).isEqualTo("keyTextField");

    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    assertThat(notNull(focusManager.getFocusOwner().getParent()).getName()).isEqualTo("defaultValueTextField");

    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    assertThat(notNull(focusManager.getFocusOwner().getParent()).getName()).isEqualTo("translationTextField");

    // Jumps to toolbar actions:
    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    assertThat(getFocusedActionButton()).isInstanceOf(AddKeyAction.class);

    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    assertThat(getFocusedActionButton()).isInstanceOf(RemoveKeysAction.class);

    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    assertThat(getFocusedActionButton()).isInstanceOf(AddLocaleAction.class);

    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    assertThat(getFocusedActionButton()).isInstanceOf(FilterKeysAction.class);

    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    assertThat(getFocusedActionButton()).isInstanceOf(FilterLocalesAction.class);

    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    assertThat(getFocusedActionButton()).isInstanceOf(ReloadStringResourcesAction.class);

    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    assertThat(getFocusedActionButton()).isInstanceOf(BrowserHelpAction.class);

    // Tab jumps back to the scrollable table:
    ui.keyboard.pressAndRelease(KeyEvent.VK_TAB);
    assertThat(notNull(focusManager.getFocusOwner()).getName()).isEqualTo("scrollableTable");
  }

  @Nullable
  private AnAction getFocusedActionButton() {
    KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Component[] toolbarButtons = notNull(myPanel.getToolbar()).getComponent().getComponents();
    for (int i=0; i<toolbarButtons.length; i++) {
      if (SwingUtilities.isDescendingFrom(focusManager.getFocusOwner(), toolbarButtons[i])) {
        return myPanel.getToolbar().getActions().get(i);
      }
    }
    return null;
  }

  @NotNull
  private <T> T notNull(@Nullable T e) {
    if (e == null) {
      throw new RuntimeException("Expected to be non null");
    }
    return e;
  }
}
