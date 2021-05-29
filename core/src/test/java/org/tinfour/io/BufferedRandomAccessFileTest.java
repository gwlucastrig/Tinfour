/* --------------------------------------------------------------------
 *
 * The MIT License
 *
 * Copyright (C) 2019  Gary W. Lucas.

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * ---------------------------------------------------------------------
 */

 /*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 02/2020  G. Lucas     Created  
 *
 * Notes:
 * -----------------------------------------------------------------------
 */
package org.tinfour.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Provides round-trip tests for reading and writing data in various
 * formats to a BufferedRandomAccessFile. Tests both the Java-compatible
 * (Big Endian) and the Little Endian methods.
 * <p>
 * Verifies that this class is compatible with the Java standard
 * DataInput and DataOutput implementations:
 * <ol>
 * <li>Write to a BRAF file and then read it using DataInputStream.</li>
 * <li>Write to a DataOutputStream and then read it using BRAF</li>
 * </ol>
 * <p>
 * At this time, tests for random positioning, seeks, etc. are not implemented.
 */
public class BufferedRandomAccessFileTest {

  @TempDir
  Path tempDir;

  public BufferedRandomAccessFileTest() {
  }

  @BeforeAll
  public static void setUpClass() {
  }

  @AfterAll
  public static void tearDownClass() {
  }

  @BeforeEach
  public void setUp() {
  }

  @AfterEach
  public void tearDown() {
  }


  static boolean qBoolean = true;
  static int    qByte   = 0x01;
  static int    qShort  = 0x0102;
  static int    qInt    = 0x03040506;
  static long   qLong   = 0x0708090a0b0c0d0eL;
  static float  qFloat  = (float) Math.PI;
  static double qDouble = (double) Math.E;
  static String qUTF    = "Good night and \u041f\u0440\u043e\u0449\u0430\u0439";  // includes cyrillic characters.
  static String qASCII  = "abcd";
  
  static byte []qByteArray;
  static {
    qByteArray = new byte[256];
    for(int i=0; i<256; i++){
      qByteArray[i] = (byte)i;
    }
  }

  private void writeToDataOutput(DataOutput output) throws IOException {
    output.writeBoolean(qBoolean);
    output.writeByte(qByte);
    output.writeShort(qShort);
    output.writeInt(qInt);
    output.writeLong(qLong);
    output.writeFloat(qFloat);
    output.writeDouble(qDouble);
    output.writeUTF(qUTF);
    output.write(qByteArray);
    output.write(qByteArray, 10, 15);
	output.writeChars(qUTF);  // cyrillic characters are still only 2 bytes long
  }

  private void testByReadingDataInput(DataInput input) throws IOException {
	assertEquals(input.readBoolean(), qBoolean, "Mismatched boolean");
	assertEquals(input.readByte(),    qByte,    "Mismatched byte");
    assertEquals(input.readShort(),   qShort,   "Mismatched short");
    assertEquals(input.readInt(),     qInt,     "Mismatched int");
    assertEquals(input.readLong(),    qLong,    "Mismatched long");
    assertEquals(input.readFloat(),   qFloat,   "Mismatched float");
    assertEquals(input.readDouble(),  qDouble,  "Mismatched e");
    assertEquals(input.readUTF(),     qUTF,     "Miscmatched UTF");
    
    byte []scratch = new byte[qByteArray.length];
    input.readFully(scratch);
    for(int i=0; i<qByteArray.length; i++){
      assertEquals(qByteArray[i], scratch[i], "Mismatched byte array");
    }
    input.readFully(scratch, 10, 15);
    for(int i=0; i<15; i++){
      assertEquals(qByteArray[i+10], scratch[i+10], "Mismatched byte array offset/length");
    }
	for(int i=0; i<qUTF.length(); i++){
		assertEquals(input.readChar(), qUTF.charAt(i), "Missmatched read char call");
	}
 
  }


  private void writeToLeDataOutput(BufferedRandomAccessFile output) throws IOException {
    output.leWriteShort(qShort);
    output.leWriteInt(qInt);
    output.leWriteLong(qLong);
    output.leWriteFloat(qFloat);
    output.leWriteDouble(qDouble); 
  }

  private void testByReadingLeDataInput(BufferedRandomAccessFile input) throws IOException {
    assertEquals(input.leReadShort(), qShort, "Mismatched short");
    assertEquals(input.leReadInt(), qInt, "Mismatched int");
    assertEquals(input.leReadLong(), qLong, "Mismatched long");
    assertEquals(input.leReadFloat(), qFloat, "Mismatched float");
    assertEquals(input.leReadDouble(), qDouble, "Mismatched e");
  }


  @Test
  public void testRoundTrip() throws Exception {
    File tempFolder = tempDir.toFile();
    File tempFile = new File(tempFolder, "Test.data");
    try(BufferedRandomAccessFile braf
            = new BufferedRandomAccessFile(tempFile, "rw")){

		writeToDataOutput(braf);
		// now write data using methods unique to BufferedRandomAccessFile
		braf.seek(0);
		testByReadingDataInput(braf);
	
		// now test using methods unique to BufferedRandomAccessFile	
		long filePos = braf.getFilePosition();
		braf.writeASCII(qASCII, 16);
		braf.seek(filePos);
		assertEquals(braf.readASCII(16), qASCII, "Mismatched ASCII");
		
		braf.seek(0);
		writeToLeDataOutput(braf);
		braf.seek(0);
		testByReadingLeDataInput(braf);
	}
	
	// Now test against Java's implementations to verify
	// that BRAF correctly implements DataInput and DataOutput methods.
	try{
		FileOutputStream fos = new FileOutputStream(tempFile);
		BufferedOutputStream bos = new BufferedOutputStream(fos);
		DataOutputStream dos = new DataOutputStream(bos);
		writeToDataOutput(dos);
		dos.flush();
		dos.close();
		bos.close();
		fos.close();
		BufferedRandomAccessFile braf
            = new BufferedRandomAccessFile(tempFile, "rw");
		testByReadingDataInput(braf);
		
		braf.seek(0);
		writeToDataOutput(braf);
		braf.flush();
		braf.close();
		
		FileInputStream fins = new FileInputStream(tempFile);
		BufferedInputStream bins = new BufferedInputStream(fins);
		DataInputStream dins = new DataInputStream(bins);
		testByReadingDataInput(dins);
		fins.close();
		
		boolean status = tempFile.delete();
		if(!status){
			fail("Couldn't delete file");
		}
 
	}catch(IOException ioex){
		fail("IOException while performing tests: "+ioex.getMessage());
	}
  }
  
}
