package org.openas2;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.h2.jdbcx.JdbcConnectionPool;
import org.h2.tools.Server;
import org.openas2.params.InvalidParameterException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 *
 * @author Luc Guinchard
 */
public class DBFactory implements IDBHandler {

	private static final Log logger = LogFactory.getLog(DBFactory.class.getSimpleName());
	public static final String PARAM_JDBC = "jdbc:";
	public static final int COMMENT_MAX_LENTGH = 2000;

	public static final String CONFIG_NAMED_NODE_NAME = "name";

	public static final String CONFIG_NAMED_NODE_DB_USER = "db_user";
	public static final String CONFIG_NAMED_NODE_DB_PASSWORD = "db_pwd";
	public static final String CONFIG_NAMED_NODE_DB_DIRECTORY = "db_directory";
	public static final String CONFIG_NAMED_NODE_JDBC_DRIVER = "jdbc_driver";
	public static final String CONFIG_NAMED_NODE_JDBC_SERVER_URL = "jdbc_server_url";
	public static final String CONFIG_NAMED_NODE_JDBC_EXTRA_PARAMTERS = "jdbc_extra_paramters";
	public static final String CONFIG_NAMED_NODE_JDBC_CONNECT_STRING = "jdbc_connect_string";
	public static final String CONFIG_NAMED_NODE_SQL_ESCAPE_CHARACTER = "sql_escape_character";
	public static final String CONFIG_NAMED_NODE_TCP_SERVER_START = "tcp_server_start";
	public static final String CONFIG_NAMED_NODE_TCP_SERVER_PORT = "tcp_server_port";
	public static final String CONFIG_NAMED_NODE_TCP_SERVER_PASSWORD = "tcp_server_password";
	public static final String CONFIG_NAMED_NODE_USE_EMBEDDED_DB = "use_embedded_db";
	public static final String CONFIG_NAMED_NODE_FORCE_LOAD_JDBC_DRIVER = "force_load_jdbc_driver";

	public static HashMap<String, DBFactory> DBFactoryList = new HashMap();

	private String dbName = "openas2";
	private String dbUser = "openas2";
	private String dbPwd = "OpenAS2";
	private String jdbcConnectString = null;
	private String configBaseDir = null;
	private String jdbcDriver = null;
	private boolean running = false;
	private String sqlEscapeChar = "'";
	private boolean useEmbeddedDB = true;
	private boolean forceLoadJdbcDriver = false;
	private boolean tcpServerStart = true;
	private Integer tcpServerPort = 3306;
	private String tcpServerPassword = null;
	private String dbDirectory = "";

	private String dbPlatform = "h2";

	@Nullable
	private JdbcConnectionPool cp = null;
	private Server server = null;

	private Connection connection;

