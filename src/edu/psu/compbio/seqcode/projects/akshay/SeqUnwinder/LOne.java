package edu.psu.compbio.seqcode.projects.akshay.SeqUnwinder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import edu.psu.compbio.seqcode.projects.akshay.SeqUnwinder.LBFGS;
import weka.core.Utils;
import edu.psu.compbio.seqcode.projects.akshay.SeqUnwinder.LBFGS.ExceptionWithIflag;
import edu.psu.compbio.seqcode.projects.akshay.SeqUnwinder.SeqUnwinder.ClassRelationStructure;
import edu.psu.compbio.seqcode.projects.akshay.SeqUnwinder.SeqUnwinder.ClassRelationStructure.Node;

public class LOne extends Optimizer {
	// Fixed ADMM parameters

	/** Relaxation parameter (to help faster convergence) */
	public final double ADMM_ALPHA = 1.9;
	/** Absolute feasibility tolerance for the primal and dual feasibility conditions */
	public final double ADMM_ABSTOL = 1E-2; 
	/** Relative  feasibility tolerance for the primal and dual feasibility conditions */
	public final double ADMM_RELTOL = 1E-2;

	// Fixed SeqUnwinder parameters

	/** Tolerence for internal Nodes convergence */
	public final double NODES_tol = 1E-1;

	// Tunable ADMM parameters

	/** The maximum number of allowed iterations for the ADMM algorithm */
	public int ADMM_maxItr = 30;
	/** Augmented Lagrangian parameter rho */
	public double ADMM_pho = 0.001;
	public double ADMM_pho_fold = 1.0;
	/** The maximum allowed value for pho */
	public double ADMM_pho_max = 100000000;
	/** Number of threads to run ADMM on */
	public int ADMM_numThreads = 5;

	// BGFS parameters 

	/** The maximum number of allowed iterations for the BGFS algorithm */
	public int BGFS_maxIts=-1;

	// SeqUnwinder parameters
	/** The total number of predictors/features in the model (does not include the intercept term) */
	protected int numPredictors;
	/** Total number of classes to be predicted */
	protected int numClasses;
	/** Relationships between the different nodes in SeqUnwinder */
	protected ClassRelationStructure classStructure;
	/** Total number of nodes (internal nodes and classes) in SeqUnwinder */
	protected int numNodes;
	/** The L1 regularization parameter in the SeqUnwinder model */
	protected double regularization;
	/** Maximum number of iterations to update internal nodes. For small number of node levels (usually 3 to 4), we might need very few iterations*/
	protected int NODES_maxItr=10;
	/** Internal flag that indicated if ADMM has been run before */
	protected boolean ranADMM=false;

	// SeqUnwinder and ADMM variables

	/** Current feature weights for all the nodes in the SeqUnwinder (also contains the intercept term). Dimension :- (numPredictors+1)*numNodes */
	public double[] sm_x;
	/** Current feature weights for all the leaf nodes (classes) in the SeqUnwinder (also contains the intercept term). Dimension :- (numPredictors+1)*numClasses */
	public double[] x;
	/** Current values (t) of z (z-step in ADMM). Dimension :- (numPredictors+1)*numNodes*numNodes */
	public double[] z; // for the Z-step
	/** Value of z at previous iteration (t-1). Needed to assess convergence  Dimension :- (numPredictors+1)*numNodes*numNodes */
	public double[] zold;
	/** Current values of the augmented lagrange dual variables (t). Dimension :- (numPredictors+1)*numNodes*numNodes  */
	public double[] u;



	// SeqUnwinder training data
	/** Training data (Instances)*/
	public double[][] data;
	/** Weights of the instances */
	protected double[] weights;
	/** Instance class membership */
	protected int[] cls;

	// Misc

	public void initUandZ(){
		int dim = numPredictors+1;
		z= new double[numNodes*numNodes*dim];
		zold = new double[numNodes*numNodes*dim];
		u= new double[numNodes*numNodes*dim];
	}

	/** Boolean variable indicating debug mode */
	protected boolean sm_Debug;


	//Settors
	public void setBGFSmaxItrs(int m){BGFS_maxIts=m;}
	public void setADMMmaxItrs(int m){ADMM_maxItr = m;}
	public void setSeqUnwinderMaxIts(int m){NODES_maxItr = m;}
	public void setInstanceWeights(double[] w){weights=w;}
	public void setClsMembership(int[] c){cls=c;}
	public void setNumPredictors(int p){numPredictors=p;}
	public void setNumClasses(int c){numClasses = c;}
	public void setClassStructure(ClassRelationStructure rel){classStructure = rel; setNumNodes(rel.allNodes.size());}
	public void setNumNodes(int n){numNodes = n;}
	public void setRidge(double r){regularization = r;}
	public void setDebugMode(boolean debug){sm_Debug =debug;}
	public void setPho(double ph){ADMM_pho = ph;}
	public void set_numThreads(int nt){ADMM_numThreads = nt;}

