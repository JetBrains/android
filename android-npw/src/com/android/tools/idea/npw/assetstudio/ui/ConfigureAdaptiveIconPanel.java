/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.getBundledImage;
import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.toUpperCamelCase;
import static com.android.tools.idea.npw.assetstudio.LauncherIconGenerator.DEFAULT_FOREGROUND_COLOR;

import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.model.StudioAndroidModuleInfo;
import com.android.tools.idea.npw.assetstudio.AdaptiveIconGenerator;
import com.android.tools.idea.npw.assetstudio.IconGenerator.Shape;
import com.android.tools.idea.npw.assetstudio.LauncherIconGenerator;
import com.android.tools.idea.npw.assetstudio.TvChannelIconGenerator;
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
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.OptionalValueProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.expressions.Expression;
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
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.module.AndroidModuleInfo;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
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
public class ConfigureAdaptiveIconPanel extends JPanel implements Disposable, ConfigureIconView, PersistentStateComponent<PersistentState> {
  private static final boolean HIDE_INAPPLICABLE_CONTROLS = false; // TODO Decide on hiding or disabling.

  private static final File DEFAULT_FOREGROUND_IMAGE = getBundledImage("asset_studio", "ic_launcher_foreground.xml");
  private static final File DEFAULT_BACKGROUND_IMAGE = getBundledImage("asset_studio", "ic_launcher_background.xml");
  private static final ForegroundAssetType DEFAULT_FOREGROUND_ASSET_TYPE = ForegroundAssetType.IMAGE;
  private static final BackgroundAssetType DEFAULT_BACKGROUND_ASSET_TYPE = BackgroundAssetType.IMAGE;
  private static final Density DEFAULT_PREVIEW_DENSITY = Density.XHIGH;
  private static final Shape DEFAULT_ICON_SHAPE = Shape.SQUARE;

  private static final String FOREGROUND_ASSET_TYPE_PROPERTY = "foregroundAssetType";
  private static final String BACKGROUND_ASSET_TYPE_PROPERTY = "backgroundAssetType";
  private static final String BACKGROUND_COLOR_PROPERTY = "backgroundColor";
  private static final String GENERATE_LEGACY_ICON_PROPERTY = "generateLegacyIcon";
  private static final String GENERATE_ROUND_ICON_PROPERTY = "generateRoundIcon";
  private static final String GENERATE_PLAY_STORE_ICON_PROPERTY = "generatePlayStoreIcon";
  private static final String GENERATE_WEBP_ICONS_PROPERTY = "generateWebpIcons";
  private static final String LEGACY_ICON_SHAPE_PROPERTY = "legacyIconShape";
  private static final String SHOW_GRID_PROPERTY = "showGrid";
  private static final String SHOW_SAFE_ZONE_PROPERTY = "showSafeZone";
  private static final String PREVIEW_DENSITY_PROPERTY = "previewDensity";
  private static final String OUTPUT_NAME_PROPERTY = "outputName";
  private static final String FOREGROUND_LAYER_NAME_PROPERTY = "foregroundLayerName";
  private static final String BACKGROUND_LAYER_NAME_PROPERTY = "backgroundLayerName";
  private static final String BACKGROUND_IMAGE_PROPERTY = "backgroundImage";
  private static final String FOREGROUND_CLIPART_ASSET_PROPERTY = "foregroundClipartAsset";
  private static final String FOREGROUND_TEXT_ASSET_PROPERTY = "foregroundTextAsset";

  /**
   * This panel presents a list of radio buttons (clipart, image, text), and whichever one is
   * selected sets the active asset.
   */
  private JPanel myRootPanel;
  private JBLabel myOutputNameLabel;
  private JTextField myOutputNameTextField;

  private JPanel myForegroundAllOptionsPanel;
  private JRadioButton myForegroundClipartRadioButton;
  private JRadioButton myForegroundTextRadioButton;
  private JRadioButton myForegroundImageRadioButton;
  private JRadioButton myForegroundTrimYesRadioButton;
  private JRadioButton myForegroundTrimNoRadioButton;
  private JPanel myForegroundTrimOptionsPanel;
  private JSlider myForegroundResizeSlider;
  private JLabel myForegroundResizeValueLabel;
  private JPanel myForegroundAssetRadioButtonsPanel;
  private JPanel myForegroundResizeSliderPanel;
  private JTextField myForegroundLayerNameTextField;
  private JPanel myForegroundColorRowPanel;
  private ColorPanel myForegroundColorPanel;
  private JPanel mGenerateLegacyIconRadioButtonsPanel;
  private JRadioButton myGenerateLegacyIconYesRadioButton;
  private JBScrollPane myForegroundScrollPane;
  private JPanel myForegroundImageAssetRowPanel;
  private JPanel myForegroundClipartAssetRowPanel;
  private JPanel myForegroundTextAssetRowPanel;
  private ImageAssetBrowser myForegroundImageAssetBrowser;
  private ClipartIconButton myForegroundClipartAssetButton;
  private MultiLineTextAssetEditor myForegroundTextAssetEditor;
  private JBLabel myForegroundLayerNameLabel;
  private JLabel myForegroundAssetTypeLabel;
  private JBLabel myForegroundImagePathLabel;
  private JBLabel myForegroundClipartLabel;
  private JBLabel myForegroundTextLabel;
  private JBLabel myForegroundTrimLabel;
  private JBLabel myForegroundResizeLabel;
  private JBLabel myForegroundColorLabel;
  private JBLabel myGenerateLegacyIconLabel;

  private JPanel myBackgroundAllOptionsPanel;
  private JRadioButton myBackgroundImageRadioButton;
  private JRadioButton myBackgroundColorRadioButton;
  private JRadioButton myBackgroundTrimYesRadioButton;
  private JPanel myBackgroundTrimOptionsPanel;
  private JSlider myBackgroundResizeSlider;
  private JLabel myBackgroundResizeValueLabel;
  private JPanel myBackgroundAssetRadioButtonsPanel;
  private JPanel myBackgroundResizeSliderPanel;
  private JTextField myBackgroundLayerNameTextField;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myBackgroundTrimRowPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myBackgroundResizeRowPanel;
  private JPanel myBackgroundColorRowPanel;
  private ColorPanel myBackgroundColorPanel;
  private TitledSeparator myGenerateRoundIconTitle;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myGenerateRoundIconRowPanel;
  private JPanel myGenerateRoundIconRadioButtonsPanel;
  private JRadioButton myGenerateRoundIconYesRadioButton;
  private JComboBox<Shape> myLegacyIconShapeComboBox;
  private JBScrollPane myBackgroundScrollPane;
  private JPanel myBackgroundImageAssetRowPanel;
  private ImageAssetBrowser myBackgroundImageAssetBrowser;
  private JBLabel myBackgroundLayerNameLabel;
  private JLabel myBackgroundAssetTypeLabel;
  private JBLabel myBackgroundImagePathLabel;
  private JBLabel myBackgroundTrimLabel;
  private JBLabel myBackgroundResizeLabel;
  private JBLabel myGenerateRoundIconLabel;
  private JBLabel myBackgroundColorLabel;
  private JBLabel myLegacyIconShapeLabel;
  private JBScrollPane myOtherIconsScrollPane;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myOtherIconsAllOptionsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundAssetTypePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundLayerNamePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundAssetTypeSourcePanel;
  private JPanel myForegroundImageOptionsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myBackgroundLayerNamePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myBackgroundAssetTypePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myBackgroundAssetTypeSourcePanel;
  private JPanel myBackgroundImageOptionsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myLegacyIconShapeRowPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myOutputNamePanelRow;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private TitledSeparator myForegroundScalingTitleSeparator;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private TitledSeparator mySourceAssetTitleSeparator;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundResizePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private TitledSeparator myBackgroundScalingTitleSeparator;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private TitledSeparator myBackgroundSourceAssetTitleSeparator;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myGenerateLegacyIconRowPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundTrimPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myLegacyIconShapePanel;
  private TitledSeparator myGeneratePlayStoreIconTitle;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myGeneratePlayStoreIconRowPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JBLabel myGeneratePlayStoreIconLabel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myGeneratePlayStoreIconRadioButtonsPanel;
  private JRadioButton myGeneratePlayStoreIconYesRadioButton;
  private JRadioButton myBackgroundTrimNoRadioButton;
  private TitledSeparator myIconFormatTitle;
  private JBLabel myIconFormatLabel;
  private JPanel myIconFormatRowPanel;
  private JRadioButton myIconFormatWebpRadioButton;
  private JPanel myIconFormatRadioButtonsPanel;

  @NotNull private final AndroidIconType myIconType;
  @NotNull private final String myDefaultOutputName;

  // @formatter:off
  private static final Map<Shape, String> myShapeNames = ImmutableMap.of(
      Shape.NONE, "None",
      Shape.CIRCLE, "Circle",
      Shape.SQUARE, "Square",
      Shape.VRECT, "Vertical",
      Shape.HRECT, "Horizontal");
  // @formatter:on

  @NotNull private final AndroidVersion myBuildSdkVersion;
  @NotNull private final AdaptiveIconGenerator myIconGenerator;
  @NotNull private final ValidatorPanel myValidatorPanel;