	public DBFactory(NamedNodeMap namedNodeMap) throws InvalidParameterException, Exception {
		try {
			Node node;
			node = namedNodeMap.getNamedItem(DBFactory.CONFIG_NAMED_NODE_DB_USER);
			if (node != null) {
				dbUser = node.getNodeValue();
			}
			node = namedNodeMap.getNamedItem(DBFactory.CONFIG_NAMED_NODE_DB_PASSWORD);
			if (node != null) {
				dbPwd = node.getNodeValue();
			}
			node = namedNodeMap.getNamedItem(DBFactory.CONFIG_NAMED_NODE_TCP_SERVER_START);
			if (node != null) {
				tcpServerStart = Boolean.parseBoolean(node.getNodeValue());
			}
			// Support component attributes in connect string
			node = namedNodeMap.getNamedItem(DBFactory.CONFIG_NAMED_NODE_JDBC_CONNECT_STRING);
			if (node != null) {
				jdbcConnectString = node.getNodeValue();
				dbPlatform = jdbcConnectString.replaceAll(".*jdbc:([^:]*):.*", "$1");
			}

			node = namedNodeMap.getNamedItem(DBFactory.CONFIG_NAMED_NODE_JDBC_DRIVER);
			if (node != null) {
				jdbcDriver = node.getNodeValue();
			}
			node = namedNodeMap.getNamedItem(DBFactory.CONFIG_NAMED_NODE_SQL_ESCAPE_CHARACTER);
			if (node != null) {
				sqlEscapeChar = node.getNodeValue();
			}
			node = namedNodeMap.getNamedItem(DBFactory.CONFIG_NAMED_NODE_USE_EMBEDDED_DB);
			if (node != null) {
				useEmbeddedDB = Boolean.parseBoolean(node.getNodeValue());
			}
			node = namedNodeMap.getNamedItem(DBFactory.CONFIG_NAMED_NODE_FORCE_LOAD_JDBC_DRIVER);
			if (node != null) {
				forceLoadJdbcDriver = Boolean.parseBoolean(node.getNodeValue());
			}
			node = namedNodeMap.getNamedItem(DBFactory.CONFIG_NAMED_NODE_TCP_SERVER_PORT);
			if (node != null) {
				tcpServerPort = Integer.parseInt(node.getNodeValue());
			}
			node = namedNodeMap.getNamedItem(DBFactory.CONFIG_NAMED_NODE_TCP_SERVER_PASSWORD);
			if (node != null) {
				tcpServerPassword = node.getNodeValue();
			}
			node = namedNodeMap.getNamedItem(DBFactory.CONFIG_NAMED_NODE_DB_DIRECTORY);
			if (node != null) {
				dbDirectory = node.getNodeValue();
			}
		} catch (Exception e) {
			logger.error("the configuration of the database is not right.");
			StringBuilder builder = new StringBuilder("\n------ SAMPLE CONFIGURATION ------\n");
			builder.append("<").append(XMLSession.EL_DATABASECONFIG).append("  name=\"as2_db\"\n\t")
					.append(CONFIG_NAMED_NODE_DB_USER).append("=\"").append(dbUser).append("\"\n\t")
					.append(CONFIG_NAMED_NODE_DB_PASSWORD).append("=\"").append(dbPwd).append("\"\n\t")
					.append(CONFIG_NAMED_NODE_JDBC_DRIVER).append("=\"").append(jdbcDriver).append("\"\n\t")
					.append(CONFIG_NAMED_NODE_JDBC_CONNECT_STRING).append("=\"").append(jdbcConnectString).append("\"\n\t")
					.append(CONFIG_NAMED_NODE_SQL_ESCAPE_CHARACTER).append("=\"").append(sqlEscapeChar).append("\"\n\t")
					.append(CONFIG_NAMED_NODE_TCP_SERVER_START).append("=\"").append(tcpServerStart).append("\"\n\t")
					.append(CONFIG_NAMED_NODE_TCP_SERVER_PORT).append("=\"").append(tcpServerPort).append("\"\n\t")
					.append(CONFIG_NAMED_NODE_TCP_SERVER_PASSWORD).append("=\"").append(tcpServerPassword).append("\"\n\t")
					.append(CONFIG_NAMED_NODE_USE_EMBEDDED_DB).append("=\"").append(useEmbeddedDB).append("\"\n\t")
					.append("/>");
			logger.info(builder);
			throw e;
		}

		if (!useEmbeddedDB && forceLoadJdbcDriver) {
			try {
				Class.forName(jdbcDriver);
			} catch (ClassNotFoundException e) {
				logger.error("Failed to load JDBC driver: " + jdbcDriver, e);
				e.printStackTrace();
				return;
			}
		}
	}

	public static DBFactory getDBFactory(String dbConfig) throws OpenAS2Exception {
		DBFactory dBFactory = null;
		if (dbConfig != null) {
			dBFactory = DBFactoryList.get(dbConfig);
			if (dBFactory == null) {
				if (DBFactoryList.size() != 1) {
					throw new OpenAS2Exception("A " + XMLSession.EL_DATABASECONFIG + " '" + dbConfig + "' is missing!");
				}
				dBFactory = DBFactoryList.values().iterator().next();
				logger.info("Connection to default DBFactory: " + DBFactoryList.keySet().iterator().next());
			}
			logger.debug("Connection to URL: " + dBFactory.jdbcConnectString);
		} else {
			if (DBFactoryList.size() == 1) {
				dBFactory = DBFactoryList.values().iterator().next();
				logger.info("Connection to default DBFactory: " + DBFactoryList.keySet().iterator().next());
			} else {
				logger.info("No DBFactory.");
			}
		}
		return dBFactory;
	}

	public void appendFieldForUpdate(String name, String value, StringBuffer sb, int dataType) {
		if (sb.length() > 0) {
			sb.append(",");
		}

		sb.append(name).append("=").append(formatField(value, dataType));

	}

	public void appendFieldForInsert(String name, String value, StringBuffer names, StringBuffer values, int dataType) {
		if (names.length() > 0) {
			names.append(",");
			values.append(",");
		}

		names.append(name);
		values.append(formatField(value, dataType));

	}

	private String formatField(String value, int dataType) {
		if (value == null) {
			return "NULL";
		}
		switch (dataType) {
			case Types.BIGINT:
			case Types.DECIMAL:
			case Types.DOUBLE:
			case Types.FLOAT:
			case Types.INTEGER:
			case Types.NUMERIC:
			case Types.REAL:
			case Types.SMALLINT:
			case Types.BINARY:
			case Types.TINYINT:
				//case Types.ROWID:
				return value;
			case Types.TIME_WITH_TIMEZONE:
			case Types.TIMESTAMP_WITH_TIMEZONE:
			case Types.DATE:
			case Types.TIME:
			case Types.TIMESTAMP:
				if ("oracle".equalsIgnoreCase(dbPlatform)) {
					if (value.length() > 19) {
						return ("TO_TIMESTAMP('" + value + "','YYYY-MM-DD HH24:MI:SS.FF')");
					} else {
						return ("TO_DATE('" + value + "','YYYY-MM-DD HH24:MI:SS')");
					}
				} else if ("mssql".equalsIgnoreCase(dbPlatform)) {
					return ("CAST('" + value + "' AS DATETIME)");
				} else {
					return "'" + value + "'";
				}

		}
		// Must be some kind of string value if it gets here
		return "'" + value.replaceAll("'", sqlEscapeChar + "'") + "'";

	}

