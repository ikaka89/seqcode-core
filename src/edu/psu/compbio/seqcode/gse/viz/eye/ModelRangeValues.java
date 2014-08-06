/*
 * Author: tdanford
 * Date: Sep 16, 2008
 */
package edu.psu.compbio.seqcode.gse.viz.eye;

import java.awt.*;
import java.lang.reflect.*;
import java.util.*;

import edu.psu.compbio.seqcode.gse.utils.Pair;
import edu.psu.compbio.seqcode.gse.utils.models.Model;
import edu.psu.compbio.seqcode.gse.utils.models.ModelFieldAnalysis;
import edu.psu.compbio.seqcode.gse.viz.colors.Coloring;
import edu.psu.compbio.seqcode.genome.location.Region;

public class ModelRangeValues extends AbstractModelPaintable {

	public static final String boundsKey = "bounds";
	public static final String colorKey = "color";
	public static final String strokeKey = "stroke";
	public static final String axisColorKey = "axis-color";

	private String xFieldName, yFieldName;
	private Vector<Pair<Integer,Integer>> ranges;
	private Vector<Model> models;
	
	public ModelRangeValues() { 
		xFieldName = "start";
		yFieldName = "end";
		ranges = new Vector<Pair<Integer,Integer>>();
		models = new Vector<Model>();
		
		Color transblue = Color.blue;
		transblue = Coloring.clearer(Coloring.clearer(transblue));
		
		initProperty(new PropertyValueWrapper<Integer[]>(boundsKey, new Integer[] { 0, 1 }));
		initProperty(new PropertyValueWrapper<Color>(colorKey, transblue));
		initProperty(new PropertyValueWrapper<Float>(strokeKey, (float)3.0));
	}
	
	public ModelRangeValues(String xfield, String yfield) { 
		this();
		xFieldName = xfield;
		yFieldName = yfield;
	}
	
	public void addValue(Object value) { 
		if (value instanceof Region) {  
			Region r = (Region)value;
			int start = r.getStart(), end = r.getEnd();
			
			models.add(new RangeModel(start, end));
			addRangeValue(start, end);
			
		} else { 
			super.addValue(value);
		}
	}
	
	public void addModel(Model m) {
		Class modelClass = m.getClass();
		ModelFieldAnalysis analysis = new ModelFieldAnalysis(modelClass);

		Field xfield = analysis.findField(xFieldName);
		Field yfield = analysis.findField(yFieldName);

		if(xfield != null && yfield != null) { 
			try {
				Object xvalue = xfield.get(m);
				Object yvalue = yfield.get(m);
				
				if(xvalue != null && yvalue != null) { 
					Class xclass = xvalue.getClass();
					Class yclass = yvalue.getClass();
					
					if(!Model.isSubclass(xclass, Integer.class)) { 
						throw new IllegalArgumentException("Start value must be an Integer");
					}
					if(!Model.isSubclass(yclass, Integer.class)) { 
						throw new IllegalArgumentException("End value must be an Integer");
					}
					
					Integer xnumber = (Integer)xvalue;
					Integer ynumber = (Integer)yvalue;
					
					int x = xnumber.intValue();
					int y = ynumber.intValue();
					
					models.add(m);
					addRangeValue(x, y);
					
				} else { 
					throw new IllegalArgumentException("location or value was null");
				}
				
			} catch (IllegalAccessException e) {
				throw new IllegalArgumentException("location or value field was inaccessible", e);
			}
		} else { 
			String msg = "No Fields:";
			if(xfield == null) { 
				msg += String.format(" %s", xFieldName);
			}
			if(yfield == null) { 
				msg += String.format(" %s", yFieldName);
			}
			throw new IllegalArgumentException(msg);
		}
	}
	
	private void addRangeValue(int x, int y) {
		if(x > y) { throw new IllegalArgumentException(); }
		
		ModelPaintableProperty boundsProp = getProperty(boundsKey);
		Integer[] bounds = (Integer[])boundsProp.getValue();
		
		ranges.add(new Pair<Integer,Integer>(x, y));

		if(x < bounds[0] || y > bounds[1]) {
			bounds[0] = Math.min(x, bounds[0]);
			bounds[1] = Math.max(y, bounds[1]);
			setProperty(new PropertyValueWrapper<Integer[]>(boundsKey, bounds));
		}
		
		dispatchChangedEvent();
	}

	public void addModels(Iterator<? extends Model> itr) {
		while(itr.hasNext()) { 
			addModel(itr.next());
		}
	}

	public void clearModels() {
		ranges.clear();
		models.clear();

		dispatchChangedEvent();
	}

	public void paintItem(Graphics g, int x1, int y1, int x2, int y2) {
		Integer[] bounds = getPropertyValue(boundsKey);
		Color color = getPropertyValue(colorKey, Color.red);
		float stroke = getPropertyValue(strokeKey, (float)1.0);
		int strokeWidth = Math.max(1, (int)Math.floor(stroke));
		Color axisColor = getPropertyValue(axisColorKey, Color.black);
		
		int length = Math.max(1, bounds[1] - bounds[0] + 1);
		
		int w = x2-x1, h = y2-y1;
		
		Graphics2D g2 = (Graphics2D)g;
		Stroke oldStroke = g2.getStroke();
		g2.setStroke(new BasicStroke(stroke));
		
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		/** Painting Code **/
		
		// Axes
		g2.setColor(axisColor);
		g2.drawRect(x1, y1, w-1, h-1);
		
		int wArea = w - strokeWidth*2;
		
		// Points
		for(Pair<Integer,Integer> r : ranges) {
			int start = r.getFirst();
			int end = r.getLast();
			
			double xf = (double)(start-bounds[0]) / (double)length; 
			double yf = (double)(end-bounds[0]+1) / (double)length; 
			
			int px = x1 + (int)Math.round(xf * (double)wArea);
			int py = x1 + (int)Math.round(yf * (double)wArea);

			g2.setColor(color);
			g2.fillRect(px, y1, py-px, y2-y1);
		}
		
		g2.setStroke(oldStroke);
	}
	
	public static class RangeModel extends Model { 
		public Integer start, end;
		
		public RangeModel(int s, int e) { 
			start = s; end = e;
		}
	}
}


