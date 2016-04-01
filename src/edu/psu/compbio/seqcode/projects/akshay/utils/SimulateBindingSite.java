package edu.psu.compbio.seqcode.projects.akshay.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edu.psu.compbio.seqcode.genome.GenomeConfig;
import edu.psu.compbio.seqcode.gse.datasets.motifs.MarkovBackgroundModel;
import edu.psu.compbio.seqcode.gse.datasets.motifs.WeightMatrix;

public class SimulateBindingSite {
	
	/** Motifs to inset in the simulated binding sites */
	public List<WeightMatrix> motifs = new ArrayList<WeightMatrix>();
	/** Insert rate (fraction of sites that should spit a sampling from PPM) of the motifs,
	 * PPM :- Position probability matrix */
	public List<Double> insertRate = new ArrayList<Double>();
	/** Background Markov model */
	public MarkovBackgroundModel markov;
	/** length of the binding site */
	public int len;
	public Random rand = new Random();
	/** Number of sequences to generate */
	public int N;
	
	public GenomeConfig gcon; 
	
	//Settors
	public void setMotifs(List<WeightMatrix> m){motifs = m;}
	public void setInsertRate(List<Double> rate){insertRate = rate;}
	public void setBack(MarkovBackgroundModel m){markov = m;}
	public void setLen(int l){len=l;}
	public void setN(int n){N=n;}
	
	public SimulateBindingSite(GenomeConfig g) {
		gcon = g;
	}
	
	public List<String> execute(){
		List<String> seq = new ArrayList<String>();
		
		for(int i=0; i<N; i++){
			// First, generate a background sequence
			String s = generateASeq();
			// Now insert motifs
			// How many motifs to insert
			double motifBlockSize = len/motifs.size();
			// Insert point (id*motifBlockSize+15)
			for(int mID=0; mID<motifs.size(); mID++){
				// first covert the PWM to a PFM or PPM
				StringBuilder motifConsensusBuilder = new StringBuilder();
				motifs.get(mID).normalizeFrequencies();
				for(int j=0; j<motifs.get(mID).matrix.length; j++){
					double r = Math.random();
					if(r<= motifs.get(mID).matrix[j]['A']){
						motifConsensusBuilder.append('A');
					}else if(r>motifs.get(mID).matrix[j]['A'] && 
							r<= motifs.get(mID).matrix[j]['A']+motifs.get(mID).matrix[j]['G']){
						motifConsensusBuilder.append('G');
					}else if(r>motifs.get(mID).matrix[j]['A']+motifs.get(mID).matrix[j]['G'] &&
							r<=motifs.get(mID).matrix[j]['A']+motifs.get(mID).matrix[j]['G']+motifs.get(mID).matrix[j]['C']){
						motifConsensusBuilder.append('C');
					}else if(r>motifs.get(mID).matrix[j]['A']+motifs.get(mID).matrix[j]['G']+motifs.get(mID).matrix[j]['C'] &&
							r<=1){
						motifConsensusBuilder.append('T');
					}
				}
				int pointOfInsertion = (int)(mID*motifBlockSize) + 15;
				if(i <  insertRate.get(mID)*N){
					StringBuilder sBuilder = new StringBuilder(s);
					sBuilder.replace(pointOfInsertion, pointOfInsertion + motifs.get(mID).matrix.length, motifConsensusBuilder.toString());
					s=sBuilder.toString();
				}
				
			}
			seq.add(s);
		}

		return seq;
	}
	
	public String generateASeq(){
		String s = new String();
		//Preliminary bases
		for(int i=1; i<markov.getMaxKmerLen() && i<=len; i++){
			double prob = rand.nextDouble();
			double sum=0; int j=0;
			while(sum<prob){
				String test = s.concat(int2base(j));
				sum += markov.getMarkovProb(test);
				if(sum>=prob){
					s = test;
					break;
				}
				j++;
			}
		}
		//Remaining bases
		for(int i=markov.getMaxKmerLen(); i<=len; i++){
			String lmer = s.substring(s.length()-(markov.getMaxKmerLen() -1));
			double prob = rand.nextDouble();
			double sum=0; int j=0;
			while(sum<prob){
				String test = lmer.concat(int2base(j));
				sum += markov.getMarkovProb(test);
				if(sum>=prob){
					s =s.concat(int2base(j));
					break;
				}
				j++;
			}
		}
		return s;
	}
	
	public String int2base(int x){
		String c;
		switch(x){
		case 0:
			c="A"; break;
		case 1:
			c="C"; break;
		case 2:
			c="G"; break;
		case 3:
			c="T"; break;
		default:
			c="N";
		}
		return(c);
	}
	
}