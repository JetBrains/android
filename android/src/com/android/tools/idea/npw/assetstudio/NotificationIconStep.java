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
import com.android.tools.idea.npw.template.RenderTemplateModel;
import com.android.tools.idea.npw.template.TemplateHandle;
import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.StringEvaluator;
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
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.ui.wizard.Validator;
import com.android.tools.idea.ui.wizard.ValidatorPanel;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * An AssetStudio wizard step that lets the user preview and then generate icons that can be used
 * by the Android notification system.
 *
 * This is a Swing port of the
 * <a href="https://romannurik.github.io/AndroidAssetStudio/icons-notification.html">Notification Icon Generator</a>
 * web application.
 */
public final class NotificationIconStep extends ModelWizardStep<RenderTemplateModel> {

  private final AssetStudioAssetGenerator myAssetGenerator = new AssetStudioAssetGenerator();

  private final StudioWizardStepPanel myStudioPanel;
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
  private JPanel myV11PanelContainer;
  private JPanel myV9PanelContainer;
  private JPanel myPreV9PanelContainer;
  private JTextField myOutputNameTextField;
  private JPanel myOutputNamePanel;
  private JBLabel myOutputPreviewLabel;
  private JPanel mySourceAssetPanel;

  @Nullable private BufferedImage myEnqueuedImageToProcess;

  public NotificationIconStep(@NotNull RenderTemplateModel model) {
    super(model, "Generate Notification Icons");

    myOutputPreviewPanels = ImmutableList
      .of(new GeneratedIconsPanel(NotificationIconGenerator.Version.V11.getDisplayName(), "API 11+", GeneratedIconsPanel.Theme.DARK),
          new GeneratedIconsPanel(NotificationIconGenerator.Version.V9.getDisplayName(), "API 9+", GeneratedIconsPanel.Theme.LIGHT),
          new GeneratedIconsPanel(NotificationIconGenerator.Version.OLDER.getDisplayName(), "Older APIs", GeneratedIconsPanel.Theme.GRAY));

    for (GeneratedIconsPanel iconsPanel : myOutputPreviewPanels) {
      myOutputPreviewPanel.add(iconsPanel);
    }

    myValidatorPanel = new ValidatorPanel(this, myRootPanel);
    myStudioPanel = new StudioWizardStepPanel(myValidatorPanel, "Convert a source asset into an Android notification icon");

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
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
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

    renderIconPreviews();
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myStudioPanel;
  }

  @Override
  protected void onEntering() {

    /**
     * Setup this icon's name (which is evaluated using rules set up in the template)
     */
    TemplateHandle templateHandle = getModel().getTemplateHandle();
    String iconNameExpression = templateHandle.getMetadata().getIconName();
    String iconName = null;
    if (iconNameExpression != null && !iconNameExpression.isEmpty()) {
      StringEvaluator evaluator = new StringEvaluator();
      iconName = evaluator.evaluate(iconNameExpression, getModel().getTemplateValues());
    }

    if (iconName == null) {
      // Shouldn't happen as long as the template is correct but just in case provide a default
      iconName = String.format(AndroidIconType.NOTIFICATION.getDisplayName(), "name");
    }

    String suffix = "";
    int i = 2;
    while (iconExists(iconName + suffix)) {
      suffix = Integer.toString(i);
      i++;
    }

    myOutputName.set(iconName);
  }

  private boolean iconExists(@NotNull String iconName) {
    File resDir = null;
    AndroidProjectPaths projectPaths = getModel().getPaths();
    if (projectPaths != null) {
      resDir = projectPaths.getResDirectory();
    }

    if (resDir != null) {
      return Parameter.existsResourceFile(resDir, ResourceFolderType.DRAWABLE, iconName);
    }
    else {
      return Parameter.existsResourceFile(getModel().getModule(), ResourceType.DRAWABLE, iconName);
    }
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  protected void onProceeding() {
    getModel().setIconGenerator(new IconGenerator(AndroidIconType.NOTIFICATION, myActiveAsset.get(), myOutputName.get()));
  }

  private void renderIconPreviews() {
    BufferedImage assetImage = myActiveAsset.get().toImage();
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
