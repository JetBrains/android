/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat

import com.android.ddmlib.Client
import com.android.ddmlib.FileListingService
import com.android.ddmlib.IDevice
import com.android.ddmlib.IDevice.DeviceState.DISCONNECTED
import com.android.ddmlib.IShellOutputReceiver
import com.android.ddmlib.InstallReceiver
import com.android.ddmlib.RawImage
import com.android.ddmlib.ScreenRecorderOptions
import com.android.ddmlib.SyncService
import com.android.ddmlib.log.LogReceiver
import com.android.sdklib.AndroidVersion
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

private const val TODO_MESSAGE = "Please report this issue at https://issuetracker.google.com/issues/new?component=192708&template=840533"

/**
 * A minimal implementation of [IDevice] that can be persisted to disk, so it can be used to restore the state of a Logcat panel.
 *
 * It is "minimal" in a sense that it only implements the methods that are triggered by [com.android.tools.idea.ddms.DevicePanel]
 * handling of the Device combo box. All other methods will throw if used.
 */
internal class SavedDevice(
  private val serialNumber: String,
  private val name: String,
  private val isEmulator: Boolean,
  private val avdName: String?,
  private val properties: Map<String, String?>,
) : IDevice {
  override fun getSerialNumber(): String = serialNumber

  override fun isEmulator(): Boolean = isEmulator

  override fun getState(): IDevice.DeviceState = DISCONNECTED

  override fun isOnline(): Boolean = false

  override fun isOffline(): Boolean = true

  override fun getClient(applicationName: String?): Client? = null

  override fun getClients(): Array<Client> = emptyArray()

  override fun getName(): String = name

  override fun getAvdName(): String? = avdName

  override fun getSystemProperty(name: String): ListenableFuture<String?> = Futures.immediateFuture(properties[name])

  // PLEASE KEEP UNIMPLEMENTED METHODS ONLY BELLOW THIS COMMENT

  override fun executeShellCommand(command: String?, receiver: IShellOutputReceiver?, maxTimeToOutputResponse: Int) {
    TODO(TODO_MESSAGE)
  }

  override fun executeShellCommand(command: String?, receiver: IShellOutputReceiver?) {
    TODO(TODO_MESSAGE)
  }

  override fun executeShellCommand(
    command: String?,
    receiver: IShellOutputReceiver?,
    maxTimeToOutputResponse: Long,
    maxTimeUnits: TimeUnit?) {
    TODO(TODO_MESSAGE)
  }

  override fun executeShellCommand(
    command: String?,
    receiver: IShellOutputReceiver?,
    maxTimeout: Long,
    maxTimeToOutputResponse: Long,
    maxTimeUnits: TimeUnit?) {
    TODO(TODO_MESSAGE)
  }

  override fun getAvdPath(): String? {
    TODO(TODO_MESSAGE)
  }

  override fun getProperties(): MutableMap<String, String> {
    TODO(TODO_MESSAGE)
  }

  override fun getPropertyCount(): Int {
    TODO(TODO_MESSAGE)
  }

  override fun getProperty(name: String): String? {
    TODO(TODO_MESSAGE)
  }

  override fun arePropertiesSet(): Boolean {
    TODO(TODO_MESSAGE)
  }

  override fun getPropertySync(name: String?): String {
    TODO(TODO_MESSAGE)
  }

  override fun getPropertyCacheOrSync(name: String?): String {
    TODO(TODO_MESSAGE)
  }

  override fun supportsFeature(feature: IDevice.Feature): Boolean {
    TODO(TODO_MESSAGE)
  }

  override fun supportsFeature(feature: IDevice.HardwareFeature): Boolean {
    TODO(TODO_MESSAGE)
  }

  override fun getMountPoint(name: String): String? {
    TODO(TODO_MESSAGE)
  }

  override fun isBootLoader(): Boolean {
    TODO(TODO_MESSAGE)
  }

  override fun hasClients(): Boolean {
    TODO(TODO_MESSAGE)
  }

  override fun getSyncService(): SyncService? {
    TODO(TODO_MESSAGE)
  }

  override fun getFileListingService(): FileListingService {
    TODO(TODO_MESSAGE)
  }

  override fun getScreenshot(): RawImage {
    TODO(TODO_MESSAGE)
  }

  override fun getScreenshot(timeout: Long, unit: TimeUnit?): RawImage {
    TODO(TODO_MESSAGE)
  }

  override fun startScreenRecorder(remoteFilePath: String, options: ScreenRecorderOptions, receiver: IShellOutputReceiver) {
    TODO(TODO_MESSAGE)
  }

  override fun runEventLogService(receiver: LogReceiver?) {
    TODO(TODO_MESSAGE)
  }

  override fun runLogService(logname: String?, receiver: LogReceiver?) {
    TODO(TODO_MESSAGE)
  }

  override fun createForward(localPort: Int, remotePort: Int) {
    TODO(TODO_MESSAGE)
  }

  override fun createForward(localPort: Int, remoteSocketName: String?, namespace: IDevice.DeviceUnixSocketNamespace?) {
    TODO(TODO_MESSAGE)
  }

  override fun getClientName(pid: Int): String {
    TODO(TODO_MESSAGE)
  }

  override fun pushFile(local: String, remote: String) {
    TODO(TODO_MESSAGE)
  }

  override fun pullFile(remote: String?, local: String?) {
    TODO(TODO_MESSAGE)
  }

  override fun installPackage(packageFilePath: String?, reinstall: Boolean, vararg extraArgs: String?) {
    TODO(TODO_MESSAGE)
  }

  override fun installPackage(packageFilePath: String?, reinstall: Boolean, receiver: InstallReceiver?, vararg extraArgs: String?) {
    TODO(TODO_MESSAGE)
  }

  override fun installPackage(
    packageFilePath: String?,
    reinstall: Boolean,
    receiver: InstallReceiver?,
    maxTimeout: Long,
    maxTimeToOutputResponse: Long,
    maxTimeUnits: TimeUnit?,
    vararg extraArgs: String?) {
    TODO(TODO_MESSAGE)
  }

  override fun installPackages(
    apks: MutableList<File>,
    reinstall: Boolean,
    installOptions: MutableList<String>,
    timeout: Long,
    timeoutUnit: TimeUnit) {
    TODO(TODO_MESSAGE)
  }

  override fun syncPackageToDevice(localFilePath: String?): String {
    TODO(TODO_MESSAGE)
  }

  override fun installRemotePackage(remoteFilePath: String?, reinstall: Boolean, vararg extraArgs: String?) {
    TODO(TODO_MESSAGE)
  }

  override fun installRemotePackage(remoteFilePath: String?, reinstall: Boolean, receiver: InstallReceiver?, vararg extraArgs: String?) {
    TODO(TODO_MESSAGE)
  }

  override fun installRemotePackage(
    remoteFilePath: String?,
    reinstall: Boolean,
    receiver: InstallReceiver?,
    maxTimeout: Long,
    maxTimeToOutputResponse: Long,
    maxTimeUnits: TimeUnit?,
    vararg extraArgs: String?) {
    TODO(TODO_MESSAGE)
  }

  override fun removeRemotePackage(remoteFilePath: String?) {
    TODO(TODO_MESSAGE)
  }

  override fun uninstallPackage(packageName: String?): String {
    TODO(TODO_MESSAGE)
  }

  override fun uninstallApp(applicationID: String?, vararg extraArgs: String?): String {
    TODO(TODO_MESSAGE)
  }

  override fun reboot(into: String?) {
    TODO(TODO_MESSAGE)
  }

  override fun root(): Boolean {
    TODO(TODO_MESSAGE)
  }

  override fun isRoot(): Boolean {
    TODO(TODO_MESSAGE)
  }

  override fun getBatteryLevel(): Int {
    TODO(TODO_MESSAGE)
  }

  override fun getBatteryLevel(freshnessMs: Long): Int {
    TODO(TODO_MESSAGE)
  }

  override fun getBattery(): Future<Int> {
    TODO(TODO_MESSAGE)
  }

  override fun getBattery(freshnessTime: Long, timeUnit: TimeUnit): Future<Int> {
    TODO(TODO_MESSAGE)
  }

  override fun getAbis(): MutableList<String> {
    TODO(TODO_MESSAGE)
  }

  override fun getDensity(): Int {
    TODO(TODO_MESSAGE)
  }

  override fun getLanguage(): String? {
    TODO(TODO_MESSAGE)
  }

  override fun getRegion(): String? {
    TODO(TODO_MESSAGE)
  }

  override fun getVersion(): AndroidVersion {
    TODO(TODO_MESSAGE)
  }
}