	//gettors
	public double[] getX(){return x;}
	public double[] getsmX(){return sm_x;}

	public LOne(double[] xinit, double[] sm_xinit, double[][] d) {
		x = xinit;
		sm_x = sm_xinit;
		data=d;
	}

	public void execute() throws Exception{

		for(int it=0; it<NODES_maxItr; it++){

			System.err.println("Running SeqUnwinder for Iteration: "+ it);

			double[] sm_x_old = new double[sm_x.length];

			for(int i=0; i<sm_x.length; i++){
				sm_x_old[i] = sm_x[i];
			}

			// First, run admm on leaf nodes
			ADMMrunner admm = new ADMMrunner();
			admm.execute();

			// Now update the internal nodes
			updateInternalNodes();

			// Check Convergence
			boolean converged = true;
			for(Node n : classStructure.allNodes.values()){
				double diff = 0.0;
				for(int w=0; w<(numPredictors+1); w++){
					diff += Math.pow(sm_x_old[n.nodeIndex*(numPredictors+1)+w]-sm_x[n.nodeIndex*(numPredictors+1)+w],2);
				}
				diff = Math.sqrt(diff);
				if(sm_Debug){
					System.err.println("Hierarchy update diff: Node: "+n.nodeIndex + " diff is: "+  diff);
					double tmp = NODES_tol*getL2NormX(n.nodeIndex);
					System.err.println("Target diff : Node: " + n.nodeIndex + " is "+ tmp);
				}
				if( diff > NODES_tol*getL2NormX(n.nodeIndex)){
					converged=false;
					break;
				}
			}

			if(converged){
				System.err.println();
				System.err.println("SeqUnwinder has converged after "+it+1+" iterations !!");
				break;
			}

		}

	}

	// Slave methods

	private void updateInternalNodes(){
		// First update odd layaer nodes
		for(int l=1; l<classStructure.numLayers; l+=2){
			for(Node n : classStructure.layers.get(l)){// Get nodes in this layer
				updateNode(n);
			}
		}
		// Now update even layer nodes except the leaf node
		for(int l=2; l<classStructure.numLayers; l+=2){
			for(Node n : classStructure.layers.get(l)){// Get nodes in this layer
				updateNode(n);
			}
		}
	}

	private void updateNode(Node n){
		int dim = numPredictors+1;
		int nOffset = n.nodeIndex*dim;
		// Note the intercept term not included
		for(int w=1; w<dim; w++){
			List<Double> xs = new ArrayList<Double>();
			for(int pid : n.parents){
				xs.add(sm_x[pid*dim+w]);
			}
			for(int cid : n.children){
				xs.add(sm_x[cid*dim+w]);
			}
			Collections.sort(xs);
			sm_x[nOffset+w] = (xs.size() % 2 == 0) ? (xs.get(xs.size()/2) + xs.get((xs.size()/2)-1))/2 : xs.get(xs.size()/2);
			//	int midInd = xs.size()/2;
			//	sm_x[nOffset+w] = xs.get(midInd);
		}
	}

	private double getL2NormX(int nodeIndex){
		double norm=0;
		int dim = numPredictors+1;
		int offset = nodeIndex*dim;
		for(int w=0; w<dim; w++){
			norm += Math.pow(sm_x[offset+w], 2);
		}
		return Math.sqrt(norm);
	}






	// To clear memory
	public void clearOptimizer(){
		data=null;
		sm_x=null;
		x=null;
	}


	public class ADMMrunner {

		// Threaded variables

		/** 
		 * Hashmap holding the data block that goes into each thread. 
		 * Keys are the thread ids (for eg:- Thread2) and value is the data-block
		 */
		public HashMap<String,double[][]> t_Data = new HashMap<String,double[][]>();
		/**
		 * Hashmap holding the computed x's from each thread at iteration "t+1".
		 * Keys are the thread ids (for eg:- Thread2)
		 */
		public HashMap<String,double[]> t_x = new HashMap<String,double[]>();
		/** Hashmap holding the u's from each thread at iteration "t" */
		public HashMap<String,double[]> t_u = new HashMap<String,double[]>();
		/** The weights of input instances for the data blocks that go into each thread */
		public HashMap<String,double[]> t_weights = new HashMap<String,double[]>();
		/** The class assignment of input instances for the data blocks that go into each thread*/
		public HashMap<String, int[]> t_cls = new HashMap<String,int[]>();
		/** All the threads vote their status on line search (or thex-update) */
		public boolean[] finished_linesrch;
		/** Tracks the convergenece of ADMM  */
		public AtomicBoolean ADMMconverged = new AtomicBoolean(false);
		/** Finished running the current z-step */
		public AtomicBoolean beginLineSearch = new AtomicBoolean(false);

