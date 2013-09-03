package edu.psu.compbio.seqcode.projects.akshay.chexmix.datasets;

import java.util.*;

import edu.psu.compbio.seqcode.projects.akshay.chexmix.utils.*;

public class Vec {
	public String orientation;
	public int range;
	public String chr;
	public Map<Integer,Integer> tags = new TreeMap<Integer, Integer>();
	public int binsize;
	public int Smoothing;
	public int midpoint;
	
	public Vec(int range, int midpoint, String chr, String orientation, int Smoothing, int binsize, Map<Integer, Integer> tags) {
		this.range = range;
		this.chr = chr;
		this.orientation = orientation;
		this.Smoothing = Smoothing;
		this.binsize = binsize;
		this.tags = tags;
		this.midpoint = midpoint;
	}
	
	@Override
	public boolean equals(Object obj){
		if(obj == this){
			return true;
		}
		
		Vec ve = (Vec) obj;
		return this.orientation == ve.orientation && this.range == ve.range && this.chr == ve.chr && this.binsize == ve.binsize && this.Smoothing == ve.Smoothing;
	}
	
	@Override
	public int hashCode(){
		int result = 17;
		int code = (int) this.range;
		code+= (int) this.Smoothing;
		code+= (int) this.midpoint;
		code += (int) (this.chr == null ? 0 :this.chr.hashCode());
		code+= (int) (this.orientation == null ? 0 : this.orientation.hashCode());
		
		result = result*37 + code;
		return result;
	}
	
	
	 public Vec getSub(int midpoint, int range){
		 if(midpoint-range<this.midpoint-this.range || midpoint+range > this.midpoint+this.range){
			 System.err.println("the requested vector is out of bounds");
		 }
		 int[] positions = new int[this.tags.size()];
		 int[] counts = new int[this.tags.size()];
		 int i=0;
		 for(int pos: this.tags.keySet()){
			 positions[i]=pos;
			 counts[i]=this.tags.get(pos);
			 i++;
		 }
		 int start_ind = Arrays.binarySearch(positions, midpoint-range);
		 int end_ind = start_ind+range*2;
		 Map<Integer, Integer> rettags = new TreeMap<Integer, Integer>();
		 for(int j=start_ind; j< end_ind; j++ ){
			 rettags.put(positions[j], counts[j]);
		 }
		 Vec ret = new Vec(range, midpoint, this.chr,this.orientation,this.Smoothing,this.binsize,rettags);
		 return ret;
	}
	 
}
