/* --------------------------------------------------------------------
 * Copyright 2016 Gary W. Lucas.
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
 * 02/2016  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */

package tinfour.las;

public class LasVariableLengthRecord {

  final long offset;
  final String userId;
  final int recordId;
  final int recordLength; // not including header
  final String description;

  LasVariableLengthRecord(
    long offset,
    String userID,
    int recordID,
    int recordLength,
    String description) {
    this.offset = offset;
    this.userId = userID;
    this.recordId = recordID;
    this.recordLength = recordLength;
    this.description = description;
  }

  /**
   * Gets the file position for the start of the data associated with
   * this record. The data is stored as a series of bytes of the
   * length given by the record-length element.
   *
   * @return a positive long integer.
   */
  public long getFilePosition() {
    return offset;
  }

  /**
   * Gets the user ID for the record.
   *
   * @return a valid, potentially empty string.
   */
  public String getUserId() {
    return userId;
  }

  /**
   * Gets the numerical ID associated with the record.
   *
   * @return a positive value in the range of an unsigned short (two-byte)
   * integer.
   */
  public int getRecordId() {
    return recordId;
  }

  /**
   * Gets the length, in bytes, of the data associated with the record.
   *
   * @return a positive value in the range of an unsigned short (two-byte)
   * integer.
   */
  public int getRecordLength() {
    return recordLength;
  }

  /**
   * Get the description text associated with the record.
   *
   * @return a valid, potentially empty, string.
   */
  public String getDescription() {
    return description;
  }

  @Override
  public String toString(){
    return String.format(
      "Variable Length Record: %6d  %6d    %-16s  %s",
      recordId, recordLength, userId, description);
  }

}
