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
import com.android.ide.common.blame.SourceFile;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.manifmerger.Actions;
import com.android.manifmerger.MergingReport;
import com.android.manifmerger.XmlNode;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.parser.NamedObject;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.rendering.HtmlLinkManager;
import com.android.utils.HtmlBuilder;
import com.google.common.base.*;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
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

// TODO for permission if not from main file
// TODO then have option to tools:node="remove" tools:selector="com.example.lib1"

// TODO merge conflict, then use tools:node=”replace”
// TODO or tools:node=”merge-only-attributes”

// TODO add option to tools:node=”removeAll" Remove all elements of the same node type
// TODO add option to tools:node=”strict” can be added to anything that merges perfectly

public class ManifestPanel extends JPanel implements TreeSelectionListener {

  private static final Color[] FILE_COLORS = new Color[] { JBColor.RED, JBColor.ORANGE, JBColor.YELLOW, JBColor.GREEN, JBColor.BLUE, JBColor.MAGENTA };
  private static final String SUGGESTION_MARKER = "Suggestion: ";
  private static final Pattern ADD_SUGGESTION_FORMAT = Pattern.compile(".*? 'tools:([\\w:]+)=\"([\\w:]+)\"' to \\<(\\w+)\\> element at [^:]+:(\\d+):(\\d+)-[\\d:]+ to override\\.", Pattern.DOTALL);

  private final AndroidFacet myFacet;
  private Tree myTree;
  private JEditorPane myDetails;
  private JPopupMenu myPopup;
  private JMenuItem myRemoveItem;

  private MergedManifest myManifest;
  private final Map<String, XmlNode.NodeKey> myNodeKeys = new HashMap<String, XmlNode.NodeKey>();
  private final List<File> myFiles = new ArrayList<File>();
  private final HtmlLinkManager myHtmlLinkManager = new HtmlLinkManager();

  public ManifestPanel(final @NotNull AndroidFacet facet) {
    myFacet = facet;
    setLayout(new BorderLayout());

    myTree = new Tree() {
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
        return getBackgroundColorForXmlElement((XmlElement)object);
      }
    };

    TreeSelectionModel selectionModel = myTree.getSelectionModel();
    selectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    selectionModel.addTreeSelectionListener(this);

