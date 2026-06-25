/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.avd.glassespairing

import com.android.adblib.AdbSession
import com.android.adblib.tools.aiglasses.AiGlassesPairing
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.avd.StudioLocalEmulatorDeviceHandle
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.android.utils.throwIfCancellation
import com.google.wireless.android.sdk.stats.GlassesPairingEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.NonInjectable
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class DeviceTrulyBonded {
  TRULY_BONDED,
  NOT_TRULY_BONDED,
  UNKNOWN;

  companion object {
    // We use .trim() because raw ADB shell output might contain trailing newlines.
    fun fromString(value: String?): DeviceTrulyBonded {
      val trimmed = value?.trim() ?: return UNKNOWN
      val lines = trimmed.lines().map { it.trim() }
      return entries.find { lines.contains(it.name) } ?: UNKNOWN
    }
  }
}

/**
 * Monitors and synchronizes the Bluetooth bonding state between running Handheld emulators and running AI Glasses emulators.
 *
 * It manages an adaptive background polling loop with automatic multiplier backoffs to balance responsiveness with CPU efficiency.
 */
@Service(Service.Level.PROJECT)
class GlassesPairingStateManager
@NonInjectable
internal constructor(
  private val project: Project,
  private val scope: CoroutineScope,
  internal val ioDispatcher: CoroutineDispatcher,
  internal val timeSource: TimeSource = TimeSource.Monotonic,
  internal val adbProber: AdbProber = DefaultAdbProber(project, { AiGlassesPairing(it) }),
) {
  @Suppress("unused") // used by IntelliJ component system
  constructor(project: Project, scope: CoroutineScope) : this(project, scope, Dispatchers.IO, timeSource = TimeSource.Monotonic)

  private val logger = logger<GlassesPairingStateManager>()

  private val triggerChannel = Channel<Unit>(Channel.CONFLATED)
  private val initialized = AtomicBoolean(false)
  internal val cycleDurations = mutableListOf<Long>()

  fun initialize() {
    if (!initialized.compareAndSet(false, true)) return
    logger.info("GlassesPairingStateManager initialized and starting background loop.")

    scope.launch {
      val lockService = service<GlassesPairingLockService>()
      var reconciliationJob: Job? = null
      lockService.isWizardOpen.collect { isOpen ->
        if (isOpen) {
          logger.debug("Wizard is open, suspending reconciliation.")
          reconciliationJob?.cancel()
          reconciliationJob = null
        } else {
          if (reconciliationJob != null) return@collect // Already running
          logger.debug("Wizard is closed, starting reconciliation.")
          reconciliationJob = launch {
            lockService.discoveryLock.withLock {
              var backoffMultiplier = 1L // Backoff multiplier ceiling at 16x
              while (true) {
                logger.debug("Starting reconciliation cycle.")
                val startTime = timeSource.markNow()
                var changesFound = false
                try {
                  changesFound = reconcileState()
                } catch (e: Exception) {
                  e.throwIfCancellation()
                  logger.warn("Reconciliation cycle failed", e)
                }
                val duration = startTime.elapsedNow().inWholeMilliseconds

                cycleDurations.add(duration)
                // Maintain a rolling window of last 5 iterations to compute dynamic baseline
                if (cycleDurations.size > 5) {
                  cycleDurations.removeFirst()
                }

                val averageDuration = if (cycleDurations.isNotEmpty()) cycleDurations.average().toLong() else 1_000L
                val baseline = averageDuration.coerceAtLeast(1_000L).coerceAtMost(30_000L) // Safety floor 1s, cap at 30s

                // Adaptive backoff: Reset multiplier on changes found, double it when idle
                backoffMultiplier = if (changesFound) 1L else (backoffMultiplier * 2).coerceAtMost(16L)
                val currentDelay = (baseline * backoffMultiplier).coerceAtMost(30_000L) // Sleep cap at 30s

                if (changesFound) {
                  logger.info("Reconciliation cycle completed and applied state changes in ${duration}ms.")
                }
                logger.debug("Cycle finished in ${duration}ms. Sleeping for ${currentDelay}ms (multiplier: $backoffMultiplier).")

                // Sleep for baseline to prevent busy-spin, then wait for trigger if needed
                delay(baseline)
                withTimeoutOrNull(currentDelay - baseline) { triggerChannel.receive() }
              }
            }
          }
        }
      }
    }
  }

  internal suspend fun reconcileState(): Boolean =
    withContext(ioDispatcher) {
      var anyChanges = false
      val provisioner = project.service<DeviceProvisionerService>()?.deviceProvisioner ?: return@withContext false
      val devices = provisioner.devices.value

      val emulatorDevices = devices.filterIsInstance<StudioLocalEmulatorDeviceHandle>()

      // Filter handles by type at the start
      val runningGlasses = emulatorDevices.filter { it.state.isReady && it.state.properties.deviceType == DeviceType.AI_GLASSES }
      val runningPhones = emulatorDevices.filter { it.state.isReady && it.state.properties.deviceType == DeviceType.HANDHELD }

      // Phase 1: Identify "Orphan" Glasses (Running glasses not linked to any phone tracker)
      val allPairedGlassesIds = runningPhones.flatMap { it.state.properties.pairedGlassesInfos }.map { it.id }.toSet()
      val orphanGlasses = runningGlasses.filter { it.id !in allPairedGlassesIds }

      // Phase 2: Probe running phones for active Bluetooth bonds with orphans to auto-import linkage
      if (orphanGlasses.isNotEmpty()) {
        for (glasses in orphanGlasses) {
          val glassesBondCount =
            try {
              withTimeoutOrNull(2000L) { adbProber.getPairedDeviceCount(glasses) }
            } catch (e: Exception) {
              e.throwIfCancellation()
              null
            }
          if (glassesBondCount == 0) {
            logger.debug("Orphan glasses ${glasses.id} has 0 bonds, skipping auto-import.")
            continue
          }
          val mac =
            try {
              withTimeoutOrNull(2000L) { adbProber.getBluetoothAddress(glasses) }
            } catch (e: Exception) {
              e.throwIfCancellation()
              logger.warn("Failed to fetch address for orphan glasses ${glasses.id}", e)
              continue
            } ?: continue

          for (phone in runningPhones) {
            try {
              val bondState = withTimeoutOrNull(2000L) { adbProber.checkBondState(phone, mac) } ?: DeviceTrulyBonded.UNKNOWN
              if (bondState == DeviceTrulyBonded.TRULY_BONDED) {
                logger.debug("Auto-importing linkage: phone=${phone.id}, glasses=${glasses.id}")
                phone.addPairedGlasses(glasses.id, mac)
                glasses.updatePairedPhone(phone)
                logger.info("Auto-imported linkage between phone ${phone.id} and glasses ${glasses.id} (MAC: $mac)")
                GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.RECONCILIATION_BOND_DISCOVERED)
                anyChanges = true
                return@withContext true
              }
            } catch (e: Exception) {
              e.throwIfCancellation()
              logger.warn("Auto-import linkage probe failed for ${glasses.id} on ${phone.id}", e)
            }
          }
        }
      }

      // Phase 3a: Validate existing recorded bonds on phones and prune dead entries
      for (handle in runningPhones) {
        val properties = handle.state.properties

        for (glassesInfo in properties.pairedGlassesInfos) {
          val mac = glassesInfo.mac
          val targetGlasses = emulatorDevices.find { it.id == glassesInfo.id }
          if (targetGlasses != null && targetGlasses.state.isReady) {
            val glassesBondCount =
              try {
                withTimeoutOrNull(2000L) { adbProber.getPairedDeviceCount(targetGlasses) }
              } catch (e: Exception) {
                e.throwIfCancellation()
                null
              }
            if (glassesBondCount == 0) {
              logger.info("Target glasses ${targetGlasses.id} has 0 bonds, pruning stale linkage on phone ${handle.id}.")
              try {
                handle.removePairedGlasses(glassesInfo.id)
                if (targetGlasses.state.properties.pairedPhoneId == handle.id) {
                  targetGlasses.updatePairedPhone(null)
                }
                anyChanges = true
              } catch (e: Exception) {
                e.throwIfCancellation()
                logger.warn("Failed to prune stale linkage for glasses ${glassesInfo.id} on phone ${handle.id}", e)
              }
              continue
            }
          }
          if (targetGlasses == null) {
            // Target glasses AVD was deleted or missing, prune it
            try {
              handle.removePairedGlasses(glassesInfo.id)
              logger.info("Pruned references to deleted glasses ${glassesInfo.id} from phone ${handle.id}")
              GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.ORPHANED_PAIRING_PURGED)
              anyChanges = true
            } catch (e: Exception) {
              e.throwIfCancellation()
              logger.warn("Failed to prune references to deleted glasses ${glassesInfo.id} from phone ${handle.id}", e)
            }
          } else if (mac == null) {
            if (targetGlasses.state.isReady) {
              val bondCount =
                try {
                  withTimeoutOrNull(2000L) { adbProber.getPairedDeviceCount(targetGlasses) }
                } catch (e: Exception) {
                  e.throwIfCancellation()
                  logger.warn("Failed to get paired device count", e)
                  null
                }
              when (bondCount) {
                0 -> {
                  try {
                    handle.removePairedGlasses(glassesInfo.id)
                    logger.info("Glasses ${glassesInfo.id} has no paired devices on ADB, clearing local state.")
                    anyChanges = true
                  } catch (e: Exception) {
                    e.throwIfCancellation()
                    logger.warn("Failed to remove paired glasses reference from phone ${handle.id}", e)
                  }
                }
                null -> {
                  /* Skip if we couldn't fetch bond count due to error */
                }
                else -> {
                  try {
                    val newMac = withTimeoutOrNull(2000L) { adbProber.getBluetoothAddress(targetGlasses) }
                    if (newMac != null) {
                      handle.addPairedGlasses(glassesInfo.id, newMac)
                      targetGlasses.updatePairedPhone(handle)
                      logger.info("Updated MAC address for glasses ${glassesInfo.id} to $newMac on phone ${handle.id}")
                      anyChanges = true
                    }
                  } catch (e: Exception) {
                    e.throwIfCancellation()
                    logger.warn("Failed to fetch address for glasses ${glassesInfo.id}", e)
                  }
                }
              }
            }
          } else {
            try {
              val bondState = withTimeoutOrNull(2000L) { adbProber.checkBondState(handle, mac) } ?: DeviceTrulyBonded.UNKNOWN
              if (bondState == DeviceTrulyBonded.NOT_TRULY_BONDED) {
                handle.removePairedGlasses(glassesInfo.id)
                if (targetGlasses.state.properties.pairedPhoneId == handle.id) {
                  targetGlasses.updatePairedPhone(null)
                }
                logger.info("Unpaired glasses ${glassesInfo.id} from phone ${handle.id} due to broken bond state.")
                GlassesPairingUsageTracker.log(GlassesPairingEvent.EventKind.RECONCILIATION_BOND_REMOVED)
                anyChanges = true
              }
            } catch (e: Exception) {
              e.throwIfCancellation()
              logger.warn("Check bond state failed for ${glassesInfo.id}", e)
            }
          }
        }
      }

      // Phase 3b: Prune stale references to phones on glasses
      for (handle in runningGlasses) {
        val pairedPhoneId = handle.state.properties.pairedPhoneId
        if (pairedPhoneId != null) {
          val targetPhone = emulatorDevices.find { it.id == pairedPhoneId }
          if (targetPhone != null && targetPhone.state.isTransitioning) continue
          if (targetPhone == null || targetPhone.state.properties.pairedGlassesInfos.none { it.id == handle.id }) {
            // Target phone was deleted or wiped (no longer lists this glasses), prune it
            try {
              handle.updatePairedPhone(null)
              logger.info("Pruned references to deleted/wiped phone $pairedPhoneId from glasses ${handle.id}")
              anyChanges = true
            } catch (e: Exception) {
              e.throwIfCancellation()
              logger.warn("Failed to prune references to deleted/wiped phone $pairedPhoneId from glasses ${handle.id}", e)
            }
          }
        }
      }
      anyChanges
    }

  fun triggerReconcile() {
    triggerChannel.trySend(Unit)
  }
}

