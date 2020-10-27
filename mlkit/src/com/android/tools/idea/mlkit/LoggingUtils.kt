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

@file:JvmName("LoggingUtils")

package com.android.tools.idea.mlkit

import com.android.tools.analytics.UsageTracker
import com.android.tools.mlkit.MlConstants
import com.android.tools.mlkit.ModelInfo
import com.google.common.hash.Hashing
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.MlModelBindingEvent
import com.google.wireless.android.sdk.stats.MlModelBindingEvent.EventType
import com.google.wireless.android.sdk.stats.MlModelBindingEvent.ModelMetadata
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.nio.ByteBuffer

private val MODEL_METADATA_KEY = Key.create<ModelMetadata>("model_metadata")

fun logEvent(eventType: EventType, modelInfo: ModelInfo) {
  UsageTracker.log(
    AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.ML_MODEL_BINDING)
      .setMlModelBindingEvent(
        MlModelBindingEvent.newBuilder()
          .setEventType(eventType)
          .addModelMetadatas(
            ModelMetadata.newBuilder()
              .setIsValidModel(true)
              .setHasMetadata(modelInfo.isMetadataExisted)
              .setFileSize(modelInfo.modelSize)
              .setFileHash(modelInfo.modelHash))))
}

fun logEvent(eventType: EventType, modelFile: VirtualFile) {
  ApplicationManager.getApplication().executeOnPooledThread(Runnable {
    UsageTracker.log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.ML_MODEL_BINDING)
        .setMlModelBindingEvent(
          MlModelBindingEvent.newBuilder()
            .setEventType(eventType)
            .addModelMetadatas(getModelMetadata(modelFile))))
  })
}

private fun getModelMetadata(modelFile: VirtualFile): ModelMetadata {
  val modelMetadata = modelFile.getUserData(MODEL_METADATA_KEY)
  if (modelMetadata != null) {
    return modelMetadata
  }

  val metadataBuilder = ModelMetadata.newBuilder().setFileSize(modelFile.length)
  if (modelFile.length <= MlConstants.MAX_SUPPORTED_MODEL_FILE_SIZE_IN_BYTES) {
    try {
      val bytes = VfsUtilCore.virtualToIoFile(modelFile).readBytes()
      metadataBuilder.fileHash = Hashing.sha256().hashBytes(bytes).toString()
      val modelInfo = ModelInfo.buildFrom(ByteBuffer.wrap(bytes))
      metadataBuilder.isValidModel = true
      metadataBuilder.hasMetadata = modelInfo.isMetadataExisted
    }
    catch (e: Exception) {
      metadataBuilder.isValidModel = false
    }
  }

  val metadata = metadataBuilder.build()
  modelFile.putUserData(MODEL_METADATA_KEY, metadata)
  return metadata
}