    myDetails = new JEditorPane();
    myDetails.setContentType(UIUtil.HTML_MIME);
    myDetails.setEditable(false);
    HyperlinkListener hyperLinkListener = new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          String url = e.getDescription();
          myHtmlLinkManager.handleUrl(url, facet.getModule(), null, null, null);
        }
      }
    };
    myDetails.addHyperlinkListener(hyperLinkListener);

    // We have to use ColoredTreeCellRenderer instead of DefaultTreeCellRenderer to allow the Tree.isFileColorsEnabled to work
    // as otherwise the DefaultTreeCellRenderer will always insist on filling the background
    myTree.setCellRenderer(new ColoredTreeCellRenderer() {
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
            setBackground(getBackgroundColorForXmlElement(node.getUserObject()));
            setOpaque(true);
          }

          setIcon(null);

          if (node.getUserObject() instanceof XmlTag) {
            append("<" + node.toString() + (expanded ? "" : " ... >"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
          }
          if (node.getUserObject() instanceof XmlAttribute) {
            // if we are the last child, add ">"
            ManifestTreeNode parent = node.getParent();
            assert parent != null; // can not be null if we are a XmlAttribute
            append(node.toString() + (parent.lastAttribute() == node ? " >" : ""));
          }
        }
      }
    });
    new TreeSpeedSearch(myTree);

    myPopup = new JPopupMenu();
    myRemoveItem = new JMenuItem("Remove");
    myRemoveItem.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        TreePath treePath = myTree.getSelectionPath();
        final ManifestTreeNode node = (ManifestTreeNode)treePath.getLastPathComponent();

        new WriteCommandAction<Void>(myFacet.getModule().getProject(), "Removing manifest tag", ManifestUtils.getMainManifest(myFacet)) {
          @Override
          protected void run(@NotNull Result<Void> result) throws Throwable {
            ManifestUtils.toolsRemove(ManifestUtils.getMainManifest(myFacet), node.getUserObject());
          }
        }.execute();
      }
    });
    myPopup.add(myRemoveItem);

    MouseListener ml = new MouseAdapter() {
      @Override
      public void mousePressed(@NotNull MouseEvent e) {
        if (SwingUtilities.isRightMouseButton(e)) {
          TreePath treePath = myTree.getPathForLocation(e.getX(), e.getY());
          if (treePath != null) {
            ManifestTreeNode node = (ManifestTreeNode)treePath.getLastPathComponent();
            myRemoveItem.setVisible(canRemove(node.getUserObject()));

            if (hasVisibleChildren(myPopup)) {
              myPopup.show(e.getComponent(), e.getX(), e.getY());
            }
          }
        }
      }
    };
    myTree.addMouseListener(ml);

    JBSplitter splitter = new JBSplitter(0.66f);
    splitter.setFirstComponent(new JBScrollPane(myTree));
    splitter.setSecondComponent(new JBScrollPane(myDetails));

    add(splitter);
  }

  public void setManifest(@NotNull MergedManifest manifest, @NotNull VirtualFile selectedManifest) {
    myManifest = manifest;
    XmlTag root = myManifest.getXmlTag();
    myTree.setModel(root == null ? null : new DefaultTreeModel(new ManifestTreeNode(root)));
    Actions actions = myManifest.getActions();

    myNodeKeys.clear();
    if (actions != null) {
      Set<XmlNode.NodeKey> keys = actions.getNodeKeys();
      for (XmlNode.NodeKey key : keys) {
        myNodeKeys.put(key.toString(), key);
      }
    }

    myFiles.clear();
    // make sure that the selected manifest is always the first color
    myFiles.add(VfsUtilCore.virtualToIoFile(selectedManifest));

    if (root != null) {
      TreeUtil.expandAll(myTree);
    }

    // display the LoggingRecords from the merger
    valueChanged(null);
  }

  @Override
  public void valueChanged(@Nullable TreeSelectionEvent e) {
    if (e != null && e.isAddedPath()) {
      TreePath treePath = e.getPath();
      ManifestTreeNode node = (ManifestTreeNode)treePath.getLastPathComponent();
      List<? extends Actions.Record> records = getRecords(node.getUserObject());
      HtmlBuilder sb = new HtmlBuilder();
      sb.openHtmlBody();
      for (Actions.Record record : records) {
        sb.add(String.valueOf(record.getActionType()));
        sb.add(" from ");
        sb.addHtml(getHtml(myFacet, record.getActionLocation()));

        String reason = record.getReason();
        if (reason != null) {
          sb.add(" reason: ");
          sb.add(reason);
        }
        sb.newline();
      }
      sb.closeHtmlBody();
      myDetails.setText(sb.getHtml());
    }
    else {
      HtmlBuilder sb = new HtmlBuilder();
      sb.openHtmlBody();
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
      sb.closeHtmlBody();
      myDetails.setText(sb.getHtml());
    }
  }

  @NotNull
  private Color getBackgroundColorForXmlElement(@NotNull XmlElement item) {
    Color background = myTree.getBackground();
    List<? extends Actions.Record> records = getRecords(item);
    if (!records.isEmpty()) {
      File file = records.get(0).getActionLocation().getFile().getSourceFile();
      if (file != null) {
        background = getFileColor(file);
      }
    }
    return ColorUtil.withAlpha(background, 0.2);
  }

  @NotNull
  private Color getFileColor(@NotNull File file) {
    if (!myFiles.contains(file)) {
      myFiles.add(file);
    }
    int index = myFiles.indexOf(file);
    if (index < FILE_COLORS.length) {
      return FILE_COLORS[index];
    }
    return JBColor.GRAY;
  }

  private boolean canRemove(@NotNull XmlElement node) {
    List<? extends Actions.Record> records = getRecords(node);
    if (records.isEmpty()) {
      // if we don't know where we are coming from, we are prob displaying the main manifest with a merge error.
      return false;
    }
    File mainManifest = VfsUtilCore.virtualToIoFile(ManifestUtils.getMainManifest(myFacet).getVirtualFile());
    for (Actions.Record record : records) {
      // if we are already coming from the main file, then we can't remove it using this editor
      if (FileUtil.filesEqual(record.getActionLocation().getFile().getSourceFile(), mainManifest)) {
        return false;
      }
    }
    return true;
  }

  private static boolean hasVisibleChildren(@NotNull Container container) {
    for (Component component : container.getComponents()) {
      if (component.isVisible()) {
        return true;
      }
    }
    return false;
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
    XmlFile mainManifest = ManifestUtils.getMainManifest(facet);
    final XmlTag xmlTag = ManifestUtils.getXmlTag(mainManifest, line, col);

    if (xmlTag != null && tagName.equals(xmlTag.getName())) {
      sb.addLink(message, htmlLinkManager.createRunnableLink(new Runnable() {
        @Override
        public void run() {
          addToolsAttribute(xmlTag, attributeName, attributeValue);
        }
      }));
    }
    else {
      Logger.getInstance(ManifestPanel.class).warn("can not find " + tagName + " tag " + xmlTag);
      sb.add(message);
    }
    return sb.getHtml();
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
      final XmlTag xmlTag = ManifestUtils.getXmlTag(mainManifest, position.getPosition().getStartLine() + 1, position.getPosition().getStartColumn() + 1);
      if (xmlTag != null && SdkConstants.TAG_USES_SDK.equals(xmlTag.getName())) {
        sb.addLink(message.substring(0, end + 1), htmlLinkManager.createRunnableLink(new Runnable() {
          @Override
          public void run() {
            int eq = suggestion.indexOf('=');
            String attributeName = suggestion.substring(suggestion.indexOf(':') + 1, eq);
            String attributeValue = suggestion.substring(eq + 2, suggestion.length() - 1);
            addToolsAttribute(xmlTag, attributeName, attributeValue);
          }
        }));
        sb.add(message.substring(end + 1));
      }
      else {
        Logger.getInstance(ManifestPanel.class).warn("can not find uses-sdk tag " + xmlTag);
        sb.add(message);
      }
    }
    else {
      // if we do not have a uses-sdk tag in our main manifest, the suggestion is not useful
      sb.add(message);
    }
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
              new WriteCommandAction<Void>(facet.getModule().getProject(), "Apply manifest suggestion", buildFile.getPsiFile(), manifestOverlayPsiFile) {
                @Override
                protected void run(@NotNull Result<Void> result) throws Throwable {
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
            new WriteCommandAction<Void>(facet.getModule().getProject(), "Apply manifest suggestion", buildFile.getPsiFile(), manifestOverlayPsiFile) {
              @Override
              protected void run(@NotNull Result<Void> result) throws Throwable {
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

  static void addToolsAttribute(final @NotNull XmlTag xmlTag, final @NotNull String attributeName, final @NotNull String attributeValue) {
    final XmlFile file = (XmlFile)xmlTag.getContainingFile();
    final Project project = file.getProject();
    new WriteCommandAction<Void>(project, "Apply manifest suggestion", file) {
      @Override
      protected void run(@NotNull Result<Void> result) throws Throwable {
        ManifestUtils.addToolsAttribute(file, xmlTag, attributeName, attributeValue);
      }
    }.execute();
  }

  @NotNull
  static String getHtml(@NotNull MergingReport.Record.Severity severity) {
    if (severity == MergingReport.Record.Severity.ERROR) {
      return new HtmlBuilder().addHtml("<font color=\"#" + ColorUtil.toHex(JBColor.RED) + "\">")
        .addBold(severity.toString()).addHtml("</font>").getHtml();
    }
    return severity.toString();
  }

  @NotNull
  static String getHtml(@NotNull AndroidFacet facet, @NotNull SourceFilePosition sourceFilePosition) {
    HtmlBuilder sb = new HtmlBuilder();
    SourceFile sourceFile = sourceFilePosition.getFile();
    SourcePosition sourcePosition = sourceFilePosition.getPosition();
    File file = sourceFile.getSourceFile();
    if (file != null) {
      sb.addHtml("<a href=\"");
      sb.add(file.toURI().toString());
      if (!SourcePosition.UNKNOWN.equals(sourcePosition)) {
        sb.add(":");
        sb.add(String.valueOf(sourcePosition.getStartLine()));
        sb.add(":");
        sb.add(String.valueOf(sourcePosition.getStartColumn()));
      }
      sb.addHtml("\">");
      sb.add(file.getName());
      if (!SourcePosition.UNKNOWN.equals(sourcePosition)) {
        sb.add(":");
        sb.add(String.valueOf(sourcePosition));
      }

      VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(file);
      if (vFile != null) {
        Module module = ModuleUtilCore.findModuleForFile(vFile, facet.getModule().getProject());
        if (module != null) {
          if (!module.equals(facet.getModule())) {
            sb.add(" (");
            sb.add(module.getName());
            sb.add(")");
          }
        }
        IdeaSourceProvider provider = ManifestUtils.findManifestSourceProvider(facet, vFile);
        if (provider != null && !provider.equals(facet.getMainIdeaSourceProvider())) {
          sb.add(" (");
          sb.add(provider.getName());
          sb.add(")");
        }
      }
      sb.addHtml("</a>");
    }
    return sb.getHtml();
  }

  @NotNull
  private List<? extends Actions.Record> getRecords(@NotNull XmlElement item) {
    Actions actions = myManifest.getActions();
    if (actions != null) {
      if (item instanceof XmlTag) {
        XmlTag xmlTag = (XmlTag)item;
        XmlNode.NodeKey key = getNodeKey(xmlTag);
        return actions.getNodeRecords(key);
      }
      else if (item instanceof XmlAttribute) {
        XmlAttribute xmlAttribute = (XmlAttribute)item;
        XmlTag xmlTag = xmlAttribute.getParent();
        XmlNode.NodeKey key = getNodeKey(xmlTag);
        XmlNode.NodeName name = XmlNode.fromXmlName(xmlAttribute.getName());
        List<? extends Actions.Record> attributeRecords = actions.getAttributeRecords(key, name);
        if (!attributeRecords.isEmpty()) {
          return attributeRecords;
        }
        return actions.getNodeRecords(key);
      }
    }
    return Collections.emptyList();
  }

  @Nullable("can not find report node for xml tag")
  private XmlNode.NodeKey getNodeKey(@NotNull XmlTag xmlTag) {
    XmlNode.NodeKey key = myNodeKeys.get(xmlTag.getName());
    if (key == null) {
      XmlAttribute attribute = xmlTag.getAttribute(SdkConstants.ATTR_NAME, SdkConstants.ANDROID_URI);
      if (attribute != null) {
        key = myNodeKeys.get(xmlTag.getName() + "#" + attribute.getValue());
      }
      else {
        XmlTag[] children = xmlTag.getSubTags();
        String[] names = new String[children.length];
        for (int c = 0; c < children.length; c++) {
          XmlAttribute childAttribute = children[c].getAttribute(SdkConstants.ATTR_NAME, SdkConstants.ANDROID_URI);
          if (childAttribute != null) {
            names[c] = childAttribute.getValue();
          }
          else {
            // we don't know how to find this item, give up
            return null;
          }
        }
        key = myNodeKeys.get(xmlTag.getName() + "#" + Joiner.on('+').join(names));
      }
    }
    return key;
  }

  static class ManifestTreeNode extends DefaultMutableTreeNode {

    public ManifestTreeNode(@NotNull XmlElement obj) {
      super(obj);
    }

    @Override
    @NotNull
    public XmlElement getUserObject() {
      return (XmlElement)super.getUserObject();
    }

    @Override
    public int getChildCount() {
      XmlElement obj = getUserObject();
      if (obj instanceof XmlTag) {
        XmlTag xmlTag = (XmlTag)obj;
        return xmlTag.getAttributes().length + xmlTag.getSubTags().length;
      }
      return 0;
    }

    @Override
    @NotNull
    public ManifestTreeNode getChildAt(int index) {
      XmlElement obj = getUserObject();
      if (children == null && obj instanceof XmlTag) {
        XmlTag xmlTag = (XmlTag)obj;
        for (XmlAttribute xmlAttribute : xmlTag.getAttributes()) {
          add(new ManifestTreeNode(xmlAttribute));
        }
        for (XmlTag childTag : xmlTag.getSubTags()) {
          add(new ManifestTreeNode(childTag));
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
      XmlElement obj = getUserObject();
      if (obj instanceof XmlAttribute) {
        XmlAttribute xmlAttribute = (XmlAttribute)obj;
        return xmlAttribute.getName() + " = " + xmlAttribute.getValue();
      }
      if (obj instanceof XmlTag) {
        XmlTag xmlTag = (XmlTag)obj;
        return xmlTag.getName();
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
      XmlTag xmlTag = (XmlTag)getUserObject();
      return getChildAt(xmlTag.getAttributes().length - 1);
    }
  }
}
