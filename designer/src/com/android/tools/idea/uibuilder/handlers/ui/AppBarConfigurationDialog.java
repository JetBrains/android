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
package com.android.tools.idea.uibuilder.handlers.ui;

import static com.android.SdkConstants.ANDROID_NS_NAME;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.AndroidXConstants.APP_BAR_LAYOUT;
import static com.android.SdkConstants.APP_PREFIX;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.AndroidXConstants.CLASS_NESTED_SCROLL_VIEW;
import static com.android.AndroidXConstants.COORDINATOR_LAYOUT;
import static com.android.AndroidXConstants.FLOATING_ACTION_BUTTON;
import static com.android.SdkConstants.TOOLS_PREFIX;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.XMLNS_PREFIX;
import static com.android.tools.idea.uibuilder.handlers.ui.AppBarConfigurationUtilKt.formatNamespaces;

import com.android.annotations.concurrency.WorkerThread;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.repository.GoogleMavenArtifactId;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.adtui.ImageUtils;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncReason;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.rendering.RenderServiceUtilsKt;
import com.android.tools.idea.rendering.parsers.PsiXmlFile;
import com.android.tools.rendering.RenderService;
import com.android.tools.rendering.RenderTask;
import com.android.tools.idea.rendering.StudioRenderService;
import com.android.tools.rendering.imagepool.ImagePool;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.icons.AllIcons;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AppBarConfigurationDialog extends JDialog {
  private static final String DIALOG_TITLE = "Configure App Bar";
  private static final String DEFAULT_BACKGROUND_IMAGE = "@android:drawable/sym_def_app_icon";
  private static final String DEFAULT_FAB_IMAGE = "@android:drawable/ic_input_add";
  private static final String PREVIEW_PLACEHOLDER_FILE = "preview.xml";
  private static final String SAMPLE_TEXT = "This text is present to test the Application Bar. ";
  private static final int SAMPLE_REPETITION = 200;
  private static final String PREVIEW_HEADER = "Preview:";
  private static final String RENDER_ERROR = "An error happened during rendering...";
  private static final String OVERLAP_TOP_FORMAT = "%1$s:behavior_overlapTop=\"%2$s\"";
  private static final double FUDGE_FACTOR = 0.95;
  private static final int MIN_WIDTH = 40;
  private static final int MIN_HEIGHT = 60;
  private static final int START_WIDTH = 225;
  private static final int START_HEIGHT = 400;

  private final NlModel myModel;
  private final Disposable myDisposable;
  private final JBLoadingPanel myLoadingPanel;
  private final boolean myUseAndroidxDependency;
  private JPanel myContentPane;
  private JButton myButtonOK;
  private JButton myButtonCancel;
  private JBLabel myPreview;
  private JCheckBox myCollapsing;
  private JCheckBox myShowBackgroundImage;
  private JCheckBox myFloatingActionButton;
  private JCheckBox myFitStatusBar;
  private JCheckBox myContentOverlap;
  private JCheckBox myWithTabs;
  private JButton myBackgroundImageSelector;
  private JButton myFloatingActionButtonImageSelector;
  private JPanel myPreviewPanel;
  private JBLabel myCollapsedPreview;
  private JBLabel myExpandedPreview;
  private Future<?> myCollapsedPreviewFuture;
  private Future<?> myExpandedPreviewFuture;
  private JBLabel myExpandedLabel;
  private JBLabel myCollapsedLabel;
  private JCheckBox myParallax;
  private JTextField myContentOverlapAmount;
  private JSpinner myTabCount;
  private boolean myWasAccepted;
  private BufferedImage myExpandedImage;
  private BufferedImage myCollapsedImage;
  private String myBackgroundImage;
  private String myFloatingActionButtonImage;

  public AppBarConfigurationDialog(@NotNull NlModel model, boolean useAndroidxDependency) {
    setupUI();
    myModel = model;
    myUseAndroidxDependency = useAndroidxDependency;
    myDisposable = Disposer.newDisposable();
    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), myDisposable, 20);
    myLoadingPanel.add(myContentPane);
    Disposer.register(model, myDisposable);
    setTitle(DIALOG_TITLE);
    setContentPane(myLoadingPanel);
    setModal(true);
    getRootPane().setDefaultButton(myButtonOK);
    myBackgroundImage = DEFAULT_BACKGROUND_IMAGE;
    myFloatingActionButtonImage = DEFAULT_FAB_IMAGE;
    final ActionListener updatePreviewListener = event -> {
      updateControls();
      generatePreviews();
    };
    myWithTabs.addActionListener(updatePreviewListener);
    myTabCount.setValue(3);
    myCollapsing.addActionListener(updatePreviewListener);
    myShowBackgroundImage.addActionListener(updatePreviewListener);
    myFloatingActionButton.addActionListener(updatePreviewListener);
    myFitStatusBar.addActionListener(updatePreviewListener);
    myParallax.addActionListener(updatePreviewListener);
    myContentOverlap.addActionListener(updatePreviewListener);
    myContentOverlapAmount.addActionListener(updatePreviewListener);
    ((GridLayoutManager)myPreviewPanel.getLayout()).setRowStretch(0, 2);
    myTabCount.addChangeListener(event -> generatePreviews());

    final ActionListener actionListener = event -> {
      if (event.getSource() == myBackgroundImageSelector) {
        String src = ViewEditor.displayResourceInput(myModel, EnumSet.of(ResourceType.DRAWABLE));
        if (src != null) {
          myBackgroundImage = src;
          generatePreviews();
        }
      }
      else if (event.getSource() == myFloatingActionButtonImageSelector) {
        String src = ViewEditor.displayResourceInput(myModel, EnumSet.of(ResourceType.DRAWABLE));
        if (src != null) {
          myFloatingActionButtonImage = src;
          generatePreviews();
        }
      }
      else if (event.getSource() == myButtonOK) {
        onOK();
      }
      else if (event.getSource() == myButtonCancel ||
               event.getSource() == myContentPane) {
        onCancel();
      }
    };
    myBackgroundImageSelector.addActionListener(actionListener);
    myFloatingActionButtonImageSelector.addActionListener(actionListener);
    myButtonOK.addActionListener(actionListener);
    myButtonCancel.addActionListener(actionListener);
    myContentPane.registerKeyboardAction(actionListener, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                         JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    myPreviewPanel.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(final ComponentEvent e) {
        updatePreviewImages();
      }
    });

    // For UI testing
    myCollapsedPreview.setName("CollapsedPreview");
    myExpandedPreview.setName("ExpandedPreview");

    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        onCancel();
      }
    });
  }

  public boolean open() {
    Project project = myModel.getProject();
    Module module = myModel.getModule();
    boolean hasDesignLib = DependencyManagementUtil.dependsOn(module, GoogleMavenArtifactId.DESIGN) ||
                           DependencyManagementUtil.dependsOn(module, GoogleMavenArtifactId.ANDROIDX_DESIGN);
    if (!hasDesignLib && !addDesignLibrary()) {
      return false;
    }

    myCollapsedPreview.setMinimumSize(new Dimension(START_WIDTH, START_HEIGHT));
    myExpandedPreview.setMinimumSize(new Dimension(START_WIDTH, START_HEIGHT));
    pack();
    myCollapsedPreview.setMinimumSize(null);
    myExpandedPreview.setMinimumSize(null);
    Dimension size = getSize();
    Rectangle screen = getGraphicsConfiguration().getBounds();
    setLocation(screen.x + (screen.width - size.width) / 2, screen.y + (screen.height - size.height) / 2);
    updateControls();
    myButtonOK.requestFocus();
    if (hasDesignLib) {
      generatePreviews();
    }

    setVisible(true);
    if (myWasAccepted) {
      XmlFile file = myModel.getFile();
      WriteCommandAction.writeCommandAction(project, file).withName("Configure App Bar").run(() -> applyChanges(file));
    }
    return myWasAccepted;
  }

  private boolean addDesignLibrary() {
    myLoadingPanel.startLoading();

    Module module = myModel.getModule();

    GoogleMavenArtifactId artifact = myUseAndroidxDependency ?
                                     GoogleMavenArtifactId.ANDROIDX_DESIGN :
                                     GoogleMavenArtifactId.DESIGN;
    boolean designAdded = DependencyManagementUtil
      .addDependenciesWithUiConfirmation(module, Set.of(artifact), true, false)
      .isEmpty();

    if (!designAdded) {
      return false;
    }

    ListenableFuture<SyncResult> syncResult = ProjectSystemUtil.getSyncManager(module.getProject())
      .requestSyncProject(SyncReason.PROJECT_MODIFIED);

    Futures.addCallback(syncResult, new FutureCallback<SyncResult>() {
      @Override
      public void onSuccess(@Nullable SyncResult result) {
        if (result != null && result.isSuccessful()) {
          onDesignSourcesGenerated();
        }
        else {
          onBuildError();
        }
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        onBuildError();
      }
    }, MoreExecutors.directExecutor());

    return true;
  }

  private void onDesignSourcesGenerated() {
    if (isVisible()) {
      ApplicationManager.getApplication().invokeLater(this::generatePreviews);
    }
  }

  private void onBuildError() {
    myPreview.setText("Preview is unavailable until after a successful project sync");
    myPreview.setIcon(AllIcons.General.Warning);
    myCollapsedLabel.setVisible(false);
    myExpandedLabel.setVisible(false);
    myLoadingPanel.stopLoading();
  }

  private void onOK() {
    myWasAccepted = true;
    dispose();
  }

  private void onCancel() {
    dispose();
  }

  @Override
  public void dispose() {
    super.dispose();
    Disposer.dispose(myDisposable);
  }

  @NotNull
  private Project getProject() {
    return myModel.getProject();
  }

  private void updateControls() {
    myTabCount.setEnabled(myWithTabs.isSelected());
    myShowBackgroundImage.setEnabled(!myWithTabs.isSelected());
    myBackgroundImageSelector.setEnabled(!myWithTabs.isSelected() && myShowBackgroundImage.isSelected());
    myFitStatusBar.setEnabled(!myWithTabs.isSelected() && myShowBackgroundImage.isSelected());
    myParallax.setEnabled(!myWithTabs.isSelected() && myShowBackgroundImage.isSelected());
    myFitStatusBar.setEnabled(!myWithTabs.isSelected() && myShowBackgroundImage.isSelected());
    myContentOverlap.setEnabled(!myWithTabs.isSelected() && myShowBackgroundImage.isSelected());
    myContentOverlapAmount.setEnabled(!myWithTabs.isSelected() && myShowBackgroundImage.isSelected() && myContentOverlap.isSelected());
    myFloatingActionButtonImageSelector.setEnabled(myFloatingActionButton.isSelected());
  }

  private void generatePreviews() {
    myExpandedPreviewFuture = cancel(myExpandedPreviewFuture);
    myCollapsedPreviewFuture = cancel(myCollapsedPreviewFuture);
    myExpandedPreviewFuture = updateExpandedImage();
    myCollapsedPreviewFuture = updateCollapsedImage();
  }

  private void setupUI() {
    myContentPane = new JPanel();
    myContentPane.setLayout(new GridLayoutManager(2, 1, new Insets(10, 10, 10, 10), -1, -1));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myContentPane.add(panel1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null,
                                                  null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    panel1.add(spacer1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                            GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1, true, false));
    panel1.add(panel2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                           false));
    myButtonOK = new JButton();
    myButtonOK.setText("OK");
    panel2.add(myButtonOK, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myButtonCancel = new JButton();
    myButtonCancel.setText("Cancel");
    panel2.add(myButtonCancel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel3 = new JPanel();
    panel3.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    myContentPane.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                  null, 0, false));
    final JSplitPane splitPane1 = new JSplitPane();
    panel3.add(splitPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                               new Dimension(200, 200), null, 0, false));
    final JPanel panel4 = new JPanel();
    panel4.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    splitPane1.setLeftComponent(panel4);
    final JPanel panel5 = new JPanel();
    panel5.setLayout(new GridLayoutManager(6, 3, new Insets(16, 8, 0, 0), -1, -1));
    panel4.add(panel5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                           false));
    myCollapsing = new JCheckBox();
    myCollapsing.setText("Collapsing Toolbar");
    panel5.add(myCollapsing, new GridConstraints(0, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                 GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myShowBackgroundImage = new JCheckBox();
    myShowBackgroundImage.setText("Image Background");
    panel5.add(myShowBackgroundImage, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myFloatingActionButton = new JCheckBox();
    myFloatingActionButton.setText("Floating Action Button");
    panel5.add(myFloatingActionButton, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myWithTabs = new JCheckBox();
    myWithTabs.setText("With Tabs");
    panel5.add(myWithTabs, new GridConstraints(5, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel6 = new JPanel();
    panel6.setLayout(new GridLayoutManager(2, 4, new Insets(0, 12, 0, 0), -1, -1));
    panel5.add(panel6, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                           false));
    myFitStatusBar = new JCheckBox();
    myFitStatusBar.setActionCommand("");
    myFitStatusBar.setText("Fit Status Bar on Image");
    myFitStatusBar.setToolTipText(
      "Makes the toolbar with the image appear below the status bar. The status bar can be set to draw transparent by setting the theme value for: statusBarColor to color/transparent.");
    panel6.add(myFitStatusBar, new GridConstraints(0, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myParallax = new JCheckBox();
    myParallax.setText("Parallax Scrolling");
    myParallax.setToolTipText("Makes the image scroll slower than the TabBar (only visible at runtime).");
    panel6.add(myParallax, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                               GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myBackgroundImageSelector = new JButton();
    Font myBackgroundImageSelectorFont = getFont(null, -1, 8, myBackgroundImageSelector.getFont());
    if (myBackgroundImageSelectorFont != null) myBackgroundImageSelector.setFont(myBackgroundImageSelectorFont);
    myBackgroundImageSelector.setText("...");
    panel5.add(myBackgroundImageSelector, new GridConstraints(1, 1, 1, 2, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                              GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myFloatingActionButtonImageSelector = new JButton();
    Font myFloatingActionButtonImageSelectorFont = getFont(null, -1, 8, myFloatingActionButtonImageSelector.getFont());
    if (myFloatingActionButtonImageSelectorFont != null) {
      myFloatingActionButtonImageSelector.setFont(myFloatingActionButtonImageSelectorFont);
    }
    myFloatingActionButtonImageSelector.setText("...");
    panel5.add(myFloatingActionButtonImageSelector, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_EAST, GridConstraints.FILL_NONE,
                                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                        GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myContentOverlap = new JCheckBox();
    myContentOverlap.setText("Overlapping content");
    panel5.add(myContentOverlap, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myContentOverlapAmount = new JTextField();
    myContentOverlapAmount.setText("32dp");
    panel5.add(myContentOverlapAmount, new GridConstraints(4, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, 1,
                                                           GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(60, -1), null, 0, false));
    myTabCount = new JSpinner();
    panel5.add(myTabCount, new GridConstraints(5, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                               false));
    final Spacer spacer2 = new Spacer();
    panel4.add(spacer2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                            GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
    myPreviewPanel = new JPanel();
    myPreviewPanel.setLayout(new GridLayoutManager(4, 2, new Insets(0, 0, 0, 0), -1, -1));
    splitPane1.setRightComponent(myPreviewPanel);
    myPreview = new JBLabel();
    myPreview.setText("Preview:");
    myPreviewPanel.add(myPreview, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_SOUTH, GridConstraints.FILL_NONE,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null,
                                                      null, null, 0, false));
    myExpandedLabel = new JBLabel();
    myExpandedLabel.setAlignmentX(0.0f);
    myExpandedLabel.setText("Expanded");
    myPreviewPanel.add(myExpandedLabel, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_NONE,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                            null, null, null, 0, false));
    myCollapsedLabel = new JBLabel();
    myCollapsedLabel.setAlignmentX(0.0f);
    myCollapsedLabel.setText("Collapsed");
    myPreviewPanel.add(myCollapsedLabel, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                             null, null, null, 0, false));
    myExpandedPreview = new JBLabel();
    myPreviewPanel.add(myExpandedPreview, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                              null, null, null, 0, false));
    myCollapsedPreview = new JBLabel();
    myPreviewPanel.add(myCollapsedPreview, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                               null, null, null, 1, false));
    final Spacer spacer3 = new Spacer();
    myPreviewPanel.add(spacer3, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                    GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
  }

  private Font getFont(String fontName, int style, int size, Font currentFont) {
    if (currentFont == null) return null;
    String resultName;
    if (fontName == null) {
      resultName = currentFont.getName();
    }
    else {
      Font testFont = new Font(fontName, Font.PLAIN, 10);
      if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
        resultName = fontName;
      }
      else {
        resultName = currentFont.getName();
      }
    }
    Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
    boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
    Font fontWithFallback = isMac
                            ? new Font(font.getFamily(), font.getStyle(), font.getSize())
                            : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
    return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
  }

  @Nullable
  private static Future<?> cancel(@Nullable Future<?> future) {
    if (future != null) {
      future.cancel(true);
    }
    return null;
  }

  @WorkerThread
  private XmlFile generateXml(boolean collapsed) {
    DumbService.getInstance(getProject()).waitForSmartMode();
    StringBuilder text = new StringBuilder(SAMPLE_REPETITION * SAMPLE_TEXT.length());
    for (int i = 0; i < SAMPLE_REPETITION; i++) {
      text.append(SAMPLE_TEXT);
    }
    Map<String, String> namespaces = getNameSpaces(null, collapsed);
    String content = Templates.getTextView(namespaces.get(ANDROID_URI), text.toString());
    String xml = getXml(content, collapsed, namespaces);
    Project project = getProject();
    return (XmlFile)ReadAction.compute(
      () -> PsiFileFactory.getInstance(project).createFileFromText(PREVIEW_PLACEHOLDER_FILE, XmlFileType.INSTANCE, xml));
  }

  private void updatePreviewImages() {
    if (myCollapsedImage != null) {
      updatePreviewImage(myCollapsedImage, myCollapsedPreview);
    }
    if (myExpandedImage != null) {
      updatePreviewImage(myExpandedImage, myExpandedPreview);
    }
  }

  private void applyChanges(@NotNull XmlFile file) {
    Map<String, String> namespaces = getNameSpaces(file.getRootTag(), false);
    String xml = getXml(getDesignContent(file), false, namespaces);
    XmlElementFactory elementFactory = XmlElementFactory.getInstance(file.getProject());
    XmlTag tag = elementFactory.createTagFromText(xml);
    if (file.getRootTag() == null) {
      file.add(tag);
    }
    else {
      file.getRootTag().replace(tag);
    }
  }

  @NotNull
  private String getXml(@NotNull String content, boolean collapsed, @NotNull Map<String, String> namespaces) {
    return myWithTabs.isSelected() ? getXmlWithTabs(content, collapsed, namespaces) : getXmlWithoutTabs(content, collapsed, namespaces);
  }

  @NotNull
  private String getXmlWithoutTabs(@NotNull String content, boolean collapsed, @NotNull Map<String, String> namespaces) {
    return Templates.getCoordinatorLayout(
      getProject(),
      namespaces.get(ANDROID_URI),
      namespaces.get(AUTO_URI),
      formatNamespaces(namespaces),
      getFitsSystemWindows(namespaces),
      getToolbarScrollBehavior(),
      getInterpolator(namespaces),
      getBackgroundImage(namespaces),
      getBehaviorOverlapTop(namespaces),
      getScrollPos(collapsed, namespaces),
      content,
      getFloatingActionButton(namespaces));
  }

  @NotNull
  private String getXmlWithTabs(@NotNull String content, boolean collapsed, @NotNull Map<String, String> namespaces) {
    return Templates.getCoordinatorLayoutWithTabs(
      getProject(),
      namespaces.get(ANDROID_URI),
      namespaces.get(AUTO_URI),
      formatNamespaces(namespaces),
      getTabLayoutScroll(namespaces),
      getTabItems(namespaces),
      getScrollPos(collapsed, namespaces),
      content,
      getFloatingActionButton(namespaces));
  }

  @NotNull
  private String getFitsSystemWindows(@NotNull Map<String, String> namespaces) {
    if (!myShowBackgroundImage.isSelected() || !myFitStatusBar.isSelected()) {
      return "";
    }
    return String.format("    %1$s:fitsSystemWindows=\"true\"\n",
                         namespaces.get(ANDROID_URI));
  }

  @NotNull
  private String getToolbarScrollBehavior() {
    return myCollapsing.isSelected() ? "scroll|enterAlways|enterAlwaysCollapsed" : "scroll|exitUntilCollapsed";
  }

  @NotNull
  private String getTabLayoutScroll(@NotNull Map<String, String> namespaces) {
    if (!myCollapsing.isSelected()) {
      return "";
    }
    return String.format("        %1$s:layout_scrollFlags=\"scroll|enterAlways\"\n",
                         namespaces.get(AUTO_URI));
  }

  @NotNull
  private String getTabItems(@NotNull Map<String, String> namespaces) {
    StringBuilder builder = new StringBuilder();
    for (int index = 0; index < (Integer)myTabCount.getValue(); index++) {
      builder.append(Templates.getTabItem(
        getProject(),
        namespaces.get(ANDROID_URI),
        "Tab" + (index + 1)));
    }
    return builder.toString();
  }

  @NotNull
  private String getInterpolator(@NotNull Map<String, String> namespaces) {
    if (!myShowBackgroundImage.isSelected()) {
      return "";
    }
    return String.format("        %2$s:layout_scrollInterpolator=\"@%1$s:anim/decelerate_interpolator\"\n",
                         namespaces.get(ANDROID_URI),
                         namespaces.get(AUTO_URI));
  }

  @NotNull
  private String getBackgroundImage(@NotNull Map<String, String> namespaces) {
    if (!myShowBackgroundImage.isSelected()) {
      return "";
    }
    return Templates.getImageView(
      namespaces.get(ANDROID_URI),
      getBackgroundImageCollapseMode(namespaces),
      myBackgroundImage);
  }

  @NotNull
  private String getBehaviorOverlapTop(@NotNull Map<String, String> namespaces) {
    if (!myContentOverlap.isSelected()) {
      return "";
    }
    return String.format(OVERLAP_TOP_FORMAT,
                         namespaces.get(AUTO_URI),
                         myContentOverlapAmount.getText());
  }

  @NotNull
  private static String getScrollPos(boolean collapsed, @NotNull Map<String, String> namespaces) {
    if (!collapsed) {
      return "";
    }
    return String.format("        %1$s:scrollY=\"830px\"\n",
                         namespaces.get(TOOLS_URI));
  }

  @NotNull
  private String getBackgroundImageCollapseMode(@NotNull Map<String, String> namespaces) {
    if (myParallax.isSelected()) {
      return "";
    }
    return String.format("    %1$s:layout_collapseMode=\"parallax\"\n",
                         namespaces.get(AUTO_URI));
  }

  @NotNull
  private String getFloatingActionButton(@NotNull Map<String, String> namespaces) {
    if (!myFloatingActionButton.isSelected()) {
      return "";
    }
    return Templates.getTagFloatingActionButton(getProject(), namespaces.get(ANDROID_URI), myFloatingActionButtonImage);
  }

  @NotNull
  private static Map<String, String> getNameSpaces(@Nullable XmlTag root, boolean includeToolsNamespace) {
    Map<String, String> reverse = new HashMap<>();
    if (root != null) {
      Map<String, String> namespaces = root.getLocalNamespaceDeclarations();
      for (String prefix : namespaces.keySet()) {
        reverse.put(namespaces.get(prefix), prefix);
      }
    }
    if (!reverse.containsKey(ANDROID_URI)) {
      reverse.put(ANDROID_URI, ANDROID_NS_NAME);
    }
    if (!reverse.containsKey(AUTO_URI)) {
      reverse.put(AUTO_URI, APP_PREFIX);
    }
    if (includeToolsNamespace && !reverse.containsKey(TOOLS_URI)) {
      reverse.put(TOOLS_URI, TOOLS_PREFIX);
    }
    return reverse;
  }

  // If AppBarLayout is applied a second time it should replace the current AppBarLayout:
  @NotNull
  private static String getDesignContent(@NotNull XmlFile file) {
    XmlTag content = file.getRootTag();
    if (content != null && COORDINATOR_LAYOUT.isEquals(content.getName())) {
      XmlTag root = content;
      content = null;
      for (XmlTag tag : root.getSubTags()) {
        if (!APP_BAR_LAYOUT.isEquals(tag.getName()) &&
            !FLOATING_ACTION_BUTTON.isEquals(tag.getName())) {
          if (CLASS_NESTED_SCROLL_VIEW.isEquals(tag.getName())) {
            content = tag.getSubTags().length > 0 ? tag.getSubTags()[0] : null;
          }
          else {
            content = tag;
          }
          break;
        }
      }
    }
    if (content == null) {
      return "";
    }
    // Remove any xmlns: attributes since this element will be added into the document
    for (XmlAttribute attribute : content.getAttributes()) {
      if (attribute != null && attribute.getName().startsWith(XMLNS_PREFIX)) {
        attribute.delete();
      }
    }
    return content.getText();
  }

  private CompletableFuture<Void> updateCollapsedImage() {
    return CompletableFuture.supplyAsync(() -> generateXml(true), AppExecutorUtil.getAppExecutorService())
      .thenCompose(file -> updateImage(file, myCollapsedPreview))
      .thenAccept(image -> myCollapsedImage = image);
  }

  private CompletableFuture<Void> updateExpandedImage() {
    return CompletableFuture.supplyAsync(() -> generateXml(false), AppExecutorUtil.getAppExecutorService())
      .thenCompose(file -> updateImage(file, myExpandedPreview))
      .thenAcceptAsync(image -> {
        myExpandedImage = image;
        myLoadingPanel.stopLoading();
      }, EdtExecutorService.getInstance());
  }

  @NotNull
  private CompletableFuture<BufferedImage> updateImage(@NotNull XmlFile xmlFile, @NotNull JBLabel preview) {
    return renderImage(xmlFile).whenCompleteAsync((image, ex) -> updatePreviewImage(image, preview), EdtExecutorService.getInstance());
  }

  private CompletableFuture<BufferedImage> renderImage(@NotNull XmlFile xmlFile) {
    AndroidFacet facet = myModel.getFacet();
    RenderService renderService = StudioRenderService.getInstance(getProject());
    final CompletableFuture<RenderTask> taskFuture =
      RenderServiceUtilsKt.taskBuilderWithHtmlLogger(renderService, myModel.getBuildTarget(), myModel.getConfiguration())
        .withPsiFile(new PsiXmlFile(xmlFile))
        .build();
    return taskFuture.thenCompose(task -> {
      if (task != null) {
        task.setRenderingMode(SessionParams.RenderingMode.NORMAL);
        task.getContext().setFolderType(ResourceFolderType.LAYOUT);
        return task.render().thenApply(result -> {
          ImagePool.Image image = result.getRenderedImage();
          if (!image.isValid() || image.getWidth() < MIN_WIDTH || image.getHeight() < MIN_HEIGHT) {
            return null;
          }

          return result.getRenderedImage().getCopy();
        }).whenCompleteAsync((image, ex) -> task.dispose(), AppExecutorUtil.getAppExecutorService());
      }
      return null;
    });
  }

  private void updatePreviewImage(@Nullable BufferedImage image, @NotNull JBLabel view) {
    if (image == null) {
      view.setIcon(null);
      myPreview.setText(RENDER_ERROR);
      return;
    }
    double width = myPreviewPanel.getWidth() / 2.0;
    double height =
      myPreviewPanel.getHeight() - myPreview.getHeight() - Math.max(myExpandedLabel.getHeight(), myCollapsedLabel.getHeight());
    if (width < MIN_WIDTH || height < MIN_HEIGHT) {
      view.setIcon(null);
    }
    double scale = Math.min(width / image.getWidth(), height / image.getHeight()) * FUDGE_FACTOR;
    image = ImageUtils.scale(image, scale, scale);
    view.setIcon(new ImageIcon(image));
    myPreview.setText(PREVIEW_HEADER);
  }

  private static Logger getLogger() {
    return Logger.getInstance(AppBarConfigurationDialog.class);
  }
}
