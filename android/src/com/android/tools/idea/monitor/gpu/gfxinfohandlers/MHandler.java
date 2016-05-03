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
package com.android.tools.idea.monitor.gpu.gfxinfohandlers;

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.MultiLineReceiver;
import com.android.tools.adtui.TimelineData;
import com.android.tools.idea.monitor.DeviceSampler;
import com.android.tools.idea.monitor.gpu.GpuSampler;
import com.intellij.util.ThreeState;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sampler for M API or higher.
 */
public final class MHandler implements GfxinfoHandler {
  public static final int MIN_API_LEVEL = 23;

  private static final float NS_TO_MS = 0.000001f;

  private Client myClient;

  private ProcessStatReceiver myReceiver = new ProcessStatReceiver();

  private long mySynchronizedIdeTimeMs = -1;
  private long mySynchronizedHostTimeNs = -1;

  private boolean myDelimiterAdded = true;
  private boolean myIgnoreInitialSamples = true;

  @Override
  public boolean accept(@NotNull Client client) {
    return GpuSampler.decodeApiLevel(client) >= MIN_API_LEVEL;
  }

  @Override
  public void setClient(@Nullable Client client) {
    myClient = client;
    myReceiver.reset();
    mySynchronizedHostTimeNs = -1;
    mySynchronizedIdeTimeMs = -1;
    myDelimiterAdded = true;
    myIgnoreInitialSamples = true;
  }