internal interface AdbProber {
  suspend fun getBluetoothAddress(glasses: DeviceHandle): String?

  suspend fun checkBondState(phone: DeviceHandle, mac: String): DeviceTrulyBonded

  suspend fun getPairedDeviceCount(device: DeviceHandle): Int? = null
}

private class DefaultAdbProber(private val project: Project, private val aiGlassesPairingFactory: (AdbSession) -> AiGlassesPairing) :
  AdbProber {
  override suspend fun getBluetoothAddress(glasses: DeviceHandle): String? {
    val connectedDevice = glasses.state.connectedDevice ?: return null
    val adbSession = AdbLibService.getSession(project)
    val pairing = aiGlassesPairingFactory(adbSession)
    return with(pairing) { connectedDevice.getBluetoothAddress() }
  }

  override suspend fun checkBondState(phone: DeviceHandle, mac: String): DeviceTrulyBonded {
    val connectedDevice = phone.state.connectedDevice ?: return DeviceTrulyBonded.UNKNOWN
    val adbSession = AdbLibService.getSession(project)
    val pairing = aiGlassesPairingFactory(adbSession)
    val result = with(pairing) { connectedDevice.checkBondState(mac) }
    return DeviceTrulyBonded.fromString(result)
  }

  override suspend fun getPairedDeviceCount(device: DeviceHandle): Int? {
    val connectedDevice = device.state.connectedDevice ?: return null
    val adbSession = AdbLibService.getSession(project)
    val pairing = aiGlassesPairingFactory(adbSession)
    return with(pairing) { connectedDevice.getPairedBluetoothDeviceCount() }
  }
}
