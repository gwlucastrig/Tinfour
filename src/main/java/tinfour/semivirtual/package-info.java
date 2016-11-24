
/**
 * Provides classes and interfaces for creating a Triangulated Irregular
 * Network (TIN) based on the Delaunay Triangulation specification
 * and using a semi-virtual representation of edges to reduce memory
 * requirements. While all data is still kept in core, the definitions
 * of edges is maintained as data primitives and only instantiated as objects
 * on a short-persistence, as-needed basis.
 */
package tinfour.semivirtual;
