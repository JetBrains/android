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

import com.android.assetstudiolib.GraphicGenerator;
import com.android.resources.Density;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import com.android.tools.idea.npw.assetstudio.icon.AndroidAdaptiveIconGenerator;
import com.android.tools.idea.npw.assetstudio.icon.AndroidAdaptiveIconType;
import com.android.tools.idea.npw.assetstudio.wizard.GenerateIconsPanel;
import com.android.tools.idea.ui.properties.AbstractProperty;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.adapters.OptionalToValuePropertyAdapter;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.ui.properties.expressions.Expression;
import com.android.tools.idea.ui.properties.expressions.bool.BooleanExpression;
import com.android.tools.idea.ui.properties.expressions.optional.AsOptionalExpression;
import com.android.tools.idea.ui.properties.expressions.string.FormatExpression;
import com.android.tools.idea.ui.properties.expressions.string.StringExpression;
import com.android.tools.idea.ui.properties.swing.*;
import com.android.tools.idea.ui.validation.Validator;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;

/**
 * A panel which allows the configuration of an icon, by specifying the source asset used to
 * generate the icon plus some other options. Note that this panel provides a superset of all
 * options used by each {@link AndroidAdaptiveIconType}, but the relevant options are shown / hidden based
 * on the exact type passed into the constructor.
 *
 * See also {@link GenerateIconsPanel} which owns a couple of these panels, one for each
 * {@link AndroidAdaptiveIconType}.
 */
public class ConfigureAdaptiveIconPanel extends JPanel implements Disposable, ConfigureIconView {

  /**
   * Source material icons are provided in a vector graphics format, but their default resolution
   * is very low (24x24). Since we plan to render them to much larger icons, we will up the detail
   * a fair bit.
   */
  private static final Dimension CLIPART_RESOLUTION = new Dimension(256, 256);

  @NotNull private final List<ActionListener> myAssetListeners = Lists.newArrayListWithExpectedSize(1);

  @NotNull private final AndroidVersion myTargetSdkVersion;
  @NotNull private final BoolProperty myShowGridProperty;
  @NotNull private final BoolProperty myShowSafeZoneProperty;
  @NotNull private final AbstractProperty<Density> myPreviewDensityProperty;
  @NotNull private final AndroidAdaptiveIconGenerator myIconGenerator;
  @NotNull private final ValidatorPanel myValidatorPanel;

  @NotNull private final BindingsManager myGeneralBindings = new BindingsManager();
  @NotNull private final BindingsManager myForegroundActiveAssetBindings = new BindingsManager();
  @NotNull private final BindingsManager myBackgroundActiveAssetBindings = new BindingsManager();
  @NotNull private final ListenerManager myListeners = new ListenerManager();

  /**
   * This panel presents a list of radio buttons (clipart, image, text), and whichever one is
   * selected sets the active asset.
   */
  private final ObjectProperty<BaseAsset> myForegroundActiveAsset;
  private final OptionalProperty<ImageAsset> myBackgroundImageAsset;
  private final StringProperty myOutputName;
  private final StringProperty myForegroundLayerName;
  private final StringProperty myBackgroundLayerName;

  private final ImmutableMap<JRadioButton, ? extends AssetComponent> myForegroundAssetPanelMap;

  private final Map<GraphicGenerator.Shape, String> myShapeNames = ImmutableMap.of(
    GraphicGenerator.Shape.NONE, "None",
    GraphicGenerator.Shape.CIRCLE, "Circle",
    GraphicGenerator.Shape.SQUARE, "Square",
    GraphicGenerator.Shape.VRECT, "Vertical",
    GraphicGenerator.Shape.HRECT, "Horizontal");

  private JPanel myRootPanel;
  private JBLabel myOutputNameLabel;
  private JTextField myOutputNameTextField;

