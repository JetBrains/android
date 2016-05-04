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
package com.android.tools.idea.editors.manifest;

import com.android.SdkConstants;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.MavenCoordinates;
import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.manifmerger.Actions;
import com.android.manifmerger.MergingReport;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.parser.NamedObject;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.rendering.HtmlLinkManager;
import com.android.utils.HtmlBuilder;
import com.android.utils.PositionXmlParser;
import com.google.common.collect.Sets;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.XmlHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.*;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.gradle.AndroidGradleModel.EXPLODED_AAR;

// TODO for permission if not from main file
// TODO then have option to tools:node="remove" tools:selector="com.example.lib1"

// TODO merge conflict, then use tools:node=”replace”
// TODO or tools:node=”merge-only-attributes”

// TODO add option to tools:node=”removeAll" Remove all elements of the same node type
// TODO add option to tools:node=”strict” can be added to anything that merges perfectly

public class ManifestPanel extends JPanel implements TreeSelectionListener {

  private static final String SUGGESTION_MARKER = "Suggestion: ";
  private static final Pattern ADD_SUGGESTION_FORMAT = Pattern.compile(".*? 'tools:([\\w:]+)=\"([\\w:]+)\"' to \\<(\\w+)\\> element at [^:]+:(\\d+):(\\d+)-[\\d:]+ to override\\.", Pattern.DOTALL);

  private final AndroidFacet myFacet;
  private final Font myDefaultFont;
  private Tree myTree;
  private JEditorPane myDetails;
  private JPopupMenu myPopup;
  private JMenuItem myRemoveItem;
  private JMenuItem myGotoItem;

  private MergedManifest myManifest;
  private final List<File> myFiles = new ArrayList<File>();
  private final List<File> myOtherFiles = new ArrayList<File>();
  private final HtmlLinkManager myHtmlLinkManager = new HtmlLinkManager();
  private VirtualFile myFile;
  private final Color myBackgroundColor;

  public ManifestPanel(final @NotNull AndroidFacet facet) {
    myFacet = facet;
    setLayout(new BorderLayout());

    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    EditorColorsScheme scheme = colorsManager.getGlobalScheme();
    myBackgroundColor = scheme.getDefaultBackground();
    myDefaultFont = scheme.getFont(EditorFontType.PLAIN);

    myTree = new FileColorTree();
    myTree.setCellRenderer(new SyntaxHighlightingCellRenderer());

    TreeSelectionModel selectionModel = myTree.getSelectionModel();
    selectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    selectionModel.addTreeSelectionListener(this);

    myDetails = createDetailsPane(facet);

    addSpeedSearch();
    createPopupMenu();
    registerGotoAction();

    JBSplitter splitter = new JBSplitter(0.5f);
    splitter.setFirstComponent(new JBScrollPane(myTree));
    splitter.setSecondComponent(new JBScrollPane(myDetails));

    add(splitter);
  }

