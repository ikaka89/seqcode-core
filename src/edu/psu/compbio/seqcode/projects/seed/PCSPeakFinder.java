package edu.psu.compbio.seqcode.projects.seed;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.psu.compbio.seqcode.deepseq.StrandedBaseCount;
import edu.psu.compbio.seqcode.deepseq.experiments.ExperimentCondition;
import edu.psu.compbio.seqcode.deepseq.experiments.ExperimentManager;
import edu.psu.compbio.seqcode.deepseq.experiments.ExptConfig;
import edu.psu.compbio.seqcode.deepseq.experiments.Sample;
import edu.psu.compbio.seqcode.genome.GenomeConfig;
import edu.psu.compbio.seqcode.genome.location.Region;
import edu.psu.compbio.seqcode.gse.datasets.motifs.WeightMatrix;
import edu.psu.compbio.seqcode.gse.gsebricks.verbs.sequence.SequenceGenerator;
import edu.psu.compbio.seqcode.gse.utils.sequence.SequenceUtils;
import edu.psu.compbio.seqcode.projects.multigps.utilities.Utils;
import edu.psu.compbio.seqcode.projects.seed.DomainFinder.DomainFinderThread;
import edu.psu.compbio.seqcode.projects.seed.FeatureDetection.FeatureDetectionThread;
import edu.psu.compbio.seqcode.projects.seed.PeakFinder.PeakFinderThread;
import edu.psu.compbio.seqcode.projects.seed.SEEDConfig.PeakFindingMethod;
import edu.psu.compbio.seqcode.projects.seed.features.EnrichedFeature;
import edu.psu.compbio.seqcode.projects.seed.features.EnrichedPeakFeature;
import edu.psu.compbio.seqcode.projects.seed.features.Feature;

/**
 * Permanganate-ChIP-seq peak-finder
 * @author mahony
 *
 */
public class PCSPeakFinder extends DomainFinder{
	public static String version = "0.1";
	
	protected int tagSeqWin = 20;
	protected float[][][] tagSeqComposition; //Sequence composition around tag 5' ends; indexed by Sample, then by relative position around tag 5' end, then by base (ACGT)

	public PCSPeakFinder(GenomeConfig gcon, ExptConfig econ, SEEDConfig scon, ExperimentManager man) {
		super(gcon, econ, scon, man);
		
		tagSeqComposition = new float[manager.getSamples().size()][tagSeqWin+1][4];
		for(int s=0; s<manager.getSamples().size(); s++)
			for(int i=0; i<=tagSeqWin; i++)
				for(int j=0; j<4; j++)
					tagSeqComposition[s][i][j]=0;
		
	}
	
	/**
	 * Return the class name
	 */
	public String getProgramName(){
		return "edu.psu.compbio.seqcode.projects.seed.PCSPeakFinder";
	}
	
