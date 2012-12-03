package org.red5.server.plugin.admin.dao;

/*
 * RED5 Open Source Flash Server - http://www.osflash.org/red5
 * 
 * Copyright (c) 2006-2008 by respective authors (see below). All rights reserved.
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
 */

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.derby.jdbc.EmbeddedDataSource;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

/**
 * Database setup for admin.
 * 
 * http://db.apache.org/derby/docs/10.5/ref/
 * jdbc:derby:;databaseName=newDB;create=true
 * 
 * @author The Red5 Project (red5@osflash.org)
 * @author Paul Gregoire (mondain@gmail.com)
 */
public class UserDatabase {

	private static Logger log = Red5LoggerFactory.getLogger(UserDatabase.class, "admin");		
	
	private boolean debug;

	private static String database;

	private static String userName;

	private static String password;

	private static DataSource ds;
	
	public void init() {
		if (debug) {
			System.setProperty("derby.debug.true", "");
		}
		//override derby log
		System.setProperty("derby.stream.error.method", "org.red5.logging.DerbyLogInterceptor.handleDerbyLogFile");
		
		String prop = System.getenv("DERBY_HOME");
		if (prop == null) {
			prop = System.getProperty("user.home");
		}
		System.setProperty("derby.system.home", prop);
		log.debug("Setting derby home to: {}", prop);

		Connection conn = null;
		Statement stmt = null;
		try {
            // JDBC stuff
			try {
				if (ds == null) {
					log.debug("Creating new db - name: {} user: {} password: {}", new Object[]{database, userName, password});
					EmbeddedDataSource eds = new EmbeddedDataSource();
					eds.setCreateDatabase("create");
					eds.setDataSourceName(database);
					eds.setDatabaseName(database);
					eds.setPassword(password);
					eds.setUser(userName);

					ds = eds;
				}
			} catch (Exception e) {
				log.error("Context check for datasource", e);
			}
			
			if (ds == null) {
				log.error("No usable datasource is set");
				return;
			}
			
			//create the db and get a connection
			conn = ds.getConnection();
			//make a statement
			stmt = conn.createStatement();
			//check for table
			ResultSet rs = stmt.executeQuery("SELECT t.tablename FROM sys.systables t WHERE t.tabletype = 'T' AND t.tablename = 'APPUSER'");
			if (rs.next()) {
				String tableName = rs.getString("tablename");
				if (tableName != null) {
					log.debug("Tablename: {}", tableName);
					rs.close();
					//check for entries
					rs = stmt.executeQuery("SELECT COUNT(userid) FROM APPUSER");
					if (rs.next()) {
						log.debug("Records: {}", rs.getInt(1));
					}
				}
			} else {
				log.debug("APPUSER table was not found");
				//create the table
				if (stmt.execute("CREATE TABLE APPUSER(userid INT GENERATED BY DEFAULT AS IDENTITY, username VARCHAR(16), password VARCHAR(36), enabled VARCHAR(7) NOT NULL)")) {
					log.debug("Create user table executed");
				}
			}
			rs.close();
			//check for table
			rs = stmt.executeQuery("SELECT t.tablename FROM sys.systables t WHERE t.tabletype = 'T' AND t.tablename = 'APPROLE'");
			if (rs.next()) {			
				String tableName = rs.getString("tablename");
				if (tableName != null) {
					log.debug("Tablename: {}", tableName);
				}
			} else {
    			log.debug("APPROLE table was not found");
    			//create the table
    			if (stmt.execute("CREATE TABLE APPROLE(username VARCHAR(16) NOT NULL PRIMARY KEY, authority VARCHAR(16) NOT NULL)")) {
    				log.debug("Create role table executed");
    			}
    		}			
			rs.close();			
			
		} catch (Exception e) {
			log.error("Error in db setup", e);
		} finally {
			if (stmt != null) {
				try {
					stmt.close();
				} catch (SQLException e) {
				}
			}
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException e) {
				}
			}
		}

	}

	public void shutdown() {
		log.debug("Shutdown {} db", UserDatabase.database);
		try {
			//DriverManager.getConnection("jdbc:derby:" + UserDatabase.database + ";shutdown=true");
            ((EmbeddedDataSource) ds).setShutdownDatabase("shutdown");            
		} catch (Exception e) {
			log.debug("Error in db shutdown", e);
		}
	}
	
	public boolean isDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public static String getDatabase() {
		return database;
	}

	public void setDatabase(String database) {
		UserDatabase.database = database;
	}

	public static String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		UserDatabase.userName = userName;
	}

	public static String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		UserDatabase.password = password;
	}

	public static DataSource getDataSource() {
		return ds;
	}	
	
}