  @NotNull private final BindingsManager myGeneralBindings = new BindingsManager();
  @NotNull private final BindingsManager myForegroundActiveAssetBindings = new BindingsManager();
  @NotNull private final BindingsManager myBackgroundActiveAssetBindings = new BindingsManager();
  @NotNull private final ListenerManager myListeners = new ListenerManager();
  @NotNull private final List<ActionListener> myAssetListeners = new ArrayList<>(1);

  @NotNull private final ImmutableMap<ForegroundAssetType, AssetComponent<?>> myForegroundAssetPanelMap;

  @NotNull private final StringProperty myOutputName;
  @NotNull private final StringProperty myForegroundLayerName;
  @NotNull private final StringProperty myBackgroundLayerName;
  @NotNull private final ObjectProperty<BaseAsset> myForegroundActiveAsset;
  @NotNull private final OptionalProperty<ImageAsset> myBackgroundImageAsset;
  @NotNull private final ObjectProperty<Validator.Result> myForegroundAssetValidityState = new ObjectValueProperty<>(Validator.Result.OK);
  @NotNull private final ObjectProperty<Validator.Result> myBackgroundAssetValidityState = new ObjectValueProperty<>(Validator.Result.OK);
  @NotNull private final AbstractProperty<ForegroundAssetType> myForegroundAssetType;
  @NotNull private final AbstractProperty<BackgroundAssetType> myBackgroundAssetType;
  @NotNull private final BoolProperty myShowGrid;
  @NotNull private final BoolProperty myShowSafeZone;
  @NotNull private final AbstractProperty<Density> myPreviewDensity;
  private ColorProperty myForegroundColor;
  private AbstractProperty<Color> myBackgroundColor;
  private BoolProperty myForegroundTrimmed;
  private BoolProperty myBackgroundTrimmed;
  private IntProperty myForegroundResizePercent;
  private IntProperty myBackgroundResizePercent;
  private BoolProperty myGenerateLegacyIcon;
  private BoolProperty myGenerateRoundIcon;
  private BoolProperty myGeneratePlayStoreIcon;
  private BoolProperty myGenerateWebpIcons;
  private AbstractProperty<Shape> myLegacyIconShape;
  @NotNull private final IdeResourceNameValidator myNameValidator = IdeResourceNameValidator.forFilename(ResourceFolderType.DRAWABLE);

  /**
   * Initializes a panel which can generate Android launcher icons. The supported types passed in
   * will be presented to the user in a combo box (unless there's only one supported type). If no
   * supported types are passed in, then all types will be supported by default.
   */
  public ConfigureAdaptiveIconPanel(@NotNull Disposable disposableParent,
                                    @NotNull AndroidFacet facet,
                                    @NotNull AndroidIconType iconType,
                                    @NotNull BoolProperty showGrid,
                                    @NotNull BoolProperty showSafeZone,
                                    @NotNull AbstractProperty<Density> previewDensity,
                                    @NotNull ValidatorPanel validatorPanel,
                                    @Nullable DrawableRenderer renderer) {
    super(new BorderLayout());
    setupUI();
    myIconType = iconType;
    myDefaultOutputName = myIconType.toOutputName("");

    AndroidModuleInfo androidModuleInfo = StudioAndroidModuleInfo.getInstance(facet);
    AndroidVersion buildSdkVersion = androidModuleInfo.getBuildSdkVersion();
    myBuildSdkVersion = buildSdkVersion != null ? buildSdkVersion : new AndroidVersion(26);

    myShowGrid = showGrid;
    myShowSafeZone = showSafeZone;
    myPreviewDensity = previewDensity;
    myIconGenerator = myIconType == AndroidIconType.LAUNCHER ?
        new LauncherIconGenerator(facet.getModule().getProject(), androidModuleInfo.getMinSdkVersion().getApiLevel(), renderer) :
        new TvChannelIconGenerator(facet.getModule().getProject(), androidModuleInfo.getMinSdkVersion().getApiLevel(), renderer);
    myValidatorPanel = validatorPanel;

    myForegroundImageAssetBrowser.getAsset().setDefaultImagePath(DEFAULT_FOREGROUND_IMAGE);
    myBackgroundImageAssetBrowser.getAsset().setDefaultImagePath(DEFAULT_BACKGROUND_IMAGE);
    myForegroundTextAssetEditor.getAsset().setDefaultText("Aa");

    DefaultComboBoxModel<Shape> legacyShapesModel = new DefaultComboBoxModel<>();
    for (Shape shape : myShapeNames.keySet()) {
      legacyShapesModel.addElement(shape);
    }
    myLegacyIconShapeComboBox.setRenderer(SimpleListCellRenderer.create("", myShapeNames::get));
    myLegacyIconShapeComboBox.setModel(legacyShapesModel);
    myLegacyIconShapeComboBox.setSelectedItem(Shape.SQUARE);

    myForegroundScrollPane.getVerticalScrollBar().setUnitIncrement(10);
    myForegroundScrollPane.setBorder(JBUI.Borders.empty());

    myBackgroundScrollPane.getVerticalScrollBar().setUnitIncrement(10);
    myBackgroundScrollPane.setBorder(JBUI.Borders.empty());

    myOtherIconsScrollPane.getVerticalScrollBar().setUnitIncrement(10);
    myOtherIconsScrollPane.setBorder(JBUI.Borders.empty());

    myOutputName = new TextProperty(myOutputNameTextField);
    myForegroundLayerName = new TextProperty(myForegroundLayerNameTextField);
    myBackgroundLayerName = new TextProperty(myBackgroundLayerNameTextField);
    myListeners.listen(myForegroundLayerName, name -> {
      if (name.equals(defaultForegroundLayerName())) {
        myGeneralBindings.bind(myForegroundLayerName, Expression.create(this::defaultForegroundLayerName, myOutputName));
      }
      else {
        myGeneralBindings.release(myForegroundLayerName);
      }
    });
    myListeners.listen(myBackgroundLayerName, name -> {
      if (name.equals(defaultBackgroundLayerName())) {
        myGeneralBindings.bind(myBackgroundLayerName, Expression.create(this::defaultBackgroundLayerName, myOutputName));
      }
      else {
        myGeneralBindings.release(myBackgroundLayerName);
      }
    });

    myForegroundAssetPanelMap = ImmutableMap.of(
      ForegroundAssetType.IMAGE, myForegroundImageAssetBrowser,
      ForegroundAssetType.CLIP_ART, myForegroundClipartAssetButton,
      ForegroundAssetType.TEXT, myForegroundTextAssetEditor);

    myForegroundImageAssetBrowser.getAsset().imagePath().setValue(DEFAULT_FOREGROUND_IMAGE);
    myBackgroundImageAssetBrowser.getAsset().imagePath().setValue(DEFAULT_BACKGROUND_IMAGE);

    // Call "setLabelFor" in code instead of designer since designer is so inconsistent about
    // valid targets.
    myOutputNameLabel.setLabelFor(myOutputNameTextField);

    myForegroundLayerNameLabel.setLabelFor(myForegroundLayerNameTextField);
    myForegroundAssetTypeLabel.setLabelFor(myForegroundAssetRadioButtonsPanel);
    myForegroundImagePathLabel.setLabelFor(myForegroundImageAssetBrowser);
    myForegroundClipartLabel.setLabelFor(myForegroundClipartAssetButton);
    myForegroundTextLabel.setLabelFor(myForegroundTextAssetEditor);
    myForegroundTrimLabel.setLabelFor(myForegroundTrimOptionsPanel);
    myForegroundResizeLabel.setLabelFor(myForegroundResizeSliderPanel);
    myForegroundColorLabel.setLabelFor(myForegroundColorPanel);
    myGenerateLegacyIconLabel.setLabelFor(mGenerateLegacyIconRadioButtonsPanel);

    myBackgroundLayerNameLabel.setLabelFor(myBackgroundLayerNameTextField);
    myBackgroundAssetTypeLabel.setLabelFor(myBackgroundAssetRadioButtonsPanel);
    myBackgroundImagePathLabel.setLabelFor(myBackgroundImageAssetBrowser);
    myBackgroundTrimLabel.setLabelFor(myBackgroundTrimOptionsPanel);
    myBackgroundResizeLabel.setLabelFor(myBackgroundResizeSliderPanel);
    myBackgroundColorLabel.setLabelFor(myBackgroundColorPanel);
    myGenerateRoundIconLabel.setLabelFor(myGenerateRoundIconRadioButtonsPanel);
    myLegacyIconShapeLabel.setLabelFor(myLegacyIconShapeComboBox);

    myForegroundAssetType = new SelectedRadioButtonProperty<>(DEFAULT_FOREGROUND_ASSET_TYPE, ForegroundAssetType.values(),
                                                              myForegroundImageRadioButton, myForegroundClipartRadioButton,
                                                              myForegroundTextRadioButton);
    myForegroundActiveAsset = new ObjectValueProperty<>(myForegroundImageAssetBrowser.getAsset());
    myForegroundImageAssetBrowser.getAsset().setRole("foreground image");
    myForegroundColorPanel.setSelectedColor(DEFAULT_FOREGROUND_COLOR);

    myBackgroundAssetType = new SelectedRadioButtonProperty<>(DEFAULT_BACKGROUND_ASSET_TYPE, BackgroundAssetType.values(),
                                                              myBackgroundImageRadioButton, myBackgroundColorRadioButton);
    myBackgroundImageAsset = new OptionalValueProperty<>(myBackgroundImageAssetBrowser.getAsset());
    myBackgroundImageAssetBrowser.getAsset().setRole("background image");
    myBackgroundColorPanel.setSelectedColor(myIconGenerator.backgroundColor().get());

    initializeListenersAndBindings();
    initializeValidators();

    Disposer.register(disposableParent, this);
    for (AssetComponent<?> assetComponent : myForegroundAssetPanelMap.values()) {
      Disposer.register(this, assetComponent);
    }
    Disposer.register(this, myBackgroundImageAssetBrowser);
    Disposer.register(this, myIconGenerator);

    add(myRootPanel);
  }

