package com.google.idea.blaze.qsync.dispatchers

import com.google.idea.common.experiments.IntExperiment
import kotlinx.coroutines.Dispatchers

/**
 * Provides shared dispatchers for query sync operations.
 */
object QuerySyncDispatchers {

  private const val DEFAULT_READER_THREADS = 50
  private val readerThreadsExperiment =
    IntExperiment("querysync.reader.threads", DEFAULT_READER_THREADS)

  /**
   * Dispatcher for I/O bound tasks, such as reading package prefixes.
   */
  val IO = Dispatchers.IO.limitedParallelism(runCatching { readerThreadsExperiment.value }.getOrDefault(DEFAULT_READER_THREADS))
}
