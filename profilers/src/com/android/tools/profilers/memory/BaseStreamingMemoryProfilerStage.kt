/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.memory

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.model.AspectModel
import com.android.tools.adtui.model.DataSeries
import com.android.tools.adtui.model.DurationData
import com.android.tools.adtui.model.DurationDataModel
import com.android.tools.adtui.model.Interpolatable
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.RangeSelectionListener
import com.android.tools.adtui.model.RangeSelectionModel
import com.android.tools.adtui.model.RangedSeries
import com.android.tools.adtui.model.SeriesData
import com.android.tools.adtui.model.axis.ClampedAxisComponentModel
import com.android.tools.adtui.model.formatter.BaseAxisFormatter
import com.android.tools.adtui.model.formatter.MemoryAxisFormatter
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter
import com.android.tools.adtui.model.updater.Updatable
import com.android.tools.idea.codenavigation.CodeLocation
import com.android.tools.idea.codenavigation.CodeNavigator
import com.android.tools.idea.transport.poller.TransportEventListener
import com.android.tools.profiler.proto.Commands
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory.MemoryAllocSamplingData
import com.android.tools.profiler.proto.Transport
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilerMode
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.UnifiedEventDataSeries
import com.android.tools.profilers.analytics.FeatureTracker
import com.android.tools.profilers.event.EventMonitor
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.Companion.getModeFromFrequency
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.FULL
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.diagnostic.Logger
import com.android.tools.idea.io.grpc.StatusRuntimeException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

private fun Long.nanosToMicros() = TimeUnit.NANOSECONDS.toMicros(this)
private fun Long.microsToNanos() = TimeUnit.MICROSECONDS.toNanos(this)
private typealias DataSeriesConstructor<T> = (ProfilerClient, Common.Session, FeatureTracker, BaseMemoryProfilerStage) -> DataSeries<T>

/**
 * This class implements common functionalities of a memory stage with a timeline
 */
