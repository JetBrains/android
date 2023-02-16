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
@file:Suppress(
  "BlockingMethodInNonBlockingContext"
) // A dispatcher is passed in as a parameter, but it's not explicitly Kotlin's IO one.

package com.android.tools.idea.sqlite.cli

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.sqlite.cli.SqliteQueries.selectTableContents
import com.android.tools.idea.sqlite.cli.SqliteQueries.selectTableNames
import com.android.tools.idea.sqlite.cli.SqliteQueries.selectViewNames
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.text.Strings
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.io.Writer
import java.nio.file.Path
import kotlin.text.Charsets.UTF_8
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

private const val sqliteCliOutputArgPrefix = ".output"

/** Runs commands against the `sqlite3` CLI tool */
interface SqliteCliClient {
  suspend fun runSqliteCliCommand(args: List<SqliteCliArg>): SqliteCliResponse
}

data class SqliteCliArg(val rawArg: String)

/**
 * Note in some cases [exitCode] can be `0` despite an error - inspecting [errOutput] can be used to
 * detect such scenarios.
 */
data class SqliteCliResponse(val exitCode: Int, val stdOutput: String, val errOutput: String)

class SqliteCliArgs private constructor() {
  companion object {
    fun builder(): Builder = Builder()
  }

  class Builder {
    private val args = mutableListOf<SqliteCliArg>()

    fun database(path: Path) = apply { args.add(SqliteCliArg(".open '${path.toAbsolutePath()}'")) }
    fun output(path: Path) = apply {
      args.add(SqliteCliArg("$sqliteCliOutputArgPrefix ${path.toAbsolutePath()}"))
    }
    fun modeCsv() = apply { args.add(SqliteCliArg(".mode csv")) }
    fun dump() = apply { args.add(SqliteCliArg(".dump")) }
    fun dumpTable(tableName: String) = apply { args.add(SqliteCliArg(".dump '$tableName'")) }
    fun headersOn() = apply { args.add(SqliteCliArg(".headers on")) }
    fun separator(separator: Char) = apply { args.add(SqliteCliArg(".separator '$separator'")) }
    fun queryTableContents(tableName: String) = apply {
      args.add(SqliteCliArg("${selectTableContents(tableName)};"))
    }
    fun queryTableList() = apply { args.add(SqliteCliArg("$selectTableNames;")) }
    fun queryViewList() = apply { args.add(SqliteCliArg("$selectViewNames;")) }
    fun raw(rawArg: String) = apply { args.add(SqliteCliArg(rawArg)) }
    /**
     * Moves data from the WAL (write-ahead log) to the main DB file.
     *
     * [SQLite documentation](https://www.sqlite.org/pragma.html#pragma_wal_checkpoint)
     */
    fun walCheckpointTruncate() = apply {
      args.add(SqliteCliArg("PRAGMA wal_checkpoint(TRUNCATE);"))
    }
    private fun quit() = apply {
      args.add(SqliteCliArg(".quit"))
    } // exits the sqlite3 interactive mode

    fun build() = this.quit().args.toList() // appends the ".quit" command as the last argument
  }
}

object SqliteQueries {
  const val selectTableNames =
    "select name from sqlite_master where type = 'table' AND name not like 'sqlite_%'"
  const val selectViewNames =
    "select name from sqlite_master where type = 'view' AND name not like 'sqlite_%'"
  fun selectTableContents(tableName: String) = "select * from '$tableName'"
}

class SqliteCliClientImpl(private val sqlite3: Path, private val dispatcher: CoroutineDispatcher) :
  SqliteCliClient {
  private val logger = logger<SqliteCliClientImpl>()

  @WorkerThread
  override suspend fun runSqliteCliCommand(args: List<SqliteCliArg>): SqliteCliResponse =
    withContext(dispatcher) {
      val sqlCliPath = sqlite3.toAbsolutePath()
      val stringArgs = args.map { it.rawArg }
      logger.info(
        "Executing external command $sqlCliPath with arguments ${stringArgs.toString().ellipsize(500)}"
      )

      // The sqlite3 .output parameter proved buggy on Windows with non-ascii characters, so we use
      // stream redirection instead.
      // If the parameter is not present, we use a StringWriter.
      // Note that the .clone command does not have the same issue as .output, so we don't need to
      // do anything special in the .clone case.
      val (outputArgs, inputLines) =
        stringArgs.partition { it.startsWith("$sqliteCliOutputArgPrefix ") }
      val outputArg = outputArgs.firstOrNull()
      val outputPath = outputArg?.removePrefix("$sqliteCliOutputArgPrefix ")
      val outputFile = outputPath?.let { File(it) }
      val outputWriter =
        outputFile?.let { BufferedWriter(OutputStreamWriter(FileOutputStream(it, false), UTF_8)) }
          ?: StringWriter()

      val errWriter = StringWriter()

      outputWriter.use {
        val exitCode =
          ProcessExecutor.exec(
            sqlCliPath.toString(),
            inputLines,
            outputWriter,
            errWriter,
            dispatcher
          )
        val stdOutput =
          if (outputWriter is StringWriter) outputWriter.toString()
          else "" // in the "else" case we assume a file output
        val errOutput = errWriter.toString()
        SqliteCliResponse(exitCode, stdOutput, errOutput).also {
          logger.info(
            "Successfully executed external command $sqlCliPath with arguments ${stringArgs.toString().ellipsize(500)}"
          )
        }
      }
    }
}

/**
 * Executor consuming std/err output streams as the process is being executed.
 *
 * @return exitCode of the process
 */
private object ProcessExecutor {
  suspend fun exec(
    executable: String,
    inputLines: List<String>,
    stdWriter: Writer,
    errWriter: Writer,
    dispatcher: CoroutineDispatcher
  ): Int =
    withContext(dispatcher) {
      val process = ProcessBuilder(listOf(executable)).start()

      val exitCode = async { process.waitFor() }
      val errOutput = async {
        consumeProcessOutput(process.errorStream, errWriter, process, dispatcher)
      }
      val stdOutput = async {
        consumeProcessOutput(process.inputStream, stdWriter, process, dispatcher)
      }
      val input = async { feedProcessInput(process.outputStream, inputLines) }

      input.await()
      stdOutput.await()
      errOutput.await()
      exitCode.await()
    }

  // Feeds input lines to the process' outputStream
  private fun feedProcessInput(outputStream: OutputStream, inputLines: List<String>) {
    outputStream.writer(UTF_8).use { writer ->
      inputLines.forEach { line ->
        writer.write(line + System.lineSeparator())
        writer.flush()
      }
    }
  }

  // Consumes output stream as the process is being executed - otherwise on Windows the process
  // would block when the output buffer is full.
  private suspend fun consumeProcessOutput(
    source: InputStream?,
    outputWriter: Writer,
    process: Process,
    dispatcher: CoroutineDispatcher
  ) =
    withContext(dispatcher) {
      if (source == null) return@withContext

      var isFirstLine = true
      BufferedReader(InputStreamReader(source, UTF_8.name())).use { reader ->
        do {
          val line = reader.readLine()
          if (Strings.isNotEmpty(line)) {
            if (!isFirstLine) outputWriter.append(System.lineSeparator())
            isFirstLine = false
            outputWriter.append(line)
          } else {
            yield()
          }
          ensureActive()
        } while (process.isAlive || line != null)
      }
    }
}

// Shortens the string if over maxLength (and adds an ellipsis at the end if the case)
private fun String.ellipsize(maxLength: Int): String {
  val text = this
  return if (text.length <= maxLength) text else "${text.subSequence(0, maxLength - 3)}..."
}
