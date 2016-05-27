package com.android.tools.adtui.segment;

import com.android.annotations.NonNull;
import com.android.tools.adtui.Animatable;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.AxisComponent;
import com.android.tools.adtui.Range;
import com.android.tools.adtui.common.AdtUiUtils;

import javax.swing.*;
import java.util.List;

/**
 * A simple Segment that holds the horizontal time axis, taking advantage of the default BaseSegment
 * implementation with proper column spacing and selection support.
 */
public final class TimeAxisSegment extends BaseSegment {

  @NonNull
  private final AxisComponent mTimeAxis;

  public TimeAxisSegment(@NonNull Range scopedRange, @NonNull AxisComponent timeAxis) {
    super("", scopedRange); // Empty label.
    mTimeAxis = timeAxis;
  }

  @Override
  protected void setCenterContent(@NonNull JPanel panel) {
    panel.add(mTimeAxis);
  }

  @Override
  protected void setLeftContent(@NonNull JPanel panel) {
    panel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, AdtUiUtils.DEFAULT_BORDER_COLOR));
  }

  @Override
  protected void setRightContent(@NonNull JPanel panel) {
    panel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, AdtUiUtils.DEFAULT_BORDER_COLOR));
  }
}
