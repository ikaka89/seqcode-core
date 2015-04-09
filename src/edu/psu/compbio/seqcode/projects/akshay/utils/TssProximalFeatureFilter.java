package edu.psu.compbio.seqcode.projects.akshay.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;

import edu.psu.compbio.seqcode.genome.GenomeConfig;
import edu.psu.compbio.seqcode.genome.location.Point;
import edu.psu.compbio.seqcode.genome.location.Region;
import edu.psu.compbio.seqcode.genome.location.StrandedPoint;
import edu.psu.compbio.seqcode.gse.tools.utils.Args;
import edu.psu.compbio.seqcode.gse.utils.ArgParser;
import edu.psu.compbio.seqcode.gse.utils.io.RegionFileUtilities;

public class TssProximalFeatureFilter {
	
	protected GenomeConfig gcon;
	protected List<StrandedPoint> refTSSs;
	protected List<Region> regions = new ArrayList<Region>();
	protected List<Point> peaks = new ArrayList<Point>();
	protected int minDistance;
	
	public void execute(){
		if(regions.size()!=0){
			
			Map<String, List<StrandedPoint>> refTSSsbyChrom = new HashMap<String, List<StrandedPoint>>();
			for(StrandedPoint sp: refTSSs){
				if(refTSSsbyChrom.containsKey(sp.getChrom())){refTSSsbyChrom.get(sp.getChrom()).add(sp);}
				else{refTSSsbyChrom.put(sp.getChrom(),new ArrayList<StrandedPoint>()); refTSSsbyChrom.get(sp.getChrom()).add(sp);}
			}
			
			Iterator<Region> it = regions.iterator();
			while(it.hasNext()){
				Region r = it.next();
				int min = Integer.MAX_VALUE;
				if(refTSSsbyChrom.containsKey(r.getChrom())){
					for(StrandedPoint sp : refTSSsbyChrom.get(r.getChrom())){
						int dis = sp.distance(r.getMidpoint());
						if(dis < min){
							dis = min;
						}
					}
				}
				if(min <= minDistance){
					it.remove();
				}
			}
			
			for(Region r : regions){
				System.out.println(r.getLocationString());
			}
			
		}else if(peaks.size() !=0){
			Map<String, List<StrandedPoint>> refTSSsbyChrom = new HashMap<String, List<StrandedPoint>>();
			for(StrandedPoint sp: refTSSs){
				if(refTSSsbyChrom.containsKey(sp.getChrom())){refTSSsbyChrom.get(sp.getChrom()).add(sp);}
				else{refTSSsbyChrom.put(sp.getChrom(),new ArrayList<StrandedPoint>()); refTSSsbyChrom.get(sp.getChrom()).add(sp);}
			}
			
			Iterator<Point> it = peaks.iterator();
			while(it.hasNext()){
				Point p = it.next();
				int min = Integer.MAX_VALUE;
				if(refTSSsbyChrom.containsKey(p.getChrom())){
					for(StrandedPoint sp : refTSSsbyChrom.get(p.getChrom())){
						int dis = sp.distance(p);
						if(dis < min){
							dis = min;
						}
					}
				}
				if(min <= minDistance){
					it.remove();
				}
			}
			
			for(Point p : peaks){
				System.out.println(p.getLocationString());
			}
			
		}
	}

	public TssProximalFeatureFilter(GenomeConfig gconf) {
		gcon = gconf;
	}
	
	// Settors
	public void setRegions(List<Region> regs){regions = regs;}
	public void setPeaks(List<Point> pts){peaks =pts;}
	public void setMinD(int minD){minDistance = minD;}
	
	public static void main(String[] args){
		GenomeConfig gcon = new GenomeConfig(args);
		ArgParser ap = new ArgParser(args);
		TssProximalFeatureFilter runner = new TssProximalFeatureFilter(gcon);
		int minD = Args.parseInteger(args, "minD", 2000);
		runner.setMinD(minD);
		if(!ap.hasKey("peaks") && !ap.hasKey("regions")){
			System.err.println("Provide peaks of regions file!!!");
			System.exit(1);
		}else{
			if(ap.hasKey("peaks")){
				List<Point> points = RegionFileUtilities.loadPeaksFromPeakFile(gcon.getGenome(), ap.getKeyValue("peaks"), -1);
				runner.setPeaks(points);
			}else{
				List<Region> regs = RegionFileUtilities.loadRegionsFromPeakFile(gcon.getGenome(), ap.getKeyValue("regions"), -1);
				runner.setRegions(regs);
			}
		}
		
		runner.execute();
		
	}
	
	

}