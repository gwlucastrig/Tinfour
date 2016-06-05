/* --------------------------------------------------------------------
 * Copyright 2015 Gary W. Lucas.
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
 * ---------------------------------------------------------------------
 */


/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 02/2015  G. Lucas     Created
 *
 * -----------------------------------------------------------------------
 */
package tinfour.las;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * Accesses a random access file using a ByteBuffer with little-endian
 * byte order in support of the LAS file format.
 */
public class BufferedRandomAccessForLidar implements  Closeable {

  /**
   * The default size for the read (and eventually write) buffer
   */
  private static final int DEFAULT_BUFFER_SIZE = 8 * 1024;


  /**
   * A reference to the file with which this instance is associated
   */
  final File file;

  /**
   * The byte buffer used for reading (and eventually writing) data
   */
  final ByteBuffer buffer;

  /**
   * The instance of the random-access file.
   */
  RandomAccessFile raFile;

  /**
   * The file channel obtained from the random-access file
   */
  FileChannel fileChannel;
  /**
   * the length of the random-access file being read
   */
  long raFileLen;

  /**
   * the position in the virtual file (not in the actual file)
   */
  long raFilePos;

  /**
   * Indicates that at least some data has been read into the buffer.
   */
  boolean bufferContainsData;

  /**
   * Opens the specified file for read-only, random-access.
   * A buffer is created to provide efficient input operations
   * across a series of sequential method calls.  In accordance with
   * the LAS standard, all data in the file is treated as being in
   * little-endian byte order.
   *
   * @param file A valid file object.
   * @throws IOException In the event of an unrecoverable IO condition
   * such as file not found, access denied, etc.
   */
  public BufferedRandomAccessForLidar(File file) throws IOException {
    if (file == null) {
      throw new NullPointerException();
    }
    buffer = ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE);
    buffer.order(ByteOrder.LITTLE_ENDIAN);

