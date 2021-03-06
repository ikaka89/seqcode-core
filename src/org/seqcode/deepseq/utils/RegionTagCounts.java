package org.seqcode.deepseq.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.seqcode.data.io.RegionFileUtilities;
import org.seqcode.deepseq.StrandedBaseCount;
import org.seqcode.deepseq.experiments.ControlledExperiment;
import org.seqcode.deepseq.experiments.ExperimentCondition;
import org.seqcode.deepseq.experiments.ExperimentManager;
import org.seqcode.deepseq.experiments.ExptConfig;
import org.seqcode.genome.GenomeConfig;
import org.seqcode.genome.location.NamedRegion;
import org.seqcode.genome.location.Region;
import org.seqcode.gsebricks.verbs.location.ChromRegionIterator;
import org.seqcode.gseutils.ArgParser;
import org.seqcode.gseutils.Args;


/**
 * This class aims to count tags overlapping a set of regions. 
 *   
 * @author mahony
 *
 */
public class RegionTagCounts {

	private GenomeConfig gConfig;
	private ExptConfig eConfig;
	private List<Region> testRegs;
	private ExperimentManager manager=null;
	private String outName = "out";
	private boolean totalTagNorm=false; //normalize to total tags
	private boolean sigPropNorm=false; //normalize to signal proportion
	private boolean scaleToPercentile=false; //scale to Xth percentile of observed counts
	private double scalePercentile=0.99; //Percentile to scale to
	
	public RegionTagCounts(GenomeConfig gcon, ExptConfig econ, List<Region> regs, String out){
		gConfig = gcon;
		eConfig = econ;
		testRegs = regs;
		manager = new ExperimentManager(eConfig);
		outName = out;
	}
	
