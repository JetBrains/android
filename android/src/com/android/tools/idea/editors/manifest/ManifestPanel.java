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

import static com.android.SdkConstants.FN_BUILD_GRADLE;
import static com.android.tools.idea.gradle.util.GradleUtil.getDependencyDisplayName;
import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getModuleSystem;
import static com.intellij.openapi.command.WriteCommandAction.writeCommandAction;

import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.ide.common.util.PathString;
import com.android.manifmerger.Actions;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.XmlNode;
import com.android.projectmodel.ExternalAndroidLibrary;
import com.android.tools.adtui.workbench.WorkBenchLoadingPanel;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.model.MergedManifestSnapshot;
import com.android.tools.idea.projectsystem.DependencyScopeType;
import com.android.tools.idea.projectsystem.FilenameConstants;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.rendering.HtmlLinkManager;
import com.android.utils.FileUtils;
import com.android.utils.HtmlBuilder;
import com.android.utils.PositionXmlParser;
import com.google.common.collect.Iterables;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.HTMLEditorKitBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.tree.TreeUtil;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.JEditorPane;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.SourceProviderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

// TODO for permission if not from main file
// TODO then have option to tools:node="remove" tools:selector="com.example.lib1"

// TODO merge conflict, then use tools:node=”replace”
// TODO or tools:node=”merge-only-attributes”

// TODO add option to tools:node=”removeAll" Remove all elements of the same node type
// TODO add option to tools:node=”strict” can be added to anything that merges perfectly

public class ManifestPanel extends JPanel implements TreeSelectionListener {

  private static final String SUGGESTION_MARKER = "Suggestion: ";
  private static final Pattern ADD_SUGGESTION_FORMAT = Pattern.compile(".*? 'tools:([\\w:]+)=\"([\\w:]+)\"' to \\<(\\w+)\\> element at [^:]+:(\\d+):(\\d+)-[\\d:]+ to override\\.", Pattern.DOTALL);
  private static final Pattern NAV_FILE_PATTERN = Pattern.compile(".*/res/.*navigation(-[^/]*)?/[^/]*$");

  /**
   * We don't have an exact position for values coming from the
   * Gradle model. This file is used as a marker pointing to the
   * Gradle model.
   */
  private static final File GRADLE_MODEL_MARKER_FILE = new File(FN_BUILD_GRADLE);

  private final AndroidFacet myFacet;
  private final Font myDefaultFont;
  private final Tree myTree;
  private final JEditorPane myDetails;
  private final WorkBenchLoadingPanel myLoadingPanel;
  private final JBSplitter mySplitter;
  private JPopupMenu myPopup;
  private JMenuItem myRemoveItem;

  private MergedManifestSnapshot myManifest;
  private boolean myManifestEditable;
  private final List<ManifestFileWithMetadata> myFiles = new ArrayList<>();
  private final List<ManifestFileWithMetadata> myOtherFiles = new ArrayList<>();
  private final HtmlLinkManager myHtmlLinkManager = new HtmlLinkManager();
  private VirtualFile myFile;
  private final Color myBackgroundColor;
  private Map<PathString, ExternalAndroidLibrary> myLibrariesByManifestDir;

  public ManifestPanel(final @NotNull AndroidFacet facet, final @NotNull Disposable parent) {
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

    mySplitter = new JBSplitter(0.5f);
    mySplitter.setFirstComponent(new JBScrollPane(myTree));
    mySplitter.setSecondComponent(new JBScrollPane(myDetails));

    myLoadingPanel = new WorkBenchLoadingPanel(new BorderLayout(), parent, 0);
    myLoadingPanel.add(mySplitter);
    add(myLoadingPanel);
  }

  @NotNull
  public JEditorPane getDetailsPane() {
    return myDetails;
  }

  @NotNull
  public Tree getTree() {
    return myTree;
  }

  private JEditorPane createDetailsPane(@NotNull final AndroidFacet facet) {
    JEditorPane details = new JEditorPane();
    details.setMargin(JBUI.insets(5));
    details.setEditorKit(HTMLEditorKitBuilder.simple());
    details.setEditable(false);
    details.setFont(myDefaultFont);
    details.setBackground(myBackgroundColor);
    HyperlinkListener hyperLinkListener = e -> {
      if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        String url = e.getDescription();
        myHtmlLinkManager.handleUrl(url, facet.getModule(), null, null, false, null);
      }
    };
    details.addHyperlinkListener(hyperLinkListener);

