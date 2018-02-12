/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.fd.crash;

import com.android.tools.analytics.Anonymizer;
import com.android.tools.idea.fd.FlightRecorder;
import com.android.utils.NullLogger;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PermanentInstallationID;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.entity.GzipCompressingEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * {@link GoogleCrash} provides APIs to upload crash reports to Google crash reporting service.
 * @see <a href="http://go/studio-g3doc/implementation/crash">Crash Backend</a> for more information.
 */
public class GoogleCrash {
  private static final boolean UNIT_TEST_MODE = ApplicationManager.getApplication() == null;
  private static final boolean DEBUG_BUILD = !UNIT_TEST_MODE && ApplicationManager.getApplication().isInternal();

  // Send crashes during development to the staging backend
  private static final String CRASH_URL =
    (UNIT_TEST_MODE || DEBUG_BUILD) ? "https://clients2.google.com/cr/staging_report" : "https://clients2.google.com/cr/report";

  @Nullable
  private static final String ANONYMIZED_UID = getAnonymizedUid();
  private static final String LOCALE = Locale.getDefault() == null ? "unknown" : Locale.getDefault().toString();

  // The standard keys expected by crash backend. The product id and version are required, others are optional.
  static final String KEY_PRODUCT_ID = "productId";
  static final String KEY_VERSION = "version";

  private static GoogleCrash ourInstance;
  private final String myCrashUrl;

  @Nullable
  private static String getAnonymizedUid() {
    if (UNIT_TEST_MODE) {
      return "UnitTest";
    }

    try {
      return Anonymizer.anonymizeUtf8(new NullLogger(), PermanentInstallationID.get());
    }
    catch (IOException e) {
      return null;
    }
  }

  private GoogleCrash() {
    this(CRASH_URL);
  }

  @VisibleForTesting
  GoogleCrash(@NotNull String crashUrl) {
    myCrashUrl = crashUrl;
  }

  public CompletableFuture<String> submit(@NotNull FlightRecorder flightRecorder, @NotNull String issueText, @NotNull List<Path> logFiles) {
    CompletableFuture<String> future = new CompletableFuture<>();
    ForkJoinPool.commonPool().submit(() -> {
      try {
        HttpClient client = HttpClients.createDefault();
        HttpResponse response = client.execute(createPost(flightRecorder, issueText, logFiles));
        StatusLine statusLine = response.getStatusLine();
        if (statusLine.getStatusCode() >= 300) {
          future.completeExceptionally(new HttpResponseException(statusLine.getStatusCode(), statusLine.getReasonPhrase()));
          if (DEBUG_BUILD) {
            //noinspection UseOfSystemOutOrSystemErr
            System.out.println("Error submitting report: " + statusLine);
          }
          return;
        }

        HttpEntity entity = response.getEntity();
        if (entity == null) {
          future.completeExceptionally(new NullPointerException("Empty response entity"));
          return;
        }

        String reportId = EntityUtils.toString(entity);
        if (DEBUG_BUILD) {
          //noinspection UseOfSystemOutOrSystemErr
          System.out.println("Report submitted: http://go/crash-staging/" + reportId);
        }
        future.complete(reportId);
      }
      catch (IOException e) {
        future.completeExceptionally(e);
      }
    });
    return future;
  }

