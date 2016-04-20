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

import com.android.assetstudiolib.ActionBarIconGenerator;
import com.android.assetstudiolib.GraphicGenerator;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import com.android.tools.idea.npw.assetstudio.icon.AndroidActionBarIconGenerator;
import com.android.tools.idea.npw.assetstudio.icon.AndroidIconGenerator;
import com.android.tools.idea.npw.assetstudio.icon.AndroidIconType;
import com.android.tools.idea.npw.assetstudio.icon.AndroidLauncherIconGenerator;
import com.android.tools.idea.npw.assetstudio.wizard.GenerateIconsPanel;
import com.android.tools.idea.ui.properties.*;
import com.android.tools.idea.ui.properties.adapters.OptionalToValuePropertyAdapter;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.ui.properties.expressions.bool.BooleanExpression;
import com.android.tools.idea.ui.properties.expressions.optional.AsOptionalExpression;
import com.android.tools.idea.ui.properties.expressions.string.FormatExpression;
import com.android.tools.idea.ui.properties.swing.*;
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
 * options used by each {@link AndroidIconType}, but the relevant options are shown / hidden based
 * on the exact type passed into the constructor.
 *
 * See also {@link GenerateIconsPanel} which owns a couple of these panels, one for each
 * {@link AndroidIconType}.
 */
public final class ConfigureIconPanel extends JPanel implements Disposable {

  /**
   * Source material icons are provided in a vector graphics format, but their default resolution
   * is very low (24x24). Since we plan to render them to much larger icons, we will up the detail
   * a fair bit.
   */
  private static final Dimension CLIPART_RESOLUTION = new Dimension(256, 256);

  @NotNull private final List<ActionListener> myAssetListeners = Lists.newArrayListWithExpectedSize(1);

  @NotNull private final AndroidIconType myIconType;
  @NotNull private final AndroidIconGenerator myIconGenerator;

  private final BindingsManager myGeneralBindings = new BindingsManager();
  private final BindingsManager myActiveAssetBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  /**
   * This panel presents a list of radio buttons (clipart, image, text), and whichever one is
   * selected sets the active asset.
   */
  private final ObjectProperty<BaseAsset> myActiveAsset;
  private final StringProperty myOutputName;

  private final ImmutableMap<JRadioButton, ? extends AssetComponent> myAssetPanelMap;

  // @formatter:off
  private final Map<GraphicGenerator.Shape, String> myShapeNames = ImmutableMap.of(
    GraphicGenerator.Shape.NONE, "None",
    GraphicGenerator.Shape.CIRCLE, "Circle",
    GraphicGenerator.Shape.SQUARE, "Square",
    GraphicGenerator.Shape.VRECT, "Vertical",
    GraphicGenerator.Shape.HRECT, "Horizontal");
  // @formatter:on

  private JPanel myRootPanel;
  private JRadioButton myClipartRadioButton;
  private JRadioButton myTextRadioButton;
  private JRadioButton myImageRadioButton;
  private JPanel myAllOptionsPanel;
  private JPanel mySourceAssetTypePanel;
  private JPanel myIconOptionsPanel;
  private JRadioButton myTrimmedRadioButton;
  private JRadioButton myNotTrimmedRadioButton;
  private JPanel myTrimOptionsPanel;
  private JSlider myPaddingSlider;
  private JLabel myPaddingValueLabel;
  private JPanel myAssetRadioButtonsPanel;
  private JPanel myPaddingSliderPanel;
  private JTextField myOutputNameTextField;
  private JPanel myOutputNamePanel;
  private JPanel myTrimRowPanel;
  private JPanel myNameRowPanel;
  private JPanel myPaddingRowPanel;
  private JPanel myForegroundRowPanel;
  private ColorPanel myForegroundColorPanel;
  private JPanel myBackgroundRowPanel;
  private ColorPanel myBackgroundColorPanel;
  private JPanel myScalingRowPanel;
  private JPanel myShapeRowPanel;
  private JPanel myScalingRadioButtonsPanel;
  private JRadioButton myCropRadioButton;
  private JRadioButton myShrinkToFitRadioButton;
  private JPanel myEffectRadioButtonsPanel;
  private JRadioButton myNoEffectRadioButton;
  private JRadioButton myDogEarRadioButton;
  private JPanel myThemeRowPanel;
  private JComboBox myThemeComboBox;
  private JPanel myEffectRowPanel;
  private JBScrollPane myScrollPane;
  private JComboBox myShapeComboBox;
  private JPanel myCustomThemeRowPanel;
  private ColorPanel myCustomThemeColorPanel;
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

