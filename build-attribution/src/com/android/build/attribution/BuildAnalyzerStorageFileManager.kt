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
package com.android.build.attribution

import com.android.annotations.concurrency.Slow
import com.android.build.attribution.proto.converters.BuildResultsProtoMessageConverter
import com.android.utils.FileUtils
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class BuildAnalyzerStorageFileManager(
  private val dataFolder: File
) {
  private val log: Logger get() = Logger.getInstance("Build Analyzer")

  /**
   * Converts build analysis results into a protobuf-generated data structure, that is then stored in byte form in a file. If there is an
   * error during file storage, then an IOException is logged and False is returned. If the folder containing build results cannot be resolved
   * then False is returned and file storage is not attempted. If the process succeeds then True is returned.
   *
   * @return Boolean
   */
  @Slow
  fun storeBuildResultsInFile(buildResults: BuildAnalysisResults): Boolean =
    try {
      FileUtils.mkdirs(dataFolder)
      val buildResultFile = getFileFromBuildID(buildResults.getBuildSessionID())
      buildResultFile.createNewFile()
      BuildResultsProtoMessageConverter.convertBuildAnalysisResultsFromObjectToBytes(
        buildResults,
        buildResults.getPluginMap(),
        buildResults.getTaskMap()
      ).writeDelimitedTo(FileOutputStream(buildResultFile))
      true
    }
    catch (e: IOException) {
      log.error("Error when attempting to store build results with ID ${buildResults.getBuildSessionID()} in file.")
      false
    }

  /**
   * Does not take in input, returns the size of the build-analyzer-history-data folder in bytes.
   * If it fails to locate the folder then 0 is returned.
   * @return Bytes
   */
  @Slow
  fun getCurrentBuildHistoryDataSize() : Long {
    FileUtils.mkdirs(dataFolder)
    return FileUtils.getAllFiles(dataFolder).sumOf { it.length() }
  }

  /**
   * Reads in build results with the build session ID specified from bytes and converts them to a proto-structure,
   * and then converts them again to a BuildAnalysisResults object before returning them to the user.
   * If there is an issue resolving the file then an IOException is thrown. If there is an issue converting the proto-structure
   * to the BuildAnalysisResults object then the BuildResultsProtoMessageConverter class is responsible for handling the exception.
   *
   * @return BuildAnalysisResults
   * @exception IOException
   */
  @Slow
  fun getHistoricBuildResultByID(buildSessionID: String): BuildAnalysisResults {
    try {
      val stream = FileInputStream(getFileFromBuildID(buildSessionID))
      val message = BuildAnalysisResultsMessage.parseDelimitedFrom(stream)
      return BuildResultsProtoMessageConverter
        .convertBuildAnalysisResultsFromBytesToObject(message)
    }
    catch (e: Exception) {
      throw IOException("Error reading in build results file with ID: $buildSessionID", e)
    }
  }

  @Slow
  fun deleteHistoricBuildResultByID(buildID: String) {
    getFileFromBuildID(buildID).delete()
  }

  @VisibleForTesting
  fun getFileFromBuildID(buildID: String): File {
    return dataFolder.resolve(buildID)
  }
}