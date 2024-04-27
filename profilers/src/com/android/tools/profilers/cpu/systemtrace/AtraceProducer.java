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
package com.android.tools.profilers.cpu.systemtrace;

import com.android.tools.idea.protobuf.ByteString;
import com.google.common.base.Charsets;
import com.intellij.openapi.diagnostic.Logger;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import trebuchet.io.BufferProducer;
import trebuchet.io.DataSlice;

/**
 * This class takes concatenated compressed atrace files and will on the fly decompress them one line at a time.
 * As lines are requested from nextLine this class will read in a chunk of the compressed atrace file decompress it
 * and return the next line decoded. Each line is returned as a string as the atrace file is encoded as a series of
 * strings.
 */
public class AtraceProducer implements TrebuchetBufferProducer {
  private static final int BUFFER_SIZE_BYTES = 2048;
  private byte[] myOutputBuffer = new byte[BUFFER_SIZE_BYTES];
  private byte[] myInputBuffer = new byte[BUFFER_SIZE_BYTES];
  private int myInputBufferOffset = BUFFER_SIZE_BYTES; //We have no data available to read so we point to end of buffer.
  private boolean myIsFinished = false;
  private Queue<String> myLineQueue = new LinkedList<>();
  private String myLastPartialLine = "";
  private InputStream myInputStream;
  private Inflater myInflater;

  /**
   * The TRACE:\n header comes from atrace when it dumps data to disk. Each compressed chunk starts with this.
   */
  public static final ByteString HEADER = ByteString.copyFrom("TRACE:\n", Charsets.UTF_8);

  private static Logger getLogger() {
    return Logger.getInstance(AtraceProducer.class);
  }

  public AtraceProducer() {
  }

  @Override
  public boolean parseFile(File file) {
    try {
      myInputStream = new FileInputStream(file);
      myInflater = new Inflater();

      // Read the initial header of the input file.
      fillInputBuffer();
      verifyHeader();
      myLineQueue.add("# Initial Data Required by Importer");
      return true;
    } catch (IOException ex) {
      getLogger().error(ex);
      return false;
    }
  }

  /**
   * Whether a given {@link File} header matches {@link #HEADER}.
   */
  public static boolean verifyFileHasAtraceHeader(@NotNull File trace) {
    try (FileInputStream input = new FileInputStream(trace)) {
      byte[] buffer = new byte[HEADER.size()];
      int bytesRead = input.read(buffer, 0, HEADER.size());
      if (bytesRead != HEADER.size()) {
        getLogger().warn("Some bytes of the trace file header could not be read.");
        return false;
      }
      ByteString fileHeader = ByteString.copyFrom(buffer);
      return HEADER.toStringUtf8().equals(fileHeader.toStringUtf8());
    }
    catch (IOException e) {
      getLogger().warn("There was an error trying to read the trace file.");
      return false;
    }
  }

  private void verifyHeader() {
    for (int i = 0; i < HEADER.size(); i++) {
      assert HEADER.byteAt(i) == myInputBuffer[myInputBufferOffset];
      myInputBufferOffset++;
    }
  }

  /**
   * Read as much data as we can from our input file, and set a new input buffer.
   */
  private int fillInputBuffer() throws IOException {
    shift(myInputBuffer, myInputBufferOffset, 0, myInputBuffer.length - myInputBufferOffset);
    myInputBufferOffset = myInputBuffer.length - myInputBufferOffset;
    return myInputStream.read(myInputBuffer, myInputBufferOffset, myInputBuffer.length - myInputBufferOffset);
  }

  /**
   * Fill data on the inflater from the input buffer.
   */
  private void fill() throws IOException {
    int readAmount = fillInputBuffer();
    myInflater.setInput(myInputBuffer, 0, readAmount + myInputBufferOffset);
    myInputBufferOffset = 0;
  }

