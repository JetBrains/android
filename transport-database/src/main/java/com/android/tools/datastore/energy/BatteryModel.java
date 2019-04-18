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

import com.google.common.annotations.VisibleForTesting;
import com.android.tools.datastore.energy.PowerProfile.LocationStats;
import com.android.tools.datastore.energy.PowerProfile.LocationType;
import com.android.tools.profiler.proto.Energy;
import com.android.tools.profiler.proto.EnergyProfiler.EnergySample;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A battery model is an modeled representation of a battery's state over time. It can be queried
 * for for an estimate of how much energy was being drawn from the battery at any given time.
 * <p>
 * Externally, someone should feed relevant events into the battery model using
 * {@link #handleEvent(long, Event, Object)}. Each {@link Event} takes an argument; read the
 * enumeration's comment to see what it expects.
 * <p>
 * Once events have been added, use {@link #getSamplesBetween(long, long)} to retrieve
 * all energy values between two times. Samples will automatically be bucketed at a regular
 * interval - that is, the results will give the appearance of being sampled periodically and
 * discretely, as opposed to returning exact timestamps that an event happened at.
 * <p>
 * Note that this means, if a couple of events happen within microseconds of each other,
 * they can be merged into a single bucket.
 * <p>
 * TODO(b/73538823): Move battery model out of the datastore
 */
public final class BatteryModel {
  private static final long DEFAULT_SAMPLE_INTERVAL_NS = TimeUnit.MILLISECONDS.toNanos(200);

  private static final long GPS_LOCK_DURATION_NS = TimeUnit.SECONDS.toNanos(7);
  private static final long NETWORK_SCAN_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(500);
  // The total number of forward smoothing samples we will use to amortize the cost of the GPS energy use.
  private static final int LOCATION_SMOOTHING_SAMPLES = 4;

  /**
   * Sparse samples will be converted to dense samples on the fly when the user calls
   * {@link #getSamplesBetween(long, long)}.
   */
  @NotNull
  private final List<EnergySample> mySparseSamples = new ArrayList<>();
  @NotNull
  private final PowerProfile myPowerProfile;
  private final long mySampleIntervalNs;

  private PowerProfile.CpuCoreUsage[] myLastCpuCoresUsage;
  @NotNull
  private PowerProfile.NetworkType myNetworkType = PowerProfile.NetworkType.NONE;
  private long myReceivingBps;
  private long mySendingBps;

  private Map<Integer, Long> myGpsLockonMap = new HashMap<>(); // event ID -> initial GPS location request timestamp
  private Map<Integer, EnergySample> myLocationSmoothingMap = new HashMap<>(); // event ID -> event/smoothing start sample
  private long myLastNetworkLocationOffTime = 0;
  private final long mySmoothingEndDeltaTime;
  private long myLastGpsOffTime = 0;

  public BatteryModel() {
    this(new PowerProfile.DefaultPowerProfile(), DEFAULT_SAMPLE_INTERVAL_NS);
  }

  @VisibleForTesting
  public BatteryModel(@NotNull PowerProfile powerProfile, long sampleIntervalNs) {
    myPowerProfile = powerProfile;
    mySampleIntervalNs = sampleIntervalNs;
    mySmoothingEndDeltaTime = LOCATION_SMOOTHING_SAMPLES * mySampleIntervalNs;
    mySparseSamples.add(0, EnergySample.newBuilder().setEnergyUsage(Energy.EnergyUsageData.getDefaultInstance()).build());
  }

  /**
   * Round {@code originalValue} to the nearest {@code alignment}. For example,
   * with an alignment of 200:
   * <p>
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
   * <p>
   * Each event is expected to handle a typed {@code eventArg}. See each event's javadoc comment
   * to see what the right arg type is.
   */
  public void handleEvent(long timestampNs, @NotNull BatteryModel.Event energyEvent, Object eventArg) {
    PowerProfile.LocationEvent locationEvent;
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

      case LOCATION_REGISTER:
        locationEvent = (PowerProfile.LocationEvent)eventArg;
        if (locationEvent.myLocationType == LocationType.GPS) {
          // TODO(b/77588320) Remove forward charging and associated data structures.
          myGpsLockonMap.put(locationEvent.myEventId, timestampNs);
          addNewLocationSample(timestampNs + mySampleIntervalNs, new LocationStats(LocationType.GPS_ACQUIRE, 0, mySampleIntervalNs));
        }
        break;

      case LOCATION_UNREGISTER:
        locationEvent = (PowerProfile.LocationEvent)eventArg;
        Long timestamp = myGpsLockonMap.remove(locationEvent.myEventId);
        if (timestamp != null) {
          // This should only be entered if the location event is GPS, and the first location fix hasn't been entered.
          addNewLocationSample(timestampNs + mySampleIntervalNs, new LocationStats(LocationType.NONE, 0, mySampleIntervalNs));
        }
        break;

      case LOCATION_UPDATE:
        handleLocationUpdateEvent(timestampNs, (PowerProfile.LocationEvent)eventArg);
        break;
    }
  }

  @NotNull
  public List<EnergySample> getSamplesBetween(long startInclusiveNs, long endExclusiveNs) {
    int currIndex = getSampleIndexFor(startInclusiveNs);
    List<EnergySample> samples = new ArrayList<>();
    // By aligning start time to our bucket interval and incrementing by that interval, every
    // intermediate for-loop value will be aligned as well.
    for (long timestampNs = alignToSampleInterval(startInclusiveNs); timestampNs < endExclusiveNs; timestampNs += mySampleIntervalNs) {
      EnergySample nearestSample = mySparseSamples.get(currIndex);
      int nextIndex = currIndex + 1;
      if (nextIndex < mySparseSamples.size()) {
        EnergySample nextSample = mySparseSamples.get(nextIndex);
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
      EnergySample sample = mySparseSamples.get(i);
      if (sample.getTimestamp() <= timestampNs) {
        // TODO (b/73486903): How slow is this? Replace with binary search?
        return i;
      }
    }
    return 0;
  }

  private void addNewCpuSample(long timestampNs) {
    addNewSample(timestampNs, usage -> usage.setCpuUsage(myPowerProfile.getCpuUsage(myLastCpuCoresUsage)));
  }

  private void addNewNetworkSample(long timestampNs, @NotNull PowerProfile.NetworkStats networkStats) {
    addNewSample(timestampNs, usage -> usage.setNetworkUsage(myPowerProfile.getNetworkUsage(networkStats)));
  }

  @NotNull
  private EnergySample addNewLocationSample(long timestampNs, @NotNull LocationStats locationStats) {
    return addNewSample(timestampNs, usage -> usage.setLocationUsage(myPowerProfile.getLocationUsage(locationStats)));
  }

  private void removeLocationSample(long timestampNs) {
    removeDataFromSample(timestampNs, sample -> sample.setLocationUsage(0));
  }

  @NotNull
  private EnergySample addNewSample(long timestampNs,
                                    @NotNull Function<Energy.EnergyUsageData.Builder, Energy.EnergyUsageData.Builder> produceNewUsage) {
    timestampNs = alignToSampleInterval(timestampNs);

    int prevSampleIndex = getSampleIndexFor(timestampNs);
    EnergySample prevSample = mySparseSamples.get(prevSampleIndex);
    if (timestampNs < prevSample.getTimestamp()) {
      throw new IllegalArgumentException("Received energy events out of order");
    }

    EnergySample newSample = EnergySample.newBuilder()
      .setTimestamp(timestampNs)
      .setEnergyUsage(produceNewUsage.apply(prevSample.getEnergyUsage().toBuilder()))
      .build();

    // We want to compare samples to see if their usage amounts are the same even if they are at
    // different timestamps. The easiest way to do this is to make a copy of the two samples
    // with their timestamps stubbed out.
    EnergySample prevSampleNoTime = prevSample.toBuilder().setTimestamp(0).build();
    EnergySample newSampleNoTime = newSample.toBuilder().setTimestamp(0).build();

    if (!prevSampleNoTime.equals(newSampleNoTime)) {
      if (prevSample.getTimestamp() == newSample.getTimestamp()) {
        // This means we had multiple events occur at the same time. Replace with the latest sample
        // in that case.
        mySparseSamples.set(prevSampleIndex, newSample);
      }
      else {
        mySparseSamples.add(prevSampleIndex + 1, newSample);
      }
    }

    return newSample;
  }

  private void removeDataFromSample(long timestampNs,
                                    @NotNull Function<Energy.EnergyUsageData.Builder, Energy.EnergyUsageData.Builder> resetSample) {
    timestampNs = alignToSampleInterval(timestampNs);

    int sampleIndex = getSampleIndexFor(timestampNs);
    EnergySample originalSample = mySparseSamples.get(sampleIndex);
    if (timestampNs != originalSample.getTimestamp()) {
      // There were no valid samples at this timestamp, so just ignore.
      return;
    }

    EnergySample updatedSample = EnergySample.newBuilder()
      .setTimestamp(timestampNs)
      .setEnergyUsage(resetSample.apply(originalSample.getEnergyUsage().toBuilder()))
      .build();

    // If the sample usage amounts are all the default values, we want to remove the sample.
    // This is easiest done by removing the time value and comparing against the default EnergySample.
    EnergySample updatedSampleNoTime = updatedSample.toBuilder().setTimestamp(0).build();

    if (updatedSampleNoTime.equals(EnergySample.getDefaultInstance())) {
      mySparseSamples.remove(sampleIndex);
    }
    else {
      mySparseSamples.set(sampleIndex, updatedSample);
    }
  }

  private void handleLocationUpdateEvent(long timestampNs, @NotNull PowerProfile.LocationEvent locationEvent) {
    switch (locationEvent.myLocationType) {
      case GPS:
        if (myGpsLockonMap.containsKey(locationEvent.myEventId)) {
          myGpsLockonMap.remove(locationEvent.myEventId);
          addNewLocationSample(timestampNs, new LocationStats(LocationType.NONE, 0, mySampleIntervalNs));
        }
        else {
          // TODO(b/77588320) Since we don't have a power-on time for the GPS (and associativity information for why the GPS was powered
          // on, we can't properly associate the time the GPS was on to our samples. Therefore, as a hack, we forward amortize the
          // accumulated power cost of the GPS into the GPS event's future samples. As we do this, we will have to assume certain
          // accounting as to how samples are retrieved, as well as allowing modification of events that have occurred. To fix this,
          // we will need to implement a way to tentatively associate GPS power information to the app, and only when we get a GPS event
          // do we actually charge the power cost to the app.
          EnergySample previousSample = myLocationSmoothingMap.get(locationEvent.myEventId);
          long residualTime = calculateResidualSmoothingTime(previousSample, timestampNs);
          if (residualTime > 0) {
            // Remove stale smoothing end sample.
            removeLocationSample(previousSample.getTimestamp() + mySmoothingEndDeltaTime);
          }
          EnergySample eventSample = addNewLocationSample(
            timestampNs,
            // Use the minimum of the time since the last GPS sample or the estimated time it takes the GPS to lock.
            // This basically means that if the time since the last sample is longer than the average lock time, we assume the OS turned
            // the GPS off to conserve battery, and we only incur the cost of a lock on.
            new LocationStats(
              LocationType.GPS,
              Math.min(GPS_LOCK_DURATION_NS, timestampNs - myLastGpsOffTime) + residualTime,
              mySampleIntervalNs * LOCATION_SMOOTHING_SAMPLES));
          myLocationSmoothingMap.put(locationEvent.myEventId, eventSample);
          // Add an artificial end time for GPS energy forward smoothing.
          addNewLocationSample(timestampNs + mySmoothingEndDeltaTime,
                               new LocationStats(LocationType.NONE, 0, mySampleIntervalNs));
        }
        myLastGpsOffTime = timestampNs;
        break;
      case NETWORK:
        addNewLocationSample(
          timestampNs,
          // Similar to the GPS, we assume that the OS knows to not constantly keep the wireless network adapter scanning beyond
          // NETWORK_SCAN_DURATION_NS time.
          new LocationStats(
            LocationType.NETWORK,
            Math.min(NETWORK_SCAN_DURATION_NS, timestampNs - myLastNetworkLocationOffTime),
            mySampleIntervalNs * LOCATION_SMOOTHING_SAMPLES));
        addNewLocationSample(timestampNs + LOCATION_SMOOTHING_SAMPLES * mySampleIntervalNs,
                             new LocationStats(LocationType.NONE, 0, mySampleIntervalNs));
        myLastNetworkLocationOffTime = timestampNs;
        break;
      case PASSIVE: // fall through
      case NONE: // fall through
      default:
        addNewLocationSample(timestampNs, new LocationStats(locationEvent.myLocationType, 0, mySampleIntervalNs));
        break;
    }
  }

  /**
   * Align {@code timestampNs} into its closest sample bucket.
   */
  private long alignToSampleInterval(long timestampNs) {
    return align(timestampNs, mySampleIntervalNs);
  }

  private long calculateResidualSmoothingTime(@Nullable EnergySample previousSample, long currentTime) {
    if (previousSample == null) {
      return 0;
    }

    return Math.max(0, alignToSampleInterval(previousSample.getTimestamp() + mySmoothingEndDeltaTime) - alignToSampleInterval(currentTime));
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

    /**
     * A location request register event.
     * arg: A {@link PowerProfile.LocationEvent} indicating the event ID and the source of location fix.
     */
    LOCATION_REGISTER,

    /**
     * A location request unregister event.
     * arg: A {@link PowerProfile.LocationEvent} indicating the event ID and the source of location fix.
     */
    LOCATION_UNREGISTER,

    /**
     * A location update has occurred.
     * arg: A {@link PowerProfile.LocationEvent} indicating the event ID and the source of location fix.
     */
    LOCATION_UPDATE,
  }
}
