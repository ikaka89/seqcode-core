package edu.psu.compbio.seqcode.projects.akshay.SeqUnwinder.framework;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import edu.psu.compbio.seqcode.genome.Genome;
import edu.psu.compbio.seqcode.genome.GenomeConfig;
import edu.psu.compbio.seqcode.genome.location.NamedRegion;
import edu.psu.compbio.seqcode.genome.location.Point;
import edu.psu.compbio.seqcode.genome.location.Region;
import edu.psu.compbio.seqcode.genome.location.RepeatMaskedRegion;
import edu.psu.compbio.seqcode.gse.datasets.motifs.WeightMatrix;
import edu.psu.compbio.seqcode.gse.gsebricks.verbs.location.ChromRegionIterator;
import edu.psu.compbio.seqcode.gse.gsebricks.verbs.location.RepeatMaskedGenerator;
import edu.psu.compbio.seqcode.gse.gsebricks.verbs.sequence.SequenceGenerator;
import edu.psu.compbio.seqcode.gse.tools.utils.Args;
import edu.psu.compbio.seqcode.gse.utils.ArgParser;
import edu.psu.compbio.seqcode.gse.utils.io.RegionFileUtilities;
import edu.psu.compbio.seqcode.gse.utils.strings.StringUtils;

/**
 * @author akshaykakumanu
 * @twitter ikaka89
 * @email auk262@psu.edu
 */
