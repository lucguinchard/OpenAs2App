package org.openas2;

import java.sql.Connection;
import java.sql.SQLException;

public interface IDBHandler {

	void createConnectionPool(String connectString, String userName, String pwd) throws OpenAS2Exception;

	void destroyConnectionPool();

	Connection getConnection() throws SQLException, OpenAS2Exception;

	boolean shutdown(String connectString) throws SQLException, OpenAS2Exception;

	void start() throws OpenAS2Exception;

	void stop();
}