  @Override
  @NotNull
  public PersistentState getState() {
    PersistentState state = new PersistentState();
    state.set(FOREGROUND_ASSET_TYPE_PROPERTY, myForegroundAssetType.get(), DEFAULT_FOREGROUND_ASSET_TYPE);
    state.set(BACKGROUND_ASSET_TYPE_PROPERTY, myBackgroundAssetType.get(), DEFAULT_BACKGROUND_ASSET_TYPE);
    for (Map.Entry<ForegroundAssetType, AssetComponent<?>> entry : myForegroundAssetPanelMap.entrySet()) {
      state.setChild("foreground" + toUpperCamelCase(entry.getKey()), entry.getValue().getAsset().getState());
    }
    state.setChild(BACKGROUND_IMAGE_PROPERTY, myBackgroundImageAssetBrowser.getAsset().getState());
    // Notice that the foreground colors that are owned by the asset components have already been saved.
    state.set(BACKGROUND_COLOR_PROPERTY, myBackgroundColor.get(), AdaptiveIconGenerator.DEFAULT_BACKGROUND_COLOR);
    state.set(GENERATE_LEGACY_ICON_PROPERTY, myGenerateLegacyIcon.get(), true);
    state.set(GENERATE_ROUND_ICON_PROPERTY, myGenerateRoundIcon.get(), true);
    state.set(GENERATE_PLAY_STORE_ICON_PROPERTY, myGeneratePlayStoreIcon.get(), true);
    state.set(GENERATE_WEBP_ICONS_PROPERTY, myGenerateWebpIcons.get(), true);
    state.set(LEGACY_ICON_SHAPE_PROPERTY, myLegacyIconShape.get(), DEFAULT_ICON_SHAPE);
    state.set(SHOW_GRID_PROPERTY, myShowGrid.get(), false);
    state.set(SHOW_SAFE_ZONE_PROPERTY, myShowSafeZone.get(), true);
    state.setEncoded(PREVIEW_DENSITY_PROPERTY, nullIfDefault(myPreviewDensity.get()), Density::getResourceValue);
    state.set(OUTPUT_NAME_PROPERTY, myOutputName.get(), myDefaultOutputName);
    state.set(FOREGROUND_LAYER_NAME_PROPERTY, myForegroundLayerName.get(), defaultForegroundLayerName());
    state.set(BACKGROUND_LAYER_NAME_PROPERTY, myBackgroundLayerName.get(), defaultBackgroundLayerName());
    state.setChild(FOREGROUND_CLIPART_ASSET_PROPERTY, myForegroundClipartAssetButton.getState());
    state.setChild(FOREGROUND_TEXT_ASSET_PROPERTY, myForegroundTextAssetEditor.getAsset().getState());
    return state;
  }

  @Nullable
  private Density nullIfDefault(Density density) {
    return density == null || density.equals(DEFAULT_PREVIEW_DENSITY) ? null : density;
  }

  @Override
  public void loadState(@NotNull PersistentState state) {
    myForegroundAssetType.set(state.get(FOREGROUND_ASSET_TYPE_PROPERTY, DEFAULT_FOREGROUND_ASSET_TYPE));
    myBackgroundAssetType.set(state.get(BACKGROUND_ASSET_TYPE_PROPERTY, DEFAULT_BACKGROUND_ASSET_TYPE));
    for (Map.Entry<ForegroundAssetType, AssetComponent<?>> entry : myForegroundAssetPanelMap.entrySet()) {
      PersistentStateUtil.load(entry.getValue().getAsset(), state.getChild("foreground" + toUpperCamelCase(entry.getKey())));
    }
    PersistentStateUtil.load(myBackgroundImageAssetBrowser.getAsset(), state.getChild(BACKGROUND_IMAGE_PROPERTY));
    // Notice that the foreground colors that are owned by the asset components have already been loaded.
    myBackgroundColor.set(state.get(BACKGROUND_COLOR_PROPERTY, AdaptiveIconGenerator.DEFAULT_BACKGROUND_COLOR));
    myGenerateLegacyIcon.set(state.get(GENERATE_LEGACY_ICON_PROPERTY, true));
    myGenerateRoundIcon.set(state.get(GENERATE_ROUND_ICON_PROPERTY, true));
    myGeneratePlayStoreIcon.set(state.get(GENERATE_PLAY_STORE_ICON_PROPERTY, true));
    myGenerateWebpIcons.set(state.get(GENERATE_WEBP_ICONS_PROPERTY, true));
    myLegacyIconShape.set(state.get(LEGACY_ICON_SHAPE_PROPERTY, DEFAULT_ICON_SHAPE));
    myShowGrid.set(state.get(SHOW_GRID_PROPERTY, false));
    myShowSafeZone.set(state.get(SHOW_SAFE_ZONE_PROPERTY, true));
    myPreviewDensity.set(LintUtils.coalesce(state.getDecoded(PREVIEW_DENSITY_PROPERTY, Density::create), DEFAULT_PREVIEW_DENSITY));
    myOutputName.set(state.get(OUTPUT_NAME_PROPERTY, myDefaultOutputName));
    myForegroundLayerName.set(state.get(FOREGROUND_LAYER_NAME_PROPERTY, defaultForegroundLayerName()));
    myBackgroundLayerName.set(state.get(BACKGROUND_LAYER_NAME_PROPERTY, defaultBackgroundLayerName()));
    PersistentStateUtil.load(myForegroundClipartAssetButton, state.getChild(FOREGROUND_CLIPART_ASSET_PROPERTY));
    PersistentStateUtil.load(myForegroundTextAssetEditor.getAsset(), state.getChild(FOREGROUND_TEXT_ASSET_PROPERTY));
  }

  private void createUIComponents() {
    // TODO: place custom component creation code here
  }