public class SeqUnwinderConfig implements Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static String version = "0.1";
	
	// General options
	protected GenomeConfig gcon;
	protected SequenceGenerator<Region> seqgen = null;
	protected String[] args;
	
	// Feature options (for making the arff file and beyond)
	protected int minK=4;
	protected int maxK=5;
	/** Number K-mers in the model */
	protected int numK;
	protected int win=150;
	protected String outArffFileName="out.arff";
	protected String designFileName="SeqUnwinder.design";
	protected String Rpath="";
	
	// Peaks features
	/** Peak annotations as given by the user; Enhancer;Shared*/
	protected List<String> annotations = new ArrayList<String>();
	/** List of peaks; this has the same order as the "subGroupsAtPeaks" */
	protected List<Point> peaks = new ArrayList<Point>();
	/** List of regions (win/2 around the peak mid points) */
	protected List<Region> regions = new ArrayList<Region>();
	/** Flag to include random/background regions in the model */
	protected boolean generateRandRegs=false;
	/** Flag to mask repeats while generating random regions */
	protected boolean screenReps=false;
	/** Random regions generated by SeqUnwinder */ 
	protected List<Region> randRegs = new ArrayList<Region>();
	
	
	// Classifier options
	protected double m_Ridge=10;
	/** Augmented Lagrangian parameter rho */
	protected double m_ADMM_pho=1.7;
	protected int m_ADMM_numThreads=5;
	protected int m_SeqUnwinder_MaxIts=20;
	protected int m_ADMM_MaxIts=500;
	protected int sm_NumLayers=2;
	protected int numCrossValidation=2;
	/** Relaxation parameter (to help faster convergence) */
	public static final double ADMM_ALPHA=1.9;
	/** Absolute feasibility tolerance for the primal and dual feasibility conditions */
	public static final double ADMM_ABSTOL = 1E-2; 
	/** Relative  feasibility tolerance for the primal and dual feasibility conditions */
	public static final double ADMM_RELTOL = 1E-2;
	/** Tolerence for internal Nodes convergence */
	public static final double NODES_tol = 1E-2;
	/** The maximum allowed value for pho */
	public static double ADMM_pho_max = 1000;
	/** The options string that is consistent with the WEKA framework */
	protected String[] wekaOptsString;
	protected boolean debugMode = false;
	
	// Discrim options
	/** Minimum length to consider for motif finding */
	protected int minM=6;
	/** Maximum length for motif finding */
	protected int maxM=10;
	/** The base name of the output directory */
	protected String outbase;
	/** The output directory file */
	protected File outdir;
	/** The minimum value of model scan score to consider form motif finding */
	protected double thresold_hills = 0.1;
	/** The number of hills to consider for clustering and motif finding for a given K-mer model */
	public static final int NUM_HILLS = 1500;
	/** No of iterations of clustering */
	public static final int ITRS_CLUS=10;
	/** Number of cluster; ideally each cluster corresponds to a motif */
	protected int numClus_CLUS=3;
	// Meme parameters
	protected String MEMEpath;
	protected String MEMEargs = " -dna -mod zoops -revcomp -nostatus ";
	protected int MEMEminw = 6;
	protected int MEMEmaxw = 11;
	protected int MEMEnmotifs = 3;
	protected int MEMEwin = 16;
	public static final double MOTIF_FINDING_ALLOWED_REPETITIVE = 0.2;
	
	// Following are filled during the course of a SeqUnwinder run
	/** Model names **/
	protected List<String> modelNames = new ArrayList<String>();
	/** SubGroup names */
	protected List<String> kmerSubGroupNames = new ArrayList<String>();
	/** Stores the weights of the K-mers
	 *  Keys are the model name
	 * 	Values are the K-mers weights for a given model */ 
	protected HashMap<String,double[]> kmerweights = new HashMap<String,double[]>();
	protected List<WeightMatrix> discrimMotifs = new ArrayList<WeightMatrix>();
	protected HashMap<String,double[]> discrimMotifScores = new HashMap<String,double[]>();
	protected HashMap<String, Double> discrimMotifRocs = new HashMap<String,Double>(); 
	protected String classifier_out ="";
	protected HashMap<String,double[]> testSetStats = new HashMap<String,double[]>();
	protected HashMap<String,double[]> trainSetStats = new HashMap<String,double[]>();
	
	//Settors
	public void setSubGroupNames(LinkedHashSet<String> sGNs){kmerSubGroupNames.addAll(sGNs);}
	public void setModelNames(List<String> modNames){modelNames.addAll(modNames);}
	public void setNumLayers(int n){sm_NumLayers = n;}
	public void setWeights(HashMap<String,double[]> wts){kmerweights.putAll(wts);}
	public void setDiscrimMotifs(List<WeightMatrix> mots){discrimMotifs.addAll(mots);}
	public void setDiscrimMotifScores(HashMap<String,double[]> scrs){discrimMotifScores.putAll(scrs);}
	public void setClassifierOut(String s){classifier_out = s;}
	public void setDiscrimMotifsRocs(HashMap<String, Double> dscrimRocs){discrimMotifRocs.putAll(dscrimRocs);}
	public void setTrainSetStats(HashMap<String,double[]> trainStats){trainSetStats.putAll(trainStats);}
	public void setTestSetStats(HashMap<String,double[]> tesetStats){testSetStats.putAll(tesetStats);}
	
	//Gettors
	public List<Point> getPeaks(){return peaks;}
	public List<String> getPeakAnnotations(){return annotations;}
	public List<Region> getRegions(){return regions;}
	public Genome getGenome(){return gcon.getGenome();}
	public SequenceGenerator<Region> getSeqGen(){return seqgen;}
	public int getWin(){return win;}
	public String getArffOutName(){return outArffFileName;}
	public int getKmin(){return minK;}
	public int getKmax(){return maxK;}
	public boolean getHasRandRegs(){return generateRandRegs;}
	public List<Region> getGenRandRegs(){return randRegs;}
	public String getDesignFileName(){return designFileName;}
	public String[] getWekaOptions(){return wekaOptsString;}
	public int getNumK(){return numK;}
	public HashMap<String,double[]> getKmerWeights(){return kmerweights;}
	public File getOutDir(){return outdir;}
	public String getOutbase(){return outbase;}
	public List<String> getSubGroupNames(){return kmerSubGroupNames;}
	public List<String> getMNames(){return modelNames;}
	public String getMemeArgs(){String memeargs = MEMEargs+" -nmotifs "+MEMEnmotifs + " -minw "+MEMEminw+" -maxw "+MEMEmaxw; return memeargs;}
	public String getMemePath(){return MEMEpath;}
	public int getNumDiscrimClusters(){return numClus_CLUS;}
	public int getMemeSearchWin(){return MEMEwin;}
	public int getMinM(){return minM;}
	public int getMaxM(){return maxM;}
	public int getKmerBaseInd(String kmer){
		int baseInd = 0;
		for(int k=minK; k<kmer.length(); k++){
			baseInd += (int)Math.pow(4, k);
		}
		return baseInd;
	}
	public double getHillsThresh(){return thresold_hills;}
	public List<WeightMatrix> getDiscrimMotifs(){return discrimMotifs;}
	public String getArgs(){
		String a="";
		for(int i=0; i<args.length; i++)
			a = a+" "+args[i];
		return a;
	}
	public String getVersion(){return version;}
	public HashMap<String,Double> getDiscrimMotifRocs(){return discrimMotifRocs;}
	public String getClassifierOutput(){return classifier_out;}
	public HashMap<String,double[]> getTrainSetStats(){return trainSetStats;}
	public HashMap<String,double[]> getTestSetStats(){return testSetStats;}
	public HashMap<String, double[]> getDiscrimMotsScore(){return discrimMotifScores;}
	public String getRpath(){return Rpath;}

	public SeqUnwinderConfig(String[] arguments) throws IOException {
		// Loading general options
		args = arguments;
		ArgParser ap = new ArgParser(args);
		if(ap.hasKey("h") || ap.hasKey("help") || args.length == 0){
			System.err.println(SeqUnwinderConfig.getSeqUnwinderArgsList());
			System.exit(1);
		}
		gcon = new GenomeConfig(args);
		seqgen = gcon.getSequenceGenerator();
		
		// First load all options needed for making the arff file
		
		// Set windown size around peaks
		win = Args.parseInteger(args, "win", 150);

		minK = Args.parseInteger(args, "minK", 4);
		maxK = Args.parseInteger(args, "maxK", 5);

		// Get outdir and outbase and make them; delete dirs that exist with the same
		outbase = Args.parseString(args, "out", "seqUnwinder_out");
		outdir = new File(outbase);
		makeOutPutDirs();

		// use the base to name the arff file
		outArffFileName = outbase+".arff";
		
		numK = 0;
		for(int k=minK; k<=maxK; k++ ){
			numK += (int)Math.pow(4, k);
		}

		// Load peaks and annotations
		if(!ap.hasKey("peaks")){
			System.err.println("Please provide ChIP-Seq binding locations and try again !!");
			SeqUnwinderConfig.getSeqUnwinderArgsList();
			System.exit(1);
		}else{
			// Reading peaks files and storing annotations
			String peaksFile = ap.getKeyValue("peaks");

			FileReader fr = new FileReader(peaksFile);
			BufferedReader br = new BufferedReader(fr);
			String line;
			while((line = br.readLine()) != null){
				if(!line.startsWith("#")){
					String[] pieces = line.split("\t");
					annotations.add(pieces[1]);
				}
			}
			br.close();
			
			// Loading peaks
			List<Point> peaks = new ArrayList<Point>();
			peaks.addAll(RegionFileUtilities.loadPeaksFromPeakFile(gcon.getGenome(), peaksFile, -1));
			
			// Converting peaks to regions
			regions.addAll(RegionFileUtilities.loadRegionsFromPeakFile(gcon.getGenome(), peaksFile, win));
		}


		// Check id random regions are needed to be created 
		if(ap.hasKey("makerandregs"))
			generateRandRegs = true;
		if(ap.hasKey("screenRepeats"))
			screenReps = true;
		
		// Now create randregs if needed
		if(generateRandRegs){

			// First find how many rand regions are needed
			int numRand = Integer.MAX_VALUE; // This should be the size of the subgroup with minimum no of. instances
			Set<String> subgroupNames = new HashSet<String>();
			for(String s : annotations){
				if(subgroupNames.add(s)){}
			}

			for(String s : subgroupNames){
				if(Collections.frequency(annotations, s) < numRand)
					numRand = Collections.frequency(annotations, s);
			}

			RandRegionsGenerator randGenerator = new RandRegionsGenerator(screenReps,numRand);
			randRegs.addAll(randGenerator.execute());
			regions.addAll(randRegs);
			// Now add "Random" to annotations
			for(Region r :  randRegs){
				peaks.add(r.getMidpoint());
				annotations.add("Random");
			}
			
		}
		
		// Now, load all options needed to run the classifier
		// the regularization constant
		debugMode = ap.hasKey("debug");
		m_Ridge = Args.parseDouble(args, "R", 10);
		m_ADMM_pho = Args.parseDouble(args, "PHO", 1.7);
		m_ADMM_MaxIts = Args.parseInteger(args, "A", 500);
		m_SeqUnwinder_MaxIts = Args.parseInteger(args, "S", 20);
		m_ADMM_numThreads = Args.parseInteger(args, "threads", 5);
		numCrossValidation = Args.parseInteger(args, "X", 2);
		
		// Now make the weka options string
		if(!debugMode)
			wekaOptsString=new String[20];
		else
			wekaOptsString=new String[21];
		
		wekaOptsString[0] = "-t"; wekaOptsString[1] = outdir.getAbsolutePath()+File.separator+outArffFileName;
		wekaOptsString[2] = "-x"; wekaOptsString[3] = Integer.toString(numCrossValidation);
		wekaOptsString[4] = "-CLS"; wekaOptsString[5] = outdir.getAbsolutePath()+File.separator+designFileName;
		wekaOptsString[6] = "-NL";wekaOptsString[7] = Integer.toString(sm_NumLayers);
		wekaOptsString[8] = "-TY";wekaOptsString[9] = "L1";
		wekaOptsString[10] = "-A";wekaOptsString[11] = Integer.toString(m_ADMM_MaxIts);
		wekaOptsString[12] = "-S";wekaOptsString[13]= Integer.toString(m_SeqUnwinder_MaxIts);
		wekaOptsString[14] = "-PHO";wekaOptsString[15]=Double.toString(m_ADMM_pho);
		wekaOptsString[16] = "-threads";wekaOptsString[17]=Integer.toString(m_ADMM_numThreads);
		wekaOptsString[18] = "-R";wekaOptsString[19]=Double.toString(m_Ridge);
		if(debugMode)
			wekaOptsString[20] = "-DEBUG";

		// Load all MEME arguments
		// Path to MEME binary
		MEMEpath = Args.parseString(args, "memepath", "");
		MEMEargs = Args.parseString(args, "memeargs", MEMEargs);
		
		MEMEminw = Args.parseInteger(args, "mememinw", 6);
		MEMEmaxw = Args.parseInteger(args, "mememaxw", 11);
		//Size of the focussed meme search win
		MEMEwin = Args.parseInteger(args, "memeSearchWin", 16);
		MEMEnmotifs = Args.parseInteger(args, "memenmotifs", 3);

		// Load arguments for Discrim analysis
		numClus_CLUS = Args.parseInteger(args, "numClusters", 3);
		minM = Args.parseInteger(args, "minScanLen", 6);
		maxM = Args.parseInteger(args, "maxScanLen", 10);
		thresold_hills = Args.parseDouble(args, "hillsThresh", 0.1);
		
		// Get R path
		Rpath = Args.parseString(args, "rpath", Rpath);
		if(!Rpath.equals("") && !Rpath.endsWith("/")){ Rpath= Rpath+"/";}
		

	}

	

	public void makeOutPutDirs(){
		//Test if output directory already exists. If it does,  recursively delete contents
		if(outdir.exists())
			deleteDirectory(outdir);
		//(re)make the output directory
		outdir.mkdirs();
		
		// Make the intermediate directory
		File interDir = new File(outdir.getAbsoluteFile()+File.separator+"intermediate");
		interDir.mkdirs();

	}

	public static String getSeqUnwinderArgsList(){
		return(new String("" +
				"Copyright (C) Akshay Kakumanu 2015-2016\n" +
				"\n" +
				"SeqUnwinder comes with ABSOLUTELY NO WARRANTY. This is free software, and you\n"+
				"are welcome to redistribute it under certain conditions.  See the MIT license \n"+
				"for details.\n"+
				"\n OPTIONS:\n" +
				" General:\n"+
				"\t--out <output file prefix>\n" +
				"\t--threads <number of threads to use>\n" +
				"\t--debug [flag to run in debug mode; prints extra output]\n" +
				" Genome:\n" +
				"\t--geninfo <genome info file> AND --seq <fasta seq directory reqd if using motif prior>\n" +
				" Loading Data:\n" +
				"\t--peaks <List of TF binding sites with annotations; eg: chr1:151736000  Shared;Proximal>\n" +
				"\t--win <window around peaks to consider for k-mer counting>\n" +
				"\t--makerandregs <flag to make random genomic regions as an extra outgroup class in classification>\n" +
				"\t--screenRepeats <flag to screen replicates while creating random genomic regions>\n" +
				" Running SeqUnwinder:\n" +
				"\t--minK <minimum length of k-mer (default = 4)>\n" + 
				"\t--maxK <maximum length of k-mer (default = 5)>\n" + 
				"\t--R <regularization constant (default = 10)>\n" + 
				"\t--PHO < (Augmented Lagrangian parameter default = 1.7)>\n" +
				"\t--A < (Maximum number of allowed ADMM iterations default = 500)>\n" +
				"\t--S < (Maximum number of allowed SeqUnwinder iterations default = 20)>\n" +
				"\t--X < (Number of folds for cross validation default = 2)>\n" +
				"\t--memepath <path to the meme bin dir (default: meme is in $PATH)>\n" +
				"\t--memenmotifs <number of motifs MEME should find for each condition (default=3)>\n" +
				"\t--mememinw <minw arg for MEME (default=6)>\n"+
				"\t--mememaxw <maxw arg for MEME (default=18)>\n"+
				"\t--memeargs <additional args for MEME (default=  -dna -mod zoops -revcomp -nostatus)>\n"+
				"\t--memeSearchWin <window around hills to search for discriminative motifs (default=16)>\n"+
				"\t--numClusters <number of clusters to split k-mer hills using k-means (default=3)>\n"+
				"\t--Rpath <path to R bin dir (default=Rscript in $PATH)>\n"+
				"\t--minScanLen <minimum length of the window to scan k-mer models (default=6)>\n"+
				"\t--maxScanLen <maximum length of the window to scan k-mer models (default=10)>\n"+
				"\t--hillsThresh <thresholding for detecting hills (default=0.1)>\n"+
				""));
	}


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

	public class RandRegionsGenerator{

		private int numSamples = 1000;
		private int validSamples=0;

		private RepeatMaskedGenerator<Region> repMask;
		private double genomeSize=0;
		private long [] chromoSize;
		private String [] chromoNames;
		private int numChroms=0;
		private Random rand = new Random();
		private double repPropLimit=0.5;
		private boolean screenRepeats=false;

		//Settors
		public void setNum(int n){numSamples=n;}
		public void setScreenRepeats(boolean s){screenRepeats=s;}

		public RandRegionsGenerator(boolean screenReps, int num) {
			repMask = new RepeatMaskedGenerator<Region>(gcon.getGenome());
			setScreenRepeats(screenReps);
			setNum(num);
		}


		public List<Region> execute() {

			List<Region>regList = new ArrayList<Region>();
			// First see how big the genome is:
			chromoSize = new long[gcon.getGenome().getChromList().size()];
			chromoNames = new String[gcon.getGenome().getChromList().size()];
			Iterator<NamedRegion> chroms = new ChromRegionIterator(gcon.getGenome());
			while (chroms.hasNext()) {
				NamedRegion currentChrom = chroms.next();
				genomeSize += (double) currentChrom.getWidth();
				chromoSize[numChroms] = currentChrom.getWidth();
				chromoNames[numChroms] = currentChrom.getChrom();
				// System.out.println(chromoNames[numChroms]+"\t"+chromoSize[numChroms]);
				numChroms++;
			}// System.out.println(genomeSize);

			// Now, iteratively generate random positions and check if they are
			// valid
			while (validSamples < numSamples) {
				Region potential;
				long randPos = (long) (1 + (rand.nextDouble() * genomeSize));
				// find the chr
				boolean found = false;
				long total = 0;
				for (int c = 0; c < numChroms && !found; c++) {
					if (randPos < total + chromoSize[c]) {
						found = true;
						if (randPos + win < total + chromoSize[c]) {
							potential = new Region(gcon.getGenome(), chromoNames[c], (int) (randPos - total), (int) (randPos+ win - total - 1));
							boolean regionOK = true;

							// screen repeats
							if (screenRepeats) {
								// is this overlapping a repeat?
								double repLen = 0;
								Iterator<RepeatMaskedRegion> repItr = repMask.execute(potential);
								while (repItr.hasNext()) {
									RepeatMaskedRegion currRep = repItr.next();
									if (currRep.overlaps(potential)) {
										repLen += (double) currRep.getWidth();
									}
								}
								if (repLen / (double) potential.getWidth() > repPropLimit)
									regionOK = false;

								// Is the sequence free from N's?
								String potSeq = seqgen.execute(potential);
								if (potSeq.indexOf('N') >= 0) {
									regionOK = false;
								}
							}
							// Screen dupicates
							for (Region r : regList) {
								if (potential.overlaps(r))
									regionOK = false;
							}

							// Screen for any exclude regions provided
							if (regions.size() != 0) {
								for (Region ex : regions) {
									if (potential.overlaps(ex)) {
										regionOK = false;
									}
								}
							}

							if (regionOK) {
								validSamples++;
								regList.add(potential);
								System.out.println(potential.getChrom() + ":"
										+ potential.getStart() + "-"
										+ potential.getEnd());
							}
						}
					}
					total += chromoSize[c];
				}
			}
			return (regList);
		}


	}

}
