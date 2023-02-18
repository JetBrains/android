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

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.StudioAndroidModuleInfo;
import com.android.tools.idea.rendering.DrawableRenderer;
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
import com.android.tools.idea.observable.expressions.string.FormatExpression;
import com.android.tools.idea.observable.ui.ColorProperty;
import com.android.tools.idea.observable.ui.EnabledProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.SelectedRadioButtonProperty;
import com.android.tools.idea.observable.ui.SliderValueProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.observable.ui.VisibleProperty;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
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
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
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
  private JLabel myForegroundImageResizeValueLabel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myForegroundTextResizeSliderPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JSlider myForegroundTextResizeSlider;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
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
  private AndroidFacet myFacet;

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
      ResourceRepositoryManager repositoryManager = ResourceRepositoryManager.getInstance(myFacet);
      LocalResourceRepository projectResources = repositoryManager.getProjectResources();
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
    myForegroundImageResizePercent = new SliderValueProperty(myForegroundImageResizeSlider);
    StringProperty foregroundImageResizeValueString = new TextProperty(myForegroundImageResizeValueLabel);
    myGeneralBindings.bind(foregroundImageResizeValueString, new FormatExpression("%d %%", myForegroundImageResizePercent));

    myForegroundTextResizePercent = new SliderValueProperty(myForegroundTextResizeSlider);
    StringProperty foregroundTextResizeValueString = new TextProperty(myForegroundTextResizeValueLabel);
    myGeneralBindings.bind(foregroundTextResizeValueString, new FormatExpression("%d %%", myForegroundTextResizePercent));

    myBackgroundResizePercent = new SliderValueProperty(myBackgroundResizeSlider);
    StringProperty backgroundResizeValueString = new TextProperty(myBackgroundResizeValueLabel);
    myGeneralBindings.bind(backgroundResizeValueString, new FormatExpression("%d %%", myBackgroundResizePercent));

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
    myValidatorPanel.registerTest(nameIsNotEmptyExpression(isActive, myBackgroundLayerName),
                                  "Background layer name must be set");
    myValidatorPanel.registerTest(namesAreDistinctExpression(isActive, myOutputName, myForegroundLayerName),
                                  "Foreground layer must have a name distinct from the icon name");
    myValidatorPanel.registerTest(namesAreDistinctExpression(isActive, myOutputName, myBackgroundLayerName),
                                  "Background layer must have a name distinct from the icon name");
    myValidatorPanel.registerTest(namesAreDistinctExpression(isActive, myForegroundLayerName, myBackgroundLayerName),
                                  "Background and foreground layers must have distinct names");

    myValidatorPanel.registerValidator(myForegroundAssetValidityState, validity -> validity);
    myValidatorPanel.registerValidator(myBackgroundAssetValidityState, validity -> validity);
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
