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
package com.android.tools.idea.uibuilder.structure;

import static com.android.AndroidXConstants.APP_BAR_LAYOUT;
import static com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_CHAIN;
import static com.android.AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_HELPER;
import static com.android.AndroidXConstants.CLASS_NESTED_SCROLL_VIEW;
import static com.android.AndroidXConstants.COLLAPSING_TOOLBAR_LAYOUT;
import static com.android.AndroidXConstants.CONSTRAINT_LAYOUT;
import static com.android.AndroidXConstants.COORDINATOR_LAYOUT;
import static com.android.SdkConstants.ABSOLUTE_LAYOUT;
import static com.android.SdkConstants.ATTR_BARRIER_DIRECTION;
import static com.android.SdkConstants.ATTR_LAYOUT_START_TO_END_OF;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.BUTTON;
import static com.android.SdkConstants.CONSTRAINT_BARRIER_END;
import static com.android.SdkConstants.CONSTRAINT_REFERENCED_IDS;
import static com.android.SdkConstants.IMAGE_VIEW;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.RELATIVE_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;
import static com.android.tools.idea.common.LayoutTestUtilities.findActionForKey;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.adtui.workbench.ComponentStack;
import com.android.tools.idea.common.LayoutTestUtilities;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.actions.GotoComponentAction;
import com.android.tools.idea.common.fixtures.DropTargetDropEventBuilder;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.surface.DesignSurfaceActionHandler;
import com.android.tools.idea.common.util.NlTreeDumper;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintHelperHandler;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.util.MockCopyPasteManager;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.LineColumn;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ui.UIUtil;
import java.awt.Rectangle;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;

public class NlComponentTreeTest extends LayoutTestCase {

