package com.android.tools.adtui.common.formatter;

import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;

public final class NetworkTrafficFormatter extends BaseAxisFormatter {
  private static final int MULTIPLIER = 1024;
  private static final int BASE = 2;
  private static final int[] MIN_INTERVALS = new int[]{4, 1, 1};    // 4 B/S, 1 KB/S, 1 MB/S
  private static String[] UNITS = new String[]{"B/S", "KB/S", "MB/S"};
  private static final TIntArrayList BASE_FACTORS = new TIntArrayList(new int[]{2, 1});

  public static final NetworkTrafficFormatter DEFAULT = new NetworkTrafficFormatter(4, 10, 2);

  public NetworkTrafficFormatter(int maxMinorTicks, int maxMajorTicks, int switchThreshold) {
    super(maxMinorTicks, maxMajorTicks, switchThreshold);
  }

  @Override
  protected int getNumUnits() {
    return UNITS.length;
  }

  @Override
  @NotNull
  protected String getUnit(int index) {
    return UNITS[index];
  }

  @Override
  protected int getUnitBase(int index) {
    return BASE;
  }

  @Override
  protected int getUnitMultiplier(int index) {
    return MULTIPLIER;
  }

  @Override
  protected int getUnitMinimalInterval(int index) {
    return MIN_INTERVALS[index];
  }

  @Override
  @NotNull
  protected TIntArrayList getUnitBaseFactors(int index) {
    return BASE_FACTORS;
  }
}
