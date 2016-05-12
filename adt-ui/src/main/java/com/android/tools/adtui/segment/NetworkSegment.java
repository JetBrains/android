package com.android.tools.adtui.segment;

import com.android.annotations.NonNull;
import com.android.tools.adtui.*;
import com.android.tools.adtui.common.formatter.MemoryAxisFormatter;
import com.android.tools.adtui.model.LegendRenderData;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.ReportingSeries;
import com.android.tools.adtui.model.ReportingSeriesRenderer;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class NetworkSegment extends BasicTwoAxisSegment {

  private static final String SEGMENT_NAME = "Network";
  private static final String SENDING_NAME = "Sending";
  private static final String RECEIVING_NAME = "Receiving";

  public NetworkSegment(@NonNull Range scopedRange, @NonNull List<RangedContinuousSeries> data) {
    super(SEGMENT_NAME, scopedRange, data, MemoryAxisFormatter.DEFAULT, MemoryAxisFormatter.DEFAULT);
  }

  @Override
  public List<LegendRenderData> createLegendData(@NonNull ReportingSeriesRenderer renderer) {
    List<LegendRenderData> legendRenderDataList = new ArrayList<>();
    List<ReportingSeries> reportingSeriesList = renderer.getReportingSeries();
    for (ReportingSeries series : reportingSeriesList) {
      Color color = renderer.getReportingSeriesColor(series);
      LegendRenderData renderData = new LegendRenderData(LegendRenderData.IconType.LINE, color, series);
      legendRenderDataList.add(renderData);
    }
    return legendRenderDataList;
  }

  @Override
  public void populateSeriesData(@NonNull List<RangedContinuousSeries> data, @NonNull Range leftAxisRange, @NonNull Range rightAxisRange) {

    //TODO Refactor the interaction between TestData, LineChart, and this.
    //Currently an array is passed from the test framework, to us, to LineChart for it to configure the chart.
    //Ideally this function can create the series, and set them on the LineChart with the proper configs. To do this the test framework and
    //monitor framework need some way to set data on the series created here.
    data.add(new RangedContinuousSeries(SENDING_NAME, mScopedRange, leftAxisRange));
    data.add(new RangedContinuousSeries(RECEIVING_NAME, mScopedRange, rightAxisRange));
  }
}
