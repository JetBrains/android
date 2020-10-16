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
package com.android.tools.profilers.cpu.analysis;

import com.android.tools.adtui.model.ViewBinder;
import com.android.tools.profilers.StudioProfilersView;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import org.jetbrains.annotations.NotNull;

public class CpuAnalysisSummaryTab extends CpuAnalysisTab<CpuAnalysisSummaryTabModel<?>> {
  /**
   * Binds different types of summary tab model to its views, e.g. thread summary, trace event summary.
   */
  @NotNull private final ViewBinder<StudioProfilersView, CpuAnalysisSummaryTabModel<?>, SummaryDetailsViewBase<?>> myViewBinder;

  public CpuAnalysisSummaryTab(@NotNull StudioProfilersView profilersView, @NotNull CpuAnalysisSummaryTabModel<?> model) {
    super(profilersView, model);
    myViewBinder = new ViewBinder<>();
    myViewBinder.bind(FullTraceAnalysisSummaryTabModel.class, FullTraceSummaryDetailsView::new);
    myViewBinder.bind(CpuThreadAnalysisSummaryTabModel.class, CpuThreadSummaryDetailsView::new);
    myViewBinder.bind(CaptureNodeAnalysisSummaryTabModel.class, CaptureNodeSummaryDetailsView::new);
    initComponents();
  }

  private void initComponents() {
    setLayout(new BorderLayout());
    JScrollPane scrollPane = new JBScrollPane(myViewBinder.build(getProfilersView(), getModel()),
                                              ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                              ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.setBorder(JBUI.Borders.empty());
    add(scrollPane);
  }
}