/*
 * Created on Aug 25, 2005
 */
package org.seqcode.gseutils;

import java.io.*;

/**
 * @author tdanford
 */
public interface Saveable {
	public void save(DataOutputStream dos) throws IOException;
}
