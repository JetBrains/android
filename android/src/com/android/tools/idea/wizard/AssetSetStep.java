/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.tools.idea.wizard;

import com.android.assetstudiolib.ActionBarIconGenerator;
import com.android.assetstudiolib.GraphicGenerator;
import com.android.builder.model.SourceProvider;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.google.common.collect.Iterators;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import static com.android.tools.idea.wizard.AssetStudioAssetGenerator.*;

/**
 * {@linkplain AssetSetStep} is a wizard page that lets the user create a variety of density-scaled assets.
 */
public class AssetSetStep extends TemplateWizardStep implements Disposable {
  private static final Logger LOG = Logger.getInstance(AssetSetStep.class);
  private static final int CLIPART_ICON_SIZE = 32;
  private static final int CLIPART_DIALOG_BORDER = 10;
  private static final int DIALOG_HEADER = 20;
  public static final String ATTR_ICON_RESOURCE = "icon_resource";

  private static final String V11 = "V11";
  private static final String V9 = "V9";
  private SourceProvider mySourceProvider = null;

  private AssetStudioAssetGenerator myAssetGenerator;
  private JPanel myPanel;
  private JRadioButton myImageRadioButton;
  private JRadioButton myClipartRadioButton;
  private JRadioButton myTextRadioButton;
  private JRadioButton myCropRadioButton;
  private JRadioButton myCenterRadioButton;
  private JRadioButton myCircleRadioButton;
  private JRadioButton mySquareRadioButton;
  private JRadioButton myNoneRadioButton;
  private JButton myChooseClipart;
  private JLabel myError;
  private JLabel myDescription;
  private JCheckBox myTrimBlankSpace;
  private JTextField myText;
  private JComboBox myFontFamily;
  private TextFieldWithBrowseButton myImageFile;
  private ColorPanel myBackgroundColor;
  private ColorPanel myForegroundColor;
  private ImageComponent myMdpiPreview;
  private ImageComponent myHdpiPreview;
  private ImageComponent myXHdpiPreview;
  private JSlider myPaddingSlider;
  private ImageComponent myXXHdpiPreview;
  private JLabel myImageFileLabel;
  private JLabel myTextLabel;
  private JLabel myFontFamilyLabel;
  private JLabel myChooseClipartLabel;
  private JLabel myBackgroundColorLabel;
  private JLabel myForegroundColorLabel;
  private JComboBox myAssetTypeComboBox;
  private JLabel myAssetTypeLabel;
  private JComboBox myChooseThemeComboBox;
  private JLabel myChooseThemeLabel;
  private JLabel myForegroundScalingLabel;
  private JLabel myShapeLabel;
  private JPanel myShapePanel;
  private JPanel myScalingPanel;
  private JTextField myResourceNameField;
  private JLabel myResourceNameLabel;
  private JPanel myVersionPanel;
  private ImageComponent myV9XHdpiPreview;
  private ImageComponent myV9XXHdpiPreview;
  private ImageComponent myV9MdpiPreview;
  private ImageComponent myV9HdpiPreview;
  private ImageComponent myV11MdpiPreview;
  private ImageComponent myV11HdpiPreview;
  private ImageComponent myV11XHdpiPreview;
  private ImageComponent myV11XXHdpiPreview;
  private ImageComponent myXXXHdpiPreview;
  private JRadioButton myVerticalRadioButton;
  private JRadioButton myHorizontalRadioButton;
  private JLabel myXXXHDPILabel;
  private JCheckBox myDogEarEffectCheckBox;
  private JBScrollPane myScrollPane;

  protected AssetType mySelectedAssetType;
  private boolean myInitialized;
  private final StringEvaluator myStringEvaluator = new StringEvaluator();
  private final MergingUpdateQueue myUpdateQueue;
  private final Map<String, Map<String, BufferedImage>> myImageMap = new ConcurrentHashMap<String, Map<String, BufferedImage>>();

