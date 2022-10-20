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
package com.android.tools.idea.tests.gui.framework.fixture;

import static com.android.tools.idea.tests.gui.framework.GuiTests.clickPopupMenuItem;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitForBackgroundTasks;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitForPopup;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.fest.reflect.core.Reflection.method;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.idea.common.editor.DesignToolsSplitEditor;
import com.android.tools.idea.common.editor.DesignerEditor;
import com.android.tools.idea.common.editor.SplitEditor;
import com.android.tools.idea.editors.manifest.ManifestPanel;
import com.android.tools.idea.editors.strings.StringResourceEditor;
import com.android.tools.idea.io.TestFileUtils;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.VisualizationFixture;
import com.android.tools.idea.tests.gui.framework.fixture.translations.TranslationsEditorFixture;
import com.android.tools.idea.uibuilder.visual.VisualizationToolWindowFactory;
import com.google.common.collect.ImmutableList;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.SplitEditorToolbar;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.RowIcon;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.tabs.impl.TabLabel;
import java.awt.Component;
import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.KeyPressInfo;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.driver.ComponentDriver;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fixture wrapping the IDE source editor, providing convenience methods
 * for controlling the source editor and verifying editor state. Note that unlike
 * the IntelliJ Editor class, which is one per file, this fixture represents an
 * editor in the more traditional sense: a container for multiple files, so you
 * ask "the" editor its current file, to select text in that file, to switch to
 * a different file, etc.
 */
public class EditorFixture {
  /**
   * Performs simulation of user events on {@code target}.
   */
  final Robot robot;
  private final IdeFrameFixture myFrame;

  /**
   * Constructs a new editor fixture, tied to the given project.
   */
  EditorFixture(Robot robot, IdeFrameFixture frame) {
    this.robot = robot;
    myFrame = frame;
  }

  /**
   * Returns the {@link IdeFrameFixture} containing this editor.
   */
  @NotNull
  public IdeFrameFixture frame() {
    return myFrame;
  }

  /** Returns the selected file with most recent focused editor, or {@code null} if there are no selected files. */
  @Nullable
  public VirtualFile getCurrentFile() {
    VirtualFile[] selectedFiles = FileEditorManager.getInstance(myFrame.getProject()).getSelectedFiles();
    return (selectedFiles.length > 0) ? selectedFiles[0] : null;
  }

  /**
   * Returns the name of the current file, if any. Convenience method
   * for {@link #getCurrentFile()}.getName().
   *
   * @return the current file name, or null
   */
  @Nullable
  public String getCurrentFileName() {
    VirtualFile currentFile = getCurrentFile();
    return currentFile != null ? currentFile.getName() : null;
  }

  /**
   * From the currently selected text editor, returns the line number where the primary caret is.
   * <p>
   * This line number is conventionally 1-based, unlike the 0-based line numbers in {@link Editor}.
   *
   * @throws IllegalStateException if there is no currently selected text editor
   */
  public int getCurrentLineNumber() {
    return GuiQuery.getNonNull(
      () -> {
        Editor editor = getSelectedTextEditor();
        int offset = editor.getCaretModel().getPrimaryCaret().getOffset();
        return editor.getDocument().getLineNumber(offset) + 1;  // Editor uses 0-based line numbers.
      });
  }

  /**
   * From the currently selected text editor, returns the text of the line where the primary caret is.
   *
   * @throws IllegalStateException if there is no currently selected text editor
   */
  @NotNull
  public String getCurrentLine() {
    return GuiQuery.getNonNull(
      () -> {
        Editor editor = getSelectedTextEditor();
        Caret primaryCaret = editor.getCaretModel().getPrimaryCaret();
        return editor.getDocument().getText(new TextRange(primaryCaret.getVisualLineStart(), primaryCaret.getVisualLineEnd()));
      });
  }

  /**
   * Returns the text of the document in the currently selected text editor.
   *
   * @throws IllegalStateException if there is no currently selected text editor
   */
  @NotNull
  public String getCurrentFileContents() {
    return GuiQuery.getNonNull(
      () -> {
        Editor editor = getSelectedTextEditor();
        return editor.getDocument().getImmutableCharSequence().toString();
      });
  }

