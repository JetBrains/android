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
package com.android.tools.idea.monitor.gpu;

import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.MultiLineReceiver;
import com.android.tools.chartlib.TimelineData;
import com.android.tools.idea.monitor.DeviceSampler;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GpuSampler extends DeviceSampler {
  private static final float NS_TO_MS = 0.000001f;

  private int myApiLevel;
  private long mySynchronizedIdeTimeMs;
  private long mySynchronizedHostTimeNs;
  private long myLastSampleTime;
  private boolean myDelimiterAdded;
  private boolean myIgnoreInitialSamples = false;

  public GpuSampler(@NotNull TimelineData data, int sampleFrequencyMs, int apiLevel) {
    super(data, sampleFrequencyMs);
    myApiLevel = apiLevel;
  }

  @NotNull
  @Override
  public String getName() {
    return "GPU Sampler";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "gpu usage information";
  }

  public void resetClientState(@Nullable Client client, @NotNull TimelineData data, int apiLevel) {
    stop();
    myClient = client;
    myData = data;
    myData.clear();
    myApiLevel = apiLevel;
    myLastSampleTime = myApiLevel >= GpuMonitorView.DETAILED_API_LEVEL ? 0 : System.currentTimeMillis();
    mySynchronizedHostTimeNs = -1;
    mySynchronizedIdeTimeMs = -1;
    myDelimiterAdded = true;
    myIgnoreInitialSamples = true;
    start();
  }

  @Override
  public void setClient(@Nullable Client client) {
    super.setClient(client);
    myIgnoreInitialSamples = true;
    if (client != null) {
      synchronizeClocks(client);
    }
  }

  @Override
  protected void sample(boolean forced) throws InterruptedException {
    Client client = getClient();
    if (client == null) {
      return;
    }

    IDevice device = client.getDevice();
    if (device != null) {
      try {
        ClientData data = client.getClientData();

        if (myApiLevel >= GpuMonitorView.DETAILED_API_LEVEL) {
          sampleMOrLater(device, data);
        }
        else {
          sampleLOrEarlier(device, data);
        }
      }
      catch (Exception ignored) {
      }
    }
  }

  private void synchronizeClocks(@NotNull Client client) {
    HostNanotimeReceiver hostNanotimeReceiver = new HostNanotimeReceiver();
    for (int i = 0; i < 3; ++i) {
      try {
        mySynchronizedIdeTimeMs = System.currentTimeMillis();
        client.getDevice().executeShellCommand("cat /proc/timer_list", hostNanotimeReceiver, 1, TimeUnit.SECONDS);
        break;
      }
      catch (Exception ignored) {}
    }
    mySynchronizedHostTimeNs = hostNanotimeReceiver.getCurrentSystemTimeNs();
    if (mySynchronizedHostTimeNs < 0) {
      stop();
    }
  }

  private void sampleMOrLater(@NotNull IDevice device, @NotNull ClientData data) throws Exception {
    int pid = data.getPid();

    ProcessStatReceiverApi23OrLater dumpsysReceiver = new ProcessStatReceiverApi23OrLater(myLastSampleTime);
    device.executeShellCommand("dumpsys gfxinfo " + pid + " framestats", dumpsysReceiver, 1, TimeUnit.SECONDS);

    if (dumpsysReceiver.getSampleSize() > 0) {
      if (myDelimiterAdded) {
        myData.add(mySynchronizedIdeTimeMs + (dumpsysReceiver.getFirstFrameStartTime() - mySynchronizedHostTimeNs) / 1000000, TYPE_DATA,
                   0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        myDelimiterAdded = false;
      }

      if (!myIgnoreInitialSamples) {
        for (int i = 0; i < dumpsysReceiver.getSampleSize(); ++i) {
          long sampleTime = mySynchronizedIdeTimeMs + (dumpsysReceiver.getEndTime(i) - mySynchronizedHostTimeNs) / 1000000;
          myData.add(sampleTime, TYPE_DATA, (float)dumpsysReceiver.getVSyncDelay(i) * NS_TO_MS,
                     (float)dumpsysReceiver.getInputHandlingTime(i) * NS_TO_MS, (float)dumpsysReceiver.getAnimationTime(i) * NS_TO_MS,
                     (float)dumpsysReceiver.getTraversalTime(i) * NS_TO_MS, (float)dumpsysReceiver.getDrawTime(i) * NS_TO_MS,
                     (float)dumpsysReceiver.getSyncTime(i) * NS_TO_MS, (float)dumpsysReceiver.getCommandIssueTime(i) * NS_TO_MS,
                     (float)dumpsysReceiver.getSwapBufferTime(i) * NS_TO_MS, (float)dumpsysReceiver.getRemainderTime(i) * NS_TO_MS);
        }
      }

      myLastSampleTime = dumpsysReceiver.getLastSampleEndTime();
      myIgnoreInitialSamples = false;
    }
    else if (!myDelimiterAdded || myIgnoreInitialSamples) {
      myData.add(mySynchronizedIdeTimeMs + (myLastSampleTime - mySynchronizedHostTimeNs) / 1000000, TYPE_DATA,
                 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
      myDelimiterAdded = true;
      myIgnoreInitialSamples = false;
    }
    else {
      Client client = getClient();
      assert client != null;
      synchronizeClocks(client);
    }
  }

  private void sampleLOrEarlier(@NotNull IDevice device, @NotNull ClientData data) throws Exception {
    int pid = data.getPid();
    long currentTime = System.currentTimeMillis();

    ProcessStatReceiverApi22OrEarlier dumpsysReceiver = new ProcessStatReceiverApi22OrEarlier();
    device.executeShellCommand("dumpsys gfxinfo " + pid, dumpsysReceiver, 1, TimeUnit.SECONDS);

    long timeDelta = currentTime - myLastSampleTime;
    if (dumpsysReceiver.getLogSize() > 0) {
      if (!myIgnoreInitialSamples) {
        if (myDelimiterAdded) {
          myData.add(currentTime, TYPE_DATA, 0.0f, 0.0f, 0.0f, 0.0f);
          myDelimiterAdded = false;
        }
        for (int i = 0; i < dumpsysReceiver.getLogSize(); ++i) {
          long time = timeDelta * (long)(i + 1) / (long)dumpsysReceiver.getLogSize() + myLastSampleTime;
          myData.add(time, TYPE_DATA, dumpsysReceiver.getDrawTime(i), dumpsysReceiver.getPrepareTime(i), dumpsysReceiver.getProcessTime(i),
                     dumpsysReceiver.getExecuteTime(i));
        }
      }
      myIgnoreInitialSamples = false;
    }
    else {
      if (!myDelimiterAdded) {
        myData.add(myLastSampleTime, TYPE_DATA, 0.0f, 0.0f, 0.0f, 0.0f);
        myDelimiterAdded = true;
      }
      myData.add(currentTime, TYPE_DATA, 0.0f, 0.0f, 0.0f, 0.0f);
    }
    myLastSampleTime = currentTime;
  }

  /**
   * Output receiver for "dumpsys gfxinfo <pid>" in API 22 or earlier.
   */
  private static final class ProcessStatReceiverApi22OrEarlier extends MultiLineReceiver {
    List<String> myOutput = new ArrayList<String>(500);

    private List<Float> myDrawTimes = new ArrayList<Float>();
    private List<Float> myPrepareTimes = new ArrayList<Float>();
    private List<Float> myProcessTimes = new ArrayList<Float>();
    private List<Float> myExecuteTimes = new ArrayList<Float>();

    /*
     * Get the number of timing entries read from the dumpsys command.
     */
    public int getLogSize() {
      return myExecuteTimes.size(); // Use myExecuteTimes's size since it's the most conservative (due to NumberFormatException possibility)
    }

    /**
     * Get the parsed draw time.
     *
     * @return the time the app took to issue draw commands
     */
    @Nullable
    public Float getDrawTime(int index) {
      return myDrawTimes.get(index);
    }

    /**
     * Get the parsed prepare time.
     *
     * @return the time the system took to prepare the rendering commands
     */
    @Nullable
    public Float getPrepareTime(int index) {
      return myPrepareTimes.get(index);
    }

    /**
     * Get the parsed prepare time.
     *
     * @return the time the system took to process and upload the rendering commands
     */
    @Nullable
    public Float getProcessTime(int index) {
      return myProcessTimes.get(index);
    }

    /**
     * Get the parsed execute time.
     *
     * @return the time the system took to execute the rendering commands on the gpu
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

  /**
   * Output receiver for "dumpsys gfxinfo [pid] framestats" in API 22/M preview or later.
   */
  private static final class ProcessStatReceiverApi23OrLater extends MultiLineReceiver {
    private long myFirstFrameStartTime = Long.MAX_VALUE;
    private long myLastSampleEndTime;

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

    private boolean myFoundStart = false;
    private boolean myFoundEnd = false;

    public ProcessStatReceiverApi23OrLater(long lastSampleEndTime) {
      super();
      myLastSampleEndTime = lastSampleEndTime;
    }

    public long getFirstFrameStartTime() {
      return myFirstFrameStartTime;
    }

    /**
     * Get the number of samples parsed.
     *
     * @return number of samples parsed
     */
    public int getSampleSize() {
      return mySwapBufferTimes.size();
    }

    /**
     * Get the timestamp of the last command parsed.
     *
     * @return timestamp of the last parsed sample
     */
    public long getLastSampleEndTime() {
      return myLastSampleEndTime;
    }

    /**
     * Get the end time of the frame.
     *
     * @return the time stamp of when the frame ended (in nanoseconds)
     */
    public long getEndTime(int index) {
      return myEndTimes.get(index);
    }

    /**
     * Get the VSync delay.
     *
     * @return the time the app took before responding to the vsync signal
     */
    public long getVSyncDelay(int index) {
      return myVSyncDelays.get(index);
    }

    /**
     * Get the input handling time.
     *
     * @return the time the app took to handle input
     */
    public long getInputHandlingTime(int index) {
      return myInputHandlingTimes.get(index);
    }

    /**
     * Get the animation time.
     *
     * @return the time the app took to perform animations
     */
    public long getAnimationTime(int index) {
      return myAnimationTimes.get(index);
    }

    /**
     * Get the traversal time.
     *
     * @return the time the app took to perform measuring and layout
     */
    public long getTraversalTime(int index) {
      return myTraversalTimes.get(index);
    }

    /**
     * Get the draw time.
     *
     * @return the time the app took to issue draw commands
     */
    public long getDrawTime(int index) {
      return myDrawTimes.get(index);
    }

    /**
     * Get the sync time.
     *
     * @return the time the system took to sync in the rendering phase
     */
    public long getSyncTime(int index) {
      return mySyncTimes.get(index);
    }

    /**
     * Get the rendering command issue time.
     *
     * @return the time the system took to issue the rendering commands
     */
    public long getCommandIssueTime(int index) {
      return myCommandIssueTimes.get(index);
    }

    /**
     * Get the swap buffer time.
     *
     * @return the time the system took to swap buffers on the GPU
     */
    public long getSwapBufferTime(int index) {
      return mySwapBufferTimes.get(index);
    }

    /**
     * Get the remainder of the time spent on the frame.
     *
     * @return the remainder of the time spent on the frame, i.e. timings not included in the other categories
     */
    public long getRemainderTime(int index) {
      return myRemainingTimes.get(index);
    }

    @Override
    public boolean isCancelled() {
      return myFoundEnd;
    }

    @Override
    public void processNewLines(@NotNull String[] lines) {
      for (String line : lines) {
        if (myFoundEnd) {
          break;
        }

        if (line.startsWith("---PROFILEDATA---")) {
          if (myFoundStart) {
            myFoundEnd = true;
            break;
          }
          else {
            myFoundStart = true;
            continue;
          }
        }

        if (!myFoundStart) {
          continue;
        }

        String[] timings = line.split(",");
        if (timings.length == 13) {
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

              myFirstFrameStartTime = Math.min(myFirstFrameStartTime, intendedVSyncStart);
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
