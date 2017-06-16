/*
 * Created on Mar 11, 2006
 */
package org.seqcode.genome.sequence;

import java.util.*;

import org.seqcode.gsebricks.verbs.Expander;

/**
 * @author tdanford
 */
public class StringToKMers implements Expander<String, String> {

	private int width;

	public StringToKMers(int w) {
		width = w;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.seqcode.gsebricks.verbs.Expander#execute(null)
	 */
	public Iterator<String> execute(String base) {
		LinkedList<String> kmerList = new LinkedList<String>();
		for (int i = 0; i < base.length() - width; i++) {
			kmerList.addLast(base.substring(i, i + width));
		}
		return kmerList.iterator();
	}

}
