/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.datastore;

import com.android.testutils.TestResources;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * This class is used to manage validation, and serialization of RPC calls.
 * when test.export.grpc=True this class will serialize out to test.export.path.
 * when test.export.grpc=False this class will lo
 */
public class TestGrpcFile {
  // The message delimiter is not required, however this is kept in the output incase we want to change the way validation works
  // to match on message instead of match on exact contents.
  private static final boolean EXPORT_CALLS = Boolean.parseBoolean(System.getProperty("test.export.grpc", "False"));
  private static final String TEST_RESOURCE_DIR = System.getProperty("test.export.path", System.getProperty("java.io.tmpdir"));
  private static final String TEST_FILE_EXT = ".xml";
  private File myFile;
  GrpcCallStack myGrpcCalls;

  /**
   * @param file The path/fileName to the test file used to validate the order and contents of rpc calls.
   */
  public TestGrpcFile(String file) throws IOException {
    myFile = loadFile(file, EXPORT_CALLS);
    myGrpcCalls = new GrpcCallStack();
  }

  /**
   * If test.export.grpc=True this function serializes GrpcCallStack to XML, otherwise it validates
   * that myGrpcCalls matches exactly the file passed in to the constructor.
   * @throws IOException
   */
  public void closeAndValidate() throws IOException {
    if (EXPORT_CALLS) {
      try {
        Marshaller marshaller  = JAXBContext.newInstance(GrpcCallStack.class).createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        marshaller.setProperty("com.sun.xml.internal.bind.xmlHeaders", "\n<!-- Auto generated with perfd-host test via flag -Dtest.export.grpc=True -->");
        marshaller.marshal(myGrpcCalls, myFile);
      } catch (JAXBException ex) {
        throw new IOException("Failed to serialize test file", ex);
      }
    } else {
      validateFile();
    }
  }

  /**
   * Validates that the cached query contents (myContents) exactly matches that of the cached file.
   */
  private void validateFile() throws IOException {
    try {
      Unmarshaller marshaller = JAXBContext.newInstance(GrpcCallStack.class).createUnmarshaller();
      GrpcCallStack expectedCalls = (GrpcCallStack)marshaller.unmarshal(myFile);
      assertEquals(expectedCalls, myGrpcCalls);
    } catch (JAXBException ex) {
      throw new IOException("Failed to deserialize test file", ex);
    }
  }

  private File loadFile(String fileName, boolean export) throws IOException {
    File file;
    if (export) {
      file = new File(TEST_RESOURCE_DIR + fileName + TEST_FILE_EXT);
      if (!file.exists()) {
        file.getParentFile().mkdir();
        assertTrue(file.createNewFile());
      }
    }
    else {
      try {
        file = TestResources.getFile(TestGrpcService.class, fileName.replace('\\', '/') + TEST_FILE_EXT);
      }
      catch (IllegalArgumentException ex) {
        throw new IllegalArgumentException(
          String.format("The file %s could not be found, if this is a new test run with -Dtest.export.grpc=True" +
                        " then copy the test file from %s to the testData folder", fileName,
                        TEST_RESOURCE_DIR), ex);
      }
    }
    return file;
  }

  protected void recordCall(String method, String msgClass, String msg) {
    GrpcCall call = new GrpcCall();
    call.setFunctionCalled(method);
    call.setClazz(msgClass);
    call.setMessage(msg);
    myGrpcCalls.getGrpcCalls().add(call);
  }
}