		//ADMM consensus variables

		/** Current values (t) of z (z-step in ADMM). Dimension :- (numPredictors+1)*numNodes*numNodes */
		//public double[] z; // for the Z-step
		/** Value of z at previous iteration (t-1). Needed to assess convergence  Dimension :- (numPredictors+1)*numNodes*numNodes */
		//public double[] zold;
		/** Current values of the augmented lagrange dual variables (t). Dimension :- (numPredictors+1)*numNodes*numNodes  */
		//public double[] u;
		/** Stores the primal residuals over the course of the ADMM algorithm Dimension:- [ADMM_maxItr][numNodes*numNodes] */
		public double[][] history_primal;
		/** Stores the dual residuals over the course of the ADMM algorithm Dimension:- [ADMM_maxItr][numNodes*numNodes] */
		public double[][] history_dual;

		public double[][] history_xnorm;

		public double[][] history_unorm;

		public double[][] history_znorm;

		/** Tracking the iterations of ADMM */
		public AtomicInteger ADMM_currItr_value = new AtomicInteger(0);

		// Initialize
		public void initHistories(){
			int dim = numPredictors+1;
			history_primal = new double[ADMM_maxItr][numNodes*numNodes];
			history_dual = new double[ADMM_maxItr][numNodes*numNodes];
			history_xnorm = new double[ADMM_maxItr][numNodes];
			history_unorm = new double[ADMM_maxItr][numNodes*numNodes];
			history_znorm = new double[ADMM_maxItr][numNodes*numNodes];
		}
		public ADMMrunner() {

			finished_linesrch = new boolean[ADMM_numThreads];
			for(int i=0; i<ADMM_numThreads; i++){
				finished_linesrch[i] = true;
			}

			int blockSize = data.length/ADMM_numThreads;
			for(int i=0; i<blockSize*ADMM_numThreads; i++){
				int threadID = i % ADMM_numThreads;
				String threadName = "Thread"+threadID;
				if(t_Data.containsKey(threadName)){
					t_weights.get(threadName)[i/ADMM_numThreads] = weights[i];
					t_cls.get(threadName)[i/ADMM_numThreads] = cls[i];
					for(int j=0; j<data[0].length; j++){
						t_Data.get(threadName)[i/ADMM_numThreads][j] = data[i][j];
					}
				}else{
					t_Data.put(threadName, new double[blockSize][data[0].length]);
					t_weights.put(threadName, new double[blockSize]);
					t_weights.get(threadName)[i/ADMM_numThreads] = weights[i];
					t_cls.put(threadName, new int[blockSize]);
					t_cls.get(threadName)[i/ADMM_numThreads] = cls[i];
					for(int j=0; j< data[0].length; j++){
						t_Data.get(threadName)[i/ADMM_numThreads][j] = data[i][j];
					}
				}
			}


			// Initialize t_x
			for(int i=0; i< ADMM_numThreads; i++){
				String threadName = "Thread"+i;
				if(t_x.containsKey(threadName)){
					for(int j=0; j<x.length; j++){
						t_x.get(threadName)[j] = x[j];
					}
				}else{
					t_x.put(threadName, new double[x.length]);
					for(int j=0; j<x.length; j++){
						t_x.get(threadName)[j] = x[j];
					}
				}
			}

			// Initialize histories
			initHistories();

			//Initialize t_u
			for(int i=0; i<ADMM_numThreads; i++){
				String threadName = "Thread"+i;
				if(t_u.containsKey(threadName)){
					for(int j=0; j<u.length; j++){
						t_u.get(threadName)[j] = u[j];
					}
				}else{
					t_u.put(threadName, new double[u.length]);
					for(int j=0; j<u.length; j++){
						t_u.get(threadName)[j] = u[j];
					}
				}
			}
		}

