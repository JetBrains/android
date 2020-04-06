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
import com.android.tools.mlkit.MetadataExtractor
import com.android.tools.mlkit.ModelInfo
import com.google.common.hash.Hashing
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.MlModelBindingEvent
import com.google.wireless.android.sdk.stats.MlModelBindingEvent.EventType
import com.google.wireless.android.sdk.stats.MlModelBindingEvent.ModelMetadata
import com.intellij.openapi.vfs.VirtualFile
import java.nio.ByteBuffer

fun logEvent(eventType: EventType, modelFile: VirtualFile) {
  UsageTracker.log(
    AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.ML_MODEL_BINDING)
      .setMlModelBindingEvent(
        MlModelBindingEvent.newBuilder()
          .setEventType(eventType)
          .addModelMetadatas(getModelMetadata(modelFile)))
  )
}

private fun getModelMetadata(modelFile: VirtualFile): ModelMetadata {
  val metadataBuilder = ModelMetadata.newBuilder().setFileSize(modelFile.length)
  try {
    val bytes = modelFile.contentsToByteArray()
    // TODO(b/153499565): Sync file hash calculation with MLKit swappable model feature.
    metadataBuilder.fileHash = Hashing.murmur3_128().hashBytes(bytes).toString()
    val modelInfo = ModelInfo.buildFrom(MetadataExtractor(ByteBuffer.wrap(bytes)))
    metadataBuilder.isValidModel = true
    metadataBuilder.hasMetadata = modelInfo.isMetadataExisted
  }
  catch (e: Exception) {
    metadataBuilder.isValidModel = false
  }

  return metadataBuilder.build()
}
