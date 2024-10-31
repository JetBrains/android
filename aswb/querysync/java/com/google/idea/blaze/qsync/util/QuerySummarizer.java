/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync.util;

import com.google.idea.blaze.qsync.project.SnapshotProto;
import com.google.idea.blaze.qsync.project.SnapshotProto.Snapshot;
import com.google.idea.blaze.qsync.query.QuerySummary;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Command line tool to create a query summary from the output from a query invocation.
 *
 * <p>To use this, run:
 *
 * <pre>
 *   blaze run //third_party/intellij/bazel/plugin/querysync/java/com/google/idea/blaze/qsync/util:query_summarizer \
 *     -- /path/to/query_summary_output
 * </pre>
 *
 * Where {@code /path/to/query_summary_output} is the stdout from a {@code query} invocation with
 * the appropriate flags set (see {@link com.google.idea.blaze.qsync.query.QuerySpec} for details).
 *
 * <p>A {@code qsyncdata.gz} file will be generated, but note that this cannot be used directly with
 * later querysync stages as it does not include the project definition.
 */
public class QuerySummarizer {

  private final File queryOutputFile;

  public static void main(String[] args) throws IOException {
    System.exit(new QuerySummarizer(new File(args[0])).run());
  }

  private QuerySummarizer(File queryOutputFile) {
    this.queryOutputFile = queryOutputFile;
  }

  private int run() throws IOException {
    System.err.println(
        "Reading query data from "
            + queryOutputFile.getAbsolutePath()
            + " ("
            + queryOutputFile.length()
            + " bytes)");
    QuerySummary summary = QuerySummary.create(queryOutputFile);
    File f = new File(queryOutputFile.getParent(), queryOutputFile.getName() + "_qsyncdata.gz");
    System.err.println("Writing qsyncdata to " + f.getAbsolutePath());
    try (OutputStream out = new GZIPOutputStream(new FileOutputStream(f))) {
      SnapshotProto.Snapshot snapshot =
          Snapshot.newBuilder().setQuerySummary(summary.protoForSerializationOnly()).build();
      snapshot.writeTo(out);
    }
    return 0;
  }
  ;
}
