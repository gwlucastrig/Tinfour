About module "gis"

This module is intended to support utilities related to Geographic Information Systems (GIS).
Although Tinfour is not intended to be a GIS system, the GIS community is a good place
to find useful test and example data sets.   This package provides modules that allow
Tinfour applications to access those data sets.

Currently, support is included for 
	Lidar:       LAS and LAZ files as a source of input vertices
	Shapefiles:  Shapefiles as a source of input vertices or constraint boundaries
	
Dependencies
  As usual, the Tinfour coding practice is to avoid external dependencies as
much as possible.  However, the lidar-related modules require an external
dependency named "laszip4j" to read LAZ (zipped LAS) files.
  The Shapefile modules have no external dependency at this time.
 
Map Projections and Datums (GIS-specific topics)
  At present, there is no support for map projections and datums.  These
GIS-specific are outside the scope of Tinfour and would add many, many
external dependencies.  This design choice is a disadvantage from a
GIS perspective because it does not allow Tinfour to perform coordinate
transformations when loading data.  