		//Update methods
		public void updateUbar(){
			synchronized(t_u){
				for(int i=0; i<u.length; i++){
					u[i] = 0;
					for(String tname: t_u.keySet()){
						u[i] += t_u.get(tname)[i];
					}
					u[i] = u[i]/ADMM_numThreads;
				}
			}

		}
		public void updateXbar(){
			synchronized(t_x){
				for(int i=0; i<x.length; i++){
					x[i] = 0;
					for(String tname: t_x.keySet()){
						x[i] += t_x.get(tname)[i];
					}
					x[i] = x[i]/ADMM_numThreads;
				}
			}
		}
		public void updateResiduals(int itr){
			int dim = numPredictors + 1;
			// First calculate and update the primal residual at the current iteration
			for(Node n : classStructure.leafs){
				double r_t = 0.0;
				double s_t =0.0;
				if(n.parents.size() > 0){
					for(int pid : n.parents){
						int zOffset = (n.nodeIndex*numNodes*dim)+(pid*dim);
						for(String thname: t_x.keySet()){
							for(int w=0; w<dim; w++){
								r_t += Math.pow(t_x.get(thname)[n.nodeIndex*dim+w]-z[zOffset+w]-sm_x[pid*dim+w], 2);
							}
						}
						for(int w=0; w<dim; w++){
							s_t += Math.pow(ADMM_pho*(z[zOffset+w] - zold[zOffset+w]), 2);
						}
						s_t = Math.sqrt(s_t*ADMM_numThreads);
						history_primal[itr][n.nodeIndex*numNodes+pid] = Math.sqrt(r_t);
						history_dual[itr][n.nodeIndex*numNodes+pid] = s_t;
					}
				}else{
					int zOffset = (n.nodeIndex*numNodes*dim)+(n.nodeIndex*dim);
					for(String thname: t_x.keySet()){
						for(int w=0; w<dim; w++){
							r_t += Math.pow(t_x.get(thname)[n.nodeIndex*dim+w]-z[zOffset+w], 2);
						}
					}
					for(int w=0; w<dim; w++){
						s_t += Math.pow(ADMM_pho*(z[zOffset+w] - zold[zOffset+w]), 2);
					}
					s_t = Math.sqrt(s_t*ADMM_numThreads);
					history_primal[itr][n.nodeIndex*numNodes+n.nodeIndex] = Math.sqrt(r_t);
					history_dual[itr][n.nodeIndex*numNodes+n.nodeIndex] = s_t;
				}
			} // Over all the leaf nodes		
		}
		public void updateUnorm(int itr){
			for(Node n : classStructure.leafs){
				int dim = numPredictors+1;
				if(n.parents.size() > 0){
					for(int pid: n.parents){
						double unorm = 0.0;
						int uOffset = (n.nodeIndex*numNodes*dim)+(pid*dim);
						for(int w=0; w<dim; w++){
							unorm += Math.pow(u[uOffset+w], 2);
						}
						history_unorm[itr][n.nodeIndex*numNodes+pid] = Math.sqrt(unorm);
					}
				}else{
					double unorm = 0;
					int uOffset = (n.nodeIndex*numNodes*dim)+(n.nodeIndex*dim);
					for(int w=0; w<dim; w++){
						unorm += Math.pow(u[uOffset+w], 2);
					}
					history_unorm[itr][n.nodeIndex*numNodes+n.nodeIndex] = Math.sqrt(unorm);
				}
			}
		}
		public void updateXnorm(int itr){
			int dim = numPredictors+1;
			for(Node n : classStructure.leafs){
				int xOffset = n.nodeIndex*dim;
				double xnorm = 0.0;
				for(int w=0; w<dim; w++){
					xnorm += Math.pow(x[xOffset+w], 2);
				}
				history_xnorm[itr][n.nodeIndex] = Math.sqrt(xnorm);
			}
		}
		public void updateZnorm(int itr){
			for(Node n : classStructure.leafs){
				int dim = numPredictors+1;
				if(n.parents.size() > 0){
					for(int pid: n.parents){
						double znorm = 0.0;
						int zOffset = (n.nodeIndex*numNodes*dim)+(pid*dim);
						for(int w=0; w<dim; w++){
							znorm += Math.pow(z[zOffset+w], 2);
						}
						history_znorm[itr][n.nodeIndex*numNodes+pid] = Math.sqrt(znorm);
					}
				}else{
					double znorm = 0;
					int zOffset = (n.nodeIndex*numNodes*dim)+(n.nodeIndex*dim);
					for(int w=0; w<dim; w++){
						znorm += Math.pow(u[zOffset+w], 2);
					}
					history_znorm[itr][n.nodeIndex*numNodes+n.nodeIndex] = Math.sqrt(znorm);
				}
			}

		}
		public void updatePhoAndFold(int A_itr){

			double primal_residuals=0; // Sum of all the primal residuals
			double dual_residuals=0; // Sum of all the dual residuals

			double mu = 10; // maintains the primal and dual residuals within a factor of mu of one another
			double tao=2; // the factor by with pho will be increased or decreased at each iterations

			for(int i=0; i<(numNodes*numNodes); i++){
				primal_residuals += history_primal[A_itr][i];
				dual_residuals += history_dual[A_itr][i];
			}

			if(primal_residuals > mu*dual_residuals){ // if primal residual are greater than dual by a factor of mu; decrease pho 
				double old_pho = ADMM_pho;
				ADMM_pho = Math.min(ADMM_pho_max,ADMM_pho*tao);
				ADMM_pho_fold =  ADMM_pho/old_pho;
			}else if(dual_residuals > primal_residuals*mu){
				ADMM_pho = ADMM_pho/tao;
				ADMM_pho_fold = 1/tao;
			}else {
				ADMM_pho_fold = 1.0;
			}

		}
		public void updateZ(double pho){
			for(int i=0; i<z.length; i++){
				z[i] = z[i] - Math.signum(z[i])*Math.min(pho, Math.abs(z[i]));
				//z[i] = Math.max(0, xrel[i]-pho) - Math.max(0, -xrel[i] - pho);
			}
		}