	@Override
	public void start() throws OpenAS2Exception {
		try {
			if (connection == null || connection.isClosed()) {
				if (useEmbeddedDB) {
					createConnectionPool(jdbcConnectString, dbUser, dbPwd);
					if (tcpServerStart) {
						server = Server.createTcpServer("-tcpPort", tcpServerPort.toString(), "-tcpPassword", tcpServerPassword, "-baseDir", dbDirectory, "-tcpAllowOthers");
						server.start();
					}
				} else {
					connection = DriverManager.getConnection(jdbcConnectString, dbUser, dbPwd);
				}
				connection.setAutoCommit(true);
			}
		} catch (SQLException ex) {
			ex.printStackTrace();
			throw new OpenAS2Exception(ex);
		}
		running = true;
	}

	public void stop() {
		if (!useEmbeddedDB) {
			return;
		}

		if (server != null) {
			server.stop();
		} else {
			try {
				shutdown(jdbcConnectString);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			destroyConnectionPool();
		}
		running = false;
	}

	/**
	 * @return the dbName
	 */
	public String getDbName() {
		return dbName;
	}

	/**
	 * @return the dbUser
	 */
	public String getDbUser() {
		return dbUser;
	}

	/**
	 * @return the dbPwd
	 */
	public String getDbPwd() {
		return dbPwd;
	}

	/**
	 * @return the jdbcConnectString
	 */
	public String getJdbcConnectString() {
		return jdbcConnectString;
	}

	/**
	 * @return the configBaseDir
	 */
	public String getConfigBaseDir() {
		return configBaseDir;
	}

	/**
	 * @return the jdbcDriver
	 */
	public String getJdbcDriver() {
		return jdbcDriver;
	}

	/**
	 * @return the running
	 */
	public boolean isRunning() {
		return running;
	}

	/**
	 * @return the sqlEscapeChar
	 */
	public String getSqlEscapeChar() {
		return sqlEscapeChar;
	}

	/**
	 * @return the useEmbeddedDB
	 */
	public boolean isUseEmbeddedDB() {
		return useEmbeddedDB;
	}

	/**
	 * @return the forceLoadJdbcDriver
	 */
	public boolean isForceLoadJdbcDriver() {
		return forceLoadJdbcDriver;
	}

	/**
	 * @return the tcpServerStart
	 */
	public boolean isTcpServerStart() {
		return tcpServerStart;
	}

	/**
	 * @return the tcpServerPort
	 */
	public Integer getTcpServerPort() {
		return tcpServerPort;
	}

	/**
	 * @return the tcpServerPassword
	 */
	public String getTcpServerPassword() {
		return tcpServerPassword;
	}

	/**
	 * @return the dbDirectory
	 */
	public String getDbDirectory() {
		return dbDirectory;
	}

	/**
	 * @return the dbPlatform
	 */
	public String getDbPlatform() {
		return dbPlatform;
	}

	/**
	 * @return the cp
	 */
	public JdbcConnectionPool getCp() {
		return cp;
	}

	/**
	 * @return the server
	 */
	public Server getServer() {
		return server;
	}

	@Override
	public Connection getConnection() {
		return connection;
	}

	@Override
	public boolean shutdown(String connectString) throws SQLException, OpenAS2Exception {
		// Wait briefly if there are active connections
		int waitCount = 0;
		try {
			while (cp != null && cp.getActiveConnections() > 0 && waitCount < 10) {
				TimeUnit.MILLISECONDS.sleep(100);
				waitCount++;
			}
		} catch (InterruptedException e) {
			// Do nothing
		}
		Connection c = getConnection();
		Statement st = c.createStatement();

		boolean result = st.execute("SHUTDOWN");
		c.close();
		return result;
	}

	@Override
	public void destroyConnectionPool() {
		if (cp == null) {
			return;
		}
		cp.dispose();
		cp = null;
	}

	@Override
	public void createConnectionPool(String jdbcConnectString, String dbUser, String dbPwd) throws OpenAS2Exception {
		if (cp != null) {
			throw new OpenAS2Exception(
					"Connection pool already initialized. Cannot create a new connection pool. Stop current one first. DB connect string:"
					+ jdbcConnectString + " :: Active pool connect string: ");
		}

		cp = JdbcConnectionPool.create(jdbcConnectString, dbUser, dbPwd);
	}
}
