package edu.psu.compbio.seqcode.projects.chexmix;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import edu.psu.compbio.seqcode.genome.Genome;
import edu.psu.compbio.seqcode.genome.GenomeConfig;
import edu.psu.compbio.seqcode.genome.location.Region;
import edu.psu.compbio.seqcode.genome.location.StrandedPoint;
import edu.psu.compbio.seqcode.gse.tools.utils.Args;
import edu.psu.compbio.seqcode.gse.utils.ArgParser;
import edu.psu.compbio.seqcode.projects.multigps.utilities.Utils;

/**
 * ChExMixConfig: 
 * 		Maintains all constants needed by ChExMix. 
 *     
 * @author Shaun Mahony
 * @version	%I%, %G%
 */
public class ChExMixConfig {

	public static String version = "0.1";
	protected GenomeConfig gconfig;
	protected Genome gen=null;
	protected String outName="chexmix", outBase="chexmix";
	protected File outDir=null, interDir=null, imagesDir=null;
	protected boolean printHelp=false;
	protected TagProbabilityDensity defaultCSModel=null;
	protected List<StrandedPoint> compositePoints = new ArrayList<StrandedPoint>(); //Centers of the composite plots
	protected int compositeWinSize=1000; //Width of the composite plot
	protected int XLDistribOffset=6; //exonuclease head-space
	protected double XLDistribSigma=1.5; //gaussian distrib sigma
	protected int XLComponentSpacing = 8; //Inital number of bp between XL Components
	protected int maxModelUpdateRounds=5; //Maximum number of outer EM training rounds
	protected double modelConvergenceKL=-15; //KL-divergence threshold for convergence 
	protected int maxThreads=1;				//Number of threads to use. Default is 1 for single processor machines. 
	protected double alphaScalingFactor = 10.0; //Scale the alpha value by this factor relative to the noise component per-base
	protected double fixedAlpha = 0.0; //Fixed alpha value if above 0
	protected boolean smoothingBMDuringUpdate=true;
	protected boolean gaussianSmoothingBMDuringUpdate=false;
	protected boolean updateBM=true; //Set to false to turn off binding model update
	protected double bindingmodel_spline_smooth = 30; //Smoothing step for cubic spline in binding model reestimation
    protected double bindingmodel_gauss_smooth = 2; //Variance for Gaussian smoothing in binding model reestimation
    protected boolean plotCompositeEM=false; //Plot the EM process for the composite plots
	protected boolean printCompositeResponsibilities = true; //Print the responsibilities for each composite position
	protected boolean writeSinglePlots=false; //Plot the individual PNG images along with the gifs
    protected List<Region> regionsToPlotML = new ArrayList<Region>(); //List of regions that will be printed during ML training (for debugging/demonstration)
	
	//Constants
	public final double LOG2 = Math.log(2);
	public final double INIT_CS_TO_XO_RATIO=0.05; 	//Initial ratio of CS component pi values to sum of XO pi values. 
	public final int MAX_EM_ITER=2000;
    public final int EM_ML_ITER=100;     				//Run EM up until <tt>ML_ITER</tt> without using sparse prior
    public final int ML_ML_ITER=100;     				//Run ML up until <tt>ML_ITER</tt> without using sparse prior
    public final int ALPHA_ANNEALING_ITER=100;     //Run EM up until <tt>ALPHA_ANNEALING_ITER</tt> with smaller alpha based on the current iteration
    public final int EM_MU_UPDATE_WIN=50; //Half the window size in which to look for mu maximization (i.e. component position) during EM.
    public final double EM_CONVERGENCE = 1e-10; //EM convergence between the likelihood of the current and the previous step
    public final double EM_STATE_EQUIV_THRES = 1e-10; //EM state equivalence threshold 
    public final int EM_STATE_EQUIV_ROUNDS = 3; //Number of training rounds where the EM states have to be equivalent
    public final double NOISE_EMISSION_MIN = 0.05; //Arbitrary floor on the emission probability of noise (must be non-zero to mop up noise reads)
    public final double NOISE_EMISSION_MAX = 0.75; //Arbitrary ceiling on the emission probability of noise
    public final int NOISE_DISTRIB_SMOOTHING_WIN = 50; //Smoothing window for the noise distribution used in the BindingMixture
    public final int MAX_BINDINGMODEL_WIDTH=1000; //Maximum width for binding models (affects how large the read profiles are for binding components    
	public final boolean CALC_LL=false; //Calculate the log-likelihood during EM.
	public final boolean CALC_COMP_LL=false; //Calculate component-wise log-likelihoods during ML
	
	protected String[] args;
	public String getArgs(){
		String a="";
		for(int i=0; i<args.length; i++)
			a = a+" "+args[i];
		return a;
	}
	