  @NotNull
  private Editor getSelectedTextEditor() {
    FileEditorManager manager = FileEditorManager.getInstance(myFrame.getProject());
    Editor editor = manager.getSelectedTextEditor();
    if (editor == null) {
      // When using the split editor, we need to retrieve its text editor.
      FileEditor selectedEditor = manager.getSelectedEditor();
      if (selectedEditor instanceof TextEditorWithPreview) {
        editor = ((TextEditorWithPreview)selectedEditor).getTextEditor().getEditor();
      }
    }
    checkState(editor != null, "no currently selected text editor");
    return editor;
  }

  /**
   * Wait for the editor to become active.
   */
  public void waitForFileToActivate() {
    Wait.seconds(10).expecting("File editor is active").until(() -> getCurrentFile() != null);
  }

  /**
   * Type the given text into the editor
   *
   * @param text the text to type at the current editor position
   */
  public EditorFixture typeText(@NotNull String text) {
    getFocusedEditor();
    robot.typeText(text);
    return this;
  }

  /**
   * Paste the given text into the editor
   *
   * @param text the text to paste at the current editor position
   */
  public EditorFixture pasteText(@NotNull String text) {
    getFocusedEditor();
    robot.pasteText(text);
    return this;
  }

  /**
   * Replace current editor text by the given text. After calling this method, the selected editor tab will be Tab.EDITOR
   *
   * @param text the text to paste at the current editor position
   */
  public EditorFixture replaceText(@NotNull String text) {
    selectEditorTab(Tab.EDITOR);
    invokeAction(EditorFixture.EditorAction.SELECT_ALL);
    return pasteText(text);
  }

  /**
   * Enter the given text into the editor. Types short strings, pastes longer ones to save time. Most fixtures or tests that enter
   * text into the editor should use this method. If there's a good reason to force one mode of entry or the other, use typeText or
   * pasteText as appropriate.
   *
   * @param text the text to enter at the current editor position
   */
  public EditorFixture enterText(@NotNull String text) {
    getFocusedEditor();
    robot.enterText(text);
    return this;
  }

  @NotNull
  public EditorFixture pressAndReleaseKey(@NotNull KeyPressInfo keypress) {
    getFocusedEditor();
    robot.pressAndReleaseKey(keypress.keyCode(), keypress.modifiers());

    return this;
  }

  @NotNull
  public EditorFixture pressAndReleaseKeys(@NotNull int keys) {
    getFocusedEditor();
    robot.pressAndReleaseKeys(keys);

    return this;
  }

  /**
   * Requests focus in the editor, waits and returns editor component.
   */
  @NotNull
  private JComponent getFocusedEditor() {
    Editor editor = GuiQuery.getNonNull(() -> getSelectedTextEditor());

    JComponent contentComponent = editor.getContentComponent();
    new ComponentDriver(robot).focusAndWaitForFocusGain(contentComponent);
    return contentComponent;
  }

  /**
   * Moves the caret to the start of the first occurrence of {@code after} immediately following {@code before} in the selected text editor.
   *
   * @throws IllegalStateException if there is no selected text editor or if no match is found
   */
  @NotNull
  public EditorFixture moveBetween(@NotNull String before, @NotNull String after) {
    return select(String.format("%s()%s", Pattern.quote(before), Pattern.quote(after)));
  }

  /**
   * Given a {@code regex} with one capturing group, selects the subsequence captured in the first match found in the selected text editor.
   *
   * @throws IllegalStateException if there is no currently selected text editor or no match is found
   * @throws IllegalArgumentException if {@code regex} does not have exactly one capturing group
   */
  @NotNull
  public EditorFixture select(@NotNull String regex) {
    Matcher matcher = Pattern.compile(regex).matcher(getCurrentFileContents());
    checkArgument(matcher.groupCount() == 1, "must have exactly one capturing group: %s", regex);
    // noinspection ResultOfMethodCallIgnored
    matcher.find();
    int start = matcher.start(1);
    int end = matcher.end(1);
    SelectTarget selectTarget = GuiQuery.getNonNull(
      () -> {
        Editor editor = getSelectedTextEditor();
        LogicalPosition startPosition = editor.offsetToLogicalPosition(start);
        LogicalPosition endPosition = editor.offsetToLogicalPosition(end);
        // CENTER_DOWN tries to make endPosition visible; if that fails, write selectWithKeyboard and rename this method selectWithMouse?
        editor.getScrollingModel().scrollTo(startPosition, ScrollType.CENTER_DOWN);

        SelectTarget target = new SelectTarget();
        target.component = editor.getContentComponent();
        target.startPoint = editor.logicalPositionToXY(startPosition);
        target.endPoint = editor.logicalPositionToXY(endPosition);
        return target;
      });
    robot.pressMouse(selectTarget.component, selectTarget.startPoint);
    robot.moveMouse(selectTarget.component, selectTarget.endPoint);
    robot.releaseMouseButtons();

    return this;
  }

