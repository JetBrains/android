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

package com.android.tools.idea.npw;

import com.android.SdkConstants;
import com.android.assetstudiolib.GraphicGenerator;
import com.android.builder.model.SourceProvider;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.npw.template.GenerateIconsStep;
import com.android.tools.idea.templates.*;
import com.android.tools.idea.ui.ApiComboBoxItem;
import com.android.tools.idea.ui.ImageComponent;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithDescription;
import com.android.tools.idea.wizard.dynamic.RadioButtonGroupBinding;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static com.android.tools.idea.npw.AssetStudioAssetGenerator.*;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.PATH;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Scope.STEP;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.createKey;

/**
 * {@linkplain IconStep} is a wizard page that lets the user create a variety of density-scaled assets.
 *
 * @deprecated Replaced by {@link GenerateIconsStep}
 */
public class IconStep extends DynamicWizardStepWithDescription implements Disposable {
  public static final Key<String> ATTR_ASSET_NAME = createKey(AssetStudioAssetGenerator.ATTR_ASSET_NAME, PATH, String.class);
  public static final Key<String> ATTR_CLIPART_NAME = createKey(AssetStudioAssetGenerator.ATTR_CLIPART_NAME, PATH, String.class);
  public static final Key<URL> ATTR_VECTOR_LIB_ICON_PATH = createKey(AssetStudioAssetGenerator.ATTR_VECTOR_LIB_ICON_PATH, PATH, URL.class);
  public static final Key<String> ATTR_TEXT = createKey(AssetStudioAssetGenerator.ATTR_TEXT, PATH, String.class);
  public static final Key<String> ATTR_FONT = createKey(AssetStudioAssetGenerator.ATTR_FONT, PATH, String.class);
  public static final Key<AssetType> ATTR_ASSET_TYPE = createKey(AssetStudioAssetGenerator.ATTR_ASSET_TYPE, PATH, AssetType.class);
  public static final Key<String> ATTR_ASSET_THEME = createKey(AssetStudioAssetGenerator.ATTR_ASSET_THEME, PATH, String.class);
  public static final Key<Scaling> ATTR_SCALING = createKey(AssetStudioAssetGenerator.ATTR_SCALING, PATH, Scaling.class);
  public static final Key<GraphicGenerator.Shape> ATTR_SHAPE =
    createKey(AssetStudioAssetGenerator.ATTR_SHAPE, PATH, GraphicGenerator.Shape.class);
  public static final Key<SourceType> ATTR_SOURCE_TYPE = createKey(AssetStudioAssetGenerator.ATTR_SOURCE_TYPE, PATH, SourceType.class);
  public static final Key<Boolean> ATTR_TRIM = createKey(AssetStudioAssetGenerator.ATTR_TRIM, PATH, Boolean.class);
  public static final Key<Boolean> ATTR_DOGEAR = createKey(AssetStudioAssetGenerator.ATTR_DOGEAR, PATH, Boolean.class);
  public static final Key<Integer> ATTR_PADDING = createKey(AssetStudioAssetGenerator.ATTR_PADDING, PATH, Integer.class);
  public static final Key<Color> ATTR_FOREGROUND_COLOR = createKey(AssetStudioAssetGenerator.ATTR_FOREGROUND_COLOR, PATH, Color.class);
  public static final Key<Color> ATTR_BACKGROUND_COLOR = createKey(AssetStudioAssetGenerator.ATTR_BACKGROUND_COLOR, PATH, Color.class);
  public static final Key<String> ATTR_IMAGE_PATH = createKey(AssetStudioAssetGenerator.ATTR_IMAGE_PATH, PATH, String.class);
  public static final Key<String> ATTR_ICON_RESOURCE = createKey("icon_resource", PATH, String.class);
  public static final Key<Integer> ATTR_FONT_SIZE = createKey(AssetStudioAssetGenerator.ATTR_FONT_SIZE, PATH, Integer.class);
  public static final Key<File> ATTR_OUTPUT_FOLDER = createKey(CommonAssetSetStep.ATTR_OUTPUT_FOLDER, STEP, File.class);
  public static final Key<String> ATTR_ERROR_LOG = createKey(AssetStudioAssetGenerator.ATTR_ERROR_LOG, PATH, String.class);
  public static final Key<String> ATTR_VECTOR_DRAWBLE_WIDTH =
    createKey(AssetStudioAssetGenerator.ATTR_VECTOR_DRAWBLE_WIDTH, STEP, String.class);
  public static final Key<String> ATTR_VECTOR_DRAWBLE_HEIGHT =
    createKey(AssetStudioAssetGenerator.ATTR_VECTOR_DRAWBLE_HEIGHT, STEP, String.class);
  public static final Key<Integer> ATTR_ORIGINAL_WIDTH = createKey(AssetStudioAssetGenerator.ATTR_ORIGINAL_WIDTH, STEP, Integer.class);
  public static final Key<Integer> ATTR_ORIGINAL_HEIGHT = createKey(AssetStudioAssetGenerator.ATTR_ORIGINAL_HEIGHT, STEP, Integer.class);
  public static final Key<Integer> ATTR_VECTOR_DRAWBLE_OPACTITY =
    createKey(AssetStudioAssetGenerator.ATTR_VECTOR_DRAWBLE_OPACTITY, STEP, Integer.class);
  public static final Key<Boolean> ATTR_VECTOR_DRAWBLE_AUTO_MIRRORED =
    createKey(AssetStudioAssetGenerator.ATTR_VECTOR_DRAWBLE_AUTO_MIRRORED, STEP, Boolean.class);
  public static final Key<Boolean> ATTR_VALID_PREVIEW = createKey(AssetStudioAssetGenerator.ATTR_VALID_PREVIEW, STEP, Boolean.class);