  /**
   * Move data from one part of an array to another. This is used to shift data left before reading more data from the file.
   *
   * @param data       array to move data around in.
   * @param srcOffset  offset to start moving data from.
   * @param destOffset offset to move data to, has to be less than srcOffset.
   * @param length     amount of data to move.
   */
  private void shift(byte[] data, int srcOffset, int destOffset, int length) {
    for (int i = 0; i < length; i++) {
      data[destOffset + i] = data[srcOffset + i];
    }
  }

  /**
   * This function will decompress the atrace file on demand and return the next line found.
   *
   * @return the next line of data from the trace.
   */
  public String getNextLine() throws IOException, DataFormatException {
    // Early out if we have no more data to read from the file, and we have no lines in our queue.
    if (myIsFinished && myLineQueue.isEmpty()) {
      return null;
    }

    // If we have more data in the file, and our queue is empty we get the next block of lines.
    while (myLineQueue.isEmpty()) {

      // If we are finished with our decompression buffer, we either are done with our input,
      // or we are done with this chunk of the file.
      if (myInflater.finished()) {
        // If we have no more input then we are at the end of the file, and have nothing left to
        // decompress.
        if (myInputStream.available() == 0) {
          myIsFinished = true;
          myInputStream.close();
          myLineQueue.add(myLastPartialLine);
          myLastPartialLine = "";
          break;
        }
        else {

          // We can get into a state where we read the exact amount of data into our buffer and as we reset to the head of our next
          // file we want to refill the buffer.
          if (myInputBufferOffset + HEADER.size() >= myInputBuffer.length) {
            fillInputBuffer();
          }
          // If we are only done with one chunk of the file, then we read the header and reset our
          // inflater.
          verifyHeader();
          myInflater = new Inflater();
        }
      }

      if (myInflater.needsInput()) {
        fill();
      }

      // Need to keep track of where in our buffer the inflater has read to. To do this,
      // we keep track of the total bytes in the inflater has consumed. So we subtract
      // the previous total from our new total and add this to our buffer count.
      int inputBufferTotal = myInflater.getTotalIn();
      int bytesInOutputBuffer = myInflater.inflate(myOutputBuffer, 0, myOutputBuffer.length);
      inputBufferTotal = myInflater.getTotalIn() - inputBufferTotal;
      myInputBufferOffset += inputBufferTotal;

      // Create a string from our decompressed buffer, and split it into the lines for that string.
      myLastPartialLine += new String(myOutputBuffer, 0, bytesInOutputBuffer);
      // By default string split gets passed 0, this indicates that the string should return minimum number of split lines. -1 indicates
      // that we want to return the actual number of splits. Allowing the split to handle the case of the last char of \n instead of
      // dropping it we get an additional line. So foo\nbar\n ends up returning 3 lines ("foo", "bar", "") which works with our
      // lastIndexOf('\n') substring below.
      String[] lines = myLastPartialLine.split("\n", -1);

      // Add each line to our queue and keep track of our partial line for the next time we decompress more info.
      for (int i = 0; i < lines.length - 1; i++) {
        myLineQueue.add(lines[i].trim());
      }

      myLastPartialLine = myLastPartialLine.substring(myLastPartialLine.lastIndexOf('\n') + 1);
    }
    return myLineQueue.remove();
  }

  /**
   * Required by {@link BufferProducer}, closes the streams held by the decompressor.
   */
  @Override
  public void close() {
    try {
      myInflater.end();
      myInputStream.close();
    }
    catch (IOException ex) {
      getLogger().warn(ex);
    }
  }

  /**
   * @return the next line used by {@link BufferProducer}. The parser assumes that each line ends
   * with \n, so we add a return to the string before returning the next requested slice.
   */
  @Nullable
  @Override
  public DataSlice next() {
    try {
      String line = getNextLine();
      if (line != null) {
        // Due to a bug in StreamingLineReader we need to truncate all lines to 1023 characters including the \n appended to the end.
        // For more details see (b/77846431)
        byte[] data = String.format("%s\n", line.substring(0, Math.min(1022, line.length()))).getBytes();
        return new DataSlice(data, 0, data.length);
      }
    }
    catch (IOException | DataFormatException ex) {
      getLogger().error(ex);
    }
    return null;
  }
}