  private static class SelectTarget {
    JComponent component;
    Point startPoint;
    Point endPoint;
  }

  @NotNull
  public EditorFixture selectCurrentLine() {
    GuiTask.execute(() ->
      FileEditorManager.getInstance(myFrame.getProject())
        .getSelectedTextEditor()
        .getSelectionModel()
        .selectLineAtCaret()
    );
    return this;
  }

  /**
   * Closes the current editor
   */
  public EditorFixture close() {
    EdtTestUtil.runInEdtAndWait(
      () -> {
        VirtualFile currentFile = getCurrentFile();
        if (currentFile != null) {
          FileEditorManager manager = FileEditorManager.getInstance(myFrame.getProject());
          manager.closeFile(currentFile);
        }
      });
    return this;
  }

  /**
   * Closes the specified file.
   */
  public EditorFixture closeFile(@NotNull String relativePath) {
    EdtTestUtil.runInEdtAndWait(
      () -> {
        VirtualFile file = myFrame.findFileByRelativePath(relativePath);
        if (file != null) {
          FileEditorManager manager = FileEditorManager.getInstance(myFrame.getProject());
          manager.closeFile(file);
        }
      });
    return this;
  }

  /**
   * Selects the given tab in the current editor. Used to switch between
   * design mode and editor mode for example.
   *
   * @param tab the tab to switch to
   */
  public EditorFixture selectEditorTab(@NotNull Tab tab) {
    String tabName = tab.myTabName;
    Wait.seconds(5).expecting(String.format("find editor tab '%s'", tabName == null ? "<default>" : tabName)).until(
      () -> GuiQuery.getNonNull(() -> {
        VirtualFile currentFile = getCurrentFile();
        assertNotNull("Can't switch to tab " + tabName + " when no file is open in the editor", currentFile);
        FileEditorManager manager = FileEditorManager.getInstance(myFrame.getProject());
        for (FileEditor editor : manager.getAllEditors(currentFile)) {
          if (editor instanceof TextEditorWithPreview) {
            boolean consumedBySplitEditor = selectSplitEditorTab(tab, (TextEditorWithPreview)editor);
            if (consumedBySplitEditor) {
              return true;
            }
          }

          if (tabName == null || tabName.equals(editor.getName())) {
            // Have to use reflection
            //FileEditorManagerImpl#setSelectedEditor(FileEditor editor)
            method("setSelectedEditor").withParameterTypes(FileEditor.class).in(manager).invoke(editor);
            return true;
          }
        }
        return false;
      }));
    return this;
  }

