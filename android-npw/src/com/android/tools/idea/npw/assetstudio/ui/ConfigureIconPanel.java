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
package com.android.tools.idea.npw.assetstudio.ui;

import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.toLowerCamelCase;

import com.android.tools.idea.npw.assetstudio.ActionBarIconGenerator;
import com.android.tools.idea.npw.assetstudio.IconGenerator;
import com.android.tools.idea.npw.assetstudio.IconGenerator.Shape;
import com.android.tools.idea.npw.assetstudio.LauncherLegacyIconGenerator;
import com.android.tools.idea.npw.assetstudio.NotificationIconGenerator;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.npw.assetstudio.icon.AndroidIconType;
import com.android.tools.idea.npw.assetstudio.wizard.PersistentState;
import com.android.tools.idea.npw.assetstudio.wizard.PersistentStateUtil;
import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.core.BoolProperty;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.IntProperty;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.expressions.bool.BooleanExpression;
import com.android.tools.idea.observable.expressions.optional.AsOptionalExpression;
import com.android.tools.idea.observable.expressions.string.FormatExpression;
import com.android.tools.idea.observable.ui.ColorProperty;
import com.android.tools.idea.observable.ui.EnabledProperty;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.SelectedRadioButtonProperty;
import com.android.tools.idea.observable.ui.SliderValueProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.observable.ui.VisibleProperty;
import com.android.tools.idea.rendering.DrawableRenderer;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A panel which allows the configuration of an icon, by specifying the source asset used to
 * generate the icon plus some other options. Note that this panel provides a superset of all
 * options used by each {@link AndroidIconType}, but the relevant options are shown / hidden based
 * on the exact type passed into the constructor.
 */
public final class ConfigureIconPanel extends JPanel implements Disposable, ConfigureIconView {
  private static final AssetType DEFAULT_ASSET_TYPE = AssetType.CLIP_ART;
  // @formatter:off
  private static final Map<Shape, String> SHAPE_NAMES = ImmutableMap.of(
    Shape.NONE, "None",
    Shape.SOLID, "Solid Color",
    Shape.CIRCLE, "Circle",
    Shape.SQUARE, "Square",
    Shape.VRECT, "Vertical",
    Shape.HRECT, "Horizontal");
  // @formatter:on

  private static final String OUTPUT_NAME_PROPERTY = "outputName";
  private static final String ASSET_TYPE_PROPERTY = "assetType";
  private static final String IMAGE_ASSET_PROPERTY = "imageAsset";
  private static final String CLIPART_ASSET_PROPERTY = "clipartAsset";
  private static final String TEXT_ASSET_PROPERTY = "textAsset";
  private static final String BACKGROUND_COLOR_PROPERTY = "backgroundColor";
  private static final String ICON_SHAPE_PROPERTY = "iconShape";
  private static final String CROPPED_PROPERTY = "cropped";
  private static final String DOG_EARED_PROPERTY = "dogEared";
  private static final String THEME_PROPERTY = "theme";
  private static final String THEME_COLOR_PROPERTY = "themeColor";

  /**
   * This panel presents a list of radio buttons (clipart, image, text), and whichever one is
   * selected sets the active asset.
   */
  private JPanel myRootPanel;
  private JRadioButton myClipartRadioButton;
  private JRadioButton myTextRadioButton;
  private JRadioButton myImageRadioButton;
  private JPanel myAllOptionsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel mySourceAssetTypePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myIconOptionsPanel;
  private JRadioButton myTrimmedRadioButton;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JRadioButton myNotTrimmedRadioButton;
  private JPanel myTrimOptionsPanel;
  private JSlider myPaddingSlider;
  private JLabel myPaddingValueLabel;
  private JPanel myAssetRadioButtonsPanel;
  private JPanel myPaddingSliderPanel;
  private JTextField myOutputNameTextField;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myOutputNamePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myTrimRowPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myNameRowPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myPaddingRowPanel;
  private JPanel myForegroundRowPanel;
  private ColorPanel myForegroundColorPanel;
  private JPanel myBackgroundRowPanel;
  private ColorPanel myBackgroundColorPanel;
  private JPanel myScalingRowPanel;
  private JPanel myShapeRowPanel;
  private JPanel myScalingRadioButtonsPanel;
  private JRadioButton myCropRadioButton;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JRadioButton myShrinkToFitRadioButton;
  private JPanel myEffectRadioButtonsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JRadioButton myNoEffectRadioButton;
  private JRadioButton myDogEarRadioButton;
  private JPanel myThemeRowPanel;
  private JComboBox<ActionBarIconGenerator.Theme> myThemeComboBox;
  private JPanel myEffectRowPanel;
  private JBScrollPane myScrollPane;
  private JComboBox<Shape> myShapeComboBox;
  private JPanel myCustomThemeRowPanel;
  private ColorPanel myCustomThemeColorPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myAssetPanels;
  private JPanel myImageAssetRowPanel;
  private JPanel myClipartAssetRowPanel;
  private JPanel myTextAssetRowPanel;
  private ImageAssetBrowser myImageAssetBrowser;
  private ClipartIconButton myClipartAssetButton;
  private MultiLineTextAssetEditor myTextAssetEditor;
  private JBLabel myOutputNameLabel;
  private JLabel myAssetTypeLabel;
  private JBLabel myImagePathLabel;
  private JBLabel myClipartLabel;
  private JBLabel myTextLabel;
  private JBLabel myTrimLabel;
  private JBLabel myPaddingLabel;
  private JBLabel myForegroundLabel;
  private JBLabel myThemeLabel;
  private JBLabel myCustomColorLabel;
  private JBLabel myBackgroundLabel;
  private JBLabel myScalingLabel;
  private JBLabel myShapeLabel;
  private JBLabel myEffectLabel;

