package org.seqcode.projects.seqview.paintable;

import java.awt.*;

import org.seqcode.genome.location.Named;
import org.seqcode.genome.location.Region;
import org.seqcode.genome.location.Typed;
import org.seqcode.projects.seqview.model.RegionExpanderModel;


public class NamedTypedPainter extends NamedStrandedPainter {
    private RegionExpanderModel model;    
    private Color palegreen = new Color((float)0.0,(float)1.0,(float)0.0,(float).8);
    public NamedTypedPainter(RegionExpanderModel model) {
        super(model);
        this.model = model;
    }
    public Color getColor(Region r) {
        if (r instanceof Typed) {
            String type = ((Typed)r).getType();
            if (type.matches(".*Dubious.*")) {
                return Color.PINK;
            }else if (type.equals("Transposon") || type.equals("repeat_region")) {
                return Color.BLUE;
            } else if (type.matches(".*[rR][nN][aA].*")) {
                return Color.RED;
            } else if (type.equals("Telomeric Region")) {
                return Color.CYAN;
            } else if (type.matches(".*Repeat.*")) {
                return Color.GRAY;
            } else {
                return palegreen;
            }
        } else {
            return super.getColor(r);
        }
    }
    
    public String getLabel(Region r) {
        if (r instanceof Named &&
            r instanceof Typed) {
            return ((Named)r).getName() + "/" + 
                ((Typed)r).getType();
        } else {
            return super.getLabel(r);
        }
    }
}