  /**
   * Given a {@link Tab}, selects the corresponding mode in the {@link TextEditorWithPreview}, i.e. "Text only" when tab is {@link Tab#EDITOR} and
   * "Design only" when tab is {@link Tab#DESIGN}.
   * @return Whether this method effectively changed tabs. This only returns false if tab is not {@link Tab#EDITOR} or {@link Tab#DESIGN}.
   */
  private boolean selectSplitEditorTab(@NotNull Tab tab, @NotNull TextEditorWithPreview editor) {
    if (!(tab == Tab.EDITOR || tab == Tab.DESIGN)) {
      // Only text and design are supported by split editor at the moment.
      return false;
    }
    // The concept of tabs can be mapped to the split editor toolbar, where there are three actions to change the editor to (in this order):
    // 1) text-only, 2) split view, 3) preview(design)-only. We try to find this toolbar and select the corresponding action.
    SplitEditorToolbar toolbar = robot.finder().find(
      editor.getComponent(),
      new GenericTypeMatcher<SplitEditorToolbar>(SplitEditorToolbar.class) {
        @Override
        protected boolean isMatching(@NotNull SplitEditorToolbar component) {
          return true;
        }
      }
    );

    ActionToolbar actionToolbar = robot.finder().find(
      toolbar,
      new GenericTypeMatcher<ActionToolbarImpl>(ActionToolbarImpl.class) {
        @Override
        protected boolean isMatching(@NotNull ActionToolbarImpl component) {
          return component.getPlace().equals("TextEditorWithPreview");
        }
      }
    );

    List<ToggleAction> actions =
      actionToolbar.getActions()
        .stream()
        .flatMap((action) -> {
          if (action instanceof DefaultActionGroup) {
            return Arrays.stream(((DefaultActionGroup)action).getChildren(null));
          }
          return Stream.of(action);
        })
        .filter(ToggleAction.class::isInstance)
        .map(ToggleAction.class::cast)
        .collect(Collectors.toList());
    TestActionEvent e = new TestActionEvent();
    int actionToSelect = -1;
    switch (tab) {
      case EDITOR:
        // Text-only is the first action in the toolbar
        actionToSelect = 0;
        break;
      case DESIGN:
        // Design is the third action in the toolbar
        actionToSelect = 2;
        break;
      default: fail("Wrong tab action to select " + tab);
    }
    ToggleAction toggleAction = actions.get(actionToSelect);
    if (!toggleAction.isSelected(e)) {
      toggleAction.setSelected(e, true);
    }
    return true;
  }

  @NotNull
  public EditorFixture open(@NotNull Path relativePath, @NotNull Tab tab) {
    return open(relativePath.toString().replace('\\', '/'), tab);
  }

  /**
   * Opens up a different file. This will run through the "Open File..." dialog to
   * find and select the given file.
   *
   * @param file the file to open
   * @param tab which tab to open initially, if there are multiple editors
   */
  public EditorFixture open(@NotNull VirtualFile file, @NotNull Tab tab) {
    return open(file, tab, Wait.seconds(10));
  }

  public EditorFixture open(@NotNull VirtualFile file, @NotNull Tab tab, @NotNull Wait waitForFileOpen) {
    robot.waitForIdle(); // Make sure there are no pending open requests

    EdtTestUtil.runInEdtAndWait(
      () -> {
        // TODO: Use UI to navigate to the file instead
        Project project = myFrame.getProject();
        FileEditorManager manager = FileEditorManager.getInstance(project);
        if (tab == Tab.EDITOR) {
          manager.openTextEditor(new OpenFileDescriptor(project, file), true);
        }
        else {
          manager.openFile(file, true);
        }
      });

    selectEditorTab(tab);

    waitForFileOpen
      .expecting("file '" + file.getPath() + "' to be opened and loaded")
      .until(
        () -> GuiQuery.get(() -> {
          if (!file.equals(getCurrentFile())) {
            return false;
          }

          FileEditor fileEditor = FileEditorManager.getInstance(myFrame.getProject()).getSelectedEditor(file);
          JComponent editorComponent = fileEditor.getComponent();
          if (editorComponent instanceof JBLoadingPanel) {
            return !((JBLoadingPanel)editorComponent).isLoading();
          }
          return true;
        })
      );

    Editor editor = GuiQuery.get(() -> {
      FileEditorManager manager = FileEditorManager.getInstance(myFrame.getProject());
      FileEditor selectedEditor = manager.getSelectedEditor();
      if (selectedEditor instanceof SplitEditor) {
        SplitEditor splitEditor = (SplitEditor)selectedEditor;
        if (splitEditor.isTextMode()) {
          return splitEditor.getEditor();
        }
        else {
          return null;
        }
      }
      return manager.getSelectedTextEditor();
    });
    if (editor == null) {
      myFrame.requestFocusIfLost();
    }
    else {
      Wait.seconds(10).expecting("the editor to have the focus").until(() -> {
        // Keep requesting focus until it is obtained. Since there is no guarantee that the request focus will be granted,
        // keep asking until it is.
        JComponent target = editor.getContentComponent();
        robot.focus(target);
        return target.hasFocus();
      });
    }
    return this;
  }

