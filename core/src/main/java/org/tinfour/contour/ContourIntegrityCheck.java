/* --------------------------------------------------------------------
 * Copyright (C) 2021  Gary W. Lucas.
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
 * 04/2021  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */



package org.tinfour.contour;

import java.util.List;
import org.tinfour.contour.Contour.ContourType;
import org.tinfour.contour.ContourRegion.ContourRegionType;

/**
 * Provides methods for checking the correctness of implementation for
 * Tinfour contour building operations. This class can detect many problematic
 * results, but is by no means comprehensive. It is intended for testing,
 * diagnostic, and debugging purposes.
 */
public class ContourIntegrityCheck {

  private final ContourBuilderForTin builder;
  String message = "No inspection was performed";

  public ContourIntegrityCheck(ContourBuilderForTin builder){
    if(builder==null){
      throw new IllegalArgumentException(
        "A null reference for the builder is not supported");
    }
    this.builder = builder;
  }

  /**
   * Inspects the content of the builder to verify that it was correctly
   * constructed.  This method can detect many problematic geometries,
   * but is by no means comprehensive.  It is intended for testing, diagnostic,
   * and debugging purposes.
   * <p>
   * If the builder fails inspection, a message giving an explanation
   * can be obtained through a call to getMessage().
   * @return true if the builder passes inspection; otherwise, false.
   */
  public boolean inspect(){
    List<ContourRegion>regions = builder.getRegions();
    if(!regions.isEmpty()){
      if(!checkRegionAreas(regions)){
        return false;
      }
        if(!checkContourTraversal(regions)){
        return false;
      }
    }

    message = "Inspection passed";
    return true;
  }

  /**
   * Gets a status message indicating the results of the most recent inspection.
   * @return a valid string.
   */
  public String getMessage(){
   return message;
  }

  private boolean checkRegionAreas(List<ContourRegion>regions){

    double []xy = builder.getEnvelope();
    double envelopeArea = 0;
    double xOffset = xy[0];
    double yOffset = xy[1];
    double x0 = 0;
    double y0 = 0;
    for(int i=2; i<xy.length-2; i+=2){
      double x1 = xy[i]-xOffset;
      double y1 = xy[i+1]-yOffset;
      envelopeArea+=x0*y1-x1*y0;
      x0 = x1;
      y0 = y1;
    }
    envelopeArea/=2.0;

    double aSum=0;
    double adjustedSum = 0;
    for(ContourRegion r: regions){
      ContourRegionType rt = r.getContourRegionType();
      if(rt == ContourRegionType.Perimeter){
        aSum+=r.getArea();
      }
      adjustedSum+=r.getAdjustedArea();
    }

    // Check the sum of the areas of the primary contours
    // (those that are constructed using the perimeter).
    // This test verifies that all the primary contours were
    // correctly identified.
    double areaDiff = aSum-envelopeArea;
    if(Math.abs(areaDiff)>envelopeArea*1.0e-6){
       message = "The area of the primary contour regions "
         +"does not match the area of the enclosing envelope";
       return false;
    }

    // Check the adjusted areas.  This test provides an indication
    // of whether the nesting of areas was correctly identified.
    areaDiff = adjustedSum - envelopeArea;
    if (Math.abs(areaDiff) > envelopeArea * 1.0e-6) {
      message = "The sum of the adjusted areas for all contour regions "
        + "does not match the area of the enclosing envelope";
      return false;
    }
    return true;
  }

  private boolean checkContourTraversal(List<ContourRegion> regions) {
    for (ContourRegion r : regions) {
      ContourRegionType rt = r.getContourRegionType();
      if (rt != ContourRegionType.Perimeter) {
        continue;
      }
      for(ContourRegionMember member: r.memberList){
        Contour contour = member.contour;
        if(contour.getContourType()==ContourType.Interior){
          if(!contour.traversedBackward && contour.traversedForward){
            message = "Interior contour not traversed in both directions,"
              +" contour ID "+contour.getContourId();
            return false;
          }
        }
      }

    }
    return true;

  }
}
