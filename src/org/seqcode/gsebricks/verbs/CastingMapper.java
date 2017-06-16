/*
 * Created on Mar 20, 2006
 */
package org.seqcode.gsebricks.verbs;

/**
 * @author tdanford
 */
public class CastingMapper<A, B> implements Mapper<A, B> {

	public CastingMapper() {
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.seqcode.gsebricks.verbs.Filter#execute(null)
	 */
	public B execute(A a) {
		return (B) a;
	}

}
