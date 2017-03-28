package com.android.tools.idea.monitor.ui.network.model;

import gnu.trove.TDoubleArrayList;
import gnu.trove.TLongArrayList;

import java.util.concurrent.TimeUnit;

/**
 * Class which converts input of "bytes sent / received so far" into a list of speeds (KB / s).
 *
 * Android devices return absolute number of bytes sent / received since device boot; however,
 * we're more interested in current speeds (e.g. KB / s), so this class handles transforming the
 * data appropriately.
 *
 * TODO: This class is estimating download areas using trapezoids. Let's document this clearly in
 * this header comment.
 */
public final class SpeedData {
  private static final double SEC_TO_US = TimeUnit.SECONDS.toMicros(1);
  private final TLongArrayList myTrafficTimeData = new TLongArrayList();
  // bytes/sec
  private final TLongArrayList mySentSpeeds = new TLongArrayList();
  // bytes/sec
  private final TLongArrayList myReceivedSpeeds = new TLongArrayList();

  // We are always interested in relative traffic values, not absolute, so keep track of the last
  // values so we can always know the delta.
  private long myLastSentSoFar = Long.MIN_VALUE;
  private long myLastReceivedSoFar = Long.MIN_VALUE;

  /**
   * @return rectangular trapezoid's maximum width with known one base side {@code baseLength} and with area {@code area}.
   * The second base side can be arbitrary and it's width limited with {@code maxWidth}, i.e answer is less or equal to maxWidth
   */
  private static double calcTrapezoidMaxWidth(double maxWidth, double baseLength, double area) {
    if (area == 0) {
      return 0;
    }
    if (maxWidth * baseLength / 2 > area) {
      return area * 2 / baseLength;
    }
    else {
      return maxWidth;
    }
  }

  private static double interpolate(double val1, double val2, double percent) {
    return val1 + ((val2 - val1) * percent);
  }

  public TLongArrayList getTimeData() {
    return myTrafficTimeData;
  }

  public TLongArrayList getSent() {
    return mySentSpeeds;
  }

  public TLongArrayList getReceived() {
    return myReceivedSpeeds;
  }

  public void add(long sentBytesSoFar, long receivedBytesSoFar, long timestampUs) {
    if (myTrafficTimeData.isEmpty()) {
      // First data is special, because backend returns traffics since device boot time.
      myTrafficTimeData.add(timestampUs);
      myReceivedSpeeds.add(0);
      mySentSpeeds.add(0);
    }
    else {
      convertTrafficsToSpeeds(timestampUs, sentBytesSoFar - myLastSentSoFar, receivedBytesSoFar - myLastReceivedSoFar);
    }

    myLastSentSoFar = sentBytesSoFar;
    myLastReceivedSoFar = receivedBytesSoFar;
  }

  private void convertTrafficsToSpeeds(long timestampUs, long sentBytes, long receivedBytes) {
    long lastTimeUs = myTrafficTimeData.get(myTrafficTimeData.size() - 1);
    long lastReceivedSpeed = myReceivedSpeeds.get(myReceivedSpeeds.size() - 1);
    long lastSentSpeed = mySentSpeeds.get(mySentSpeeds.size() - 1);
    double timestampDeltaSec = (timestampUs - lastTimeUs) / SEC_TO_US;

    double receivedTimeDeltaSec = calcTrapezoidMaxWidth(timestampDeltaSec, lastReceivedSpeed, receivedBytes);
    double sentTimeDeltaSec = calcTrapezoidMaxWidth(timestampDeltaSec, lastSentSpeed, sentBytes);

    // TODO: This may be unecessary if we keep a separate timestamp list for sent and received bytes
    TDoubleArrayList timeDeltasSec = new TDoubleArrayList(3);
    timeDeltasSec.add(receivedTimeDeltaSec);
    timeDeltasSec.add(sentTimeDeltaSec);
    timeDeltasSec.add(timestampDeltaSec);
    timeDeltasSec.sort();

    long[] receivedSpeeds = new long[timeDeltasSec.size()];
    long[] sentSpeeds = new long[timeDeltasSec.size()];

    for (int i = 0; i < timeDeltasSec.size(); ++i) {
      double timeDeltaSec = timeDeltasSec.get(i);
      if (timeDeltaSec <= receivedTimeDeltaSec) {
        // Derived from trapezoid's area equation: (a + b) * width / 2 = area, i.e
        // (lastReceivedSpeed + receivedSpeed) * receivedTimeDeltaSec / 2 = receivedBytes
        double receivedSpeed = 2.0 * receivedBytes / receivedTimeDeltaSec - lastReceivedSpeed;
        receivedSpeeds[i] = (long)interpolate(lastReceivedSpeed, receivedSpeed, timeDeltaSec / receivedTimeDeltaSec);
      }
      else {
        receivedSpeeds[i] = 0;
      }
    }

    for (int i = 0; i < timeDeltasSec.size(); ++i) {
      double timeDeltaSec = timeDeltasSec.get(i);
      if (timeDeltaSec <= sentTimeDeltaSec) {
        // Derived from trapezoid's area equation: (a + b) * width / 2 = area, i.e
        // (lastSentSpeed + sentSpeed) * sentTimeDeltaSec / 2 = sentBytes
        double sentSpeed = 2.0 * sentBytes / sentTimeDeltaSec - lastSentSpeed;
        sentSpeeds[i] = (long)interpolate(lastSentSpeed, sentSpeed, timeDeltaSec / sentTimeDeltaSec);
      }
      else {
        sentSpeeds[i] = 0;
      }
    }

    for (int i = 0; i < timeDeltasSec.size(); ++i) {
      double timeIntervalSec = timeDeltasSec.get(i);
      if (i > 0 && timeIntervalSec == timeDeltasSec.get(i - 1)) {
        // Eliminate degenerate points on the path
        continue;
      }

      myTrafficTimeData.add((long)(timeIntervalSec * SEC_TO_US + lastTimeUs));
      mySentSpeeds.add(sentSpeeds[i]);
      myReceivedSpeeds.add(receivedSpeeds[i]);
    }
  }
}
