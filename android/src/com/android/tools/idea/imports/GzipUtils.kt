/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.imports

import org.apache.commons.compress.utils.IOUtils
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 *  Returns a decompressed byte array of the given content.
 */
fun ungzip(data: ByteArray): ByteArray {
  val byteArrayInputStream = data.inputStream()
  return GZIPInputStream(byteArrayInputStream).use {
    IOUtils.toByteArray(it)
  }
}

/**
 *  Returns a compressed byte array of the given content.
 */
fun gzip(content: ByteArray): ByteArray {
  val byteArrayOutputStream = ByteArrayOutputStream()
  GZIPOutputStream(byteArrayOutputStream).use {
    it.write(content)
  }

  return byteArrayOutputStream.toByteArray()
}