  private void initializeListenersAndBindings() {
    myForegroundTrimmed = new SelectedProperty(myForegroundTrimYesRadioButton);
    myBackgroundTrimmed = new SelectedProperty(myBackgroundTrimYesRadioButton);

    myForegroundResizePercent = new SliderValueProperty(myForegroundResizeSlider);
    StringProperty foregroundResizeValueString = new TextProperty(myForegroundResizeValueLabel);
    myGeneralBindings.bind(foregroundResizeValueString, new FormatExpression("%d %%", myForegroundResizePercent));

    myBackgroundResizePercent = new SliderValueProperty(myBackgroundResizeSlider);
    StringProperty backgroundResizeValueString = new TextProperty(myBackgroundResizeValueLabel);
    myGeneralBindings.bind(backgroundResizeValueString, new FormatExpression("%d %%", myBackgroundResizePercent));

    myForegroundColor = new ColorProperty(myForegroundColorPanel);
    myBackgroundColor = ObjectProperty.wrap(new ColorProperty(myBackgroundColorPanel));
    myGenerateLegacyIcon = new SelectedProperty(myGenerateLegacyIconYesRadioButton);
    myGenerateRoundIcon = new SelectedProperty(myGenerateRoundIconYesRadioButton);
    myGeneratePlayStoreIcon = new SelectedProperty(myGeneratePlayStoreIconYesRadioButton);
    myGenerateWebpIcons = new SelectedProperty(myIconFormatWebpRadioButton);

    myLegacyIconShape = ObjectProperty.wrap(new SelectedItemProperty<>(myLegacyIconShapeComboBox));

    updateBindingsAndUiForActiveIconType();

    // Update foreground layer asset type depending on asset type radio buttons.
    myForegroundAssetType.addListener(() -> {
      AssetComponent<?> assetComponent = myForegroundAssetPanelMap.get(myForegroundAssetType.get());
      myForegroundActiveAsset.set(assetComponent.getAsset());
    });

    // Update background asset depending on asset type radio buttons.
    myBackgroundAssetType.addListener(() -> {
      if (myBackgroundAssetType.get() == BackgroundAssetType.IMAGE) {
        myBackgroundImageAsset.setValue(myBackgroundImageAssetBrowser.getAsset());
      } else {
        myBackgroundImageAsset.clear();
      }
    });

    // If any of our underlying asset panels change, we should pass that on to anyone listening to
    // us as well.
    ActionListener assetPanelListener = e -> fireAssetListeners();
    for (AssetComponent<?> assetComponent : myForegroundAssetPanelMap.values()) {
      assetComponent.addAssetListener(assetPanelListener);
    }
    myBackgroundImageAssetBrowser.addAssetListener(assetPanelListener);

    Runnable onAssetModified = this::fireAssetListeners;
    myListeners
      .listenAll(myForegroundTrimmed, myForegroundResizePercent, myForegroundColor,
                 myBackgroundTrimmed, myBackgroundResizePercent, myBackgroundColor,
                 myGenerateLegacyIcon, myLegacyIconShape,
                 myGenerateRoundIcon, myGeneratePlayStoreIcon, myGenerateWebpIcons)
      .with(onAssetModified);

    BoolValueProperty foregroundIsResizable = new BoolValueProperty();
    myListeners.listenAndFire(myForegroundActiveAsset, () -> {
      myForegroundActiveAssetBindings.releaseAll();
      BaseAsset asset = myForegroundActiveAsset.get();
      myForegroundActiveAssetBindings.bindTwoWay(myForegroundTrimmed, asset.trimmed());
      myForegroundActiveAssetBindings.bindTwoWay(myForegroundResizePercent, asset.scalingPercent());
      OptionalValueProperty<Color> assetColor = asset.color();
      if (assetColor.getValueOrNull() == null) {
        assetColor.setNullableValue(myForegroundColor.getValueOrNull());
      }
      myForegroundActiveAssetBindings.bindTwoWay(myForegroundColor, assetColor);
      myForegroundActiveAssetBindings.bind(foregroundIsResizable, asset.isResizable());
      if (asset instanceof ImageAsset) {
        myForegroundActiveAssetBindings.bind(myForegroundAssetValidityState, ((ImageAsset)asset).getValidityState());
      }
      else {
        myForegroundAssetValidityState.set(Validator.Result.OK);
      }

      getIconGenerator().sourceAsset().setValue(asset);
      onAssetModified.run();
    });

    BoolValueProperty backgroundIsResizable = new BoolValueProperty();
    // When switching between Image/Color for background, bind corresponding properties and regenerate asset (to be sure).
    Runnable onBackgroundAssetModified = () -> {
      myBackgroundActiveAssetBindings.releaseAll();
      ImageAsset asset = myBackgroundImageAsset.getValueOrNull();
      if (asset != null) {
        myBackgroundActiveAssetBindings.bindTwoWay(myBackgroundTrimmed, asset.trimmed());
        myBackgroundActiveAssetBindings.bindTwoWay(myBackgroundResizePercent, asset.scalingPercent());
        myBackgroundActiveAssetBindings.bind(backgroundIsResizable, asset.isResizable());
        myBackgroundActiveAssetBindings.bind(myBackgroundAssetValidityState, asset.getValidityState());
      }
      else {
        backgroundIsResizable.set(false);
        myBackgroundAssetValidityState.set(Validator.Result.OK);
      }
      getIconGenerator().backgroundImageAsset().setNullableValue(asset);
      onAssetModified.run();
    };
    myListeners.listenAndFire(myBackgroundImageAsset, onBackgroundAssetModified::run);

    /*
     * Hook up a bunch of UI <- boolean expressions, so that when certain conditions are met,
     * various components show/hide. This also requires refreshing the panel explicitly, as
     * otherwise Swing doesn't realize it should trigger a re-layout.
     */
    ImmutableMap.Builder<BoolProperty, ObservableValue<Boolean>> layoutPropertiesBuilder = ImmutableMap.builder();
    layoutPropertiesBuilder.put(new VisibleProperty(myForegroundImageAssetRowPanel), new SelectedProperty(myForegroundImageRadioButton));
    layoutPropertiesBuilder.put(
      new VisibleProperty(myForegroundClipartAssetRowPanel), new SelectedProperty(myForegroundClipartRadioButton));
    layoutPropertiesBuilder.put(new VisibleProperty(myForegroundTextAssetRowPanel), new SelectedProperty(myForegroundTextRadioButton));
    Expression<Boolean> isForegroundIsNotImage =
      Expression.create(() -> myForegroundAssetType.get() != ForegroundAssetType.IMAGE, myForegroundAssetType);
    layoutPropertiesBuilder.put(new VisibleProperty(myForegroundColorRowPanel), isForegroundIsNotImage);

    if (HIDE_INAPPLICABLE_CONTROLS) {
      layoutPropertiesBuilder.put(new VisibleProperty(myForegroundScalingTitleSeparator), foregroundIsResizable);
      layoutPropertiesBuilder.put(new VisibleProperty(myForegroundImageOptionsPanel), foregroundIsResizable);
    }
    else {
      layoutPropertiesBuilder.put(new EnabledProperty(myForegroundTrimYesRadioButton), foregroundIsResizable);
      layoutPropertiesBuilder.put(new EnabledProperty(myForegroundTrimNoRadioButton), foregroundIsResizable);
      layoutPropertiesBuilder.put(new EnabledProperty(myForegroundResizeSlider), foregroundIsResizable);
    }

    // Show either the image or the color UI controls.
    ObservableBool backgroundIsImage = new SelectedProperty(myBackgroundImageRadioButton);
    ObservableBool backgroundIsColor = new SelectedProperty(myBackgroundColorRadioButton);
    layoutPropertiesBuilder.put(new VisibleProperty(myBackgroundImageAssetRowPanel), backgroundIsImage);
    layoutPropertiesBuilder.put(new VisibleProperty(myBackgroundColorRowPanel), backgroundIsColor);

    if (HIDE_INAPPLICABLE_CONTROLS) {
      layoutPropertiesBuilder.put(new VisibleProperty(myBackgroundScalingTitleSeparator), backgroundIsResizable);
      layoutPropertiesBuilder.put(new VisibleProperty(myBackgroundImageOptionsPanel), backgroundIsResizable);
    }
    else {
      layoutPropertiesBuilder.put(new EnabledProperty(myBackgroundTrimYesRadioButton), backgroundIsResizable);
      layoutPropertiesBuilder.put(new EnabledProperty(myBackgroundTrimNoRadioButton), backgroundIsResizable);
      layoutPropertiesBuilder.put(new EnabledProperty(myBackgroundResizeSlider), backgroundIsResizable);
    }

    layoutPropertiesBuilder.put(new EnabledProperty(myLegacyIconShapeComboBox), new SelectedProperty(myGenerateLegacyIconYesRadioButton));

    ObservableBool isLauncherIcon = new BoolValueProperty(myIconType == AndroidIconType.LAUNCHER);
    layoutPropertiesBuilder.put(new VisibleProperty(myLegacyIconShapeRowPanel), isLauncherIcon);
    layoutPropertiesBuilder.put(new VisibleProperty(myGenerateRoundIconTitle), isLauncherIcon);
    layoutPropertiesBuilder.put(new VisibleProperty(myGenerateRoundIconRowPanel), isLauncherIcon);
    layoutPropertiesBuilder.put(new VisibleProperty(myGeneratePlayStoreIconTitle), isLauncherIcon);
    layoutPropertiesBuilder.put(new VisibleProperty(myGeneratePlayStoreIconRowPanel), isLauncherIcon);

    ImmutableMap<BoolProperty, ObservableValue<Boolean>> layoutProperties = layoutPropertiesBuilder.build();
    for (Map.Entry<BoolProperty, ObservableValue<Boolean>> entry : layoutProperties.entrySet()) {
      // Initialize everything off, as this makes sure the frame that uses this panel won't start
      // REALLY LARGE by default.
      entry.getKey().set(false);
      myGeneralBindings.bind(entry.getKey(), entry.getValue());
    }
    myListeners.listenAll(layoutProperties.keySet()).with(() -> {
      SwingUtilities.updateComponentTreeUI(myForegroundAllOptionsPanel);
      SwingUtilities.updateComponentTreeUI(myBackgroundAllOptionsPanel);
      SwingUtilities.updateComponentTreeUI(myOtherIconsAllOptionsPanel);
    });
  }

  private void initializeValidators() {
    // We use this property as a way to trigger the validation when the panel is shown/hidden
    // when the "output icon type" changes in our parent component.
    // For example, we only want to validate the API level (see below) if the user is trying
    // to create an adaptive icon (from this component).
    VisibleProperty isActive = new VisibleProperty(this);

    // Validate the API level when the panel is active.
    myValidatorPanel.registerTest(Expression.create(() -> !isActive.get() || myBuildSdkVersion.getFeatureLevel() >= 26, isActive),
                                  "Project must be built with SDK 26 or later to use adaptive icons");

    // Validate foreground and background layer names when the panel is active.
    myValidatorPanel.registerTest(nameIsNotEmptyExpression(isActive, myForegroundLayerName),
                                  "Foreground layer name must be set");
    myValidatorPanel.registerValidator(
      myForegroundLayerName, name -> Validator.Result.fromNullableMessage(myNameValidator.getErrorText(name.trim())));
    myValidatorPanel.registerTest(nameIsNotEmptyExpression(isActive, myBackgroundLayerName),
                                  "Background layer name must be set");
    myValidatorPanel.registerValidator(
      myBackgroundLayerName, name -> Validator.Result.fromNullableMessage(myNameValidator.getErrorText(name.trim())));
    myValidatorPanel.registerTest(namesAreDistinctExpression(isActive, myOutputName, myForegroundLayerName),
                                  "Foreground layer must have a name distinct from the icon name");
    myValidatorPanel.registerTest(namesAreDistinctExpression(isActive, myOutputName, myBackgroundLayerName),
                                  "Background layer must have a name distinct from the icon name");
    myValidatorPanel.registerTest(namesAreDistinctExpression(isActive, myForegroundLayerName, myBackgroundLayerName),
                                  "Background and foreground layers must have distinct names");

    myValidatorPanel.registerValidator(myForegroundAssetValidityState, validity -> validity);
    myValidatorPanel.registerValidator(myBackgroundAssetValidityState, validity -> validity);
  }

