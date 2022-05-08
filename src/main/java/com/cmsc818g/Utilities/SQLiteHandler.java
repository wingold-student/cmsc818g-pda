package com.cmsc818g.Utilities;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import akka.actor.ActorPath;
import akka.actor.typed.ActorRef;

public interface SQLiteHandler {
    /**
     * Holds data that may be useful for debugging / information.
     * 
     * Mainly received by the Context Engine
     */
    public static final class StatusOfRead {
        public final boolean success;
        public final String message;
        public final ActorPath actorPath;

        public StatusOfRead(boolean success, String message, ActorPath actorPath) {
            this.success = success;
            this.message = message;
            this.actorPath = actorPath;
        }
    }

    /**
     * Connects to the requested database.
     * 
     * @param databaseURI
     * @param replyTo
     * @return
     * @throws ClassNotFoundException
     * @throws SQLException
     */
    static Connection connectToDB(String databaseURI,
                                    ActorRef<StatusOfRead> replyTo,
                                    ActorPath myPath) throws ClassNotFoundException, SQLException {
        Connection conn = null;
        String errorMsg = null;

        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(databaseURI);
        } catch (SQLException e) {
            errorMsg = "Failed to connect to the database: " + databaseURI;
            replyTo.tell(new StatusOfRead(false, errorMsg, myPath));
            throw e;
        }

        return conn;
    }

    /**
     * Executes the query on the database.
     * 
     * @param databaseURI
     * @param statement
     * @param replyTo
     * @param myPath
     * @return
     * @throws SQLException
     */
    static ResultSet queryDB(String databaseURI,
                            PreparedStatement statement,
                            ActorRef<StatusOfRead> replyTo,
                            ActorPath myPath) throws SQLException {

        ResultSet results = null;

        try {
            results = statement.executeQuery();
        } catch (SQLException e) {
            String errorMsg = "Failed query: " + statement.toString();
            replyTo.tell(new StatusOfRead(false, errorMsg, myPath));
            throw e;
        }

        return results;
    }
}
