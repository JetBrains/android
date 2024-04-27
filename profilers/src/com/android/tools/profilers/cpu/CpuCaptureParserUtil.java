/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.profilers.cpu.art.ArtTraceParser;
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType;
import com.android.tools.profilers.cpu.simpleperf.SimpleperfTraceParser;
import com.android.tools.profilers.cpu.systemtrace.AtraceProducer;
import com.android.tools.profilers.cpu.systemtrace.PerfettoProducer;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CpuCaptureParserUtil {
  private static final Set<TraceType> AVAILABLE_TRACE_TYPES =
    ImmutableSet.of(TraceType.ART, TraceType.SIMPLEPERF, TraceType.ATRACE, TraceType.PERFETTO);

  // Each tester function verifies if a passed-in file is of the respective configuration type.
  private static final Predicate<File> ART_TESTER = ArtTraceParser::verifyFileHasArtHeader;
  private static final Predicate<File> SIMPLE_PREF_TESTER = SimpleperfTraceParser::verifyFileHasSimpleperfHeader;
  private static final Predicate<File> ATRACE_FILE_TESTER = AtraceProducer::verifyFileHasAtraceHeader;
  private static final Predicate<File> PERFETTO_FILE_TESTER = PerfettoProducer::verifyFileHasPerfettoTraceHeader;

  @Nullable
  public static TraceType getFileTraceType(@NotNull File traceFile, @NotNull TraceType profilerType) {
    boolean isKnownType = !TraceType.UNSPECIFIED.equals(profilerType);
    final Set<TraceType> traceTypesToTry = getTraceTypesToTryToParseWith(profilerType, isKnownType);
    for (TraceType traceType : traceTypesToTry) {
      final Optional<Boolean> inputVerification = getInputVerification(traceType, traceFile, isKnownType);
      // If parser can take this trace, then traceType is found.
      if (inputVerification.isPresent() && inputVerification.get()) {
        return traceType;
      }
    }
    return null;
  }

  private static Set<TraceType> getTraceTypesToTryToParseWith(@NotNull TraceType profilerType, boolean isKnownType) {
    if (isKnownType) {
      return ImmutableSet.of(profilerType);
    }
    return AVAILABLE_TRACE_TYPES;
  }

  private static Optional<Boolean> getInputVerification(@NotNull TraceType type,
                                                        @NotNull File traceFile,
                                                        boolean expectedToBeCorrectParser) {
    Optional<Predicate<File>> traceInputVerification = getTraceInputVerification(type);
    if (traceInputVerification.isEmpty()) {
      return Optional.empty();
    }
    try {
      final boolean inputVerificationStatus = traceInputVerification.get().test(traceFile);
      // If we expected this to be the correct parser and parser can't take this trace, then we need to throw
      if (expectedToBeCorrectParser && !inputVerificationStatus) {
        throw new CpuCaptureParser.FileHeaderParsingFailureException(traceFile.getAbsolutePath(), type);
      }
      return Optional.of(inputVerificationStatus);
    }
    catch (Throwable t) {
      if (expectedToBeCorrectParser) {
        throw new CpuCaptureParser.FileHeaderParsingFailureException(traceFile.getAbsolutePath(), type, t);
      }
    }
    return Optional.empty();
  }

  @VisibleForTesting
  private static Optional<Predicate<File>> getTraceInputVerification(@NotNull TraceType type) {
    return switch (type) {
      case ART -> Optional.of(ART_TESTER);
      case SIMPLEPERF -> Optional.of(SIMPLE_PREF_TESTER);
      case ATRACE -> Optional.of(ATRACE_FILE_TESTER);
      case PERFETTO -> Optional.of(PERFETTO_FILE_TESTER);
      default -> Optional.empty();
    };
  }
}
