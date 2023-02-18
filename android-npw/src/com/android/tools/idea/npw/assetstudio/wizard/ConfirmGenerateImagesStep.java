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
package com.android.tools.idea.npw.assetstudio.wizard;

import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.orderTemplates;
import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.scaleDimension;
import static com.android.tools.idea.npw.assetstudio.IconGenerator.getMdpiScaleFactor;
import static com.android.tools.idea.npw.assetstudio.LauncherIconGenerator.SIZE_FULL_BLEED_DP;

import com.android.resources.Density;
import com.android.tools.adtui.common.ProposedFileTreeCellRenderer;
import com.android.tools.adtui.common.ProposedFileTreeModel;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.adtui.validation.validators.FalseValidator;
import com.android.tools.idea.npw.assetstudio.GeneratedIcon;
import com.android.tools.idea.npw.assetstudio.GeneratedImageIcon;
import com.android.tools.idea.npw.assetstudio.GeneratedXmlResource;
import com.android.tools.idea.npw.assetstudio.GraphicGeneratorContext;
import com.android.tools.idea.npw.assetstudio.IconCategory;
import com.android.tools.idea.npw.assetstudio.IconGenerator;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.projectsystem.AndroidModulePaths;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.rendering.VectorDrawableTransformer;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.ui.CheckeredBackgroundPanel;
import com.android.tools.idea.wizard.ui.WizardUtils;
import com.android.utils.XmlUtils;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSortedSet;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.NamedColorUtil;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.jetbrains.android.actions.widgets.SourceSetCellRenderer;
import org.jetbrains.android.actions.widgets.SourceSetItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This step allows the user to select a build variant and provides a preview of the assets that
 * are about to be created.
 */
public final class ConfirmGenerateImagesStep extends ModelWizardStep<GenerateIconsModel> {
  private final List<NamedModuleTemplate> myTemplates;
  private final ValidatorPanel myValidatorPanel;
  private final ListenerManager myListeners = new ListenerManager();
  private final JBLabel myPreviewIcon;

  private JPanel myRootPanel;
  private JComboBox<SourceSetItem> myPathsComboBox;
  private Tree myOutputPreviewTree;
  private CheckeredBackgroundPanel myPreviewPanel;
  private JTextField mySizeDpTextField;
  private JTextField myDensityTextField;
  private JTextField myFileTypeTextField;
  private JTextField mySizePxTextField;
  private JSplitPane mySplitPane;
  private Map<File, GeneratedIcon> myPathToPreviewImage;

  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myLeftPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myRightPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myPreviewFillPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private TitledSeparator myDetailsHeaderPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myDetailsGridContainer;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myDensityRow;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel mySizeDetailsRow;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel mySizePxRow;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myDetailsPanel;
  private JPanel myImagePreviewPanel;
  private JPanel myXmlPreviewPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JTextPane myXmlTextPane;
  private EditorEx myFilePreviewEditor;

  private EditorFactory myEditorFactory;
  private Document myXmlPreviewDocument;

  private ObjectProperty<SourceSetItem> mySelectedSourceSetItem;
  private final SourceSetItem myInitialSelectedItem;
  private final BoolProperty myFilesAlreadyExist = new BoolValueProperty();
  private ProposedFileTreeModel myProposedFileTreeModel;