	public void setTotalTagNorm(boolean n){totalTagNorm=n;}
	public void setSigPropNorm(boolean n){sigPropNorm=n;}
	public void setPercentileScale(double perc){
		if(perc>0 && perc<=1){
			scalePercentile=perc;
			scaleToPercentile=true;
		}
	}
	
	
	public void execute(){
		Map<Region, Double[]> regCounts = new HashMap<Region, Double[]>();
		for(Region r : testRegs){
			regCounts.put(r, new Double[manager.getReplicates().size()]);
			for(int i=0; i<manager.getReplicates().size(); i++)
			    regCounts.get(r)[i] = 0.0;
		}
		
		for(ExperimentCondition c : manager.getConditions()){
			for(ControlledExperiment rep : c.getReplicates()){
				System.err.println("Condition "+c.getName()+":\tRep "+rep.getName());
				double scaling = rep.getControlScaling();
				double sigStrength = 1-(scaling/(rep.getSignal().getHitCount()/rep.getControl().getHitCount()));
				double sigCount = sigStrength * rep.getSignal().getHitCount();
				
				ArrayList<Double> allRepCounts= new ArrayList<Double>();
				ChromRegionIterator chroms = new ChromRegionIterator(gConfig.getGenome());
				while(chroms.hasNext()){
					NamedRegion currentRegion = chroms.next();
					
					//Split the job up into chunks of 100Mbp
					for(int x=currentRegion.getStart(); x<=currentRegion.getEnd(); x+=100000000){
						int y = x+100000000; 
						if(y>currentRegion.getEnd()){y=currentRegion.getEnd();}
						Region currSubRegion = new Region(gConfig.getGenome(), currentRegion.getChrom(), x, y);
						
						List<StrandedBaseCount> hits = rep.getSignal().getBases(currSubRegion);
	                    double stackedTagStarts[] = makeTagStartLandscape(hits, currSubRegion);
	                    
	                    //Get coverage of points that lie within the current region
	                    for(Region r : testRegs){
	                    	if(currSubRegion.contains(r)){
		                    
								int offsetStart = inBounds(r.getStart()-currSubRegion.getStart(), 0, currSubRegion.getWidth()-1);
								int offsetEnd =inBounds(r.getEnd()-currSubRegion.getStart(), 0, currSubRegion.getWidth()-1);
								double sum=0;
								for(int o=offsetStart; o<=offsetEnd; o++){
									sum+=stackedTagStarts[o];
								}
								
								double count = 0;
								if(totalTagNorm)
									count=sum/rep.getSignal().getHitCount();
								else if(sigPropNorm)
									count=sum/sigCount;
								else
									count=sum;
								
								regCounts.get(r)[rep.getIndex()]=count;
								allRepCounts.add(count);
	                    	}
						}
					}
				}	
				if(scaleToPercentile){
					Collections.sort(allRepCounts);
					double ceiling = allRepCounts.get((int)(allRepCounts.size()*scalePercentile)); 
					for(Region r : testRegs){
						if(regCounts.containsKey(r)){
							if(regCounts.get(r)[rep.getIndex()]>ceiling)
								regCounts.get(r)[rep.getIndex()] = 1.0;
							else
								regCounts.get(r)[rep.getIndex()] /=ceiling;
						}
					}
				}
			}
		}
	
		//Write to file
		try {
			FileWriter fw = new FileWriter(outName+".region-counts.txt");
			fw.write("Coord");
			for(ExperimentCondition c : manager.getConditions())
				for(ControlledExperiment rep : c.getReplicates())
					fw.write("\t"+rep.getName());
			fw.write("\n");
			for(Region r : testRegs){
				fw.write(r.getLocationString());
				for(ExperimentCondition c : manager.getConditions())
					for(ControlledExperiment rep : c.getReplicates())
						fw.write("\t"+String.format("%f", regCounts.get(r)[rep.getIndex()]));
				fw.write("\n");
			}
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	
	}
	
	protected double[] makeTagStartLandscape(List<StrandedBaseCount> hits, Region currReg){
		double[] counts = new double[(int)currReg.getWidth()+1];
        for(int i=0; i<=currReg.getWidth(); i++){counts[i]=0;}
        for(StrandedBaseCount r : hits){
        	if(r.getCoordinate()>=currReg.getStart() && r.getCoordinate()<=currReg.getEnd()){
	        	int offset=inBounds(r.getCoordinate()-currReg.getStart(),0,currReg.getWidth());
	        	counts[offset]+=r.getCount();
        	}
        }
        return(counts);
    }
	//keep the number in bounds
	protected final double inBounds(double x, double min, double max){
		if(x<min){return min;}
		if(x>max){return max;}
		return x;
	}
	protected final int inBounds(int x, int min, int max){
		if(x<min){return min;}
		if(x>max){return max;}
		return x;
	}
	
	public void close(){
		if(manager !=null)
			manager.close();
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ArgParser ap = new ArgParser(args);
		if(args.length==0 || ap.hasKey("h")){
			System.err.println("RegionTagCounts:");
			System.err.println("Genome:" +
					"\t--species <Species;Genome>\n" +
					"\tOR\n" +
					"\t--geninfo <genome info file> AND --seq <fasta seq directory>\n" +
					"Coverage Testing:\n" +
					"\t--reg <region coords>\n" +
					"\t--win <win size bp>\n" +
					"\t--out <output file root>\n" +
					"\t--signormcounts [flag to normalize counts by inferred signal proportion]\n" +
					"\t--totalnormcounts [flag to normalize counts by total tags]\n" +
					"\t--percscale <fraction> : scale counts to this percentile of observed counts\n"
					);
		}else{
			
			GenomeConfig gcon = new GenomeConfig(args);
			ExptConfig econ = new ExptConfig(gcon.getGenome(), args);
		
			Integer win = Args.parseInteger(args, "win", -1);
			List<Region> testSites = new ArrayList<Region>();
			Collection<String> regFiles = Args.parseStrings(args, "reg");
			for(String rf : regFiles)
				testSites.addAll(RegionFileUtilities.loadRegionsFromFile(rf, gcon.getGenome(), win));
			String outName = Args.parseString(args, "out", "out");
			boolean signorm = Args.parseFlags(args).contains("signormcounts");
			boolean totnorm = Args.parseFlags(args).contains("totalnormcounts");
			double scalePercentile = Args.parseDouble(args, "percscale", -1); 
			
			RegionTagCounts rct = new RegionTagCounts(gcon, econ, testSites, outName);
			rct.setSigPropNorm(signorm); rct.setTotalTagNorm(totnorm); rct.setPercentileScale(scalePercentile);
			rct.execute();
			rct.close();
		}	
		
	}

}
