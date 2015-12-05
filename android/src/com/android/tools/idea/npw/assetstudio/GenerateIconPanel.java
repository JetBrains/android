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

package com.android.tools.idea.npw.assetstudio;

import com.android.assetstudiolib.NotificationIconGenerator;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.project.AndroidProjectPaths;
import com.android.tools.idea.ui.ImageComponent;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.ui.properties.expressions.string.FormatExpression;
import com.android.tools.idea.ui.properties.swing.SelectedProperty;
import com.android.tools.idea.ui.properties.swing.SliderValueProperty;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.android.tools.idea.ui.wizard.Validator;
import com.android.tools.idea.ui.wizard.ValidatorPanel;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.IconUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

/**
 * A panel which presents a UI for selecting some source asset and converting it to a target set of
 * Android Icons. See {@link AndroidIconType} for the types of icons this can generate.
 *
 * Before generating icons, you should first check {@link #hasErrors()} to make sure there won't be
 * any errors in the generation process.
 *
 * This is a Swing port of the various icon generators provided by the
 * <a href="https://romannurik.github.io/AndroidAssetStudio/index.html">Asset Studio</a>
 * web application.
 */
public final class GenerateIconPanel extends JPanel implements Disposable {

  @NotNull private final AndroidFacet myFacet;
  private final AssetStudioAssetGenerator myAssetGenerator = new AssetStudioAssetGenerator();

  private final ValidatorPanel myValidatorPanel;

  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  // Depending on which asset radio button is selected, the active asset changes
  private final ObjectProperty<BaseAsset> myActiveAsset;
  private final StringProperty myOutputName;
  private final List<GeneratedIconsPanel> myOutputPreviewPanels;

  private JPanel myRootPanel;
  private JRadioButton myClipartRadioButton;
  private JRadioButton myTextRadioButton;
  private JRadioButton myImageRadioButton;
  private JPanel mySourceAssetOptionsPanel;
  private JPanel mySourceAssetTypePanel;
  private JPanel myAssetTypesPanel;
  private JPanel myConfigureCommonPanel;
  private JBLabel myTrimLabel;
  private JRadioButton myTrimmedRadioButton;
  private JRadioButton myNotTrimmedRadioButton;
  private JBLabel myPaddingLabel;
  private JPanel myTrimOptionsPanel;
  private JSlider myPaddingSlider;
  private JLabel myPaddingValueLabel;
  private JBLabel myOutputNameLabel;
  private JPanel myAssetRadioButtonsPanel;
  private JPanel myPaddingSliderPanel;
  private JPanel mySourceAssetPreviewPanel;
  private ImageComponent mySourceAssetPreviewImage;
  private JPanel mySourceAssetPreviewBorder;
  private JPanel myOutputPreviewPanel;
  private JTextField myOutputNameTextField;
  private JPanel myOutputNamePanel;
  private JBLabel myOutputPreviewLabel;
  private JPanel mySourceAssetPanel;
  private JPanel myTrimRowPanel;
  private JPanel myNameRowPanel;
  private JPanel myPaddingRowPanel;

  @NotNull private AndroidIconType myOutputType;

  @Nullable private AndroidProjectPaths myProjectPaths;
  @Nullable private BufferedImage myEnqueuedImageToProcess;
  @NotNull private String myOutputNameWithoutSuffix = "";