  @NotNull private final List<ActionListener> myAssetListeners = new ArrayList<>(1);

  @NotNull private final AndroidIconType myIconType;
  @NotNull private final IconGenerator myIconGenerator;
  @NotNull private final String myDefaultOutputName;

  @NotNull private final BindingsManager myGeneralBindings = new BindingsManager();
  @NotNull private final BindingsManager myActiveAssetBindings = new BindingsManager();
  @NotNull private final ListenerManager myListeners = new ListenerManager();

  @NotNull private final ImmutableMap<AssetType, AssetComponent<?>> myAssetPanelMap;

  @NotNull private final ObjectProperty<BaseAsset> myActiveAsset;
  @NotNull private final StringProperty myOutputName;
  @NotNull private final AbstractProperty<AssetType> myAssetType;
  private ColorProperty myForegroundColor;
  private AbstractProperty<Color> myBackgroundColor;
  private AbstractProperty<Shape> myShape;
  private BoolProperty myCropped;
  private BoolProperty myDogEared;
  private AbstractProperty<ActionBarIconGenerator.Theme> myTheme;
  private AbstractProperty<Color> myThemeColor;

  /**
   * Initializes a panel which can generate few kinds of Android icons.
   */
  public ConfigureIconPanel(@NotNull Disposable disposableParent, @NotNull AndroidFacet facet,
                            @NotNull AndroidIconType iconType, int minSdkVersion, @Nullable DrawableRenderer renderer) {
    super(new BorderLayout());
    setupUI();

    myIconType = iconType;
    myDefaultOutputName = myIconType.toOutputName("name");
    myIconGenerator = createIconGenerator(facet.getModule().getProject(), iconType, minSdkVersion, renderer);

    myTextAssetEditor.getAsset().setDefaultText("Aa");

    DefaultComboBoxModel<ActionBarIconGenerator.Theme> themesModel = new DefaultComboBoxModel<>(ActionBarIconGenerator.Theme.values());
    myThemeComboBox.setModel(themesModel);

    DefaultComboBoxModel<Shape> shapesModel = new DefaultComboBoxModel<>();
    for (Shape shape : SHAPE_NAMES.keySet()) {
      shapesModel.addElement(shape);
    }
    myShapeComboBox.setRenderer(SimpleListCellRenderer.create("", SHAPE_NAMES::get));
    myShapeComboBox.setModel(shapesModel);
    myShapeComboBox.setSelectedItem(Shape.SQUARE);

    myScrollPane.getVerticalScrollBar().setUnitIncrement(10);
    myScrollPane.setBorder(JBUI.Borders.empty());

    myOutputName = new TextProperty(myOutputNameTextField);

    myAssetPanelMap = ImmutableMap.of(
      AssetType.IMAGE, myImageAssetBrowser,
      AssetType.CLIP_ART, myClipartAssetButton,
      AssetType.TEXT, myTextAssetEditor);

    // Call "setLabelFor" in code instead of designer since designer is so inconsistent about
    // valid targets.
    myOutputNameLabel.setLabelFor(myOutputNameTextField);
    myAssetTypeLabel.setLabelFor(myAssetRadioButtonsPanel);
    myImagePathLabel.setLabelFor(myImageAssetBrowser);
    myClipartLabel.setLabelFor(myClipartAssetButton);
    myTextLabel.setLabelFor(myTextAssetEditor);
    myTrimLabel.setLabelFor(myTrimOptionsPanel);
    myPaddingLabel.setLabelFor(myPaddingSliderPanel);
    myForegroundLabel.setLabelFor(myForegroundColorPanel);
    myThemeLabel.setLabelFor(myThemeComboBox);
    myCustomColorLabel.setLabelFor(myCustomThemeColorPanel);
    myBackgroundLabel.setLabelFor(myBackgroundColorPanel);
    myScalingLabel.setLabelFor(myScalingRadioButtonsPanel);
    myShapeLabel.setLabelFor(myShapeComboBox);
    myEffectLabel.setLabelFor(myEffectRadioButtonsPanel);

    // Default the active asset type to "clipart", it's the most visually appealing and easy to
    // play around with.
    ImageAsset clipartAsset = myClipartAssetButton.getAsset();
    myActiveAsset = new ObjectValueProperty<>(clipartAsset);
    myAssetType = new SelectedRadioButtonProperty<>(DEFAULT_ASSET_TYPE, AssetType.values(),
                                                    myImageRadioButton, myClipartRadioButton, myTextRadioButton);

    initializeListenersAndBindings();

    Disposer.register(disposableParent, this);
    for (AssetComponent<?> assetComponent : myAssetPanelMap.values()) {
      Disposer.register(this, assetComponent);
    }
    Disposer.register(this, myIconGenerator);
    add(myRootPanel);
  }

