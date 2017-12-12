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

import com.android.ide.common.util.AssetUtil;
import com.android.resources.Density;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.adtui.validation.validators.FalseValidator;
import com.android.tools.idea.npw.assetstudio.*;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.ui.FileTreeCellRenderer;
import com.android.tools.idea.ui.FileTreeModel;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.utils.XmlUtils;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.android.tools.idea.npw.assetstudio.IconGenerator.getMdpiScaleFactor;
import static com.android.tools.idea.npw.assetstudio.IconGenerator.pathToDensity;
import static com.android.tools.idea.npw.assetstudio.LauncherIconGenerator.IMAGE_SIZE_FULL_BLEED_DP;
import static com.android.tools.idea.npw.assetstudio.LauncherIconGenerator.SIZE_FULL_BLEED_DP;

/**
 * This step allows the user to select a build variant and provides a preview of the assets that
 * are about to be created.
 */
public final class ConfirmGenerateImagesStep extends ModelWizardStep<GenerateIconsModel>
    implements PersistentStateComponent<PersistentState> {
  private static final String CONFIRMATION_STEP_PROPERTY = "confirmationStep";
  private static final String RESOURCE_DIRECTORY_PROPERTY = "resourceDirectory";

  private final List<NamedModuleTemplate> myTemplates;
  private final ValidatorPanel myValidatorPanel;
  private final ListenerManager myListeners = new ListenerManager();
  private final JBLabel myPreviewIcon;

  private JPanel myRootPanel;
  private JComboBox<NamedModuleTemplate> myPathsComboBox;
  private Tree myOutputPreviewTree;
  private CheckeredBackgroundPanel myPreviewPanel;
  private JTextField mySizeDpTextField;
  private JTextField myDensityTextField;
  private JTextField myFileTypeTextField;
  private JTextField mySizePxTextField;
  private JSplitPane mySplitPane;
  private Map<FileTreeModel.Node, GeneratedIcon> myNodeToPreviewImage = new HashMap<>();

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

  private ObjectProperty<NamedModuleTemplate> mySelectedTemplate;
  private BoolProperty myFilesAlreadyExist = new BoolValueProperty();

  public ConfirmGenerateImagesStep(@NotNull GenerateIconsModel model, @NotNull List<NamedModuleTemplate> templates) {
    super(model, "Confirm Icon Path");
    Preconditions.checkArgument(!templates.isEmpty());
    myTemplates = templates;
    myValidatorPanel = new ValidatorPanel(this, myRootPanel);

    DefaultComboBoxModel<NamedModuleTemplate> moduleTemplatesModel = new DefaultComboBoxModel<>();
    for (NamedModuleTemplate template : templates) {
      moduleTemplatesModel.addElement(template);
    }
    myPathsComboBox.setRenderer(new ListCellRendererWrapper<NamedModuleTemplate>() {
      @Override
      public void customize(JList list, NamedModuleTemplate template, int index, boolean selected, boolean hasFocus) {
        setText(template.getName());
      }
    });
    myPathsComboBox.setModel(moduleTemplatesModel);

    DefaultTreeModel emptyModel = new DefaultTreeModel(null);
    myOutputPreviewTree.setModel(emptyModel);
    myOutputPreviewTree.setCellRenderer(new FileTreeCellRenderer());
    myOutputPreviewTree.setBorder(BorderFactory.createLineBorder(UIUtil.getBoundsColor()));
    // Tell the tree to ask the TreeCellRenderer for an individual height for each cell.
    myOutputPreviewTree.setRowHeight(-1);
    myOutputPreviewTree.getEmptyText().setText("No resource folder defined in project");
    myOutputPreviewTree.addTreeSelectionListener(e -> {
      TreePath newPath = e.getNewLeadSelectionPath();
      showSelectedNodeDetails(newPath);
    });

    String alreadyExistsError = WizardUtils.toHtmlString(
        "Some existing files will be overwritten by this operation.<br>" +
        "Files which replace existing files are marked red in the preview above.");
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

  private void showSelectedNodeDetails(TreePath newPath) {
    if (newPath != null && newPath.getLastPathComponent() instanceof FileTreeModel.Node) {
      FileTreeModel.Node node = (FileTreeModel.Node)newPath.getLastPathComponent();

      GeneratedIcon generatedIcon = myNodeToPreviewImage.get(node);
      if (generatedIcon instanceof GeneratedImageIcon) {
        GeneratedImageIcon generatedImageIcon = (GeneratedImageIcon)generatedIcon;
        BufferedImage image = generatedImageIcon.getImage();
        ImageIcon icon = new ImageIcon(image);
        myPreviewIcon.setIcon(icon);
        myPreviewIcon.setVisible(true);

        //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale  // file names are not locale sensitive
        String extension = FileUtilRt.getExtension(node.name).toUpperCase();
        if (StringUtil.isEmpty(extension)) {
          myFileTypeTextField.setText("N/A");
        }
        else {
          myFileTypeTextField.setText(String.format("%s File", extension));
        }

        mySizePxTextField.setText(String.format("%dx%d", icon.getIconWidth(), icon.getIconHeight()));

        Density density = generatedImageIcon.getDensity();
        myDensityTextField.setText(density.getResourceValue());

        float scaleFactor = getMdpiScaleFactor(density);
        mySizeDpTextField.setText(
            String.format("%dx%d", Math.round(icon.getIconWidth() / scaleFactor), Math.round(icon.getIconHeight() / scaleFactor)));

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
              myFilePreviewEditor.setHighlighter(EditorHighlighterFactory.getInstance().createEditorHighlighter(null, StdFileTypes.XML));
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
            mySizeDpTextField.setText(String.format("%dx%d", dpSize.width, dpSize.height));
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
      Rectangle rectangle =
          AssetUtil.scaleRectangle(IMAGE_SIZE_FULL_BLEED_DP, getMdpiScaleFactor(Density.XHIGH) * 0.8f);
      ListenableFuture<BufferedImage> imageFuture = generatorContext.renderDrawable(xmlText, rectangle.getSize());
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
    mySelectedTemplate = ObjectProperty.wrap(new SelectedItemProperty<>(myPathsComboBox));

    PersistentStateUtil.load(this, getModel().getPersistentState().getChild(CONFIRMATION_STEP_PROPERTY));
  }

  @Override
  public void onWizardFinished() {
    getModel().getPersistentState().setChild(CONFIRMATION_STEP_PROPERTY, getState());
  }

  @Override
  @NotNull
  public PersistentState getState() {
    PersistentState state = new PersistentState();
    NamedModuleTemplate moduleTemplate = mySelectedTemplate.get();
    state.set(RESOURCE_DIRECTORY_PROPERTY, moduleTemplate.getName(), myTemplates.get(0).getName());
    return state;
  }

  @Override
  public void loadState(@NotNull PersistentState state) {
    String templateName = state.get(RESOURCE_DIRECTORY_PROPERTY);
    if (templateName != null) {
      for (NamedModuleTemplate template : myTemplates) {
        if (template.getName().equals(templateName)) {
          mySelectedTemplate.set(template);
          break;
        }
      }
    }
  }

  @Override
  @NotNull
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    getModel().setPaths(mySelectedTemplate.get().getPaths());
  }

  @Override
  protected void onEntering() {
    myListeners.release(mySelectedTemplate); // Just in case we're entering this step a second time.
    myListeners.receiveAndFire(mySelectedTemplate, (NamedModuleTemplate template) -> {
      IconGenerator iconGenerator = getModel().getIconGenerator();
      File resDir = template.getPaths().getResDirectory();
      if (iconGenerator == null || resDir == null || resDir.getParentFile() == null) {
        return;
      }

      myNodeToPreviewImage.clear();
      Map<File, GeneratedIcon> pathIconMap = iconGenerator.generateIntoIconMap(template.getPaths());
      myFilesAlreadyExist.set(false);

      // Create a FileTreeModel containing all generated files.
      FileTreeModel treeModel = new FileTreeModel(resDir.getParentFile(), true);
      for (Map.Entry<File, GeneratedIcon> entry : pathIconMap.entrySet()) {
        File path = entry.getKey();
        GeneratedIcon icon = entry.getValue();

        if (path.exists()) {
          myFilesAlreadyExist.set(true);
        }

        FileTreeModel.Node newNode = treeModel.forceAddFile(path, null);
        myNodeToPreviewImage.put(newNode, icon);
      }

      // Collect all directory names from all generated file names for sorting purposes.
      // We use this map instead of looking at the file system when sorting, since
      // not all files/directories exist on disk at this point.
      Set<File> outputDirectories = pathIconMap.keySet()
        .stream()
        .flatMap(x -> {
          File root = resDir.getParentFile();
          List<File> directories = new ArrayList<>();
          x = x.getParentFile();
          while (x != null && !Objects.equals(x, root)) {
            directories.add(x);
            x = x.getParentFile();
          }
          return directories.stream();
        })
        .distinct()
        .collect(Collectors.toSet());

      // Sort the FileTreeModel so that the preview tree entries are sorted.
      treeModel.sort(getFileComparator(outputDirectories));

      myOutputPreviewTree.setModel(treeModel);

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
          if (treeModel.isLeaf(rowPath.getLastPathComponent())) {
            myOutputPreviewTree.setSelectionRow(i);
            break;
          }
        }
      }
    });
  }

  @NotNull
  private static Comparator<File> getFileComparator(Set<File> outputDirectories) {
    return (file1, file2) -> {
      // Sort by "directory vs file" first, then by density, then by name.
      boolean isDirectory1 = outputDirectories.contains(file1);
      boolean isDirectory2 = outputDirectories.contains(file2);
      if (isDirectory1 == isDirectory2) {
        String path1 = file1.getAbsolutePath();
        String path2 = file2.getAbsolutePath();
        Density density1 = pathToDensity(path1 + File.separator);
        Density density2 = pathToDensity(path2 + File.separator);

        if (density1 != null && density2 != null && density1 != density2) {
          // Sort least dense to most dense.
          return Integer.compare(density2.ordinal(), density1.ordinal());
        }
        else {
          return path1.compareTo(path2);
        }
      }
      else if (isDirectory1) {
        return -1;
      }
      else {
        return 1;
      }
    };
  }

  @Override
  public void dispose() {
    if (myEditorFactory != null && myFilePreviewEditor != null) {
      myEditorFactory.releaseEditor(myFilePreviewEditor);
    }
    myListeners.releaseAll();
  }
}