	public ChExMixConfig(GenomeConfig gcon, String[] arguments){this(gcon, arguments, true);}
	public ChExMixConfig(GenomeConfig gcon, String[] arguments, boolean isGPS){
		System.setProperty("java.awt.headless", "true");
		gconfig = gcon;
		gen = gconfig.getGenome();
		this.args=arguments; 

		ArgParser ap = new ArgParser(args);
		if(args.length==0 || ap.hasKey("h")){
			printHelp=true;			
		}else{
			try{
				//Test for a config file... if there is concatenate the contents into the args
				if(ap.hasKey("config")){
					ArrayList<String> confArgs = new ArrayList<String>();
					String confName = ap.getKeyValue("config");
					File confFile = new File(confName);
					if(!confFile.isFile())
						System.err.println("\nCannot find configuration file: "+confName);
					BufferedReader reader = new BufferedReader(new FileReader(confFile));
				    String line;
			        while ((line = reader.readLine()) != null) {
			        	line = line.trim();
			        	String[] words = line.split("\\s+");
			        	if(!words[0].startsWith("--"))
			        		words[0] = new String("--"+words[0]);
			        	confArgs.add(words[0]); 
			        	if(words.length>1){
				        	String rest=words[1];
				        	for(int w=2; w<words.length; w++)
				        		rest = rest+" "+words[w];
				        	confArgs.add(rest);
			        	}
			        }
			        String [] confArgsArr = confArgs.toArray(new String[confArgs.size()]);
			        String [] newargs =new String[args.length + confArgsArr.length];
			        System.arraycopy(args, 0, newargs, 0, args.length);
			        System.arraycopy(confArgsArr, 0, newargs, args.length, confArgsArr.length);
			        args = newargs;
			        ap = new ArgParser(args);
				}
				
				//Read distribution file
				String modelFile = Args.parseString(args, "d", null);	// ChIP-seq read distribution file
				if (modelFile != null){
					File pFile = new File(modelFile);
					if(!pFile.isFile()){
						System.err.println("\nCannot find read distribution file: "+modelFile);
						System.exit(1);
					}
					defaultCSModel = new TagProbabilityDensity(pFile); 
				}
				
				
				/****Miscellaneous arguments****/
				//Width of the composite window
				compositeWinSize = Args.parseInteger(args,"cwin",compositeWinSize);
				//Composite plot center points (required)
				if(ap.hasKey("cpoints"))
					compositePoints = Utils.loadStrandedPointsFromFile(gen, Args.parseString(args, "cpoints", null));
				//Maximum number of model update rounds
				maxModelUpdateRounds = Args.parseInteger(args,"r", 3);
				//Turn off binding model updates
				updateBM = Args.parseFlags(args).contains("nomodelupdate") ? false : true;
				//Turn off smoothing during binding model updates 
				smoothingBMDuringUpdate = Args.parseFlags(args).contains("nomodelsmoothing") ? false : true;
				//Parameter for spline smoothing
				bindingmodel_spline_smooth = Args.parseDouble(args,"splinesmoothparam",bindingmodel_spline_smooth); 
				//Turn on Gaussian smoothing during binding model updates
				gaussianSmoothingBMDuringUpdate = Args.parseFlags(args).contains("gaussmodelsmoothing") ? true : false;
				//Parameter for Gaussian smoothing (std. dev.)
				bindingmodel_gauss_smooth = Args.parseDouble(args,"gausssmoothparam",bindingmodel_gauss_smooth);
				//Output path
				DateFormat df = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");  
			    df.setTimeZone(TimeZone.getTimeZone("EST"));
				outName = Args.parseString(args, "out", outName+"_"+df.format(new Date()));
				outDir =  new File(outName); //Output directory
				outBase = outDir.getName(); //Last part of name
				//Threads
				maxThreads = Args.parseInteger(args,"threads",maxThreads);
				maxThreads = Math.min(maxThreads, java.lang.Runtime.getRuntime().availableProcessors());
				//Alpha scaling factor
				alphaScalingFactor = Args.parseDouble(args,"alphascale",alphaScalingFactor);
				//Fixed alpha value
				fixedAlpha = Args.parseDouble(args,"fixedalpha",fixedAlpha);
				//Plot the EM process on the composite
				plotCompositeEM =  Args.parseFlags(args).contains("plot") ? true : false;
				//Regions to print during ML training
				if(ap.hasKey("plotregions"))
					regionsToPlotML = Utils.loadRegionsFromFile(Args.parseString(args, "plotregions", null), gen, -1);
				
				
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Merge a set of estimated genomes 
	 * @param estGenomes
	 * @return
	 */
	public Genome mergeGenomes(List<Genome> estGenomes){
		//Combine the chromosome information
		HashMap<String, Integer> chrLenMap = new HashMap<String, Integer>();
		for(Genome e : estGenomes){
			Map<String, Integer> currMap = e.getChromLengthMap();
			for(String s: currMap.keySet()){
				if(!chrLenMap.containsKey(s) || chrLenMap.get(s)<currMap.get(s))
					chrLenMap.put(s, currMap.get(s));
			}
		}
		gen =new Genome("Genome", chrLenMap);
		return gen;		
	}
	
	//Accessors
	public Genome getGenome(){return gen;}
	public boolean helpWanted(){return printHelp;}
	public int getCompositeWinSize(){return compositeWinSize;}
	public List<StrandedPoint> getCompositePoints(){return compositePoints;}
	public int getMaxThreads(){return maxThreads;}
	public double getAlphaScalingFactor(){return alphaScalingFactor;}
	public double getFixedAlpha(){return fixedAlpha;}
	public boolean getPlotEM(){return plotCompositeEM;}
	public boolean getWriteSinglePlots(){return writeSinglePlots;}
	public List<Region> getRegionsToPlot(){return regionsToPlotML;}
	public TagProbabilityDensity getDefaultCSModel(){return defaultCSModel;}
	public boolean doBMUpdate(){return updateBM;}
	public boolean getSmoothingBMDuringUpdate(){return smoothingBMDuringUpdate;}
	public double getBindingModelSplineSmoothParam(){return bindingmodel_spline_smooth;}
	public boolean getGaussBMSmooth(){return gaussianSmoothingBMDuringUpdate;}
	public double getBindingModelGaussSmoothParam(){return bindingmodel_gauss_smooth;}
	public int getMaxModelUpdateRounds(){return maxModelUpdateRounds;}
	public double getModelConvergenceKL(){return modelConvergenceKL;}
	public int getXLDistribOffset(){return XLDistribOffset;}
	public double getXLDistribSigma(){return XLDistribSigma;}
	public int getXLComponentSpacing(){return XLComponentSpacing;}
	public boolean getPrintCompositeResponsibilities(){return printCompositeResponsibilities;}
	
	/**
	 * Make some output directories used by ChExMix
	 */
	public void makeChExMixOutputDirs(boolean makeInterAndImageDirs){
		//Test if output directory already exists. If it does,  recursively delete contents
		outDir =  new File(outName);
		if(outDir.exists())
			deleteDirectory(outDir);
		outBase = outDir.getName();
		//(re)make the output directory
		outDir.mkdirs();
		if(makeInterAndImageDirs){
			//Make the gps intermediate results output directory
			interDir = new File(outDir.getAbsolutePath()+File.separator+"intermediate-results");
			interDir.mkdirs();
			//Make the image results output directory
			imagesDir = new File(outDir.getAbsolutePath()+File.separator+"images");
			imagesDir.mkdirs();
		}
	}
	public String getOutName(){return outName;}
	public String getOutBase(){return outBase;}
	public File getOutputParentDir(){return outDir;}
	public File getOutputIntermediateDir(){return interDir;}
	public File getOutputImagesDir(){return imagesDir;}
	
	/**
	 * Delete a direcctory
	 */
	public boolean deleteDirectory(File path) {
	    if( path.exists() ) {
	      File[] files = path.listFiles();
	      for(int i=0; i<files.length; i++) {
	         if(files[i].isDirectory()) {
	           deleteDirectory(files[i]);
	         }
	         else {
	           files[i].delete();
	         }
	      }
	    }
	    return( path.delete() );
	}
	
	/**
	 * returns a string describing the arguments handled by this parser. 
	 * @return String
	 */
	public String getArgsList(){
		return(new String("" +
				"Genome:" +
				"\t--species <Species;Genome>\n" +
				"\tOR\n" +
				"\t--geninfo <genome info file> AND --seq <fasta seq directory>\n" +
				"General:\n" +
				"\t--cwin <composite window size (default="+compositeWinSize+")>\n" +
				"\t--cpoints <composite plot stranded center points>\n" +
				"\t--d <read distribution model file>\n" +
				"\t--r <max. model update rounds (default="+maxModelUpdateRounds+")>\n" +
				"\t--out <out name (default="+outBase+")>\n" +
				"\t--nonunique [flag to use non-unique reads]\n" +
				"\t--threads <number of threads to use (default="+maxThreads+")>\n" +
				"Experiment Design File:\n" +
				"\t--design <file name>\n" +
				"Miscellaneous:\n" +
				"\t--alphascale <alpha scaling factor(default="+alphaScalingFactor+">\n" +
				"\t--fixedalpha <impose this alpha (default: set automatically)>\n" +
				"\t--nomodelupdate [flag to turn off binding model updates]\n" +
				"\t--nomodelsmoothing [flag to turn off binding model smoothing]\n" +
				"\t--splinesmoothparam <spline smoothing parameter (default="+bindingmodel_spline_smooth+">\n" +
				"\t--gaussmodelsmoothing [flag to turn o Gaussian model smoothing (default = cubic spline)]\n" +
				"\t--gausssmoothparam <Gaussian smoothing std dev (default="+bindingmodel_gauss_smooth+">\n" +
				"\t--fixedmodelrange [flag to keep binding model range constant]\n" +
				"\t--mlconfignotshared [flag to not share component configs in the ML step]\n" +
				"\t--plot <plot the EM training process in the composite>\n" +
				"\t--plotregions <regions to print during ML training>\n" +
				"\t--verbose [flag to print intermediate files and extra output]\n" +
				"\t--config <config file: all options can be specified in a name<space>value text file, over-ridden by command-line args>\n" +
				""));
	}
}