  @Override
  @NotNull
  public PersistentState getState() {
    PersistentState state = new PersistentState();
    for (Map.Entry<AssetType, AssetComponent<?>> entry : myAssetPanelMap.entrySet()) {
      state.setChild(toLowerCamelCase(entry.getKey()), entry.getValue().getAsset().getState());
    }
    state.set(OUTPUT_NAME_PROPERTY, myOutputName.get(), myDefaultOutputName);
    state.set(ASSET_TYPE_PROPERTY, myAssetType.get(), DEFAULT_ASSET_TYPE);
    File file = myImageAssetBrowser.getAsset().imagePath().getValueOrNull();
    state.set(IMAGE_ASSET_PROPERTY, file == null ? null : file.getPath());
    state.setChild(CLIPART_ASSET_PROPERTY, myClipartAssetButton.getState());
    state.setChild(TEXT_ASSET_PROPERTY, myTextAssetEditor.getAsset().getState());
    switch (myIconType) {
      case LAUNCHER_LEGACY:
        // Notice that the foreground colors that are owned by the asset components have already been stored.
        state.set(BACKGROUND_COLOR_PROPERTY, myBackgroundColor.get(), LauncherLegacyIconGenerator.DEFAULT_BACKGROUND_COLOR);
        state.set(ICON_SHAPE_PROPERTY, myShape.get(), LauncherLegacyIconGenerator.DEFAULT_ICON_SHAPE);
        state.set(CROPPED_PROPERTY, myCropped.get(), false);
        state.set(DOG_EARED_PROPERTY, myDogEared.get(), false);
        break;

      case ACTIONBAR:
        state.set(THEME_PROPERTY, myTheme.get(), ActionBarIconGenerator.DEFAULT_THEME);
        state.set(THEME_COLOR_PROPERTY, myThemeColor.get(), ActionBarIconGenerator.DEFAULT_CUSTOM_COLOR);
        break;

      default:
        break; // No special properties.
    }
    return state;
  }

  @Override
  public void loadState(@NotNull PersistentState state) {
    for (Map.Entry<AssetType, AssetComponent<?>> entry : myAssetPanelMap.entrySet()) {
      PersistentStateUtil.load(entry.getValue().getAsset(), state.getChild(toLowerCamelCase(entry.getKey())));
    }
    myOutputName.set(state.get(OUTPUT_NAME_PROPERTY, myDefaultOutputName));
    myAssetType.set(state.get(ASSET_TYPE_PROPERTY, DEFAULT_ASSET_TYPE));
    String path = state.get(IMAGE_ASSET_PROPERTY);
    myImageAssetBrowser.getAsset().imagePath().setNullableValue(path == null ? null : new File(path));
    PersistentStateUtil.load(myClipartAssetButton, state.getChild(CLIPART_ASSET_PROPERTY));
    PersistentStateUtil.load(myTextAssetEditor.getAsset(), state.getChild(TEXT_ASSET_PROPERTY));
    switch (myIconType) {
      case LAUNCHER_LEGACY:
        // Notice that the foreground colors that are owned by the asset components have already been loaded.
        myBackgroundColor.set(state.get(BACKGROUND_COLOR_PROPERTY, LauncherLegacyIconGenerator.DEFAULT_BACKGROUND_COLOR));
        myShape.set(state.get(ICON_SHAPE_PROPERTY, LauncherLegacyIconGenerator.DEFAULT_ICON_SHAPE));
        myCropped.set(state.get(CROPPED_PROPERTY, false));
        myDogEared.set(state.get(DOG_EARED_PROPERTY, false));
        break;

      case ACTIONBAR:
        myTheme.set(state.get(THEME_PROPERTY, ActionBarIconGenerator.DEFAULT_THEME));
        myThemeColor.set(state.get(THEME_COLOR_PROPERTY, ActionBarIconGenerator.DEFAULT_CUSTOM_COLOR));
        break;

      default:
        break; // No special properties.
    }
  }

