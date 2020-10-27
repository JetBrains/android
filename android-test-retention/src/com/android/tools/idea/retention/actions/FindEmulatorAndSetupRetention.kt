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
package com.android.tools.idea.retention.actions

import com.android.annotations.concurrency.Slow
import com.android.emulator.control.SnapshotPackage
import com.android.tools.idea.emulator.DummyStreamObserver
import com.android.tools.idea.emulator.EmulatorController
import com.android.tools.idea.emulator.RunningEmulatorCatalog
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_FILE_KEY
import com.android.tools.idea.testartifacts.instrumented.EMULATOR_SNAPSHOT_ID_KEY
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.grpc.stub.ClientCallStreamObserver
import io.grpc.stub.ClientResponseObserver
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch

/**
 * An action to load an Android Test Retention snapshot.
 */
class FindEmulatorAndSetupRetention : AnAction() {
  override fun actionPerformed(event: AnActionEvent) {
    // TODO(b/154140562): we currently don't have the emulator ID, so just use the first running emulator.
    val catalog = RunningEmulatorCatalog.getInstance()
    val emulators = catalog.emulators
    if (emulators != null) {
      val emulatorController = RunningEmulatorCatalog.getInstance().emulators.iterator().next()
      if (emulatorController.connectionState != EmulatorController.ConnectionState.CONNECTED) {
        emulatorController.connect()
      }
      val snapshotId = event.dataContext.getData(EMULATOR_SNAPSHOT_ID_KEY) ?: return
      val snapshotFile = event.dataContext.getData(EMULATOR_SNAPSHOT_FILE_KEY) ?: return
      // TODO(b/156287594): slow, need progress bar
      emulatorController.pushAndLoadSync(snapshotId, snapshotFile)
    }
  }

  override fun update(event: AnActionEvent) {
    super.update(event)
    val snapshotId = event.dataContext.getData(EMULATOR_SNAPSHOT_ID_KEY)
    val snapshotFile = event.dataContext.getData(EMULATOR_SNAPSHOT_FILE_KEY)
    event.presentation.isEnabledAndVisible = (snapshotId != null && snapshotFile != null)
  }
}

/**
 * Pushes a snapshot file into the emulator and load it.
 *
 * @param snapshotId a snapshot name which must match the snapshot in the snapshot file.
 * @param snapshotFile a file of an exported snapshot.
 *
 * @return true if succeeds.
 */
@Slow
private fun EmulatorController.pushAndLoadSync(snapshotId: String, snapshotFile: File): Boolean {
  return pushSnapshotSync(snapshotId, snapshotFile) && loadSnapshotSync(snapshotId)
}

/**
 * Loads a snapshot in the emulator.
 *
 * @param snapshotId a name of a snapshot in the emulator.
 *
 * @return true if succeeds.
 */
@Slow
private fun EmulatorController.loadSnapshotSync(snapshotId: String): Boolean {
  val doneSignal = CountDownLatch(1)
  var succeeded = true
  loadSnapshot(snapshotId, object : DummyStreamObserver<SnapshotPackage>() {
    override fun onCompleted() {
      doneSignal.countDown()
    }

    override fun onError(throwable: Throwable) {
      succeeded = false
      doneSignal.countDown()
    }
  })
  doneSignal.await()
  return succeeded
}

/**
 * Pushes a snapshot file into the emulator.
 *
 * @param snapshotId a snapshot name which must match the snapshot in the snapshot file.
 * @param snapshotFile a file of an exported snapshot.
 *
 * @return true if succeeds.
 */
@Slow
@Throws(IOException::class)
private fun EmulatorController.pushSnapshotSync(snapshotId: String, snapshotFile: File): Boolean {
  snapshotFile.inputStream().use { inputStream ->
    var succeeded = true
    val doneSignal = CountDownLatch(1)
    pushSnapshot(object : ClientResponseObserver<SnapshotPackage, SnapshotPackage> {
      override fun onCompleted() {
        doneSignal.countDown()
      }

      override fun onError(throwable: Throwable) {
        succeeded = false
        doneSignal.countDown()
      }

      override fun beforeStart(clientCallStreamObserver: ClientCallStreamObserver<SnapshotPackage>) {
        var snapshotIdSent = false
        var completionRequested = false
        val bytes = ByteArray(2 * 1024 * 1024)
        clientCallStreamObserver.setOnReadyHandler {
          // https://grpc.github.io/grpc-java/javadoc/io/grpc/stub/CallStreamObserver.html#setOnReadyHandler-java.lang.Runnable-
          // Get rid of "spurious" notifications first.
          if (!clientCallStreamObserver.isReady) {
            return@setOnReadyHandler
          }
          if (!snapshotIdSent) {
            clientCallStreamObserver.onNext(SnapshotPackage.newBuilder().setSnapshotId(snapshotId).build())
            snapshotIdSent = true;
          }
          var bytesRead = 0;
          while (clientCallStreamObserver.isReady) {
            bytesRead = inputStream.read(bytes)
            if (bytesRead <= 0) {
              break
            }
            clientCallStreamObserver.onNext(SnapshotPackage.newBuilder().setPayload(ByteString.copyFrom(bytes, 0, bytesRead)).build())
          }
          if (bytesRead < 0 && !completionRequested) {
            completionRequested = true
            clientCallStreamObserver.onCompleted()
          }
        }
      }

      override fun onNext(snapshotPackage: SnapshotPackage) {}
    })

    // Slow
    doneSignal.await()
    return succeeded
  }
}