  private JPanel myForegroundAllOptionsPanel;
  private JRadioButton myForegroundClipartRadioButton;
  private JRadioButton myForegroundTextRadioButton;
  private JRadioButton myForegroundImageRadioButton;
  private JRadioButton myForegroundTrimmedRadioButton;
  private JPanel myForegroundTrimOptionsPanel;
  private JSlider myForegroundPaddingSlider;
  private JLabel myForegroundPaddingValueLabel;
  private JPanel myForegroundAssetRadioButtonsPanel;
  private JPanel myForegroundPaddingSliderPanel;
  private JTextField myForegroundLayerNameTextField;
  private JPanel myForegroundColorRowPanel;
  private ColorPanel myForegroundColorPanel;
  private JPanel myForegroundScalingRadioButtonsPanel;
  private JRadioButton myForegroundCropRadioButton;
  private JPanel myForegroundEffectRadioButtonsPanel;
  // Effects not supported
  @SuppressWarnings("unused") private JRadioButton myForegroundDogEarRadioButton;
  private JBScrollPane myForegroundScrollPane;
  private JPanel myForegroundImageAssetRowPanel;
  private JPanel myForegroundClipartAssetRowPanel;
  private JPanel myForegroundTextAssetRowPanel;
  private ImageAssetBrowser myForegroundImageAssetBrowser;
  private VectorIconButton myForegroundClipartAssetButton;
  private TextAssetEditor myForegroundTextAssetEditor;
  private JBLabel myForegroundLayerNameLabel;
  private JLabel myForegroundAssetTypeLabel;
  private JBLabel myForegroundImagePathLabel;
  private JBLabel myForegroundClipartLabel;
  private JBLabel myForegroundTextLabel;
  private JBLabel myForegroundTrimLabel;
  private JBLabel myForegroundPaddingLabel;
  private JBLabel myForegroundColorLabel;
  private JBLabel myForegroundScalingLabel;
  private JBLabel myForegroundEffectLabel;

  private JPanel myBackgroundAllOptionsPanel;
  private JRadioButton myBackgroundImageRadioButton;
  private JRadioButton myBackgroundColorRadioButton;
  private JRadioButton myBackgroundTrimmedRadioButton;
  private JPanel myBackgroundTrimOptionsPanel;
  private JSlider myBackgroundPaddingSlider;
  private JLabel myBackgroundPaddingValueLabel;
  private JPanel myBackgroundAssetRadioButtonsPanel;
  private JPanel myBackgroundPaddingSliderPanel;
  private JTextField myBackgroundLayerNameTextField;
  private JPanel myBackgroundTrimRowPanel;
  private JPanel myBackgroundPaddingRowPanel;
  private JPanel myBackgroundColorRowPanel;
  private ColorPanel myBackgroundColorPanel;
  private JPanel myBackgroundScalingRowPanel;
  private JPanel myBackgroundScalingRadioButtonsPanel;
  private JRadioButton myBackgroundCropRadioButton;
  private JPanel myBackgroundEffectRadioButtonsPanel;
  // Effects not supported
  @SuppressWarnings("unused") private JRadioButton myBackgroundDogEarRadioButton;
  private JPanel myBackgroundEffectPanel;
  private JBScrollPane myBackgroundScrollPane;
  private JComboBox<GraphicGenerator.Shape> myBackgroundLegacyShapeComboBox;
  private JPanel myBackgroundImageAssetRowPanel;
  private ImageAssetBrowser myBackgroundImageAssetBrowser;
  private JBLabel myBackgroundLayerNameLabel;
  private JLabel myBackgroundAssetTypeLabel;
  private JBLabel myBackgroundImagePathLabel;
  private JBLabel myBackgroundTrimLabel;
  private JBLabel myBackgroundPaddingLabel;
  private JBLabel myBackgroundScalingLabel;
  private JBLabel myBackgroundColorLabel;
  private JBLabel myBackgroundLegacyShapeLabel;
  private JBLabel myBackgroundEffectLabel;
  private JPanel myForegroundEffectPanel;
  private JBScrollPane myLegacyScrollPane;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myLegacyAllOptionsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myForegroundAssetTypePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myForegroundLayerNamePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myForegroundAssetTypeSourcePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myForegroundImageOptionsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myBackgroundLayerNamePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myBackgroundAssetTypePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myBackgroundAssetTypeSourcePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myBackgroundImageOptionsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myLegacyShapePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myOutputNamePanelRow;