  @Mock
  private BrowserLauncher myBrowserLauncher;
  @Mock
  private DataContext myDataContext;
  @Nullable
  private ComponentStack componentStack = null;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    replaceApplicationService(CopyPasteManager.class, new MockCopyPasteManager());
    registerApplicationService(BrowserLauncher.class, myBrowserLauncher);
    componentStack = new ComponentStack(getProject());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      // Null out all fields, since otherwise they're retained for the lifetime of the suite (which can be long if e.g. you're running many
      // tests through IJ)
      myBrowserLauncher = null;
      myDataContext = null;
      if (componentStack != null) {
        componentStack.restore();
        componentStack = null;
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testTreeStructure() {
    NlComponentTree tree = createTree(createModel());
    UIUtil.dispatchAllInvocationEvents();
    assertEquals("<RelativeLayout>  [expanded]\n" +
                 "    <LinearLayout>  [expanded]\n" +
                 "        <Button>\n" +
                 "    <TextView>\n" +
                 "    <AbsoluteLayout>\n", toTree(tree));
  }

  public void testTreeStructureOfAppBar() {
    NlComponentTree tree = createTree(createModelWithAppBar());
    UIUtil.dispatchAllInvocationEvents();
    assertEquals("<android.support.design.widget.CoordinatorLayout>  [expanded]\n" +
                 "    <android.support.design.widget.AppBarLayout>  [expanded]\n" +
                 "        <android.support.design.widget.CollapsingToolbarLayout>  [expanded]\n" +
                 "            <ImageView>\n" +
                 "            <android.support.v7.widget.Toolbar>\n" +
                 "    <android.support.v4.widget.NestedScrollView>  [expanded]\n" +
                 "        <TextView>\n", toTree(tree));
  }

  public void testSelectionInTreeIsPropagatedToModel() {
    SyncNlModel model = createModel();
    NlComponentTree tree = createTree(model);
    SelectionModel selectionModel = model.getSurface().getSelectionModel();
    assertNull(tree.getSelectionPaths());
    assertFalse(selectionModel.getSelection().iterator().hasNext());

    NlComponent root = (NlComponent)tree.getModel().getRoot();
    NlComponent node1 = root.getChild(1);
    NlComponent node2 = root.getChild(2);
    tree.addSelectionPath(new TreePath(new Object[]{root, node1}));
    tree.addSelectionPath(new TreePath(new Object[]{root, node2}));

    Iterator<NlComponent> selected = selectionModel.getSelection().iterator();
    assertEquals(node1, selected.next());
    assertEquals(node2, selected.next());
    assertFalse(selected.hasNext());
  }

  public void testSelectionInModelIsShownInTree() {
    SyncNlModel model = createModel();
    SelectionModel selectionModel = model.getSurface().getSelectionModel();
    NlComponent textView = findFirst(model, TEXT_VIEW);
    NlComponent button = findFirst(model, BUTTON);

    NlComponentTree tree = createTree(model);
    assertNull(tree.getSelectionPaths());
    assertFalse(selectionModel.getSelection().iterator().hasNext());
    selectionModel.toggle(textView);
    selectionModel.toggle(button);

    TreePath[] selection = tree.getSelectionPaths();
    assertThat(selection).isNotNull();

    assertThat(selection.length).isEqualTo(2);
    assertThat(selection[0].getLastPathComponent()).isSameAs(textView);
    assertThat(selection[1].getLastPathComponent()).isSameAs(button);
  }

  @SuppressWarnings("UnnecessaryLocalVariable")
  public void testHierarchyUpdate() {
    SyncNlModel model = createModel();
    NlComponent textView = findFirst(model, TEXT_VIEW);
    NlComponent button = findFirst(model, BUTTON);
    NlComponent linearLayout = findFirst(model, LINEAR_LAYOUT);
    NlComponent absoluteLayout = findFirst(model, ABSOLUTE_LAYOUT);
    NlComponent relativeLayout = findFirst(model, RELATIVE_LAYOUT);

    NlComponentTree tree = createTree(model);
    // Extract xml
    XmlTag tagLinearLayout = checkNotNull(linearLayout.getTag());
    XmlTag tagTextView = checkNotNull(textView.getTag());
    XmlTag tagAbsoluteLayout = checkNotNull(absoluteLayout.getTag());

    // Mix the component references
    relativeLayout.setChildren(null);
    linearLayout.setTag(tagAbsoluteLayout);
    linearLayout.setSnapshot(null);
    linearLayout.setChildren(null);
    textView.setTag(tagLinearLayout);
    textView.setSnapshot(null);
    textView.setChildren(null);
    absoluteLayout.setTag(tagTextView);
    absoluteLayout.setSnapshot(null);
    absoluteLayout.setChildren(null);

    NlComponent newRelativeLayout = relativeLayout;
    NlComponent newLinearLayout = textView;
    NlComponent newButton = button;
    NlComponent newTextView = absoluteLayout;
    NlComponent newAbsoluteLayout = linearLayout;
    newRelativeLayout.addChild(newLinearLayout);
    newRelativeLayout.addChild(newTextView);
    newRelativeLayout.addChild(newAbsoluteLayout);

    newLinearLayout.addChild(newButton);

    tree.modelDerivedDataChanged(model);
    UIUtil.dispatchAllInvocationEvents();
    assertEquals("<RelativeLayout>  [expanded]\n" +
                 "    <LinearLayout>\n" +
                 "        <Button>\n" +
                 "    <TextView>\n" +
                 "    <AbsoluteLayout>\n", toTree(tree));
  }

  public void testPasteIsNotPossibleWhenMultipleComponentsAreSelected() {
    SyncNlModel model = createModel();
    NlComponentTree tree = createTree(model);
    SelectionModel selectionModel = model.getSurface().getSelectionModel();
    DesignSurfaceActionHandler actionHandler = getActionHandler(tree);
    copy(tree, findFirst(model, TEXT_VIEW));

    selectionModel.toggle(findFirst(model, LINEAR_LAYOUT));
    selectionModel.toggle(findFirst(model, ABSOLUTE_LAYOUT));
    assertThat(actionHandler.isPasteEnabled(myDataContext)).isFalse();
    assertThat(actionHandler.isPastePossible(myDataContext)).isFalse();
    actionHandler.performPaste(myDataContext);
    assertThat(toTree(tree)).isEqualTo("<RelativeLayout>  [expanded]\n" +
                                       "    <LinearLayout>  [expanded]  [selected]\n" +
                                       "        <Button>\n" +
                                       "    <TextView>\n" +
                                       "    <AbsoluteLayout>  [selected]\n");
  }

  public void testCopyIntoRootWhenNothingIsSelected() throws Exception {
    SyncNlModel model = createModel();
    NlComponentTree tree = createTree(model);
    DesignSurfaceActionHandler actionHandler = getActionHandler(tree);
    copy(tree, findFirst(model, TEXT_VIEW));

    assertThat(actionHandler.isPasteEnabled(myDataContext)).isTrue();
    assertThat(actionHandler.isPastePossible(myDataContext)).isTrue();
    actionHandler.performPaste(myDataContext);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    assertThat(toTree(tree)).isEqualTo("<RelativeLayout>  [expanded]\n" +
                                       "    <TextView>  [selected]\n" +
                                       "    <LinearLayout>  [expanded]\n" +
                                       "        <Button>\n" +
                                       "    <TextView>\n" +
                                       "    <AbsoluteLayout>\n");
  }

  private static DesignSurfaceActionHandler getActionHandler(NlComponentTree tree) {
    return (DesignSurfaceActionHandler)tree.getData(PlatformDataKeys.PASTE_PROVIDER.getName());
  }

  public void testPasteIntoLayoutAsFirstChild() throws Exception {
    SyncNlModel model = createModel();
    NlComponentTree tree = createTree(model);
    SelectionModel selectionModel = model.getSurface().getSelectionModel();
    DesignSurfaceActionHandler actionHandler = getActionHandler(tree);
    copy(tree, findFirst(model, TEXT_VIEW));

    selectionModel.toggle(findFirst(model, LINEAR_LAYOUT));
    assertThat(actionHandler.isPasteEnabled(myDataContext)).isTrue();
    assertThat(actionHandler.isPastePossible(myDataContext)).isTrue();
    actionHandler.performPaste(myDataContext);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    assertThat(toTree(tree)).isEqualTo("<RelativeLayout>  [expanded]\n" +
                                       "    <LinearLayout>  [expanded]\n" +
                                       "        <TextView>  [selected]\n" +
                                       "        <Button>\n" +
                                       "    <TextView>\n" +
                                       "    <AbsoluteLayout>\n");
  }

  public void testPasteIntoParentAfterButton() throws Exception {
    SyncNlModel model = createModel();
    NlComponentTree tree = createTree(model);
    SelectionModel selectionModel = model.getSurface().getSelectionModel();
    DesignSurfaceActionHandler actionHandler = getActionHandler(tree);
    copy(tree, findFirst(model, TEXT_VIEW));

    selectionModel.toggle(findFirst(model, BUTTON));
    assertThat(actionHandler.isPasteEnabled(myDataContext)).isTrue();
    assertThat(actionHandler.isPastePossible(myDataContext)).isTrue();
    actionHandler.performPaste(myDataContext);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    assertThat(toTree(tree)).isEqualTo("<RelativeLayout>  [expanded]\n" +
                                       "    <LinearLayout>  [expanded]\n" +
                                       "        <Button>\n" +
                                       "        <TextView>  [selected]\n" +
                                       "    <TextView>\n" +
                                       "    <AbsoluteLayout>\n");
  }

  public void testCopyMultiple() {
    SyncNlModel model = createModel();
    NlComponentTree tree = createTree(model);
    SelectionModel selectionModel = model.getSurface().getSelectionModel();
    selectionModel.toggle(findFirst(model, TEXT_VIEW));
    selectionModel.toggle(findFirst(model, BUTTON));
    DesignSurfaceActionHandler actionHandler = getActionHandler(tree);
    assertThat(actionHandler.isCopyVisible(myDataContext)).isTrue();
    assertThat(actionHandler.isCopyEnabled(myDataContext)).isTrue();
  }

  public void testPasteMultipleIntoLayout() throws Exception {
    SyncNlModel model = createModel();
    NlComponentTree tree = createTree(model);
    SelectionModel selectionModel = model.getSurface().getSelectionModel();
    DesignSurfaceActionHandler actionHandler = getActionHandler(tree);
    copy(tree, findFirst(model, TEXT_VIEW), findFirst(model, BUTTON));

    selectionModel.toggle(findFirst(model, ABSOLUTE_LAYOUT));
    assertThat(actionHandler.isPasteEnabled(myDataContext)).isTrue();
    assertThat(actionHandler.isPastePossible(myDataContext)).isTrue();
    actionHandler.performPaste(myDataContext);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    assertThat(toTree(tree)).isEqualTo("<RelativeLayout>  [expanded]\n" +
                                       "    <LinearLayout>  [expanded]\n" +
                                       "        <Button>\n" +
                                       "    <TextView>\n" +
                                       "    <AbsoluteLayout>  [expanded]\n" +
                                       "        <TextView>  [selected]\n" +
                                       "        <Button>  [selected]\n");
  }

  public void testDropOnChain() throws Exception {
    SyncNlModel model = createModelWithConstraintLayout();
    NlComponentTree tree = createTree(model);
    SelectionModel selectionModel = model.getSurface().getSelectionModel();
    NlComponent chain = findFirst(model, CLASS_CONSTRAINT_LAYOUT_CHAIN.defaultName());
    DesignSurfaceActionHandler actionHandler = getActionHandler(tree);
    copy(tree, findFirst(model, BUTTON));
    selectionModel.toggle(chain);
    assertThat(actionHandler.isPasteEnabled(myDataContext)).isTrue();
    assertThat(actionHandler.isPastePossible(myDataContext)).isTrue();
    actionHandler.performPaste(myDataContext);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    assertThat(toTree(tree)).isEqualTo("<android.support.constraint.ConstraintLayout>  [expanded]\n" +
                                       "    <Button>\n" +
                                       "    <Button>\n" +
                                       "    <Button>\n" +
                                       "    <android.support.constraint.Chain>\n" +
                                       "        <Button>  [selected]\n");
  }

  public void testCutRemovesComponents() {
    SyncNlModel model = createModel();
    NlComponentTree tree = createTree(model);
    SelectionModel selectionModel = model.getSurface().getSelectionModel();
    selectionModel.toggle(findFirst(model, TEXT_VIEW));
    DesignSurfaceActionHandler actionHandler = getActionHandler(tree);
    assertThat(actionHandler.isCutVisible(myDataContext)).isTrue();
    assertThat(actionHandler.isCutEnabled(myDataContext)).isTrue();
    actionHandler.performCut(myDataContext);
    assertThat(toTree(tree)).isEqualTo("<RelativeLayout>  [expanded]\n" +
                                       "    <LinearLayout>  [expanded]\n" +
                                       "        <Button>\n" +
                                       "    <AbsoluteLayout>\n");
  }

  public void testPasteAfterCut() throws Exception {
    SyncNlModel model = createModel();
    NlComponentTree tree = createTree(model);
    DesignSurfaceActionHandler actionHandler = getActionHandler(tree);
    cut(tree, findFirst(model, TEXT_VIEW));

    SelectionModel selectionModel = model.getSurface().getSelectionModel();
    selectionModel.clear();
    selectionModel.toggle(findFirst(model, BUTTON));
    assertThat(actionHandler.isPasteEnabled(myDataContext)).isTrue();
    assertThat(actionHandler.isPastePossible(myDataContext)).isTrue();
    actionHandler.performPaste(myDataContext);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    assertThat(toTree(tree)).isEqualTo("<RelativeLayout>  [expanded]\n" +
                                       "    <LinearLayout>  [expanded]\n" +
                                       "        <Button>  [selected]\n" +
                                       "        <TextView>\n" +
                                       "    <AbsoluteLayout>\n");
  }

  public void testDelete() {
    SyncNlModel model = createModel();
    NlComponentTree tree = createTree(model);
    SelectionModel selectionModel = model.getSurface().getSelectionModel();
    selectionModel.toggle(findFirst(model, TEXT_VIEW));
    DesignSurfaceActionHandler actionHandler = getActionHandler(tree);
    assertThat(actionHandler.canDeleteElement(myDataContext)).isTrue();
    actionHandler.deleteElement(myDataContext);
    assertThat(toTree(tree)).isEqualTo("<RelativeLayout>  [expanded]\n" +
                                       "    <LinearLayout>  [expanded]\n" +
                                       "        <Button>\n" +
                                       "    <AbsoluteLayout>\n");
  }

  public void testGotoDeclaration() throws IOException {
    FileEditorManager fileManager = enableFileOpenCaptures();
    NlComponentTree tree = createTree(createModel());
    UIUtil.dispatchAllInvocationEvents();
    tree.addSelectionRow(2); // Button
    NlDesignSurface surface = tree.getDesignSurface();
    assertThat(surface).isNotNull();
    AnAction gotoDeclaration = new GotoComponentAction(surface);
    gotoDeclaration.actionPerformed(mock(AnActionEvent.class));
    checkEditor(fileManager, "relative.xml", 9, "<Button");
  }

  @NotNull
  private FileEditorManager enableFileOpenCaptures() {
    FileEditorManager fileManager = Mockito.mock(FileEditorManagerEx.class);
    when(fileManager.openEditor(ArgumentMatchers.any(OpenFileDescriptor.class), ArgumentMatchers.anyBoolean()))
      .thenReturn(Collections.singletonList(Mockito.mock(FileEditor.class)));
    when(fileManager.getSelectedEditors()).thenReturn(FileEditor.EMPTY_ARRAY);
    when(fileManager.getOpenFiles()).thenReturn(VirtualFile.EMPTY_ARRAY);
    //noinspection UnstableApiUsage
    when(fileManager.getOpenFilesWithRemotes()).thenReturn(VirtualFile.EMPTY_ARRAY);
    when(fileManager.getAllEditors()).thenReturn(FileEditor.EMPTY_ARRAY);
    if (componentStack != null) {
      componentStack.registerServiceInstance(FileEditorManager.class, fileManager);
    }
    return fileManager;
  }

  private void checkEditor(
    @NotNull FileEditorManager fileManager,
    @NotNull String fileName,
    int lineNumber,
    @NotNull String text
  ) throws IOException {
    ArgumentCaptor<OpenFileDescriptor> file = ArgumentCaptor.forClass(OpenFileDescriptor.class);
    Mockito.verify(fileManager).openEditor(file.capture(), ArgumentMatchers.eq(true));
    OpenFileDescriptor descriptor = file.getValue();
    Pair<LineColumn, String> line = findLineAtOffset(descriptor.getFile(), descriptor.getOffset());
    assertThat(descriptor.getFile().getName()).isEqualTo(fileName);
    assertThat(line.second).isEqualTo(text);
    assertThat(line.first.line + 1).isEqualTo(lineNumber);
  }

  private Pair<LineColumn, String> findLineAtOffset(@NotNull VirtualFile file, int offset) throws IOException {
    String text = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
    LineColumn line = StringUtil.offsetToLineColumn(text, offset);
    String lineText = text.substring(offset - line.column, text.indexOf('\n', offset));
    return Pair.create(line, lineText.trim());
  }

  public void testShiftHelpOnComponentTree() {
    SyncNlModel model = createModel();
    NlComponentTree tree = createTree(model);
    AnAction action = findActionForKey(tree, KeyEvent.VK_F1, InputEvent.SHIFT_DOWN_MASK);

    assertThat(action).isNotNull();

    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getDataContext()).thenReturn(myDataContext);

    SelectionModel selectionModel = model.getSurface().getSelectionModel();
    selectionModel.toggle(findFirst(model, TEXT_VIEW));
    action.actionPerformed(event);
    verify(myBrowserLauncher).browse(eq("https://developer.android.com/reference/android/widget/TextView.html"), isNull(), isNull());
  }

