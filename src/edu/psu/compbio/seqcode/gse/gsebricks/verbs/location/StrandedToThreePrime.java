package edu.psu.compbio.seqcode.gse.gsebricks.verbs.location;

import edu.psu.compbio.seqcode.genome.location.Region;
import edu.psu.compbio.seqcode.genome.location.StrandedRegion;
import edu.psu.compbio.seqcode.gse.gsebricks.verbs.Mapper;

/**
 * Maps a StrandedRegion to its three prime end.
 */
public class StrandedToThreePrime<X extends StrandedRegion> implements Mapper<X,Region> {

    private int upstream, downstream;
    /**
     * @param up how many bases upstream of the 3' end to return in the output
     * @param down how many baes downstream of the 3' end ot return in the output.
     */
    public StrandedToThreePrime(int up, int down) {
		upstream = up;
		downstream = down;
    }
    public Region execute(X a) {
        int start, stop;
        switch(a.getStrand()) { 
        case '+':
            start = a.getEnd() - upstream;
            stop = a.getEnd() + downstream;
            return new Region(a.getGenome(), a.getChrom(), start, stop);
        case '-':
            start = a.getStart() - downstream;
            stop = a.getStart() + upstream;
            return new Region(a.getGenome(), a.getChrom(), start, stop);
        default:
            return a;
        }
    }

}