  private void setupUI() {
    myRootPanel = new JPanel();
    myRootPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    myScrollPane = new JBScrollPane();
    myScrollPane.setHorizontalScrollBarPolicy(31);
    myRootPanel.add(myScrollPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null,
                                                      null, null, 0, false));
    myAllOptionsPanel = new JPanel();
    myAllOptionsPanel.setLayout(new GridLayoutManager(5, 2, new Insets(0, 0, 0, 0), -1, -1));
    myScrollPane.setViewportView(myAllOptionsPanel);
    myAllOptionsPanel.setBorder(
      BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(0, 0, 0, 20), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                       TitledBorder.DEFAULT_POSITION, null, null));
    mySourceAssetTypePanel = new JPanel();
    mySourceAssetTypePanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myAllOptionsPanel.add(mySourceAssetTypePanel, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                      GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                      GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
    myAssetTypeLabel = new JLabel();
    myAssetTypeLabel.setText("Asset type:");
    mySourceAssetTypePanel.add(myAssetTypeLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                                     null, null, null, 0, false));
    myAssetRadioButtonsPanel = new JPanel();
    myAssetRadioButtonsPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    mySourceAssetTypePanel.add(myAssetRadioButtonsPanel,
                               new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_VERTICAL,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                   null, 0, false));
    myClipartRadioButton = new JRadioButton();
    myClipartRadioButton.setText("Clip art");
    myClipartRadioButton.setToolTipText("Select from a list of clipart choices to generate Android icons for your app.");
    myAssetRadioButtonsPanel.add(myClipartRadioButton,
                                 new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myTextRadioButton = new JRadioButton();
    myTextRadioButton.setText("Text");
    myTextRadioButton.setToolTipText("Enter text which will be rendered into Android icons for your app.");
    myAssetRadioButtonsPanel.add(myTextRadioButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                        GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myImageRadioButton = new JRadioButton();
    myImageRadioButton.setText("Image");
    myImageRadioButton.setToolTipText(
      "Select an image, e.g. PNG, SVG, PSD, or a drawable from disk to generate Android icons for your app.");
    myAssetRadioButtonsPanel.add(myImageRadioButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                         GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myIconOptionsPanel = new JPanel();
    myIconOptionsPanel.setLayout(new GridLayoutManager(10, 2, new Insets(0, 0, 0, 0), -1, -1));
    myAllOptionsPanel.add(myIconOptionsPanel, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
    myTrimRowPanel = new JPanel();
    myTrimRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myTrimRowPanel.setVisible(true);
    myIconOptionsPanel.add(myTrimRowPanel, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               null, null, null, 0, true));
    myTrimLabel = new JBLabel();
    myTrimLabel.setText("Trim:");
    myTrimRowPanel.add(myTrimLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                        null, 0, false));
    myTrimOptionsPanel = new JPanel();
    myTrimOptionsPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    myTrimRowPanel.add(myTrimOptionsPanel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myTrimOptionsPanel.add(spacer1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    myTrimmedRadioButton = new JRadioButton();
    myTrimmedRadioButton.setText("Yes");
    myTrimmedRadioButton.setToolTipText("Remove any transparent space from around your source asset before rendering to icon.");
    myTrimOptionsPanel.add(myTrimmedRadioButton, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                     GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                     null, null, null, 0, false));
    myNotTrimmedRadioButton = new JRadioButton();
    myNotTrimmedRadioButton.setSelected(true);
    myNotTrimmedRadioButton.setText("No");
    myNotTrimmedRadioButton.setToolTipText("Leave the original asset unmodified.");
    myTrimOptionsPanel.add(myNotTrimmedRadioButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                        GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myPaddingRowPanel = new JPanel();
    myPaddingRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myPaddingRowPanel.setVisible(true);
    myIconOptionsPanel.add(myPaddingRowPanel, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
    myPaddingLabel = new JBLabel();
    myPaddingLabel.setText("Padding:");
    myPaddingRowPanel.add(myPaddingLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              null, null, 0, false));
    myPaddingSliderPanel = new JPanel();
    myPaddingSliderPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    myPaddingRowPanel.add(myPaddingSliderPanel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                    GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                    GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myPaddingSlider = new JSlider();
    myPaddingSlider.setMaximum(50);
    myPaddingSlider.setMinimum(-10);
    myPaddingSlider.setMinorTickSpacing(5);
    myPaddingSlider.setPaintLabels(false);
    myPaddingSlider.setPaintTicks(true);
    myPaddingSlider.setSnapToTicks(true);
    myPaddingSlider.setToolTipText(
      "Add a percentage of padding around the original asset before rendering. This happens after any trimming.");
    myPaddingSlider.setValue(0);
    myPaddingSliderPanel.add(myPaddingSlider,
                             new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                 false));
    myPaddingValueLabel = new JLabel();
    myPaddingValueLabel.setHorizontalAlignment(4);
    myPaddingValueLabel.setText("100 %");
    myPaddingSliderPanel.add(myPaddingValueLabel,
                             new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                 new Dimension(40, -1), null, 0, false));
    final Spacer spacer2 = new Spacer();
    myPaddingSliderPanel.add(spacer2, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    myForegroundRowPanel = new JPanel();
    myForegroundRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myIconOptionsPanel.add(myForegroundRowPanel, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                     GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                     GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
    myForegroundLabel = new JBLabel();
    myForegroundLabel.setText("Foreground:");
    myForegroundRowPanel.add(myForegroundLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                    GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                                    null, null, null, 0, false));
    myForegroundColorPanel = new ColorPanel();
    myForegroundColorPanel.setSelectedColor(new Color(-16777216));
    myForegroundRowPanel.add(myForegroundColorPanel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                         GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                         GridConstraints.SIZEPOLICY_CAN_GROW, new Dimension(78, -1), null,
                                                                         null, 0, false));
    myBackgroundRowPanel = new JPanel();
    myBackgroundRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myIconOptionsPanel.add(myBackgroundRowPanel, new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                     GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                     GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
    myBackgroundLabel = new JBLabel();
    myBackgroundLabel.setText("Background:");
    myBackgroundRowPanel.add(myBackgroundLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                    GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                                    null, null, null, 0, false));
    myBackgroundColorPanel = new ColorPanel();
    myBackgroundColorPanel.setSelectedColor(new Color(-1));
    myBackgroundRowPanel.add(myBackgroundColorPanel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                         GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                         GridConstraints.SIZEPOLICY_CAN_GROW, new Dimension(78, -1), null,
                                                                         null, 0, false));
    myScalingRowPanel = new JPanel();
    myScalingRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myIconOptionsPanel.add(myScalingRowPanel, new GridConstraints(6, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
    myScalingLabel = new JBLabel();
    myScalingLabel.setText("Scaling:");
    myScalingRowPanel.add(myScalingLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              null, null, 0, false));
    myScalingRadioButtonsPanel = new JPanel();
    myScalingRadioButtonsPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    myScalingRowPanel.add(myScalingRadioButtonsPanel,
                          new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                              0, false));
    myCropRadioButton = new JRadioButton();
    myCropRadioButton.setText("Crop");
    myCropRadioButton.setToolTipText("Crop source asset to fit icon size.");
    myScalingRadioButtonsPanel.add(myCropRadioButton,
                                   new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myShrinkToFitRadioButton = new JRadioButton();
    myShrinkToFitRadioButton.setSelected(true);
    myShrinkToFitRadioButton.setText("Shrink to fit");
    myShrinkToFitRadioButton.setToolTipText("Shrink the source asset to fit icon size.");
    myScalingRadioButtonsPanel.add(myShrinkToFitRadioButton,
                                   new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer3 = new Spacer();
    myScalingRadioButtonsPanel.add(spacer3, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                                GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    myShapeRowPanel = new JPanel();
    myShapeRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myIconOptionsPanel.add(myShapeRowPanel, new GridConstraints(7, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                null, null, null, 0, true));
    myShapeLabel = new JBLabel();
    myShapeLabel.setText("Shape:");
    myShapeRowPanel.add(myShapeLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                          GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                          null, 0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myShapeRowPanel.add(panel1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                    null, 0, false));
    myShapeComboBox = new JComboBox();
    myShapeComboBox.setToolTipText("The shape of the launcher icon's backdrop.");
    panel1.add(myShapeComboBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                    GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                    0, false));
    final Spacer spacer4 = new Spacer();
    panel1.add(spacer4, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                            GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    myEffectRowPanel = new JPanel();
    myEffectRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myIconOptionsPanel.add(myEffectRowPanel, new GridConstraints(8, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
    myEffectLabel = new JBLabel();
    myEffectLabel.setText("Effect:");
    myEffectRowPanel.add(myEffectLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                            null, 0, false));
    myEffectRadioButtonsPanel = new JPanel();
    myEffectRadioButtonsPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    myEffectRowPanel.add(myEffectRadioButtonsPanel,
                         new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                             0, false));
    myNoEffectRadioButton = new JRadioButton();
    myNoEffectRadioButton.setSelected(true);
    myNoEffectRadioButton.setText("None");
    myNoEffectRadioButton.setToolTipText("Do not apply any transformative effects to the icon.");
    myEffectRadioButtonsPanel.add(myNoEffectRadioButton,
                                  new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myDogEarRadioButton = new JRadioButton();
    myDogEarRadioButton.setText("DogEar");
    myDogEarRadioButton.setToolTipText("Add a fold to the top right of the icon's backdrop shape (if supported).");
    myEffectRadioButtonsPanel.add(myDogEarRadioButton,
                                  new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer5 = new Spacer();
    myEffectRadioButtonsPanel.add(spacer5, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                               GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    myThemeRowPanel = new JPanel();
    myThemeRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myIconOptionsPanel.add(myThemeRowPanel, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                null, null, null, 0, true));
    myThemeLabel = new JBLabel();
    myThemeLabel.setText("Theme:");
    myThemeRowPanel.add(myThemeLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                          GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                          null, 0, false));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myThemeRowPanel.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                    null, 0, false));
    myThemeComboBox = new JComboBox();
    panel2.add(myThemeComboBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer6 = new Spacer();
    panel2.add(spacer6, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                            GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    myCustomThemeRowPanel = new JPanel();
    myCustomThemeRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myIconOptionsPanel.add(myCustomThemeRowPanel, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                      GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                      GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
    myCustomColorLabel = new JBLabel();
    myCustomColorLabel.setText("Custom color:");
    myCustomThemeRowPanel.add(myCustomColorLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                                      null, null, null, 0, false));
    myCustomThemeColorPanel = new ColorPanel();
    myCustomThemeColorPanel.setSelectedColor(new Color(-1));
    myCustomThemeRowPanel.add(myCustomThemeColorPanel,
                              new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  new Dimension(78, -1), null, null, 0, false));
    final Spacer spacer7 = new Spacer();
    myAllOptionsPanel.add(spacer7, new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                       GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myAssetPanels = new JPanel();
    myAssetPanels.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
    myAllOptionsPanel.add(myAssetPanels, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             null, null, null, 0, true));
    myImageAssetRowPanel = new JPanel();
    myImageAssetRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myAssetPanels.add(myImageAssetRowPanel, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                null, null, null, 0, true));
    myImagePathLabel = new JBLabel();
    myImagePathLabel.setText("Path:");
    myImageAssetRowPanel.add(myImagePathLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                   GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                                   null, null, 0, false));
    myImageAssetBrowser = new ImageAssetBrowser();
    myImageAssetRowPanel.add(myImageAssetBrowser,
                             new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                 null, 0, false));
    myClipartAssetRowPanel = new JPanel();
    myClipartAssetRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myAssetPanels.add(myClipartAssetRowPanel, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, true));
    myClipartLabel = new JBLabel();
    myClipartLabel.setText("Clip art:");
    myClipartAssetRowPanel.add(myClipartLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                   GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                                   null, null, 0, false));
    myClipartAssetButton = new ClipartIconButton();
    myClipartAssetRowPanel.add(myClipartAssetButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                         GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                         GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myTextAssetRowPanel = new JPanel();
    myTextAssetRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myAssetPanels.add(myTextAssetRowPanel, new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               null, null, null, 0, true));
    myTextLabel = new JBLabel();
    myTextLabel.setText("Text:");
    myTextAssetRowPanel.add(myTextLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                             null, 0, false));
    myTextAssetEditor = new MultiLineTextAssetEditor();
    myTextAssetRowPanel.add(myTextAssetEditor,
                            new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                null, 0, false));
    myNameRowPanel = new JPanel();
    myNameRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myNameRowPanel.setVisible(true);
    myAllOptionsPanel.add(myNameRowPanel, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              null, null, null, 0, true));
    myOutputNameLabel = new JBLabel();
    myOutputNameLabel.setText("Name:");
    myNameRowPanel.add(myOutputNameLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              null, null, 0, false));
    myOutputNamePanel = new JPanel();
    myOutputNamePanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    myNameRowPanel.add(myOutputNamePanel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              null, null, null, 0, false));
    myOutputNameTextField = new JTextField();
    myOutputNameTextField.setText("(name)");
    myOutputNameTextField.setToolTipText("The filename which will be used for these icons.");
    myOutputNamePanel.add(myOutputNameTextField,
                          new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    ButtonGroup buttonGroup;
    buttonGroup = new ButtonGroup();
    buttonGroup.add(myImageRadioButton);
    buttonGroup.add(myClipartRadioButton);
    buttonGroup.add(myTextRadioButton);
    buttonGroup = new ButtonGroup();
    buttonGroup.add(myNotTrimmedRadioButton);
    buttonGroup.add(myNotTrimmedRadioButton);
    buttonGroup.add(myTrimmedRadioButton);
    buttonGroup = new ButtonGroup();
    buttonGroup.add(myShrinkToFitRadioButton);
    buttonGroup.add(myCropRadioButton);
    buttonGroup = new ButtonGroup();
    buttonGroup.add(myDogEarRadioButton);
    buttonGroup.add(myNoEffectRadioButton);
  }

  @NotNull
  private static IconGenerator createIconGenerator(@NotNull Project project,
                                                   @NotNull AndroidIconType iconType,
                                                   int minSdkVersion,
                                                   @Nullable DrawableRenderer renderer) {
    switch (iconType) {
      case LAUNCHER_LEGACY:
        return new LauncherLegacyIconGenerator(project, minSdkVersion, renderer);
      case ACTIONBAR:
        return new ActionBarIconGenerator(project, minSdkVersion, renderer);
      case NOTIFICATION:
        return new NotificationIconGenerator(project, minSdkVersion, renderer);
      default:
        throw new IllegalArgumentException("Unexpected icon type: " + iconType);
    }
  }

  private void initializeListenersAndBindings() {
    IntProperty paddingPercent = new SliderValueProperty(myPaddingSlider);
    StringProperty paddingValueString = new TextProperty(myPaddingValueLabel);
    myGeneralBindings.bind(paddingValueString, new FormatExpression("%d %%", paddingPercent));

    myForegroundColor = new ColorProperty(myForegroundColorPanel);
    myBackgroundColor = ObjectProperty.wrap(new ColorProperty(myBackgroundColorPanel));

    myCropped = new SelectedProperty(myCropRadioButton);
    myDogEared = new SelectedProperty(myDogEarRadioButton);

    myTheme = ObjectProperty.wrap(new SelectedItemProperty<>(myThemeComboBox));
    myThemeColor = ObjectProperty.wrap(new ColorProperty(myCustomThemeColorPanel));

    myShape = ObjectProperty.wrap(new SelectedItemProperty<>(myShapeComboBox));

    initializeBindingsAndUiForIconType();

    // Update foreground layer asset type depending on asset type radio buttons.
    myAssetType.addListener(() -> {
      AssetComponent<?> assetComponent = myAssetPanelMap.get(myAssetType.get());
      myActiveAsset.set(assetComponent.getAsset());
    });

    // If any of our underlying asset panels change, we should pass that on to anyone listening to
    // us as well.
    ActionListener assetPanelListener = e -> fireAssetListeners();
    for (AssetComponent<?> assetComponent : myAssetPanelMap.values()) {
      assetComponent.addAssetListener(assetPanelListener);
    }

    BoolProperty trimmed = new SelectedProperty(myTrimmedRadioButton);

    Runnable onAssetModified = this::fireAssetListeners;
    myListeners
      .listenAll(myAssetType, trimmed, paddingPercent, myForegroundColor, myBackgroundColor, myCropped, myDogEared, myTheme, myThemeColor,
                 myShape)
      .with(onAssetModified);

    myListeners.listenAndFire(myActiveAsset, () -> {
      myActiveAssetBindings.releaseAll();
      BaseAsset asset = myActiveAsset.get();
      myActiveAssetBindings.bindTwoWay(trimmed, asset.trimmed());
      myActiveAssetBindings.bindTwoWay(paddingPercent, asset.paddingPercent());
      OptionalValueProperty<Color> assetColor = asset.color();
      if (assetColor.getValueOrNull() == null) {
        assetColor.setNullableValue(myForegroundColor.getValueOrNull());
      }
      myActiveAssetBindings.bindTwoWay(myForegroundColor, assetColor);

      getIconGenerator().sourceAsset().setValue(asset);
      onAssetModified.run();
    });

    ObservableBool isLauncherIcon = new BoolValueProperty(myIconType == AndroidIconType.LAUNCHER_LEGACY);
    ObservableBool isActionBarIcon = new BoolValueProperty(myIconType == AndroidIconType.ACTIONBAR);
    ObservableBool isCustomTheme = myTheme.isEqualTo(ActionBarIconGenerator.Theme.CUSTOM);
    ObservableValue<Boolean> isClipartOrText =
      myActiveAsset.transform(asset -> myClipartAssetButton.getAsset() == asset || myTextAssetEditor.getAsset() == asset);
    ObservableBool supportsEffects = new BooleanExpression(myShape) {
      @Override
      @NotNull
      public Boolean get() {
        Shape shape = myShape.get();
        switch (shape) {
          case SQUARE:
          case VRECT:
          case HRECT:
            return true;
          default:
            return false;
        }
      }
    };

    /*
     * Hook up a bunch of UI <- boolean expressions, so that when certain conditions are met,
     * various components show/hide. This also requires refreshing the panel explicitly, as
     * otherwise Swing doesn't realize it should trigger a relayout.
     */
    ImmutableMap.Builder<BoolProperty, ObservableBool> layoutPropertiesBuilder = ImmutableMap.builder();
    layoutPropertiesBuilder.put(new VisibleProperty(myImageAssetRowPanel), new SelectedProperty(myImageRadioButton));
    layoutPropertiesBuilder.put(new VisibleProperty(myClipartAssetRowPanel), new SelectedProperty(myClipartRadioButton));
    layoutPropertiesBuilder.put(new VisibleProperty(myTextAssetRowPanel), new SelectedProperty(myTextRadioButton));
    layoutPropertiesBuilder.put(new VisibleProperty(myForegroundRowPanel), isLauncherIcon.and(isClipartOrText));
    layoutPropertiesBuilder.put(new VisibleProperty(myBackgroundRowPanel), isLauncherIcon);
    layoutPropertiesBuilder.put(new VisibleProperty(myScalingRowPanel), isLauncherIcon);
    layoutPropertiesBuilder.put(new VisibleProperty(myShapeRowPanel), isLauncherIcon);
    layoutPropertiesBuilder.put(new VisibleProperty(myEffectRowPanel), isLauncherIcon);
    layoutPropertiesBuilder.put(new EnabledProperty(myDogEarRadioButton), supportsEffects);
    layoutPropertiesBuilder.put(new VisibleProperty(myThemeRowPanel), isActionBarIcon);
    layoutPropertiesBuilder.put(new VisibleProperty(myCustomThemeRowPanel), isActionBarIcon.and(isCustomTheme));

    ImmutableMap<BoolProperty, ObservableBool> layoutProperties = layoutPropertiesBuilder.build();
    for (Map.Entry<BoolProperty, ObservableBool> e : layoutProperties.entrySet()) {
      // Initialize everything off, as this makes sure the frame that uses this panel won't start
      // REALLY LARGE by default.
      e.getKey().set(false);
      myGeneralBindings.bind(e.getKey(), e.getValue());
    }
    myListeners.listenAll(layoutProperties.keySet()).with(() -> SwingUtilities.updateComponentTreeUI(myAllOptionsPanel));
  }

  @NotNull
  public BaseAsset getAsset() {
    return myActiveAsset.get();
  }

  /**
   * Returns an icon generator which will create Android icons using the panel's current settings.
   */
  @Override
  @NotNull
  public IconGenerator getIconGenerator() {
    return myIconGenerator;
  }

  @Override
  @NotNull
  public JComponent getRootComponent() {
    return this;
  }

  /**
   * Adds a listener which will be triggered whenever the asset represented by this panel is
   * modified in any way.
   */
  @Override
  public void addAssetListener(@NotNull ActionListener listener) {
    myAssetListeners.add(listener);
  }

  @Override
  @NotNull
  public StringProperty outputName() {
    return myOutputName;
  }

  private void fireAssetListeners() {
    ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null);
    for (ActionListener assetListener : myAssetListeners) {
      assetListener.actionPerformed(event);
    }
  }

  private void initializeBindingsAndUiForIconType() {
    myOutputName.set(myDefaultOutputName);

    myGeneralBindings.bind(myIconGenerator.sourceAsset(), new AsOptionalExpression<>(myActiveAsset));
    myGeneralBindings.bind(myIconGenerator.outputName(), myOutputName);

    switch (myIconType) {
      case LAUNCHER_LEGACY:
        LauncherLegacyIconGenerator launcherIconGenerator = (LauncherLegacyIconGenerator)myIconGenerator;
        myGeneralBindings.bindTwoWay(launcherIconGenerator.backgroundColor(), myBackgroundColor);
        myGeneralBindings.bindTwoWay(launcherIconGenerator.cropped(), myCropped);
        myGeneralBindings.bindTwoWay(launcherIconGenerator.shape(), myShape);
        myGeneralBindings.bindTwoWay(launcherIconGenerator.dogEared(), myDogEared);
        break;

      case ACTIONBAR:
        ActionBarIconGenerator actionBarIconGenerator = (ActionBarIconGenerator)myIconGenerator;
        myGeneralBindings.bindTwoWay(actionBarIconGenerator.customColor(), myThemeColor);
        myGeneralBindings.bindTwoWay(actionBarIconGenerator.theme(), myTheme);
        break;

      case NOTIFICATION:
        // No special options.
        break;

      default:
        throw new IllegalStateException("Unexpected icon type: " + myIconType);
    }
  }

  @Override
  public void dispose() {
    myGeneralBindings.releaseAll();
    myActiveAssetBindings.releaseAll();
    myListeners.releaseAll();
    myAssetListeners.clear();
  }

  private enum AssetType {
    IMAGE,
    CLIP_ART,
    TEXT
  }
}
