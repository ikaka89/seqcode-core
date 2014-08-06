/*
 * Author: tdanford
 * Date: Aug 12, 2008
 */
package edu.psu.compbio.seqcode.gse.viz.metaprofile;

import edu.psu.compbio.seqcode.genome.location.Point;
import edu.psu.compbio.seqcode.gse.gsebricks.verbs.Filter;

public interface PointProfiler<PointClass extends Point, ProfileClass extends Profile> extends Filter<PointClass,ProfileClass> { 
	public BinningParameters getBinningParameters();
	public void cleanup();
}