	/**
	 * Return a thread for this implementation
	 */
	public FeatureDetectionThread getMyThread(List<Region> regs){
		return new PCSPeakFinderThread(regs);
	}
	

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "true");
		System.err.println("Permanganate-ChIP-seq Peak Finder version "+PeakFinder.version+"\n\n");
		
		GenomeConfig gcon = new GenomeConfig(args);
		ExptConfig econ = new ExptConfig(gcon.getGenome(), args);
		SEEDConfig scon = new SEEDConfig(gcon, args);
		
		if(scon.helpWanted()){
			//System.out.println(PeakFinder.getPCSPeakFinderArgs());
			System.err.println(gcon.getArgsList()+
					econ.getArgsList()+
					scon.getArgsList());
		}else{
			ExperimentManager man = new ExperimentManager(econ);
			PCSPeakFinder finder = new PCSPeakFinder(gcon, econ, scon, man);
			System.err.println("\nBeginning peak finding...");
			finder.execute();
			man.close();
		}
	}
	
	/**
	 * Return a help string
	 * @return
	 */
	public static String getPCSPeakFinderArgs() {
		//TODO
		return("TODO");
	}
	
	
	
	/**
	 * Multiple-hypothesis correction. Print the output files. 
	 * Assumes that all peaks have been found.
	 * @return : final features
	 */
	public Map<ExperimentCondition, List<Feature>> postProcess() {
		System.err.println("\nPeak finding complete.");
		
		//Normalize the tag sequence compositions
		for(int s=0; s<manager.getSamples().size(); s++)
			for(int i=0; i<=tagSeqWin; i++){
				float currTot=0;
				for(int j=0; j<4; j++)
					currTot+=tagSeqComposition[s][i][j];
				for(int j=0; j<4; j++)
					tagSeqComposition[s][i][j]/=currTot;
			}
		//Print tag sequence composition motifs
		for(Sample s : manager.getSamples()){
			String imName = sconfig.getOutputParentDir()+File.separator+sconfig.getOutBase()+s.getName()+"-tag-sequence-motif.png";
			String motifLabel = s.getName()+" tag-sequence-motif";
			Utils.printMotifLogo(makeTagSeqCompositionWeightMatrix(s), new File(imName), 75, motifLabel);
		}
		
		
		//Correct domains for multiple testing
		for(ExperimentCondition cond : manager.getConditions()){
			stats.benjaminiHochbergCorrection(features.get(cond));
		}
		
		Map<ExperimentCondition, List<Feature>> signifFeatures = this.filterByScore(features, sconfig.perBinBinomialPThres, true);
       	
		//All domains
		this.printEventsFile(features, ".all.pcspeaks.txt");
        
		//Filtered by q-value
		this.printEventsFile(signifFeatures, ".p"+sconfig.perBinBinomialPThres+".pcspeaks.txt");
		
		//Summarize
		for(ExperimentCondition cond : manager.getConditions())
			System.err.println(cond.getName()+"\t"+features.get(cond).size()+" peaks\t"+signifFeatures.get(cond).size()+" peaks below threshold.");
		
		return features;
	}
	
	protected WeightMatrix makeTagSeqCompositionWeightMatrix(Sample s){
		WeightMatrix matrix = new WeightMatrix(tagSeqWin+1);
	    matrix.setNameVerType(s.getName()+"-tag-sequence-motif", "freq", "CUSTOM");
	    for (int i = 0; i <= tagSeqWin; i++) {
	    	//Assume normalized already
	    	matrix.matrix[i]['A'] = tagSeqComposition[s.getIndex()][i][0];
	    	matrix.matrix[i]['C'] = tagSeqComposition[s.getIndex()][i][1];
			matrix.matrix[i]['G'] = tagSeqComposition[s.getIndex()][i][2];
			matrix.matrix[i]['T'] = tagSeqComposition[s.getIndex()][i][3];
	    }matrix.setLogOdds();
		return matrix;
	}
	
	/**
	 * PeakFinderThread: thread that searches for domains
	 * @author mahony
	 *
	 */
	public class PCSPeakFinderThread extends DomainFinderThread {

		protected char[] currRegionSeq;
		protected char[] currRegionSeqRC;
		protected SequenceGenerator seqgen;
		
		public PCSPeakFinderThread(List<Region> regs) {
			super(regs);
			seqgen = new SequenceGenerator(gconfig.getGenome());
		}
		
		/**
		 * findFeatures: 
		 * 	1) Count frequencies of bases around all tag 5' positions
		 * 	2) Find potentially enriched domains on the genome by comparing all tag counts to a background model,
		 *	3) Find the most likely transcriptional bubble positions within the domain
		 * 
		 * @param subRegion : region to run analysis on
		 * @return Map of Lists of Features in the subRegion, Indexed by ExperimentCondition 
		 */
		public Map<ExperimentCondition, List<Feature>> findFeatures(Region subRegion) {
			
			//Tag sequence composition
			calculateTagSequenceComposition(subRegion);
			
			//Get the enriched domains using the functionality from the superclass (DomainFinderThread) 
			//and the over-ridden processDomain methods below 
			Map<ExperimentCondition, List<Feature>> peaks = super.findFeatures(subRegion);
			return peaks;
		}
		
		/**
		 * ProcessDomains: 
		 *  - Trims feature coordinates back to agree with overlapping hits. 
		 *  - Counts hits in each feature, per sample 
		 *  - Finds the peak & bubble position in the domain according to a choice of methods.
		 *  
		 * @param currFeatures
		 * @param current region
		 * @return : Lists of EnrichedFeatures, indexed by condition
		 */
		protected Map<ExperimentCondition, List<EnrichedFeature>> processDomains(List<EnrichedFeature> currFeatures, Region currSubRegion){
			Map<ExperimentCondition, List<EnrichedFeature>> peakFeatures = new HashMap<ExperimentCondition, List<EnrichedFeature>>();
			for(ExperimentCondition cond : manager.getConditions())
				peakFeatures.put(cond, new ArrayList<EnrichedFeature>());
			
			for(ExperimentCondition currCondition : manager.getConditions()){
				for(EnrichedFeature currDomain : currFeatures){
					Map<Sample, List<StrandedBaseCount>> fHitsPos = overlappingHits(hitsPos, currDomain);
					Map<Sample, List<StrandedBaseCount>> fHitsNeg = overlappingHits(hitsNeg, currDomain);
					
					//Trim the coordinates
					trimFeature(currDomain, fHitsPos, fHitsNeg, currCondition);
					
					//Quantify the feature in each Sample and in the condition in which it was found
					quantifyFeature(currDomain, fHitsPos, fHitsNeg, currCondition);
					
					//Find the peaks
					EnrichedPeakFeature peak=null;
					
					peakFeatures.get(currCondition).add(currDomain);
				}
			}
			return(peakFeatures);
		}
		
		/**
		 * Calculate the sequence composition around every tag 5' position in the current region, 
		 * and add the counts to the global models.  
		 * @param currReg
		 */
		protected void calculateTagSequenceComposition(Region currReg){
			//Build the sequence model in the window around the tag 5' locations
			currRegionSeq = seqgen.execute(currReg).toCharArray();
			currRegionSeqRC = currRegionSeq.clone();
			SequenceUtils.reverseComplement(currRegionSeqRC);
			float[][][] localTagSeqComposition = new float[manager.getSamples().size()][tagSeqWin+1][4];
			for(int s=0; s<manager.getSamples().size(); s++)
				for(int i=0; i<=tagSeqWin; i++)
					for(int j=0; j<4; j++)
						localTagSeqComposition[s][i][j]=0;
			int halfSeqWin = tagSeqWin/2;
			for(Sample s : manager.getSamples()){
				for(StrandedBaseCount sbc : hitsPos.get(s)){
					int w=0;
					for(int x=sbc.getCoordinate()-halfSeqWin-currReg.getStart(); x<=sbc.getCoordinate()+halfSeqWin-currReg.getStart(); x++){
						if(x>=0 && x<currRegionSeq.length)
							localTagSeqComposition[s.getIndex()][w][SequenceUtils.char2int(currRegionSeq[x])]+=sbc.getCount();
						w++;
					}
				}
				for(StrandedBaseCount sbc : hitsNeg.get(s)){
					int w=0;
					for(int x=currReg.getEnd()-sbc.getCoordinate()-halfSeqWin; x<=currReg.getEnd()-sbc.getCoordinate()+halfSeqWin; x++){
						if(x>=0 && x<currRegionSeqRC.length)
							localTagSeqComposition[s.getIndex()][w][SequenceUtils.char2int(currRegionSeqRC[x])]+=sbc.getCount();
						w++;
					}
				}
			}
			synchronized(tagSeqComposition){
				for(int s=0; s<manager.getSamples().size(); s++)
					for(int i=0; i<=tagSeqWin; i++)
						for(int j=0; j<4; j++)
							tagSeqComposition[s][i][j]+=localTagSeqComposition[s][i][j];
			}
		}
	}
}