  public ConfirmGenerateImagesStep(@NotNull GenerateIconsModel model, @NotNull List<NamedModuleTemplate> templates) {
    super(model, "Confirm Icon Path");
    Preconditions.checkArgument(!templates.isEmpty());
    myTemplates = templates;
    myValidatorPanel = new ValidatorPanel(this, myRootPanel);
    SourceSetItem[] resDirs = orderTemplates(templates).stream()
      .flatMap(template -> template.getPaths().getResDirectories().stream()
        .map(folder -> SourceSetItem.create(template, folder)))
      .filter(Objects::nonNull)
      .toArray(SourceSetItem[]::new);
    myInitialSelectedItem = Arrays.stream(resDirs)
      .filter(item -> item.getSourceSetName().equals(model.getTemplate().getName()) &&
                      item.getResDirUrl().equals(model.getResFolder().getAbsolutePath()))
      .findFirst().orElse(null);

    DefaultComboBoxModel<SourceSetItem> moduleTemplatesModel = new DefaultComboBoxModel<>(resDirs);
    myPathsComboBox.setModel(moduleTemplatesModel);
    myPathsComboBox.setRenderer(new SourceSetCellRenderer());

    DefaultTreeModel emptyModel = new DefaultTreeModel(null);
    myOutputPreviewTree.setModel(emptyModel);
    myOutputPreviewTree.setCellRenderer(new ProposedFileTreeCellRenderer());
    myOutputPreviewTree.setBorder(BorderFactory.createLineBorder(NamedColorUtil.getBoundsColor()));
    // Tell the tree to ask the TreeCellRenderer for an individual height for each cell.
    myOutputPreviewTree.setRowHeight(-1);
    myOutputPreviewTree.getEmptyText().setText("No resource folder defined in project");
    myOutputPreviewTree.addTreeSelectionListener(e -> {
      TreePath newPath = e.getNewLeadSelectionPath();
      showSelectedNodeDetails(newPath);
    });

    String alreadyExistsError = WizardUtils.toHtmlString("Some files (shown in red) will overwrite existing files.");
    myValidatorPanel.registerValidator(myFilesAlreadyExist, new FalseValidator(Validator.Severity.WARNING, alreadyExistsError));

    myPreviewIcon = new JBLabel();
    myPreviewIcon.setVisible(false);
    myPreviewIcon.setHorizontalAlignment(SwingConstants.CENTER);
    myPreviewIcon.setVerticalAlignment(SwingConstants.CENTER);

    myPreviewPanel.setLayout(new BorderLayout());
    myPreviewPanel.add(myPreviewIcon, BorderLayout.CENTER);

    // Replace the JSplitPane component with a Splitter (IntelliJ look & feel).
    //
    // Note: We set the divider location on the JSplitPane from the left component preferred size to override the
    //       default divider location of the new Splitter (the default is to put the divider in the middle).
    //mySplitPane.setDividerLocation(mySplitPane.getLeftComponent().getPreferredSize().width);
    GuiUtils.replaceJSplitPaneWithIDEASplitter(mySplitPane);
  }

  private void showSelectedNodeDetails(@Nullable TreePath newPath) {
    if (newPath != null && newPath.getLastPathComponent() instanceof ProposedFileTreeModel.Node) {
      ProposedFileTreeModel.Node node = (ProposedFileTreeModel.Node)newPath.getLastPathComponent();

      GeneratedIcon generatedIcon = myPathToPreviewImage.get(node.getFile());
      if (generatedIcon instanceof GeneratedImageIcon) {
        GeneratedImageIcon generatedImageIcon = (GeneratedImageIcon)generatedIcon;
        BufferedImage image = generatedImageIcon.getImage();
        ImageIcon icon = new ImageIcon(image);
        myPreviewIcon.setIcon(icon);
        myPreviewIcon.setVisible(true);

        String extension = StringUtil.toUpperCase(FileUtilRt.getExtension(node.getFile().getName()));
        if (StringUtil.isEmpty(extension)) {
          myFileTypeTextField.setText("N/A");
        }
        else {
          myFileTypeTextField.setText(String.format("%s File", extension));
        }

        mySizePxTextField.setText(String.format(Locale.US, "%dx%d", icon.getIconWidth(), icon.getIconHeight()));

        Density density = generatedImageIcon.getDensity();
        myDensityTextField.setText(density.getResourceValue());

        double scaleFactor = getMdpiScaleFactor(density);
        mySizeDpTextField.setText(
          String.format(Locale.US, "%dx%d", Math.round(icon.getIconWidth() / scaleFactor), Math.round(icon.getIconHeight() / scaleFactor)));

        mySizeDetailsRow.setVisible(true);
        mySizePxRow.setVisible(true);
        myImagePreviewPanel.setVisible(true);
        myXmlPreviewPanel.setVisible(false);
        return;
      }
      else if (generatedIcon instanceof GeneratedXmlResource) {
        GeneratedXmlResource xml = (GeneratedXmlResource)generatedIcon;
        String xmlText = xml.getXmlText();
        BufferedImage image = getPreviewImage(xml);
        if (image == null) {
          ApplicationManager.getApplication().runWriteAction(() -> {
            if (myEditorFactory == null) {
              myEditorFactory = EditorFactory.getInstance();
            }

            if (myXmlPreviewDocument == null) {
              myXmlPreviewDocument = myEditorFactory.createDocument("");
            }
            myXmlPreviewDocument.setReadOnly(false);
            myXmlPreviewDocument.setText(StringUtil.convertLineSeparators(xmlText));
            myXmlPreviewDocument.setReadOnly(true);

            if (myFilePreviewEditor == null) {
              myFilePreviewEditor = (EditorEx)myEditorFactory.createViewer(myXmlPreviewDocument);
              myFilePreviewEditor.setCaretVisible(false);
              myFilePreviewEditor.getSettings().setLineNumbersShown(false);
              myFilePreviewEditor.getSettings().setLineMarkerAreaShown(false);
              myFilePreviewEditor.getSettings().setFoldingOutlineShown(false);
              myFilePreviewEditor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(null, XmlFileType.INSTANCE));
              myXmlPreviewPanel.removeAll();
              myXmlPreviewPanel.add(myFilePreviewEditor.getComponent());
            }
          });

          myImagePreviewPanel.setVisible(false);
          myXmlPreviewPanel.setVisible(true);
        }
        else {
          ImageIcon icon = new ImageIcon(image);
          myPreviewIcon.setIcon(icon);
          String drawableType = getDrawableType(xmlText);
          myFileTypeTextField.setText(drawableType);
          myPreviewIcon.setVisible(true);
          myDensityTextField.setText(Density.ANYDPI.getShortDisplayValue());
          Dimension dpSize = getDpSize(xml);
          if (dpSize == null) {
            mySizeDetailsRow.setVisible(false);
          }
          else {
            mySizeDpTextField.setText(String.format(Locale.US, "%dx%d", dpSize.width, dpSize.height));
            mySizeDetailsRow.setVisible(true);
          }
          mySizePxRow.setVisible(false);
          myImagePreviewPanel.setVisible(true);
          myXmlPreviewPanel.setVisible(false);
        }

        return;
      }
    }