  private void copy(@NotNull NlComponentTree tree, @NotNull NlComponent... components) {
    NlDesignSurface surface = tree.getDesignSurface();
    assertNotNull(surface);
    SelectionModel selectionModel = surface.getSelectionModel();

    selectionModel.setSelection(Arrays.asList(components));
    getActionHandler(tree).performCopy(mock(DataContext.class));
    selectionModel.clear();
  }

  private void cut(@NotNull NlComponentTree tree, @NotNull NlComponent... components) {
    NlDesignSurface surface = tree.getDesignSurface();
    assertNotNull(surface);
    SelectionModel selectionModel = surface.getSelectionModel();

    List<NlComponent> list = Arrays.asList(components);
    selectionModel.setSelection(list);
    getActionHandler(tree).performCut(mock(DataContext.class));
    selectionModel.clear();
  }

  private String toTree(@NotNull NlComponentTree tree) {
    StringBuilder sb = new StringBuilder();
    describe(tree, sb, new TreePath(tree.getModel().getRoot()), 0);

    return sb.toString();
  }

  private void describe(@NotNull NlComponentTree tree, @NotNull StringBuilder sb, @NotNull TreePath path, int depth) {
    for (int i = 0; i < depth; i++) {
      sb.append("    ");
    }

    NlComponent component = (NlComponent)path.getLastPathComponent();
    sb.append("<").append(component.getTagName())
      .append(">");
    if (tree.isExpanded(path)) {
      sb.append("  [expanded]");
    }
    if (tree.isPathSelected(path)) {
      sb.append("  [selected]");
    }
    sb.append("\n");
    for (Object subNodeAsObject : component.getChildren()) {
      TreePath subPath = path.pathByAddingChild(subNodeAsObject);
      describe(tree, sb, subPath, depth + 1);
    }
  }