  /**
   * Opens up a different file. This will run through the "Open File..." dialog to
   * find and select the given file.
   *
   * @param relativePath the project-relative path (with /, not File.separator, as the path separator)
   * @param tab which tab to open initially, if there are multiple editors
   */
  public EditorFixture open(@NotNull String relativePath, @NotNull Tab tab) {
    return open(relativePath, tab, Wait.seconds(10));
  }

  public EditorFixture open(@NotNull String relativePath, @NotNull Tab tab, @NotNull Wait waitForFileOpen) {
    assertFalse("Should use '/' in test relative paths, not '" + File.separator + "'", relativePath.contains("\\"));
    VirtualFile file = myFrame.findFileByRelativePath(relativePath);
    assertNotNull("Could not find " + relativePath, file);
    return open(file, tab, waitForFileOpen);
  }

  @NotNull
  public EditorFixture open(@NotNull Path relativePath) {
    return open(relativePath, Tab.DEFAULT);
  }

  /**
   * Like {@link #open(String, Tab)} but always uses the default tab.
   *
   * @param relativePath the project-relative path (with /, not File.separator, as the path separator)
   */
  public EditorFixture open(@NotNull String relativePath) {
    return open(relativePath, Tab.DEFAULT);
  }

  /** Invokes {@code editorAction} via its (first) keyboard shortcut in the active keymap. */
  public EditorFixture invokeAction(@NotNull EditorAction editorAction) {
    AnAction anAction = ActionManager.getInstance().getAction(editorAction.id);
    assertTrue(editorAction.id + " is not enabled", anAction.getTemplatePresentation().isEnabled());

    Component component = getFocusedEditor();
    Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    Shortcut[] shortcuts = keymap.getShortcuts(editorAction.id);
    if (shortcuts.length > 0 && shortcuts[0] instanceof KeyboardShortcut) {
      KeyboardShortcut cs = (KeyboardShortcut)shortcuts[0];
      KeyStroke firstKeyStroke = cs.getFirstKeyStroke();
      ComponentDriver<Component> driver = new ComponentDriver<>(robot);
      driver.pressAndReleaseKey(component, firstKeyStroke.getKeyCode(), new int[]{firstKeyStroke.getModifiers()});
      KeyStroke secondKeyStroke = cs.getSecondKeyStroke();
      if (secondKeyStroke != null) {
        driver.pressAndReleaseKey(component, secondKeyStroke.getKeyCode(), new int[]{secondKeyStroke.getModifiers()});
      }
    }
    else {
      EdtTestUtil.runInEdtAndWait(() -> {
        DataContext context = DataManager.getInstance().getDataContext(component);
        AnActionEvent event = AnActionEvent.createFromAnAction(anAction, null, "menu", context);
        anAction.actionPerformed(event);
      });
    }
    return this;
  }

  @NotNull
  public EditorFixture newFile(@NotNull Path relativePath, @NotNull String text) throws IOException {
    Path projectPath = Paths.get(myFrame.getProject().getBasePath());
    Path filePath = projectPath.resolve(relativePath);
    assert Files.notExists(filePath);

    VirtualFile file = TestFileUtils.writeFileAndRefreshVfs(filePath, "");
    assert  file != null;

    return open(relativePath).replaceText(text);
  }

  @NotNull
  public EditorNotificationPanelFixture awaitNotification(@NotNull String text) {
    // Notification panels can be updated/re-added on Dumb to Smart mode transitions (See EditorNotificationsImpl)
    DumbService.getInstance(myFrame.getProject()).waitForSmartMode();
    robot.waitForIdle();

    JLabel label = waitUntilShowing(robot, JLabelMatcher.withText(text));
    EditorNotificationPanel notificationPanel = (EditorNotificationPanel)label.getParent().getParent();
    return new EditorNotificationPanelFixture(myFrame, notificationPanel);
  }

  @NotNull
  public List<String> getHighlights(HighlightSeverity severity) {
    List<String> infos = new ArrayList<>();
    for (HighlightInfo info : getCurrentFileFixture().getHighlightInfos(severity)) {
      infos.add(info.getDescription());
    }
    return infos;
  }

