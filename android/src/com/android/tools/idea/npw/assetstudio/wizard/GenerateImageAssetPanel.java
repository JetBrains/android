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

package com.android.tools.idea.npw.assetstudio.wizard;

import com.android.assetstudiolib.NotificationIconGenerator;
import com.android.tools.idea.npw.assetstudio.icon.AndroidAdaptiveIconType;
import com.android.tools.idea.npw.assetstudio.icon.AndroidIconGenerator;
import com.android.tools.idea.npw.assetstudio.icon.CategoryIconMap;
import com.android.tools.idea.npw.assetstudio.ui.ConfigureAdaptiveIconPanel;
import com.android.tools.idea.npw.assetstudio.ui.PreviewIconsPanel;
import com.android.tools.idea.npw.project.AndroidProjectPaths;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import com.android.tools.idea.ui.properties.expressions.value.AsValueExpression;
import com.android.tools.idea.ui.properties.swing.SelectedItemProperty;
import com.android.tools.idea.ui.validation.Validator;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;

/**
 * A panel which presents a UI for selecting some source asset and converting it to a target set of
 * Android Icons. See {@link AndroidAdaptiveIconType} for the types of icons this can generate.
 *
 * Before generating icons, you should first check {@link #hasErrors()} to make sure there won't be
 * any errors in the generation process.
 *
 * This is a Swing port of the various icon generators provided by the
 * <a href="https://romannurik.github.io/AndroidAssetStudio/index.html">Asset Studio</a>
 * web application.
 */
public final class GenerateImageAssetPanel extends JPanel implements Disposable {

  private final AndroidProjectPaths myDefaultPaths;
  private final ValidatorPanel myValidatorPanel;

  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  private final Map<AndroidAdaptiveIconType, PreviewIconsPanel> myOutputPreviewPanels;
  private final ObservableValue<AndroidAdaptiveIconType> myOutputIconType;
  private final StringProperty myOutputName = new StringValueProperty();

  private JPanel myRootPanel;
  private JComboBox<AndroidAdaptiveIconType> myIconTypeCombo;
  private JPanel myConfigureIconPanels;
  private CheckeredBackgroundPanel myOutputPreviewPanel;
  private JBLabel myOutputPreviewLabel;
  private JBScrollPane myOutputPreviewScrollPane;
  private JSplitPane mySplitPane;

  @NotNull private AndroidProjectPaths myPaths;
  @Nullable private BufferedImage myEnqueuedImageToProcess;

