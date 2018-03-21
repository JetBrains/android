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
import java.util.Arrays;
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

  private PowerProfile.CpuCoreUsage[] myLastCpuCoresUsage;
  @NotNull
  private PowerProfile.NetworkType myNetworkType = PowerProfile.NetworkType.NONE;
  private long myReceivingBps;
  private long mySendingBps;

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
        PowerProfile.CpuCoreUsage[] cpuCoresUsage = (PowerProfile.CpuCoreUsage[])eventArg;
        if (!Arrays.equals(myLastCpuCoresUsage, cpuCoresUsage)) {
          myLastCpuCoresUsage = cpuCoresUsage;
          addNewCpuSample(timestampNs);
        }
        break;

      case NETWORK_USAGE:
        PowerProfile.NetworkStats networkStats = (PowerProfile.NetworkStats)eventArg;
        if (myNetworkType != networkStats.myNetworkType ||
            myReceivingBps != networkStats.myReceivingBps ||
            mySendingBps != networkStats.mySendingBps) {
          // TODO(b/75977959): Fix stale data usage below. E.g. when we transition from WIFI to RADIO, we'd have a spike since WIFI much faster than RADIO.
          myNetworkType = networkStats.myNetworkType;
          myReceivingBps = networkStats.myReceivingBps;
          mySendingBps = networkStats.mySendingBps;
          addNewNetworkSample(timestampNs, new PowerProfile.NetworkStats(myNetworkType, myReceivingBps, mySendingBps));
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
    addNewSample(timestampNs, sample -> sample.setCpuUsage(myPowerProfile.getCpuUsage(myLastCpuCoresUsage)));
  }

  private void addNewNetworkSample(long timestampNs, @NotNull PowerProfile.NetworkStats networkStats) {
    addNewSample(timestampNs, sample -> sample.setNetworkUsage(myPowerProfile.getNetworkUsage(networkStats)));
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

    // We want to compare samples to see if their usage amounts are the same even if they are at
    // different timestamps. The easiest way to do this is to make a copy of the two samples
    // with their timestamps stubbed out.
    EnergyProfiler.EnergySample prevSampleNoTime = prevSample.toBuilder().setTimestamp(0).build();
    EnergyProfiler.EnergySample newSampleNoTime = newSample.toBuilder().setTimestamp(0).build();

    if (!prevSampleNoTime.equals(newSampleNoTime)) {
      if (prevSample.getTimestamp() == newSample.getTimestamp()) {
        // This means we had multiple events occur at the same time. Replace with the latest sample
        // in that case.
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
     * arg: An array of CPU usage {@link PowerProfile.CpuCoreUsage}, each element on a per-core level.
     */
    CPU_USAGE,

    /**
     * Something about the network hardware has changed.
     * arg: A {@link PowerProfile.NetworkStats} value.
     */
    NETWORK_USAGE,
  }
}