    // Reset properties of both preview panels.
    myPreviewIcon.setVisible(false);
    myPreviewIcon.setIcon(null);
    myFileTypeTextField.setText("");
    mySizeDpTextField.setText("");
    mySizePxTextField.setText("");
    myDensityTextField.setText("");
    // Activate the image preview by default: this is somewhat arbitrary, but the image preview
    // is the most common one, so it alleviates flickering when changing the selection fast in
    // the tree.
    myImagePreviewPanel.setVisible(true);
    myXmlPreviewPanel.setVisible(false);
  }

  @NotNull
  private static String getDrawableType(@NotNull String xmlText) {
    String tagName = XmlUtils.getRootTagName(xmlText);
    if (tagName != null) {
      switch (tagName) {
        case "vector":
          return "Vector Drawable";
        case "shape":
          return "Shape Drawable";
        case "bitmap":
          return "Bitmap Drawable";
        case "layer-list":
          return "Layer List";
      }
    }
    return "Drawable";
  }

  @Nullable
  private static Dimension getDpSize(@NotNull GeneratedXmlResource xml) {
    String xmlText = xml.getXmlText();
    Dimension size = VectorDrawableTransformer.getSizeDp(xmlText);
    if (size != null) {
      return size;
    }
    IconCategory xmlCategory = xml.getCategory();
    if (xmlCategory == IconCategory.ADAPTIVE_BACKGROUND_LAYER || xmlCategory == IconCategory.ADAPTIVE_FOREGROUND_LAYER) {
      return SIZE_FULL_BLEED_DP;
    }
    return null;
  }

  @Nullable
  private BufferedImage getPreviewImage(@NotNull GeneratedXmlResource xml) {
    String xmlText = xml.getXmlText();
    IconGenerator generator = getModel().getIconGenerator();
    IconCategory xmlCategory = xml.getCategory();
    if (generator != null
        && (xmlCategory == IconCategory.ADAPTIVE_BACKGROUND_LAYER || xmlCategory == IconCategory.ADAPTIVE_FOREGROUND_LAYER)) {
      GraphicGeneratorContext generatorContext = generator.getGraphicGeneratorContext();
      // Use the same scale as a full bleed preview at xhdpi (see LauncherIconGenerator.generatePreviewImage).
      Dimension size = VectorDrawableTransformer.getSizeDp(xmlText);
      if (size == null) {
        size = SIZE_FULL_BLEED_DP;
      }
      double scale = Math.max(size.width, size.height) >= 320 ? 0.7 : 0.8;
      Dimension scaledSize = scaleDimension(size, getMdpiScaleFactor(Density.XHIGH) * scale);
      Future<BufferedImage> imageFuture = generatorContext.renderDrawable(xmlText, scaledSize.getSize());
      try {
        return imageFuture.get();
      }
      catch (InterruptedException | ExecutionException e) {
        // Ignore.
      }
    }
    return null;
  }

  @Override
  @NotNull
  protected JComponent getComponent() {
    return myValidatorPanel;
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    mySelectedSourceSetItem = ObjectProperty.wrap(new SelectedItemProperty<>(myPathsComboBox));
    if (myInitialSelectedItem != null) {
      mySelectedSourceSetItem.set(myInitialSelectedItem);
    }
  }

  @Override
  @NotNull
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    SourceSetItem item = mySelectedSourceSetItem.get();
    NamedModuleTemplate template = findTemplateByName(item.getSourceSetName());
    if (template == null) {
      return;
    }
    GenerateIconsModel model = getModel();
    model.setTemplate(template);
    model.setResFolder(new File(item.getResDirUrl()));
    model.setFilesToDelete(myProposedFileTreeModel.getShadowConflictedFiles());
  }

  @Override
  protected void onEntering() {
    myListeners.release(mySelectedSourceSetItem); // Just in case we're entering this step a second time.
    myListeners.listenAndFire(mySelectedSourceSetItem, sourceSetItem -> {
      IconGenerator iconGenerator = getModel().getIconGenerator();
      NamedModuleTemplate template = findTemplateByName(sourceSetItem.getSourceSetName());
      File resDirectory = new File(sourceSetItem.getResDirUrl());
      if (iconGenerator == null || resDirectory.getParentFile() == null || template == null) {
        return;
      }
      AndroidModulePaths paths = template.getPaths();

      myFilesAlreadyExist.set(false);
      myPathToPreviewImage = iconGenerator.generateIntoIconMap(paths, new File(sourceSetItem.getResDirUrl()));

      // Collect all directory names from all generated file names for sorting purposes.
      // We use this map instead of looking at the file system when sorting, since
      // not all files/directories exist on disk at this point.
      Set<File> outputDirectories = myPathToPreviewImage.keySet()
        .stream()
        .flatMap(x -> {
          File root = resDirectory.getParentFile();
          List<File> directories = new ArrayList<>();
          File f = x.getParentFile();
          while (f != null && !Objects.equals(f, root)) {
            directories.add(f);
            f = f.getParentFile();
          }
          return directories.stream();
        })
        .collect(Collectors.toSet());

      // Create a tree model containing all generated files.
      Set<File> proposedFiles = ImmutableSortedSet
        .orderedBy(new DensityAwareFileComparator(outputDirectories))
        .addAll(myPathToPreviewImage.keySet())
        .build();
      myProposedFileTreeModel = new ProposedFileTreeModel(resDirectory.getParentFile(), proposedFiles);

      myFilesAlreadyExist.set(myProposedFileTreeModel.hasConflicts());
      myOutputPreviewTree.setModel(myProposedFileTreeModel);

      // The tree should be totally expanded by default
      // Note: There is subtle behavior here: even though we merely expand "rows", we
      //       actually end up expanding all entries in the tree, because
      //       "getRowCount()" keeps increasing as we expand each row.
      for (int i = 0; i < myOutputPreviewTree.getRowCount(); ++i) {
        myOutputPreviewTree.expandRow(i);
      }

      // Select first file entry by default so that the preview panel shows something.
      for (int i = 0; i < myOutputPreviewTree.getRowCount(); ++i) {
        TreePath rowPath = myOutputPreviewTree.getPathForRow(i);
        if (rowPath != null) {
          if (myProposedFileTreeModel.isLeaf(rowPath.getLastPathComponent())) {
            myOutputPreviewTree.setSelectionRow(i);
            break;
          }
        }
      }
    });
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myPathsComboBox;
  }

  @Override
  public void dispose() {
    if (myEditorFactory != null && myFilePreviewEditor != null) {
      myEditorFactory.releaseEditor(myFilePreviewEditor);
    }
    myListeners.releaseAll();
  }

  @Nullable
  private NamedModuleTemplate findTemplateByName(@NotNull String name) {
    return myTemplates.stream().filter(template -> name.equals(template.getName())).findFirst().orElse(null);
  }
}
