/*
 * Copyright 2014 Gary W. Lucas.
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
/**
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date Name Description
 * ------ --------- -------------------------------------------------
 * 08/2014 G. Lucas Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */
package tinfour.gwr;

/**
 * An enumeration defining model selections for interpolation.
 */
public enum SurfaceModel {
    /**
     *  z(x,y) = b0 + b1*x + b2*y
     */
    Planar(3, "PLN"),

    /**
     *  z(x,y) = b0 + b1*x + b2*y + b3*x*y
     */
    PlanarWithCrossTerms(4,"PWC"),

    /**
     * z(x, y) = b0 + b1*x + b2*y + b3*x^2 +b4*y^2
     */
    Quadratic(5, "QDR"),

    /**
     * z(x, y) = b0 + b1*x + b2*y + b3*x^2 +b4*y^2 + b5*x*y
     */
    QuadraticWithCrossTerms(6, "QWC"),

    /**
     * z(x, y) = b0 + b1*x + b2*y + b3*x^2 +b4*y^2
     * + b5*x^3 + b6*y^3.
     */
    Cubic(7,"CUB"),

    /**
     * z(x, y) = b0 + b1*x + b2*y + b3*x^2 +b4*y^2 + b5*x*y
     * + b6*x^2*y + b7*x*y^2 + b8*x^3 + b9*y^3.
     */
    CubicWithCrossTerms(10, "CWC");


    final int nCoefficients;
    final String abbreviation;

    SurfaceModel(int nCoefficients, String abbreviation) {
        this.nCoefficients = nCoefficients;
        this.abbreviation = abbreviation;
    }

    public int getCoefficientCount() {
        return nCoefficients;
    }

    public int getIndependentVariableCount() {
        return nCoefficients - 1;
    }

    /**
     * Gets a three letter abbreviation indicating the model type,
     * intended for tabular data output.
     * @return a three letter abbreviation.
     */
    public String getAbbreviation(){
      return abbreviation;
    }

}