abstract class BaseStreamingMemoryProfilerStage(profilers: StudioProfilers,
                                                captureObjectLoader: CaptureObjectLoader = CaptureObjectLoader())
      : BaseMemoryProfilerStage(profilers, captureObjectLoader),
        CodeNavigator.Listener {
  protected val logger get() = Logger.getInstance(this.javaClass)
  protected val sessionData = profilers.session
  var isTrackingAllocations = false
    protected set
  val aspect = AspectModel<MemoryProfilerAspect>()
  val client = profilers.client.memoryClient
  val gcStatsModel = makeModel(makeGcSeries())

  val allocationSamplingRateDataSeries = AllocationSamplingRateDataSeries(profilers.client, sessionData, true)
  val allocationSamplingRateDurations = makeModel(allocationSamplingRateDataSeries)

  val detailedMemoryUsage = DetailedMemoryUsage(profilers, this)
  val memoryAxis = ClampedAxisComponentModel.Builder(detailedMemoryUsage.memoryRange, MEMORY_AXIS_FORMATTER).build()
  val objectsAxis = ClampedAxisComponentModel.Builder(detailedMemoryUsage.objectsRange, OBJECT_COUNT_AXIS_FORMATTER).build()
  val legends = MemoryStageLegends(this, timeline.dataRange, false)
  val tooltipLegends = MemoryStageLegends(this, timeline.tooltipRange, true)
  val eventMonitor = EventMonitor(profilers)

  private val allocationSamplingRateUpdatable = object: Updatable {
    override fun update(elapsedNs: Long) {
      if (isLiveAllocationTrackingReady) {
        getLiveAllocationSamplingModeFromData()?.let { liveAllocationSamplingMode = it }
      }
    }
  }

  private val captureElapsedTimeUpdatable = object: Updatable {
    override fun update(elapsedNs: Long) {
      if (isTrackingAllocations) {
        captureSelection.aspect.changed(CaptureSelectionAspect.CURRENT_CAPTURE_ELAPSED_TIME)
      }
    }
  }

  val rangeSelectionModel = RangeSelectionModel(timeline.selectionRange, timeline.viewRange).apply {
    addListener(object : RangeSelectionListener {
      override fun selectionCreated() {
        selectCaptureFromSelectionRange()
        profilers.ideServices.featureTracker.trackSelectRange()
        profilers.ideServices.temporaryProfilerPreferences.setBoolean(HAS_USED_MEMORY_CAPTURE, true)
      }

      override fun selectionCleared() = selectCaptureFromSelectionRange()
    })
  }

  var liveAllocationSamplingMode = LiveAllocationSamplingMode.NONE
    @VisibleForTesting
    set(mode) {
      if (mode != field) {
        field = mode;
        aspect.changed(MemoryProfilerAspect.LIVE_ALLOCATION_SAMPLING_MODE);
      }
    }

  abstract val captureSeries: List<DurationDataModel<CaptureDurationData<out CaptureObject>>>

  private val updatables
    get() =
      listOf(detailedMemoryUsage, memoryAxis, objectsAxis, gcStatsModel, allocationSamplingRateDurations,
             allocationSamplingRateUpdatable, captureElapsedTimeUpdatable) +
      captureSeries

  val isLiveAllocationTrackingReady get() = MemoryProfiler.isUsingLiveAllocation(studioProfilers, sessionData)
  val isLiveAllocationTrackingSupported
    get() = with(getDeviceForSelectedSession()) { this != null && featureLevel >= AndroidVersion.VersionCodes.O }

  init {
    gcStatsModel.apply {
      setAttachedSeries(detailedMemoryUsage.objectsSeries, Interpolatable.SegmentInterpolator)
      setAttachPredicate { data ->
        // Only attach to the object series if live allocation is disabled or the gc event happens within full-tracking mode.
        !isLiveAllocationTrackingReady ||
        MemoryProfiler.hasOnlyFullAllocationTrackingWithinRegion(profilers, sessionData, data.x, data.x)
      }
    }
    allocationSamplingRateDurations.apply {
      setAttachedSeries(detailedMemoryUsage.objectsSeries, Interpolatable.SegmentInterpolator)
      setAttachPredicate { data ->
        // The DurationData should attach to the Objects series at both the start and end of the FULL tracking mode region.
        (data.value.previousRate != null && data.value.previousRate!!.samplingNumInterval == FULL.value) ||
        data.value.currentRate.samplingNumInterval == FULL.value
      }
      setRenderSeriesPredicate { data, series ->
        // Only show the object series if live allocation is not enabled or if the current sampling rate is FULL.
        series.name != detailedMemoryUsage.objectsSeries.name ||
        (!isLiveAllocationTrackingReady || data.value.currentRate.samplingNumInterval == FULL.value)
      }
    }

    allocationSamplingRateUpdatable.update(0)
  }

  override fun enter() {
    loader.start()
    eventMonitor.enter()
    updatables.forEach(studioProfilers.updater::register)
    studioProfilers.ideServices.codeNavigator.addListener(this)
    studioProfilers.ideServices.featureTracker.trackEnterStage(stageType)
  }

  override fun exit() {
    eventMonitor.exit()
    updatables.forEach(studioProfilers.updater::unregister)
    loader.stop()
    studioProfilers.ideServices.codeNavigator.removeListener(this)
    rangeSelectionModel.clearListeners()
  }

  /**
   * Trigger a change to the sampling mode that should be used for live allocation tracking.
   */
  fun requestLiveAllocationSamplingModeUpdate(mode: LiveAllocationSamplingMode) {
    try {
      val samplingRate = MemoryAllocSamplingData.newBuilder().setSamplingNumInterval(mode.value).build()
      val response = studioProfilers.client.transportClient.execute(
        Transport.ExecuteRequest.newBuilder().setCommand(Commands.Command.newBuilder()
                                                           .setStreamId(sessionData.streamId)
                                                           .setPid(sessionData.pid)
                                                           .setType(Commands.Command.CommandType.MEMORY_ALLOC_SAMPLING)
                                                           .setMemoryAllocSampling(samplingRate))
          .build())
    }
    catch (e: StatusRuntimeException) {
      logger.debug(e)
    }
  }

  fun forceGarbageCollection() {
    val response = studioProfilers.client.transportClient.execute(
      Transport.ExecuteRequest.newBuilder()
        .setCommand(Commands.Command.newBuilder()
                      .setStreamId(sessionData.streamId)
                      .setPid(sessionData.pid)
                      .setType(Commands.Command.CommandType.GC))
        .build())
  }

  /**
   * Toggle a behavior where if there is currently no CaptureObject selected, the model will attempt to select the next CaptureObject
   * that has been created.
   *
   * @param loadJoiner if specified, the joiner executor will be passed down to [CaptureObjectLoader.loadCapture] so that
   * the load operation of the CaptureObject will be joined and the CURRENT_LOAD_CAPTURE aspect would
   * be fired via the desired executor.
   */
  fun enableSelectLatestCapture(enable: Boolean, loadJoiner: Executor?) {
    if (enable)
      timeline.dataRange.addDependency(this).onChange(Range.Aspect.RANGE) {
        queryAndSelectCaptureObject(loadJoiner ?: MoreExecutors.directExecutor())
      }
    // Removing the aspect observers on Ranges.
    else
      timeline.dataRange.removeDependencies(this)
  }

  /**
   * Returns the capture object whose range overlaps with a given range. If multiple captures overlap with it,
   * the first object found is returned.
   */
  open fun getIntersectingCaptureDuration(range: Range): CaptureDurationData<out CaptureObject?>? {
    var durationData: CaptureDurationData<out CaptureObject?>? = null
    var overlap = 0.0 // Weight value to determine which capture is "more" selected.
    captureSeries.forEach {
      it.series.getSeriesForRange(range).forEach { data ->
        val duration = data.value.durationUs
        if (duration != Long.MAX_VALUE || data.value.selectableWhenMaxDuration) {
          val dataMax = if (duration == Long.MAX_VALUE) duration else data.x + duration
          val intersectionLen = range.getIntersectionLength(data.x.toDouble(), dataMax.toDouble())
          // We need both an intersection check and length requirement because the intersection might be a point.
          if (range.intersectsWith(data.x.toDouble(), dataMax.toDouble()) && intersectionLen >= overlap) {
            durationData = data.value
            overlap = intersectionLen
          }
        }
      }
    }
    return durationData
  }


  override fun onNavigated(location: CodeLocation) {
    profilerMode = ProfilerMode.NORMAL
  }

  /**
   * Select the capture corresponding to the selected range in the timeline.
   * This is called when the timeline selection changes.
   */
  protected abstract fun selectCaptureFromSelectionRange()

  /**
   * Find a pending allocation or heap dump capture matching {@code myPendingCaptureStartTime} if no capture is currently selected.
   * Selection range will also be updated to match if the capture isn't ongoing.
   */
  private fun queryAndSelectCaptureObject(executor: Executor) {
    val dataRange = timeline.dataRange
    if (pendingCaptureStartTime != INVALID_START_TIME) {
      val pendingCaptureStartTimeUs = pendingCaptureStartTime.nanosToMicros()
      val captureToSelect = captureSeries
        .flatMap { it.series.getSeriesForRange(dataRange) }
        .findLast { it.x == pendingCaptureStartTimeUs }
      if (captureToSelect != null &&
          (captureToSelect.value.durationUs != Long.MAX_VALUE || captureToSelect.value.selectableWhenMaxDuration)) {
        onCaptureToSelect(captureToSelect, executor)
      }
    }
  }

  /**
   * Perform selecting the given capture.
   * This is called when selecting the latest capture is enabled.
   */
  protected abstract fun onCaptureToSelect(data: SeriesData<CaptureDurationData<out CaptureObject>>, loadJoiner: Executor)

  protected fun <T : DurationData> makeModel(series: DataSeries<T>) = DurationDataModel(RangedSeries(timeline.viewRange, series))

  protected inline fun <T : DurationData> makeModel(make: DataSeriesConstructor<T>) =
    makeModel(applyDataSeriesConstructor(make))

  protected inline fun <T : DurationData> applyDataSeriesConstructor(f: DataSeriesConstructor<T>) =
    f(studioProfilers.client, sessionData, studioProfilers.ideServices.featureTracker, this)

  protected fun getDeviceForSelectedSession() = studioProfilers.getStream(studioProfilers.session.streamId).let { stream ->
    if (stream.type === Common.Stream.Type.DEVICE) stream.device
    else null
  }

  private fun makeGcSeries() =
    UnifiedEventDataSeries(studioProfilers.client.transportClient,
                           sessionData.streamId,
                           sessionData.pid,
                           Common.Event.Kind.MEMORY_GC,
                           UnifiedEventDataSeries.DEFAULT_GROUP_ID) { events ->
      events.map { SeriesData(it.timestamp.nanosToMicros(), GcDurationData(it.memoryGc.duration.nanosToMicros())) }
    }

  // This method is factored out just for testing the allocation sampling mode after the stage has exited,
  // because `exit()` unregisters all updatables, leaving the field `liveAllocationSamplingMode` stale.
  @VisibleForTesting
  fun getLiveAllocationSamplingModeFromData(): LiveAllocationSamplingMode? {
    // Find the last sampling info and see if it is different from the current, if so,
    val dataRangeMaxUs = timeline.dataRange.max
    val data = allocationSamplingRateDataSeries.getDataForRange(Range(dataRangeMaxUs, dataRangeMaxUs))
    // If no data available, keep the current settings.
    return data.lastOrNull()?.let { getModeFromFrequency(it.value.currentRate.samplingNumInterval) }
  }

  companion object {
    protected const val HAS_USED_MEMORY_CAPTURE = "memory.used.capture"
    val MEMORY_AXIS_FORMATTER: BaseAxisFormatter = MemoryAxisFormatter(1, 5, 5)
    val OBJECT_COUNT_AXIS_FORMATTER: BaseAxisFormatter = SingleUnitAxisFormatter(1, 5, 5, "")
  }

  enum class LiveAllocationSamplingMode(val value: Int, val displayName: String) {
    NONE(0, "None"),        // 0 is a special value for disabling tracking.
    SAMPLED(10, "Sampled"), // Sample every 10 allocations
    FULL(1, "Full");        // Sample every allocation

    companion object {
      private val SamplingRateMap = values().associateBy { it.value }
      private val NameMap = values().associateBy { it.displayName }

      @JvmStatic
      fun getModeFromFrequency(frequency: Int): LiveAllocationSamplingMode = SamplingRateMap[frequency] ?: SAMPLED

      @JvmStatic
      fun getModeFromDisplayName(displayName: String): LiveAllocationSamplingMode =
        NameMap[displayName] ?: throw IllegalArgumentException("Unrecognized mode display name \"$displayName\"")
    }
  }
}
