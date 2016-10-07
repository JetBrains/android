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
import gnu.trove.TFloatArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Sampler for Lollipop API or higher.
 */
public final class LHandler implements GfxinfoHandler {
  public static final int MIN_API_LEVEL = 21;

  private ProcessStatReceiver myReceiver = new ProcessStatReceiver();

  private long myLastSampleTime;
  private boolean myDelimiterAdded = true;
  private boolean myIgnoreInitialSamples = true;

  @Override
  public boolean accept(@NotNull Client client) {
    return GpuSampler.decodeApiLevel(client) >= MIN_API_LEVEL;
  }

  @Override
  public void setClient(@Nullable Client client) {
    myLastSampleTime = System.currentTimeMillis();
    myReceiver.reset();
    myDelimiterAdded = true;
    myIgnoreInitialSamples = true;
  }

  @Override
  public void sample(@NotNull IDevice device, @NotNull ClientData data, @NotNull TimelineData timeline) throws Exception {
    int pid = data.getPid();
    long currentTime = System.currentTimeMillis();

    myReceiver.resetSamples();
    device.executeShellCommand("dumpsys gfxinfo " + pid, myReceiver, 1, TimeUnit.SECONDS);

    long timeDelta = currentTime - myLastSampleTime;
    if (myReceiver.getLogSize() > 0) {
      if (myDelimiterAdded) {
        timeline.add(myLastSampleTime, DeviceSampler.TYPE_DATA, 0.0f, 0.0f, 0.0f, 0.0f);
        myDelimiterAdded = false;
      }

      if (!myIgnoreInitialSamples) {
        for (int i = 0; i < myReceiver.getLogSize(); ++i) {
          long time = timeDelta * (long)(i + 1) / (long)myReceiver.getLogSize() + myLastSampleTime;
          timeline.add(time, DeviceSampler.TYPE_DATA, myReceiver.getDrawTime(i), myReceiver.getPrepareTime(i), myReceiver.getProcessTime(i),
                       myReceiver.getExecuteTime(i));
        }
      }
      myIgnoreInitialSamples = false;
    }
    else {
      if (!myDelimiterAdded) {
        timeline.add(myLastSampleTime, DeviceSampler.TYPE_DATA, 0.0f, 0.0f, 0.0f, 0.0f);
        myDelimiterAdded = true;
      }
      timeline.add(currentTime, DeviceSampler.TYPE_DATA, 0.0f, 0.0f, 0.0f, 0.0f);
    }
    myLastSampleTime = currentTime;
  }

  @NotNull
  @Override
  public TimelineData createTimelineData() {
    return new TimelineData(4, SAMPLE_BUFFER_SIZE);
  }

  @Override
  public ThreeState getIsEnabledOnDevice(@NotNull IDevice device) {
    return JHandler.parseIsEnabledOnDevice(device);
  }

  /**
   * Output receiver for "dumpsys gfxinfo <pid>" in API 21 or later.
   */
  private static final class ProcessStatReceiver extends MultiLineReceiver {
    private List<String> myOutput = new ArrayList<String>();

    private TFloatArrayList myDrawTimes = new TFloatArrayList();
    private TFloatArrayList myPrepareTimes = new TFloatArrayList();
    private TFloatArrayList myProcessTimes = new TFloatArrayList();
    private TFloatArrayList myExecuteTimes = new TFloatArrayList();

    public void reset() {
      resetSamples();
    }

    public void resetSamples() {
      myOutput.clear();
      myDrawTimes.resetQuick();
      myPrepareTimes.resetQuick();
      myProcessTimes.resetQuick();
      myExecuteTimes.resetQuick();
    }

    /*
     * Get the number of timing entries read from the dumpsys command.
     */
    public int getLogSize() {
      return myExecuteTimes.size(); // Use myExecuteTimes's size since it's the most conservative (due to NumberFormatException possibility)
    }

    /**
     * Get the time the app took to issue draw commands.
     */
    @Nullable
    public Float getDrawTime(int index) {
      return myDrawTimes.get(index);
    }

    /**
     * Get the time the system took to prepare the rendering commands.
     */
    @Nullable
    public Float getPrepareTime(int index) {
      return myPrepareTimes.get(index);
    }

    /**
     * Get the time the system took to process and upload the rendering commands.
     */
    @Nullable
    public Float getProcessTime(int index) {
      return myProcessTimes.get(index);
    }

    /**
     * Get the time the system took to execute the rendering commands on the gpu.
     */
    @Nullable
    public Float getExecuteTime(int index) {
      return myExecuteTimes.get(index);
    }

    @Override
    public boolean isCancelled() {
      return false;
    }

    @Override
    public void processNewLines(@NotNull String[] lines) {
      myOutput.addAll(Arrays.asList(lines));
    }

    @Override
    public void done() {
      super.done();

      int profileSectionIndex = 0;
      // First look for the "Profile data in ms:" section.
      for (; profileSectionIndex < myOutput.size(); ++profileSectionIndex) {
        if (myOutput.get(profileSectionIndex).startsWith("Profile data in ms:")) {
          // Now fast forward to the line that shows the four columns "Draw Prepare Process Execute"
          for (profileSectionIndex += 1; profileSectionIndex < myOutput.size(); ++profileSectionIndex) {
            String[] tokens = myOutput.get(profileSectionIndex).split("\\s+");
            if (tokens.length == 4 &&
                "Draw".equals(tokens[0]) &&
                "Prepare".equals(tokens[1]) &&
                "Process".equals(tokens[2]) &&
                "Execute".equals(tokens[3])) {
              profileSectionIndex += 1;
              break;
            }
          }
          break;
        }
      }

      for (int i = profileSectionIndex; i < myOutput.size(); ++i) {
        String line = myOutput.get(i);
        if (line.startsWith("View hierarchy:")) {
          break;
        }
        String[] timings = line.split("\\s+");
        if (timings.length == 4) {
          try {
            myDrawTimes.add(Float.parseFloat(timings[0]));
            myPrepareTimes.add(Float.parseFloat(timings[1]));
            myProcessTimes.add(Float.parseFloat(timings[2]));
            myExecuteTimes.add(Float.parseFloat(timings[3]));
          }
          catch (NumberFormatException ignored) {
          }
        }
      }
    }
  }
}
