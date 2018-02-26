package org.openas2.processor.msgtracking;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.DBFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.XMLSession;
import org.openas2.message.Message;
import org.openas2.params.ComponentParameters;
import org.openas2.params.CompositeParameters;
import org.openas2.util.DateUtil;

public class DbTrackingModule extends BaseMsgTrackingModule {
	private final Log logger = LogFactory.getLog(DbTrackingModule.class.getSimpleName());
	public static final String PARAM_DB_TABLE = "db_table";

	private String dbConfig = null;
	private String dbTable = null;

	private DBFactory dBFactory;

	@Override
	public void init(Session session, Map<String, String> options) throws OpenAS2Exception {
		super.init(session, options);
		dbConfig = getParameter(XMLSession.EL_DATABASECONFIG, null);
		dbTable = getParameter(PARAM_DB_TABLE, "msg_metadata");
		dBFactory = DBFactory.getDBFactory(dbConfig);
		try {
			Connection conn = dBFactory.getConnection();
			Statement statement = conn.createStatement();
			ResultSet resultat = statement.executeQuery("SELECT 1 FROM `" + dbTable + "` LIMIT 1;");
			while (resultat.next()) {
			}

		} catch (SQLException e) {
			logger.error("Error in module " + getClass().getName());
			logger.error(e.getMessage());
			StringBuilder builder = new StringBuilder("\n------ CREATE TABLE ------").append("\n");
			builder.append("CREATE TABLE `").append(dbTable).append("` (").append("\n")
					.append("`ID` int(11) NOT NULL AUTO_INCREMENT,\n")
					.append("`MSG_ID` longtext NOT NULL,\n")
					.append("`MDN_ID` longtext,\n")
					.append("`DIRECTION` varchar(25) DEFAULT NULL,\n")
					.append("`IS_RESEND` varchar(1) DEFAULT 'N',\n")
					.append("`RESEND_COUNT` int(11) DEFAULT '0',\n")
					.append("`SENDER_ID` varchar(255) NOT NULL,\n")
					.append("`RECEIVER_ID` varchar(255) NOT NULL,\n")
					.append("`STATUS` varchar(255) DEFAULT NULL,\n")
					.append("`STATE` varchar(255) DEFAULT NULL,\n")
					.append("`SIGNATURE_ALGORITHM` varchar(255) DEFAULT NULL,\n")
					.append("`ENCRYPTION_ALGORITHM` varchar(255) DEFAULT NULL,\n")
					.append("`COMPRESSION` varchar(255) DEFAULT NULL,\n")
					.append("`FILE_NAME` varchar(255) DEFAULT NULL,\n")
					.append("`CONTENT_TYPE` varchar(255) DEFAULT NULL,\n")
					.append("`CONTENT_TRANSFER_ENCODING` varchar(255) DEFAULT NULL,\n")
					.append("`MDN_MODE` varchar(255) DEFAULT NULL,\n")
					.append("`MDN_RESPONSE` longtext,\n")
					.append("`STATE_MSG` longtext,\n")
					.append("`CREATE_DT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,\n")
					.append("`UPDATE_DT` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',\n")
					.append("PRIMARY KEY (`ID`)\n")
					.append(") ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=latin1\n");

			builder.append("------------").append("\n");
			logger.info(builder);
			throw new OpenAS2Exception(e.getMessage());
		}
	}

	@Override
	protected String getModuleAction() {
		return DO_TRACK_MSG;
	}

	protected CompositeParameters createParser() {
		CompositeParameters params = new CompositeParameters(true);

		params.add("component", new ComponentParameters(this));
		return params;
	}

	@Override
	protected void persist(Message msg, Map<String, String> map) {
		Connection conn = null;
		try {
			dBFactory = DBFactory.getDBFactory(dbConfig);
			conn = dBFactory.getConnection();
			Statement s = conn.createStatement();
			String msgIdField = FIELDS.MSG_ID;
			ResultSet rs = s.executeQuery(
					"select * from " + dbTable + " where " + msgIdField + " = '" + map.get(msgIdField) + "'");
			ResultSetMetaData meta = rs.getMetaData();
			boolean isUpdate = rs.next(); // Record already exists so update
			StringBuffer fieldStmt = new StringBuffer();
			StringBuffer valuesStmt = new StringBuffer();
			for (int i = 0; i < meta.getColumnCount(); i++) {
				String colName = meta.getColumnLabel(i + 1);
				if (colName.equalsIgnoreCase("ID")) {
					continue;
				} else if (colName.equalsIgnoreCase(FIELDS.UPDATE_DT)) {
					// Ignore if not update mode
					if (isUpdate) {
						dBFactory.appendFieldForUpdate(colName, DateUtil.getSqlTimestamp(), fieldStmt, meta.getColumnType(i + 1));
					}
				} else if (colName.equalsIgnoreCase(FIELDS.CREATE_DT)) {
					map.remove(FIELDS.CREATE_DT);
				} else if (isUpdate) {
					// Only write unchanged field values
					String mapVal = map.get(colName.toUpperCase());
					if (mapVal == null) {
						continue;
					}
					String dbVal = rs.getString(colName);
					if (dbVal != null && mapVal.equals(dbVal)) {
						// Unchanged value so remove from map
						continue;
					}
					dBFactory.appendFieldForUpdate(colName, mapVal, fieldStmt, meta.getColumnType(i + 1));
				} else {
					// For new record add every field that is not NULL
					String mapVal = map.get(colName.toUpperCase());
					if (mapVal == null) {
						continue;
					}
					dBFactory.appendFieldForInsert(colName, mapVal, fieldStmt, valuesStmt, meta.getColumnType(i + 1));
				}
			}
			if (fieldStmt.length() > 0) {
				String stmt = "";
				if (isUpdate) {
					stmt = "update " + dbTable + " set " + fieldStmt.toString() + " where " + FIELDS.MSG_ID + " = '"
							+ map.get(msgIdField) + "'";
				} else {
					stmt = "insert into " + dbTable + " (" + fieldStmt.toString() + ") values (" + valuesStmt.toString() + ")";
				}
				if (s.executeUpdate(stmt) > 0) {
					if (logger.isDebugEnabled()) {
						logger.debug("Tracking record successfully persisted to database: " + map);
					}
				} else {
					throw new OpenAS2Exception("Failed to persist tracking record to DB: " + map);
				}
			} else {
				if (logger.isInfoEnabled()) {
					logger.info("No change from existing record in DB. Tracking record not updated: " + map);
				}
			}
		} catch (Exception e) {
			msg.setLogMsg("Failed to persist a tracking event: " + org.openas2.logging.Log.getExceptionMsg(e)
					+ " ::: Data map: " + map);
			logger.error(msg, e);
		}

	}

	@Override
	public boolean isRunning() {
		return dBFactory.isRunning();
	}

	@Override
	public void start() throws OpenAS2Exception {
		dBFactory.start();
	}

	@Override
	public void stop() {
		dBFactory.stop();
	}

	@Override
	public boolean healthcheck(List<String> failures) {
		Connection conn = null;
		try {
			conn = dBFactory.getConnection();
			Statement s = conn.createStatement();
			ResultSet rs = s.executeQuery(
					"select count(*) from " + dbTable);
		} catch (Exception e) {
			failures.add(this.getClass().getSimpleName()
					+ " - Failed to check DB tracking module connection to DB: " + e.getMessage()
					+ " :: Connect String: " + dBFactory.getJdbcConnectString());
			return false;
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (Exception e) {
				}
			}
		}

		return true;
	}

}