  private AbstractProperty<Color> myForegroundColor;
  private AbstractProperty<Color> myBackgroundColor;
  private BoolProperty myCropped;
  private BoolProperty myDogEared;
  private AbstractProperty<ActionBarIconGenerator.Theme> myTheme;
  private AbstractProperty<GraphicGenerator.Shape> myShape;
  private AbstractProperty<Color> myThemeColor;

  /**
   * Create a panel which can generate Android icons. The supported types passed in will be
   * presented to the user in a pulldown menu (unless there's only one supported type). If no
   * supported types are passed in, then all types will be supported by default.
   */
  public ConfigureIconPanel(@NotNull Disposable disposableParent, @NotNull AndroidIconType iconType) {
    super(new BorderLayout());

    myIconType = iconType;
    myIconGenerator = AndroidIconType.createIconGenerator(iconType);

    DefaultComboBoxModel themesModel = new DefaultComboBoxModel(ActionBarIconGenerator.Theme.values());
    myThemeComboBox.setModel(themesModel);

    DefaultComboBoxModel shapesModel = new DefaultComboBoxModel();
    for (GraphicGenerator.Shape shape : myShapeNames.keySet()) {
      shapesModel.addElement(shape);
    }
    myShapeComboBox.setRenderer(new ListCellRendererWrapper<GraphicGenerator.Shape>() {
      @Override
      public void customize(JList list, GraphicGenerator.Shape shape, int index, boolean selected, boolean hasFocus) {
        setText(myShapeNames.get(shape));
      }
    });
    myShapeComboBox.setModel(shapesModel);
    myShapeComboBox.setSelectedItem(GraphicGenerator.Shape.SQUARE);

    myScrollPane.getVerticalScrollBar().setUnitIncrement(10);
    myScrollPane.setBorder(IdeBorderFactory.createEmptyBorder());

    myOutputName = new TextProperty(myOutputNameTextField);

    // @formatter:off
    myAssetPanelMap = ImmutableMap.of(
      myImageRadioButton, myImageAssetBrowser,
      myClipartRadioButton, myClipartAssetButton,
      myTextRadioButton, myTextAssetEditor
    );
    // @formatter:on

    // Call "setLabelFor" in code instead of designer since designer is so inconsistent about
    // valid targets
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
    myActiveAsset = new ObjectValueProperty<BaseAsset>(clipartAsset);
    myClipartRadioButton.setSelected(true);

    initializeListenersAndBindings();

    Disposer.register(disposableParent, this);
    for (AssetComponent assetComponent : myAssetPanelMap.values()) {
      Disposer.register(this, assetComponent);
    }
    add(myRootPanel);
  }

