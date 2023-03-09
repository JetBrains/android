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

import static com.android.tools.idea.npw.assetstudio.AssetStudioUtils.toLowerCamelCase;

import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.adtui.common.WrappedFlowLayout;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.StudioAndroidModuleInfo;
import com.android.tools.idea.npw.assetstudio.GeneratedIcon;
import com.android.tools.idea.npw.assetstudio.GeneratedImageIcon;
import com.android.tools.idea.npw.assetstudio.IconGenerator;
import com.android.tools.idea.npw.assetstudio.icon.AndroidIconType;
import com.android.tools.idea.npw.assetstudio.icon.IconGeneratorResult;
import com.android.tools.idea.npw.assetstudio.ui.ConfigureAdaptiveIconPanel;
import com.android.tools.idea.npw.assetstudio.ui.ConfigureIconPanel;
import com.android.tools.idea.npw.assetstudio.ui.ConfigureIconView;
import com.android.tools.idea.npw.assetstudio.ui.ConfigureTvBannerPanel;
import com.android.tools.idea.npw.assetstudio.ui.PreviewIconsPanel;
import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.VisibleProperty;
import com.android.tools.idea.projectsystem.AndroidModulePaths;
import com.android.tools.idea.rendering.DrawableRenderer;
import com.android.tools.idea.res.IdeResourceNameValidator;
import com.android.tools.idea.wizard.ui.CheckeredBackgroundPanel;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ui.LoadingDecorator;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.ui.AsyncProcessIcon;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
public final class GenerateImageAssetPanel extends JPanel implements Disposable, PersistentStateComponent<PersistentState> {
  private static final String OUTPUT_ICON_TYPE_PROPERTY = "outputIconType";

  @NotNull private final AndroidModulePaths myDefaultPaths;
  private final ValidatorPanel myValidatorPanel;

  @NotNull private final BindingsManager myBindings = new BindingsManager();
  @NotNull private final ListenerManager myListeners = new ListenerManager();

  private final Map<AndroidIconType, PreviewIconsPanel> myOutputPreviewPanels;
  private final ObjectProperty<AndroidIconType> myOutputIconType;
  private final StringProperty myOutputName = new StringValueProperty();

  private JPanel myRootPanel;
  private JComboBox<AndroidIconType> myIconTypeCombo;
  private JPanel myConfigureIconPanels;
  private final Map<AndroidIconType, ConfigureIconView> myConfigureIconViews = new TreeMap<>();
  private CheckeredBackgroundPanel myOutputPreviewPanel;
  private TitledSeparator myOutputPreviewLabel;
  private JBScrollPane myOutputPreviewScrollPane;
  private JSplitPane mySplitPane;
  private JCheckBox myShowGrid;
  private JCheckBox myShowSafeZone;
  private JComboBox<Density> myPreviewResolutionComboBox;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myPreviewPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myIconTypePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myPreviewTitlePanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myPreviewContentsPanel;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer
  private JPanel myOutputIconTypePanel;
  private final SelectedProperty myShowGridProperty;
  private final SelectedProperty myShowSafeZoneProperty;
  private final AbstractProperty<Density> myPreviewDensityProperty;
  private final JBLoadingPanel myLoadingPanel;

  @NotNull private AndroidModulePaths myPaths;
  @NotNull private final File myResFolder;
  @NotNull private final IconGenerationProcessor myIconGenerationProcessor = new IconGenerationProcessor();
  @NotNull private final StringProperty myPreviewRenderingError = new StringValueProperty();
  @NotNull private final IdeResourceNameValidator myNameValidator = IdeResourceNameValidator.forFilename(ResourceFolderType.DRAWABLE);

