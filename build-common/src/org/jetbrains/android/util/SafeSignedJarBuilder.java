package org.jetbrains.android.util;

import com.android.tools.build.apkzlib.sign.SigningOptions;
import com.android.tools.build.apkzlib.zfile.ApkCreator;
import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory;
import com.android.tools.build.apkzlib.zfile.ApkZFileCreatorFactory;
import com.android.tools.build.apkzlib.zfile.NativeLibrariesPackagingMode;
import com.android.tools.build.apkzlib.zip.ZFileOptions;
import com.android.tools.build.apkzlib.zip.compress.DeflateExecutionCompressor;
import com.google.common.base.Predicate;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.zip.Deflater;
import org.jetbrains.annotations.Nullable;

public class SafeSignedJarBuilder implements AutoCloseable {
  private final String myOutFilePath;
  private final ApkCreator apkCreator;

  public SafeSignedJarBuilder(PrivateKey key, X509Certificate certificate, String outFilePath) throws IOException {
    ZFileOptions options = new ZFileOptions();
    options.setNoTimestamps(true);
    options.setCoverEmptySpaceUsingExtraField(true);
    options.setAutoSortFiles(true);
    options.setCompressor(new DeflateExecutionCompressor(AppExecutorUtil.getAppExecutorService(), Deflater.BEST_SPEED));

    ApkZFileCreatorFactory factory = new ApkZFileCreatorFactory(options);

    Path apkPath = new File(outFilePath).toPath();
    if (Files.isRegularFile(apkPath) && Files.size(apkPath) == 0){
      // apkzlib cannot append empty file, because it tries to find a signature, and fail if no signature found.
      Files.delete(apkPath);
    }

    ApkCreatorFactory.CreationData.Builder creationData = ApkCreatorFactory.CreationData.builder()
      .setApkPath(apkPath.toFile())
      .setBuiltBy("IntelliJ IDEA")
      .setCreatedBy("IntelliJ IDEA")
      .setIncremental(false)
      .setNativeLibrariesPackagingMode(NativeLibrariesPackagingMode.UNCOMPRESSED_AND_ALIGNED);

    if (key != null && certificate != null) {
      SigningOptions signingOptions = SigningOptions.builder()
        .setCertificates(certificate)
        .setKey(key)
        .setV1SigningEnabled(true)
        .setMinSdkVersion(1) // todo: add v2 signature and pass API version here
        .build();

      creationData.setSigningOptions(signingOptions);
    }


    apkCreator = factory.make(creationData.build());
    myOutFilePath = FileUtil.toSystemDependentName(outFilePath);
  }

  public void writeFile(File inputFile, String jarPath) throws IOException {
    if (FileUtil.pathsEqual(inputFile.getPath(), myOutFilePath)) {
      throw new IOException("Cannot pack file " + myOutFilePath + " into itself");
    }
    apkCreator.writeFile(inputFile, jarPath);
  }

  @Override
  public void close() throws IOException {
    apkCreator.close();
  }

  public void writeZip(File zip, @Nullable Predicate<String> isIgnored) throws IOException {
    apkCreator.writeZip(zip, null, isIgnored);
  }
}
