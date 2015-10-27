package edu.psu.compbio.seqcode.projects.chexmix;

import java.util.List;

import edu.psu.compbio.seqcode.deepseq.experiments.ExperimentCondition;
import edu.psu.compbio.seqcode.deepseq.experiments.ExperimentManager;
import edu.psu.compbio.seqcode.deepseq.experiments.ExptConfig;
import edu.psu.compbio.seqcode.genome.GenomeConfig;
import edu.psu.compbio.seqcode.genome.location.StrandedPoint;

public class CompositeXLFinder {

	protected ExperimentManager manager;
	protected GenomeConfig gconfig;
	protected ExptConfig econfig;
	protected ChExMixConfig cconfig;
	protected CompositeModelMixture mixtureModel;
	protected CompositeTagDistribution signalComposite;
	protected CompositeTagDistribution controlComposite;
	protected List<StrandedPoint> compositePoints;
	
	
	public CompositeXLFinder(GenomeConfig gcon, ExptConfig econ, ChExMixConfig ccon){
		gconfig = gcon;
		econfig = econ;
		cconfig = ccon;
		manager = new ExperimentManager(econfig);
	}
	
	public void execute(){
		//Load appropriate options
		compositePoints = cconfig.getCompositePoints();
		int winSize = cconfig.getCompositeWinSize();
		
		//Build the composite distribution(s)
		signalComposite = new CompositeTagDistribution(compositePoints, manager, winSize, true);
		//controlComposite = new CompositeTagDistribution(points, manager, winSize, false);
		controlComposite =null;
		
		//Initialize the mixture model 
		mixtureModel = new CompositeModelMixture(signalComposite, controlComposite, gconfig, econfig, cconfig, manager);
		
		System.out.println("TrainingRound: -1:\n"+mixtureModel.getModel().toString());
		
		//Train EM
		//TODO: check for convergence properly
		int maxT=5;
		for(int t=0; t<maxT; t++){
			
			mixtureModel.execute(true);
			
			System.out.println("TrainingRound: "+t+":\n"+mixtureModel.getModel().toString());
		}
		
		//ML assignment
		
		//Report
		for(ExperimentCondition cond : manager.getConditions()){
			String compositeFileName = "composite."+cond.getName()+".txt";
			signalComposite.printToFile(cond, compositeFileName);
		}
	}
	
	//Main
	public static void main(String[] args){
		System.setProperty("java.awt.headless", "true");
		System.err.println("ChExMix version: "+ChExMixConfig.version);
		GenomeConfig gcon = new GenomeConfig(args);
		ExptConfig econ = new ExptConfig(gcon.getGenome(), args);
		ChExMixConfig ccon = new ChExMixConfig(gcon, args);
		if(ccon.helpWanted()){
			System.err.println(ccon.getArgsList());
		}else{
			CompositeXLFinder xlFinder = new CompositeXLFinder(gcon, econ, ccon);
			xlFinder.execute();
		}
		
	}
}