  private BoolProperty myIgnoreForegroundColor;
  private AbstractProperty<Color> myForegroundColor;
  private AbstractProperty<Color> myBackgroundColor;
  private BoolProperty myForegroundCropped;
  private BoolProperty myBackgroundCropped;
  private AbstractProperty<GraphicGenerator.Shape> myLegacyShape;

  /**
   * Create a panel which can generate Android icons. The supported types passed in will be
   * presented to the user in a combo box (unless there's only one supported type). If no
   * supported types are passed in, then all types will be supported by default.
   */
  public ConfigureAdaptiveIconPanel(@NotNull Disposable disposableParent,
                                    @NotNull AndroidVersion minSdkVersion,
                                    @NotNull AndroidVersion targetSdkVersion,
                                    @NotNull BoolProperty showGridProperty,
                                    @NotNull BoolProperty showSafeZoneProperty,
                                    @NotNull AbstractProperty<Density> previewDensityProperty,
                                    @NotNull ValidatorPanel validatorPanel) {
    super(new BorderLayout());
    myTargetSdkVersion = targetSdkVersion;

    myShowGridProperty = showGridProperty;
    myShowSafeZoneProperty = showSafeZoneProperty;
    myPreviewDensityProperty = previewDensityProperty;
    myIconGenerator =
      (AndroidAdaptiveIconGenerator)AndroidAdaptiveIconType
        .createIconGenerator(AndroidAdaptiveIconType.ADAPTIVE, minSdkVersion.getApiLevel());
    myValidatorPanel = validatorPanel;

    DefaultComboBoxModel<GraphicGenerator.Shape> shapesModel = new DefaultComboBoxModel<>();
    for (GraphicGenerator.Shape shape : myShapeNames.keySet()) {
      shapesModel.addElement(shape);
    }
    myBackgroundLegacyShapeComboBox.setRenderer(new ListCellRendererWrapper<GraphicGenerator.Shape>() {
      @Override
      public void customize(JList list, GraphicGenerator.Shape shape, int index, boolean selected, boolean hasFocus) {
        setText(myShapeNames.get(shape));
      }
    });
    myBackgroundLegacyShapeComboBox.setModel(shapesModel);
    myBackgroundLegacyShapeComboBox.setSelectedItem(GraphicGenerator.Shape.SQUARE);

    myForegroundScrollPane.getVerticalScrollBar().setUnitIncrement(10);
    myForegroundScrollPane.setBorder(IdeBorderFactory.createEmptyBorder());

    myBackgroundScrollPane.getVerticalScrollBar().setUnitIncrement(10);
    myBackgroundScrollPane.setBorder(IdeBorderFactory.createEmptyBorder());

    myLegacyScrollPane.getVerticalScrollBar().setUnitIncrement(10);
    myLegacyScrollPane.setBorder(IdeBorderFactory.createEmptyBorder());

    myOutputName = new TextProperty(myOutputNameTextField);
    myForegroundLayerName = new TextProperty(myForegroundLayerNameTextField);
    myBackgroundLayerName = new TextProperty(myBackgroundLayerNameTextField);

    myForegroundAssetPanelMap = ImmutableMap.of(
      myForegroundImageRadioButton, myForegroundImageAssetBrowser,
      myForegroundClipartRadioButton, myForegroundClipartAssetButton,
      myForegroundTextRadioButton, myForegroundTextAssetEditor
    );

    // Call "setLabelFor" in code instead of designer since designer is so inconsistent about
    // valid targets
    myOutputNameLabel.setLabelFor(myOutputNameTextField);

    myForegroundLayerNameLabel.setLabelFor(myForegroundLayerNameTextField);
    myForegroundAssetTypeLabel.setLabelFor(myForegroundAssetRadioButtonsPanel);
    myForegroundImagePathLabel.setLabelFor(myForegroundImageAssetBrowser);
    myForegroundClipartLabel.setLabelFor(myForegroundClipartAssetButton);
    myForegroundTextLabel.setLabelFor(myForegroundTextAssetEditor);
    myForegroundTrimLabel.setLabelFor(myForegroundTrimOptionsPanel);
    myForegroundPaddingLabel.setLabelFor(myForegroundPaddingSliderPanel);
    myForegroundColorLabel.setLabelFor(myForegroundColorPanel);
    myForegroundScalingLabel.setLabelFor(myForegroundScalingRadioButtonsPanel);
    myForegroundEffectLabel.setLabelFor(myForegroundEffectRadioButtonsPanel);

    myBackgroundLayerNameLabel.setLabelFor(myBackgroundLayerNameTextField);
    myBackgroundAssetTypeLabel.setLabelFor(myBackgroundAssetRadioButtonsPanel);
    myBackgroundImagePathLabel.setLabelFor(myBackgroundImageAssetBrowser);
    myBackgroundTrimLabel.setLabelFor(myBackgroundTrimOptionsPanel);
    myBackgroundPaddingLabel.setLabelFor(myBackgroundPaddingSliderPanel);
    myBackgroundColorLabel.setLabelFor(myBackgroundColorPanel);
    myBackgroundScalingLabel.setLabelFor(myBackgroundScalingRadioButtonsPanel);
    myBackgroundEffectLabel.setLabelFor(myBackgroundEffectRadioButtonsPanel);
    myBackgroundColorLabel.setLabelFor(myBackgroundColorPanel);
    myBackgroundLegacyShapeLabel.setLabelFor(myBackgroundLegacyShapeComboBox);

    // Default the active asset type to "clipart", it's the most visually appealing and easy to
    // play around with.
    VectorAsset clipartAsset = myForegroundClipartAssetButton.getAsset();
    clipartAsset.outputWidth().set(CLIPART_RESOLUTION.width);
    clipartAsset.outputHeight().set(CLIPART_RESOLUTION.height);
    myForegroundActiveAsset = new ObjectValueProperty<>(clipartAsset);
    myForegroundClipartRadioButton.setSelected(true);

    // Set a reasonable default path for the background image
    myBackgroundImageAssetBrowser.getAsset().imagePath().set(ImageAsset.getTemplateImage("ic_image_back.png"));
    myBackgroundImageAsset = new OptionalValueProperty<>(myBackgroundImageAssetBrowser.getAsset());

    // For the background layer, use a simple plain color by default
    //myBackgroundColorRadioButton.setSelected(true);
    myBackgroundImageRadioButton.setSelected(true);

    initializeListenersAndBindings();
    initializeValidators();

    Disposer.register(disposableParent, this);
    for (AssetComponent assetComponent : myForegroundAssetPanelMap.values()) {
      Disposer.register(this, assetComponent);
    }
    add(myRootPanel);
  }