  @NotNull
  private HttpUriRequest createPost(@NotNull FlightRecorder flightRecorder, @NotNull String issueText, @NotNull List<Path> logFiles) {
    HttpPost post = new HttpPost(myCrashUrl);

    ApplicationInfo applicationInfo = getApplicationInfo();

    String strictVersion = applicationInfo == null ? "0.0.0.0" : applicationInfo.getStrictVersion();

    MultipartEntityBuilder builder = MultipartEntityBuilder.create();

    // key names recognized by crash
    builder.addTextBody(KEY_PRODUCT_ID, "AndroidStudio");
    builder.addTextBody(KEY_VERSION, strictVersion);
    builder.addTextBody("exception_info", getUniqueStackTrace());
    builder.addTextBody("user_report", issueText);

    if (ANONYMIZED_UID != null) {
      builder.addTextBody("guid", ANONYMIZED_UID);
    }
    RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
    builder.addTextBody("ptime", Long.toString(runtimeMXBean.getUptime()));

    // product specific key value pairs
    builder.addTextBody("fullVersion", applicationInfo == null ? "0.0.0.0" : applicationInfo.getFullVersion());

    builder.addTextBody("osName", StringUtil.notNullize(SystemInfo.OS_NAME));
    builder.addTextBody("osVersion", StringUtil.notNullize(SystemInfo.OS_VERSION));
    builder.addTextBody("osArch", StringUtil.notNullize(SystemInfo.OS_ARCH));
    builder.addTextBody("locale", StringUtil.notNullize(LOCALE));

    builder.addTextBody("vmName", StringUtil.notNullize(runtimeMXBean.getVmName()));
    builder.addTextBody("vmVendor", StringUtil.notNullize(runtimeMXBean.getVmVendor()));
    builder.addTextBody("vmVersion", StringUtil.notNullize(runtimeMXBean.getVmVersion()));

    MemoryUsage usage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    builder.addTextBody("heapUsed", Long.toString(usage.getUsed()));
    builder.addTextBody("heapCommitted", Long.toString(usage.getCommitted()));
    builder.addTextBody("heapMax", Long.toString(usage.getMax()));

    // add report specific data
    builder.addTextBody("Type", "InstantRunFlightRecorder");
    addFlightRecorderLogs(builder, flightRecorder, logFiles);

    post.setEntity(new GzipCompressingEntity(builder.build()));
    return post;
  }

  static String getUniqueStackTrace() {
    StringBuilder sb = new StringBuilder(100);
    sb.append("com.android.InstantRunException: Flight Recorder Information: ");
    sb.append(System.currentTimeMillis());
    sb.append('\n');
    sb.append("\tat ");

    int i = 0;
    for (String u : Splitter.on('-').split(UUID.randomUUID().toString())) {
      sb.append('p');
      sb.append(u);
      sb.append('.');
    }
    sb.append("FlightRecorder.report(Flight");
    sb.append(System.currentTimeMillis());
    sb.append("Recorder.java:500)");

    return sb.toString();
  }

  private static void addFlightRecorderLogs(@NotNull MultipartEntityBuilder builder,
                                            @NotNull FlightRecorder flightRecorder,
                                            @NotNull List<Path> logFiles) {
    try {
      // Crash backend restricts uploads to 1.2M total, so we need to zip up all the files together.
      Path path = zipFiles(flightRecorder, logFiles);
      builder.addBinaryBody(path.getFileName().toString(),
                            Files.readAllBytes(path),
                            ContentType.APPLICATION_OCTET_STREAM,
                            path.getFileName().toString());
    }
    catch (IOException e) {
      builder.addTextBody("IOError", e.toString());
    }
  }

  @NotNull
  private static Path zipFiles(@NotNull FlightRecorder flightRecorder, @NotNull List<Path> logFiles)
    throws IOException {
    Path tempFile = Files.createTempFile("flr", ".zip");
    String baseName = FileUtilRt.getNameWithoutExtension(tempFile.getFileName().toString());

    byte[] data = new byte[4096];
    int i;

    try (FileOutputStream fos = new FileOutputStream(tempFile.toFile());
         BufferedOutputStream bos = new BufferedOutputStream(fos);
         ZipOutputStream zos = new ZipOutputStream(bos)) {
      zos.setMethod(ZipOutputStream.DEFLATED);

      for (Path file : logFiles) {
        String name = baseName + "/" + flightRecorder.getPresentablePath(file);
        zos.putNextEntry(new ZipEntry(name.replace(File.separatorChar, '/')));

        try (InputStream is = Files.newInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(is)) {
          while ((i = bis.read(data)) > 0) {
            zos.write(data, 0, i);
          }
        }
      }
    }

    return tempFile;
  }

  @Nullable
  private static ApplicationInfo getApplicationInfo() {
    // We obtain the ApplicationInfo only if running with an application instance. Otherwise, a call to a ServiceManager never returns..
    return ApplicationManager.getApplication() == null ? null : ApplicationInfo.getInstance();
  }

  public static synchronized GoogleCrash getInstance() {
    if (ourInstance == null) {
      ourInstance = new GoogleCrash();
    }

    return ourInstance;
  }
}

