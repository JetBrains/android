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
import com.android.tools.idea.common.editor.NlEditor;
import com.android.tools.idea.editors.manifest.ManifestPanel;
import com.android.tools.idea.editors.strings.StringResourceEditor;
import com.android.tools.idea.editors.theme.ThemeEditorComponent;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlPreviewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemePreviewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.translations.TranslationsEditorFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.editor.NlPreviewForm;
import com.android.tools.idea.uibuilder.editor.NlPreviewManager;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.RowIcon;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBEditorTabs;
import com.intellij.ui.tabs.impl.TabLabel;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.driver.ComponentDriver;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.android.tools.idea.tests.gui.framework.GuiTests.*;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.fest.reflect.core.Reflection.method;
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
   * Performs simulation of user events on <code>target</code>
   */
  final Robot robot;
  private final IdeFrameFixture myFrame;

  /**
   * Constructs a new editor fixture, tied to the given project
   */
  EditorFixture(Robot robot, IdeFrameFixture frame) {
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
   * From the currently selected text editor, returns the line number where the primary caret is.
   * <p>
   * This line number is conventionally 1-based, unlike the 0-based line numbers in {@link Editor}.
   *
   * @throws IllegalStateException if there is no currently selected text editor
   */
  public int getCurrentLineNumber() {
    return GuiQuery.getNonNull(
      () -> {
        Editor editor = FileEditorManager.getInstance(myFrame.getProject()).getSelectedTextEditor();
        checkState(editor != null, "no currently selected text editor");
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
        Editor editor = FileEditorManager.getInstance(myFrame.getProject()).getSelectedTextEditor();
        checkState(editor != null, "no currently selected text editor");
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
        Editor editor = FileEditorManager.getInstance(myFrame.getProject()).getSelectedTextEditor();
        checkState(editor != null, "no currently selected text editor");
        return editor.getDocument().getImmutableCharSequence().toString();
      });
  }

  /**
   * Type the given text into the editor
   *
   * @param text the text to type at the current editor position
   */
  public EditorFixture enterText(@NotNull final String text) {
    robot.enterText(text, getFocusedEditor());
    return this;
  }

  /**
   * Requests focus in the editor, waits and returns editor component
   */
  @NotNull
  private JComponent getFocusedEditor() {
    Editor editor = GuiQuery.getNonNull(() -> {
      Editor selectedTextEditor = FileEditorManager.getInstance(myFrame.getProject()).getSelectedTextEditor();
      checkState(selectedTextEditor != null, "no currently selected text editor");
      return selectedTextEditor;
    });

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
  public EditorFixture select(String regex) {
    Matcher matcher = Pattern.compile(regex).matcher(getCurrentFileContents());
    checkArgument(matcher.groupCount() == 1, "must have exactly one capturing group: %s", regex);
    // noinspection ResultOfMethodCallIgnored
    matcher.find();
    int start = matcher.start(1);
    int end = matcher.end(1);
    SelectTarget selectTarget = GuiQuery.getNonNull(
      () -> {
        Editor editor = FileEditorManager.getInstance(myFrame.getProject()).getSelectedTextEditor();
        checkState(editor != null, "no currently selected text editor");
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
        VirtualFile file = myFrame.findFileByRelativePath(relativePath, true);
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
  public EditorFixture selectEditorTab(@NotNull final Tab tab) {
    String tabName = tab.myTabName;
    Wait.seconds(5).expecting(String.format("find editor tab '%s'", tabName == null ? "<default>" : tabName)).until(
      () -> GuiQuery.getNonNull(() -> {
        VirtualFile currentFile = getCurrentFile();
        assertNotNull("Can't switch to tab " + tabName + " when no file is open in the editor", currentFile);
        FileEditorManager manager = FileEditorManager.getInstance(myFrame.getProject());
        for (FileEditor editor : manager.getAllEditors(currentFile)) {
          if (tabName == null || tabName.equals(editor.getName())) {
            // Have to use reflection
            //FileEditorManagerImpl#setSelectedEditor(final FileEditor editor)
            method("setSelectedEditor").withParameterTypes(FileEditor.class).in(manager).invoke(editor);
            return true;
          }
        }
        return false;
      }));
    return this;
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
  public EditorFixture open(@NotNull final VirtualFile file, @NotNull final Tab tab) {
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

    Wait.seconds(10).expecting("file " + quote(file.getPath()) + " to be opened and loaded").until(() -> {
      if (!file.equals(getCurrentFile())) {
        return false;
      }

      FileEditor fileEditor = FileEditorManager.getInstance(myFrame.getProject()).getSelectedEditor(file);
      JComponent editorComponent = fileEditor.getComponent();
      if (editorComponent instanceof JBLoadingPanel) {
        return !((JBLoadingPanel)editorComponent).isLoading();
      }
      return true;
    });

    Editor editor = GuiQuery.get(() -> FileEditorManager.getInstance(myFrame.getProject()).getSelectedTextEditor());
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
  public EditorFixture open(@NotNull final String relativePath, @NotNull Tab tab) {
    assertFalse("Should use '/' in test relative paths, not File.separator", relativePath.contains("\\"));
    VirtualFile file = myFrame.findFileByRelativePath(relativePath, true);
    return open(file, tab);
  }

  @NotNull
  public EditorFixture open(@NotNull Path relativePath) {
    return open(relativePath, Tab.DEFAULT);
  }

  /**
   * Like {@link #open(String, com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab)} but
   * always uses the default tab
   *
   * @param relativePath the project-relative path (with /, not File.separator, as the path separator)
   */
  public EditorFixture open(@NotNull final String relativePath) {
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

  @Nullable
  public TextEditorFixture getVisibleTextEditor(@NotNull String relativePath) {
    return GuiQuery.get(
      () -> {
        FileEditor[] editors = FileEditorManager.getInstance(myFrame.getProject()).getAllEditors();
        for (FileEditor editor : editors) {
          if (editor instanceof TextEditor && editor.getComponent().isVisible()) {
            TextEditor textEditor = (TextEditor)editor;
            Document document = textEditor.getEditor().getDocument();
            PsiFile psiFile = PsiDocumentManager.getInstance(myFrame.getProject()).getPsiFile(document);
            if (psiFile != null && psiFile.getName().endsWith(relativePath)) {
              return new TextEditorFixture(robot, textEditor);
            }
          }
        }
        return null;
      });
  }

  @NotNull
  public EditorNotificationPanelFixture awaitNotification(@NotNull String text) {
    JLabel label = waitUntilShowing(robot, JLabelMatcher.withText(text));
    EditorNotificationPanel notificationPanel = (EditorNotificationPanel)label.getParent().getParent();
    return new EditorNotificationPanelFixture(myFrame, notificationPanel);
  }

  @NotNull
  public EditorFixture checkNoNotification() {
    Collection<EditorNotificationPanel> notificationPanels = robot.finder().findAll(Matchers.byType(EditorNotificationPanel.class));
    if (!notificationPanels.isEmpty()) {
      String notifications = notificationPanels.stream()
        .map(p -> p.getIntentionAction().getText())
        .collect(Collectors.joining(", "));
      throw new AssertionError("unwanted notifications: " + notifications);
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
   * Returns a fixture around the layout editor, <b>if</b> the currently edited file
   * is a layout file and it is currently showing the layout editor tab or the parameter
   * requests that it be opened if necessary
   *
   * @param switchToTabIfNecessary if true, switch to the design tab if it is not already showing
   * @throws IllegalStateException if there is no selected editor or it is not a {@link NlEditor}
   */
  @NotNull
  public NlEditorFixture getLayoutEditor(boolean switchToTabIfNecessary) {
    if (switchToTabIfNecessary) {
      selectEditorTab(Tab.DESIGN);
    }

    // Wait for the editor to do any initializations
    robot.waitForIdle();

    return GuiQuery.getNonNull(
      () -> {
        FileEditor[] editors = FileEditorManager.getInstance(myFrame.getProject()).getSelectedEditors();
        checkState(editors.length > 0, "no selected editors");
        FileEditor selected = editors[0];
        checkState(selected instanceof NlEditor, "not a %s: %s", NlEditor.class.getSimpleName(), selected);
        return new NlEditorFixture(myFrame.robot(), (NlEditor)selected);
      });
  }

  /**
   * Returns a fixture around the layout preview window, <b>if</b> the currently edited file
   * is a layout file and it the XML editor tab of the layout is currently showing.
   *
   * @param switchToTabIfNecessary if true, switch to the editor tab if it is not already showing
   * @return a layout preview fixture, or null if the current file is not a layout file or the
   * wrong tab is showing
   */
  @NotNull
  public NlPreviewFixture getLayoutPreview(boolean switchToTabIfNecessary) {
    if (switchToTabIfNecessary) {
      selectEditorTab(Tab.EDITOR);
    }

    PreviewWindowFixture previewWindow = getPreviewWindow();
    previewWindow.activateAndWaitUntilIsVisible(10);
    return new NlPreviewFixture(myFrame.getProject(), myFrame.robot());
  }

  private PreviewWindowFixture getPreviewWindow() {
    return new PreviewWindowFixture(myFrame.getProject(), myFrame.robot());
  }

  public boolean isPreviewVisible() {
    return NlPreviewManager.getInstance(myFrame.getProject()).isPreviewVisible();
  }

  public boolean isPreviewShowing(@NotNull String fileName) {
    return GuiQuery.getNonNull(() -> {
      NlPreviewForm preview = NlPreviewManager.getInstance(myFrame.getProject()).getPreviewForm();
      return preview.getSurface().isShowing() && getCurrentFileName().equals(preview.getFileName());
    });
  }

  public int getPreviewUpdateCount() {
    return GuiQuery.getNonNull(
      () -> NlPreviewManager.getInstance(myFrame.getProject()).getUpdateCount());
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
        return new TranslationsEditorFixture(robot);
      });
  }

  /**
   * Returns a fixture around the {@link com.android.tools.idea.editors.theme.ThemeEditor} <b>if</b> the currently
   * displayed editor is a theme editor.
   */
  @NotNull
  public ThemeEditorFixture getThemeEditor() {
    return new ThemeEditorFixture(robot, waitUntilFound(robot, Matchers.byType(ThemeEditorComponent.class)));
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

    boolean visible = GuiQuery.getNonNull(
      () -> ToolWindowManager.getInstance(myFrame.getProject()).getToolWindow("Theme Preview").isActive());
    if (!visible) {
      myFrame.invokeMenuPath("View", "Tool Windows", "Theme Preview");
    }

    Wait.seconds(1).expecting("Theme Preview window to be visible").until(() -> GuiQuery.getNonNull(() -> {
      ToolWindow window = ToolWindowManager.getInstance(myFrame.getProject()).getToolWindow("Theme Preview");
      return window != null && window.isVisible();
    }));

    // Wait for it to be fully opened
    robot.waitForIdle();
    return new ThemePreviewFixture(robot, myFrame.getProject());
  }

  @NotNull
  public String getSelectedTab() {
    return robot.finder().find(
      new GenericTypeMatcher<JBEditorTabs>(JBEditorTabs.class) {
        @Override
        protected boolean isMatching(@NotNull JBEditorTabs component) {
          for (TabInfo tabInfo : component.getTabs()) {
            String text = tabInfo.getText();
            if (!("Design".equals(text)
                  || "Text".equals(text))) {
              return false;
            }
          }
          return true;
        }
      }
    ).getSelectedInfo().getText();
  }

  /**
   * Switch to an open tab
   */
  public EditorFixture switchToTab(@NotNull String tabName) {
    TabLabel tab = waitUntilShowing(robot, new GenericTypeMatcher<TabLabel>(TabLabel.class) {
      @Override
      protected boolean isMatching(@NotNull TabLabel tabLabel) {
        return tabName.equals(tabLabel.getAccessibleContext().getAccessibleName());
      }
    });
    robot.click(tab);
    return this;
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