		//Has ADMM converged
		public boolean hasADMMConverged(int itr){
			boolean converged =true;
			int dim = numPredictors + 1;

			double[] primal_tol = new double[numNodes*numNodes];
			double[] dual_tol = new double[numNodes*numNodes];

			for(Node n : classStructure.leafs){
				double xnorm = history_xnorm[itr][n.nodeIndex];
				if(n.parents.size() > 0){ // If this node has parents
					for(int pid : n.parents){ // Over all the parents of this node
						int zOffset = (n.nodeIndex*numNodes)+(pid);
						double znorm = history_znorm[itr][zOffset];
						double unorm = history_unorm[itr][zOffset];
						double cnorm = getL2NormX(pid);
						primal_tol[n.nodeIndex*numNodes+pid] = Math.sqrt(dim)*ADMM_ABSTOL + Math.sqrt(ADMM_numThreads)*ADMM_RELTOL*Math.max(xnorm, Math.max(znorm, cnorm));
						dual_tol[n.nodeIndex*numNodes+pid] = Math.sqrt(dim)*ADMM_ABSTOL + Math.sqrt(ADMM_numThreads)*ADMM_RELTOL*ADMM_pho*unorm;
					}
				}else{
					int zOffset = (n.nodeIndex*numNodes)+(n.nodeIndex);
					double znorm = history_znorm[itr][zOffset];
					double unorm = history_unorm[itr][zOffset];
					primal_tol[n.nodeIndex*numNodes+n.nodeIndex] = Math.sqrt(dim)*ADMM_ABSTOL + Math.sqrt(ADMM_numThreads)*ADMM_RELTOL*Math.max(xnorm, znorm);
					dual_tol[n.nodeIndex*numNodes+n.nodeIndex] = Math.sqrt(dim)*ADMM_ABSTOL + Math.sqrt(ADMM_numThreads)*ADMM_RELTOL*ADMM_pho*unorm;
				}
			}

			for(int i=0; i<primal_tol.length; i++){
				if(history_primal[itr][i] > primal_tol[i])
					converged=false;
				if(history_dual[itr][i] > dual_tol[i])
					converged=false;
				if(!converged)
					break;
			}

			return converged;
		}
		
		public boolean finshedLineSrch(){
			boolean ret = true;
			for(int i=0; i<finished_linesrch.length; i++){
				if(!finished_linesrch[i]){
					ret = false;
					break;
				}
			}
			return ret;	
		}