  /**
   * Create a panel which can generate Android icons. The supported types passed in will be
   * presented to the user in a pulldown menu (unless there's only one supported type). If no
   * supported types are passed in, then all types will be supported by default.
   */
  public GenerateIconPanel(@NotNull Disposable disposableParent, @NotNull AndroidFacet facet, @NotNull AndroidIconType... supportedTypes) {
    super(new BorderLayout());

    if (supportedTypes.length == 0) {
      supportedTypes = AndroidIconType.values();
    }

    myFacet = facet;

    myOutputPreviewPanels = ImmutableList
      .of(new GeneratedIconsPanel(NotificationIconGenerator.Version.V11.getDisplayName(), "API 11+", GeneratedIconsPanel.Theme.DARK),
          new GeneratedIconsPanel(NotificationIconGenerator.Version.V9.getDisplayName(), "API 9+", GeneratedIconsPanel.Theme.LIGHT),
          new GeneratedIconsPanel(NotificationIconGenerator.Version.OLDER.getDisplayName(), "Older APIs", GeneratedIconsPanel.Theme.GRAY));

    for (GeneratedIconsPanel iconsPanel : myOutputPreviewPanels) {
      myOutputPreviewPanel.add(iconsPanel);
    }

    Disposer.register(disposableParent, this);
    myValidatorPanel = new ValidatorPanel(this, myRootPanel);

    myOutputName = new TextProperty(myOutputNameTextField);

    final ClipartAssetPanel clipartAssetPanel = new ClipartAssetPanel();
    final ImageAssetPanel imageAssetPanel = new ImageAssetPanel();
    final TextAssetPanel textAssetPanel = new TextAssetPanel();

    assert myAssetTypesPanel.getLayout() instanceof CardLayout;
    myAssetTypesPanel.add(myClipartRadioButton.getText(), clipartAssetPanel);
    myAssetTypesPanel.add(myImageRadioButton.getText(), imageAssetPanel);
    myAssetTypesPanel.add(myTextRadioButton.getText(), textAssetPanel);

    // Default the active asset type to "clipart", it's the most visually appealing and easy to
    // play around with.
    myActiveAsset = new ObjectValueProperty<BaseAsset>(clipartAssetPanel.getAsset());
    myClipartRadioButton.setSelected(true);

    // TODO: Add a pulldown of all supported types and change contents dynamically when changed.
    myOutputType = supportedTypes[0];
    setOutputName(myOutputType.toOutputName(""));

    initializeListenersAndBindings();
    initializeValidators();
    renderIconPreviews();

    Disposer.register(this, myValidatorPanel);
    add(myValidatorPanel);
  }

