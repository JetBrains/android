/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.example.targetsdkmigrationsample;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.io.*;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * This class provides functionality to traverse and process entries within a ZIP file.
 * It utilizes Java's built-in ZIP handling classes to open, read, and iterate
 * through the contents of a ZIP archive.
 */
public class ZipPathTraversal{

    /**
     * Verified behavior changes in API 34 for Zip path traversal.More details below
     * <a href="https://developer.android.com/about/versions/14/behavior-changes-14#zip-path-traversal">...</a>
     */
    public static void processZipFile(String zipFilePath) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipFilePath);
             InputStream fis = new FileInputStream(zipFilePath);
             ZipInputStream zis = new ZipInputStream(fis)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // Process zip entries
                System.out.println("Entry: " + entry.getName());
                // ... your logic to handle each entry ...
            }
        }  catch (IOException e) {
            System.err.println("Error processing zip file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Verified behavior changes in API 34 OpenJDK17 UUID handlingl.More details below
     * <a href="https://developer.android.com/about/versions/14/behavior-changes-14#core-libraries">...</a>
     */
    public static void strictValidation(){
        // Example of strict validation using UUID
        UUID uuid = UUID.fromString("f81d4fae-7dec-11d0-a765-00a0c91e6bf6");
        System.out.println("Validated UUID: " + uuid);
    }
}

