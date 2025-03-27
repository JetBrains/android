/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.gct.studio.samples;

import static com.intellij.ui.GuiUtils.replaceJSplitPaneWithIDEASplitter;

import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.observable.expressions.value.TransformOptionalExpression;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.ui.deprecated.StudioWizardStepPanel;
import com.android.utils.HtmlBuilder;
import com.appspot.gsamplesindex.samplesindex.model.Sample;
import com.appspot.gsamplesindex.samplesindex.model.SampleCollection;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.SwingHelper;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.border.TitledBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import org.jetbrains.annotations.NotNull;

/**
 * SampleBrowserStep is the first page in the Sample Import wizard that allows the user to select a sample to import
 */
public class SampleBrowserStep extends ModelWizardStep<SampleModel> {

  private final SampleCollection mySampleList;
  private final SampleSetupStep mySampleSetupStep;
  private final StudioWizardStepPanel myStudioPanel;
  private final ValidatorPanel myValidatorPanel;
  private final ListenerManager myListeners = new ListenerManager();

  private final BindingsManager myBindings = new BindingsManager();

  private Tree mySampleTree;
  private SampleImportTreeManager mySampleTreeManager;
  private JPanel myPanel;
  private JEditorPane myDescriptionPane;
  private SearchTextField mySearchBox;
  private SamplePreviewPanel mySamplePreviewPanel;
  private JBScrollPane mySamplePreviewScrollPanel;
  private JPanel myDescriptionPanel;
  private JSplitPane mySplitPane;

  public SampleBrowserStep(@NotNull SampleModel model, @NotNull SampleCollection sampleList) {
    super(model, SamplesBrowserBundle.message("sample.browser.title"));
    setupUI();

    mySampleList = sampleList;
    mySampleTreeManager = new SampleImportTreeManager(mySampleTree, mySampleList);

    myValidatorPanel = new ValidatorPanel(this, myPanel);
    myStudioPanel = new StudioWizardStepPanel(myValidatorPanel, SamplesBrowserBundle.message("sample.browser.description"));

    mySampleSetupStep = new SampleSetupStep(model);

    replaceJSplitPaneWithIDEASplitter(mySplitPane);

    // Create description pane manually, via createHtmlViewer, instead of IntelliJ forms
    myDescriptionPane = SwingHelper.createHtmlViewer(false, null, null, null);
    myDescriptionPane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
    myDescriptionPanel.add(myDescriptionPane);

    // for better mouse wheel scrolling
    mySamplePreviewScrollPanel.getVerticalScrollBar().setUnitIncrement(16);
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myStudioPanel;
  }

  @Override
  public JComponent getPreferredFocusComponent() {
    return mySearchBox;
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep<?>> createDependentSteps() {
    return Lists.newArrayList(mySampleSetupStep);
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    final OptionalProperty<Sample> sample = getModel().sample();

    sample.setNullableValue(mySampleTreeManager.getSelectedSample());
    mySampleTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        sample.setNullableValue(mySampleTreeManager.getSelectedSample());
      }
    });

    DoubleClickListener treeDoubleClicked = new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        return wizard.goForward();
      }
    };
    treeDoubleClicked.installOn(mySampleTree);

    myListeners.listenAndFire(sample, () -> {
      Sample sampleValue = sample.getValueOrNull();
      mySamplePreviewPanel.setSample(sampleValue);
      mySampleSetupStep.setUrl(sampleValue != null ? sampleValue.getCloneUrl() : "");
    });

    final StringProperty htmlDesc = new StringValueProperty();
    myBindings.bind(htmlDesc, new TransformOptionalExpression<Sample, String>("", sample) {
      @NotNull
      @Override
      protected String transform(@NotNull Sample sample) {
        HtmlBuilder description = new HtmlBuilder();
        // Add sample summary and tags
        if (sample.getDescription() != null) {
          description.addHtml(sample.getDescription());
        }
        else {
          description.add(SamplesBrowserBundle.message("sample.browser.no.description"));
        }
        description.newlineIfNecessary().newline();
        description.add("Tags: ");
        description.add(StringUtil.join(sample.getCategories(), ","));
        description.newlineIfNecessary().newline();

        // Add "open source in browser" URL
        StringBuilder urlBuilder = new StringBuilder();
        String cloneUrl = sample.getCloneUrl();
        String path = sample.getPath();
        urlBuilder.append(cloneUrl);
        if (!Strings.isNullOrEmpty(path)) {
          urlBuilder.append(cloneUrl.endsWith("/") ? "" : "/");
          urlBuilder.append("tree/master/");
          urlBuilder.append(SampleModel.trimSlashes(path));
        }
        description.addLink(SamplesBrowserBundle.message("sample.browse.source"), urlBuilder.toString());
        return description.getHtml();
      }
    });

    myListeners.listenAndFire(htmlDesc, () -> SwingHelper.setHtml(myDescriptionPane, htmlDesc.get(), UIUtil.getLabelForeground()));

    TextProperty searchValue = new TextProperty(mySearchBox.getTextEditor());
    myListeners.listenAndFire(searchValue, keyword -> mySampleTreeManager.filterTree(keyword));

    myValidatorPanel.registerValidator(sample, new Validator<Optional<Sample>>() {
      @NotNull
      @Override
      public Result validate(@NotNull Optional<Sample> sample) {
        if (mySampleTreeManager.isEmpty()) {
          return new Result(Severity.ERROR, SamplesBrowserBundle.message("sample.browser.empty"));
        }
        if (!sample.isPresent()) {
          return new Result(Severity.ERROR, SamplesBrowserBundle.message("sample.browser.please.select"));
        }
        return Result.OK;
      }
    });
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
    mySplitPane = new JSplitPane();
    mySplitPane.setDividerSize(8);
    mySplitPane.setResizeWeight(0.5);
    myPanel.add(mySplitPane, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                 new Dimension(200, 200), null, 0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    mySplitPane.setLeftComponent(panel1);
    mySearchBox = new SearchTextField();
    mySearchBox.setToolTipText("Search by Name or Key Word");
    panel1.add(mySearchBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL,
                                                GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBScrollPane jBScrollPane1 = new JBScrollPane();
    jBScrollPane1.setEnabled(true);
    panel1.add(jBScrollPane1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                                  null, 0, false));
    mySampleTree = new Tree();
    jBScrollPane1.setViewportView(mySampleTree);
    mySamplePreviewScrollPanel = new JBScrollPane();
    mySamplePreviewScrollPanel.setHorizontalScrollBarPolicy(31);
    mySamplePreviewScrollPanel.setVerticalScrollBarPolicy(20);
    mySplitPane.setRightComponent(mySamplePreviewScrollPanel);
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridBagLayout());
    mySamplePreviewScrollPanel.setViewportView(panel2);
    panel2.setBorder(
      BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                       TitledBorder.DEFAULT_POSITION, null, null));
    myDescriptionPanel = new JPanel();
    myDescriptionPanel.setLayout(new BorderLayout(0, 0));
    GridBagConstraints gbc;
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    gbc.anchor = GridBagConstraints.NORTH;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    panel2.add(myDescriptionPanel, gbc);
    mySamplePreviewPanel = new SamplePreviewPanel();
    gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.insets = new Insets(10, 0, 0, 0);
    panel2.add(mySamplePreviewPanel, gbc);
  }
}
