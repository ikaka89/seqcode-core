package org.seqcode.gsebricks.verbs.location;

import java.util.*;
import java.sql.*;

import org.seqcode.data.connections.DatabaseException;
import org.seqcode.genome.Genome;
import org.seqcode.genome.location.NamedTypedRegion;
import org.seqcode.genome.location.Region;
import org.seqcode.gsebricks.verbs.Expander;


/* Generator that returns NamedTypedRegion objects from the specified table. 
   The table must have columns: chrom, chromStart, chromEnd, name, strand, and type.
   The regions are returned sorted by ascending chromStart */

public class NamedTypedGenerator<X extends Region> implements Expander<X,NamedTypedRegion> {

    private Genome genome;
    private String tablename;

    public NamedTypedGenerator(Genome g, String t) {
        genome = g;
        tablename = t;
    }

    public Iterator<NamedTypedRegion> execute(X region) {
        try {
            java.sql.Connection cxn =
                genome.getAnnotationDBConnection();
            PreparedStatement ps = cxn.prepareStatement("select name, strand, type, chromStart, chromEnd from " + tablename + " where chrom = ? and " +
                                                        "((chromStart <= ? and chromEnd >= ?) or (chromStart >= ? and chromStart <= ?)) order by chromStart");
            String chr = region.getChrom();
            if (!chr.matches("^(chr|scaffold).*")) {
                chr = "chr" + chr;
            }
            ps.setString(1,chr);
            ps.setInt(2,region.getStart());
            ps.setInt(3,region.getStart());
            ps.setInt(4,region.getStart());
            ps.setInt(5,region.getEnd());
            ResultSet rs = ps.executeQuery();
            ArrayList<NamedTypedRegion> results = new ArrayList<NamedTypedRegion>();
            while (rs.next()) {
                results.add(new NamedTypedRegion(genome,
                                                 region.getChrom(),
                                                 rs.getInt(4),
                                                 rs.getInt(5),
                                                 rs.getString(1),
                                                 rs.getString(3),
                                                 rs.getString(2).charAt(0)));
                                     
            }
            rs.close();
            ps.close();
            cxn.close();
            return results.iterator();
        } catch (SQLException ex) {
            throw new DatabaseException("Couldn't get UCSC RefGenes",ex);
        }

    }


}