  @SuppressWarnings("UseJBColor") // Colors are used for the graphics generator, not the plugin UI
  public AssetSetStep(TemplateWizardState state, @Nullable Project project, @Nullable Module module,
                      @Nullable Icon sidePanelIcon, UpdateListener updateListener, @Nullable VirtualFile invocationTarget) {
    super(state, project, module, sidePanelIcon, updateListener);
    myAssetGenerator = new AssetStudioAssetGenerator(state);
    if (invocationTarget != null && module != null) {
      AndroidFacet facet = AndroidFacet.getInstance(myModule);
      if (facet != null) {
        mySourceProvider = Iterators.getNext(IdeaSourceProvider.getSourceProvidersForFile(facet, invocationTarget, null).iterator(), null);
      }
    }

    // Speed the scrolling of myScrollPane
    myScrollPane.getVerticalScrollBar().setUnitIncrement(16);

    myUpdateQueue = new MergingUpdateQueue("asset.studio", 200, true, null, this, null, false);

    register(ATTR_TEXT, myText);
    register(ATTR_SCALING, myCropRadioButton, Scaling.CROP);
    register(ATTR_SCALING, myCenterRadioButton, Scaling.CENTER);
    register(ATTR_SHAPE, myCircleRadioButton, GraphicGenerator.Shape.CIRCLE);
    register(ATTR_SHAPE, myNoneRadioButton, GraphicGenerator.Shape.NONE);
    register(ATTR_SHAPE, mySquareRadioButton, GraphicGenerator.Shape.SQUARE);
    register(ATTR_SHAPE, myVerticalRadioButton, GraphicGenerator.Shape.VRECT);
    register(ATTR_SHAPE, myHorizontalRadioButton, GraphicGenerator.Shape.HRECT);
    register(ATTR_DOGEAR, myDogEarEffectCheckBox);
    register(ATTR_PADDING, myPaddingSlider);
    register(ATTR_TRIM, myTrimBlankSpace);
    register(ATTR_FONT, myFontFamily);
    register(ATTR_SOURCE_TYPE, myImageRadioButton, AssetStudioAssetGenerator.SourceType.IMAGE);
    register(ATTR_SOURCE_TYPE, myClipartRadioButton, AssetStudioAssetGenerator.SourceType.CLIPART);
    register(ATTR_SOURCE_TYPE, myTextRadioButton, AssetStudioAssetGenerator.SourceType.TEXT);
    register(ATTR_FOREGROUND_COLOR, myForegroundColor);
    register(ATTR_BACKGROUND_COLOR, myBackgroundColor);
    register(ATTR_ASSET_TYPE, myAssetTypeComboBox);
    register(ATTR_ASSET_THEME, myChooseThemeComboBox);
    register(ATTR_ASSET_NAME, myResourceNameField);


    myImageFile.addBrowseFolderListener(null, null, null, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    myForegroundColor.setSelectedColor(Color.BLUE);
    myBackgroundColor.setSelectedColor(Color.WHITE);

    for (String font : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
      myFontFamily.addItem(new ComboBoxItem(font, font, 1, 1));
      if (font.equals(myTemplateState.get(ATTR_FONT))) {
        myFontFamily.setSelectedIndex(myFontFamily.getItemCount() - 1);
      }
    }

    myChooseClipart.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        displayClipartDialog();
      }
    });

    populateComboBox(myAssetTypeComboBox, AssetType.class);
    populateComboBox(myChooseThemeComboBox, ActionBarIconGenerator.Theme.class);
  }

  @Override
  public void deriveValues() {
    super.deriveValues();

    myTemplateState.put(ATTR_ICON_RESOURCE, myTemplateState.getString(ATTR_ASSET_NAME));

    // Source Radio button
    if (myImageRadioButton.isSelected()) {
      hide(myChooseClipart, myChooseClipartLabel, myText, myTextLabel, myFontFamily, myFontFamilyLabel, myForegroundColor,
           myForegroundColorLabel);
      show(myImageFile, myImageFileLabel, myBackgroundColor, myBackgroundColorLabel);
    } else if (myClipartRadioButton.isSelected()) {
      hide(myText, myTextLabel, myFontFamily, myFontFamilyLabel, myImageFile, myImageFileLabel);
      show(myChooseClipart, myChooseClipartLabel, myBackgroundColor, myBackgroundColorLabel,myForegroundColor, myForegroundColorLabel);
    } else if (myTextRadioButton.isSelected()) {
      hide(myChooseClipart, myChooseClipartLabel, myImageFile, myImageFileLabel);
      show(myText, myTextLabel, myFontFamily, myFontFamilyLabel, myBackgroundColor, myBackgroundColorLabel, myForegroundColor,
           myForegroundColorLabel);
      myFontFamily.setSelectedItem(myTemplateState.getString(ATTR_FONT));
    }

    // Asset Type Combo Box
    if (myTemplateState.get(ATTR_ASSET_TYPE) != null) {
      final AssetType selectedAssetType = AssetType.valueOf(myTemplateState.getString(ATTR_ASSET_TYPE));
      mySelectedAssetType = selectedAssetType;
      if (selectedAssetType != null) {
        switch (selectedAssetType) {
          case LAUNCHER:
            hide(myChooseThemeComboBox, myChooseThemeLabel, myVersionPanel,
                 myDogEarEffectCheckBox);
            show(myForegroundScalingLabel, myScalingPanel, myShapeLabel, myShapePanel,
                 myResourceNameLabel, myResourceNameField, myXXXHdpiPreview, myXXXHDPILabel, myScrollPane);
            if (!myTemplateState.myModified.contains(ATTR_ASSET_NAME)) {
              myTemplateState.put(ATTR_ASSET_NAME, "icon");
            }
            //Dog-ear effect
            if(mySquareRadioButton.isSelected() || myVerticalRadioButton .isSelected()
               || myHorizontalRadioButton.isSelected()) {
              show(myDogEarEffectCheckBox);
            }
            break;
          case ACTIONBAR:
            show(myResourceNameField, myResourceNameLabel);
            show(myChooseThemeComboBox, myChooseThemeLabel, myScrollPane);
            hide(myForegroundScalingLabel, myScalingPanel, myShapeLabel, myShapePanel,
                 myBackgroundColorLabel, myBackgroundColor, myVersionPanel, myXXXHdpiPreview,
                 myXXXHDPILabel, myDogEarEffectCheckBox);
            break;
          case NOTIFICATION:
            show(myResourceNameField, myResourceNameLabel, myVersionPanel);
            hide(myChooseThemeComboBox, myChooseThemeLabel, myForegroundColor, myForegroundColorLabel);
            hide(myForegroundScalingLabel, myScalingPanel, myShapeLabel, myShapePanel,
                 myBackgroundColorLabel, myBackgroundColor, myScrollPane, myDogEarEffectCheckBox);
            break;
        }

        if (!myTemplateState.myModified.contains(ATTR_ASSET_NAME)) {
          updateDerivedValue(ATTR_ASSET_NAME, myResourceNameField, new Callable<String>() {
            @Override
            public String call() throws Exception {
              return computeResourceName();
            }
          });
        }
      }
    }

    // Theme chooser
    if (myChooseThemeComboBox.isVisible() && myTemplateState.hasAttr(ATTR_ASSET_THEME)) {
      if (ActionBarIconGenerator.Theme.valueOf(myTemplateState.getString(ATTR_ASSET_THEME))
          .equals(ActionBarIconGenerator.Theme.CUSTOM)) {
        show(myForegroundColor, myForegroundColorLabel);
      } else {
        hide(myForegroundColor, myForegroundColorLabel);
      }
    }
  }

  @Override
  public boolean validate() {
    if (!super.validate()) {
      return false;
    }


    String assetName = myTemplateState.getString(ATTR_ASSET_NAME);
    if (drawableExists(assetName)) {
      setErrorHtml(String.format("A drawable resource named %s already exists and will be overwritten.", assetName));
    }

    requestPreviewUpdate();
    return true;
  }

  /**
   * (Re)schedule the background task which updates the preview images.
   */
  private void requestPreviewUpdate() {
    myUpdateQueue.cancelAllUpdates();
    myUpdateQueue.queue(new Update("update") {
      @Override
      public void run() {
        try {
          myAssetGenerator.generateImages(myImageMap, true, true);
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              updatePreviewImages();
            }
          });
        }
        catch (final ImageGeneratorException e) {
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              setErrorHtml(e.getMessage());
            }
          });
        }
      }
    });
  }

  private void updatePreviewImages() {
    if (mySelectedAssetType == null || myImageMap == null) {
      return;
    }

    if (mySelectedAssetType.equals(AssetType.NOTIFICATION)) {
      final BufferedImage v9_mdpi = getImage(myImageMap, V9, Density.MEDIUM);
      final BufferedImage v9_hdpi = getImage(myImageMap, V9, Density.HIGH);
      final BufferedImage v9_xhdpi = getImage(myImageMap, V9, Density.XHIGH);
      final BufferedImage v9_xxhdpi = getImage(myImageMap, V9, Density.XXHIGH);
      setIconOrClear(myV9MdpiPreview, v9_mdpi);
      setIconOrClear(myV9HdpiPreview, v9_hdpi);
      setIconOrClear(myV9XHdpiPreview, v9_xhdpi);
      setIconOrClear(myV9XXHdpiPreview, v9_xxhdpi);

      final BufferedImage v11_mdpi = getImage(myImageMap, V11, Density.MEDIUM);
      final BufferedImage v11_hdpi = getImage(myImageMap, V11, Density.HIGH);
      final BufferedImage v11_xhdpi = getImage(myImageMap, V11, Density.XHIGH);
      final BufferedImage v11_xxhdpi = getImage(myImageMap, V11, Density.XXHIGH);
      setIconOrClear(myV11MdpiPreview, v11_mdpi);
      setIconOrClear(myV11HdpiPreview, v11_hdpi);
      setIconOrClear(myV11XHdpiPreview, v11_xhdpi);
      setIconOrClear(myV11XXHdpiPreview, v11_xxhdpi);

    } else {
      final BufferedImage mdpi = getImage(myImageMap, Density.MEDIUM.getResourceValue());
      final BufferedImage hdpi = getImage(myImageMap, Density.HIGH.getResourceValue());
      final BufferedImage xhdpi = getImage(myImageMap, Density.XHIGH.getResourceValue());
      final BufferedImage xxhdpi = getImage(myImageMap, Density.XXHIGH.getResourceValue());

      setIconOrClear(myMdpiPreview, mdpi);
      setIconOrClear(myHdpiPreview, hdpi);
      setIconOrClear(myXHdpiPreview, xhdpi);
      setIconOrClear(myXXHdpiPreview, xxhdpi);

      if (mySelectedAssetType.equals(AssetType.LAUNCHER)) {
        final BufferedImage xxxhdpi = getImage(myImageMap, Density.XXXHIGH.getResourceValue());

        setIconOrClear(myXXXHdpiPreview, xxxhdpi);
      }
    }

    myUpdateListener.update();
  }

  private static void setIconOrClear(@NotNull ImageComponent component, @Nullable BufferedImage image) {
    if (image == null) {
      component.setIcon(null);
    } else {
      component.setIcon(new ImageIcon(image));
    }
  }

  /**
   * Displays a modal dialog with one button for each entry in the {@link GraphicGenerator} clipart library. Clicking on a button sets that
   * entry into the {@link ATTR_CLIPART_NAME} parameter.
   */
  private void displayClipartDialog() {
    Window window = SwingUtilities.getWindowAncestor(myPanel);
    final JDialog dialog = new JDialog(window, Dialog.ModalityType.DOCUMENT_MODAL);
    FlowLayout layout = new FlowLayout();
    dialog.getRootPane().setLayout(layout);
    int count = 0;
    for (Iterator<String> iter = GraphicGenerator.getClipartNames(); iter.hasNext(); ) {
      final String name = iter.next();
      try {
        JButton btn = new JButton();

        btn.setIcon(new ImageIcon(GraphicGenerator.getClipartIcon(name)));
        Dimension d = new Dimension(CLIPART_ICON_SIZE, CLIPART_ICON_SIZE);
        btn.setMaximumSize(d);
        btn.setPreferredSize(d);
        btn.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            myTemplateState.put(ATTR_CLIPART_NAME, name);
            dialog.setVisible(false);
            update();
          }
          });
        dialog.getRootPane().add(btn);
        count++;
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    int size = (int)(Math.sqrt(count) + 1) * (CLIPART_ICON_SIZE + layout.getHgap()) + CLIPART_DIALOG_BORDER * 2;
    dialog.setSize(size, size + DIALOG_HEADER);
    dialog.setLocationRelativeTo(window);
    dialog.setVisible(true);
  }

  public void finalizeAssetType(AssetType type) {
    mySelectedAssetType = type;
    myTemplateState.put(ATTR_ASSET_TYPE, type.name());
    for (int i = 0; i < myAssetTypeComboBox.getItemCount(); ++i) {
      if (((ComboBoxItem)myAssetTypeComboBox.getItemAt(i)).id.equals(type.name())) {
        myAssetTypeComboBox.setSelectedIndex(i);
        break;
      }
    }
    hide(myAssetTypeComboBox, myAssetTypeLabel);
    update();
  }

  @Nullable
  private static BufferedImage getImage(@NotNull Map<String, Map<String, BufferedImage>> map, @NotNull String name) {
    final Map<String, BufferedImage> images = map.get(name);
    if (images == null) {
      return null;
    }

    final Collection<BufferedImage> values = images.values();
    return values.isEmpty() ? null : values.iterator().next();
  }

  @Nullable
  private static BufferedImage getImage(@NotNull Map<String, Map<String, BufferedImage>> map,
                                        @NotNull String category, @NotNull Density density) {
    String densityString = density.getResourceValue();
    final Map<String, BufferedImage> images = map.get(category);
    if (images == null) {
      return null;
    }

    for (String key : images.keySet()) {
      if (key.contains(densityString)) {
        return images.get(key);
      }
    }
    return null;
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void updateStep() {
    super.updateStep();

    if (!myInitialized) {
      myInitialized = true;
      initialize();
    }
  }

  @Override
  public String getHelpId() {
    return "Android-Gradle_Screen_Configuration_Page";
  }

  private void initialize() {
    myTemplateState.put(ATTR_IMAGE_PATH, new File(TemplateManager.getTemplateRootFolder(), FileUtil
      .join(Template.CATEGORY_PROJECTS, NewProjectWizardState.MODULE_TEMPLATE_NAME, "root", "res", "mipmap-xhdpi", "ic_launcher.png"))
      .getAbsolutePath());
    register(ATTR_IMAGE_PATH, myImageFile);
  }

  @NotNull
  private String computeResourceName() {
    String resourceName = null;
    if (myTemplateState.get(TemplateMetadata.ATTR_ICON_NAME) != null) {
      resourceName = myStringEvaluator.evaluate(myTemplateState.getString(TemplateMetadata.ATTR_ICON_NAME), myTemplateState.getParameters());
    }

    if (resourceName == null) {
      resourceName = String.format(mySelectedAssetType.getDefaultNameFormat(), "name");
    }


    // It's unusual to have > 1 launcher icon, don't fix the name for launcher icons.
    if (drawableExists(resourceName) && mySelectedAssetType != AssetType.LAUNCHER) {
      // While uniqueness isn't satisfied, increment number and add to end
      int i = 2;
      while (drawableExists(resourceName + Integer.toString(i))) {
        i++;
      }
      resourceName += Integer.toString(i);
    }

    return resourceName;
  }

  /**
   * Must be run inside a write action. Creates the asset files on disk.
   */
  public void createAssets(@Nullable Module module) {
    File targetResDir = (File)myTemplateState.get(ChooseOutputResDirStep.ATTR_OUTPUT_FOLDER);
    if (targetResDir == null) {
      if (myTemplateState.hasAttr(TemplateMetadata.ATTR_RES_DIR)) {
        assert module != null;
        File moduleDir = new File(module.getModuleFilePath()).getParentFile();
        targetResDir = new File(moduleDir, myTemplateState.getString(TemplateMetadata.ATTR_RES_DIR));
      } else {
        return;
      }
    }

    File targetVariantDir = targetResDir.getParentFile();

    myAssetGenerator.outputImagesIntoVariantRoot(targetVariantDir);

    VirtualFile resDir = LocalFileSystem.getInstance().findFileByIoFile(targetResDir);
    if (resDir != null) {
      // Refresh the res directory so that the new files show up in the IDE.
      resDir.refresh(true, true);
    } else {
      // If we can't find the res directory, refresh the project.
      if (myProject != null) {
        myProject.getBaseDir().refresh(true, true);
      }
    }
  }

  private boolean drawableExists(String resourceName) {
    if (mySourceProvider != null) {
      return Parameter.existsResourceFile(mySourceProvider, myModule, ResourceFolderType.DRAWABLE, ResourceType.DRAWABLE, resourceName);
    }
    return Parameter.existsResourceFile(myModule, ResourceType.DRAWABLE, resourceName);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myImageRadioButton;
  }

  @NotNull
  @Override
  protected JLabel getDescription() {
    return myDescription;
  }

  @NotNull
  @Override
  protected JLabel getError() {
    return myError;
  }


  @Override
  public void dispose()  {
    myUpdateQueue.cancelAllUpdates();
  }
}