  private JEditorPane createDetailsPane(@NotNull final AndroidFacet facet) {
    JEditorPane details = new JEditorPane();
    details.setMargin(new Insets(5, 5, 5, 5));
    details.setContentType(UIUtil.HTML_MIME);
    details.setEditable(false);
    details.setFont(myDefaultFont);
    details.setBackground(myBackgroundColor);
    HyperlinkListener hyperLinkListener = new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          String url = e.getDescription();
          myHtmlLinkManager.handleUrl(url, facet.getModule(), null, null, null);
        }
      }
    };
    details.addHyperlinkListener(hyperLinkListener);

    return details;
  }

  private void createPopupMenu() {
    myPopup = new JBPopupMenu();
    myGotoItem = new JBMenuItem("Go to Declaration");
    myGotoItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        TreePath treePath = myTree.getSelectionPath();
        final ManifestTreeNode node = (ManifestTreeNode)treePath.getLastPathComponent();
        if (node != null) {
          goToDeclaration(node.getUserObject());
        }
      }
    });
    myPopup.add(myGotoItem);
    myRemoveItem = new JBMenuItem("Remove");
    myRemoveItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        TreePath treePath = myTree.getSelectionPath();
        final ManifestTreeNode node = (ManifestTreeNode)treePath.getLastPathComponent();

        new WriteCommandAction.Simple(myFacet.getModule().getProject(), "Removing manifest tag", ManifestUtils.getMainManifest(myFacet)) {
          @Override
          protected void run() throws Throwable {
            ManifestUtils.toolsRemove(ManifestUtils.getMainManifest(myFacet), node.getUserObject());
          }
        }.execute();
      }
    });
    myPopup.add(myRemoveItem);

    MouseListener ml = new MouseAdapter() {
      @Override
      public void mousePressed(@NotNull MouseEvent e) {
        if (e.isPopupTrigger()) {
          handlePopup(e);
        }
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) {
          handlePopup(e);
        }
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
          TreePath treePath = myTree.getPathForLocation(e.getX(), e.getY());
          if (treePath != null) {
            ManifestTreeNode node = (ManifestTreeNode)treePath.getLastPathComponent();
            Node attribute = node.getUserObject();
            if (attribute instanceof Attr) {
              goToDeclaration(attribute);
            }
          }
        }
      }

      private void handlePopup(@NotNull MouseEvent e) {
        TreePath treePath = myTree.getPathForLocation(e.getX(), e.getY());
        if (treePath == null || e.getSource() == myDetails) {
          // Use selection instead
          treePath = myTree.getSelectionPath();
        }
        if (treePath != null) {
          ManifestTreeNode node = (ManifestTreeNode)treePath.getLastPathComponent();
          myRemoveItem.setEnabled(canRemove(node.getUserObject()));
          myPopup.show(e.getComponent(), e.getX(), e.getY());
        }
      }
    };
    myTree.addMouseListener(ml);
    myDetails.addMouseListener(ml);
  }

  private void registerGotoAction() {
    AnAction goToDeclarationAction = new AnAction() {
      @Override
      public void actionPerformed(AnActionEvent e) {
        ManifestTreeNode node = (ManifestTreeNode)myTree.getLastSelectedPathComponent();
        if (node != null) {
          goToDeclaration(node.getUserObject());
        }
      }
    };
    goToDeclarationAction
      .registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_GOTO_DECLARATION).getShortcutSet(), myTree);
  }

  @NotNull
  private TreeSpeedSearch addSpeedSearch() {
    return new TreeSpeedSearch(myTree);
  }


  public void setManifest(@NotNull MergedManifest manifest, @NotNull VirtualFile selectedManifest) {
    myFile = selectedManifest;
    myManifest = manifest;
    Document document = myManifest.getDocument();
    Element root = document != null ? document.getDocumentElement() : null;
    myTree.setModel(root == null ? null : new DefaultTreeModel(new ManifestTreeNode(root)));

    myFiles.clear();
    myOtherFiles.clear();
    List<VirtualFile> manifestFiles = myManifest.getManifestFiles();

    // make sure that the selected manifest is always the first color
    myFiles.add(VfsUtilCore.virtualToIoFile(selectedManifest));
    Set<File> referenced = Sets.newHashSet();
    if (root != null) {
      recordLocationReferences(root, referenced);
    }

    if (manifestFiles != null) {
      for (VirtualFile f : manifestFiles) {
        if (!f.equals(selectedManifest)) {
          File file = VfsUtilCore.virtualToIoFile(f);
          if (referenced.contains(file)) {
            myFiles.add(file);
          } else {
            myOtherFiles.add(file);
          }
        }
      }
      Collections.sort(myFiles, MANIFEST_SORTER);
      Collections.sort(myOtherFiles, MANIFEST_SORTER);
    }

    if (root != null) {
      TreeUtil.expandAll(myTree);
    }

    // display the LoggingRecords from the merger
    updateDetails(null);
  }

  private static final Comparator<File> MANIFEST_SORTER = new Comparator<File>() {

    @Override
    public int compare(File o1, File o2) {
      String p1 = o1.getPath();
      String p2 = o2.getPath();
      boolean lib1 = p1.contains(EXPLODED_AAR);
      boolean lib2 = p2.contains(EXPLODED_AAR);
      if (lib1 != lib2) {
        return lib1 ? 1 : -1;
      }
      return p1.compareTo(p2);
    }
  };

  private void recordLocationReferences(@NotNull Node node, @NotNull Set<File> files) {
    short type = node.getNodeType();
    if (type == Node.ATTRIBUTE_NODE) {
      List<? extends Actions.Record> records = ManifestUtils.getRecords(myManifest, node);
      if (!records.isEmpty()) {
        for (Actions.Record record : records) {
          if (record.getActionType() == Actions.ActionType.INJECTED) {
            continue;
          }
          File location = record.getActionLocation().getFile().getSourceFile();
          if (location != null && !files.contains(location)) {
            files.add(location);
          }
        }
      }
    } else if (type == Node.ELEMENT_NODE) {
      Node child = node.getFirstChild();
      while (child != null) {
        if (child.getNodeType() == Node.ELEMENT_NODE) {
          recordLocationReferences(child, files);
        }
        child = child.getNextSibling();
      }

      NamedNodeMap attributes = node.getAttributes();
      for (int i = 0, n = attributes.getLength(); i < n; i++) {
        recordLocationReferences(attributes.item(i), files);
      }
    }
  }

  @Override
  public void valueChanged(@Nullable TreeSelectionEvent e) {
    if (e != null && e.isAddedPath()) {
      TreePath treePath = e.getPath();
      ManifestTreeNode node = (ManifestTreeNode)treePath.getLastPathComponent();
      updateDetails(node);
    }
    else {
      updateDetails(null);
    }
  }

  public void updateDetails(@Nullable ManifestTreeNode node) {
    HtmlBuilder sb = new HtmlBuilder();
    Font font = UIUtil.getLabelFont();
    sb.addHtml("<html><body style=\"font-family: " + font.getFamily() + "; " + "font-size: " + font.getSize() + "pt;\">");
    sb.beginUnderline().beginBold();
    sb.add("Manifest Sources");
    sb.endBold().endUnderline().newline();
    sb.addHtml("<table border=\"0\">");
    String borderColor = ColorUtil.toHex(JBColor.GRAY);
    for (File file : myFiles) {
      Color color = getFileColor(file);
      sb.addHtml("<tr><td width=\"24\" height=\"24\" style=\"background-color:#");
      sb.addHtml(ColorUtil.toHex(color));
      sb.addHtml("; border: 1px solid #");
      sb.addHtml(borderColor);
      sb.addHtml(";\">");
      sb.addHtml("</td><td>");
      describePosition(sb, myFacet, new SourceFilePosition(file, SourcePosition.UNKNOWN));
      sb.addHtml("</td></tr>");
    }
    sb.addHtml("</table>");
    sb.newline();
    if (!myOtherFiles.isEmpty()) {
      sb.beginUnderline().beginBold();
      sb.add("Other Manifest Files");
      sb.endBold().endUnderline().newline();
      sb.add("(Included in merge, but did not contribute any elements)").newline();
      boolean first = true;
      for (File file : myOtherFiles) {
        if (first) {
          first = false;
        } else {
          sb.add(", ");
        }
        describePosition(sb, myFacet, new SourceFilePosition(file, SourcePosition.UNKNOWN));
      }
      sb.newline().newline();
    }

    // See if there are errors; if so, show the merging report instead of node selection report
    if (!myManifest.getLoggingRecords().isEmpty()) {
      for (MergingReport.Record record : myManifest.getLoggingRecords()) {
        if (record.getSeverity() == MergingReport.Record.Severity.ERROR) {
          node = null;
          break;
        }
      }
    }

    if (node != null) {
      List<? extends Actions.Record> records = ManifestUtils.getRecords(myManifest, node.getUserObject());
      sb.beginUnderline().beginBold();
      sb.add("Merging Log");
      sb.endBold().endUnderline().newline();

      if (records.isEmpty()) {
        sb.add("No records found. (This is a bug in the manifest merger.)");
      }

      SourceFilePosition prev = null;
      for (Actions.Record record : records) {
        // There are currently some duplicated entries; filter these out
        SourceFilePosition location = ManifestUtils.getActionLocation(myFacet.getModule(), record);
        if (location.equals(prev)) {
          continue;
        }
        prev = location;

        Actions.ActionType actionType = record.getActionType();
        if (actionType == Actions.ActionType.INJECTED) {
          sb.add("Value provided by Gradle"); // TODO: include module source? Are we certain it's correct?
          sb.newline();
          continue;
        }
        sb.add(StringUtil.capitalize(String.valueOf(actionType).toLowerCase(Locale.US)));
        sb.add(" from the ");
        sb.addHtml(getHtml(myFacet, location));

        String reason = record.getReason();
        if (reason != null) {
          sb.add("; reason: ");
          sb.add(reason);
        }
        sb.newline();
      }
    }
    else if (!myManifest.getLoggingRecords().isEmpty()) {
      sb.add("Merging Errors:").newline();
      for (MergingReport.Record record : myManifest.getLoggingRecords()) {
        sb.addHtml(getHtml(record.getSeverity()));
        sb.add(" ");
        try {
          sb.addHtml(getErrorHtml(myFacet, record.getMessage(), record.getSourceLocation(), myHtmlLinkManager,
                                  LocalFileSystem.getInstance().findFileByIoFile(myFiles.get(0))));
        }
        catch (Exception ex) {
          Logger.getInstance(ManifestPanel.class).error("error getting error html", ex);
          sb.add(record.getMessage());
        }
        sb.add(" ");
        sb.addHtml(getHtml(myFacet, record.getSourceLocation()));
        sb.newline();
      }
    }

    sb.closeHtmlBody();
    myDetails.setText(sb.getHtml());
    myDetails.setCaretPosition(0);
  }

  @NotNull
  private Color getNodeColor(@NotNull Node item) {
    List<? extends Actions.Record> records = ManifestUtils.getRecords(myManifest, item);
    if (!records.isEmpty()) {
      File file = ManifestUtils.getActionLocation(myFacet.getModule(), records.get(0)).getFile().getSourceFile();
      if (file != null) {
        return getFileColor(file);
      }
    }
    return myBackgroundColor;
  }

  @NotNull
  private Color getFileColor(@NotNull File file) {
    if (!myFiles.contains(file)) {
      myFiles.add(file);
    }
    int index = myFiles.indexOf(file);
    if (index == 0) {
      // current file shouldn't be highlighted with a background
      return myBackgroundColor;
    }
    return AnnotationColors.BG_COLORS[(index - 1) * AnnotationColors.BG_COLORS_PRIME % AnnotationColors.BG_COLORS.length];
  }

  private boolean canRemove(@NotNull Node node) {
    List<? extends Actions.Record> records = ManifestUtils.getRecords(myManifest, node);
    if (records.isEmpty()) {
      // if we don't know where we are coming from, we are prob displaying the main manifest with a merge error.
      return false;
    }
    File mainManifest = VfsUtilCore.virtualToIoFile(ManifestUtils.getMainManifest(myFacet).getVirtualFile());
    for (Actions.Record record : records) {
      // if we are already coming from the main file, then we can't remove it using this editor
      if (FileUtil.filesEqual(ManifestUtils.getActionLocation(myFacet.getModule(), record).getFile().getSourceFile(), mainManifest)) {
        return false;
      }
    }
    return true;
  }

  private void goToDeclaration(Node element) {
    List<? extends Actions.Record> records = ManifestUtils.getRecords(myManifest, element);
    for (Actions.Record record : records) {
      SourceFilePosition sourceFilePosition = ManifestUtils.getActionLocation(myFacet.getModule(), record);
      SourceFile sourceFile = sourceFilePosition.getFile();
      if (!SourceFile.UNKNOWN.equals(sourceFile)) {
        File ioFile = sourceFile.getSourceFile();
        if (ioFile != null) {
          VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(ioFile);
          assert file != null;
          int line = -1;
          int column = 0;
          SourcePosition sourcePosition = sourceFilePosition.getPosition();
          if (!SourcePosition.UNKNOWN.equals(sourcePosition)) {
            line = sourcePosition.getStartLine();
            column = sourcePosition.getStartColumn();
          }
          Project project = myFacet.getModule().getProject();
          OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, line, column);
          FileEditorManager.getInstance(project).openEditor(descriptor, true);
          break;
        }
      }
    }
  }

  @NotNull
  static String getErrorHtml(final @NotNull AndroidFacet facet,
                             @NotNull String message,
                             @NotNull final SourceFilePosition position,
                             @NotNull HtmlLinkManager htmlLinkManager,
                             final @Nullable VirtualFile currentlyOpenFile) {
    HtmlBuilder sb = new HtmlBuilder();
    int index = message.indexOf(SUGGESTION_MARKER);
    if (index >= 0) {
      index += SUGGESTION_MARKER.length();
      String action = message.substring(index, message.indexOf(' ', index));
      sb.add(message.substring(0, index));
      message = message.substring(index);
      if ("add".equals(action)) {
        sb.addHtml(getErrorAddHtml(facet, message, position, htmlLinkManager,
                                   currentlyOpenFile));
      }
      else if ("use".equals(action)) {
        sb.addHtml(getErrorUseHtml(facet, message, position, htmlLinkManager,
                                   currentlyOpenFile));
      }
      else if ("remove".equals(action)) {
        sb.addHtml(getErrorRemoveHtml(facet, message, position, htmlLinkManager,
                                   currentlyOpenFile));
      }
    }
    else {
      sb.add(message);
    }
    return sb.getHtml();
  }

  @NotNull
  private static String getErrorAddHtml(final @NotNull AndroidFacet facet,
                                        @NotNull String message,
                                        @NotNull final SourceFilePosition position,
                                        @NotNull HtmlLinkManager htmlLinkManager,
                                        final @Nullable VirtualFile currentlyOpenFile) {
    /*
    Example Input:
    ERROR Attribute activity#com.foo.mylibrary.LibActivity@label value=(@string/app_name)
    from AndroidManifest.xml:24:17-49 is also present at AndroidManifest.xml:12:13-45
    value=(@string/lib_name). Suggestion: add 'tools:replace="android:label"' to <activity>
    element at AndroidManifest.xml:22:9-24:51 to override. AndroidManifest.xml:24:17-49
     */
    HtmlBuilder sb = new HtmlBuilder();
    Matcher matcher = ADD_SUGGESTION_FORMAT.matcher(message);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("unexpected add suggestion format " + message);
    }
    final String attributeName = matcher.group(1);
    final String attributeValue = matcher.group(2);
    String tagName = matcher.group(3);
    int line = Integer.parseInt(matcher.group(4));
    int col = Integer.parseInt(matcher.group(5));
    final XmlFile mainManifest = ManifestUtils.getMainManifest(facet);

    Element element = getElementAt(mainManifest, line, col);
    if (element != null && tagName.equals(element.getTagName())) {
      final Element xmlTag = element;
      sb.addLink(message, htmlLinkManager.createRunnableLink(new Runnable() {
        @Override
        public void run() {
          addToolsAttribute(mainManifest, xmlTag, attributeName, attributeValue);
        }
      }));
    }
    else {
      Logger.getInstance(ManifestPanel.class).warn("can not find " + tagName + " tag " + element);
      sb.add(message);
    }
    return sb.getHtml();
  }

  @Nullable
  private static Element getElementAt(XmlFile mainManifest, int line, int col) {
    Element element = null;
    try {
      Document document = PositionXmlParser.parse(mainManifest.getText());
      Node node = PositionXmlParser.findNodeAtLineAndCol(document, line, col);
      while (node != null) {
        if (node instanceof Element) {
          element = (Element)node;
          break;
        } else
          node = node.getParentNode();
      }
    }
    catch (Throwable ignore) {
    }
    return element;
  }

  @NotNull
  private static String getErrorUseHtml(final @NotNull AndroidFacet facet,
                                        @NotNull String message,
                                        @NotNull final SourceFilePosition position,
                                        @NotNull HtmlLinkManager htmlLinkManager,
                                        final @Nullable VirtualFile currentlyOpenFile) {
    /*
    Example Input:
    ERROR uses-sdk:minSdkVersion 4 cannot be smaller than version 8 declared in library
    /.../mylib/AndroidManifest.xml Suggestion:use tools:overrideLibrary="com.mylib"
    to force usage AndroidManifest.xml:11:5-72
     */
    HtmlBuilder sb = new HtmlBuilder();
    int eq = message.indexOf('=');
    if (eq < 0) {
      throw new IllegalArgumentException("unexpected use suggestion format " + message);
    }
    int end = message.indexOf('"', eq + 2);
    if (end < 0 || message.charAt(eq + 1) != '\"') {
      throw new IllegalArgumentException("unexpected use suggestion format " + message);
    }
    final String suggestion = message.substring(message.indexOf(' ') + 1, end + 1);
    if (!SourcePosition.UNKNOWN.equals(position.getPosition())) {
      XmlFile mainManifest = ManifestUtils.getMainManifest(facet);
      Element element = getElementAt(mainManifest, position.getPosition().getStartLine(), position.getPosition().getStartColumn());
      if (element != null && SdkConstants.TAG_USES_SDK.equals(element.getTagName())) {
        sb.addLink(message.substring(0, end + 1), htmlLinkManager.createRunnableLink(new Runnable() {
          @Override
          public void run() {
            int eq = suggestion.indexOf('=');
            String attributeName = suggestion.substring(suggestion.indexOf(':') + 1, eq);
            String attributeValue = suggestion.substring(eq + 2, suggestion.length() - 1);
            addToolsAttribute(mainManifest, element, attributeName, attributeValue);
          }
        }));
        sb.add(message.substring(end + 1));
      }
      else {
        Logger.getInstance(ManifestPanel.class).warn("Can not find uses-sdk tag " + element);
        sb.add(message);
      }
    }
    else {
      // If we do not have a uses-sdk tag in our main manifest, the suggestion is not useful
      sb.add(message);
    }
    sb.newlineIfNecessary().newline();
    return sb.getHtml();
  }

  @NotNull
  private static String getErrorRemoveHtml(final @NotNull AndroidFacet facet,
                                           @NotNull String message,
                                           @NotNull final SourceFilePosition position,
                                           @NotNull HtmlLinkManager htmlLinkManager,
                                           final @Nullable VirtualFile currentlyOpenFile) {
    /*
    Example Input:
    ERROR Overlay manifest:package attribute declared at AndroidManifest.xml:3:5-49
    value=(com.foo.manifestapplication.debug) has a different value=(com.foo.manifestapplication)
    declared in main manifest at AndroidManifest.xml:5:5-43 Suggestion: remove the overlay
    declaration at AndroidManifest.xml and place it in the build.gradle: flavorName
    { applicationId = "com.foo.manifestapplication.debug" } AndroidManifest.xml (debug)
     */
    HtmlBuilder sb = new HtmlBuilder();
    int start = message.indexOf('{');
    int end = message.indexOf('}', start + 1);
    final String declaration = message.substring(start + 1, end).trim();
    if (!declaration.startsWith("applicationId")) {
      throw new IllegalArgumentException("unexpected remove suggestion format " + message);
    }
    final GradleBuildFile buildFile = GradleBuildFile.get(facet.getModule());
    Runnable link = null;

    if (buildFile != null) {
      final String applicationId = declaration.substring(declaration.indexOf('"') + 1, declaration.lastIndexOf('"'));
      final File manifestOverlayFile = position.getFile().getSourceFile();
      assert manifestOverlayFile != null;
      VirtualFile manifestOverlayVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(manifestOverlayFile);
      assert manifestOverlayVirtualFile != null;

      IdeaSourceProvider sourceProvider = ManifestUtils.findManifestSourceProvider(facet, manifestOverlayVirtualFile);
      assert sourceProvider != null;
      final String name = sourceProvider.getName();

      AndroidGradleModel androidGradleModel = AndroidGradleModel.get(facet.getModule());
      assert androidGradleModel != null;

      final XmlFile manifestOverlayPsiFile = (XmlFile)PsiManager.getInstance(facet.getModule().getProject()).findFile(manifestOverlayVirtualFile);
      assert manifestOverlayPsiFile != null;

      if (androidGradleModel.getBuildTypeNames().contains(name)) {
        final String packageName = MergedManifest.get(facet).getPackage();
        assert packageName != null;
        if (applicationId.startsWith(packageName)) {
          link = new Runnable() {
            @Override
            public void run() {
              new WriteCommandAction.Simple(facet.getModule().getProject(), "Apply manifest suggestion", buildFile.getPsiFile(), manifestOverlayPsiFile) {
                @Override
                protected void run() throws Throwable {
                  if (currentlyOpenFile != null) {
                    // We mark this action as affecting the currently open file, so the Undo is available in this editor
                    CommandProcessor.getInstance().addAffectedFiles(facet.getModule().getProject(), currentlyOpenFile);
                  }
                  removePackageAttribute(manifestOverlayPsiFile);
                  final String applicationIdSuffix = applicationId.substring(packageName.length());
                  List<NamedObject> buildTypes = (List<NamedObject>)buildFile.getValue(BuildFileKey.BUILD_TYPES);
                  if (buildTypes == null) {
                    buildTypes = new ArrayList<NamedObject>();
                  }
                  NamedObject buildType = find(buildTypes, name);
                  if (buildType == null) {
                    buildType = new NamedObject(name);
                    buildTypes.add(buildType);
                  }
                  buildType.setValue(BuildFileKey.APPLICATION_ID_SUFFIX, applicationIdSuffix);
                  buildFile.setValue(BuildFileKey.BUILD_TYPES, buildTypes);
                  GradleProjectImporter.getInstance().requestProjectSync(facet.getModule().getProject(), null);
                }
              }.execute();
            }
          };
        }
      }
      else if (androidGradleModel.getProductFlavorNames().contains(name)) {
        link = new Runnable() {
          @Override
          public void run() {
            new WriteCommandAction.Simple(facet.getModule().getProject(), "Apply manifest suggestion", buildFile.getPsiFile(), manifestOverlayPsiFile) {
              @Override
              protected void run() throws Throwable {
                if (currentlyOpenFile != null) {
                  // We mark this action as affecting the currently open file, so the Undo is available in this editor
                  CommandProcessor.getInstance().addAffectedFiles(facet.getModule().getProject(), currentlyOpenFile);
                }
                removePackageAttribute(manifestOverlayPsiFile);
                List<NamedObject> flavors = (List<NamedObject>)buildFile.getValue(BuildFileKey.FLAVORS);
                assert flavors != null;
                NamedObject flavor = find(flavors, name);
                assert flavor != null;
                flavor.setValue(BuildFileKey.APPLICATION_ID, applicationId);
                buildFile.setValue(BuildFileKey.FLAVORS, flavors);
                GradleProjectImporter.getInstance().requestProjectSync(facet.getModule().getProject(), null);
              }
            }.execute();
          }
        };
      }
    }

    if (link != null) {
      sb.addLink(message.substring(0, end + 1), htmlLinkManager.createRunnableLink(link));
      sb.add(message.substring(end + 1));
    }
    else {
      sb.add(message);
    }
    return sb.getHtml();
  }

  private static void removePackageAttribute(XmlFile manifestFile) {
    XmlTag tag = manifestFile.getRootTag();
    assert tag != null;
    tag.setAttribute("package", null);
  }

  @Nullable("item not found")
  static NamedObject find(@NotNull List<NamedObject> items, @NotNull String name) {
    for (NamedObject item : items) {
      if (name.equals(item.getName())) {
        return item;
      }
    }
    return null;
  }

  static void addToolsAttribute(final @NotNull XmlFile file,
                                final @NotNull Element element,
                                final @NotNull String attributeName,
                                final @NotNull String attributeValue) {
    final Project project = file.getProject();
    new WriteCommandAction.Simple(project, "Apply manifest suggestion", file) {
      @Override
      protected void run() throws Throwable {
        ManifestUtils.addToolsAttribute(file, element, attributeName, attributeValue);
      }
    }.execute();
  }

  @NotNull
  static String getHtml(@NotNull MergingReport.Record.Severity severity) {
    String severityString = StringUtil.capitalize(severity.toString().toLowerCase(Locale.US));
    if (severity == MergingReport.Record.Severity.ERROR) {
      return new HtmlBuilder().addHtml("<font color=\"#" + ColorUtil.toHex(JBColor.RED) + "\">")
        .addBold(severityString).addHtml("</font>:").getHtml();
    }
    return severityString;
  }

  @NotNull
  String getHtml(@NotNull AndroidFacet facet, @NotNull SourceFilePosition sourceFilePosition) {
    HtmlBuilder sb = new HtmlBuilder();
    describePosition(sb, facet, sourceFilePosition);
    return sb.getHtml();
  }


  private void describePosition(@NotNull HtmlBuilder sb, @NotNull AndroidFacet facet, @NotNull SourceFilePosition sourceFilePosition) {
    SourceFile sourceFile = sourceFilePosition.getFile();
    SourcePosition sourcePosition = sourceFilePosition.getPosition();
    File file = sourceFile.getSourceFile();
    AndroidLibrary library = null;
    if (file != null) {
      String source = null;

      Module libraryModule = null;
      Module[] modules = ModuleManager.getInstance(facet.getModule().getProject()).getModules();
      VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
      if (vFile != null) {
        Module module = ModuleUtilCore.findModuleForFile(vFile, facet.getModule().getProject());
        if (module != null) {
          if (modules.length >= 2) {
            source = module.getName();
          }

          // AAR Library?
          if (file.getPath().contains(EXPLODED_AAR)) {
            AndroidGradleModel androidModel = AndroidGradleModel.get(module);
            if (androidModel != null) {
              library = GradleUtil.findLibrary(file.getParentFile(), androidModel.getSelectedVariant());
              if (library != null) {
                if (library.getProject() != null) {
                  libraryModule = GradleUtil.findModuleByGradlePath(facet.getModule().getProject(), library.getProject());
                  if (libraryModule != null) {
                    module = libraryModule;
                    source = module.getName();
                  } else {
                    source = library.getProject();
                    source = StringUtil.trimStart(source, ":");
                  }
                }
                else {
                  MavenCoordinates coordinates = library.getResolvedCoordinates();
                  source = /*coordinates.getGroupId() + ":" +*/  coordinates.getArtifactId() + ":" + coordinates.getVersion();
                }
              }
            }
          }
        }

        IdeaSourceProvider provider = ManifestUtils.findManifestSourceProvider(facet, vFile);
        if (provider != null /*&& !provider.equals(facet.getMainIdeaSourceProvider())*/) {
          String providerName = provider.getName();
          if (source == null) {
            source = providerName;
          } else {
            // "the app main manifest" - "app" is the module name, "main" is the source provider name
            source = source + " " + providerName;
          }
        }
      }

      if (source == null) {
        source = file.getName();
        if (!SourcePosition.UNKNOWN.equals(sourcePosition)) {
          source += ":" + String.valueOf(sourcePosition);
        }
      }

      sb.addHtml("<a href=\"");

      boolean redirected = false;
      if (libraryModule != null) {
        AndroidFacet libraryFacet = AndroidFacet.getInstance(libraryModule);
        if (libraryFacet != null) {
          File manifestFile = libraryFacet.getMainSourceProvider().getManifestFile();
          if (manifestFile.exists()) {
            sb.add(manifestFile.toURI().toString());
            redirected = true;
            // Line numbers probably aren't right
            sourcePosition = SourcePosition.UNKNOWN;
            // TODO: Set URL which points to the element/attribute path
          }
        }
      }

      if (!redirected) {
        sb.add(file.toURI().toString());
        if (!SourcePosition.UNKNOWN.equals(sourcePosition)) {
          sb.add(":");
          sb.add(String.valueOf(sourcePosition.getStartLine()));
          sb.add(":");
          sb.add(String.valueOf(sourcePosition.getStartColumn()));
        }
      }
      sb.addHtml("\">");

      sb.add(source);
      sb.addHtml("</a>");
      sb.add(" manifest");

      if (FileUtil.filesEqual(file, VfsUtilCore.virtualToIoFile(myFile))) {
        sb.add(" (this file)");
      }

      if (!SourcePosition.UNKNOWN.equals(sourcePosition)) {
        sb.add(", line ");
        sb.add(Integer.toString(sourcePosition.getStartLine()));
      }
    }
  }

  /**
   * @see ColorUtil#softer(Color)
   */
  @NotNull
  public static Color harder(@NotNull Color color) {
    if (color.getBlue() == color.getRed() && color.getRed() == color.getGreen()) return color;
    final float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
    return Color.getHSBColor(hsb[0], 1f, hsb[2]);
  }

  static class ManifestTreeNode extends DefaultMutableTreeNode {

    public ManifestTreeNode(@NotNull Node obj) {
      super(obj);
    }

    @Override
    @NotNull
    public Node getUserObject() {
      return (Node)super.getUserObject();
    }


    @Override
    public int getChildCount() {
      Node obj = getUserObject();
      if (obj instanceof Element) {
        Element element = (Element)obj;
        NamedNodeMap attributes = element.getAttributes();
        int count = attributes.getLength();
        NodeList childNodes = element.getChildNodes();
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
          Node child = childNodes.item(i);
          if (child.getNodeType() == Node.ELEMENT_NODE) {
            count++;
          }
        }

        return count;
      }
      return 0;
    }

    @Override
    @NotNull
    public ManifestTreeNode getChildAt(int index) {
      Node obj = getUserObject();
      if (children == null && obj instanceof Element) {
        Element element = (Element)obj;
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
          add(new ManifestTreeNode(attributes.item(i)));
        }
        NodeList childNodes = element.getChildNodes();
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
          Node child = childNodes.item(i);
          if (child.getNodeType() == Node.ELEMENT_NODE) {
            add(new ManifestTreeNode(child));
          }
        }
      }
      return (ManifestTreeNode)super.getChildAt(index);
    }

    @Override
    public void add(@NotNull MutableTreeNode newChild) {
      // as we override getChildCount to not use the children Vector
      // we need to make sure add inserts into the correct place.
      insert(newChild, children == null ? 0 : children.size());
    }

    @Override
    @NotNull
    public String toString() {
      Node obj = getUserObject();
      if (obj instanceof Attr) {
        Attr xmlAttribute = (Attr)obj;
        return xmlAttribute.getName() + " = " + xmlAttribute.getValue();
      }
      if (obj instanceof Element) {
        Element xmlTag = (Element)obj;
        return xmlTag.getTagName();
      }
      return obj.toString();
    }

    @Override
    @Nullable
    public ManifestTreeNode getParent() {
      return (ManifestTreeNode)super.getParent();
    }

    @NotNull
    public ManifestTreeNode lastAttribute() {
      Node xmlTag = getUserObject();
      return getChildAt(xmlTag.getAttributes().getLength() - 1);
    }

    public boolean hasElementChildren() {
      Node node = getUserObject();
      if (node instanceof Attr) {
        ManifestTreeNode parent = getParent();
        assert parent != null; // all attribute nodes have a parent element node
        return parent.hasElementChildren();
      } else {
        return node.getChildNodes().getLength() > 0;
      }
    }
  }

  /**
   * Cellrenderer which renders XML Element and Attr nodes using the current color scheme's
   * syntax token colors
   */
  private class SyntaxHighlightingCellRenderer extends ColoredTreeCellRenderer {
    // We have to use ColoredTreeCellRenderer instead of DefaultTreeCellRenderer to allow the Tree.isFileColorsEnabled to work
    // as otherwise the DefaultTreeCellRenderer will always insist on filling the background

    private final SimpleTextAttributes myTagNameAttributes;
    private final SimpleTextAttributes myNameAttributes;
    private final SimpleTextAttributes myValueAttributes;
    private final SimpleTextAttributes myPrefixAttributes;

    public SyntaxHighlightingCellRenderer() {
      EditorColorsScheme globalScheme = EditorColorsManager.getInstance().getGlobalScheme();
      Color tagNameColor = globalScheme.getAttributes(XmlHighlighterColors.XML_TAG_NAME).getForegroundColor();
      Color nameColor = globalScheme.getAttributes(XmlHighlighterColors.XML_ATTRIBUTE_NAME).getForegroundColor();
      Color valueColor = globalScheme.getAttributes(XmlHighlighterColors.XML_ATTRIBUTE_VALUE).getForegroundColor();
      Color prefixColor = globalScheme.getAttributes(XmlHighlighterColors.XML_NS_PREFIX).getForegroundColor();
      myTagNameAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, tagNameColor);
      myNameAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, nameColor);
      myValueAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, valueColor);
      myPrefixAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, prefixColor);
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof ManifestTreeNode) {
        ManifestTreeNode node = (ManifestTreeNode)value;

        // on GTK theme the Tree.isFileColorsEnabled does not work, so we fall back to using the background
        if (UIUtil.isUnderGTKLookAndFeel()) {
          // we need to make the colors saturated, but with alpha, so the selector and foreground text still work
          setBackground(ColorUtil.withAlpha(harder(getNodeColor(node.getUserObject())), 0.2));
          setOpaque(true);
        }

        setIcon(null);

        if (node.getUserObject() instanceof Element) {
          Element element = (Element)node.getUserObject();
          append("<");

          append(element.getTagName(), myTagNameAttributes);
          if (!expanded) {
            append(" ... " + getCloseTag(node));
          }
        }
        if (node.getUserObject() instanceof Attr) {
          Attr attr = (Attr)node.getUserObject();
          // if we are the last child, add ">"
          ManifestTreeNode parent = node.getParent();
          assert parent != null; // can not be null if we are a XmlAttribute

          if (attr.getPrefix() != null) {
            append(attr.getPrefix(), myPrefixAttributes);
            append(":");
            append(attr.getLocalName(), myNameAttributes);
          } else {
            append(attr.getName(), myNameAttributes);
          }
          append("=\"");
          append(attr.getValue(), myValueAttributes);
          append("\"");
          if (parent.lastAttribute() == node) {
            append(" " + getCloseTag(node));
          }
        }
      }
    }

    private String getCloseTag(ManifestTreeNode node) {
      return node.hasElementChildren() ? ">" : "/>";
    }
  }

  private class FileColorTree extends Tree {
    public FileColorTree() {
      setFont(myDefaultFont);
      setBackground(myBackgroundColor);
    }

    /**
     * @see com.intellij.ide.projectView.impl.ProjectViewTree#isFileColorsEnabledFor(JTree)
     */
    @Override
    public boolean isFileColorsEnabled() {
      if (isOpaque()) {
        // needed for fileColors to be able to paint
        setOpaque(false);
      }
      return true;
    }

    @Nullable
    @Override
    public Color getFileColorFor(Object object) {
      return getNodeColor((Node)object);
    }
  }
}