  private static final Logger LOG = Logger.getInstance(IconStep.class);
  private static final int CLIPART_ICON_SIZE = JBUI.scale(32);
  private static final int CLIPART_DIALOG_BORDER = JBUI.scale(10);
  private static final int DIALOG_HEADER = JBUI.scale(20);
  private static final String V11 = "V11";
  private static final String V9 = "V9";
  private static final String IMAGES_CLIPART_BIG = "images/clipart/big/";

  private final StringEvaluator myStringEvaluator = new StringEvaluator();
  private final MergingUpdateQueue myUpdateQueue;
  private final Map<String, Map<String, BufferedImage>> myImageMap = new ConcurrentHashMap<>();
  private final Key<TemplateEntry> myTemplateKey;
  private final Key<SourceProvider> mySourceProviderKey;
  private AssetStudioAssetGenerator myAssetGenerator;
  private JPanel myPanel;
  private JRadioButton myImageRadioButton;
  private JRadioButton myClipartRadioButton;
  private JRadioButton myTextRadioButton;
  private JButton myChooseClipart;
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
  private JLabel myForegroundColorLabel;
  private JTextField myResourceNameField;
  private ImageComponent myV9XHdpiPreview;
  private ImageComponent myV9XXHdpiPreview;
  private ImageComponent myV9MdpiPreview;
  private ImageComponent myV9HdpiPreview;
  private ImageComponent myV11MdpiPreview;
  private ImageComponent myV11HdpiPreview;
  private ImageComponent myV11XHdpiPreview;
  private ImageComponent myV11XXHdpiPreview;
  private JTextField myPaddingTextField;
  private JPanel myPageBook;
  private JLabel mySourceSetLabel;
  private JComboBox mySourceSetComboBox;
  private JPanel myAssetSourceCardPanel;
  private String myDefaultName;