  @NotNull
  private SyncNlModel createModel() {
    ModelBuilder builder = model("relative.xml",
                                 component(RELATIVE_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(LINEAR_LAYOUT)
                                       .withBounds(0, 0, 200, 200)
                                       .wrapContentWidth()
                                       .wrapContentHeight()
                                       .children(
                                         component(BUTTON)
                                           .withBounds(0, 0, 100, 100)
                                           .id("@+id/myButton")
                                           .width("100dp")
                                           .height("100dp")),
                                     component(TEXT_VIEW)
                                       .withBounds(0, 200, 100, 100)
                                       .id("@+id/myText")
                                       .width("100dp")
                                       .height("100dp"),
                                     component(ABSOLUTE_LAYOUT)
                                       .withBounds(0, 300, 400, 500)
                                       .width("400dp")
                                       .height("500dp")));
    final SyncNlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<RelativeLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<LinearLayout>, bounds=[0,0:200x200}\n" +
                 "        NlComponent{tag=<Button>, bounds=[0,0:100x100}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[0,200:100x100}\n" +
                 "    NlComponent{tag=<AbsoluteLayout>, bounds=[0,300:400x500}",
                 NlTreeDumper.dumpTree(model.getComponents()));
    return model;
  }

  @NotNull
  private SyncNlModel createModelWithAppBar() {
    ModelBuilder builder = model("coordinator.xml",
                                 component(COORDINATOR_LAYOUT.defaultName())
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(APP_BAR_LAYOUT.defaultName())
                                       .withBounds(0, 0, 1000, 192).matchParentWidth().height("192dp").children(
                                       component(COLLAPSING_TOOLBAR_LAYOUT.defaultName()).withBounds(0, 0, 1000, 192).matchParentHeight()
                                         .matchParentWidth()
                                         .children(component(IMAGE_VIEW).withBounds(0, 0, 1000, 192).matchParentWidth().matchParentHeight(),
                                                   component("android.support.v7.widget.Toolbar").withBounds(0, 0, 1000, 18)
                                                     .height("?attr/actionBarSize").matchParentWidth())),
                                     component(CLASS_NESTED_SCROLL_VIEW.defaultName()).withBounds(0, 192, 1000, 808).matchParentWidth()
                                       .matchParentHeight().withAttribute(AUTO_URI, "layout_behavior",
                                                                          "android.support.design.widget.AppBarLayout$ScrollingViewBehavior")
                                       .children(component(TEXT_VIEW).withBounds(0, 192, 1000, 808).matchParentWidth().wrapContentHeight()
                                                   .text("@string/stuff"))));

    final SyncNlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<android.support.design.widget.CoordinatorLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<android.support.design.widget.AppBarLayout>, bounds=[0,0:1000x192}\n" +
                 "        NlComponent{tag=<android.support.design.widget.CollapsingToolbarLayout>, bounds=[0,0:1000x192}\n" +
                 "            NlComponent{tag=<ImageView>, bounds=[0,0:1000x192}\n" +
                 "            NlComponent{tag=<android.support.v7.widget.Toolbar>, bounds=[0,0:1000x18}\n" +
                 "    NlComponent{tag=<android.support.v4.widget.NestedScrollView>, bounds=[0,192:1000x808}\n" +
                 "        NlComponent{tag=<TextView>, bounds=[0,192:1000x808}",
                 NlTreeDumper.dumpTree(model.getComponents()));
    return model;
  }

  @NotNull
  private SyncNlModel createModelWithConstraintLayout() {
    ModelBuilder builder = model("constraint.xml",
                                 component(CONSTRAINT_LAYOUT.defaultName())
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(BUTTON)
                                       .withBounds(0, 0, 200, 200)
                                       .id("@+id/button2")
                                       .wrapContentWidth()
                                       .wrapContentHeight(),
                                     component(BUTTON)
                                       .withBounds(0, 0, 200, 200)
                                       .id("@+id/button3")
                                       .wrapContentWidth()
                                       .wrapContentHeight(),
                                     component(BUTTON)
                                       .withBounds(0, 0, 200, 200)
                                       .id("@+id/button1")
                                       .wrapContentWidth()
                                       .wrapContentHeight(),
                                     component(CLASS_CONSTRAINT_LAYOUT_CHAIN.defaultName())
                                       .withBounds(0, 0, 200, 200)
                                       .id("@+id/chain")
                                       .wrapContentWidth()
                                       .wrapContentHeight()));
    final SyncNlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<android.support.constraint.ConstraintLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<Button>, bounds=[0,0:200x200}\n" +
                 "    NlComponent{tag=<Button>, bounds=[0,0:200x200}\n" +
                 "    NlComponent{tag=<Button>, bounds=[0,0:200x200}\n" +
                 "    NlComponent{tag=<android.support.constraint.Chain>, bounds=[0,0:200x200}",
                 NlTreeDumper.dumpTree(model.getComponents()));
    return model;
  }

  public void testNonNlComponentDrop() throws Exception {
    SyncNlModel model = createModelWithBarriers();
    NlComponentTree tree = createTree(model);
    assertNull(tree.getSelectionPaths());

    // Check initial state
    tree.expandRow(3);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

    TreePath pathForRow4 = tree.getPathForRow(4);
    TreePath pathForRow5 = tree.getPathForRow(5);
    assertThat(pathForRow4.getLastPathComponent()).isEqualTo("button2");
    assertThat(pathForRow5.getLastPathComponent()).isEqualTo("button3");

    tree.setSelectionPath(pathForRow4);
    Rectangle bounds1 = tree.getPathBounds(pathForRow4);
    Rectangle bounds2 = tree.getPathBounds(pathForRow5);
    assertNotNull(bounds1);
    assertNotNull(bounds2);

    // Perform the drop
    DelegatedTreeEventHandler handler = NlTreeUtil.getSelectionTreeHandler(tree);
    assertNotNull(handler);
    DropTargetDropEvent dropEvent =
      new DropTargetDropEventBuilder(LayoutTestUtilities.createDropTargetContext(), bounds2.x, (int)bounds2.getCenterY(),
                                     handler.getTransferable(tree.getSelectionPaths())).build();

    // We directly crate and call the listener because we cannot setup the
    // drag and drop in BuildBot since there is no X11 server
    NlDropListener listener = new NlDropListener(tree);
    listener.drop(dropEvent);

    // Check that the model is changed
    Object child1 = ((ConstraintHelperHandler)handler).getComponentTreeChild(tree.getPathForRow(3).getLastPathComponent(), 0);
    Object child2 = ((ConstraintHelperHandler)handler).getComponentTreeChild(tree.getPathForRow(3).getLastPathComponent(), 1);
    assertThat(child1).isEqualTo("button3");
    assertThat(child2).isEqualTo("button2");

    // We manually notify the model changed event to ensure that the paths are updated
    tree.modelDerivedDataChanged(model);
    tree.expandRow(3); // Ensure that the the barrier's children path are not collapsed

    // Check that button2 and button3 are swapped
    pathForRow4 = tree.getPathForRow(4);
    pathForRow5 = tree.getPathForRow(5);
    assertThat(pathForRow4.getLastPathComponent()).isEqualTo("button3");
    assertThat(pathForRow5.getLastPathComponent()).isEqualTo("button2");
  }

  public void testDeleteBarrier() throws Exception {
    SyncNlModel model = createModelWithBarriers();
    NlComponentTree tree = createTree(model);
    assertNull(tree.getSelectionPaths());
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

    // Check initial state
    tree.expandRow(3);
    TreePath pathForRow4 = tree.getPathForRow(4);
    tree.setSelectionPath(pathForRow4);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

    ((DeleteProvider)checkNotNull(tree.getData(PlatformDataKeys.DELETE_ELEMENT_PROVIDER.getName())))
      .deleteElement(DataContext.EMPTY_CONTEXT);
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    String constraintReferences = checkNotNull(model.find("barrier")).getAttribute(AUTO_URI, CONSTRAINT_REFERENCED_IDS);
    assertThat(constraintReferences).isEqualTo("button3");
  }

  @NotNull
  private SyncNlModel createModelWithBarriers() {
    ModelBuilder builder = model("constraint.xml",
                                 component(CONSTRAINT_LAYOUT.defaultName())
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(BUTTON)
                                       .withBounds(0, 0, 200, 200)
                                       .id("@+id/button2")
                                       .wrapContentWidth()
                                       .wrapContentHeight(),
                                     component(BUTTON)
                                       .withBounds(0, 0, 200, 200)
                                       .id("@+id/button3")
                                       .wrapContentWidth()
                                       .wrapContentHeight(),
                                     component(CLASS_CONSTRAINT_LAYOUT_HELPER.defaultName())
                                       .withBounds(0, 0, 200, 200)
                                       .id("@+id/barrier")
                                       .wrapContentWidth()
                                       .wrapContentHeight()
                                       .withAttribute(AUTO_URI, ATTR_BARRIER_DIRECTION, CONSTRAINT_BARRIER_END)
                                       .withAttribute(AUTO_URI, CONSTRAINT_REFERENCED_IDS, "button2,button3"),
                                     component(TEXT_VIEW)
                                       .withBounds(0, 0, 200, 200)
                                       .id("@+id/chain")
                                       .wrapContentWidth()
                                       .wrapContentHeight()
                                       .withAttribute(AUTO_URI, ATTR_LAYOUT_START_TO_END_OF, "@id/barrier")
                                   ));
    final SyncNlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<android.support.constraint.ConstraintLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<Button>, bounds=[0,0:200x200}\n" +
                 "    NlComponent{tag=<Button>, bounds=[0,0:200x200}\n" +
                 "    NlComponent{tag=<android.support.constraint.ConstraintHelper>, bounds=[0,0:200x200}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[0,0:200x200}",
                 NlTreeDumper.dumpTree(model.getComponents()));
    return model;
  }

  private NlComponentTree createTree(@NotNull SyncNlModel model) {
    model.getUpdateQueue().setPassThrough(true);
    NlVisibilityGutterPanel gutterPanel = new NlVisibilityGutterPanel();
    Disposer.register(getTestRootDisposable(), gutterPanel);
    NlDesignSurface surface = (NlDesignSurface)model.getSurface();
    NlComponentTree tree = new NlComponentTree(getProject(), surface, gutterPanel);
    Disposer.register(getTestRootDisposable(), tree);
    tree.getUpdateQueue().setPassThrough(true);
    tree.getUpdateQueue().flush();
    return tree;
  }

  @NotNull
  private NlComponent findFirst(@NotNull NlModel model, @NotNull String tagName) {
    NlComponent component = findFirst(tagName, model.getComponents());
    assert component != null;
    return component;
  }

  @Nullable
  private static NlComponent findFirst(@NotNull String tagName, @NotNull List<NlComponent> components) {
    for (NlComponent component : components) {
      if (component.getTagName().equals(tagName)) {
        return component;
      }
      NlComponent child = findFirst(tagName, component.getChildren());
      if (child != null) {
        return child;
      }
    }
    return null;
  }
}