  private void setupUI() {
    myRootPanel = new JPanel();
    myRootPanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    myOutputNamePanelRow = new JPanel();
    myOutputNamePanelRow.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myRootPanel.add(myOutputNamePanelRow, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myOutputNamePanelRow.setBorder(
      BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                       TitledBorder.DEFAULT_POSITION, null, null));
    myOutputNameLabel = new JBLabel();
    myOutputNameLabel.setText("Name:");
    myOutputNamePanelRow.add(myOutputNameLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                    GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                                    new Dimension(80, -1), null, null, 0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    myOutputNamePanelRow.add(panel1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                         null, null, 0, false));
    myOutputNameTextField = new JTextField();
    myOutputNameTextField.setText("(name)");
    myOutputNameTextField.setToolTipText("The filename which will be used for these icons.");
    panel1.add(myOutputNameTextField, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBTabbedPane jBTabbedPane1 = new JBTabbedPane();
    jBTabbedPane1.setTabPlacement(1);
    myRootPanel.add(jBTabbedPane1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                       new Dimension(200, 200), null, 0, false));
    jBTabbedPane1.setBorder(
      BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                       TitledBorder.DEFAULT_POSITION, null, null));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    jBTabbedPane1.addTab("Foreground Layer", panel2);
    myForegroundScrollPane = new JBScrollPane();
    myForegroundScrollPane.setHorizontalScrollBarPolicy(31);
    panel2.add(myForegroundScrollPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                           null, null, null, 0, false));
    myForegroundAllOptionsPanel = new JPanel();
    myForegroundAllOptionsPanel.setLayout(new GridLayoutManager(6, 2, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundScrollPane.setViewportView(myForegroundAllOptionsPanel);
    myForegroundLayerNamePanel = new JPanel();
    myForegroundLayerNamePanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundLayerNamePanel.setVisible(true);
    myForegroundAllOptionsPanel.add(myForegroundLayerNamePanel,
                                    new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, true));
    myForegroundLayerNameLabel = new JBLabel();
    myForegroundLayerNameLabel.setText("Layer name:");
    myForegroundLayerNamePanel.add(myForegroundLayerNameLabel,
                                   new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                       new Dimension(70, -1), null, null, 0, false));
    final JPanel panel3 = new JPanel();
    panel3.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundLayerNamePanel.add(panel3, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               null, null, null, 0, false));
    myForegroundLayerNameTextField = new JTextField();
    myForegroundLayerNameTextField.setText("(name)");
    myForegroundLayerNameTextField.setToolTipText("The filename which will be used for these icons.");
    panel3.add(myForegroundLayerNameTextField,
               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    mySourceAssetTitleSeparator = new TitledSeparator();
    mySourceAssetTitleSeparator.setText("Source Asset");
    myForegroundAllOptionsPanel.add(mySourceAssetTitleSeparator,
                                    new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myForegroundAssetTypePanel = new JPanel();
    myForegroundAssetTypePanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundAllOptionsPanel.add(myForegroundAssetTypePanel,
                                    new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, true));
    myForegroundAssetTypeLabel = new JLabel();
    myForegroundAssetTypeLabel.setText("Asset type:");
    myForegroundAssetTypePanel.add(myForegroundAssetTypeLabel,
                                   new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                       new Dimension(70, -1), null, null, 1, false));
    myForegroundAssetRadioButtonsPanel = new JPanel();
    myForegroundAssetRadioButtonsPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundAssetTypePanel.add(myForegroundAssetRadioButtonsPanel,
                                   new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_VERTICAL,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myForegroundClipartRadioButton = new JRadioButton();
    myForegroundClipartRadioButton.setText("Clip art");
    myForegroundClipartRadioButton.setToolTipText("Select from a list of clipart choices to generate Android icons for your app.");
    myForegroundAssetRadioButtonsPanel.add(myForegroundClipartRadioButton,
                                           new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                               GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                               null, null, 0, false));
    myForegroundTextRadioButton = new JRadioButton();
    myForegroundTextRadioButton.setText("Text");
    myForegroundTextRadioButton.setToolTipText("Enter text which will be rendered into Android icons for your app.");
    myForegroundAssetRadioButtonsPanel.add(myForegroundTextRadioButton,
                                           new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                               GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                               null, null, 0, false));
    myForegroundImageRadioButton = new JRadioButton();
    myForegroundImageRadioButton.setText("Image");
    myForegroundImageRadioButton.setToolTipText(
      "Select an image, e.g. PNG, SVG, PSD, or a drawable from disk to generate Android icons for your app.");
    myForegroundAssetRadioButtonsPanel.add(myForegroundImageRadioButton,
                                           new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                               GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                               null, null, 0, false));
    myForegroundAssetTypeSourcePanel = new JPanel();
    myForegroundAssetTypeSourcePanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundAllOptionsPanel.add(myForegroundAssetTypeSourcePanel,
                                    new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, true));
    myForegroundImageAssetRowPanel = new JPanel();
    myForegroundImageAssetRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundAssetTypeSourcePanel.add(myForegroundImageAssetRowPanel,
                                         new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, true));
    myForegroundImagePathLabel = new JBLabel();
    myForegroundImagePathLabel.setText("Path:");
    myForegroundImageAssetRowPanel.add(myForegroundImagePathLabel,
                                       new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                           GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                           new Dimension(70, -1), null, null, 1, false));
    myForegroundImageAssetBrowser = new ImageAssetBrowser();
    myForegroundImageAssetRowPanel.add(myForegroundImageAssetBrowser,
                                       new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                           GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myForegroundClipartAssetRowPanel = new JPanel();
    myForegroundClipartAssetRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundAssetTypeSourcePanel.add(myForegroundClipartAssetRowPanel,
                                         new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, true));
    myForegroundClipartLabel = new JBLabel();
    myForegroundClipartLabel.setText("Clip art:");
    myForegroundClipartAssetRowPanel.add(myForegroundClipartLabel,
                                         new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                             new Dimension(70, -1), null, null, 1, false));
    myForegroundClipartAssetButton = new ClipartIconButton();
    myForegroundClipartAssetRowPanel.add(myForegroundClipartAssetButton,
                                         new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myForegroundTextAssetRowPanel = new JPanel();
    myForegroundTextAssetRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundAssetTypeSourcePanel.add(myForegroundTextAssetRowPanel,
                                         new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, true));
    myForegroundTextLabel = new JBLabel();
    myForegroundTextLabel.setText("Text:");
    myForegroundTextAssetRowPanel.add(myForegroundTextLabel,
                                      new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                          GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                          new Dimension(70, -1), null, null, 1, false));
    myForegroundTextAssetEditor = new MultiLineTextAssetEditor();
    myForegroundTextAssetRowPanel.add(myForegroundTextAssetEditor,
                                      new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myForegroundColorRowPanel = new JPanel();
    myForegroundColorRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundAssetTypeSourcePanel.add(myForegroundColorRowPanel,
                                         new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, true));
    myForegroundColorLabel = new JBLabel();
    myForegroundColorLabel.setText("Color:");
    myForegroundColorRowPanel.add(myForegroundColorLabel,
                                  new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                      new Dimension(70, -1), null, null, 1, false));
    myForegroundColorPanel = new ColorPanel();
    myForegroundColorPanel.setSelectedColor(new Color(-16777216));
    myForegroundColorRowPanel.add(myForegroundColorPanel,
                                  new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      new Dimension(78, -1), null, null, 0, false));
    myForegroundImageOptionsPanel = new JPanel();
    myForegroundImageOptionsPanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundAllOptionsPanel.add(myForegroundImageOptionsPanel,
                                    new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, true));
    myForegroundScalingTitleSeparator = new TitledSeparator();
    myForegroundScalingTitleSeparator.setText("Scaling");
    myForegroundImageOptionsPanel.add(myForegroundScalingTitleSeparator,
                                      new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myForegroundTrimPanel = new JPanel();
    myForegroundTrimPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundTrimPanel.setVisible(true);
    myForegroundImageOptionsPanel.add(myForegroundTrimPanel,
                                      new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                          null, null, 0, true));
    myForegroundTrimLabel = new JBLabel();
    myForegroundTrimLabel.setText("Trim:");
    myForegroundTrimPanel.add(myForegroundTrimLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                         GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                                         new Dimension(70, -1), null, null, 1, false));
    myForegroundTrimOptionsPanel = new JPanel();
    myForegroundTrimOptionsPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundTrimPanel.add(myForegroundTrimOptionsPanel,
                              new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                  null, 0, false));
    final Spacer spacer1 = new Spacer();
    myForegroundTrimOptionsPanel.add(spacer1,
                                     new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                         GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    myForegroundTrimYesRadioButton = new JRadioButton();
    myForegroundTrimYesRadioButton.setText("Yes");
    myForegroundTrimYesRadioButton.setToolTipText("Remove any transparent space from around your source asset before rendering to icon.");
    myForegroundTrimOptionsPanel.add(myForegroundTrimYesRadioButton,
                                     new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myForegroundTrimNoRadioButton = new JRadioButton();
    myForegroundTrimNoRadioButton.setSelected(true);
    myForegroundTrimNoRadioButton.setText("No");
    myForegroundTrimNoRadioButton.setToolTipText("Leave the original asset unmodified.");
    myForegroundTrimOptionsPanel.add(myForegroundTrimNoRadioButton,
                                     new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myForegroundResizePanel = new JPanel();
    myForegroundResizePanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundResizePanel.setVisible(true);
    myForegroundImageOptionsPanel.add(myForegroundResizePanel,
                                      new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                          null, null, 0, true));
    myForegroundResizeLabel = new JBLabel();
    myForegroundResizeLabel.setText("Resize:");
    myForegroundResizePanel.add(myForegroundResizeLabel,
                                new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                    GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                    new Dimension(70, -1), null, null, 1, false));
    myForegroundResizeSliderPanel = new JPanel();
    myForegroundResizeSliderPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundResizePanel.add(myForegroundResizeSliderPanel,
                                new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                    null, 0, false));
    myForegroundResizeSlider = new JSlider();
    myForegroundResizeSlider.setMaximum(400);
    myForegroundResizeSlider.setMinimum(0);
    myForegroundResizeSlider.setMinorTickSpacing(20);
    myForegroundResizeSlider.setPaintLabels(false);
    myForegroundResizeSlider.setPaintTicks(true);
    myForegroundResizeSlider.setSnapToTicks(false);
    myForegroundResizeSlider.setToolTipText(
      "Resize the original asset using the specified scaling factor (in percent). This happens after any trimming.");
    myForegroundResizeSlider.setValue(100);
    myForegroundResizeSlider.setValueIsAdjusting(false);
    myForegroundResizeSliderPanel.add(myForegroundResizeSlider,
                                      new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myForegroundResizeValueLabel = new JLabel();
    myForegroundResizeValueLabel.setHorizontalAlignment(4);
    myForegroundResizeValueLabel.setText("100 %");
    myForegroundResizeSliderPanel.add(myForegroundResizeValueLabel,
                                      new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                          new Dimension(40, -1), null, 0, false));
    final Spacer spacer2 = new Spacer();
    myForegroundAllOptionsPanel.add(spacer2,
                                    new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                        GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final JPanel panel4 = new JPanel();
    panel4.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    jBTabbedPane1.addTab("Background Layer", panel4);
    myBackgroundScrollPane = new JBScrollPane();
    myBackgroundScrollPane.setHorizontalScrollBarPolicy(31);
    panel4.add(myBackgroundScrollPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                           null, null, null, 0, false));
    myBackgroundAllOptionsPanel = new JPanel();
    myBackgroundAllOptionsPanel.setLayout(new GridLayoutManager(6, 2, new Insets(0, 0, 0, 0), -1, -1));
    myBackgroundScrollPane.setViewportView(myBackgroundAllOptionsPanel);
    myBackgroundLayerNamePanel = new JPanel();
    myBackgroundLayerNamePanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myBackgroundLayerNamePanel.setVisible(true);
    myBackgroundAllOptionsPanel.add(myBackgroundLayerNamePanel,
                                    new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, true));
    myBackgroundLayerNameLabel = new JBLabel();
    myBackgroundLayerNameLabel.setText("Layer name:");
    myBackgroundLayerNamePanel.add(myBackgroundLayerNameLabel,
                                   new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                       new Dimension(70, -1), null, null, 0, false));
    final JPanel panel5 = new JPanel();
    panel5.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    myBackgroundLayerNamePanel.add(panel5, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               null, null, null, 0, false));
    myBackgroundLayerNameTextField = new JTextField();
    myBackgroundLayerNameTextField.setText("(name)");
    myBackgroundLayerNameTextField.setToolTipText("The filename which will be used for these icons.");
    panel5.add(myBackgroundLayerNameTextField,
               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myBackgroundSourceAssetTitleSeparator = new TitledSeparator();
    myBackgroundSourceAssetTitleSeparator.setText("Source Asset");
    myBackgroundAllOptionsPanel.add(myBackgroundSourceAssetTitleSeparator,
                                    new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myBackgroundAssetTypePanel = new JPanel();
    myBackgroundAssetTypePanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myBackgroundAllOptionsPanel.add(myBackgroundAssetTypePanel,
                                    new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, true));
    myBackgroundAssetTypeLabel = new JLabel();
    myBackgroundAssetTypeLabel.setText("Asset type:");
    myBackgroundAssetTypePanel.add(myBackgroundAssetTypeLabel,
                                   new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                       new Dimension(70, -1), null, null, 1, false));
    myBackgroundAssetRadioButtonsPanel = new JPanel();
    myBackgroundAssetRadioButtonsPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myBackgroundAssetTypePanel.add(myBackgroundAssetRadioButtonsPanel,
                                   new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_VERTICAL,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                       null, null, 0, false));
    myBackgroundImageRadioButton = new JRadioButton();
    myBackgroundImageRadioButton.setText("Image");
    myBackgroundImageRadioButton.setToolTipText(
      "Select an image, e.g. PNG, SVG, PSD, or a drawable from disk to generate Android icons for your app.");
    myBackgroundAssetRadioButtonsPanel.add(myBackgroundImageRadioButton,
                                           new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myBackgroundColorRadioButton = new JRadioButton();
    myBackgroundColorRadioButton.setText("Color");
    myBackgroundColorRadioButton.setToolTipText("Select from a background color for the Android icons for your app.");
    myBackgroundAssetRadioButtonsPanel.add(myBackgroundColorRadioButton,
                                           new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myBackgroundAssetTypeSourcePanel = new JPanel();
    myBackgroundAssetTypeSourcePanel.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
    myBackgroundAllOptionsPanel.add(myBackgroundAssetTypeSourcePanel,
                                    new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, true));
    myBackgroundImageAssetRowPanel = new JPanel();
    myBackgroundImageAssetRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myBackgroundAssetTypeSourcePanel.add(myBackgroundImageAssetRowPanel,
                                         new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, new Dimension(30, 30), null, null, 0, true));
    myBackgroundImagePathLabel = new JBLabel();
    myBackgroundImagePathLabel.setText("Path:");
    myBackgroundImageAssetRowPanel.add(myBackgroundImagePathLabel,
                                       new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                           GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                           new Dimension(70, -1), null, null, 1, false));
    myBackgroundImageAssetBrowser = new ImageAssetBrowser();
    myBackgroundImageAssetRowPanel.add(myBackgroundImageAssetBrowser,
                                       new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myBackgroundColorRowPanel = new JPanel();
    myBackgroundColorRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myBackgroundAssetTypeSourcePanel.add(myBackgroundColorRowPanel,
                                         new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, new Dimension(30, 30), null, null, 0, true));
    myBackgroundColorLabel = new JBLabel();
    myBackgroundColorLabel.setText("Color:");
    myBackgroundColorRowPanel.add(myBackgroundColorLabel,
                                  new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                      new Dimension(70, -1), null, null, 1, false));
    myBackgroundColorPanel = new ColorPanel();
    myBackgroundColorPanel.setSelectedColor(new Color(-16777216));
    myBackgroundColorRowPanel.add(myBackgroundColorPanel,
                                  new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_FIXED, new Dimension(78, -1), null, null, 0, false));
    myBackgroundScalingTitleSeparator = new TitledSeparator();
    myBackgroundScalingTitleSeparator.setText("Scaling");
    myBackgroundAssetTypeSourcePanel.add(myBackgroundScalingTitleSeparator,
                                         new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myBackgroundImageOptionsPanel = new JPanel();
    myBackgroundImageOptionsPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
    myBackgroundAllOptionsPanel.add(myBackgroundImageOptionsPanel,
                                    new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, true));
    myBackgroundTrimRowPanel = new JPanel();
    myBackgroundTrimRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myBackgroundTrimRowPanel.setVisible(true);
    myBackgroundImageOptionsPanel.add(myBackgroundTrimRowPanel,
                                      new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                          null, null, 0, true));
    myBackgroundTrimLabel = new JBLabel();
    myBackgroundTrimLabel.setText("Trim:");
    myBackgroundTrimRowPanel.add(myBackgroundTrimLabel,
                                 new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                     new Dimension(70, -1), null, null, 1, false));
    myBackgroundTrimOptionsPanel = new JPanel();
    myBackgroundTrimOptionsPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    myBackgroundTrimRowPanel.add(myBackgroundTrimOptionsPanel,
                                 new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                     null, null, 0, false));
    final Spacer spacer3 = new Spacer();
    myBackgroundTrimOptionsPanel.add(spacer3,
                                     new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                         GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    myBackgroundTrimYesRadioButton = new JRadioButton();
    myBackgroundTrimYesRadioButton.setText("Yes");
    myBackgroundTrimYesRadioButton.setToolTipText("Remove any transparent space from around your source asset before rendering to icon.");
    myBackgroundTrimOptionsPanel.add(myBackgroundTrimYesRadioButton,
                                     new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myBackgroundTrimNoRadioButton = new JRadioButton();
    myBackgroundTrimNoRadioButton.setSelected(true);
    myBackgroundTrimNoRadioButton.setText("No");
    myBackgroundTrimNoRadioButton.setToolTipText("Leave the original asset unmodified.");
    myBackgroundTrimOptionsPanel.add(myBackgroundTrimNoRadioButton,
                                     new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myBackgroundResizeRowPanel = new JPanel();
    myBackgroundResizeRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myBackgroundResizeRowPanel.setVisible(true);
    myBackgroundImageOptionsPanel.add(myBackgroundResizeRowPanel,
                                      new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                          null, null, 0, true));
    myBackgroundResizeLabel = new JBLabel();
    myBackgroundResizeLabel.setText("Resize:");
    myBackgroundResizeRowPanel.add(myBackgroundResizeLabel,
                                   new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                       new Dimension(70, -1), null, null, 1, false));
    myBackgroundResizeSliderPanel = new JPanel();
    myBackgroundResizeSliderPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myBackgroundResizeRowPanel.add(myBackgroundResizeSliderPanel,
                                   new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                       null, null, 0, false));
    myBackgroundResizeSlider = new JSlider();
    myBackgroundResizeSlider.setMaximum(400);
    myBackgroundResizeSlider.setMinimum(0);
    myBackgroundResizeSlider.setMinorTickSpacing(20);
    myBackgroundResizeSlider.setPaintLabels(false);
    myBackgroundResizeSlider.setPaintTicks(true);
    myBackgroundResizeSlider.setSnapToTicks(false);
    myBackgroundResizeSlider.setToolTipText(
      "Resize the original asset using the specified scaling factor (in percent). This happens after any trimming.");
    myBackgroundResizeSlider.setValue(100);
    myBackgroundResizeSliderPanel.add(myBackgroundResizeSlider,
                                      new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myBackgroundResizeValueLabel = new JLabel();
    myBackgroundResizeValueLabel.setHorizontalAlignment(4);
    myBackgroundResizeValueLabel.setText("100 %");
    myBackgroundResizeSliderPanel.add(myBackgroundResizeValueLabel,
                                      new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                          new Dimension(40, -1), null, 0, false));
    final Spacer spacer4 = new Spacer();
    myBackgroundAllOptionsPanel.add(spacer4,
                                    new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                        GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    final JPanel panel6 = new JPanel();
    panel6.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    jBTabbedPane1.addTab("Options", panel6);
    myOtherIconsScrollPane = new JBScrollPane();
    myOtherIconsScrollPane.setHorizontalScrollBarPolicy(31);
    panel6.add(myOtherIconsScrollPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                           null, null, null, 0, false));
    myOtherIconsAllOptionsPanel = new JPanel();
    myOtherIconsAllOptionsPanel.setLayout(new GridLayoutManager(10, 2, new Insets(0, 0, 0, 0), -1, -1));
    myOtherIconsScrollPane.setViewportView(myOtherIconsAllOptionsPanel);
    myOtherIconsAllOptionsPanel.setBorder(
      BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                       TitledBorder.DEFAULT_POSITION, null, null));
    final TitledSeparator titledSeparator1 = new TitledSeparator();
    titledSeparator1.setText("Legacy Icon (API ≤ 25)");
    myOtherIconsAllOptionsPanel.add(titledSeparator1,
                                    new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                        null, 0, false));
    myGenerateLegacyIconRowPanel = new JPanel();
    myGenerateLegacyIconRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myGenerateLegacyIconRowPanel.setVisible(true);
    myOtherIconsAllOptionsPanel.add(myGenerateLegacyIconRowPanel,
                                    new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, true));
    myGenerateLegacyIconLabel = new JBLabel();
    myGenerateLegacyIconLabel.setText("Generate:");
    myGenerateLegacyIconRowPanel.add(myGenerateLegacyIconLabel,
                                     new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                         new Dimension(70, -1), null, null, 1, false));
    mGenerateLegacyIconRadioButtonsPanel = new JPanel();
    mGenerateLegacyIconRadioButtonsPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    myGenerateLegacyIconRowPanel.add(mGenerateLegacyIconRadioButtonsPanel,
                                     new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                         null, null, 0, false));
    myGenerateLegacyIconYesRadioButton = new JRadioButton();
    myGenerateLegacyIconYesRadioButton.setSelected(true);
    myGenerateLegacyIconYesRadioButton.setText("Yes");
    myGenerateLegacyIconYesRadioButton.setToolTipText("Generate legacy icon (API ≤ 25)");
    mGenerateLegacyIconRadioButtonsPanel.add(myGenerateLegacyIconYesRadioButton,
                                             new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                 null, null, null, 0, false));
    final JRadioButton radioButton1 = new JRadioButton();
    radioButton1.setSelected(false);
    radioButton1.setText("No");
    radioButton1.setToolTipText("");
    mGenerateLegacyIconRadioButtonsPanel.add(radioButton1,
                                             new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                 null, null, null, 0, false));
    final Spacer spacer5 = new Spacer();
    mGenerateLegacyIconRadioButtonsPanel.add(spacer5,
                                             new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                                 GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    myLegacyIconShapeRowPanel = new JPanel();
    myLegacyIconShapeRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myOtherIconsAllOptionsPanel.add(myLegacyIconShapeRowPanel,
                                    new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, true));
    myLegacyIconShapeLabel = new JBLabel();
    myLegacyIconShapeLabel.setText("Shape:");
    myLegacyIconShapeRowPanel.add(myLegacyIconShapeLabel,
                                  new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                      new Dimension(70, -1), null, null, 1, false));
    myLegacyIconShapePanel = new JPanel();
    myLegacyIconShapePanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myLegacyIconShapeRowPanel.add(myLegacyIconShapePanel,
                                  new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                      null, null, 0, false));
    myLegacyIconShapeComboBox = new JComboBox();
    myLegacyIconShapeComboBox.setToolTipText("The shape of the launcher icon's backdrop.");
    myLegacyIconShapePanel.add(myLegacyIconShapeComboBox,
                               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null,
                                                   0, false));
    final Spacer spacer6 = new Spacer();
    myLegacyIconShapePanel.add(spacer6, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                            GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    myGenerateRoundIconTitle = new TitledSeparator();
    myGenerateRoundIconTitle.setText("Round Icon (API = 25)");
    myOtherIconsAllOptionsPanel.add(myGenerateRoundIconTitle,
                                    new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                        null, 0, false));
    myGenerateRoundIconRowPanel = new JPanel();
    myGenerateRoundIconRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myGenerateRoundIconRowPanel.setVisible(true);
    myOtherIconsAllOptionsPanel.add(myGenerateRoundIconRowPanel,
                                    new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, true));
    myGenerateRoundIconLabel = new JBLabel();
    myGenerateRoundIconLabel.setText("Generate:");
    myGenerateRoundIconRowPanel.add(myGenerateRoundIconLabel,
                                    new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                        new Dimension(70, -1), null, null, 1, false));
    myGenerateRoundIconRadioButtonsPanel = new JPanel();
    myGenerateRoundIconRadioButtonsPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    myGenerateRoundIconRowPanel.add(myGenerateRoundIconRadioButtonsPanel,
                                    new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, false));
    myGenerateRoundIconYesRadioButton = new JRadioButton();
    myGenerateRoundIconYesRadioButton.setSelected(true);
    myGenerateRoundIconYesRadioButton.setText("Yes");
    myGenerateRoundIconYesRadioButton.setToolTipText("Generate round icon (API 25)");
    myGenerateRoundIconRadioButtonsPanel.add(myGenerateRoundIconYesRadioButton,
                                             new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                 null, null, null, 0, false));
    final JRadioButton radioButton2 = new JRadioButton();
    radioButton2.setSelected(false);
    radioButton2.setText("No");
    radioButton2.setToolTipText("");
    myGenerateRoundIconRadioButtonsPanel.add(radioButton2,
                                             new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                 null, null, null, 0, false));
    final Spacer spacer7 = new Spacer();
    myGenerateRoundIconRadioButtonsPanel.add(spacer7,
                                             new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                                 GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    myGeneratePlayStoreIconTitle = new TitledSeparator();
    myGeneratePlayStoreIconTitle.setText("Google Play Store Icon");
    myOtherIconsAllOptionsPanel.add(myGeneratePlayStoreIconTitle,
                                    new GridConstraints(5, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                        null, 0, false));
    myGeneratePlayStoreIconRowPanel = new JPanel();
    myGeneratePlayStoreIconRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myGeneratePlayStoreIconRowPanel.setVisible(true);
    myOtherIconsAllOptionsPanel.add(myGeneratePlayStoreIconRowPanel,
                                    new GridConstraints(6, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, true));
    myGeneratePlayStoreIconLabel = new JBLabel();
    myGeneratePlayStoreIconLabel.setText("Generate:");
    myGeneratePlayStoreIconRowPanel.add(myGeneratePlayStoreIconLabel,
                                        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                            new Dimension(70, -1), null, null, 1, false));
    myGeneratePlayStoreIconRadioButtonsPanel = new JPanel();
    myGeneratePlayStoreIconRadioButtonsPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    myGeneratePlayStoreIconRowPanel.add(myGeneratePlayStoreIconRadioButtonsPanel,
                                        new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            null, null, null, 0, false));
    myGeneratePlayStoreIconYesRadioButton = new JRadioButton();
    myGeneratePlayStoreIconYesRadioButton.setSelected(true);
    myGeneratePlayStoreIconYesRadioButton.setText("Yes");
    myGeneratePlayStoreIconYesRadioButton.setToolTipText("Generate icon for Google Play Store (512x512)");
    myGeneratePlayStoreIconRadioButtonsPanel.add(myGeneratePlayStoreIconYesRadioButton,
                                                 new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                     GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                     null, null, null, 0, false));
    final JRadioButton radioButton3 = new JRadioButton();
    radioButton3.setSelected(false);
    radioButton3.setText("No");
    radioButton3.setToolTipText("");
    myGeneratePlayStoreIconRadioButtonsPanel.add(radioButton3,
                                                 new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                     GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                     null, null, null, 0, false));
    final Spacer spacer8 = new Spacer();
    myGeneratePlayStoreIconRadioButtonsPanel.add(spacer8, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER,
                                                                              GridConstraints.FILL_HORIZONTAL,
                                                                              GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0,
                                                                              false));
    final Spacer spacer9 = new Spacer();
    myOtherIconsAllOptionsPanel.add(spacer9,
                                    new GridConstraints(9, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                        GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myIconFormatTitle = new TitledSeparator();
    myIconFormatTitle.setText("Icon Format");
    myOtherIconsAllOptionsPanel.add(myIconFormatTitle,
                                    new GridConstraints(7, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                        null, 0, false));
    myIconFormatRowPanel = new JPanel();
    myIconFormatRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myOtherIconsAllOptionsPanel.add(myIconFormatRowPanel,
                                    new GridConstraints(8, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, true));
    myIconFormatLabel = new JBLabel();
    myIconFormatLabel.setText("Format:");
    myIconFormatRowPanel.add(myIconFormatLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                    GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                                    new Dimension(70, -1), null, null, 1, false));
    myIconFormatRadioButtonsPanel = new JPanel();
    myIconFormatRadioButtonsPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    myIconFormatRowPanel.add(myIconFormatRadioButtonsPanel,
                             new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                 null, 0, false));
    myIconFormatWebpRadioButton = new JRadioButton();
    myIconFormatWebpRadioButton.setActionCommand("WebP");
    myIconFormatWebpRadioButton.setLabel("WebP");
    myIconFormatWebpRadioButton.setSelected(true);
    myIconFormatWebpRadioButton.setText("WebP");
    myIconFormatWebpRadioButton.setToolTipText("Generate WebP icons");
    myIconFormatRadioButtonsPanel.add(myIconFormatWebpRadioButton,
                                      new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JRadioButton radioButton4 = new JRadioButton();
    radioButton4.setActionCommand("PNG");
    radioButton4.setLabel("PNG");
    radioButton4.setText("PNG");
    radioButton4.setToolTipText("Generate PNG icons");
    myIconFormatRadioButtonsPanel.add(radioButton4, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                        GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final Spacer spacer10 = new Spacer();
    myIconFormatRadioButtonsPanel.add(spacer10,
                                      new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    ButtonGroup buttonGroup;
    buttonGroup = new ButtonGroup();
    buttonGroup.add(myForegroundImageRadioButton);
    buttonGroup.add(myForegroundClipartRadioButton);
    buttonGroup.add(myForegroundTextRadioButton);
    buttonGroup = new ButtonGroup();
    buttonGroup.add(myForegroundTrimNoRadioButton);
    buttonGroup.add(myForegroundTrimYesRadioButton);
    buttonGroup = new ButtonGroup();
    buttonGroup.add(radioButton1);
    buttonGroup.add(myGenerateLegacyIconYesRadioButton);
    buttonGroup = new ButtonGroup();
    buttonGroup.add(myBackgroundImageRadioButton);
    buttonGroup.add(myBackgroundColorRadioButton);
    buttonGroup = new ButtonGroup();
    buttonGroup.add(myBackgroundTrimNoRadioButton);
    buttonGroup.add(myBackgroundTrimYesRadioButton);
    buttonGroup = new ButtonGroup();
    buttonGroup.add(radioButton2);
    buttonGroup.add(myGenerateRoundIconYesRadioButton);
    buttonGroup = new ButtonGroup();
    buttonGroup.add(myGeneratePlayStoreIconYesRadioButton);
    buttonGroup.add(radioButton3);
    buttonGroup = new ButtonGroup();
    buttonGroup.add(myIconFormatWebpRadioButton);
    buttonGroup.add(radioButton4);
  }

  @NotNull
  private static Expression<Boolean> nameIsNotEmptyExpression(@NotNull VisibleProperty isActive, @NotNull StringProperty name) {
    return Expression.create(() -> !isActive.get() || !StringUtil.isEmptyOrSpaces(name.get()), isActive, name);
  }

  @NotNull
  private static Expression<Boolean> namesAreDistinctExpression(@NotNull VisibleProperty isActive,
                                                                @NotNull StringProperty name1, @NotNull StringProperty name2) {
    return Expression.create(() -> !isActive.get() || !StringUtil.equalsTrimWhitespaces(name1.get(), name2.get()), isActive, name1, name2);
  }

  /**
   * Returns an icon generator which will create Android icons using the panel's current settings.
   */
  @Override
  @NotNull
  public AdaptiveIconGenerator getIconGenerator() {
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
    ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null);
    for (ActionListener assetListener : myAssetListeners) {
      assetListener.actionPerformed(e);
    }
  }

  private void updateBindingsAndUiForActiveIconType() {
    myOutputName.set(myDefaultOutputName);
    myForegroundLayerName.set(defaultForegroundLayerName());
    myBackgroundLayerName.set(defaultBackgroundLayerName());

    myGeneralBindings.bind(myIconGenerator.sourceAsset(), new AsOptionalExpression<>(myForegroundActiveAsset));
    myGeneralBindings.bind(myIconGenerator.outputName(), myOutputName);
    myGeneralBindings.bindTwoWay(myIconGenerator.backgroundImageAsset(), myBackgroundImageAsset);

    myGeneralBindings.bindTwoWay(myIconGenerator.backgroundColor(), myBackgroundColor);
    myGeneralBindings.bindTwoWay(myIconGenerator.showSafeZone(), myShowSafeZone);
    myGeneralBindings.bindTwoWay(myIconGenerator.generateLegacyIcon(), myGenerateLegacyIcon);
    if (myIconGenerator instanceof LauncherIconGenerator) {
      LauncherIconGenerator iconGenerator = (LauncherIconGenerator)myIconGenerator;
      myGeneralBindings.bindTwoWay(iconGenerator.generateRoundIcon(), myGenerateRoundIcon);
      myGeneralBindings.bindTwoWay(iconGenerator.generatePlayStoreIcon(), myGeneratePlayStoreIcon);
      myGeneralBindings.bindTwoWay(iconGenerator.generateWebpIcons(), myGenerateWebpIcons);
      myGeneralBindings.bindTwoWay(iconGenerator.legacyIconShape(), myLegacyIconShape);
      myGeneralBindings.bindTwoWay(iconGenerator.showGrid(), myShowGrid);
      myGeneralBindings.bindTwoWay(iconGenerator.previewDensity(), myPreviewDensity);
    }
    myGeneralBindings.bindTwoWay(myIconGenerator.foregroundLayerName(), myForegroundLayerName);
    myGeneralBindings.bindTwoWay(myIconGenerator.backgroundLayerName(), myBackgroundLayerName);
  }

  @Override
  public void dispose() {
    myGeneralBindings.releaseAll();
    myForegroundActiveAssetBindings.releaseAll();
    myBackgroundActiveAssetBindings.releaseAll();
    myListeners.releaseAll();
    myAssetListeners.clear();
  }

  @NotNull
  private String defaultForegroundLayerName() {
    return myOutputName.get() + "_foreground";
  }

  @NotNull
  private String defaultBackgroundLayerName() {
    return myOutputName.get() + "_background";
  }

  private enum ForegroundAssetType {
    IMAGE,
    CLIP_ART,
    TEXT,
  }

  private enum BackgroundAssetType {
    IMAGE,
    COLOR,
  }
}
