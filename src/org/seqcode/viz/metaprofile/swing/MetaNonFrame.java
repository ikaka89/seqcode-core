package org.seqcode.viz.metaprofile.swing;

import java.awt.Color;
import java.io.File;
import java.io.IOException;

import org.seqcode.genome.Genome;
import org.seqcode.viz.metaprofile.BinningParameters;
import org.seqcode.viz.metaprofile.MetaProfile;
import org.seqcode.viz.metaprofile.MetaProfileHandler;
import org.seqcode.viz.metaprofile.MetaUtils;
import org.seqcode.viz.metaprofile.PointProfiler;
import org.seqcode.viz.paintable.PaintableScale;


public class MetaNonFrame{
	private Genome genome;
	private BinningParameters params;
	private MetaProfile profile;
	private MetaProfileHandler handler;
	private MetaUtils utils;
	private PaintableScale peakScale, lineScale;
	private ProfileLinePanel linePanel;
	private ProfilePanel panel;
	private boolean saveSVG;
	
	public MetaNonFrame(Genome g, BinningParameters bps, PointProfiler pp, boolean normalizedMeta, boolean svg) {
		peakScale = new PaintableScale(0.0, 1.0);
		lineScale = new PaintableScale(0.0, 1.0);
		
		genome = g;
		params = bps;
		handler = new MetaProfileHandler("MetaProfile", params, pp, normalizedMeta);
		profile = handler.getProfile();
		linePanel = new ProfileLinePanel(params, lineScale);
		profile.addProfileListener(linePanel);
		utils = new MetaUtils(genome);
		saveSVG = svg;
		panel = new ProfilePanel(profile, peakScale);
	}
	public void setStyle(String s){
		panel.setStyle(s);
	}
	public void setColor(Color c){
		panel.updateColor(c);
		linePanel.updateColor(c);
	}
	public void setLinePanelColorQuanta(double [] q){
		linePanel.setLineColorQuanta(q);
	}
	public void setDrawColorBar(boolean c){
		linePanel.setDrawColorBar(c);
	}
	public void setTransparent(boolean c){
		linePanel.setTransparent(c);
		panel.setTransparent(c);
	}
	public void saveImages(String root){
		try {
			System.err.println("Saving images with root name: "+root);
			panel.saveImage(saveSVG ? new File(root+"_profile.svg") : new File(root+"_profile.png"), 1200, 700, !saveSVG);
			linePanel.saveImage(saveSVG ? new File(root+"_lines.svg") : new File(root+"_lines.png"), linePanel.getPanelWidth(), linePanel.getPanelLength(), !saveSVG);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void savePointsToFile(String root){
		String fileName = String.format("%s.points.txt", root);
		profile.saveToFile(fileName);
		fileName = String.format("%s.profiles.txt", root);
		profile.saveProfilesToFile(fileName);
	}
	public MetaProfileHandler getHandler() { return handler; }
	public MetaUtils getUtils(){return utils;}
	public void clusterLinePanel(){linePanel.cluster();}
	public void setLineMin(double m){linePanel.setMinColorVal(m);}
	public void setLineMax(double m){linePanel.setMaxColorVal(m);}
	public void setLineThick(int t){linePanel.updateLineWeight(t);}
}
