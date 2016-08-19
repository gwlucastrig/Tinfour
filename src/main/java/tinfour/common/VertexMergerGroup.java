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

/*
 * -----------------------------------------------------------------------
 *
 * Revision History:
 * Date     Name         Description
 * ------   ---------    -------------------------------------------------
 * 03/2014  G. Lucas     Created
 *
 * Notes:
 *
 * -----------------------------------------------------------------------
 */


package tinfour.common;

import java.util.ArrayList;
import java.util.List;


/**
 *  A synthetic vertex used to handle cases when multiple vertices
 * occupy coincident locations.
 */
public class VertexMergerGroup extends Vertex {

    /**
     * Specifies a rule for determining a z value based on the collection
     * of coincident vertices.  The selection of rules may be made to
     * reflect lidar return type (i.e. MaxValue for first-return processing),
     * or classification (min or average value used for ground-classified
     * vertices).
     */
    public enum ResolutionRule {
        /** use the minimum z value */
        MinValue,
        /** use the mean z value */
        MeanValue,
        /** use the maximum z value */
        MaxValue
    };

    List<Vertex> list = new ArrayList<>();

    ResolutionRule rule = ResolutionRule.MeanValue;

    double zRule;


    /**
     * Constructs a coincident vertex using the specified vertex
     * for initialization.
     * @param firstVertex a valid instance
     */
    public VertexMergerGroup(Vertex firstVertex){
        super(
          firstVertex.x,
          firstVertex.y,
          firstVertex.z,
          firstVertex.getIndex());
        zRule = z;
        list.add(firstVertex);
    }

    /**
     * Add a new vertex to the coincident collection. Recompute z value using
     * current rule.
     *
     * @param v a valid, unique instance
     * @return true if added to collection; otherwise false
     */
    public boolean addVertex(Vertex v)
    {
        if(v instanceof VertexMergerGroup){
            // put the content of the added group into
            // the existing group.  it's the only way to
            // ensure that the resolution rules behave properly.
            // note that logic assumes that in general the size
            // of the groups is rather small and so performs the
            // linear search for the contains() method.
            VertexMergerGroup g = (VertexMergerGroup)v;
            boolean status = false;
            for(Vertex a: g.list){
                if(!list.contains(a)){
                    list.add(a);
                    status = true;
                }
            }
            return status;
        }
        if(list.contains(v)){
            return false;
        }
        boolean status = list.add(v);
        applyRule();
        return status;
    }

    /**
     * Removes the specified vertex from the group. If the vertex is
     * not currently a member of the group, this operation will be ignored.
     * @param v the vertex to be removed.
     * @return true if the vertex was a member of the group and was removed;
     * otherwise, false.
     */
    public boolean removeVertex(Vertex v){
        return list.remove(v);
    }

    private void applyRule(){
         switch (rule) {
            case MeanValue:
                double zSum = 0;
                for (Vertex m : list) {
                    zSum += m.z;
                }
                zRule = zSum / list.size();
                break;
            case MinValue:
                double zMin = Double.POSITIVE_INFINITY;
                for (Vertex m : list) {
                    if (Double.isNaN(m.z)) {
                        zMin = Double.NaN;
                        break;
                    }
                    if (m.z < zMin) {
                        zMin = m.z;
                    }
                }
                zRule = zMin;
                break;
            case MaxValue:
                double zMax = Double.NEGATIVE_INFINITY;
                for (Vertex m : list) {
                    if (Double.isNaN(m.z)) {
                        zMax = Double.NaN;
                        break;
                    }
                    if (m.z > zMax) {
                        zMax = m.z;
                    }
                }
                zRule = zMax;
                break;
            default:

        }
    }


    /**
     * Sets the rule for resolving coincident vertices; recalculates
     * value for vertex if necessary
     * @param rule a valid member of the enumeration
     */
    public void setResolutionRule(ResolutionRule rule){
        if(rule==null || rule == this.rule){
           return;
        }
        this.rule = rule;
        applyRule();
    }

    /**
     * Get the z value associated with the vertex and the merging rules.
     * If the vertex is null, the return value for this method is
     * Double.NaN ("not a number").
     *
     * @return a floating point value or Double.NaN if z value is null.
     */
    @Override
    public double getZ() {
        return zRule;
    }

    /**
     * Gets an array of the coincident vertices.  Each invocation of this method
     * results in a new instance of the array.
     * @return a valid array of size 1 or greater.
     */
    public Vertex []getVertices(){
        return list.toArray(new Vertex[list.size()]);
    }

    /**
     * Gets the number of vertices grouped together in the collection
     * @return normally, a value of 1 or greater; but if the last vertex
     * in the group has been removed, a value of zero.
     */
    public int getSize(){
        return list.size();
    }

    /**
     * Indicates whether the group contains the specified vertex
     * @param v a valid vertex
     * @return true if the vertex is a member of the group; otherwise, false.
     */
    public boolean contains(Vertex v){
      return list.contains(v);
    }
}