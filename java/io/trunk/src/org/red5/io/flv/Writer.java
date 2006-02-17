package org.red5.io.flv;

/*
 * RED5 Open Source Flash Server - http://www.osflash.org/red5
 * 
 * Copyright � 2006 by respective authors. All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or modify it under the 
 * terms of the GNU Lesser General Public License as published by the Free Software 
 * Foundation; either version 2.1 of the License, or (at your option) any later 
 * version. 
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along 
 * with this library; if not, write to the Free Software Foundation, Inc., 
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA 
 * 
 * @author The Red5 Project (red5@osflash.org)
 * @author Dominick Accattato (daccattato@gmail.com)
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 */


import java.io.IOException;
import org.apache.mina.common.ByteBuffer;

/**
 * A Writer is used to write the contents of a FLV file
 * 
 * @author The Red5 Project (red5@osflash.org)
 * @author Dominick Accattato (daccattato@gmail.com)
 * @author Luke Hubbard, Codegent Ltd (luke@codegent.com)
 * @version 0.3
 */
public interface Writer {
	
	/**
	 * Return a FLV object
	 * @return FLV flvObject
	 */
	public FLV getFLV();
	
	/**
	 * Return the offset
	 * @return long offset
	 */
	public long getOffset();
	
	/**
	 * Return the bytes written
	 * @return long bytesWritten
	 */
	public long getBytesWritten();
	
	/**
	 * Writes a Tag object
	 * @param tag
	 * @return boolean 
	 * @throws IOException
	 */
	public boolean writeTag(Tag tag) throws IOException;
	
	/**
	 * Write a Tag using bytes
	 * @param type
	 * @param data
	 * @return boolean
	 * @throws IOException
	 */
	public boolean writeTag(byte type, ByteBuffer data) throws IOException;
	
	/**
	 * Write a Stream to disk using bytes
	 * @param b
	 * @return boolean
	 * @throws IOException
	 */
	public boolean writeStream(byte[] b);
	
	/**
	 * Closes a Writer
	 * @return void
	 */
	public void close();

	
}