    return details;
  }

  private void createPopupMenu() {
    myPopup = new JBPopupMenu();
    JMenuItem gotoItem = new JBMenuItem("Go to Declaration");
    gotoItem.addActionListener(e -> {
      TreePath treePath = myTree.getSelectionPath();
      final ManifestTreeNode node = (ManifestTreeNode)treePath.getLastPathComponent();
      if (node != null) {
        goToDeclaration(node.getUserObject());
      }
    });
    myPopup.add(gotoItem);
    myRemoveItem = new JBMenuItem("Remove");
    myRemoveItem.addActionListener(e -> {
      TreePath treePath = myTree.getSelectionPath();
      final ManifestTreeNode node = (ManifestTreeNode)treePath.getLastPathComponent();

      WriteCommandAction.writeCommandAction(myFacet.getModule().getProject(), ManifestUtils.getMainManifest(myFacet)).withName("Removing manifest tag").run(()-> {
        ManifestUtils.toolsRemove(ManifestUtils.getMainManifest(myFacet), node.getUserObject());
      });
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
          JBPopupMenu.showByEvent(e, myPopup);
        }
      }
    };
    myTree.addMouseListener(ml);
    myDetails.addMouseListener(ml);
  }

  private void registerGotoAction() {
    AnAction goToDeclarationAction = new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
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


  public void startLoading() {
    mySplitter.setVisible(false);
    myLoadingPanel.setLoadingText("Computing merged manifest...");
    myLoadingPanel.startLoading();
  }

  public void showLoadingError() {
    myLoadingPanel.abortLoading("Unable to compute merged manifest.", AllIcons.General.Warning);
  }

  public void showManifest(MergedManifestSnapshot manifest, @NotNull VirtualFile selectedManifest, boolean isEditable) {
    this.myManifestEditable = isEditable;
    setManifestSnapshot(manifest, selectedManifest);
    myLoadingPanel.stopLoading();
    mySplitter.setVisible(true);
  }

  private void setManifestSnapshot(@NotNull MergedManifestSnapshot manifest, @NotNull VirtualFile selectedManifest) {
    myFile = selectedManifest;
    myManifest = manifest;
    myLibrariesByManifestDir =
      Arrays.stream(ModuleManager.getInstance(myManifest.getModule().getProject()).getModules())
        .flatMap(module -> getModuleSystem(module)
          .getAndroidLibraryDependencies(DependencyScopeType.MAIN)
          .stream()
          .filter(it -> it.getManifestFile() != null)
        )
        .collect(Collectors.toMap(it -> it.getManifestFile().getParent(),
                                  it -> it,
                                  (a, b) -> a // Ignore any duplicates.
        ));
    Document document = myManifest.getDocument();
    Element root = document != null ? document.getDocumentElement() : null;
    myTree.setModel(root == null ? null : new DefaultTreeModel(new ManifestTreeNode(root)));

    List<ManifestFileWithMetadata> sortedFiles = new ArrayList<>();
    List<ManifestFileWithMetadata> sortedOtherFiles = new ArrayList<>();


    List<VirtualFile> manifestFiles = myManifest.getManifestFiles();

    // make sure that the selected manifest is always the first color
    sortedFiles.add(createMetadataForFile(myFacet, new SourceFilePosition(VfsUtilCore.virtualToIoFile(selectedManifest), SourcePosition.UNKNOWN)));
    Set<File> referenced = new HashSet<>();
    if (root != null) {
      recordLocationReferences(root, referenced);
    }

    for (VirtualFile f : manifestFiles) {
      if (!f.equals(selectedManifest)) {
        File file = VfsUtilCore.virtualToIoFile(f);
        if (referenced.contains(file)) {
          sortedFiles.add(createMetadataForFile(myFacet, new SourceFilePosition(file, SourcePosition.UNKNOWN)));
        } else {
          sortedOtherFiles.add(createMetadataForFile(myFacet, new SourceFilePosition(file, SourcePosition.UNKNOWN)));
        }
      }
    }

    // Build.gradle - injected
    if (referenced.contains(GRADLE_MODEL_MARKER_FILE)) {
      sortedFiles.add(createMetadataForFile(myFacet, new SourceFilePosition(GRADLE_MODEL_MARKER_FILE, SourcePosition.UNKNOWN)));
    }

    Collections.sort(sortedFiles);
    Collections.sort(sortedOtherFiles);

    myFiles.clear();
    myFiles.addAll(sortedFiles);
    myOtherFiles.clear();
    myOtherFiles.addAll(sortedOtherFiles);


    if (root != null) {
      TreeUtil.expandAll(myTree);
    }

    // display the LoggingRecords from the merger
    updateDetails(null);
  }

  private void recordLocationReferences(@NotNull Node node, @NotNull Set<File> files) {
    short type = node.getNodeType();
    if (type == Node.ATTRIBUTE_NODE) {
      List<? extends Actions.Record> records = ManifestUtils.getRecords(myManifest, node);
      if (!records.isEmpty()) {
        Actions.Record record = records.get(0);

        // Ignore keys specified on the parent element; those are misleading
        XmlNode.NodeKey targetId = record.getTargetId();
        if (targetId.toString().contains("@")) {
          // Injected values correspond to the Gradle model; we don't have
          // an accurate file location so just use a marker file.
          if (record.getActionType() == Actions.ActionType.INJECTED) {
            files.add(GRADLE_MODEL_MARKER_FILE);
          }
          else {
            File location = record.getActionLocation().getFile().getSourceFile();
            if (location != null && !files.contains(location)) {
              files.add(location);
            }
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

  private void updateDetails(@Nullable ManifestTreeNode node) {
    Node manifestNode = node != null ? node.getUserObject() : null;
    HtmlBuilder sb = prepareHtmlReport(manifestNode);
    myDetails.setText(sb.getHtml());
    myDetails.setCaretPosition(0);
  }

  private @NotNull HtmlBuilder prepareHtmlReport(@Nullable Node node) {
    HtmlBuilder sb = new HtmlBuilder();
    prepareReportHeader(sb);

    // If a node is selected, show relevant info, otherwise show any general errors.
    if (node != null) {
      prepareSelectedNodeReport(node, sb);
    }
    else {
      prepareMergingErrorsReportForEverything(sb);
    }

    sb.closeHtmlBody();
    return sb;
  }

  private void prepareMergingErrorsReportForEverything(@NotNull HtmlBuilder sb) {
    List<MergingReport.Record> errors =
      myManifest.getLoggingRecords().stream()
        .filter(record -> record.getSeverity().equals(MergingReport.Record.Severity.ERROR))
        .collect(Collectors.toList());
    if (!errors.isEmpty()) {
      appendMergeRecordTitle(sb, "Merge Errors");
      errors.forEach((record) -> prepareErrorRecord(sb, record));
    }

    List<MergingReport.Record> warnings = myManifest.getLoggingRecords().stream()
      .filter(record -> record.getSeverity().equals(MergingReport.Record.Severity.WARNING))
      .collect(Collectors.toList());
    if (!warnings.isEmpty()) {
      appendMergeRecordTitle(sb, "Merge Warnings");
      warnings.forEach((record) -> prepareErrorRecord(sb, record));
    }
  }

  private void prepareSelectedNodeReport(@NotNull Node manifestNode, @NotNull HtmlBuilder sb) {
    List<? extends Actions.Record> records = ManifestUtils.getRecords(myManifest, manifestNode);
    sb.beginUnderline().beginBold();
    sb.add("Merging Log");
    sb.endBold().endUnderline().newline();

    if (records.isEmpty()) {
      sb.add("No records found. (This is a bug in the manifest merger.)");
    }

    SourceFilePosition prev = null;
    boolean prevInjected = false;
    for (Actions.Record record : records) {
      // There are currently some duplicated entries; filter these out
      SourceFilePosition location = ManifestUtils.getActionLocation(myFacet.getModule(), record);
      if (location.equals(prev)) {
        continue;
      }
      prev = location;

      Actions.ActionType actionType = record.getActionType();
      boolean injected = actionType == Actions.ActionType.INJECTED;
      if (injected && prevInjected) {
        continue;
      }
      prevInjected = injected;
      if (injected) {
        sb.add("Value provided by Gradle"); // TODO: include module source? Are we certain it's correct?
        sb.newline();
        continue;
      }
      sb.add(StringUtil.capitalize(StringUtil.toLowerCase(String.valueOf(actionType))));
      sb.add(" from the ");
      sb.addHtml(getHtml(myFacet, location));

      String reason = record.getReason();
      if (reason != null) {
        sb.add("; reason: ");
        sb.add(reason);
      }
      sb.newline();
    }
    prepareMergingErrorsForNode(manifestNode, sb, records);
  }

  private void prepareMergingErrorsForNode(@NotNull Node manifestNode,
                                           @NotNull HtmlBuilder sb,
                                           List<? extends Actions.Record> actionRecords) {
    if (doesNodeHaveRecordOfSeverity(manifestNode, MergingReport.Record.Severity.WARNING)) {
      appendMergeRecordTitle(sb, "Merge Warnings");
      myManifest.getLoggingRecords().stream()
        .filter(record ->
                  actionRecords.stream().anyMatch(actionRecord -> record.getSourceLocation().equals(actionRecord.getActionLocation())))
        .forEach(record -> prepareErrorRecord(sb, record));
    }
    if (doesNodeHaveRecordOfSeverity(manifestNode, MergingReport.Record.Severity.ERROR)) {
      appendMergeRecordTitle(sb, "Merge Errors");
      myManifest.getLoggingRecords().stream()
        .filter(record ->
                  actionRecords.stream().anyMatch(actionRecord -> record.getSourceLocation().equals(actionRecord.getActionLocation())))
        .forEach(record -> prepareErrorRecord(sb, record));
    }
  }


  private void appendMergeRecordTitle(@NotNull HtmlBuilder sb, String title) {
    sb.newline();
    sb.beginUnderline().beginBold();
    sb.add(title);
    sb.endBold().endUnderline().newline();
  }

  private void prepareErrorRecord(@NotNull HtmlBuilder sb, MergingReport.Record record) {
    sb.addHtml(getHtmlForErrorRecord(record.getSeverity()));
    sb.add(" ");
    try {
      sb.addHtml(getErrorHtml(myFacet, record.getMessage(), record.getSourceLocation(), myHtmlLinkManager,
                              LocalFileSystem.getInstance().findFileByIoFile(myFiles.get(0).getFile()), myManifestEditable));
    }
    catch (Exception ex) {
      Logger.getInstance(ManifestPanel.class).error("error getting error html", ex);
      sb.add(record.getMessage());
    }
    sb.add(" ");
    sb.addHtml(getHtml(myFacet, record.getSourceLocation()));
    sb.newline();
  }

  private void prepareReportHeader(@NotNull HtmlBuilder sb) {
    Font font = StartupUiUtil.getLabelFont();
    sb.addHtml("<html><body style=\"font-family: " + font.getFamily() + "; " + "font-size: " + font.getSize() + "pt;\">");
    sb.beginUnderline().beginBold();
    sb.add("Manifest Sources");
    sb.endBold().endUnderline().newline();
    sb.addHtml("<table border=\"0\">");
    String borderColor = ColorUtil.toHex(JBColor.GRAY);
    for (ManifestFileWithMetadata file : myFiles) {
      if (file.getFile() != null) {
        Color color = getFileColor(file.getFile());
        sb.addHtml("<tr><td width=\"24\" height=\"24\" style=\"background-color:#");
        sb.addHtml(ColorUtil.toHex(color));
        sb.addHtml("; border: 1px solid #");
        sb.addHtml(borderColor);
        sb.addHtml(";\">");
        sb.addHtml("</td><td>");
        describePosition(sb, file);
        sb.addHtml("</td></tr>");
      }
    }
    sb.addHtml("</table>");
    sb.newline();
    if (!myOtherFiles.isEmpty()) {
      sb.beginUnderline().beginBold();
      sb.add("Other Manifest Files");
      sb.endBold().endUnderline().newline();
      sb.add("(Included in merge, but did not contribute any elements)").newline();
      for (ManifestFileWithMetadata file : myOtherFiles) {
        describePosition(sb, file);
        sb.newline();
      }
      sb.newline().newline();
    }
  }

  @NotNull
  private Color getNodeColor(@NotNull Node node) {
    List<? extends Actions.Record> records = ManifestUtils.getRecords(myManifest, node);
    if (!records.isEmpty()) {
      Actions.Record record = records.get(0);
      File file;
      if (record.getActionType() == Actions.ActionType.INJECTED) {
        file = createMetadataForFile(myFacet, new SourceFilePosition(GRADLE_MODEL_MARKER_FILE, SourcePosition.UNKNOWN)).getFile();
      } else {
        file = createMetadataForFile(myFacet, ManifestUtils.getActionLocation(myFacet.getModule(), record)).getFile();
      }
      if (file != null) {
        return getFileColor(file);
      }
    }
    return myBackgroundColor;
  }

  private boolean doesNodeHaveRecordOfSeverity(@NotNull Node node, MergingReport.Record.Severity severity) {
    List<? extends Actions.Record> actionRecords = ManifestUtils.getRecords(myManifest, node);
    return myManifest.getLoggingRecords().stream().filter(record -> record.getSeverity().equals(severity)).anyMatch(
      record -> {
        for (Actions.Record actionRecord : actionRecords) {
          if (record.getSourceLocation().equals(actionRecord.getActionLocation())) {
            return true;
          }
        }
        return false;
      }
    );
  }

  @Nullable
  private Icon getNodeIcon(@NotNull Node node) {
    if (doesNodeHaveRecordOfSeverity(node, MergingReport.Record.Severity.ERROR)) {
      return StudioIcons.Common.ERROR;
    }
    else if (doesNodeHaveRecordOfSeverity(node, MergingReport.Record.Severity.WARNING)) {
      return StudioIcons.Common.WARNING;
    }
    else {
      return null;
    }
  }

  @NotNull
  private Color getFileColor(@NotNull File file) {
    List<String> filesMapped = myFiles.stream().map(test -> test.getFile().getAbsolutePath()).collect(Collectors.toList());

    int index = filesMapped.indexOf(file.getAbsolutePath());
    if (index == 0) {
      // current file shouldn't be highlighted with a background
      return myBackgroundColor;
    }
    return AnnotationColors.BG_COLORS[(index - 1) * AnnotationColors.BG_COLORS_PRIME % AnnotationColors.BG_COLORS.length];
  }

  private boolean canRemove(@NotNull Node node) {
    if (!myManifestEditable) {
      return false;
    }
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
                             final @Nullable VirtualFile currentlyOpenFile,
                             final boolean manifestEditable) {
    HtmlBuilder sb = new HtmlBuilder();
    int index = message.indexOf(SUGGESTION_MARKER);
    if (manifestEditable && index >= 0) {
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
        sb.add(message);
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
      sb.addLink(message, htmlLinkManager.createRunnableLink(() -> addToolsAttribute(mainManifest, xmlTag, attributeName, attributeValue)));
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
    /.../mylib/AndroidManifest.xml Suggestion: use a compatible library with a minSdk of
    at most 4, or increase this project's minSdk version to at least 8,
    or use tools:overrideLibrary="com.lib" to force usage (may lead to runtime failures)
     */
    HtmlBuilder sb = new HtmlBuilder();

    String versionPrefix = "to at least ";
    int start = message.indexOf(versionPrefix) + versionPrefix.length();
    if (start < 0) {
      throw new IllegalArgumentException("unexpected use suggestion format " + message);
    }
    int end = message.indexOf(',', start);
    if (end < 0) {
      throw new IllegalArgumentException("unexpected use suggestion format " + message);
    }
    final String minSdkVersionString = message.substring(start, end);
    int minSdkVersion;
    try {
      minSdkVersion = Integer.parseInt(minSdkVersionString);
    }
    catch (NumberFormatException e) {
      // Ignore this and just add the message, we don't want to add a link
      sb.add(message);
      return sb.getHtml();
    }

    final int finalMinSdk = minSdkVersion;

    Runnable link =
      () -> {
        Runnable linkAction = () -> {
          // We reparse the buildModel as it is possible that it has change since this link was created.
          ProjectBuildModel pbm = ProjectBuildModel.get(facet.getModule().getProject());
          GradleBuildModel gbm = pbm.getModuleBuildModel(facet.getModule());

          if (gbm == null) {
            return;
          }

          gbm.android().defaultConfig().minSdkVersion().setValue(finalMinSdk);
          ApplicationManager.getApplication().invokeAndWait(() -> WriteCommandAction
            .runWriteCommandAction(facet.getModule().getProject(), "Update build file minSdkVersion", null, () -> pbm.applyChanges(),
                                   gbm.getPsiFile()));
          // We must make sure that the files have been updated before we sync, we block above but not here.
          Runnable syncRunnable = () -> requestSync(facet.getModule().getProject());
          if (ApplicationManager.getApplication().isUnitTestMode()) {
            syncRunnable.run();
          }
          else {
            ApplicationManager.getApplication().invokeLater(syncRunnable);
          }
        };

        if (ApplicationManager.getApplication().isUnitTestMode()) {
          linkAction.run();
        }
        else {
          ApplicationManager.getApplication().executeOnPooledThread(linkAction);
        }
      };
    sb.addLink(message.substring(0, end), htmlLinkManager.createRunnableLink(link));
    sb.add(message.substring(end));
    return sb.getHtml();
  }

  private static void requestSync(Project project) {
    assert ApplicationManager.getApplication().isDispatchThread();
    ProjectSystemUtil.getProjectSystem(project).getSyncManager().syncProject(ProjectSystemSyncManager.SyncReason.PROJECT_MODIFIED);
  }

  static void addToolsAttribute(final @NotNull XmlFile file,
                                final @NotNull Element element,
                                final @NotNull String attributeName,
                                final @NotNull String attributeValue) {
    final Project project = file.getProject();
    writeCommandAction(project).withName("Apply manifest suggestion").run(() -> {
      ManifestUtils.addToolsAttribute(file, element, attributeName, attributeValue);
    });
  }

  @NotNull
  static String getHtmlForErrorRecord(@NotNull MergingReport.Record.Severity severity) {
    String severityString = StringUtil.capitalize(StringUtil.toLowerCase(severity.toString()));
    if (severity == MergingReport.Record.Severity.ERROR || severity == MergingReport.Record.Severity.WARNING) {
      return new HtmlBuilder().addHtml("<font color=\"#" + ColorUtil.toHex(JBColor.RED) + "\">")
        .addBold(severityString).addHtml("</font>").endBold().addHtml(":").getHtml();
    }
    return severityString;
  }

  @NotNull
  String getHtml(@NotNull AndroidFacet facet, @NotNull SourceFilePosition sourceFilePosition) {
    HtmlBuilder sb = new HtmlBuilder();
    describePosition(sb, createMetadataForFile(facet, sourceFilePosition));
    return sb.getHtml();
  }

  private ManifestFileWithMetadata createMetadataForFile(@NotNull AndroidFacet facet, @NotNull SourceFilePosition sourceFilePosition) {
    SourceFile sourceFile = sourceFilePosition.getFile();
    SourcePosition sourcePosition = sourceFilePosition.getPosition();
    File file = sourceFile.getSourceFile();

    if (file != null && file.getAbsolutePath().equals(GRADLE_MODEL_MARKER_FILE.getAbsolutePath())) {
      VirtualFile gradleBuildFile = GradleUtil.getGradleBuildFile(facet.getModule());
      if (gradleBuildFile != null) {
        file = VfsUtilCore.virtualToIoFile(gradleBuildFile);
        return new InjectedBuildDotGradleFile(file);
      } else {
        return new InjectedBuildDotGradleFile(null);
      }
    }

    if (file != null && NAV_FILE_PATTERN.matcher(FileUtils.toSystemIndependentPath(file.toString())).matches()) {
      String source = "";
      Boolean isProjectFile = false;


      File resDir = file.getParentFile() == null ? null : file.getParentFile().getParentFile();
      VirtualFile vResDir = resDir == null ? null : LocalFileSystem.getInstance().findFileByIoFile(resDir);
      if (vResDir != null) {
        Module module = ModuleUtilCore.findModuleForFile(vResDir, facet.getModule().getProject());
        if (module != null) {
          isProjectFile = true;
        }
        for (NamedIdeaSourceProvider provider : SourceProviderManager.getInstance(facet).getCurrentSourceProviders()) {
          if (Iterables.contains(provider.getResDirectories(), vResDir)) {
            source += provider.getName() + " ";
            break;
          }
        }
      }
      source += file.getName();

      return new ManifestXmlWithMetadata(ManifestXmlType.NAVIGATION_XML, file, source, isProjectFile, sourcePosition);
    }

    if (file != null) {
      String source = null;
      boolean isProjectFile = false;

      Module[] modules = ModuleManager.getInstance(facet.getModule().getProject()).getModules();
      VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
      if (vFile != null) {
        String path = file.getPath();
        Module module = ModuleUtilCore.findModuleForFile(vFile, facet.getModule().getProject());
        if (module != null) {
          isProjectFile = true;
          if (modules.length >= 2) {
            source = ModuleSystemUtil.getHolderModule(module).getName();
          }

          // AAR library in the project build directory?
          if (path.contains(FilenameConstants.EXPLODED_AAR)) {
            source = findSourceForFileInExplodedAar(file);
          }
        }
        // AAR library in the build cache?
        // (e.g., ".android/build-cache/0d86e51789317f7eb0747ecb9da6162c7082982e/output/AndroidManifest.xml")
        // Since the user can change the location or name of the build cache directory, we need to detect it using the following pattern.
        else if (path.contains("output") && path.matches(".*\\w{40}[\\\\/]output.*")) {
          source = findSourceForFileInExplodedAar(file);
        }
        else if (path.contains("caches")) {
          // Look for the Gradle cache, where AAR libraries can appear when distributed via the google() Maven repository
          source = findSourceForFileInExplodedAar(file);
        }

        NamedIdeaSourceProvider provider = ManifestUtils.findManifestSourceProvider(facet, vFile);
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
      return new ManifestXmlWithMetadata(ManifestXmlType.ANDROID_MANIFEST_XML, file, source, isProjectFile, sourcePosition);
    }
    return UnknownManifestFile.INSTANCE;
  }

  private void describePosition(@NotNull HtmlBuilder sb, ManifestFileWithMetadata manifestFile) {
    if (manifestFile instanceof InjectedBuildDotGradleFile) {
      InjectedBuildDotGradleFile injectedFile = (InjectedBuildDotGradleFile) manifestFile;
      if (injectedFile.getFile() != null) {
        sb.addHtml("<a href=\"");
        sb.add(injectedFile.getFile() .toURI().toString());
        sb.addHtml("\">");
        sb.add(injectedFile.getFile() .getName());
        sb.addHtml("</a>");
        sb.add(" injection");
      } else {
        sb.add("build.gradle injection (source location unknown)");
      }
      return;
    }
    if (manifestFile instanceof ManifestXmlWithMetadata) {
      ManifestXmlWithMetadata manifestXml = (ManifestXmlWithMetadata)manifestFile;

      if (manifestXml.getType() == ManifestXmlType.NAVIGATION_XML) {
        sb.addHtml("<a href=\"");
        sb.add(manifestXml.getFile().toURI().toString());
        if (!SourcePosition.UNKNOWN.equals(manifestXml.getSourcePosition())) {
          sb.add(":");
          sb.add(String.valueOf(manifestXml.getSourcePosition().getStartLine()));
          sb.add(":");
          sb.add(String.valueOf(manifestXml.getSourcePosition().getStartColumn()));
        }
        sb.addHtml("\">");

        sb.add(manifestXml.getSourceLibrary());
        sb.addHtml("</a>");
        sb.add(" navigation file");

        if (!SourcePosition.UNKNOWN.equals(manifestXml.getSourcePosition())) {
          sb.add(", line ");
          sb.add(Integer.toString(manifestXml.getSourcePosition().getStartLine()));
        }
        return;
      }

      if (manifestXml.getType() == ManifestXmlType.ANDROID_MANIFEST_XML) {
        sb.addHtml("<a href=\"");

        sb.add(manifestXml.getFile().toURI().toString());
        if (!SourcePosition.UNKNOWN.equals(manifestXml.getSourcePosition())) {
          sb.add(":");
          sb.add(String.valueOf(manifestXml.getSourcePosition().getStartLine()));
          sb.add(":");
          sb.add(String.valueOf(manifestXml.getSourcePosition().getStartColumn()));
        }
        sb.addHtml("\">");

        sb.add(manifestXml.getSourceLibrary());
        sb.addHtml("</a>");
        sb.add(" manifest");

        if (FileUtil.filesEqual(manifestXml.getFile(), VfsUtilCore.virtualToIoFile(myFile))) {
          sb.add(" (this file)");
        }

        if (!SourcePosition.UNKNOWN.equals(manifestXml.getSourcePosition())) {
          sb.add(", line ");
          sb.add(Integer.toString(manifestXml.getSourcePosition().getStartLine()));
        }
      }
    }
  }

  @Nullable
  private String findSourceForFileInExplodedAar(@NotNull File file) {
    File parentFile = file.getParentFile();
    if (parentFile == null) return null;
    PathString parentFilePath = new PathString(parentFile);
    ExternalAndroidLibrary androidLibrary = myLibrariesByManifestDir.get(parentFilePath);
    if (androidLibrary == null) return null;
    return getDependencyDisplayName(androidLibrary.getAddress());
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

        setIcon(getNodeIcon(node.getUserObject()));

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
      return object == null? null : getNodeColor((Node)object);
    }
  }
}
