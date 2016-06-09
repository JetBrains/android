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
package com.android.tools.idea.editors.gfxtrace;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

public class JdwpSnippets {

  public static final String GAPII_ABSTRACT_SOCKET = "gapii-init";

  public static final String GAPII_HEADER = "GAPPI";

  public static String getLoaderSnippet(File[] libs) {

    String libraryNamesLiteral = Arrays.stream(libs).map(f -> '"' + f.getName() + '"').collect(Collectors.joining(", ", "{", "}"));
    String librarySizesLiteral = Arrays.stream(libs).map(f -> String.valueOf(f.length())).collect(Collectors.joining(", ", "{", "}"));

    return
      "  String TAG = \"gapii-init\";\n" +
      "  String socketName = \"" + GAPII_ABSTRACT_SOCKET + "\";\n" +
      "  byte[] header = \"" + GAPII_HEADER + "\".getBytes();\n" +
      "\n" +
      "  String[] libs = " + libraryNamesLiteral + "\n" +
      "  long[] libSizes = " + librarySizesLiteral + "\n" +
      "\n" +
      "  android.util.Log.d(TAG, \"Waiting for connection.\");\n" +
      "  android.net.LocalServerSocket serverSocket = new android.net.LocalServerSocket(socketName);\n" +
      "  android.net.LocalSocket socket = serverSocket.accept();\n" +
      "\n" +
      "  java.io.InputStream inStream = socket.getInputStream();\n" +
      "  java.io.OutputStream outStream = socket.getOutputStream();\n" +
      "  java.nio.channels.ReadableByteChannel inChannel = java.nio.channels.Channels.newChannel(inStream);\n" +
      "\n" +
      "  outStream.write(header);\n" +
      "\n" +
      "  java.io.File filesDir = ((android.app.Application) this).getFilesDir();\n" +
      "\n" +
      "  for (int i = 0; i < libs.length; i++) {\n" +
      "    java.io.File libFile = new java.io.File(filesDir, libs[i]);\n" +
      "    long libSize = libSizes[i];\n" +
      "\n" +
      "    android.util.Log.d(TAG, String.format(\"Copying %d bytes to %s.\", libSize, libFile));\n" +
      "    java.io.FileOutputStream fileStream = new java.io.FileOutputStream(libFile);\n" +
      "    java.nio.channels.FileChannel fileChannel = fileStream.getChannel();\n" +
      "\n" +
      "    long remaining = libSize;\n" +
      "    while (remaining > 0) {\n" +
      "      remaining -= fileChannel.transferFrom(inChannel, libSize - remaining, remaining); \n" +
      "    }\n" +
      "    fileChannel.close();\n" +
      "\n" +
      "    android.util.Log.d(TAG, \"Library copied, loading.\");\n" +
      "    System.load(libFile.toString());\n" +
      "  }\n" +
      "\n" +
      "  serverSocket.close();\n" +
      "  socket.close();\n";
  }
}