  private void initializeListenersAndBindings() {
    ActionListener radioSelectedListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String radioName = ((JRadioButton)e.getSource()).getText();
        ((CardLayout)myAssetTypesPanel.getLayout()).show(myAssetTypesPanel, radioName);

        // No simple way to get the panel just shown from CardLayout. Run through manually...
        for (Component component : myAssetTypesPanel.getComponents()) {
          if (component.isVisible()) {
            AssetPanel assetPanel = ((AssetPanel)component);
            myActiveAsset.set(assetPanel.getAsset());
            break;
          }
        }
      }
    };
    myClipartRadioButton.addActionListener(radioSelectedListener);
    myImageRadioButton.addActionListener(radioSelectedListener);
    myTextRadioButton.addActionListener(radioSelectedListener);

    ActionListener assetPanelListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        renderIconPreviews();
      }
    };
    for (int i = 0; i < myAssetTypesPanel.getComponentCount(); i++) {
      ((AssetPanel)myAssetTypesPanel.getComponent(i)).addActionListener(assetPanelListener);
    }

    final BoolProperty trimmed = new SelectedProperty(myTrimmedRadioButton);
    final IntProperty paddingPercent = new SliderValueProperty(myPaddingSlider);
    final StringProperty paddingValueString = new TextProperty(myPaddingValueLabel);
    myBindings.bind(paddingValueString, new FormatExpression("%d %%", paddingPercent));

    final Runnable onAssetModified = new Runnable() {
      @Override
      public void run() {
        renderIconPreviews();
      }
    };
    myListeners.listenAndFire(myActiveAsset, new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        myListeners.release(onAssetModified);
        myListeners.listenAll(myActiveAsset.get().trimmed(), myActiveAsset.get().paddingPercent()).with(onAssetModified);

        myBindings.releaseTwoWay(trimmed);
        myBindings.releaseTwoWay(paddingPercent);
        myBindings.bindTwoWay(trimmed, myActiveAsset.get().trimmed());
        myBindings.bindTwoWay(paddingPercent, myActiveAsset.get().paddingPercent());

        renderIconPreviews();
      }
    });
  }

  private void initializeValidators() {
    myValidatorPanel.registerValidator(myOutputName, new Validator<String>() {
      @NotNull
      @Override
      public Result validate(@NotNull String outputName) {
        String trimmedName = outputName.trim();
        if (trimmedName.isEmpty()) {
          return new Result(Severity.ERROR, "Icon name must be set");
        }
        else if (iconExists(trimmedName)) {
          return new Result(Severity.WARNING, "An icon already exists with the same name and will be overwritten.");
        }
        else {
          return Result.OK;
        }
      }
    });
  }

  /**
   * Set the target project paths that this panel should use when generating assets. If set to
   * {@code null}, a default resource path will be selected based on the current module.
   */
  public void setProjectPaths(@Nullable AndroidProjectPaths projectPaths) {
    myProjectPaths = projectPaths;
    // Refresh output name as paths have changed - potentially removing previously existing
    // conflicts
    setOutputName(myOutputNameWithoutSuffix);
  }

  /**
   * Set the output icon filename programmatically. If a resource already exists at that location, a
   * numerical suffix will be appended to make the name unique.
   */
  public void setOutputName(@NotNull String iconName) {
    myOutputNameWithoutSuffix = iconName;
    String suffix = "";
    int i = 2;
    while (iconExists(iconName + suffix)) {
      suffix = Integer.toString(i);
      i++;
    }

    myOutputName.set(iconName);
  }

  /**
   * Return an icon generator which will create Android icons using the panel's current settings.
   */
  @NotNull
  public IconGenerator createIconGenerator() {
    // TODO: Handle all other android icon types, not just notification
    return new IconGenerator(AndroidIconType.NOTIFICATION, myActiveAsset.get(), myOutputName.get());
  }

  /**
   * A boolean property which will be true if validation logic catches any problems with any of the
   * current icon settings, particularly the output name / path. You should probably not generate
   * icons if there are any errors.
   */
  @NotNull
  public ObservableBool hasErrors() {
    return myValidatorPanel.hasErrors();
  }

  private boolean iconExists(@NotNull String iconName) {
    if (myProjectPaths != null) {
      return AssetStudioUtils.resourceExists(myProjectPaths, ResourceFolderType.DRAWABLE, iconName);
    }
    else {
      return AssetStudioUtils.resourceExists(myFacet, ResourceType.DRAWABLE, iconName);
    }
  }

  private void renderIconPreviews() {
    BufferedImage assetImage = myActiveAsset.get().toImage();
    // TODO: Consider changing the aspect ratio of mySourceAssetPreviewImage to match assetImage
    mySourceAssetPreviewImage.setIcon(IconUtil.createImageIcon(assetImage));

    enqueueGenerateNotificationIcons(assetImage);
  }

  /**
   * Generating notification icons is not a lightweight process, and if we try to do it
   * synchronously, it stutters the UI. So instead we enqueue the request to run on a background
   * thread. If several requests are made in a row while an existing worker is still in progress,
   * only the most recently added will be handled, whenever the worker finishes.
   */
  private void enqueueGenerateNotificationIcons(@NotNull final BufferedImage assetImage) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    boolean currentlyWorking = myEnqueuedImageToProcess != null;
    myEnqueuedImageToProcess = assetImage;

    if (currentlyWorking) {
      return;
    }

    final Map<String, Map<String, BufferedImage>> assetMap = AssetStudioAssetGenerator.newAssetMap();
    SwingWorker<Void, Void> generateIconsWorker = new SwingWorker<Void, Void>() {
      @Override
      protected Void doInBackground() throws Exception {
        myAssetGenerator.generateNotificationIconsIntoMap(assetImage, assetMap, myOutputName.get());
        return null;
      }

      @Override
      protected void done() {
        for (GeneratedIconsPanel outputPreviewPanel : myOutputPreviewPanels) {
          outputPreviewPanel.updateImages(assetMap);
        }

        BufferedImage nextImage = null;
        if (myEnqueuedImageToProcess != assetImage) {
          nextImage = myEnqueuedImageToProcess;
        }
        myEnqueuedImageToProcess = null;

        if (nextImage != null) {
          final BufferedImage finalNextImage = nextImage;
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              enqueueGenerateNotificationIcons(finalNextImage);
            }
          });
        }
      }
    };

    generateIconsWorker.execute();
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }
}