  private void initializeListenersAndBindings() {
    final BoolProperty foregroundTrimmed = new SelectedProperty(myForegroundTrimmedRadioButton);
    final BoolProperty backgroundTrimmed = new SelectedProperty(myBackgroundTrimmedRadioButton);

    final IntProperty foregroundPaddingPercent = new SliderValueProperty(myForegroundPaddingSlider);
    final StringProperty foregroundPaddingValueString = new TextProperty(myForegroundPaddingValueLabel);
    myGeneralBindings.bind(foregroundPaddingValueString, new FormatExpression("%d %%", foregroundPaddingPercent));

    final IntProperty backgroundPaddingPercent = new SliderValueProperty(myBackgroundPaddingSlider);
    final StringProperty backgroundPaddingValueString = new TextProperty(myBackgroundPaddingValueLabel);
    myGeneralBindings.bind(backgroundPaddingValueString, new FormatExpression("%d %%", backgroundPaddingPercent));

    myIgnoreForegroundColor = new SelectedProperty(myForegroundImageRadioButton);
    myForegroundColor = new OptionalToValuePropertyAdapter<>(new ColorProperty(myForegroundColorPanel));
    myBackgroundColor = new OptionalToValuePropertyAdapter<>(new ColorProperty(myBackgroundColorPanel));
    myForegroundCropped = new SelectedProperty(myForegroundCropRadioButton);
    // Effects not yet supported
    //myForegroundDogEared = new SelectedProperty(myForegroundDogEarRadioButton);
    myBackgroundCropped = new SelectedProperty(myBackgroundCropRadioButton);
    // Effects not yet supported
    //myBackgroundDogEared = new SelectedProperty(myBackgroundDogEarRadioButton);

    myLegacyShape = new OptionalToValuePropertyAdapter<>(new SelectedItemProperty<>(myBackgroundLegacyShapeComboBox));

    updateBindingsAndUiForActiveIconType();

    // Update foreground layer asset type depending on asset type radio buttons
    ActionListener radioSelectedListener = e -> {
      JRadioButton source = ((JRadioButton)e.getSource());
      AssetComponent assetComponent = myForegroundAssetPanelMap.get(source);
      myForegroundActiveAsset.set(assetComponent.getAsset());
    };
    myForegroundClipartRadioButton.addActionListener(radioSelectedListener);
    myForegroundImageRadioButton.addActionListener(radioSelectedListener);
    myForegroundTextRadioButton.addActionListener(radioSelectedListener);

    // Update background asset depending on asset type radio buttons
    myBackgroundImageRadioButton.addActionListener(e -> myBackgroundImageAsset.setValue(myBackgroundImageAssetBrowser.getAsset()));
    myBackgroundColorRadioButton.addActionListener(e -> myBackgroundImageAsset.clear());

    // If any of our underlying asset panels change, we should pass that on to anyone listening to
    // us as well.
    ActionListener assetPanelListener = e -> fireAssetListeners();
    for (AssetComponent assetComponent : myForegroundAssetPanelMap.values()) {
      assetComponent.addAssetListener(assetPanelListener);
    }
    myBackgroundImageAssetBrowser.addAssetListener(assetPanelListener);

    final Runnable onAssetModified = this::fireAssetListeners;
    myListeners
      .listenAll(foregroundTrimmed, foregroundPaddingPercent, myForegroundCropped, myForegroundColor,
                 backgroundTrimmed, backgroundPaddingPercent, myBackgroundCropped, myBackgroundColor,
                 myLegacyShape)
      .with(onAssetModified);

    myListeners.listenAndFire(myForegroundActiveAsset, sender -> {
      myForegroundActiveAssetBindings.releaseAll();
      myForegroundActiveAssetBindings.bindTwoWay(foregroundTrimmed, myForegroundActiveAsset.get().trimmed());
      myForegroundActiveAssetBindings.bindTwoWay(foregroundPaddingPercent, myForegroundActiveAsset.get().paddingPercent());
      myForegroundActiveAssetBindings.bindTwoWay(myForegroundColor, myForegroundActiveAsset.get().color());

      getIconGenerator().sourceAsset().setValue(myForegroundActiveAsset.get());
      onAssetModified.run();
    });

    // When switching between Image/Color for background, bind corresponding properties and regenerate asset (to be sure)
    myListeners.listenAndFire(myBackgroundImageAsset, sender -> {
      myBackgroundActiveAssetBindings.releaseAll();
      if (myBackgroundImageAsset.getValueOrNull() != null) {
        myBackgroundActiveAssetBindings.bindTwoWay(backgroundTrimmed, myBackgroundImageAsset.getValue().trimmed());
        myBackgroundActiveAssetBindings.bindTwoWay(backgroundPaddingPercent, myBackgroundImageAsset.getValue().paddingPercent());
      }

      getIconGenerator().backgroundImageAsset().setNullableValue(myBackgroundImageAsset.getValueOrNull());
      onAssetModified.run();
    });

    BooleanExpression isClipartOrText =
      new BooleanExpression(myForegroundActiveAsset) {
        @NotNull
        @Override
        public Boolean get() {
          return myForegroundClipartAssetButton.getAsset() == myForegroundActiveAsset.get() ||
                 myForegroundTextAssetEditor.getAsset() == myForegroundActiveAsset.get();
        }
      };

    /*
     * Hook up a bunch of UI <- boolean expressions, so that when certain conditions are met,
     * various components show/hide. This also requires refreshing the panel explicitly, as
     * otherwise Swing doesn't realize it should trigger a relayout.
     */
    ImmutableMap.Builder<BoolProperty, ObservableBool> layoutPropertiesBuilder = ImmutableMap.builder();
    layoutPropertiesBuilder.put(new VisibleProperty(myForegroundImageAssetRowPanel), new SelectedProperty(myForegroundImageRadioButton));
    layoutPropertiesBuilder
      .put(new VisibleProperty(myForegroundClipartAssetRowPanel), new SelectedProperty(myForegroundClipartRadioButton));
    layoutPropertiesBuilder.put(new VisibleProperty(myForegroundTextAssetRowPanel), new SelectedProperty(myForegroundTextRadioButton));
    layoutPropertiesBuilder.put(new VisibleProperty(myForegroundColorRowPanel), isClipartOrText);
    layoutPropertiesBuilder.put(new VisibleProperty(myForegroundEffectPanel), new BoolValueProperty(false));

    // Show either the image or the color UI controls
    layoutPropertiesBuilder.put(new VisibleProperty(myBackgroundImageAssetRowPanel), myBackgroundImageAsset.isPresent());
    layoutPropertiesBuilder.put(new VisibleProperty(myBackgroundTrimRowPanel), myBackgroundImageAsset.isPresent());
    layoutPropertiesBuilder.put(new VisibleProperty(myBackgroundPaddingRowPanel), myBackgroundImageAsset.isPresent());
    layoutPropertiesBuilder.put(new VisibleProperty(myBackgroundScalingRowPanel), myBackgroundImageAsset.isPresent());
    layoutPropertiesBuilder.put(new VisibleProperty(myBackgroundColorRowPanel), myBackgroundImageAsset.isPresent().not());

    // Don't show effects for now (they are not supported)
    layoutPropertiesBuilder.put(new VisibleProperty(myBackgroundEffectPanel), new BoolValueProperty(false));

    ImmutableMap<BoolProperty, ObservableBool> layoutProperties = layoutPropertiesBuilder.build();
    for (Map.Entry<BoolProperty, ObservableBool> e : layoutProperties.entrySet()) {
      // Initialize everything off, as this makes sure the frame that uses this panel won't start
      // REALLY LARGE by default.
      e.getKey().set(false);
      myGeneralBindings.bind(e.getKey(), e.getValue());
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
    VisibleProperty isActive = new VisibleProperty(getRootComponent());

    // Validate the API level when our panel is active
    Expression<AndroidVersion> targetSdkVersion = new Expression<AndroidVersion>(isActive) {
      @NotNull
      @Override
      public AndroidVersion get() {
        return myTargetSdkVersion;
      }
    };
    myValidatorPanel.registerValidator(targetSdkVersion, targetSdk -> {
      if (isActive.get() && targetSdk.getFeatureLevel() < 26) {
        return new Validator.Result(Validator.Severity.ERROR, "Project must target API 26 or later to use adaptive icons");
      }
      else {
        return Validator.Result.OK;
      }
    });

    // Validate foreground layer name when our panel is active
    StringExpression foregroundName = new StringExpression(isActive, myForegroundLayerName) {
      @NotNull
      @Override
      public String get() {
        return myForegroundLayerName.get();
      }
    };
    myValidatorPanel.registerValidator(foregroundName, name -> {
      String trimmedName = name.trim();
      if (isActive.get() && trimmedName.isEmpty()) {
        return new Validator.Result(Validator.Severity.ERROR, "Foreground layer name must be set");
      }
      else {
        return Validator.Result.OK;
      }
    });

    // Validate background layer name when our panel is active
    StringExpression backgroundName = new StringExpression(isActive, myBackgroundLayerName) {
      @NotNull
      @Override
      public String get() {
        return myBackgroundLayerName.get();
      }
    };
    myValidatorPanel.registerValidator(backgroundName, name -> {
      String trimmedName = name.trim();
      if (isActive.get() && trimmedName.isEmpty()) {
        return new Validator.Result(Validator.Severity.ERROR, "Background layer name must be set");
      }
      else {
        return Validator.Result.OK;
      }
    });
  }

  /**
   * Return an icon generator which will create Android icons using the panel's current settings.
   */
  @Override
  @NotNull
  public AndroidAdaptiveIconGenerator getIconGenerator() {
    return myIconGenerator;
  }

  @NotNull
  @Override
  public JComponent getRootComponent() {
    return this;
  }

  /**
   * Add a listener which will be triggered whenever the asset represented by this panel is
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
    myOutputName.set(AndroidAdaptiveIconType.ADAPTIVE.toOutputName("name"));
    myForegroundLayerName.set("ic_image_foreground");
    myBackgroundLayerName.set("ic_image_background");

    myGeneralBindings.bind(myIconGenerator.sourceAsset(), new AsOptionalExpression<>(myForegroundActiveAsset));
    myGeneralBindings.bind(myIconGenerator.name(), myOutputName);
    myGeneralBindings.bindTwoWay(myIconGenerator.backgroundImageAsset(), myBackgroundImageAsset);

    AndroidAdaptiveIconGenerator adaptiveIconGenerator = myIconGenerator;
    myGeneralBindings.bind(adaptiveIconGenerator.useForegroundColor(), myIgnoreForegroundColor.not());
    myGeneralBindings.bindTwoWay(myForegroundColor, adaptiveIconGenerator.foregroundColor());
    myGeneralBindings.bindTwoWay(myBackgroundColor, adaptiveIconGenerator.backgroundColor());
    myGeneralBindings.bindTwoWay(myForegroundCropped, adaptiveIconGenerator.foregroundCropped());
    myGeneralBindings.bindTwoWay(myBackgroundCropped, adaptiveIconGenerator.backgroundCropped());
    myGeneralBindings.bindTwoWay(myLegacyShape, adaptiveIconGenerator.legacyShape());
    myGeneralBindings.bindTwoWay(myShowGridProperty, adaptiveIconGenerator.showGrid());
    myGeneralBindings.bindTwoWay(myShowSafeZoneProperty, adaptiveIconGenerator.showSafeZone());
    myGeneralBindings.bindTwoWay(myPreviewDensityProperty, adaptiveIconGenerator.previewDensity());
    myGeneralBindings.bindTwoWay(adaptiveIconGenerator.foregroundLayerName(), myForegroundLayerName);
    myGeneralBindings.bindTwoWay(adaptiveIconGenerator.backgroundLayerName(), myBackgroundLayerName);
  }

  @Override
  public void dispose() {
    myGeneralBindings.releaseAll();
    myForegroundActiveAssetBindings.releaseAll();
    myBackgroundActiveAssetBindings.releaseAll();
    myListeners.releaseAll();
    myAssetListeners.clear();
  }
}