  /**
   * Create a panel which can generate Android icons. The supported types passed in will be
   * presented to the user in a dropdown menu (unless there's only one supported type). If no
   * supported types are passed in, then all types will be supported by default.
   */
  public GenerateImageAssetPanel(@NotNull Disposable disposableParent, @NotNull AndroidFacet facet,
                                 @NotNull AndroidModulePaths defaultPaths, @NotNull File resFolder,
                                 @NotNull AndroidIconType... supportedTypes) {
    super(new BorderLayout());
    FileDocumentManager.getInstance().saveAllDocuments();
    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), panel -> new LoadingDecorator(panel, this, -1) {
      @Override
      protected NonOpaquePanel customizeLoadingLayer(JPanel parent, JLabel text, AsyncProcessIcon icon) {
        final NonOpaquePanel panel = super.customizeLoadingLayer(parent, text, icon);
        panel.setOpaque(true);
        return panel;
      }
    });
    myLoadingPanel.add(myOutputPreviewPanel);
    myOutputPreviewScrollPane.getViewport().setView(myLoadingPanel);
    myLoadingPanel.setLoadingText("Rendering preview images...");
    myLoadingPanel.startLoading();

    myDefaultPaths = defaultPaths;
    myPaths = myDefaultPaths;
    myResFolder = resFolder;

    if (supportedTypes.length == 0) {
      supportedTypes = AndroidIconType.values();
    }

    DefaultComboBoxModel<AndroidIconType> supportedTypesModel = new DefaultComboBoxModel<>(supportedTypes);
    myIconTypeCombo.setModel(supportedTypesModel);
    myIconTypeCombo.setVisible(supportedTypes.length > 1);
    myOutputIconType = ObjectProperty.wrap(new SelectedItemProperty<>(myIconTypeCombo));
    myOutputPreviewPanel.setName("PreviewIconsPanel"); // for UI Tests

    myValidatorPanel = new ValidatorPanel(this, myRootPanel, "Conversion Issues", "Encountered Issues:");

    myPreviewResolutionComboBox.setRenderer(
      SimpleListCellRenderer.create("", Density::getResourceValue));
    DefaultComboBoxModel<Density> densitiesModel = new DefaultComboBoxModel<>();
    densitiesModel.addElement(Density.MEDIUM);
    densitiesModel.addElement(Density.HIGH);
    densitiesModel.addElement(Density.XHIGH);
    densitiesModel.addElement(Density.XXHIGH);
    densitiesModel.addElement(Density.XXXHIGH);
    myPreviewResolutionComboBox.setModel(densitiesModel);
    myPreviewDensityProperty = ObjectProperty.wrap(new SelectedItemProperty<>(myPreviewResolutionComboBox));

    myShowGridProperty = new SelectedProperty(myShowGrid);
    myShowSafeZoneProperty = new SelectedProperty(myShowSafeZone);

    AndroidModuleInfo androidModuleInfo = StudioAndroidModuleInfo.getInstance(facet);
    int minSdkVersion = androidModuleInfo.getMinSdkVersion().getApiLevel();

    // Create a card and a view for each icon type.
    assert myConfigureIconPanels.getLayout() instanceof CardLayout;
    DrawableRenderer renderer = new DrawableRenderer(facet);
    Disposer.register(this, renderer);
    for (AndroidIconType iconType : supportedTypes) {
      ConfigureIconView view;
      switch (iconType) {
        case LAUNCHER:
        case TV_CHANNEL:
          view = new ConfigureAdaptiveIconPanel(this, facet, iconType, myShowGridProperty, myShowSafeZoneProperty,
                                                myPreviewDensityProperty, myValidatorPanel, renderer);
          break;
        case LAUNCHER_LEGACY:
        case ACTIONBAR:
        case NOTIFICATION:
          view = new ConfigureIconPanel(this, facet, iconType, minSdkVersion, renderer);
          break;
        case TV_BANNER:
          view = new ConfigureTvBannerPanel(this, facet, myValidatorPanel, renderer);
          break;
        default:
          throw new IllegalArgumentException("Invalid icon type");
      }
      myConfigureIconViews.put(iconType, view);
      myConfigureIconPanels.add(view.getRootComponent(), iconType.toString());
    }

    // Create an output preview panel for each icon type.
    ImmutableMap.Builder<AndroidIconType, PreviewIconsPanel> previewPanelBuilder = ImmutableMap.builder();
    previewPanelBuilder.put(AndroidIconType.LAUNCHER, new LauncherIconsPreviewPanel());
    previewPanelBuilder.put(AndroidIconType.LAUNCHER_LEGACY, new LauncherLegacyIconsPreviewPanel());
    previewPanelBuilder.put(AndroidIconType.ACTIONBAR, new ActionBarIconsPreviewPanel());
    previewPanelBuilder.put(AndroidIconType.NOTIFICATION, new NotificationIconsPreviewPanel());
    previewPanelBuilder.put(AndroidIconType.TV_BANNER, new TvBannerPreviewPanel());
    previewPanelBuilder.put(AndroidIconType.TV_CHANNEL, new TvChannelPreviewPanel());
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
    initializeValidators();

    Disposer.register(disposableParent, this);
    Disposer.register(this, myValidatorPanel);
    add(myValidatorPanel);
  }

  private void initializeListenersAndBindings() {
    // Add listener to re-generate asset when options change in configuration panels
    ActionListener onAssetModified = actionEvent -> renderIconPreviews();
    for (ConfigureIconView view : myConfigureIconViews.values()) {
      view.addAssetListener(onAssetModified);
    }

    // Re-generate preview when icon type, "Show grid" or "Show safe zone" change.
    Runnable updatePreview = () -> {
      ConfigureIconView iconView = getActiveIconView();
      myBindings.bind(myOutputName, iconView.outputName());
      myOutputPreviewLabel.setText("Preview");
      renderIconPreviews();
    };
    myListeners.listenAndFire(myOutputIconType, iconType -> {
      ((CardLayout)myConfigureIconPanels.getLayout()).show(myConfigureIconPanels, iconType.toString());
      updatePreview.run();
    });
    myListeners.listenAndFire(myShowGridProperty, selected -> updatePreview.run());
    myListeners.listenAndFire(myShowSafeZoneProperty, selected -> updatePreview.run());
    myListeners.listenAndFire(myPreviewDensityProperty, value -> updatePreview.run());

    // Show interactive preview components only if creating adaptive icons.
    Expression<Boolean> isAdaptiveIconOutput = Expression.create(() -> isAdaptiveIconType(myOutputIconType.get()), myOutputIconType);
    myBindings.bind(new VisibleProperty(myShowSafeZone), isAdaptiveIconOutput);
    Expression<Boolean> isLauncherIconOutput =
        Expression.create(() -> myOutputIconType.get() == AndroidIconType.LAUNCHER, myOutputIconType);
    myBindings.bind(new VisibleProperty(myShowGrid), isLauncherIconOutput);
    myBindings.bind(new VisibleProperty(myPreviewResolutionComboBox), isLauncherIconOutput);
  }

  /**
   * Updates our output preview panel with icons generated in the output icon panel
   * of the current icon type.
   */
  private void updateOutputPreviewPanel() {
    myOutputPreviewPanel.removeAll();
    PreviewIconsPanel iconsPanel = myOutputPreviewPanels.get(myOutputIconType.get());
    for (PreviewIconsPanel.IconPreviewInfo previewInfo : iconsPanel.getIconPreviewInfos()) {
      ImagePreviewPanel previewPanel = new ImagePreviewPanel();
      previewPanel.getComponent().setName("IconPanel"); // for UI Tests
      previewPanel.setLabelText(previewInfo.getLabel());
      previewPanel.setImage(previewInfo.getImage());
      previewPanel.setImageBackground(previewInfo.getImageBackground());
      previewPanel.setImageOpaque(previewInfo.isImageOpaque());
      if (!isAdaptiveIconType(myOutputIconType.get())) {
        previewPanel.setImageBorder(previewInfo.getImageBorder());
      }

      myOutputPreviewPanel.add(previewPanel.getComponent());
    }
    myOutputPreviewPanel.revalidate();
    myOutputPreviewPanel.repaint();
  }

  private static boolean isAdaptiveIconType(@NotNull AndroidIconType iconType) {
    return iconType == AndroidIconType.LAUNCHER || iconType == AndroidIconType.TV_CHANNEL;
  }

  @NotNull
  private ConfigureIconView getActiveIconView() {
    for (ConfigureIconView view : myConfigureIconViews.values()) {
      if (view.getRootComponent().isVisible()) {
        return view;
      }
    }

    throw new IllegalStateException(getClass().getSimpleName() + " is configured incorrectly. Please report this error.");
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
    myValidatorPanel.registerValidator(
        myOutputName, name -> Validator.Result.fromNullableMessage(myNameValidator.getErrorText(name.trim())));

    myValidatorPanel.registerValidator(myPreviewRenderingError, errorMessage -> {
      if (!errorMessage.isEmpty()) {
        return new Validator.Result(Validator.Severity.ERROR, errorMessage);
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
  @SuppressWarnings("unused") // Will be used when template wizard is updated to use this new class - make sure to also set myResFolder...
  public void setProjectPaths(@Nullable AndroidModulePaths projectPaths) {
    myPaths = projectPaths != null ? projectPaths : myDefaultPaths;
  }

  /**
   * Set the output name for the icon type currently being edited. This is exposed as some UIs may wish
   * to set this explicitly instead of relying on defaults.
   */
  @SuppressWarnings("unused") // Will be used when template wizard is updated to use this new class
  public void setOutputName(@NotNull String name) {
    getActiveIconView().outputName().set(name);
  }

  /**
   * Returns an icon generator which will create Android icons using the panel's current settings.
   */
  @NotNull
  public IconGenerator getIconGenerator() {
    return getActiveIconView().getIconGenerator();
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
    Map<File, GeneratedIcon> pathImageMap = getIconGenerator().generateIconPlaceholders(myPaths, myResFolder);
    for (File path : pathImageMap.keySet()) {
      if (path.exists()) {
        return true;
      }
    }

    return false;
  }

  private void renderIconPreviews() {
    // This method is often called as the result of a UI property changing which may also cause
    // some other properties to change. Due to asynchronous nature of some property changes, it
    // is necessary to use two invokeLater calls to make sure that everything settles before
    // icons generation is attempted.
    invokeVeryLate(this::enqueueGenerateNotificationIcons, ModalityState.any(), o -> Disposer.isDisposed(this));
  }

  /**
   * Generating notification icons is not a lightweight process, and if we try to do it
   * synchronously, it stutters the UI. So instead we enqueue the request to run on a background
   * thread. If several requests are made in a row while an existing worker is still in progress,
   * only the most recently added will be handled, whenever the worker finishes.
   */
  private void enqueueGenerateNotificationIcons() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    AndroidIconType iconType = myOutputIconType.get();
    IconGenerator iconGenerator = getActiveIconView().getIconGenerator();

    myIconGenerationProcessor.enqueue(iconGenerator, iconGeneratorResult -> {
      // There is no map if there was no source asset.
      if (iconGeneratorResult == null) {
        return;
      }

      myLoadingPanel.stopLoading();
      // Update the icon type specific output preview panel with the new preview images
      myOutputPreviewPanels.get(iconType).showPreviewImages(iconGeneratorResult);

      // Update the current preview panel only if the icon type has not changed since the request was enqueued.
      if (Objects.equals(iconType, myOutputIconType.get())) {
        updateOutputPreviewPanel();

        Collection<String> errors = iconGeneratorResult.getErrors();
        String errorMessage = errors.isEmpty() ?
                              "" :
                              errors.size() == 1 ?
                              Iterables.getOnlyElement(errors) :
                              "Icon preview was rendered with errors";
        myPreviewRenderingError.set(errorMessage);
      }
    });
  }

  /**
   * Executes the given runnable after a double 'invokeLater' delay.
   */
  private static void invokeVeryLate(@NotNull Runnable runnable, @NotNull ModalityState state, @NotNull Condition<?> expired) {
    Application application = ApplicationManager.getApplication();
    application.invokeLater(() -> application.invokeLater(runnable, state, expired), state, expired);
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @Override
  @NotNull
  public PersistentState getState() {
    PersistentState state = new PersistentState();
    state.set(OUTPUT_ICON_TYPE_PROPERTY, myOutputIconType.get(), AndroidIconType.LAUNCHER);
    for (Map.Entry<AndroidIconType, ConfigureIconView> entry: myConfigureIconViews.entrySet()) {
      state.setChild(toLowerCamelCase(entry.getKey()), entry.getValue().getState());
    }
    return state;
  }

  @Override
  public void loadState(@NotNull PersistentState state) {
    myOutputIconType.set(state.get(OUTPUT_ICON_TYPE_PROPERTY, AndroidIconType.LAUNCHER));
    // Load persistent state of individual panels after dust settles.
    ApplicationManager.getApplication().invokeLater(
        () -> {
          for (Map.Entry<AndroidIconType, ConfigureIconView> entry: myConfigureIconViews.entrySet()) {
            PersistentStateUtil.load(entry.getValue(), state.getChild(toLowerCamelCase(entry.getKey())));
          }
        },
        ModalityState.any());
  }

  private static class LauncherLegacyIconsPreviewPanel extends PreviewIconsPanel {
    LauncherLegacyIconsPreviewPanel() {
      super("", Theme.TRANSPARENT);
    }

    /**
     * Override the default implementation to filter out the "web" density image, and keep
     * only the images with a "regular" densities (mdpi, hdpi, etc.).
     */
    @Override
    public void showPreviewImages(@NotNull IconGeneratorResult iconGeneratorResult) {
      Collection<GeneratedIcon> generatedIcons = iconGeneratorResult.getIcons();
      List<Pair<String, BufferedImage>> list = generatedIcons.stream()
        .filter(icon -> icon instanceof GeneratedImageIcon)
        .map(icon -> (GeneratedImageIcon)icon)
        .filter(icon -> icon.getDensity() != Density.NODPI) // Skip Web image.
        .sorted(Comparator.comparingInt(icon -> -icon.getDensity().getDpiValue()))
        .map(icon -> Pair.of(icon.getDensity().getResourceValue(), icon.getImage()))
        .collect(Collectors.toList());
      showPreviewImagesImpl(list);
    }
  }

  private static class ActionBarIconsPreviewPanel extends PreviewIconsPanel {
    ActionBarIconsPreviewPanel() {
      super("", Theme.TRANSPARENT);
    }
  }

  private static class NotificationIconsPreviewPanel extends PreviewIconsPanel {
    NotificationIconsPreviewPanel() {
      super("", Theme.DARK);
    }
  }
}