  @Override
  public void sample(@NotNull IDevice device, @NotNull ClientData data, @NotNull TimelineData timeline) throws Exception {
    int pid = data.getPid();

    myReceiver.resetSamples();
    device.executeShellCommand("dumpsys gfxinfo " + pid + " framestats", myReceiver, 1, TimeUnit.SECONDS);

    if (myIgnoreInitialSamples) {
      synchronizeClocks(device);
    }

    if (myReceiver.getSampleSize() > 0) {
      if (myDelimiterAdded) {
        timeline.add(mySynchronizedIdeTimeMs + (myReceiver.getCurrentSampleFirstFrameTime() - mySynchronizedHostTimeNs) / 1000000,
                     DeviceSampler.TYPE_DATA, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        myDelimiterAdded = false;
      }

      if (!myIgnoreInitialSamples) {
        for (int i = 0; i < myReceiver.getSampleSize(); ++i) {
          long sampleTime = mySynchronizedIdeTimeMs + (myReceiver.getEndTime(i) - mySynchronizedHostTimeNs) / 1000000;
          timeline.add(sampleTime, DeviceSampler.TYPE_DATA, (float)myReceiver.getVSyncDelay(i) * NS_TO_MS,
                       (float)myReceiver.getInputHandlingTime(i) * NS_TO_MS, (float)myReceiver.getAnimationTime(i) * NS_TO_MS,
                       (float)myReceiver.getTraversalTime(i) * NS_TO_MS, (float)myReceiver.getDrawTime(i) * NS_TO_MS,
                       (float)myReceiver.getSyncTime(i) * NS_TO_MS, (float)myReceiver.getCommandIssueTime(i) * NS_TO_MS,
                       (float)myReceiver.getSwapBufferTime(i) * NS_TO_MS, (float)myReceiver.getRemainderTime(i) * NS_TO_MS);
        }
      }

      myIgnoreInitialSamples = false;
    }
    else if (!myDelimiterAdded || myIgnoreInitialSamples) {
      timeline.add(mySynchronizedIdeTimeMs + (myReceiver.getLastSampleEndTime() - mySynchronizedHostTimeNs) / 1000000,
                   DeviceSampler.TYPE_DATA, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
      myDelimiterAdded = true;
      myIgnoreInitialSamples = false;
    }
    else {
      synchronizeClocks(device);
    }
  }

  @NotNull
  @Override
  public TimelineData createTimelineData() {
    return new TimelineData(9, SAMPLE_BUFFER_SIZE);
  }

  @Override
  public ThreeState getIsEnabledOnDevice(@NotNull IDevice device) {
    return ThreeState.YES;
  }

  private void synchronizeClocks(@NotNull IDevice device) {
    assert myClient != null;

    HostNanotimeReceiver hostNanotimeReceiver = new HostNanotimeReceiver();
    for (int i = 0; i < 3; ++i) {
      try {
        mySynchronizedIdeTimeMs = System.currentTimeMillis();
        device.executeShellCommand("cat /proc/timer_list", hostNanotimeReceiver, 1, TimeUnit.SECONDS);
        break;
      }
      catch (Exception ignored) {}
    }
    mySynchronizedHostTimeNs = hostNanotimeReceiver.getCurrentSystemTimeNs();
    if (mySynchronizedHostTimeNs < 0) {
      throw new RuntimeException("Could not synchronize time with the device.");
    }
  }

  /**
   * Output receiver for "dumpsys gfxinfo [pid] framestats" in API 22/M preview or later.
   */
  private static final class ProcessStatReceiver extends MultiLineReceiver {
    private long myLastSampleEndTime;

    private long myCurrentSampleFirstFrameTime;
    private TLongArrayList myEndTimes = new TLongArrayList();
    private TLongArrayList myVSyncDelays = new TLongArrayList();
    private TLongArrayList myInputHandlingTimes = new TLongArrayList();
    private TLongArrayList myAnimationTimes = new TLongArrayList();
    private TLongArrayList myTraversalTimes = new TLongArrayList();
    private TLongArrayList myDrawTimes = new TLongArrayList();
    private TLongArrayList mySyncTimes = new TLongArrayList();
    private TLongArrayList myCommandIssueTimes = new TLongArrayList();
    private TLongArrayList mySwapBufferTimes = new TLongArrayList();
    private TLongArrayList myRemainingTimes = new TLongArrayList();

    private boolean myFoundProfileSection = false;
    private boolean myInTimingSection = false;

    public ProcessStatReceiver() {
      super();
      reset();
    }

    public void reset() {
      myLastSampleEndTime = 0;
      myFoundProfileSection = false;
      myInTimingSection = false;
      resetSamples();
    }

    public void resetSamples() {
      myCurrentSampleFirstFrameTime = Long.MAX_VALUE;
      myEndTimes.resetQuick();
      myVSyncDelays.resetQuick();
      myInputHandlingTimes.resetQuick();
      myAnimationTimes.resetQuick();
      myTraversalTimes.resetQuick();
      myDrawTimes.resetQuick();
      mySyncTimes.resetQuick();
      myCommandIssueTimes.resetQuick();
      mySwapBufferTimes.resetQuick();
      myRemainingTimes.resetQuick();
    }

    public long getCurrentSampleFirstFrameTime() {
      return myCurrentSampleFirstFrameTime;
    }

    /**
     * Get the number of samples parsed.
     */
    public int getSampleSize() {
      return mySwapBufferTimes.size();
    }

    /**
     * Get the timestamp of the last sample parsed.
     */
    public long getLastSampleEndTime() {
      return myLastSampleEndTime;
    }

    /**
     * Get the time stamp of when the frame ended (in nanoseconds).
     */
    public long getEndTime(int index) {
      return myEndTimes.get(index);
    }

    /**
     * Get the time the app took before responding to the VSync signal.
     */
    public long getVSyncDelay(int index) {
      return myVSyncDelays.get(index);
    }

    /**
     * Get the time the app took to handle input.
     */
    public long getInputHandlingTime(int index) {
      return myInputHandlingTimes.get(index);
    }

    /**
     * Get the time the app took to perform animations.
     */
    public long getAnimationTime(int index) {
      return myAnimationTimes.get(index);
    }

    /**
     * Get the time the app took to perform measuring and layout.
     */
    public long getTraversalTime(int index) {
      return myTraversalTimes.get(index);
    }

    /**
     * Get the time the app took to issue draw commands.
     */
    public long getDrawTime(int index) {
      return myDrawTimes.get(index);
    }

    /**
     * Get the time the system took to sync in the rendering phase.
     */
    public long getSyncTime(int index) {
      return mySyncTimes.get(index);
    }

    /**
     * Get the time the system took to issue the rendering commands.
     */
    public long getCommandIssueTime(int index) {
      return myCommandIssueTimes.get(index);
    }

    /**
     * Get the time the system took to swap buffers on the GPU.
     */
    public long getSwapBufferTime(int index) {
      return mySwapBufferTimes.get(index);
    }

    /**
     * Get the remainder of the time spent on the frame, i.e. timings not included in the other categories.
     */
    public long getRemainderTime(int index) {
      return myRemainingTimes.get(index);
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public void processNewLines(@NotNull String[] lines) {
      for (String line : lines) {
        if (!myFoundProfileSection) {
          if (line.startsWith("Profile data in ms:")) {
            myFoundProfileSection = true;
          }
          continue;
        }

        if (line.startsWith("---PROFILEDATA---")) {
          // There are multiple sections that contain timing information, but we only care about one continuous timeline.
          // Therefore, we need to account for multiple sections.
          // TODO investigate if the sections ever show up in non-hierarchical order (so far, forward-parsing appears to be correct).
          myInTimingSection ^= true;
          continue;
        }

        if (!myInTimingSection) {
          continue;
        }

        String[] timings = line.split(",");
        if (timings.length == 14) {
          try {
            long flags = Long.parseLong(timings[0]);
            long endTime = Long.parseLong(timings[12]);
            if (flags == 0 && endTime > myLastSampleEndTime) {
              long intendedVSyncStart = Long.parseLong(timings[1]);
              long vSyncStart = Long.parseLong(timings[2]);
              long handleInputStart = Long.parseLong(timings[5]);
              long animationStart = Long.parseLong(timings[6]);
              long traversalsStart = Long.parseLong(timings[7]);
              long drawStart = Long.parseLong(timings[8]);
              long syncStart = Long.parseLong(timings[9]);
              long commandIssueStart = Long.parseLong(timings[10]);
              long swapStart = Long.parseLong(timings[11]);

              myVSyncDelays.add(vSyncStart - intendedVSyncStart);
              myInputHandlingTimes.add(animationStart - handleInputStart);
              myAnimationTimes.add(traversalsStart - animationStart);
              myTraversalTimes.add(drawStart - traversalsStart);
              myDrawTimes.add(syncStart - drawStart);
              mySyncTimes.add(commandIssueStart - syncStart);
              myCommandIssueTimes.add(swapStart - commandIssueStart);
              mySwapBufferTimes.add(endTime - swapStart);
              myEndTimes.add(endTime);
              myRemainingTimes.add(handleInputStart - vSyncStart);

              myCurrentSampleFirstFrameTime = Math.min(myCurrentSampleFirstFrameTime, intendedVSyncStart);
              myLastSampleEndTime = Math.max(myLastSampleEndTime, endTime);
            }
          }
          catch (NumberFormatException ignored) {
          }
        }
      }
    }
  }

  /**
   * Output receiver for "cat /proc/timer_list" in API 22/M preview or later.
   */
  private static final class HostNanotimeReceiver extends MultiLineReceiver {
    static final Pattern myCurrentTimePattern = Pattern.compile("^now at (\\d+) nsecs$");
    private long myCurrentSystemTimeNs = -1;

    public long getCurrentSystemTimeNs() {
      return myCurrentSystemTimeNs;
    }

    @Override
    public void processNewLines(String[] lines) {
      for (String line : lines) {
        if (myCurrentSystemTimeNs < 0) {
          Matcher matcher = myCurrentTimePattern.matcher(line);
          if (matcher.find()) {
            myCurrentSystemTimeNs = Long.parseLong(matcher.group(1));
            return;
          }
        }
      }
    }

    @Override
    public boolean isCancelled() {
      return myCurrentSystemTimeNs >= 0;
    }
  }
}
