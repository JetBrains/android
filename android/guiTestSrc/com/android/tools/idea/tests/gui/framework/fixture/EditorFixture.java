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

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.editors.manifest.ManifestEditor;
import com.android.tools.idea.editors.manifest.ManifestPanel;
import com.android.tools.idea.editors.strings.StringResourceEditor;
import com.android.tools.idea.editors.strings.StringsVirtualFile;
import com.android.tools.idea.editors.theme.ThemeEditorComponent;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.Wait;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlPreviewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemePreviewFixture;
import com.android.tools.idea.uibuilder.editor.NlEditor;
import com.android.tools.idea.uibuilder.editor.NlPreviewForm;
import com.android.tools.idea.uibuilder.editor.NlPreviewManager;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.icons.AllIcons;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.RowIcon;
import com.intellij.ui.components.JBList;
import com.intellij.ui.tabs.impl.TabLabel;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.ComponentDriver;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.FocusManager;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputMethodEvent;
import java.awt.font.TextHitInfo;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.tests.gui.framework.GuiTests.*;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertThat;
import static org.fest.reflect.core.Reflection.method;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.util.Strings.quote;
import static org.junit.Assert.*;

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
   * Performs simulation of user events on <code>{@link #target}</code>
   */
  public final Robot robot;
  private final IdeFrameFixture myFrame;

  /**
   * Constructs a new editor fixture, tied to the given project
   */
  public EditorFixture(Robot robot, IdeFrameFixture frame) {
    this.robot = robot;
    myFrame = frame;
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
   * Returns the line number of the current caret position (1-based). Note that
   * internal editor line numbers are 0-based.
   *
   * @return the current 1-based line number, or -1 if there is no current file
   */
  public int getCurrentLineNumber() {
    //noinspection ConstantConditions
    return execute(new GuiQuery<Integer>() {
      @Override
      @Nullable
      protected Integer executeInEDT() throws Throwable {
        FileEditorManager manager = FileEditorManager.getInstance(myFrame.getProject());
        Editor editor = manager.getSelectedTextEditor();
        if (editor != null) {
          CaretModel caretModel = editor.getCaretModel();
          Caret primaryCaret = caretModel.getPrimaryCaret();
          int offset = primaryCaret.getOffset();
          Document document = editor.getDocument();
          return document.getLineNumber(offset) + 1;
        }

        return -1;
      }
    });
  }

  /**
   * From the currently selected text editor, returns the text of the line where the primary caret is.
   *
   * @throws IllegalStateException if there is no currently selected text editor
   */
  @NotNull
  public String getCurrentLine() {
    // noinspection ConstantConditions
    return execute(new GuiQuery<String>() {
      @Override
      protected String executeInEDT() throws Throwable {
        Editor editor = FileEditorManager.getInstance(myFrame.getProject()).getSelectedTextEditor();
        checkState(editor != null, "no currently selected text editor");
        Caret primaryCaret = editor.getCaretModel().getPrimaryCaret();
        return editor.getDocument().getText(new TextRange(primaryCaret.getVisualLineStart(), primaryCaret.getVisualLineEnd()));
      }
    });
  }

  /**
   * Returns the text of the document in the currently selected text editor.
   *
   * @throws IllegalStateException if there is no currently selected text editor
   */
  @NotNull
  public String getCurrentFileContents() {
    // noinspection ConstantConditions
    return execute(new GuiQuery<String>() {
      @Override
      @Nullable
      protected String executeInEDT() throws Throwable {
        Editor editor = FileEditorManager.getInstance(myFrame.getProject()).getSelectedTextEditor();
        checkState(editor != null, "no currently selected text editor");
        return editor.getDocument().getImmutableCharSequence().toString();
      }
    });
  }

  /**
   * Type the given text into the editor
   *
   * @param text the text to type at the current editor position
   */
  public EditorFixture enterText(@NotNull final String text) {
    Component component = getFocusedEditor();
    if (component != null) {
      robot.enterText(text);
    }

    return this;
  }

  /**
   * Type the given text into the editor as if the user had typed it
   * with an IME (an input method editor)
   *
   * @param text the text to type at the current editor position
   */
  public EditorFixture enterImeText(@NotNull final String text) {
    final Component component = getFocusedEditor();
    if (component != null && !text.isEmpty()) {
      execute(new GuiTask() {
        @Override
        protected void executeInEDT() throws Throwable {
          // Simulate editing by sending the same IME events that we observe arriving from a real input method
          int characterCount = text.length();
          TextHitInfo caret = TextHitInfo.afterOffset(characterCount - 1);
          TextHitInfo visiblePosition = TextHitInfo.beforeOffset(0);
          AttributedCharacterIterator iterator = new AttributedString(text).getIterator();
          int id = InputMethodEvent.INPUT_METHOD_TEXT_CHANGED;
          InputMethodEvent event = new InputMethodEvent(component, id, iterator, characterCount, caret, visiblePosition);
          component.dispatchEvent(event);
        }
      });
    }

    return this;
  }

  /**
   * Press and release the given key as indicated by the {@code VK_} codes in {@link java.awt.event.KeyEvent}.
   * Used to transfer key presses to the editor which may have an effect but does not insert text into
   * the editor (e.g. pressing an arrow key to move the caret)
   *
   * @param keyCode the key code to press
   */
  public EditorFixture typeKey(int keyCode) {
    Component component = getFocusedEditor();
    if (component != null) {
      new ComponentDriver(robot).pressAndReleaseKeys(component, keyCode);
    }
    return this;
  }

  /**
   * Press (but don't release yet) the given key as indicated by the {@code VK_} codes in {@link java.awt.event.KeyEvent}.
   * Used to transfer key presses to the editor which may have an effect but does not insert text into
   * the editor (e.g. pressing an arrow key to move the caret)
   *
   * @param keyCode the key code to press
   */
  public EditorFixture pressKey(int keyCode) {
    Component component = getFocusedEditor();
    if (component != null) {
      new ComponentDriver(robot).pressKey(component, keyCode);
    }
    return this;
  }

  /**
   * Release the given key (as indicated by the {@code VK_} codes in {@link java.awt.event.KeyEvent}) which
   * must be currently pressed by a previous call to {@link #pressKey(int)}.
   *
   * @param keyCode the key code
   */
  public EditorFixture releaseKey(int keyCode) {
    Component component = getFocusedEditor();
    if (component != null) {
      new ComponentDriver(robot).releaseKey(component, keyCode);
    }
    return this;
  }

  /**
   * Requests focus in the editor
   */
  public EditorFixture requestFocus() {
    getFocusedEditor();
    return this;
  }

  /**
   * Requests focus in the editor, waits and returns editor component
   */
  @Nullable
  private JComponent getFocusedEditor() {
    Editor editor = execute(new GuiQuery<Editor>() {
      @Override
      @Nullable
      protected Editor executeInEDT() throws Throwable {
        FileEditorManager manager = FileEditorManager.getInstance(myFrame.getProject());
        return manager.getSelectedTextEditor(); // Must be called from the EDT
      }
    });

    if (editor != null) {
      JComponent contentComponent = editor.getContentComponent();
      new ComponentDriver(robot).focusAndWaitForFocusGain(contentComponent);
      assertSame(contentComponent, FocusManager.getCurrentManager().getFocusOwner());
      return contentComponent;
    } else {
      fail("Expected to find editor to focus, but there is no current editor");
      return null;
    }
  }

  /**
   * Moves the caret to the start of the given line number (1-based). Note that
   * the internal editor lines are 0-based.
   *
   * @param lineNumber the line number.
   */
  @NotNull
  public EditorFixture moveToLine(final int lineNumber) {
    assertThat(lineNumber).isAtLeast(0);
    final Ref<Boolean> doneScrolling = new Ref<>(false);

    Point lineStartPoint = execute(new GuiQuery<Point>() {
      @Override
      protected Point executeInEDT() throws Throwable {
        FileEditorManager manager = FileEditorManager.getInstance(myFrame.getProject());
        Editor editor = manager.getSelectedTextEditor();
        if (editor != null) {
          Document document = editor.getDocument();
          int offset = document.getLineStartOffset(lineNumber - 1);
          LogicalPosition position = editor.offsetToLogicalPosition(offset);
          editor.getScrollingModel().scrollTo(position, ScrollType.MAKE_VISIBLE);
          editor.getScrollingModel().runActionOnScrollingFinished(() -> doneScrolling.set(true));
          return editor.logicalPositionToXY(position);
        }
        return null;
      }
    });

    Wait.seconds(10).expecting("scrolling to finish").until(() -> doneScrolling.get());

    JComponent focusedEditor = getFocusedEditor();
    if (focusedEditor != null && lineStartPoint != null) {
      robot.click(focusedEditor, lineStartPoint);
    }
    else {
      fail("Could not move to line " + lineNumber + " in the editor");
    }
    return this;
  }

  /**
   * Moves the caret to the start of the first occurrence of {@code after} immediately following {@code before} in the selected text editor.
   *
   * @throws IllegalStateException if there is no selected text editor or if no match is found
   */
  @NotNull
  public EditorFixture moveBetween(@NotNull String before, @NotNull String after) {
    String regex = String.format("%s()%s", Pattern.quote(before), Pattern.quote(after));
    Matcher matcher = Pattern.compile(regex).matcher(getCurrentFileContents());
    matcher.find();
    int offset = matcher.start(1);

    final Ref<Boolean> doneScrolling = new Ref<>(false);

    Point offsetPoint = execute(new GuiQuery<Point>() {
      @Override
      protected Point executeInEDT() throws Throwable {
        FileEditorManager manager = FileEditorManager.getInstance(myFrame.getProject());
        Editor editor = manager.getSelectedTextEditor();
        if (editor != null) {
          LogicalPosition position = editor.offsetToLogicalPosition(offset);
          editor.getScrollingModel().scrollTo(position, ScrollType.MAKE_VISIBLE);
          editor.getScrollingModel().runActionOnScrollingFinished(() -> doneScrolling.set(true));
          return editor.logicalPositionToXY(position);
        }
        return null;
      }
    });

    Wait.seconds(10).expecting("scrolling to finish").until(() -> doneScrolling.get());

    JComponent focusedEditor = getFocusedEditor();
    if (focusedEditor != null && offsetPoint != null) {
      robot.click(focusedEditor, offsetPoint);
    }
    else {
      fail("Could not move to offset " + offset + " in the editor");
    }
    return this;
  }

  /**
   * Given a {@code regex} with one capturing group, selects the subsequence captured in the first match found in the selected text editor.
   *
   * @throws IllegalStateException if there is no currently selected text editor or no match is found
   * @throws IllegalArgumentException if {@code regex} does not have exactly one capturing group
   */
  public EditorFixture select(String regex) {
    Matcher matcher = Pattern.compile(regex).matcher(getCurrentFileContents());
    checkArgument(matcher.groupCount() == 1, "must have exactly one capturing group: %s", regex);
    matcher.find();
    return select(matcher.start(1), matcher.end(1));
  }

  /**
   * Selects the given range. If the first and second offsets are the same, it simply
   * moves the caret to the given position. The caret is always placed at the second offset,
   * <b>which is allowed to be smaller than the first offset</b>. Calling {@code select(10, 7)}
   * would be the same as dragging the mouse from offset 10 to offset 7 and releasing the mouse
   * button; the caret is now at the beginning of the selection.
   *
   * @param firstOffset  the character offset where we start the selection, or -1 to remove the selection
   * @param secondOffset the character offset where we end the selection, which can be an earlier
   *                     offset than the firstOffset
   * @throws IllegalStateException if there is no currently selected text editor
   */
  private EditorFixture select(final int firstOffset, final int secondOffset) {
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        // TODO: Do this via mouse drags!
        Editor editor = FileEditorManager.getInstance(myFrame.getProject()).getSelectedTextEditor();
        checkState(editor != null, "no currently selected text editor");
        editor.getCaretModel().getPrimaryCaret().setSelection(firstOffset, secondOffset);
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      }
    });

    return this;
  }

  /**
   * Closes the current editor
   */
  public EditorFixture close() {
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        VirtualFile currentFile = getCurrentFile();
        if (currentFile != null) {
          FileEditorManager manager = FileEditorManager.getInstance(myFrame.getProject());
          manager.closeFile(currentFile);
        }
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
  public EditorFixture selectEditorTab(@NotNull final Tab tab) {
    String tabName = tab.myTabName;
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        VirtualFile currentFile = getCurrentFile();
        assertNotNull("Can't switch to tab " + tabName + " when no file is open in the editor", currentFile);
        FileEditorManager manager = FileEditorManager.getInstance(myFrame.getProject());
        FileEditor[] editors = manager.getAllEditors(currentFile);
        FileEditor target = null;
        for (FileEditor editor : editors) {
          if (tabName == null || tabName.equals(editor.getName())) {
            target = editor;
            break;
          }
        }
        if (target != null) {
          // Have to use reflection
          //FileEditorManagerImpl#setSelectedEditor(final FileEditor editor)
          method("setSelectedEditor").withParameterTypes(FileEditor.class).in(manager).invoke(target);
          return;
        }
        List<String> tabNames = Lists.newArrayList();
        for (FileEditor editor : editors) {
          tabNames.add(editor.getName());
        }
        fail("Could not find editor tab \"" + (tabName != null ? tabName : "<default>") + "\": Available tabs = " + tabNames);
      }
    });
    return this;
  }

  /**
   * Opens up a different file. This will run through the "Open File..." dialog to
   * find and select the given file.
   *
   * @param file the file to open
   * @param tab which tab to open initially, if there are multiple editors
   */
  public EditorFixture open(@NotNull final VirtualFile file, @NotNull final Tab tab) {
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        // TODO: Use UI to navigate to the file instead
        Project project = myFrame.getProject();
        FileEditorManager manager = FileEditorManager.getInstance(project);
        if (tab == Tab.EDITOR) {
          manager.openTextEditor(new OpenFileDescriptor(project, file), true);
        }
        else {
          manager.openFile(file, true);
        }
      }
    });

    selectEditorTab(tab);

    //noinspection ConstantConditions
    Wait.minutes(2).expecting("file " + quote(file.getPath()) + " to be opened").until(
      () -> execute(
        new GuiQuery<Boolean>() {
          @Override
          protected Boolean executeInEDT() throws Throwable {
            return file.equals(getCurrentFile());
          }
        }));

    myFrame.requestFocusIfLost();
    robot.waitForIdle();

    return this;
  }

  /**
   * Opens up a different file. This will run through the "Open File..." dialog to
   * find and select the given file.
   *
   * @param file the project-relative path (with /, not File.separator, as the path separator)
   * @param tab which tab to open initially, if there are multiple editors
   */
  public EditorFixture open(@NotNull final String relativePath, @NotNull Tab tab) {
    assertFalse("Should use '/' in test relative paths, not File.separator", relativePath.contains("\\"));
    VirtualFile file = myFrame.findFileByRelativePath(relativePath, true);
    return open(file, tab);
  }

  /**
   * Like {@link #open(String, com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab)} but
   * always uses the default tab
   *
   * @param file the project-relative path (with /, not File.separator, as the path separator)
   */
  public EditorFixture open(@NotNull final String relativePath) {
    return open(relativePath, Tab.DEFAULT);
  }

  /** Invokes {@code editorAction} via its (first) keyboard shortcut in the active keymap. */
  public EditorFixture invokeAction(@NotNull EditorAction editorAction) {
    AnAction anAction = ActionManager.getInstance().getAction(editorAction.id);
    assertTrue(editorAction.id + " is not enabled", anAction.getTemplatePresentation().isEnabled());

    Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    Shortcut shortcut = keymap.getShortcuts(editorAction.id)[0];
    if (shortcut instanceof KeyboardShortcut) {
      KeyboardShortcut cs = (KeyboardShortcut)shortcut;
      KeyStroke firstKeyStroke = cs.getFirstKeyStroke();
      Component component = getFocusedEditor();
      if (component != null) {
        ComponentDriver driver = new ComponentDriver(robot);
        driver.pressAndReleaseKey(component, firstKeyStroke.getKeyCode(), new int[]{firstKeyStroke.getModifiers()});
        KeyStroke secondKeyStroke = cs.getSecondKeyStroke();
        if (secondKeyStroke != null) {
          driver.pressAndReleaseKey(component, secondKeyStroke.getKeyCode(), new int[]{secondKeyStroke.getModifiers()});
        }
      } else {
        fail("Editor not focused for action");
      }
    }
    else {
      fail("Unsupported shortcut type " + shortcut.getClass().getName());
    }
    return this;
  }

  @NotNull
  public List<String> getHighlights(HighlightSeverity severity) {
    List<String> infos = Lists.newArrayList();
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
  public EditorFixture waitForCodeAnalysisHighlightCount(@NotNull final HighlightSeverity severity, int expected) {
    // Changing Java source level, for example, triggers compilation first; code analysis starts afterward
    waitForBackgroundTasks(robot);

    FileFixture file = getCurrentFileFixture();
    file.waitForCodeAnalysisHighlightCount(severity, expected);
    return this;
  }

  @NotNull
  public EditorFixture waitUntilErrorAnalysisFinishes() {
    FileFixture file = getCurrentFileFixture();
    file.waitUntilErrorAnalysisFinishes();
    robot.waitForIdle();
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
   * then waits for the actions to be displayed and finally picks the one with the given label prefix
   *
   * @param labelPrefix the prefix of the action description to be shown
   */
  @NotNull
  public EditorFixture invokeQuickfixAction(@NotNull String labelPrefix) {
    waitForQuickfix();
    invokeAction(EditorAction.SHOW_INTENTION_ACTIONS);
    JBList popup = waitForPopup(robot);
    clickPopupMenuItem(labelPrefix, popup, robot);
    return this;
  }

  /**
   * Returns a fixture around the layout editor, <b>if</b> the currently edited file
   * is a layout file and it is currently showing the layout editor tab or the parameter
   * requests that it be opened if necessary
   *
   * @param switchToTabIfNecessary if true, switch to the design tab if it is not already showing
   * @return a layout editor fixture, or null if the current file is not a layout file or the
   *     wrong tab is showing
   */
  @Nullable
  public NlEditorFixture getLayoutEditor(boolean switchToTabIfNecessary) {
    VirtualFile currentFile = getCurrentFile();
    if (ResourceHelper.getFolderType(currentFile) != ResourceFolderType.LAYOUT) {
      return null;
    }

    if (switchToTabIfNecessary) {
      selectEditorTab(Tab.DESIGN);
    }

    return execute(new GuiQuery<NlEditorFixture>() {
      @Override
      @Nullable
      protected NlEditorFixture executeInEDT() throws Throwable {
        FileEditorManager manager = FileEditorManager.getInstance(myFrame.getProject());
        FileEditor[] editors = manager.getSelectedEditors();
        if (editors.length == 0) {
          return null;
        }
        FileEditor selected = editors[0];
        if (!(selected instanceof NlEditor)) {
          return null;
        }

        return new NlEditorFixture(myFrame.robot(), myFrame, (NlEditor)selected);
      }
    });
  }

  /**
   * Returns a fixture around the layout preview window, <b>if</b> the currently edited file
   * is a layout file and it the XML editor tab of the layout is currently showing.
   *
   * @param switchToTabIfNecessary if true, switch to the editor tab if it is not already showing
   * @return a layout preview fixture, or null if the current file is not a layout file or the
   *     wrong tab is showing
   */
  @Nullable
  public NlPreviewFixture getLayoutPreview(boolean switchToTabIfNecessary) {
    VirtualFile currentFile = getCurrentFile();
    if (ResourceHelper.getFolderType(currentFile) != ResourceFolderType.LAYOUT) {
      return null;
    }

    if (switchToTabIfNecessary) {
      selectEditorTab(Tab.EDITOR);
    }

    Boolean visible = execute(new GuiQuery<Boolean>() {
      @Override
      protected Boolean executeInEDT() throws Throwable {
        NlPreviewManager manager = NlPreviewManager.getInstance(myFrame.getProject());
        NlPreviewForm toolWindowForm = manager.getPreviewForm();
        return toolWindowForm != null && toolWindowForm.getSurface().isShowing();
      }
    });
    if (visible == null || !visible) {
      myFrame.invokeMenuPath("View", "Tool Windows", "Preview");
    }

    Wait.minutes(2).expecting("Preview window to be visible")
      .until(() -> {
        NlPreviewManager manager = NlPreviewManager.getInstance(myFrame.getProject());
        NlPreviewForm toolWindowForm = manager.getPreviewForm();
        return toolWindowForm != null && toolWindowForm.getSurface().isShowing();
      });

    return new NlPreviewFixture(myFrame.getProject(), myFrame, myFrame.robot());
  }

  /**
   * Returns a fixture around the {@link com.android.tools.idea.editors.strings.StringResourceEditor} <b>if</b> the currently
   * displayed editor is a translations editor.
   */
  @Nullable
  public TranslationsEditorFixture getTranslationsEditor() {
    VirtualFile currentFile = getCurrentFile();
    if (!(currentFile instanceof StringsVirtualFile)) {
      return null;
    }

    return execute(new GuiQuery<TranslationsEditorFixture>() {
      @Override
      @Nullable
      protected TranslationsEditorFixture executeInEDT() throws Throwable {
        FileEditorManager manager = FileEditorManager.getInstance(myFrame.getProject());
        FileEditor[] editors = manager.getSelectedEditors();
        if (editors.length == 0) {
          return null;
        }
        FileEditor selected = editors[0];
        if (!(selected instanceof StringResourceEditor)) {
          return null;
        }

        return new TranslationsEditorFixture(robot, (StringResourceEditor)selected);
      }
    });
  }

  /**
   * Returns a fixture around the {@link com.android.tools.idea.editors.theme.ThemeEditor} <b>if</b> the currently
   * displayed editor is a theme editor.
   */
  @NotNull
  public ThemeEditorFixture getThemeEditor() {
    return new ThemeEditorFixture(robot, GuiTests.waitUntilFound(robot, GuiTests.matcherForType(ThemeEditorComponent.class)));
  }

  @NotNull
  public MergedManifestFixture getMergedManifestEditor() {
    robot.waitForIdle();
    MergedManifestFixture found = execute(new GuiQuery<MergedManifestFixture>() {
      @Override
      @Nullable
      protected MergedManifestFixture executeInEDT() throws Throwable {
        FileEditorManager manager = FileEditorManager.getInstance(myFrame.getProject());
        FileEditor[] editors = manager.getSelectedEditors();
        if (editors.length == 0) {
          return null;
        }
        FileEditor selected = editors[0];
        if (!(selected instanceof ManifestEditor)) {
          return null;
        }
        return new MergedManifestFixture(robot, (ManifestPanel)selected.getComponent().getComponent(0));
      }
    });
    if (found == null) {
      throw new AssertionError("manifest editor not found");
    }
    return found;
  }

  /**
   * Returns a fixture around the theme preview window, <b>if</b> the currently edited file
   * is a styles file and if the XML editor tab of the layout is currently showing.
   *
   * @param switchToTabIfNecessary if true, switch to the editor tab if it is not already showing
   * @return the theme preview fixture
   */
  @Nullable
  public ThemePreviewFixture getThemePreview(boolean switchToTabIfNecessary) {
    VirtualFile currentFile = getCurrentFile();
    if (ResourceHelper.getFolderType(currentFile) != ResourceFolderType.VALUES) {
      return null;
    }

    if (switchToTabIfNecessary) {
      selectEditorTab(Tab.EDITOR);
    }

    Boolean visible = GuiActionRunner.execute(new GuiQuery<Boolean>() {
      @Override
      protected Boolean executeInEDT() throws Throwable {
        final ToolWindow window = ToolWindowManager.getInstance(myFrame.getProject()).getToolWindow("Theme Preview");
        return window.isActive();
      }
    });
    if (visible == null || !visible) {
      myFrame.invokeMenuPath("View", "Tool Windows", "Theme Preview");
    }

    Wait.minutes(2).expecting("Theme Preview window to be visible").until(
      () -> GuiActionRunner.execute(
        new GuiQuery<Boolean>() {
          @Override
          protected Boolean executeInEDT() throws Throwable {
            ToolWindow window = ToolWindowManager.getInstance(myFrame.getProject()).getToolWindow("Theme Preview");
            return window != null && window.isVisible();
          }
        }));

    // Wait for it to be fully opened
    robot.waitForIdle();
    return new ThemePreviewFixture(robot, myFrame.getProject());
  }

  /**
   * Switch to an open tab
   */
  public void switchToTab(@NotNull String tabName) {
    TabLabel tab = waitUntilShowing(robot, new GenericTypeMatcher<TabLabel>(TabLabel.class) {
      @Override
      protected boolean isMatching(@NotNull TabLabel tabLabel) {
        return tabName.equals(tabLabel.getAccessibleContext().getAccessibleName());
      }
    });
    robot.click(tab);
  }

  /**
   * Common editor actions, invokable via {@link #invokeAction(EditorAction)}
   */
  public enum EditorAction {
    SHOW_INTENTION_ACTIONS("ShowIntentionActions"),
    FORMAT("ReformatCode"),
    SAVE("SaveAll"),
    UNDO("$Undo"),
    BACK_SPACE("EditorBackSpace"),
    COMPLETE_CURRENT_STATEMENT("EditorCompleteStatement"),
    DELETE_LINE("EditorDeleteLine"),
    TOGGLE_COMMENT("CommentByLineComment"),
    GOTO_DECLARATION("GotoDeclaration"),
    RUN_FROM_CONTEXT("RunClass"),
    ESCAPE("EditorEscape"),
    ;

    /** The {@code id} of an action mapped to a keyboard shortcut in, for example, {@code Keymap_Default.xml}. */
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
  }
}
