About the module named "Analysis"

This module is intended to support analysis operations
that operate over a surface or delaunay triangulation.
Many, but not all, of the operations depend on the
Tinfour Core packages. 



Geographically Weighted Regression
Geographically Weighted Regression would more properly be named
"Geometrically Weighted Regression" since it is not intrinsically
related to Geographic applications.  It's a regression technique that
weights samples differently based on their distance from the
query point. This is an experimental module and subject to change in the future.
 
	
Dependencies
  This package introduces a dependency to the Apache Commons Math library.
Although I have a great deal of respect for the Apache library, it 
may not be the best choice here.  The GWR logic requires only a small
set of linear-algebra operations (and the T-distribution), while the 
Apache Math library covers a broad range of topics.  Thus, this design
decision is subject to change in the future.