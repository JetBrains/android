/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.datastore.energy;

import com.android.annotations.VisibleForTesting;
import com.android.tools.profiler.proto.EnergyProfiler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * A battery model is an modeled representation of a battery's state over time. It can be queried
 * for for an estimate of how much energy was being drawn from the battery at any given time.
 *
 * Externally, someone should feed relevant events into the battery model using
 * {@link #handleEvent(long, Event, Object)}. Each {@link Event} takes an argument; read the
 * enumeration's comment to see what it expects.
 *
 * Once events have been added, use {@link #getSamplesBetween(long, long)} to retrieve
 * all energy values between two times. Samples will automatically be bucketed at a regular
 * interval - that is, the results will give the appearance of being sampled periodically and
 * discretely, as opposed to returning exact timestamps that an event happened at.
 *
 * Note that this means, if a couple of events happen within microseconds of each other,
 * they can be merged into a single bucket.
 *
 * TODO(b/73538823): Move battery model out of the datastore
 */
public final class BatteryModel {
  private static final long DEFAULT_SAMPLE_INTERVAL_NS = TimeUnit.MILLISECONDS.toNanos(200);
  /**
   * Sparse samples will be converted to dense samples on the fly when the user calls
   * {@link #getSamplesBetween(long, long)}.
   */
  @NotNull
  private final List<EnergyProfiler.EnergySample> mySparseSamples = new ArrayList<>();
  @NotNull
  private final PowerProfile myPowerProfile;
  private final long mySampleIntervalNs;

  private double myCpuTotalPercent;
  @NotNull
  private PowerProfile.NetworkType myNetworkType = PowerProfile.NetworkType.NONE;
  private boolean myIsTransmitting;
  private boolean myIsReceiving;

  public BatteryModel() {
    this(new PowerProfile.DefaultPowerProfile(), DEFAULT_SAMPLE_INTERVAL_NS);
  }

  @VisibleForTesting
  public BatteryModel(@NotNull PowerProfile powerProfile, long sampleIntervalNs) {
    myPowerProfile = powerProfile;
    mySampleIntervalNs = sampleIntervalNs;
    mySparseSamples.add(0, EnergyProfiler.EnergySample.getDefaultInstance());
  }

  /**
   * Round {@code originalValue} to the nearest {@code alignment}. For example,
   * with an alignment of 200:
   *
   * 193  -> 200
   * 299  -> 200
   * 301  -> 400
   * 1234 -> 1200
   */
  @VisibleForTesting
  static long align(long originalValue, long alignment) {
    long valueMod = originalValue % alignment;
    if (valueMod <= alignment / 2) {
      originalValue -= valueMod;
    }
    else {
      originalValue += (alignment - valueMod);
    }
    return originalValue;
  }

  /**
   * Handle an {@link Event} that occurred at some time {@code timestampNs}.
   *
   * Each event is expected to handle a typed {@code eventArg}. See each event's javadoc comment
   * to see what the right arg type is.
   */
  public void handleEvent(long timestampNs, @NotNull BatteryModel.Event energyEvent, Object eventArg) {
    switch (energyEvent) {
      case CPU_USAGE:
        double cpuTotalPercent = (double)eventArg;
        if (Double.compare(myCpuTotalPercent, cpuTotalPercent) != 0) {
          myCpuTotalPercent = cpuTotalPercent;
          addNewCpuSample(timestampNs);
        }
        break;
      case NETWORK_TYPE_CHANGED:
        PowerProfile.NetworkType networkType = (PowerProfile.NetworkType)eventArg;
        if (myNetworkType != networkType) {
          myNetworkType = networkType;
          addNewNetworkSample(timestampNs);
        }
        break;

      case NETWORK_DOWNLOAD:
        boolean isReceiving = (boolean)eventArg;
        if (myIsReceiving != isReceiving) {
          myIsReceiving = isReceiving;
          addNewNetworkSample(timestampNs);
        }
        break;
      case NETWORK_UPLOAD:
        boolean isTransmitting = (boolean)eventArg;
        if (myIsTransmitting != isTransmitting) {
          myIsTransmitting = isTransmitting;
          addNewNetworkSample(timestampNs);
        }
        break;
    }
  }

  @NotNull
  public List<EnergyProfiler.EnergySample> getSamplesBetween(long startInclusiveNs, long endExclusiveNs) {
    int currIndex = getSampleIndexFor(startInclusiveNs);
    List<EnergyProfiler.EnergySample> samples = new ArrayList<>();
    // By aligning start time to our bucket interval and incrementing by that interval, every
    // intermediate for-loop value will be aligned as well.
    for (long timestampNs = alignToSampleInterval(startInclusiveNs); timestampNs < endExclusiveNs; timestampNs += mySampleIntervalNs) {
      EnergyProfiler.EnergySample nearestSample = mySparseSamples.get(currIndex);
      int nextIndex = currIndex + 1;
      if (nextIndex < mySparseSamples.size()) {
        EnergyProfiler.EnergySample nextSample = mySparseSamples.get(nextIndex);
        if (nextSample.getTimestamp() <= timestampNs) {
          ++currIndex;
          nearestSample = nextSample;
        }
      }
      samples.add(nearestSample.toBuilder().setTimestamp(timestampNs).build());
    }
    return samples;
  }

  private int getSampleIndexFor(long timestampNs) {
    for (int i = mySparseSamples.size() - 1; i >= 0; i--) {
      EnergyProfiler.EnergySample sample = mySparseSamples.get(i);
      if (sample.getTimestamp() <= timestampNs) {
        // TODO (b/73486903): How slow is this? Replace with binary search?
        return i;
      }
    }
    return 0;
  }

  private void addNewCpuSample(long timestampNs) {
    addNewSample(timestampNs, sample -> sample.setCpuUsage(myPowerProfile.getCpuUsage(myCpuTotalPercent)));
  }

  private void addNewNetworkSample(long timestampNs) {
    addNewSample(timestampNs, sample -> {
      PowerProfile.NetworkState networkState =
        (myIsReceiving || myIsTransmitting) ? PowerProfile.NetworkState.ACTIVE : PowerProfile.NetworkState.IDLE;
      return sample.setNetworkUsage(myPowerProfile.getNetworkUsage(myNetworkType, networkState));
    });
  }

  private void addNewSample(long timestampNs,
                            Function<EnergyProfiler.EnergySample.Builder, EnergyProfiler.EnergySample.Builder> produceNewSample) {
    timestampNs = alignToSampleInterval(timestampNs);

    int prevSampleIndex = getSampleIndexFor(timestampNs);
    EnergyProfiler.EnergySample prevSample = mySparseSamples.get(prevSampleIndex);
    if (timestampNs < prevSample.getTimestamp()) {
      throw new IllegalArgumentException("Received energy events out of order");
    }

    EnergyProfiler.EnergySample newSample = produceNewSample.apply(prevSample.toBuilder().setTimestamp(timestampNs)).build();
    if (!prevSample.equals(newSample)) {
      if (prevSample.getTimestamp() == timestampNs) {
        // This means we had multiple events at the same time. Accumulate them into a single
        // sample (by replacing the last sample)
        mySparseSamples.remove(prevSampleIndex);
        mySparseSamples.add(prevSampleIndex, newSample);
      }
      else {
        mySparseSamples.add(prevSampleIndex + 1, newSample);
      }
    }
  }

  /**
   * Align {@code timestampNs} into its closest sample bucket.
   */
  private long alignToSampleInterval(long timestampNs) {
    return align(timestampNs, mySampleIntervalNs);
  }

  public enum Event {
    /**
     * The amount of CPU being used changed.
     * arg: A double representing total CPU percent, from 0.0 to 1.0
     */
    CPU_USAGE,

    /**
     * The current network type has changed.
     * arg: A {@link PowerProfile.NetworkType} value.
     */
    NETWORK_TYPE_CHANGED,

    /**
     * The app started/stopped downloading some bytes.
     * arg: true if downloading, false if stopped.
     */
    NETWORK_DOWNLOAD,

    /**
     * The app started/stopped uploading some bytes.
     * arg: true if downloading, false if stopped.
     */
    NETWORK_UPLOAD,

  }
}