  /**
   * Waits until the editor has the given number of errors at the given severity.
   * Typically used when you want to invoke an intention action, but need to wait until
   * the code analyzer has found an error it needs to resolve first.
   *
   * @param severity the severity of the issues you want to count
   * @param expected the expected count
   * @return this
   */
  @NotNull
  public EditorFixture waitForCodeAnalysisHighlightCount(@NotNull HighlightSeverity severity, int expected) {
    // Changing Java source level, for example, triggers compilation first; code analysis starts afterward
    waitForBackgroundTasks(robot);

    FileFixture file = getCurrentFileFixture();
    file.waitForCodeAnalysisHighlightCount(severity, expected);
    return this;
  }

  public List<String> moreActionsOptions() {
    JBList list = getList();
    final JListFixture listFixture = new JListFixture(robot, list);
    final ImmutableList<String> result = ImmutableList.copyOf(listFixture.contents());
    return result;
  }

  @NotNull
  private JBList getList() {
    return GuiTests.waitUntilShowingAndEnabled(robot, null, new GenericTypeMatcher<JBList>(JBList.class) {
      @Override
      protected boolean isMatching(@NotNull JBList list) {
        return list.getClass().getName().equals("com.intellij.ui.popup.list.ListPopupImpl$MyList");
      }
    });
  }

  @NotNull
  public EditorFixture waitUntilErrorAnalysisFinishes() {
    FileFixture file = getCurrentFileFixture();
    file.waitUntilErrorAnalysisFinishes();
    return this;
  }

  @NotNull
  public EditorFixture waitForQuickfix() {
    waitUntilFound(robot, new GenericTypeMatcher<JLabel>(JLabel.class) {
      @Override
      protected boolean isMatching(@NotNull JLabel component) {
        Icon icon = component.getIcon();
        if (icon instanceof RowIcon) {
          return AllIcons.Actions.QuickfixBulb.equals(((RowIcon)icon).getIcon(0));
        }
        return false;
      }
    });
    return this;
  }

  @NotNull
  private FileFixture getCurrentFileFixture() {
    VirtualFile currentFile = getCurrentFile();
    assertNotNull("Expected a file to be open", currentFile);
    return new FileFixture(myFrame.getProject(), currentFile);
  }

  /**
   * Waits for the quickfix bulb to appear before invoking the show intentions action,
   * then waits for the actions to be displayed and finally picks the one with
   * {@code labelPrefix}
   *
   */
  @NotNull
  public EditorFixture invokeQuickfixAction(@NotNull String labelPrefix) {
    waitForQuickfix();
    return invokeQuickfixActionWithoutBulb(labelPrefix);
  }

  /**
   * Invokes the show intentions action, without waiting for the quickfix bulb icon
   * then waits for the actions to be displayed and finally picks the one with
   * {@code labelPrefix}
   *
   */
  public EditorFixture invokeQuickfixActionWithoutBulb(@NotNull String labelPrefix) {
    waitUntilErrorAnalysisFinishes();
    invokeAction(EditorAction.SHOW_INTENTION_ACTIONS);
    JBList popup = waitForPopup(robot);
    clickPopupMenuItem(labelPrefix, popup, robot);
    return this;
  }

  /**
   * Returns a fixture around the layout editor, <b>if</b> the currently edited file is a layout file and it is currently showing the layout
   * editor tab or the parameter requests that it be opened if necessary.
   *
   * @param waitForSurfaceToLoad if true, this method will block until the surface is fully ready.
   *                             See {@link NlEditorFixture#waitForSurfaceToLoad()}
   * @throws IllegalStateException if there is no selected editor or it is not a {@link NlEditor}
   */
  @NotNull
  public NlEditorFixture getLayoutEditor(boolean waitForSurfaceToLoad) {
    // We don't display the design surface if the split editor is in text-only mode. Therefore, in order to get the component tree, we need
    // to switch mode to make sure it's available..
    selectEditorTab(Tab.DESIGN);

    // Wait for the editor to do any initializations
    robot.waitForIdle();

    NlEditorFixture editorFixture = GuiQuery.getNonNull(
      () -> {
        FileEditor[] editors = FileEditorManager.getInstance(myFrame.getProject()).getSelectedEditors();
        checkState(editors.length > 0, "no selected editors");
        FileEditor selected = editors[0];
        // We need to get the DesignerEditor since the split editor is a TextEditorWithPreview containing a TextEditor as well.
        checkState(selected instanceof DesignToolsSplitEditor, "invalid editor selected");
        selected = ((DesignToolsSplitEditor)selected).getDesignerEditor();
        checkState(selected instanceof DesignerEditor, "not a %s: %s", DesignerEditor.class.getSimpleName(), selected);
        return new NlEditorFixture(myFrame.robot(), (DesignerEditor)selected);
      });


    if (waitForSurfaceToLoad) {
      editorFixture.waitForSurfaceToLoad();
    }
    return editorFixture;
  }

