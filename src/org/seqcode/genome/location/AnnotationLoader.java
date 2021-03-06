package org.seqcode.genome.location;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.seqcode.data.io.parsing.TranscriptFileExpander;
import org.seqcode.genome.Genome;
import org.seqcode.gsebricks.verbs.Expander;
import org.seqcode.gsebricks.verbs.location.NamedGeneratorFactory;
import org.seqcode.gsebricks.verbs.location.NamedStrandedGeneratorFactory;
import org.seqcode.gsebricks.verbs.location.NamedTypedGeneratorFactory;
import org.seqcode.gsebricks.verbs.location.RefGeneGeneratorFactory;
import org.seqcode.gsebricks.verbs.location.RepeatMaskedGenerator;


public class AnnotationLoader {

	protected Expander<Region, ? extends Region> annotExpander;
	protected String sourceName;
	protected int maxAnnotDist=50000;
	protected boolean overlapOnly=false;
	
	public AnnotationLoader(Genome gen, String name, String type, int maxAnnotDist, boolean overlapOnly){
		sourceName = name;
		this.maxAnnotDist=maxAnnotDist;
		this.overlapOnly=overlapOnly;
		
		if(type.equals("refGene")){
			annotExpander = (new RefGeneGeneratorFactory()).getExpander(gen);
		}else if(type.equals("namedRegion")){
			annotExpander = (new NamedGeneratorFactory(sourceName)).getExpander(gen);
		}else if (type.equals("namedStrandedRegions")){
			annotExpander = (new NamedStrandedGeneratorFactory(sourceName)).getExpander(gen);
		}else if (type.equals("namedTypedRegions")){
			annotExpander = (new NamedTypedGeneratorFactory(sourceName)).getExpander(gen);
		}else if (type.equals("repeatMasker")){
			annotExpander = new RepeatMaskedGenerator(gen);
		}else if (type.equals("file")){
			annotExpander = new TranscriptFileExpander(gen, name);
		}
	}

	public Collection<Region> getAnnotations(Region coords){
		ArrayList<Region> results = new ArrayList<Region>();
		Region query;
		if (overlapOnly) {
            query = coords;
        } else {
            query = coords.expand(maxAnnotDist, maxAnnotDist);
        }
        Iterator<? extends Region> iter = annotExpander.execute(query);
        while (iter.hasNext()) {
            results.add(iter.next());
        }
        return(results);
    }
	
	/** Return genes if the expander is over genes. 
	 * There is probably a better way of doing this that doesn't rely on instanceof 
	 */ 
	public Collection<Gene> getGenes(Region coords){
		ArrayList<Gene> results = new ArrayList<Gene>();
		Region query;
		if (overlapOnly) {
            query = coords;
        } else {
            query = coords.expand(maxAnnotDist, maxAnnotDist);
        }
        Iterator<? extends Region> iter = annotExpander.execute(query);
        while (iter.hasNext()) {
        	Region r = iter.next();
        	if(r instanceof Gene)
        		results.add((Gene)r);
        }
        return(results);
    }
	/** Return genes if the expander is over genes. 
	 * There is probably a better way of doing this that doesn't rely on instanceof 
	 */ 
	public Collection<Gene> getGenes(Point point){
		ArrayList<Gene> results = new ArrayList<Gene>();
		Region query;
		if (overlapOnly) {
            query = point.expand(2);
        } else {
            query = point.expand(maxAnnotDist);
        }
        Iterator<? extends Region> iter = annotExpander.execute(query);
        while (iter.hasNext()) {
        	Region r = iter.next();
        	if(r instanceof Gene)
        		results.add((Gene)r);
        }
        return(results);
    }
}
