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

import com.android.tools.idea.npw.assetstudio.ActionBarIconGenerator;
import com.android.tools.idea.npw.assetstudio.IconGenerator;
import com.android.tools.idea.npw.assetstudio.IconGenerator.Shape;
import com.android.tools.idea.npw.assetstudio.LauncherLegacyIconGenerator;
import com.android.tools.idea.npw.assetstudio.NotificationIconGenerator;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import com.android.tools.idea.npw.assetstudio.icon.AndroidIconType;
import com.android.tools.idea.npw.assetstudio.wizard.PersistentState;
import com.android.tools.idea.npw.assetstudio.wizard.PersistentStateUtil;
import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.core.*;
import com.android.tools.idea.observable.expressions.bool.BooleanExpression;
import com.android.tools.idea.observable.expressions.optional.AsOptionalExpression;
import com.android.tools.idea.observable.expressions.string.FormatExpression;
import com.android.tools.idea.observable.ui.*;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.toLowerCamelCase;

/**
 * A panel which allows the configuration of an icon, by specifying the source asset used to
 * generate the icon plus some other options. Note that this panel provides a superset of all
 * options used by each {@link AndroidIconType}, but the relevant options are shown / hidden based
 * on the exact type passed into the constructor.
 */
public final class ConfigureIconPanel extends JPanel implements Disposable, ConfigureIconView {
  private static final AssetType DEFAULT_ASSET_TYPE = AssetType.CLIP_ART;

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
   * Source material icons are provided in a vector graphics format, but their default resolution
   * is very low (24x24). Since we plan to render them to much larger icons, we will up the detail
   * a fair bit.
   */
  private static final Dimension CLIPART_RESOLUTION = new Dimension(256, 256);

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
  private VectorIconButton myClipartAssetButton;
  private TextAssetEditor myTextAssetEditor;
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

  // @formatter:off
  private final Map<Shape, String> myShapeNames = ImmutableMap.of(
      Shape.NONE, "None",
      Shape.CIRCLE, "Circle",
      Shape.SQUARE, "Square",
      Shape.VRECT, "Vertical",
      Shape.HRECT, "Horizontal");
  // @formatter:on

  @NotNull private final List<ActionListener> myAssetListeners = new ArrayList<>(1);

  @NotNull private final AndroidIconType myIconType;
  @NotNull private final IconGenerator myIconGenerator;
  @NotNull private final String myDefaultOutputName;

  @NotNull private final BindingsManager myGeneralBindings = new BindingsManager();
  @NotNull private final BindingsManager myActiveAssetBindings = new BindingsManager();
  @NotNull private final ListenerManager myListeners = new ListenerManager();

  @NotNull private final ImmutableMap<AssetType, AssetComponent> myAssetPanelMap;

  @NotNull private final ObjectProperty<BaseAsset> myActiveAsset;
  @NotNull private final StringProperty myOutputName;
  @NotNull private final AbstractProperty<AssetType> myAssetType;
  private BoolProperty myIgnoreForegroundColor;
  private AbstractProperty<Color> myForegroundColor;
  private AbstractProperty<Color> myBackgroundColor;
  private AbstractProperty<Shape> myShape;
  private BoolProperty myCropped;
  private BoolProperty myDogEared;
  private AbstractProperty<ActionBarIconGenerator.Theme> myTheme;
  private AbstractProperty<Color> myThemeColor;

  /**
   * Initializes a panel which can generate few kinds of Android icons.
   */
  public ConfigureIconPanel(@NotNull Disposable disposableParent, @NotNull AndroidIconType iconType, int minSdkVersion) {
    super(new BorderLayout());

    myIconType = iconType;
    myDefaultOutputName = myIconType.toOutputName("name");
    myIconGenerator = createIconGenerator(iconType, minSdkVersion);

    DefaultComboBoxModel<ActionBarIconGenerator.Theme> themesModel = new DefaultComboBoxModel<>(ActionBarIconGenerator.Theme.values());
    myThemeComboBox.setModel(themesModel);

    DefaultComboBoxModel<Shape> shapesModel = new DefaultComboBoxModel<>();
    for (Shape shape : myShapeNames.keySet()) {
      shapesModel.addElement(shape);
    }
    myShapeComboBox.setRenderer(new ListCellRendererWrapper<Shape>() {
      @Override
      public void customize(JList list, Shape shape, int index, boolean selected, boolean hasFocus) {
        setText(myShapeNames.get(shape));
      }
    });
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
    VectorAsset clipartAsset = myClipartAssetButton.getAsset();
    clipartAsset.outputWidth().set(CLIPART_RESOLUTION.width);
    clipartAsset.outputHeight().set(CLIPART_RESOLUTION.height);
    myActiveAsset = new ObjectValueProperty<>(clipartAsset);
    myAssetType = new SelectedRadioButtonProperty<>(DEFAULT_ASSET_TYPE, AssetType.values(),
                                                    myImageRadioButton, myClipartRadioButton, myTextRadioButton);

    initializeListenersAndBindings();

    Disposer.register(disposableParent, this);
    for (AssetComponent assetComponent : myAssetPanelMap.values()) {
      Disposer.register(this, assetComponent);
    }
    Disposer.register(this, myIconGenerator);
    add(myRootPanel);
  }