		// Runs the ADMM algorithm
		public void execute() {
			int dim = numPredictors+1;

			// Initiate the threads
			Thread[] threads = new Thread[ADMM_numThreads];
			for(int i=0; i<ADMM_numThreads; i++){
				String thname = "Thread"+i;
				ADMMrun th = new ADMMrun(t_Data.get(thname), t_weights.get(thname), t_x.get(thname), t_cls.get(thname), t_u.get(thname), thname);
				Thread t = new Thread(th, thname);
				t.start();
				threads[i] = t;
			}

			while(ADMM_currItr_value.get() < ADMM_maxItr){
				if(sm_Debug)
					System.err.print(". "+ ADMM_currItr_value.get() + " .");


				// Update pho 
				if(ADMM_currItr_value.get() >0 && !ranADMM && ADMM_pho < ADMM_pho_max)
					updatePhoAndFold(ADMM_currItr_value.get()-1);
				if(sm_Debug)
					System.err.print(" "+ADMM_pho+" ");

				//make sure all of finished_linesrch[] are set to false before you begin the linesearch.
				synchronized(finished_linesrch){
					for(int i=0; i<finished_linesrch.length; i++){
						finished_linesrch[i] = false;
					}
				}

				// Notify all threads to begin line search
				synchronized(beginLineSearch){
					beginLineSearch.notifyAll();
				}

				
				// Wait in the monitor area of finished_linesrch
				synchronized(finished_linesrch){
					while(!finshedLineSrch()){
						try {
							finished_linesrch.wait();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
				

				//Now check for convergence at previous iteration
				if(ADMM_currItr_value.get()>0){
					ADMMconverged.set(hasADMMConverged(ADMM_currItr_value.get()-1));
				}

				// Print the primal and dual residuals
				if(ADMM_currItr_value.get()>0){
					if(sm_Debug && !ADMMconverged.get()){
						double primal = 0.0;
						double dual = 0.0;
						for(int i=0; i<(numNodes*numNodes); i++){
							primal += history_primal[ADMM_currItr_value.get()-1][i];
							dual += history_dual[ADMM_currItr_value.get()-1][i];
						}
						System.err.println("Primal residual "+ primal + " , Dual residual "+ dual);
					}else{
						System.err.println();
						System.err.println("ADMM has converged after "+ADMM_currItr_value.get()+" iterations !!");
						// Notify all slave threads
						synchronized(beginLineSearch){
							beginLineSearch.notifyAll();
						}
						ranADMM=true;
						break;
					}
				}

				// Now update z

				//Fist copy z to zold
				for(int i=0; i<z.length; i++){
					zold[i] = z[i];
				}

				//Now pool the estimates of x_t+1 from all the threads
				updateXbar();
				//Also, pool the estimates of u_t from all the threads
				updateUbar();
				// Also update norms
				if(ADMM_currItr_value.get()>0)
					updateUnorm(ADMM_currItr_value.get()-1);

				updateXnorm(ADMM_currItr_value.get());

				// Calculate over-relaxed xhat
				double[] xhat = new double[z.length];
				for(Node n : classStructure.leafs){
					int nOffset = n.nodeIndex*dim;
					if(n.parents.size()>0){
						for(int pid : n.parents){
							int zOffset = (n.nodeIndex*numNodes*dim)+(pid*dim);
							int pOffset = pid*dim;
							for(int w=0; w<dim; w++){
								xhat[zOffset+w] = ADMM_ALPHA*(x[nOffset+w])+(1-ADMM_ALPHA)*zold[zOffset+w]-ADMM_ALPHA*sm_x[pOffset+w];
							}
						}
					}else{
						int zOffset = (n.nodeIndex*numNodes*dim)+(n.nodeIndex*dim);
						for(int w=0; w<dim; w++){
							xhat[zOffset+w] = ADMM_ALPHA*x[nOffset+w]+(1-ADMM_ALPHA)*zold[zOffset+w];
						}
					}
				}

				for(int i=0; i<z.length;i++){
					z[i] = xhat[i]+u[i];
				}

				// Z-update
				updateZ((2*regularization)/(ADMM_pho*ADMM_numThreads));

				updateZnorm(ADMM_currItr_value.get());

				// Calculate and update the primal and dual residuals
				updateResiduals(ADMM_currItr_value.get());

				ADMM_currItr_value.incrementAndGet();

			}



			// Wait till all the threads terminate 
			boolean anyrunning = true;
			while (anyrunning) {
				anyrunning = false;
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) { }
				for (int i = 0; i < threads.length; i++) {
					if (threads[i].isAlive()) {
						anyrunning = true;
						break;
					}
				}
			}

			// Now copy the leaf node weights (i.e x) to sm_x
			for(Node n: classStructure.leafs){
				int nOffset = n.nodeIndex*dim;
				for(int w=0; w<dim; w++){
					sm_x[nOffset+w] = x[nOffset+w];
				}
			} 

		}





		public class ADMMrun implements Runnable{


			/** Portion of the data this thread runs on */
			public double[][] t_b_Data;

			/** the corresponding weights of the dataset */
			public double[] t_b_weights;

			/** Learned weight vector */
			public double[] t_b_x;

			/** The current u of this thread */
			public double[] t_b_u;

			public int[] t_b_cls;



			public OptObject oO = new OptObject();

			public String threadName;


			public ADMMrun(double[][] dat, double[] t_wts, double[] predictors, int[] cl, double[] admm_u, String tname) {
				t_b_Data = dat;
				t_b_weights = t_wts;
				t_b_cls = cl;
				threadName =tname;

				t_b_x = new double[predictors.length];
				for(int i=0; i<predictors.length; i++)
					t_b_x[i] = predictors[i];
				t_b_u = new double[admm_u.length];
				for(int i=0; i<admm_u.length; i++)
					t_b_u[i] = admm_u[i];

			}


			@Override
			public void run() {

				while(true){
					//while(!beginLineSearch.get()){//Wait till the z-step has finished
						//try {
						//	Thread.sleep(1000);
						//} catch (InterruptedException e){}
						//if(ADMM_currItr_value.get() >= ADMM_maxItr)
						//	break;
					//}
					
					// Go to the wait list of the monitor region for object beginLineSearch
					// The current thread will wait until the main thread notifies
					
					
					synchronized(beginLineSearch){
						try {
							beginLineSearch.wait();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
					

					if(ADMM_currItr_value.get() >= ADMM_maxItr)
						break;
					if(ADMMconverged.get())
						break;

					// update t_b_u
					// first calculate xhat
					// Calculate over-relaxed x:- xrel
					int dim = numPredictors + 1;
					double[] t_b_xhat = new double[z.length];
					for(Node n : classStructure.leafs){
						int nOffset = n.nodeIndex*dim;
						if(n.parents.size()>0){
							for(int pid : n.parents){
								int zOffset = (n.nodeIndex*numNodes*dim)+(pid*dim);
								int pOffset = pid*dim;
								for(int w=0; w<dim; w++){
									t_b_xhat[zOffset+w] = ADMM_ALPHA*(t_b_x[nOffset+w])+(1-ADMM_ALPHA)*zold[zOffset+w]-ADMM_ALPHA*sm_x[pOffset+w];
								}
							}
						}else{
							int zOffset = (n.nodeIndex*numNodes*dim)+(n.nodeIndex*dim);
							for(int w=0; w<dim; w++){
								//xrel[zOffset+w] = ADMM_ALPHA*sm_x[nOffset+w]+(1-ADMM_ALPHA)*zold[zOffset+w]+u[zOffset+w];
								t_b_xhat[zOffset+w] = ADMM_ALPHA*t_b_x[nOffset+w]+(1-ADMM_ALPHA)*zold[zOffset+w];
							}
						}
					}

					for(int i=0; i<u.length; i++){
						t_b_u[i] = t_b_u[i] + t_b_xhat[i] - z[i]; 
					}

					// Correct u
					for(int i=0; i< t_b_u.length; i++){
						t_b_u[i] = t_b_u[i]/ADMM_pho_fold;
					}


					// t_b_x update
					int[] iflag = new int[1];
					double obj= oO.objectiveFunction(t_b_x);
					double[] grad = oO.evaluateGradient(t_b_x);
					int m = 5;
					double[] diag = new double[t_b_x.length];
					int[] iprint = new int[2];
					double eps = 0.1;
					double xtol = 10e-16;

					LBFGS lineFinder = new LBFGS();

					try {
						lineFinder.lbfgs(t_b_x.length, m, t_b_x, obj, grad, false, diag, iprint, eps, xtol, iflag);
					} catch (ExceptionWithIflag e1) {
						e1.printStackTrace();
					}


					while(iflag[0] == 1 ){
						//re-evaluate the objective and the gradient
						obj = oO.objectiveFunction(t_b_x);
						grad = oO.evaluateGradient(t_b_x);

						try {
							lineFinder.lbfgs(t_b_x.length, m, t_b_x, obj, grad, false, diag, iprint, eps, xtol, iflag);
						} catch (ExceptionWithIflag e) {
							e.printStackTrace();
						}
					}

					// This could be a weak link in the code. I'm assuming the other threads have initiated line search
					// Which, they should. However, if a thread reaches this point too soon (which is highly unlikely)
					// Then the other thread whouldn't have initiated line search
					// Can't think of a good way to make this full proof at the moment.
					//beginLineSearch.set(false); // Atomically set to false

					synchronized(t_x){
						for(int i=0; i<t_b_x.length; i++){
							t_x.get(threadName)[i] = t_b_x[i];
						}
					}
					synchronized(t_u){
						for(int i=0; i<t_b_u.length; i++){
							t_u.get(threadName)[i] = t_b_u[i];
						}
					}
					synchronized(finished_linesrch){
						finished_linesrch[getThreadId()] = true;
						finished_linesrch.notifyAll();
					}
					
				}
			}

			//Gettors
			public int getThreadId(){return Integer.parseInt(threadName.substring(6));}

			/**
			 * This class implements two things:-
			 * It calculates the gradient for the x-update sub-problem. (The BGFS method will need this)
			 * It calculates the overall objective function for the x-update subproblem. (The BGFS method will need this)
			 * @author akshaykakumanu
			 *
			 */
			public class OptObject {

				public OptObject() {
				}

				/**
				 * Claclulates the gradient
				 * @param currx
				 * @return
				 */
				public double[] evaluateGradient(double[] c_x){

					double[] grad = new double[c_x.length];
					int dim = numPredictors + 1; // Number of variables per class

					for (int i = 0; i < t_b_cls.length; i++) { // ith instance
						double[] num = new double[numClasses]; // numerator of
						// [-log(1+sum(exp))]'
						int index;
						for (int offset = 0; offset < numClasses; offset++) { // Which
							// part of 
							double exp = 0.0;
							index = offset * dim;
							for (int j = 0; j < dim; j++) {
								exp += t_b_Data[i][j]*c_x[index + j];
							}
							num[offset] = exp;
						}

						double max = num[Utils.maxIndex(num)];

						double denom=0.0;
						for (int offset = 0; offset < numClasses; offset++) {
							num[offset] = Math.exp(num[offset] - max);
							denom += num[offset];
						}
						Utils.normalize(num, denom);

						// Update denominator of the gradient of -log(Posterior)
						double firstTerm;
						for (int offset = 0; offset < numClasses; offset++) { // Which
							// part of x
							index = offset * dim;
							firstTerm = t_b_weights[i] * num[offset];
							for (int q = 0; q < dim; q++) {
								grad[index + q] += firstTerm * t_b_Data[i][q];
							}
						}

						for (int p = 0; p < dim; p++) {
							grad[t_b_cls[i] * dim + p] -= t_b_weights[i] * t_b_Data[i][p];
						}

					}


					for(Node n : classStructure.leafs){
						int nOffset = n.nodeIndex*dim;
						if(n.parents.size() > 0){
							for(int pid : n.parents){
								int pOffset = pid*dim;
								int zOffset = (n.nodeIndex*numNodes*dim)+(pid*dim);
								for(int w=1; w<dim; w++){
									grad[nOffset+w] += ADMM_pho*(c_x[nOffset+w]-sm_x[pOffset+w]-z[zOffset+w]+t_b_u[zOffset+w]);
								}
							}
						}else{
							int zOffset = (n.nodeIndex*numNodes*dim)+(n.nodeIndex*dim);
							for(int w=1; w<dim; w++){
								grad[nOffset+w] += ADMM_pho*(c_x[nOffset+w]-z[zOffset+w]+t_b_u[zOffset+w]);
							}
						}
					}	

					if(sm_Debug){
						//System.err.println(grad[20]);
						//System.err.println(grad[dim+20]);
						//System.err.println(grad[2*dim+20]);
					}

					return grad;
				}

				/**
				 * Claclulates the objective function
				 * @param currx
				 * @return
				 */
				public double objectiveFunction(double[] c_x){
					double nll=0.0;
					int dim = numPredictors+1;

					for (int i = 0; i < t_b_cls.length; i++) { // ith instance
						double[] exp = new double[numClasses];
						int index;
						for (int offset = 0; offset < numClasses; offset++) {
							index = offset * dim;
							for (int j = 0; j < dim; j++) {
								exp[offset] += t_b_Data[i][j] * c_x[index + j];
							}
						}
						double max = exp[Utils.maxIndex(exp)];
						double denom = 0;
						double num = exp[t_b_cls[i]] - max;

						for (int offset = 0; offset < numClasses; offset++) {
							denom += Math.exp(exp[offset] - max);
						}

						nll -= t_b_weights[i] * (num - Math.log(denom)); // Weighted NLL
					}

					for(Node n : classStructure.leafs){
						int nOffset = n.nodeIndex*dim;
						if(n.parents.size() >0){
							for(int pid : n.parents){
								int pOffset = pid*dim;
								int zOffset = (n.nodeIndex*numNodes*dim)+(pid*dim);
								for(int w=1; w<dim; w++){
									nll += (ADMM_pho/2)*(c_x[nOffset+w]-sm_x[pOffset+w]-z[zOffset+w]+t_b_u[zOffset+w])*(c_x[nOffset+w]-sm_x[pOffset+w]-z[zOffset+w]+t_b_u[zOffset+w]);
								}
							}
						}else{
							int zOffset = (n.nodeIndex*numNodes*dim)+(n.nodeIndex*dim);
							for(int w=1; w<dim; w++){
								nll += (ADMM_pho/2)*(c_x[nOffset+w]-z[zOffset+w]+t_b_u[zOffset+w])*(c_x[nOffset+w]-z[zOffset+w]+t_b_u[zOffset+w]);
							}
						}
					}

					if(sm_Debug){
						//System.err.println("Negative Log Likelihood: "+nll);
					}

					return nll;
				}

			}


		}


	}

}