  /**
   * Returns a fixture around the layout editor, <b>if</b> the currently edited file is a layout file and it is currently showing the layout
   * editor tab or the parameter requests that it be opened if necessary.
   *
   * @throws IllegalStateException if there is no selected editor or it is not a {@link NlEditor}
   */
  @NotNull
  public NlEditorFixture getLayoutEditor() {
    return getLayoutEditor(true);
  }

  /**
   * Returns a fixture around the visualization tool window, <b>if</b> the currently edited file
   * is a layout file. If visualization tool is not available to current layout, Timeout exception
   * is thrown.
   *
   * @return a visualization tool fixture.
   */
  @NotNull
  public VisualizationFixture getVisualizationTool() {
    if (!isVisualizationToolShowing()) {
      myFrame.invokeMenuPath("View", "Tool Windows", VisualizationToolWindowFactory.TOOL_WINDOW_ID);
    }

    Wait.seconds(20).expecting("Visualization window to be visible").until(() -> isVisualizationToolShowing());

    return new VisualizationFixture(myFrame.getProject(), myFrame.robot());
  }

  @NotNull
  public EditorFixture waitForVisualizationToolToHide() {
    Wait.seconds(3).expecting("visualization tool to close").until(() -> !isVisualizationToolShowing());
    return this;
  }

