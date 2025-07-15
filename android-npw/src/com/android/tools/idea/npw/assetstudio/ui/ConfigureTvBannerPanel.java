/*
 * Copyright (C) 2019 The Android Open Source Project
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
import static com.android.tools.idea.npw.assetstudio.ui.SliderUtils.bindTwoWay;
import static com.android.tools.idea.npw.assetstudio.ui.SliderUtils.inRange;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.model.StudioAndroidModuleInfo;
import com.android.tools.idea.npw.assetstudio.TvBannerGenerator;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.npw.assetstudio.assets.TextAsset;
import com.android.tools.idea.npw.assetstudio.icon.AndroidIconType;
import com.android.tools.idea.npw.assetstudio.wizard.PersistentState;
import com.android.tools.idea.npw.assetstudio.wizard.PersistentStateUtil;
import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.InvalidationListener;
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
import com.android.tools.idea.observable.ui.ColorProperty;
import com.android.tools.idea.observable.ui.EnabledProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.SelectedRadioButtonProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.observable.ui.VisibleProperty;
import com.android.tools.idea.rendering.DrawableRenderer;
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.android.tools.module.AndroidModuleInfo;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
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
@SuppressWarnings("UseJBColor") // Android icons don't need JBColor.
public class ConfigureTvBannerPanel extends JPanel implements Disposable, ConfigureIconView, PersistentStateComponent<PersistentState> {
  private static final boolean HIDE_INAPPLICABLE_CONTROLS = false; // TODO Decide on hiding or disabling.

  private static final Color DEFAULT_FOREGROUND_COLOR = Color.BLACK;
  private static final File DEFAULT_FOREGROUND_IMAGE = getBundledImage("asset_studio", "ic_banner_image.xml");
  private static final BackgroundAssetType DEFAULT_BACKGROUND_ASSET_TYPE = BackgroundAssetType.COLOR;
  private static final String DEFAULT_OUTPUT_NAME = AndroidIconType.TV_BANNER.toOutputName("");

  private static final String BACKGROUND_ASSET_TYPE_PROPERTY = "backgroundAssetType";
  private static final String BACKGROUND_COLOR_PROPERTY = "backgroundColor";
  private static final String GENERATE_LEGACY_ICON_PROPERTY = "generateLegacyIcon";
  private static final String OUTPUT_NAME_PROPERTY = "outputName";
  private static final String FOREGROUND_LAYER_NAME_PROPERTY = "foregroundLayerName";
  private static final String BACKGROUND_LAYER_NAME_PROPERTY = "backgroundLayerName";
  private static final String FOREGROUND_IMAGE_PROPERTY = "foregroundImage";
  private static final String FOREGROUND_TEXT_PROPERTY = "foregroundText";
  private static final String BACKGROUND_IMAGE_PROPERTY = "backgroundImage";

  /**
   * This panel presents panels for configuring image and text asset.
   */
  private JPanel myRootPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myOutputNamePanelRow;
  private JBLabel myOutputNameLabel;
  private JTextField myOutputNameTextField;

  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundImageSourcePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundTextAssetRowPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundColorRowPanel;
  private JPanel myForegroundAllOptionsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundImageResizeSliderPanel;
  private JSlider myForegroundImageResizeSlider;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JTextField myForegroundImageResizeValueTextField;
  private JLabel myForegroundImageResizeValueLabel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundTextResizeSliderPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JSlider myForegroundTextResizeSlider;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JTextField myForegroundTextResizeValueTextField;
  private JLabel myForegroundTextResizeValueLabel;
  private JTextField myForegroundLayerNameTextField;
  private ColorPanel myForegroundColorPanel;
  private JBScrollPane myForegroundScrollPane;
  private ImageAssetBrowser myForegroundImageAssetBrowser;
  private JBLabel myForegroundLayerNameLabel;
  private JBLabel myForegroundImagePathLabel;
  private JBLabel myForegroundTextResizeLabel;
  private JBLabel myForegroundColorLabel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JBLabel myForegroundTextLabel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundImageResizePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JBLabel myForegroundImageResizeLabel;
  private MultiLineTextAssetEditor myForegroundTextAssetEditor;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundLayerNamePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundImageAssetPanel;
  private JPanel myForegroundTextOptionsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private TitledSeparator myForegroundTextTitleSeparator;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private TitledSeparator myForegroundImageTitleSeparator;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundTextResizePanel;

  private JPanel myBackgroundAllOptionsPanel;
  private JRadioButton myBackgroundImageRadioButton;
  private JRadioButton myBackgroundColorRadioButton;
  private JSlider myBackgroundResizeSlider;
  private JTextField myBackgroundResizeValueTextField;
  private JLabel myBackgroundResizeValueLabel;
  private JPanel myBackgroundAssetRadioButtonsPanel;
  private JPanel myBackgroundResizeSliderPanel;
  private JTextField myBackgroundLayerNameTextField;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myBackgroundResizeRowPanel;
  private JPanel myBackgroundColorRowPanel;
  private ColorPanel myBackgroundColorPanel;
  private JBScrollPane myBackgroundScrollPane;
  private JPanel myBackgroundImageAssetRowPanel;
  private ImageAssetBrowser myBackgroundImageAssetBrowser;
  private JBLabel myBackgroundLayerNameLabel;
  private JLabel myBackgroundAssetTypeLabel;
  private JBLabel myBackgroundImagePathLabel;
  private JBLabel myBackgroundResizeLabel;
  private JBLabel myBackgroundColorLabel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myBackgroundLayerNamePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myBackgroundAssetTypePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myBackgroundAssetTypeSourcePanel;
  private JPanel myBackgroundImageOptionsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private TitledSeparator myBackgroundScalingTitleSeparator;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private TitledSeparator myBackgroundSourceAssetTitleSeparator;

  private JBScrollPane myOtherIconsScrollPane;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myOtherIconsAllOptionsPanel;
  private JPanel mGenerateLegacyIconRadioButtonsPanel;
  private JRadioButton myGenerateLegacyIconYesRadioButton;
  private JBLabel myGenerateLegacyIconLabel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myGenerateLegacyIconRowPanel;

  @NotNull private final AndroidVersion myBuildSdkVersion;
  @NotNull private final TvBannerGenerator myIconGenerator;
  @NotNull private final ValidatorPanel myValidatorPanel;

  @NotNull private final BindingsManager myGeneralBindings = new BindingsManager();
  @NotNull private final BindingsManager myForegroundActiveAssetBindings = new BindingsManager();
  @NotNull private final BindingsManager myBackgroundActiveAssetBindings = new BindingsManager();
  @NotNull private final ListenerManager myListeners = new ListenerManager();
  @NotNull private final List<ActionListener> myAssetListeners = new ArrayList<>(1);

  @NotNull private final StringProperty myOutputName;
  @NotNull private final StringProperty myForegroundLayerName;
  @NotNull private final StringProperty myBackgroundLayerName;
  @NotNull private final ObjectProperty<BaseAsset> myForegroundActiveAsset;
  @NotNull private final OptionalProperty<ImageAsset> myBackgroundImageAsset;
  @NotNull private final ObjectProperty<Validator.Result> myForegroundAssetValidityState = new ObjectValueProperty<>(Validator.Result.OK);
  @NotNull private final ObjectProperty<Validator.Result> myBackgroundAssetValidityState = new ObjectValueProperty<>(Validator.Result.OK);
  @NotNull private final AbstractProperty<BackgroundAssetType> myBackgroundAssetType;
  private ColorProperty myForegroundTextColor;
  private AbstractProperty<Color> myBackgroundColor;
  private IntProperty myForegroundImageResizePercent;
  private IntProperty myForegroundTextResizePercent;
  private IntProperty myBackgroundResizePercent;
  private BoolProperty myGenerateLegacyIcon;
  private final AndroidFacet myFacet;
  @NotNull private final IdeResourceNameValidator myNameValidator = IdeResourceNameValidator.forFilename(ResourceFolderType.DRAWABLE);

  /**
   * Initializes a panel which can generate Android launcher icons. The supported types passed in
   * will be presented to the user in a combo box (unless there's only one supported type). If no
   * supported types are passed in, then all types will be supported by default.
   */
  public ConfigureTvBannerPanel(@NotNull Disposable disposableParent,
                                @NotNull AndroidFacet facet,
                                @NotNull ValidatorPanel validatorPanel,
                                @Nullable DrawableRenderer renderer) {
    super(new BorderLayout());
    setupUI();

    myFacet = facet;
    AndroidModuleInfo androidModuleInfo = StudioAndroidModuleInfo.getInstance(facet);
    AndroidVersion buildSdkVersion = androidModuleInfo.getBuildSdkVersion();
    myBuildSdkVersion = buildSdkVersion != null ? buildSdkVersion : new AndroidVersion(26);

    myForegroundImageAssetBrowser.getAsset().setDefaultImagePath(DEFAULT_FOREGROUND_IMAGE);

    myIconGenerator = new TvBannerGenerator(facet.getModule().getProject(), androidModuleInfo.getMinSdkVersion().getApiLevel(), renderer);
    myValidatorPanel = validatorPanel;

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
        myGeneralBindings.bind(myForegroundLayerName, Expression.create(() -> defaultForegroundLayerName(), myOutputName));
      }
      else {
        myGeneralBindings.release(myForegroundLayerName);
      }
    });
    myListeners.listen(myBackgroundLayerName, name -> {
      if (name.equals(defaultBackgroundLayerName())) {
        myGeneralBindings.bind(myBackgroundLayerName, Expression.create(() -> defaultBackgroundLayerName(), myOutputName));
      }
      else {
        myGeneralBindings.release(myBackgroundLayerName);
      }
    });

    myForegroundImageAssetBrowser.getAsset().imagePath().setValue(DEFAULT_FOREGROUND_IMAGE);

    // Call "setLabelFor" in code instead of designer since designer is so inconsistent about
    // valid targets.
    myOutputNameLabel.setLabelFor(myOutputNameTextField);

    myForegroundLayerNameLabel.setLabelFor(myForegroundLayerNameTextField);
    myForegroundImagePathLabel.setLabelFor(myForegroundImageAssetBrowser);
    myForegroundTextResizeLabel.setLabelFor(myForegroundTextResizeSliderPanel);
    myForegroundColorLabel.setLabelFor(myForegroundColorPanel);
    myGenerateLegacyIconLabel.setLabelFor(mGenerateLegacyIconRadioButtonsPanel);

    myBackgroundLayerNameLabel.setLabelFor(myBackgroundLayerNameTextField);
    myBackgroundAssetTypeLabel.setLabelFor(myBackgroundAssetRadioButtonsPanel);
    myBackgroundImagePathLabel.setLabelFor(myBackgroundImageAssetBrowser);
    myBackgroundResizeLabel.setLabelFor(myBackgroundResizeSliderPanel);
    myBackgroundColorLabel.setLabelFor(myBackgroundColorPanel);

    myForegroundActiveAsset = new ObjectValueProperty<>(myForegroundImageAssetBrowser.getAsset());
    myForegroundImageAssetBrowser.getAsset().setRole("foreground image");
    myForegroundColorPanel.setSelectedColor(DEFAULT_FOREGROUND_COLOR);

    myBackgroundAssetType = new SelectedRadioButtonProperty<>(DEFAULT_BACKGROUND_ASSET_TYPE, BackgroundAssetType.values(),
                                                              myBackgroundImageRadioButton, myBackgroundColorRadioButton);
    myBackgroundImageAsset = OptionalValueProperty.fromNullable(myBackgroundAssetType.get() == BackgroundAssetType.IMAGE ?
                                                                myBackgroundImageAssetBrowser.getAsset() : null);
    myBackgroundImageAssetBrowser.getAsset().setRole("background image");
    myBackgroundColorPanel.setSelectedColor(myIconGenerator.backgroundColor().get());

    InvalidationListener onTextChanged = () -> {
      ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, null);
      for (ActionListener listener : myAssetListeners) {
        listener.actionPerformed(e);
      }
    };

    TextAsset textAsset = myForegroundTextAssetEditor.getAsset();
    textAsset.text().addListener(onTextChanged);
    textAsset.fontFamily().addListener(onTextChanged);

    initializeListenersAndBindings();
    initializeValidators();

    Disposer.register(disposableParent, this);
    Disposer.register(this, myForegroundImageAssetBrowser);
    Disposer.register(this, myBackgroundImageAssetBrowser);
    Disposer.register(this, myIconGenerator);

    add(myRootPanel);
  }

  @Override
  @NotNull
  public PersistentState getState() {
    PersistentState state = new PersistentState();
    state.setChild(FOREGROUND_IMAGE_PROPERTY, myForegroundImageAssetBrowser.getAsset().getState());
    state.setChild(FOREGROUND_TEXT_PROPERTY, myForegroundTextAssetEditor.getAsset().getState());
    state.setChild(BACKGROUND_IMAGE_PROPERTY, myBackgroundImageAssetBrowser.getAsset().getState());
    state.set(BACKGROUND_ASSET_TYPE_PROPERTY, myBackgroundAssetType.get(), DEFAULT_BACKGROUND_ASSET_TYPE);
    // Notice that the foreground colors that are owned by the asset components have already been saved.
    state.set(BACKGROUND_COLOR_PROPERTY, myBackgroundColor.get(), TvBannerGenerator.DEFAULT_BACKGROUND_COLOR);
    state.set(GENERATE_LEGACY_ICON_PROPERTY, myGenerateLegacyIcon.get(), true);
    state.set(OUTPUT_NAME_PROPERTY, myOutputName.get(), DEFAULT_OUTPUT_NAME);
    state.set(FOREGROUND_LAYER_NAME_PROPERTY, myForegroundLayerName.get(), defaultForegroundLayerName());
    state.set(BACKGROUND_LAYER_NAME_PROPERTY, myBackgroundLayerName.get(), defaultBackgroundLayerName());
    return state;
  }

  @Override
  public void loadState(@NotNull PersistentState state) {
    // This method delays actual state loading until default icon text is obtained from the project
    // resource repository.
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      StudioResourceRepositoryManager repositoryManager = StudioResourceRepositoryManager.getInstance(myFacet);
      ResourceRepository projectResources = repositoryManager.getProjectResources();
      List<ResourceItem> items = projectResources.getResources(repositoryManager.getNamespace(), ResourceType.STRING, "app_name");
      ResourceValue resourceValue = !items.isEmpty() ? items.get(0).getResourceValue() : null;
      String defaultIconText = resourceValue == null || resourceValue.getValue() == null ? "Application Name" : resourceValue.getValue();
      UIUtil.invokeLaterIfNeeded(() -> {
        myForegroundTextAssetEditor.getAsset().setDefaultText(defaultIconText);
        loadStateInternal(state);
      });
    });
  }

  private void loadStateInternal(@NotNull PersistentState state) {
    PersistentStateUtil.load(myForegroundImageAssetBrowser.getAsset(), state.getChild(FOREGROUND_IMAGE_PROPERTY));
    PersistentStateUtil.load(myForegroundTextAssetEditor.getAsset(), state.getChild(FOREGROUND_TEXT_PROPERTY));
    PersistentStateUtil.load(myBackgroundImageAssetBrowser.getAsset(), state.getChild(BACKGROUND_IMAGE_PROPERTY));
    myBackgroundAssetType.set(state.get(BACKGROUND_ASSET_TYPE_PROPERTY, DEFAULT_BACKGROUND_ASSET_TYPE));
    // Notice that the foreground colors that are owned by the asset components have already been loaded.
    myBackgroundColor.set(state.get(BACKGROUND_COLOR_PROPERTY, TvBannerGenerator.DEFAULT_BACKGROUND_COLOR));
    myGenerateLegacyIcon.set(state.get(GENERATE_LEGACY_ICON_PROPERTY, true));
    myOutputName.set(state.get(OUTPUT_NAME_PROPERTY, DEFAULT_OUTPUT_NAME));
    myForegroundLayerName.set(state.get(FOREGROUND_LAYER_NAME_PROPERTY, defaultForegroundLayerName()));
    myBackgroundLayerName.set(state.get(BACKGROUND_LAYER_NAME_PROPERTY, defaultBackgroundLayerName()));
  }

  private void initializeListenersAndBindings() {
    myForegroundImageResizePercent = bindTwoWay(myGeneralBindings, myForegroundImageResizeSlider, myForegroundImageResizeValueTextField);

    myForegroundTextResizePercent = bindTwoWay(myGeneralBindings, myForegroundTextResizeSlider, myForegroundTextResizeValueTextField);

    myBackgroundResizePercent = bindTwoWay(myGeneralBindings, myBackgroundResizeSlider, myBackgroundResizeValueTextField);

    myForegroundTextColor = new ColorProperty(myForegroundColorPanel);
    myBackgroundColor = ObjectProperty.wrap(new ColorProperty(myBackgroundColorPanel));
    myGenerateLegacyIcon = new SelectedProperty(myGenerateLegacyIconYesRadioButton);

    updateBindingsAndUiForActiveIconType();

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
    myForegroundImageAssetBrowser.addAssetListener(assetPanelListener);
    myBackgroundImageAssetBrowser.addAssetListener(assetPanelListener);

    Runnable onAssetModified = this::fireAssetListeners;
    myListeners
      .listenAll(myForegroundImageResizePercent, myForegroundTextResizePercent, myForegroundTextColor,
                 myBackgroundResizePercent, myBackgroundColor,
                 myGenerateLegacyIcon)
      .with(onAssetModified);

    BoolValueProperty foregroundImageIsResizable = new BoolValueProperty();
    myListeners.listenAndFire(myForegroundActiveAsset, () -> {
      myForegroundActiveAssetBindings.releaseAll();
      BaseAsset asset = myForegroundActiveAsset.get();
      myForegroundActiveAssetBindings.bindTwoWay(myForegroundImageResizePercent, asset.scalingPercent());
      myForegroundActiveAssetBindings.bind(foregroundImageIsResizable, asset.isResizable());
      if (asset instanceof ImageAsset) {
        myForegroundActiveAssetBindings.bind(myForegroundAssetValidityState, ((ImageAsset)asset).getValidityState());
      }
      else {
        myForegroundAssetValidityState.set(Validator.Result.OK);
      }

      myIconGenerator.sourceAsset().setValue(asset);

      TextAsset textAsset = myForegroundTextAssetEditor.getAsset();
      myIconGenerator.textAsset().setNullableValue(textAsset);
      myGeneralBindings.bindTwoWay(myForegroundTextResizePercent, textAsset.scalingPercent());
      OptionalValueProperty<Color> assetColor = textAsset.color();
      if (assetColor.getValueOrNull() == null) {
        assetColor.setNullableValue(myForegroundTextColor.getValueOrNull());
      }
      myGeneralBindings.bindTwoWay(myForegroundTextColor, assetColor);
      onAssetModified.run();
    });

    BoolValueProperty backgroundIsResizable = new BoolValueProperty();
    // When switching between Image/Color for background, bind corresponding properties and regenerate asset (to be sure).
    Runnable onBackgroundAssetModified = () -> {
      myBackgroundActiveAssetBindings.releaseAll();
      ImageAsset asset = myBackgroundImageAsset.getValueOrNull();
      if (asset != null) {
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
    myListeners.listenAndFire(myBackgroundImageAsset, () -> onBackgroundAssetModified.run());

    /*
     * Hook up a bunch of UI <- boolean expressions, so that when certain conditions are met,
     * various components show/hide. This also requires refreshing the panel explicitly, as
     * otherwise Swing doesn't realize it should trigger a relayout.
     */
    ImmutableMap.Builder<BoolProperty, ObservableValue<Boolean>> layoutPropertiesBuilder = ImmutableMap.builder();

    if (HIDE_INAPPLICABLE_CONTROLS) {
      layoutPropertiesBuilder.put(new VisibleProperty(myForegroundTextTitleSeparator), foregroundImageIsResizable);
      layoutPropertiesBuilder.put(new VisibleProperty(myForegroundTextOptionsPanel), foregroundImageIsResizable);
    }
    else {
      layoutPropertiesBuilder.put(new EnabledProperty(myForegroundImageResizeSlider), foregroundImageIsResizable);
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
      layoutPropertiesBuilder.put(new EnabledProperty(myBackgroundResizeSlider), backgroundIsResizable);
    }

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
    myValidatorPanel.registerValidator(
      new TextProperty(myForegroundImageResizeValueTextField), inRange(myForegroundImageResizeSlider, "Foreground image scale")
    );
    myValidatorPanel.registerValidator(
      new TextProperty(myForegroundTextResizeValueTextField), inRange(myForegroundTextResizeSlider, "Foreground text scale")
    );
    myValidatorPanel.registerValidator(
      new TextProperty(myBackgroundResizeValueTextField), inRange(myBackgroundResizeSlider, "Background scale")
    );
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
    myForegroundAllOptionsPanel.setLayout(new GridLayoutManager(5, 2, new Insets(0, 0, 0, 0), -1, -1));
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
    myForegroundImageTitleSeparator = new TitledSeparator();
    myForegroundImageTitleSeparator.setText("Image");
    myForegroundAllOptionsPanel.add(myForegroundImageTitleSeparator,
                                    new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myForegroundImageAssetPanel = new JPanel();
    myForegroundImageAssetPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundAllOptionsPanel.add(myForegroundImageAssetPanel,
                                    new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, true));
    myForegroundImageSourcePanel = new JPanel();
    myForegroundImageSourcePanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundImageAssetPanel.add(myForegroundImageSourcePanel,
                                    new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, true));
    myForegroundImagePathLabel = new JBLabel();
    myForegroundImagePathLabel.setText("Image file:");
    myForegroundImageSourcePanel.add(myForegroundImagePathLabel,
                                     new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                         new Dimension(70, -1), null, null, 1, false));
    myForegroundImageAssetBrowser = new ImageAssetBrowser();
    myForegroundImageSourcePanel.add(myForegroundImageAssetBrowser,
                                     new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myForegroundImageResizePanel = new JPanel();
    myForegroundImageResizePanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundImageResizePanel.setVisible(true);
    myForegroundImageAssetPanel.add(myForegroundImageResizePanel,
                                    new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, true));
    myForegroundImageResizeLabel = new JBLabel();
    myForegroundImageResizeLabel.setText("Resize:");
    myForegroundImageResizePanel.add(myForegroundImageResizeLabel,
                                     new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                         new Dimension(70, -1), null, null, 1, false));
    myForegroundImageResizeSliderPanel = new JPanel();
    myForegroundImageResizeSliderPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundImageResizePanel.add(myForegroundImageResizeSliderPanel,
                                     new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                         null, null, 0, false));
    myForegroundImageResizeSlider = new JSlider();
    myForegroundImageResizeSlider.setMaximum(400);
    myForegroundImageResizeSlider.setMinimum(0);
    myForegroundImageResizeSlider.setMinorTickSpacing(20);
    myForegroundImageResizeSlider.setPaintLabels(false);
    myForegroundImageResizeSlider.setPaintTicks(true);
    myForegroundImageResizeSlider.setSnapToTicks(false);
    myForegroundImageResizeSlider.setToolTipText(
      "Resize the original asset using the specified scaling factor (in percent). This happens after any trimming.");
    myForegroundImageResizeSlider.setValue(100);
    myForegroundImageResizeSlider.setValueIsAdjusting(false);
    myForegroundImageResizeSliderPanel.add(myForegroundImageResizeSlider,
                                           new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myForegroundImageResizeValueTextField = new JTextField();
    myForegroundImageResizeValueTextField.setHorizontalAlignment(4);
    myForegroundImageResizeValueTextField.setText("100");
    myForegroundImageResizeSliderPanel.add(myForegroundImageResizeValueTextField,
                                           new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                               GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                               new Dimension(30, -1), null, 0, false));
    myForegroundImageResizeValueLabel = new JBLabel();
    myForegroundImageResizeValueLabel.setText("%");
    myForegroundImageResizeSliderPanel.add(myForegroundImageResizeValueLabel,
                                           new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                               GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                               new Dimension(-1, -1), null, 0, false));
    myForegroundTextOptionsPanel = new JPanel();
    myForegroundTextOptionsPanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundAllOptionsPanel.add(myForegroundTextOptionsPanel,
                                    new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, true));
    myForegroundTextTitleSeparator = new TitledSeparator();
    myForegroundTextTitleSeparator.setText("Label");
    myForegroundTextOptionsPanel.add(myForegroundTextTitleSeparator,
                                     new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myForegroundTextAssetRowPanel = new JPanel();
    myForegroundTextAssetRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundTextOptionsPanel.add(myForegroundTextAssetRowPanel,
                                     new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                         null, null, 0, true));
    myForegroundTextLabel = new JBLabel();
    myForegroundTextLabel.setText("Text:");
    myForegroundTextLabel.setVerifyInputWhenFocusTarget(false);
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
    myForegroundTextOptionsPanel.add(myForegroundColorRowPanel,
                                     new GridConstraints(2, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
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
    myForegroundTextResizePanel = new JPanel();
    myForegroundTextResizePanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundTextResizePanel.setVisible(true);
    myForegroundTextOptionsPanel.add(myForegroundTextResizePanel,
                                     new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                         null, null, 0, true));
    myForegroundTextResizeLabel = new JBLabel();
    myForegroundTextResizeLabel.setText("Resize:");
    myForegroundTextResizePanel.add(myForegroundTextResizeLabel,
                                    new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED,
                                                        new Dimension(70, -1), null, null, 1, false));
    myForegroundTextResizeSliderPanel = new JPanel();
    myForegroundTextResizeSliderPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    myForegroundTextResizePanel.add(myForegroundTextResizeSliderPanel,
                                    new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, false));
    myForegroundTextResizeSlider = new JSlider();
    myForegroundTextResizeSlider.setMaximum(400);
    myForegroundTextResizeSlider.setMinimum(0);
    myForegroundTextResizeSlider.setMinorTickSpacing(20);
    myForegroundTextResizeSlider.setPaintLabels(false);
    myForegroundTextResizeSlider.setPaintTicks(true);
    myForegroundTextResizeSlider.setSnapToTicks(false);
    myForegroundTextResizeSlider.setToolTipText(
      "Resize the original asset using the specified scaling factor (in percent). This happens after any trimming.");
    myForegroundTextResizeSlider.setValue(100);
    myForegroundTextResizeSlider.setValueIsAdjusting(false);
    myForegroundTextResizeSliderPanel.add(myForegroundTextResizeSlider,
                                          new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myForegroundTextResizeValueTextField = new JTextField();
    myForegroundTextResizeValueTextField.setHorizontalAlignment(4);
    myForegroundTextResizeValueTextField.setText("100");
    myForegroundTextResizeSliderPanel.add(myForegroundTextResizeValueTextField,
                                           new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                               GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                               new Dimension(30, -1), null, 0, false));

    myForegroundTextResizeValueLabel = new JLabel();
    myForegroundTextResizeValueLabel.setHorizontalAlignment(4);
    myForegroundTextResizeValueLabel.setText("%");
    myForegroundTextResizeSliderPanel.add(myForegroundTextResizeValueLabel,
                                          new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                              GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              new Dimension(-1, -1), null, 0, false));
    final Spacer spacer1 = new Spacer();
    myForegroundAllOptionsPanel.add(spacer1,
                                    new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
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
    myBackgroundImageOptionsPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myBackgroundAllOptionsPanel.add(myBackgroundImageOptionsPanel,
                                    new GridConstraints(4, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, true));
    myBackgroundResizeRowPanel = new JPanel();
    myBackgroundResizeRowPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myBackgroundResizeRowPanel.setVisible(true);
    myBackgroundImageOptionsPanel.add(myBackgroundResizeRowPanel,
                                      new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
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
    myBackgroundResizeSliderPanel.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
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
    myBackgroundResizeValueTextField = new JTextField();
    myBackgroundResizeValueTextField.setHorizontalAlignment(4);
    myBackgroundResizeValueTextField.setText("100");
    myBackgroundResizeSliderPanel.add(myBackgroundResizeValueTextField,
                                           new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                               GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                               new Dimension(30, -1), null, 0, false));
    myBackgroundResizeValueLabel = new JLabel();
    myBackgroundResizeValueLabel.setHorizontalAlignment(4);
    myBackgroundResizeValueLabel.setText("%");
    myBackgroundResizeSliderPanel.add(myBackgroundResizeValueLabel,
                                      new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                          GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                          new Dimension(-1, -1), null, 0, false));
    final Spacer spacer2 = new Spacer();
    myBackgroundAllOptionsPanel.add(spacer2,
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
    myOtherIconsAllOptionsPanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
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
    final Spacer spacer3 = new Spacer();
    mGenerateLegacyIconRadioButtonsPanel.add(spacer3,
                                             new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                                 GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    final Spacer spacer4 = new Spacer();
    myOtherIconsAllOptionsPanel.add(spacer4,
                                    new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                        GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    ButtonGroup buttonGroup;
    buttonGroup = new ButtonGroup();
    buttonGroup.add(radioButton1);
    buttonGroup.add(myGenerateLegacyIconYesRadioButton);
    buttonGroup = new ButtonGroup();
    buttonGroup.add(myBackgroundImageRadioButton);
    buttonGroup.add(myBackgroundColorRadioButton);
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
  public TvBannerGenerator getIconGenerator() {
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
    myOutputName.set(DEFAULT_OUTPUT_NAME);
    myForegroundLayerName.set(defaultForegroundLayerName());
    myBackgroundLayerName.set(defaultBackgroundLayerName());

    myGeneralBindings.bind(myIconGenerator.sourceAsset(), new AsOptionalExpression<>(myForegroundActiveAsset));
    myGeneralBindings.bind(myIconGenerator.outputName(), myOutputName);
    myGeneralBindings.bindTwoWay(myIconGenerator.backgroundImageAsset(), myBackgroundImageAsset);

    myGeneralBindings.bindTwoWay(myBackgroundColor, myIconGenerator.backgroundColor());
    myGeneralBindings.bindTwoWay(myGenerateLegacyIcon, myIconGenerator.generateLegacyIcon());
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

  private enum BackgroundAssetType {
    IMAGE,
    COLOR,
  }
}
