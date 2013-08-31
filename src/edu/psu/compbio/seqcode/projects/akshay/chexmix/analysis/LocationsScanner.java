package edu.psu.compbio.seqcode.projects.akshay.chexmix.analysis;

import java.util.ArrayList;
import java.util.List;

import edu.psu.compbio.seqcode.projects.akshay.chexmix.datasets.BindingLocation;
import edu.psu.compbio.seqcode.projects.akshay.chexmix.datasets.Config;
import edu.psu.compbio.seqcode.projects.akshay.chexmix.datasets.CustomReturn;

public class LocationsScanner {
	
	public List<CustomReturn> scanOut = new ArrayList<CustomReturn>();
	
	public LocationsScanner(List<BindingLocation> allbls, Config conf, int[] seedprofile) {
		for(BindingLocation bl : allbls){
			CustomReturn temp = bl.scanConcVecWithBl(seedprofile, conf.getIntSize(), conf.getSmoothSize());
			if(temp.pcc > conf.getPccCutoff()){
				CustomReturn pushed = new CustomReturn(temp.pcc, temp.maxvec, bl);
				scanOut.add(pushed);
			}
		}
	}
	
	//Accessories
	
	public double[] getListofPCCvalues(){
		double[] ret = new double[scanOut.size()];
		for(int i=0; i< scanOut.size(); i++){
			ret[i] = scanOut.get(i).pcc;
		}
		return ret;
	}
	
	public int[][] getTags(Config conf){
		int[][] ret = new int[scanOut.size()][4*conf.getIntSize()];
		for(int i=0; i<scanOut.size(); i++){
			List<Integer> addToRet = scanOut.get(i).bl.getConcatenatedTags(scanOut.get(i).maxvec.midpoint, conf.getIntSize(), scanOut.get(i).maxvec.orientation, conf.getSmoothSize());
			for(int j=0; j<addToRet.size(); j++){
				ret[i][j] = addToRet.get(j);
			}
		}
		return ret;
	}
	
	public String[] getBlnames(){
		String[] ret = new String[scanOut.size()];
		for(int i=0; i<scanOut.size(); i++){
			ret[i] = scanOut.get(i).bl.getName();
		}
		return ret;
	}

}