    this.file = file;
    raFile = new RandomAccessFile(file, "r");
    fileChannel = raFile.getChannel();
    raFileLen = raFile.length();
    raFilePos = 0;
    raFile.seek(0);
    bufferContainsData = false;

  }

  /**
   * Closes the file resources used by the referenced instance.
   * No further file access will be possible.
   *
   * @throws IOException In the event of an unrecoverable I/O condition.
   */

  @Override
  public void close() throws IOException {
    if (raFile != null) {
      raFile.close();
      raFile = null;
    }

    fileChannel = null;
  }

  /**
   * Gets a File object referring to the currently open file (if any).
   *
   * @return A valid object if defined; otherwise, null.
   */
  public File getFileReference() {
    return file;
  }

  /**
   * Gets the current size of the file in bytes.
   *
   * @return A long integer giving file size in bytes.
   */
  public long getFileSize() {
    return raFileLen;
  }

  /**
   * Provides the current position within the random-access file.
   *
   * @return a long integer value giving offset in bytes from beginning of file.
   */
  public long getFilePosition() {
    return raFilePos;
  }

  /**
   * Prepares the read buffer to access the specified number of
   * bytes. Adjusts internal elements accordingly. If the seek method
   * was previously called, an actual read may not yet occurred but
   * will be executed by this method.
   * at least the required number of bytes.
   *
   * @param bytesToRead Number of bytes to be read, must not exceed
   * the size of the buffer.
   * @throws IOException In the event of an unrecoverable I/O condition.
   */
  private void prepareBufferForRead(int bytesToRead) throws IOException {
    int bytesNotRead = bytesToRead;
    if (raFile == null) {
      throw new IOException("Reading from a file that was closed");
    }

    if (raFilePos >= raFileLen) {
      throw new EOFException();
    }

    boolean readEnabled = true;
    if (bufferContainsData) {
      int remaining = buffer.remaining();
      if (remaining >= bytesNotRead) {
        readEnabled = false;
      } else {
        // remaining < nBytes
        readEnabled = true;
        if (remaining == 0) {
          buffer.clear();
        } else {
          buffer.compact();
          // note that we have to tweak our bookkeeping here
          // because we have a partial in the buffer... so we need
          // to advance the rafPosition ahead so that we don't re-read
          // what we've already pulled in.
          raFilePos += remaining;
          bytesNotRead -= remaining;
        }
      }
    }

    if (readEnabled) {
      raFile.seek(raFilePos);
      fileChannel.read(buffer);
      buffer.flip();
      bufferContainsData = true;
    }
    raFilePos += bytesNotRead;
  }

  /**
   * Reads a C/C++ style null-terminated string of a specified maximum
   * length from the from data file. The source data is treated as specifying
   * a string as one byte per character following the ISO-8859-1 standard.
   * If a zero byte is encountered in the sequence, the string is terminated.
   * Otherwise, it is extended out to the maximum length. Regardless of
   * how many characters are read, the file position is always adjusted
   * forward by the maximum length.
   *
   * @param maximumLength Maximum number of bytes to be read from file.
   * @return A valid, potentially empty string.
   * @throws IOException In the event of an unrecoverable I/O condition.
   */
  public String readAscii(int maximumLength) throws IOException {
    if (maximumLength <= 0) {
      return "";
    }
    StringBuilder builder = new StringBuilder(maximumLength);
    BufferedRandomAccessForLidar.this.readAscii(builder, maximumLength);
    return builder.toString();
  }

  /**
   * Reads a C/C++ style null-terminated string of a specified maximum
   * length from the from data file. The source data is treated as specifying
   * a string as one byte per character following the ISO-8859-1 standard.
   * If a zero byte is encountered in the sequence, the string is terminated.
   * Otherwise, it is extended out to the maximum length. Regardless of
   * how many characters are read, the file position is always adjusted
   * forward by the maximum length.
   *
   * @param builder The StringBuilder to which data is appended; if
   * the builder already contains text, then it will not be clear out
   * before the data is written.
   * @param maximumLength Maximum number of bytes to be read from file.
   * @return Number of valid characters extracted from the file before
   * a null was encountered.
   * @throws IOException In the event of an unrecoverable I/O condition.
   */
  public int readAscii(final StringBuilder builder, final int maximumLength)
    throws IOException {
    if (maximumLength <= 0) {
      return 0;
    }
    int k = 0;
    while (k < maximumLength) {
      k++;
      int b = this.readByte();
      if (b == 0) {
        if (k < maximumLength) {
          skipBytes(maximumLength - k);
        }
        return k;
      }
      builder.append((char) (b & 0xff));
    }
    return maximumLength;
  }


  /**
   * Reads one input byte and returns <code>true</code> if that byte is nonzero,
   * <code>false</code> if that byte is zero. This method may be used to
   * read the byte written by the writeBoolean method of interface DataOutput.
   *
   * @return The boolean value read from the file.
   * @throws IOException In the event of an unrecoverable I/O condition.
   */

  public boolean readBoolean() throws IOException {
    prepareBufferForRead(1);
    byte test = buffer.get();
    return test != 0;
  }

  /**
   * Reads and returns one input byte. The byte is treated as a signed
   * value in the range -128 through 127, inclusive. This method may be used
   * to read the byte written by the writeByte method of interface DataOutput.
   *
   * @return The 8-bit value read.
   * @throws IOException In the event of an unrecoverable I/O condition.
   */

  public byte readByte() throws IOException {
    prepareBufferForRead(1);
    return buffer.get();
  }


  /**
   * Reads one input byte and returns an integer value
   * in the range 0 through 255.
   *
   * @return An integer primitive based on the unsigned value of a byte
   * read from the source file.
   * @throws IOException In the event of an unrecoverable I/O condition.
   */

  public int readUnsignedByte() throws IOException {
    prepareBufferForRead(1);
    return (int) (buffer.get()) & 0x000000ff;
  }



  /**
   * Reads 4 bytes given in little-endian order and and returns
   * a Java long primitive given values in the range 0 through 4294967295.
   *
   * @throws IOException In the event of an unrecoverable I/O condition.
   * @return a Java long correctly interpreted from the unsigned integer
   * (4-byte) value stored in the data file.
   */
  public long readUnsignedInt() throws IOException {
    prepareBufferForRead(4);
    return  (long)(buffer.getInt())&0xffffffffL;
  }

  /**
   * Read two bytes  and returns a
   * Java int primitive.
   *
   * @return A Java integer primitive in the range 0 to 65535.
   * @throws IOException In the event of an unrecoverable I/O condition.
   */
  public int readUnsignedShort() throws IOException {
    prepareBufferForRead(2);
    return (int)(buffer.getShort())&0xffff;
  }

  /**
   * Read 4 bytes and return Java integer.
   *
   * @return A Java integer primitive.
   * @throws IOException In the event of an unrecoverable I/O condition.
   */
  public int readInt() throws IOException {
      prepareBufferForRead(4);
    return buffer.getInt();
  }

  /**
   * Read 8 bytes from the file and returns a Java double.
   *
   * @return A Java double primitive.
   * @throws IOException In the event of an unrecoverable I/O condition.
   *
   */
  public double readDouble() throws IOException {
   prepareBufferForRead(8);
    return buffer.getDouble();
  }

  /**
   * Reads 4 bytes from the file and returns a Java float.
   *
   * @return A Java float primitive.
   * @throws IOException In the event of an unrecoverable I/O condition.
   */
  public float readFloat() throws IOException {
     prepareBufferForRead(4);
    return buffer.getFloat();
  }

  /**
   * Read 8 bytes from the file and returns a java long
   *
   * @return AJava long primitive
   * @throws IOException In the event of an unrecoverable I/O condition.
   */
  public long readLong() throws IOException {
        prepareBufferForRead(8);
    return buffer.getLong();
  }

  /**
   * Reads two bytes from the file treating them as being in little-endian order
   * and returns a short.
   *
   * @return A Java short primitive in the range -32768 to 32767.
   * @throws IOException In the event of an unrecoverable I/O condition.
   */
  public short readShort() throws IOException {
     prepareBufferForRead(2);
    return buffer.getShort();
  }

  /**
   * Sets the virtual file-pointer position measured from the
   * beginning of the file.
   *
   * @param position The file position, measured in bytes from the
   * beginning of the file at which to set the virtual file position.
   * @throws IOException In the event of an unrecoverable I/O condition.
   */
  public void seek(long position) throws IOException {

    if (bufferContainsData) {
      int bufferPosition = buffer.position();
      int bufferRemaining = buffer.remaining();
      long pos0 = raFilePos - bufferPosition;
      long pos1 = raFilePos + bufferRemaining - 1;
      if (pos0 <= position && position <= pos1) {
        raFilePos = position;
        long bufferPos = position - pos0;
        buffer.position((int) bufferPos);
        return;
      }
    }

    bufferContainsData = false;
    buffer.clear();
    raFilePos = position;
  }

  /**
   * Makes an attempt to advance the virtual file position by <code>n</code>
   * bytes in order to match the functionality of the DataInput interface.
   *
   * @param n The number of bytes byte which to advance the file position
   * @return the number of bytes skipped.
   * @throws IOException In the event of an unrecoverable I/O condition.
   */

  public int skipBytes(int n) throws IOException {

    raFilePos += n;
    if (bufferContainsData) {
      int remaining = buffer.remaining();
      if (n < remaining) {
        int position = buffer.position();
        buffer.position(position + n);
      } else {
        buffer.clear();
        bufferContainsData = false;
      }
    }
    return n;
  }

}