  private boolean isVisualizationToolShowing() {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myFrame.getProject()).getToolWindow(VisualizationToolWindowFactory.TOOL_WINDOW_ID);
    if (toolWindow == null) {
      return false;
    }
    return GuiQuery.getNonNull(() -> toolWindow.isVisible());
  }

  /**
   * Returns a fixture around the {@link com.android.tools.idea.editors.strings.StringResourceEditor} <b>if</b> the currently
   * displayed editor is a translations editor.
   */
  @NotNull
  public TranslationsEditorFixture getTranslationsEditor() {
    return GuiQuery.getNonNull(
      () -> {
        FileEditor[] editors = FileEditorManager.getInstance(myFrame.getProject()).getSelectedEditors();
        checkState(editors.length > 0, "no selected editors");
        FileEditor selected = editors[0];
        checkState(selected instanceof StringResourceEditor, "not a %s: %s", StringResourceEditor.class.getSimpleName(), selected);
        return new TranslationsEditorFixture(robot, (StringResourceEditor)selected);
      });
  }

  @NotNull
  public MergedManifestFixture getMergedManifestEditor() {
    return GuiQuery.getNonNull(
      () -> {
        FileEditor[] editors = FileEditorManager.getInstance(myFrame.getProject()).getSelectedEditors();
        checkState(editors.length > 0, "no selected editors");
        Component manifestPanel = editors[0].getComponent().getComponent(0);
        checkState(manifestPanel instanceof ManifestPanel, "not a %s: %s", ManifestPanel.class.getSimpleName(), manifestPanel);
        return new MergedManifestFixture(robot, (ManifestPanel)manifestPanel);
      });
  }

  @NotNull
  public String getSelectedTab() {
    FileEditor[] selectedEditors = FileEditorManager.getInstance(myFrame.getProject()).getSelectedEditors();
    if (selectedEditors.length > 0) {
      FileEditor editor = selectedEditors[0];
      if (editor instanceof TextEditorWithPreview) {
        TextEditorWithPreview editorWithText = (TextEditorWithPreview)editor;
        if (editorWithText.getPreferredFocusedComponent() == editorWithText.getTextEditor().getPreferredFocusedComponent()) {
          // The equivalent to the "Text" tab in the split editor is when we're in text-only mode.
          return "Text";
        }
      }
      if (editor instanceof DesignToolsSplitEditor) {
        DesignToolsSplitEditor editorWithDesigner = (DesignToolsSplitEditor)editor;
        if (editorWithDesigner.getPreferredFocusedComponent() == editorWithDesigner.getDesignerEditor().getPreferredFocusedComponent()) {
          // The equivalent to the "Design" tab in the split editor is when we're in preview-only mode.
          return "Design";
        }
      }
    }
    throw new IllegalStateException("Couldn't get the selected tab name.");
  }

  /**
   * Switch to an open tab.
   */
  public void switchToTab(@NotNull String tabName) {
    if (tabName.equals("Design") || tabName.equals("Text")) {
      // The split editor doesn't have clickable tab labels. Instead, it has IntelliJ actions that need to be clicked to switch editor type.
      FileEditor[] selectedEditors = FileEditorManager.getInstance(myFrame.getProject()).getSelectedEditors();
      checkState(selectedEditors.length > 0, "no selected editors");
      FileEditor selected = selectedEditors[0];
      checkState(selected instanceof TextEditorWithPreview, "invalid editor selected");
      Tab tab = Tab.fromName(tabName);
      assertNotNull(String.format("Can't find tab named \"%s\".", tabName), tab);
      GuiTask.execute(() -> selectSplitEditorTab(tab, (TextEditorWithPreview)selected));
    }
    else {
      TabLabel tab = waitUntilShowing(robot, new GenericTypeMatcher<TabLabel>(TabLabel.class) {
        @Override
        protected boolean isMatching(@NotNull TabLabel tabLabel) {
          return tabName.equals(tabLabel.getAccessibleContext().getAccessibleName());
        }
      });
      robot.click(tab);
    }
  }

  @NotNull
  public IdeFrameFixture getIdeFrame() {
    return myFrame;
  }

  /**
   * Common editor actions, invokable via {@link #invokeAction(EditorAction)}
   */
  public enum EditorAction {
    BACK_SPACE("EditorBackSpace"),
    COMPLETE_CURRENT_STATEMENT("EditorCompleteStatement"),
    DELETE_LINE("EditorDeleteLine"),
    LINE_END("EditorLineEnd"),
    DOWN("EditorDown"),
    ESCAPE("EditorEscape"),
    GOTO_DECLARATION("GotoDeclaration"),
    GOTO_IMPLEMENTATION("GotoImplementation"),
    SAVE("SaveAll"),
    SELECT_ALL("$SelectAll"),
    SHOW_INTENTION_ACTIONS("ShowIntentionActions"),
    SPLIT_HORIZONTALLY("SplitHorizontally"),
    SPLIT_VERTICALLY("SplitVertically"),
    TOGGLE_LINE_BREAKPOINT("ToggleLineBreakpoint"),
    UNDO("$Undo"),
    CLOSE_ALL("CloseAllEditors"),
    TEXT_END("EditorTextEnd")
    ;

    /** The {@code id} of an action mapped to a keyboard shortcut in, for example, {@code $default.xml}. */
    @NotNull private final String id;

    EditorAction(@NotNull String actionId) {
      this.id = actionId;
    }
  }

  /**
   * The different tabs of an editor; used by for example {@link #open(VirtualFile, EditorFixture.Tab)} to indicate which
   * tab should be opened
   */
  public enum Tab {
    EDITOR("Text"),
    DESIGN("Design"),
    DEFAULT(null),
    MERGED_MANIFEST("Merged Manifest"),
    ;

    /** The label in the editor, or {@code null} for the default (first) tab. */
    private final String myTabName;

    Tab(String tabName) {
      myTabName = tabName;
    }

    @Nullable
    static Tab fromName(@NotNull String name) {
      return Arrays.stream(Tab.values()).filter(tab -> name.equals(tab.myTabName)).findFirst().orElse(null);
    }
  }

  public ApkViewerFixture getApkViewer(String name) {
    switchToTab(name);
    return ApkViewerFixture.find(getIdeFrame());
  }

  @NotNull
  public LibraryEditorFixture getLibrarySymbolsFixture() {
    return LibraryEditorFixture.find(getIdeFrame());
  }

  @NotNull
  public JListFixture getAutoCompleteWindow() {
    CompletionFixture autocompletePopup = new CompletionFixture(myFrame);
    autocompletePopup.waitForCompletionsToShow();
    return autocompletePopup.getCompletionList();
  }
}