  private void initializeListenersAndBindings() {
    final BoolProperty trimmed = new SelectedProperty(myTrimmedRadioButton);

    final IntProperty paddingPercent = new SliderValueProperty(myPaddingSlider);
    final StringProperty paddingValueString = new TextProperty(myPaddingValueLabel);
    myGeneralBindings.bind(paddingValueString, new FormatExpression("%d %%", paddingPercent));

    myForegroundColor = new OptionalToValuePropertyAdapter<Color>(new ColorProperty(myForegroundColorPanel));
    myBackgroundColor = new OptionalToValuePropertyAdapter<Color>(new ColorProperty(myBackgroundColorPanel));
    myCropped = new SelectedProperty(myCropRadioButton);
    myDogEared = new SelectedProperty(myDogEarRadioButton);

    myTheme = new OptionalToValuePropertyAdapter<ActionBarIconGenerator.Theme>(
      new SelectedItemProperty<ActionBarIconGenerator.Theme>(myThemeComboBox));
    myThemeColor = new OptionalToValuePropertyAdapter<Color>(new ColorProperty(myCustomThemeColorPanel));

    myShape = new OptionalToValuePropertyAdapter<GraphicGenerator.Shape>(new SelectedItemProperty<GraphicGenerator.Shape>(myShapeComboBox));

    updateBindingsAndUiForActiveIconType();

    ActionListener radioSelectedListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JRadioButton source = ((JRadioButton)e.getSource());
        AssetComponent assetComponent = myAssetPanelMap.get(source);
        myActiveAsset.set(assetComponent.getAsset());
      }
    };
    myClipartRadioButton.addActionListener(radioSelectedListener);
    myImageRadioButton.addActionListener(radioSelectedListener);
    myTextRadioButton.addActionListener(radioSelectedListener);

    // If any of our underlying asset panels change, we should pass that on to anyone listening to
    // us as well.
    ActionListener assetPanelListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        fireAssetListeners();
      }
    };
    for (AssetComponent assetComponent : myAssetPanelMap.values()) {
      assetComponent.addAssetListener(assetPanelListener);
    }

    final Runnable onAssetModified = new Runnable() {
      @Override
      public void run() {
        fireAssetListeners();
      }
    };
    myListeners
      .listenAll(trimmed, paddingPercent, myForegroundColor, myBackgroundColor, myCropped, myDogEared, myTheme, myThemeColor, myShape)
      .with(onAssetModified);

    myListeners.listenAndFire(myActiveAsset, new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        myActiveAssetBindings.releaseAll();
        myActiveAssetBindings.bindTwoWay(trimmed, myActiveAsset.get().trimmed());
        myActiveAssetBindings.bindTwoWay(paddingPercent, myActiveAsset.get().paddingPercent());
        myActiveAssetBindings.bindTwoWay(myForegroundColor, myActiveAsset.get().color());

        getIconGenerator().sourceAsset().setValue(myActiveAsset.get());
        onAssetModified.run();
      }
    });

    ObservableBool isLauncherIcon = new BoolValueProperty(myIconType.equals(AndroidIconType.LAUNCHER));
    ObservableBool isActionBarIcon = new BoolValueProperty(myIconType.equals(AndroidIconType.ACTIONBAR));
    ObservableBool isCustomTheme = myTheme.isEqualTo(ActionBarIconGenerator.Theme.CUSTOM);
    ObservableBool isClipartOrText = new BooleanExpression(myActiveAsset) {
      @NotNull
      @Override
      public Boolean get() {
        BaseAsset asset = myActiveAsset.get();
        return myClipartAssetButton.getAsset() == asset || myTextAssetEditor.getAsset() == asset;
      }
    };
    ObservableBool supportsEffects = new BooleanExpression(myShape) {
      @NotNull
      @Override
      public Boolean get() {
        GraphicGenerator.Shape shape = myShape.get();
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

    /**
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
    myListeners.listenAll(layoutProperties.keySet()).with(new Runnable() {
      @Override
      public void run() {
        SwingUtilities.updateComponentTreeUI(myAllOptionsPanel);
      }
    });
  }

  @NotNull
  public BaseAsset getAsset() {
    return myActiveAsset.get();
  }

  /**
   * Return an icon generator which will create Android icons using the panel's current settings.
   */
  @NotNull
  public AndroidIconGenerator getIconGenerator() {
    return myIconGenerator;
  }

  /**
   * Add a listener which will be triggered whenever the asset represented by this panel is
   * modified in any way.
   */
  public void addAssetListener(@NotNull ActionListener listener) {
    myAssetListeners.add(listener);
  }

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
    myOutputName.set(myIconType.toOutputName("name"));

    myGeneralBindings.bind(myIconGenerator.sourceAsset(), new AsOptionalExpression<BaseAsset>(myActiveAsset));
    myGeneralBindings.bind(myIconGenerator.name(), myOutputName);

    switch (myIconType) {
      case LAUNCHER:
        AndroidLauncherIconGenerator launcherIconGenerator = (AndroidLauncherIconGenerator)myIconGenerator;
        myGeneralBindings.bindTwoWay(myBackgroundColor, launcherIconGenerator.backgroundColor());
        myGeneralBindings.bindTwoWay(myCropped, launcherIconGenerator.cropped());
        myGeneralBindings.bindTwoWay(myShape, launcherIconGenerator.shape());
        myGeneralBindings.bindTwoWay(myDogEared, launcherIconGenerator.dogEared());
        break;

      case ACTIONBAR:
        AndroidActionBarIconGenerator actionBarIconGenerator = (AndroidActionBarIconGenerator)myIconGenerator;
        myGeneralBindings.bindTwoWay(myThemeColor, actionBarIconGenerator.customColor());
        myGeneralBindings.bindTwoWay(myTheme, actionBarIconGenerator.theme());
        break;

      case NOTIFICATION:
        // No special options
        break;
    }
  }

  @Override
  public void dispose() {
    myGeneralBindings.releaseAll();
    myActiveAssetBindings.releaseAll();
    myListeners.releaseAll();
    myAssetListeners.clear();
  }
}
