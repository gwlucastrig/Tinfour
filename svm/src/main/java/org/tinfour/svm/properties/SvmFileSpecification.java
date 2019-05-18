/* --------------------------------------------------------------------
 * Copyright (C) 2019  Gary W. Lucas.
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
 * 04/2019  G. Lucas     Created  
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package org.tinfour.svm.properties;

import java.io.File;
import java.util.List;

/**
 *
 */
public class SvmFileSpecification {

  final String key;
  final File file;
  final String field;

  /**
   * A package-scope constructor used to create specifications by the
   * SvmProperties class. The file may be remapped based on the folder
   * specification. However, if the file specification gives an absolute path,
   * the folder argument will be ignored.
   *
   * @param key the properties key that provided the file specification
   * @param list a list of arguments including the file and any auxiliary
   * fields.
   * @param folder a file reference to a folder containing the file; or a null
   * if no folder reference is to be applied.
   *
   */
  SvmFileSpecification(String key, List<String> list, File folder) {
    if (key == null || key.isEmpty()) {
      throw new IllegalArgumentException("Invalid key");
    }
    if (list.isEmpty()) {
      throw new IllegalArgumentException("Empty specification for " + key);
    }
    this.key = key;
    String path = list.get(0);
    File test = new File(path);
    if (test.isAbsolute() || folder == null) {
      file = test;
    } else {
      file = new File(folder, path);
    }

    if (list.size() > 1) {
      field = list.get(1);
    } else {
      field = null;
    }
  }

  public File getFile() {
    return file;
  }

  public String getField() {
    return field;
  }

  @Override
  public String toString() {
    String path = file.getPath();
    return "SvmInput: " + key + "=" + path;
  }
}