  /**
   * Create a panel which can generate Android icons. The supported types passed in will be
   * presented to the user in a pulldown menu (unless there's only one supported type). If no
   * supported types are passed in, then all types will be supported by default.
   */
  public GenerateImageAssetPanel(@NotNull Disposable disposableParent,
                                 @NotNull AndroidProjectPaths defaultPaths,
                                 int minSdkVersion,
                                 @NotNull AndroidAdaptiveIconType... supportedTypes) {
    super(new BorderLayout());

    myDefaultPaths = defaultPaths;
    myPaths = myDefaultPaths;

    if (supportedTypes.length == 0) {
      supportedTypes = AndroidAdaptiveIconType.values();
    }

    DefaultComboBoxModel<AndroidAdaptiveIconType> supportedTypesModel = new DefaultComboBoxModel<>(supportedTypes);
    myIconTypeCombo.setModel(supportedTypesModel);
    myIconTypeCombo.setVisible(supportedTypes.length > 1);
    myOutputIconType = new AsValueExpression<>(new SelectedItemProperty<>(myIconTypeCombo));

    assert myConfigureIconPanels.getLayout() instanceof CardLayout;
    for (AndroidAdaptiveIconType iconType : supportedTypes) {
      myConfigureIconPanels.add(new ConfigureAdaptiveIconPanel(this, iconType, minSdkVersion), iconType.toString());
    }

    ImmutableMap.Builder<AndroidAdaptiveIconType, PreviewIconsPanel> previewPanelBuilder = ImmutableMap.builder();
    previewPanelBuilder.put(AndroidAdaptiveIconType.ACTIONBAR, new PreviewIconsPanel("", PreviewIconsPanel.Theme.TRANSPARENT));
    previewPanelBuilder.put(AndroidAdaptiveIconType.LAUNCHER_LEGACY, new PreviewIconsPanel("", PreviewIconsPanel.Theme.TRANSPARENT));
    previewPanelBuilder.put(AndroidAdaptiveIconType.ADAPTIVE, new PreviewIconsPanel("", PreviewIconsPanel.Theme.TRANSPARENT));
    previewPanelBuilder.put(AndroidAdaptiveIconType.NOTIFICATION, new PreviewIconsPanel("",
                                                                                        PreviewIconsPanel.Theme.DARK,
                                                                                        new CategoryIconMap.NotificationFilter(
                                                                                  NotificationIconGenerator.Version.V11)));
    myOutputPreviewPanels = previewPanelBuilder.build();

    WrappedFlowLayout previewLayout = new WrappedFlowLayout(FlowLayout.LEADING);
    previewLayout.setAlignOnBaseline(true);
    myOutputPreviewPanel.setLayout(previewLayout);
    myOutputPreviewScrollPane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(@NotNull ComponentEvent event) {
        // When resizing the JScrollPane, we want the WrappedFlowLayout to re-layout components
        // based on the new size (i.e. width) of the viewport.
        myOutputPreviewPanel.revalidate();
      }
    });

    // Replace the JSplitPane component with a Splitter (IntelliJ look & feel).
    //
    // Note: We set the divider location on the JSplitPane from the left component preferred size to override the
    //       default divider location of the new Splitter (the default is to put the divider in the middle).
    mySplitPane.setDividerLocation(mySplitPane.getLeftComponent().getPreferredSize().width);
    GuiUtils.replaceJSplitPaneWithIDEASplitter(mySplitPane);

    initializeListenersAndBindings();

    myValidatorPanel = new ValidatorPanel(this, myRootPanel);
    initializeValidators();

    Disposer.register(disposableParent, this);
    Disposer.register(this, myValidatorPanel);
    add(myValidatorPanel);
  }

  private void initializeListenersAndBindings() {
    ActionListener onAssetModified = actionEvent -> renderIconPreviews();

    for (Component component : myConfigureIconPanels.getComponents()) {
      ((ConfigureAdaptiveIconPanel)component).addAssetListener(onAssetModified);
    }

    myListeners.receiveAndFire(myOutputIconType, iconType -> {
      ((CardLayout)myConfigureIconPanels.getLayout()).show(myConfigureIconPanels, iconType.toString());

      ConfigureAdaptiveIconPanel iconPanel = getActiveIconPanel();
      myBindings.bind(myOutputName, iconPanel.outputName());
      if (iconType == AndroidAdaptiveIconType.NOTIFICATION) {
        myOutputPreviewLabel.setText("Preview (API 11+)");
      }
      else {
        myOutputPreviewLabel.setText("Preview");
      }
      renderIconPreviews();
    });
  }

  private void updateOutputPreviewPanel() {
    myOutputPreviewPanel.removeAll();

    PreviewIconsPanel iconsPanel = myOutputPreviewPanels.get(myOutputIconType.get());
    for (PreviewIconsPanel.IconPreviewInfo previewInfo : iconsPanel.getIconPreviewInfos()) {
      ImagePreviewPanel previewPanel = new ImagePreviewPanel();
      previewPanel.setLabelText(previewInfo.getLabel());
      previewPanel.setImage(previewInfo.getImageIcon());
      previewPanel.setImageBackground(previewInfo.getImageBackground());
      previewPanel.setImageOpaque(previewInfo.isImageOpaque());
      previewPanel.setImageBorder(previewInfo.getImageBorder());

      myOutputPreviewPanel.add(previewPanel.getComponent());
    }
    myOutputPreviewPanel.revalidate();
    myOutputPreviewPanel.repaint();
  }

  @NotNull
  private ConfigureAdaptiveIconPanel getActiveIconPanel() {
    for (Component component : myConfigureIconPanels.getComponents()) {
      if (component.isVisible()) {
        return (ConfigureAdaptiveIconPanel)component;
      }
    }

    throw new IllegalStateException("GenerateIconPanel configured incorrectly. Please report this error.");
  }

  private void initializeValidators() {
    myValidatorPanel.registerValidator(myOutputName, outputName -> {
      String trimmedName = outputName.trim();
      if (trimmedName.isEmpty()) {
        return new Validator.Result(Validator.Severity.ERROR, "Icon name must be set");
      }
      else if (iconExists()) {
        return new Validator.Result(Validator.Severity.WARNING, "An icon with the same name already exists and will be overwritten.");
      }
      else {
        return Validator.Result.OK;
      }
    });
  }

  /**
   * Set the target project paths that this panel should use when generating assets. If not set,
   * this panel will attempt to use reasonable defaults for the project.
   */
  public void setProjectPaths(@Nullable AndroidProjectPaths projectPaths) {
    myPaths = (projectPaths != null) ? projectPaths : myDefaultPaths;
  }

  /**
   * Set the output name for the icon type currently being edited. This is exposed as some UIs may wish
   * to set this explicitly instead of relying on defaults.
   */
  public void setOutputName(@NotNull String name) {
    getActiveIconPanel().outputName().set(name);
  }

  /**
   * Return an icon generator which will create Android icons using the panel's current settings.
   */
  @NotNull
  public AndroidIconGenerator getIconGenerator() {
    return getActiveIconPanel().getIconGenerator();
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

  private boolean iconExists() {
    Map<File, BufferedImage> pathImageMap = getIconGenerator().generateIntoFileMap(myPaths);
    for (File path : pathImageMap.keySet()) {
      if (path.exists()) {
        return true;
      }
    }

    return false;
  }

  private void renderIconPreviews() {
    // This method is often called as the result of a UI property changing which may also cause
    // some other properties to change. Invoke its logic later just to make sure everything gets a
    // chance to settle first.
    ApplicationManager.getApplication().invokeLater(() -> {
      BufferedImage assetImage = getActiveIconPanel().getAsset().toImage();
      enqueueGenerateNotificationIcons(assetImage);
    }, ModalityState.any());
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

    SwingWorker<Void, Void> generateIconsWorker = new SwingWorker<Void, Void>() {

      @Nullable private CategoryIconMap myCategoryIconMap;

      @Override
      protected Void doInBackground() throws Exception {
        if (getIconGenerator().sourceAsset().get().isPresent()) {
          myCategoryIconMap = getIconGenerator().generateIntoMemory();
        }
        return null;
      }

      @Override
      protected void done() {
        if (myCategoryIconMap != null) {
          myOutputPreviewPanels.get(myOutputIconType.get()).updateImages(myCategoryIconMap);
          updateOutputPreviewPanel();
          myCategoryIconMap = null;
        }

        // At this point, we've finished preparing the previews. Let's see if another request to
        // render more previews was added while we were working, and if so, start on it right away.
        BufferedImage nextImage = null;
        if (myEnqueuedImageToProcess != assetImage) {
          nextImage = myEnqueuedImageToProcess;
        }
        myEnqueuedImageToProcess = null;

        if (nextImage != null) {
          final BufferedImage finalNextImage = nextImage;
          ApplicationManager.getApplication().invokeLater(() -> enqueueGenerateNotificationIcons(finalNextImage), ModalityState.any());
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