  @SuppressWarnings("UseJBColor") // Colors are used for the graphics generator, not the plugin UI
  public IconStep(Key<TemplateEntry> templateKey, Key<SourceProvider> sourceProviderKey, Disposable disposable) {
    super(disposable);
    myTemplateKey = templateKey;
    mySourceProviderKey = sourceProviderKey;

    myUpdateQueue = new MergingUpdateQueue("asset.studio", 200, true, null, this, null, false);

    myImageFile.addBrowseFolderListener(null, null, null, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    myForegroundColor.setSelectedColor(Color.BLUE);
    myBackgroundColor.setSelectedColor(Color.WHITE);

    for (String font : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
      myFontFamily.addItem(new ApiComboBoxItem(font, font, 1, 1));
      if (font.equals(myState.get(ATTR_FONT))) {
        myFontFamily.setSelectedIndex(myFontFamily.getItemCount() - 1);
      }
    }

    myChooseClipart.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        displayClipartDialog();
      }
    });
    setBodyComponent(myPanel);
  }

  @Nullable
  private static Icon getClipartIcon(@Nullable String clipartName) {
    if (StringUtil.isEmpty(clipartName)) {
      return null;
    }

    try {
      return new ImageIcon(GraphicGenerator.getClipartIcon(clipartName), clipartName);
    }
    catch (IOException e) {
      Logger.getInstance(IconStep.class).error(e);
      return null;
    }
  }

  private static void show(JComponent... components) {
    for (JComponent component : components) {
      component.setVisible(true);
      component.getParent().invalidate();
    }
  }

  private static void hide(JComponent... components) {
    for (JComponent component : components) {
      component.setVisible(false);
      component.getParent().invalidate();
    }
  }

  private static void setIconOrClear(@NotNull ImageComponent component, @Nullable BufferedImage image) {
    if (image == null) {
      component.setIcon(null);
    }
    else {
      component.setIcon(new ImageIcon(image));
    }
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
                                        @NotNull String category,
                                        @NotNull Density density) {
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

  private static String getResourceDirLabel(@Nullable Module module, File directory) {
    if (module == null) {
      return directory.getName();
    }
    String filePath = module.getModuleFilePath();
    String parent = new File(filePath).getParent();
    String path = directory.getPath();
    return path.startsWith(parent) ? path.substring(parent.length() + 1) : directory.getName();
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    String resourceName = computeResourceName();
    myResourceNameField.setEnabled(resourceName == null);
    if (resourceName != null) {
      myState.put(ATTR_ASSET_NAME, resourceName);
    }
  }

  @Override
  public void init() {
    super.init();

    myAssetGenerator = new AssetStudioAssetGenerator(new ScopedStateStoreAdapter(myState));

    myState.put(ATTR_ASSET_TYPE, AssetType.LAUNCHER);

    String relativeTemplatePath =
      FileUtil.join(Template.CATEGORY_PROJECTS, WizardConstants.MODULE_TEMPLATE_NAME, "root", "res", "mipmap-xhdpi", "ic_launcher.png");
    myState.put(ATTR_IMAGE_PATH, new File(TemplateManager.getTemplateRootFolder(), relativeTemplatePath).getAbsolutePath());

    register(ATTR_OUTPUT_FOLDER, mySourceSetComboBox);
    register(ATTR_IMAGE_PATH, myImageFile);
    register(ATTR_TEXT, myText);

    register(ATTR_PADDING, myPaddingSlider);
    register(ATTR_PADDING, myPaddingTextField, new ComponentBinding<Integer, JTextField>() {
      @Override
      public void setValue(@Nullable Integer newValue, @NotNull JTextField component) {
        component.setText(newValue == null ? "" : String.valueOf(newValue));
      }

      @Nullable
      @Override
      public Integer getValue(@NotNull JTextField component) {
        try {
          // Shoehorn user input into acceptable bounds. There's slider and preview
          // so the user already receives enough feedback.
          return Math.max(0, Math.min(Integer.parseInt(component.getText()), 100));
        }
        catch (NumberFormatException e) {
          return 0;
        }
      }

      @Override
      public void addActionListener(@NotNull ActionListener listener, @NotNull JTextField component) {
        component.addActionListener(listener);
      }

      @Nullable
      @Override
      public Document getDocument(@NotNull JTextField component) {
        return component.getDocument();
      }
    });
    register(ATTR_TRIM, myTrimBlankSpace);
    register(ATTR_FONT, myFontFamily);
    register(ATTR_SOURCE_TYPE, ImmutableMap
      .of(myImageRadioButton, SourceType.IMAGE, myClipartRadioButton, SourceType.CLIPART, myTextRadioButton, SourceType.TEXT));
    register(ATTR_FOREGROUND_COLOR, myForegroundColor);
    register(ATTR_BACKGROUND_COLOR, myBackgroundColor);
    register(ATTR_ASSET_NAME, myResourceNameField);
    register(ATTR_CLIPART_NAME, myChooseClipart, new ComponentBinding<String, JButton>() {
      @Override
      public void setValue(@Nullable String newValue, @NotNull JButton component) {
        component.setIcon(getClipartIcon(newValue));
        component.setText(newValue);
      }
    });

  }

  private void updateDirectoryCombo() {
    final boolean showLabelAndCombo;

    List<File> folders = getResourceFolders();
    File res = myState.get(ATTR_OUTPUT_FOLDER);
    if (!folders.isEmpty()) {
      if (res == null || !folders.contains(res)) {
        res = folders.get(0);
        myState.put(ATTR_OUTPUT_FOLDER, res);
      }
      showLabelAndCombo = folders.size() > 1;
      mySourceSetComboBox.removeAllItems();
      if (showLabelAndCombo) {
        ApiComboBoxItem selected = null;
        for (File directory : folders) {
          ApiComboBoxItem item = new ApiComboBoxItem(directory, getResourceDirLabel(getModule(), directory), 0, 0);
          if (Objects.equal(directory, res)) {
            selected = item;
          }
          mySourceSetComboBox.addItem(item);
        }
        mySourceSetComboBox.setSelectedItem(selected);
      }
    }
    else {
      showLabelAndCombo = false;
    }
    mySourceSetComboBox.setVisible(showLabelAndCombo);
    mySourceSetLabel.setVisible(showLabelAndCombo);
  }

  private List<File> getResourceFolders() {
    SourceProvider provider = myState.get(mySourceProviderKey);
    if (provider == null) {
      return Collections.emptyList();
    }
    List<File> dirs = Lists.newLinkedList();
    dirs.addAll(provider.getResDirectories());
    return dirs;
  }

  private <E> void register(Key<E> key, Map<JRadioButton, E> buttonsToValues) {
    RadioButtonGroupBinding<E> binding = new RadioButtonGroupBinding<>(buttonsToValues);
    for (JRadioButton button : buttonsToValues.keySet()) {
      register(key, button, binding);
    }
  }

  @Override
  public boolean isStepVisible() {
    TemplateEntry templateEntry = myState.get(myTemplateKey);
    boolean isVisible = false;
    if (templateEntry != null) {
      TemplateMetadata templateMetadata = templateEntry.getMetadata();
      if (templateMetadata.getIconType() != null) {
        isVisible = true;
      }
    }
    return isVisible;
  }

  @Override
  public void deriveValues(Set<Key> modified) {
    super.deriveValues(modified);

    AssetType iconType = null;
    TemplateEntry templateEntry = myState.get(myTemplateKey);
    if (templateEntry != null && templateEntry.getMetadata().getIconType() != null) {
      iconType = AssetType.of(templateEntry.getMetadata().getIconType());
    }
    finalizeAssetType(iconType);

    // Note that this combo may need to reflect source set from another wizard page
    updateDirectoryCombo();

    myState.put(ATTR_ICON_RESOURCE, myState.get(ATTR_ASSET_NAME));

    SourceType sourceType = myState.get(ATTR_SOURCE_TYPE);
    if (sourceType != null) {
      switch (sourceType) {
        case IMAGE:
          ((CardLayout)myAssetSourceCardPanel.getLayout()).show(myAssetSourceCardPanel, "ImageCard");
          hide(myForegroundColor, myForegroundColorLabel);
          break;
        case CLIPART:
          ((CardLayout)myAssetSourceCardPanel.getLayout()).show(myAssetSourceCardPanel, "ClipartCard");
          show(myForegroundColor, myForegroundColorLabel);
          break;
        case TEXT:
          ((CardLayout)myAssetSourceCardPanel.getLayout()).show(myAssetSourceCardPanel, "TextCard");
          show(myForegroundColor, myForegroundColorLabel);
          myFontFamily.setSelectedItem(myState.get(ATTR_FONT));
          break;
        default:
          // TODO Do we need to handle SVG and VECTORDRAWABLE?
          break;
      }
    }

    // Asset Type Combo Box
    // TODO: This doesn't look like it's used anywhere. Confirm...? (only used from Notification template?)
    AssetType assetType = myState.get(ATTR_ASSET_TYPE);
    if (assetType != null) {
      String name = myState.get(ATTR_ASSET_NAME);
      if (name == null || Objects.equal(myDefaultName, name)) {
        myDefaultName = computeResourceName(assetType);
        myState.put(ATTR_ASSET_NAME, myDefaultName);
      }
    }

    requestPreviewUpdate();
  }

  @Override
  public boolean validate() {
    if (!super.validate()) {
      return false;
    }
    String assetName = myState.get(ATTR_ASSET_NAME);
    boolean canProceed = true;
    String error = null;
    if (StringUtil.isEmpty(assetName)) {
      canProceed = false;
      error = "Missing resource name";
    }
    else if (drawableExists(assetName)) {
      error = String.format("A drawable resource named %s already exists and will be overwritten.", assetName);
    }
    setErrorHtml(error);

    return canProceed;
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
          if (myAssetGenerator == null) { // Init not done yet
            return;
          }
          myAssetGenerator.generateImages(myImageMap, true, true);

          ApplicationManager.getApplication().invokeLater(IconStep.this::updatePreviewImages);
        }
        catch (final ImageGeneratorException exception) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              setErrorHtml(exception.getMessage());
            }
          });
        }
      }
    });
  }

  private void updatePreviewImages() {
    AssetType assetType = myState.get(ATTR_ASSET_TYPE);
    if (assetType == null || myImageMap.isEmpty()) {
      return;
    }

    if (assetType.equals(AssetType.NOTIFICATION)) {
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

    }
    else {
      final BufferedImage mdpi = getImage(myImageMap, Density.MEDIUM.getResourceValue());
      final BufferedImage hdpi = getImage(myImageMap, Density.HIGH.getResourceValue());
      final BufferedImage xhdpi = getImage(myImageMap, Density.XHIGH.getResourceValue());
      final BufferedImage xxhdpi = getImage(myImageMap, Density.XXHIGH.getResourceValue());

      setIconOrClear(myMdpiPreview, mdpi);
      setIconOrClear(myHdpiPreview, hdpi);
      setIconOrClear(myXHdpiPreview, xhdpi);
      setIconOrClear(myXXHdpiPreview, xxhdpi);
    }
    ((CardLayout)myPageBook.getLayout()).show(myPageBook, assetType == AssetType.NOTIFICATION ? "versions" : "DPI");
  }

  /**
   * Displays a modal dialog with one button for each entry in the {@link GraphicGenerator}
   * clipart library. Clicking on a button sets that entry into the {@link #ATTR_CLIPART_NAME} key.
   */
  private void displayClipartDialog() {
    Window window = SwingUtilities.getWindowAncestor(myPanel);
    final JDialog dialog = new JDialog(window, Dialog.ModalityType.DOCUMENT_MODAL);
    FlowLayout layout = new FlowLayout();
    dialog.getRootPane().setLayout(layout);
    int count = 0;
    for (Iterator<String> iter = GraphicGenerator.getResourcesNames(IMAGES_CLIPART_BIG, SdkConstants.DOT_PNG);
         iter.hasNext(); ) {
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
            myState.put(ATTR_CLIPART_NAME, name);
            dialog.setVisible(false);
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

  public void finalizeAssetType(@Nullable AssetType type) {
    myState.put(ATTR_ASSET_TYPE, type);
  }

  @NotNull
  private String computeResourceName(AssetType assetType) {
    String resourceName = computeResourceName();
    if (resourceName == null) {
      resourceName = String.format(assetType.getDefaultNameFormat(), "name");
    }

    // It's unusual to have > 1 launcher icon, don't fix the name for launcher icons.
    if (drawableExists(resourceName) && assetType != AssetType.LAUNCHER) {
      // While uniqueness isn't satisfied, increment number and add to end
      int i = 2;
      while (drawableExists(resourceName + i)) {
        i++;
      }
      resourceName += i;
    }

    return resourceName;
  }

  @Nullable
  private String computeResourceName() {
    String resourceName = null;
    TemplateEntry templateEntry = myState.get(myTemplateKey);
    String nameExpression;
    if (templateEntry != null) {
      nameExpression = templateEntry.getMetadata().getIconName();

      if (!StringUtil.isEmpty(nameExpression)) {
        Set<Key> allKeys = myState.getAllKeys();
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(allKeys.size());
        for (Key key : allKeys) {
          parameters.put(key.name, myState.get(key));
        }
        resourceName = myStringEvaluator.evaluate(nameExpression, parameters);
      }
    }
    return resourceName;
  }

  /**
   * Must be run inside a write action. Creates the asset files on disk.
   */
  public void createAssets() {
    if (isStepVisible()) {
      File destination = myState.get(ATTR_OUTPUT_FOLDER);
      assert destination != null;
      // Asset generator will append "res" by itself
      myAssetGenerator.outputImagesIntoVariantRoot(destination.getParentFile());
    }
  }

  private boolean drawableExists(String resourceName) {
    File resDir = myState.get(ATTR_OUTPUT_FOLDER);
    if (resDir != null) {
      return Parameter.existsResourceFile(resDir, ResourceFolderType.DRAWABLE, resourceName);
    }
    else {
      return Parameter.existsResourceFile(getModule(), ResourceType.DRAWABLE, resourceName);
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myImageRadioButton;
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Asset Studio";
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    return "Asset Studio";
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    return null;
  }

  @Override
  public void dispose() {
    myUpdateQueue.cancelAllUpdates();
  }

}