  @Override
  @NotNull
  public PersistentState getState() {
    PersistentState state = new PersistentState();
    for (Map.Entry<AssetType, AssetComponent> entry : myAssetPanelMap.entrySet()) {
      state.setChild(toLowerCamelCase(entry.getKey()), entry.getValue().getAsset().getState());
    }
    state.set(OUTPUT_NAME_PROPERTY, myOutputName.get(), myDefaultOutputName);
    state.set(ASSET_TYPE_PROPERTY, myAssetType.get(), DEFAULT_ASSET_TYPE);
    File file = myImageAssetBrowser.getAsset().imagePath().getValueOrNull();
    state.set(IMAGE_ASSET_PROPERTY, file == null ? null : file.getPath());
    state.setChild(CLIPART_ASSET_PROPERTY, myClipartAssetButton.getState());
    state.setChild(TEXT_ASSET_PROPERTY, myTextAssetEditor.getState());
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
        state.set(THEME_COLOR_PROPERTY, myThemeColor.get(), ActionBarIconGenerator.DEFAULT_COLOR);
        break;

      default:
        break; // No special properties.
    }
    return state;
  }

  @Override
  public void loadState(@NotNull PersistentState state) {
    for (Map.Entry<AssetType, AssetComponent> entry : myAssetPanelMap.entrySet()) {
      PersistentStateUtil.load(entry.getValue().getAsset(), state.getChild(toLowerCamelCase(entry.getKey())));
    }
    myOutputName.set(state.get(OUTPUT_NAME_PROPERTY, myDefaultOutputName));
    myAssetType.set(state.get(ASSET_TYPE_PROPERTY, DEFAULT_ASSET_TYPE));
    String path = state.get(IMAGE_ASSET_PROPERTY);
    myImageAssetBrowser.getAsset().imagePath().setNullableValue(path == null ? null : new File(path));
    PersistentStateUtil.load(myClipartAssetButton, state.getChild(CLIPART_ASSET_PROPERTY));
    PersistentStateUtil.load(myTextAssetEditor, state.getChild(TEXT_ASSET_PROPERTY));
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
        myThemeColor.set(state.get(THEME_COLOR_PROPERTY, ActionBarIconGenerator.DEFAULT_COLOR));
        break;

      default:
        break; // No special properties.
    }
  }

  @NotNull
  private static AssetType getSelectedAssetType(JRadioButton... buttons) {
    for (JRadioButton button : buttons) {
      if (button.isSelected()) {
        String name = button.getName();
        if (name != null) {
          try {
            return AssetType.valueOf(name);
          } catch (IllegalArgumentException e) {
            break;
          }
        }
      }
    }
    return DEFAULT_ASSET_TYPE;
  }

  private static void selectedRadioButton(@NotNull AssetType assetType, JRadioButton... buttons) {
    for (JRadioButton button : buttons) {
      if (assetType.name().equals(button.getName())) {
        button.setSelected(true);
        break;
      }
    }
  }

  @NotNull
  private static IconGenerator createIconGenerator(@NotNull AndroidIconType iconType, int minSdkVersion) {
    switch (iconType) {
      case LAUNCHER_LEGACY:
        return new LauncherLegacyIconGenerator(minSdkVersion);
      case ACTIONBAR:
        return new ActionBarIconGenerator(minSdkVersion);
      case NOTIFICATION:
        return new NotificationIconGenerator(minSdkVersion);
      default:
        throw new IllegalArgumentException("Unexpected icon type: " + iconType);
    }
  }

  private void initializeListenersAndBindings() {
    BoolProperty trimmed = new SelectedProperty(myTrimmedRadioButton);

    IntProperty paddingPercent = new SliderValueProperty(myPaddingSlider);
    StringProperty paddingValueString = new TextProperty(myPaddingValueLabel);
    myGeneralBindings.bind(paddingValueString, new FormatExpression("%d %%", paddingPercent));

    myIgnoreForegroundColor = new SelectedProperty(myImageRadioButton);
    myForegroundColor = ObjectProperty.wrap(new ColorProperty(myForegroundColorPanel));
    myBackgroundColor = ObjectProperty.wrap(new ColorProperty(myBackgroundColorPanel));
    myCropped = new SelectedProperty(myCropRadioButton);
    myDogEared = new SelectedProperty(myDogEarRadioButton);

    myTheme = ObjectProperty.wrap(new SelectedItemProperty<>(myThemeComboBox));
    myThemeColor = ObjectProperty.wrap(new ColorProperty(myCustomThemeColorPanel));

    myShape = ObjectProperty.wrap(new SelectedItemProperty<>(myShapeComboBox));

    updateBindingsAndUiForActiveIconType();

    // Update foreground layer asset type depending on asset type radio buttons.
    myAssetType.addListener(sender -> {
      AssetComponent assetComponent = myAssetPanelMap.get(myAssetType.get());
      myActiveAsset.set(assetComponent.getAsset());
    });

    // If any of our underlying asset panels change, we should pass that on to anyone listening to
    // us as well.
    ActionListener assetPanelListener = e -> fireAssetListeners();
    for (AssetComponent assetComponent : myAssetPanelMap.values()) {
      assetComponent.addAssetListener(assetPanelListener);
    }

    Runnable onAssetModified = this::fireAssetListeners;
    myListeners
        .listenAll(trimmed, paddingPercent, myForegroundColor, myBackgroundColor, myCropped, myDogEared, myTheme, myThemeColor, myShape)
        .with(onAssetModified);

    myListeners.listenAndFire(myActiveAsset, sender -> {
      myActiveAssetBindings.releaseAll();
      myActiveAssetBindings.bindTwoWay(trimmed, myActiveAsset.get().trimmed());
      myActiveAssetBindings.bindTwoWay(paddingPercent, myActiveAsset.get().paddingPercent());
      myActiveAssetBindings.bindTwoWay(myForegroundColor, myActiveAsset.get().color());

      getIconGenerator().sourceAsset().setValue(myActiveAsset.get());
      onAssetModified.run();
    });

    ObservableBool isLauncherIcon = new BoolValueProperty(myIconType.equals(AndroidIconType.LAUNCHER_LEGACY));
    ObservableBool isActionBarIcon = new BoolValueProperty(myIconType.equals(AndroidIconType.ACTIONBAR));
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

  private void updateBindingsAndUiForActiveIconType() {
    myOutputName.set(myDefaultOutputName);

    myGeneralBindings.bind(myIconGenerator.sourceAsset(), new AsOptionalExpression<>(myActiveAsset));
    myGeneralBindings.bind(myIconGenerator.outputName(), myOutputName);

    switch (myIconType) {
      case LAUNCHER_LEGACY:
        LauncherLegacyIconGenerator launcherIconGenerator = (LauncherLegacyIconGenerator)myIconGenerator;
        myGeneralBindings.bind(launcherIconGenerator.useForegroundColor(), myIgnoreForegroundColor.not());
        myGeneralBindings.bindTwoWay(myForegroundColor, launcherIconGenerator.foregroundColor());
        myGeneralBindings.bindTwoWay(myBackgroundColor, launcherIconGenerator.backgroundColor());
        myGeneralBindings.bindTwoWay(myCropped, launcherIconGenerator.cropped());
        myGeneralBindings.bindTwoWay(myShape, launcherIconGenerator.shape());
        myGeneralBindings.bindTwoWay(myDogEared, launcherIconGenerator.dogEared());
        break;

      case ACTIONBAR:
        ActionBarIconGenerator actionBarIconGenerator = (ActionBarIconGenerator)myIconGenerator;
        myGeneralBindings.bindTwoWay(myThemeColor, actionBarIconGenerator.customColor());
        myGeneralBindings.bindTwoWay(myTheme, actionBarIconGenerator.theme());
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
    myIconGenerator.dispose();
  }

  private enum AssetType {
    IMAGE,
    CLIP_ART,
    TEXT
  }
}
