package com.cmsc818g.StressContextEngine.Reporters;

import java.sql.Connection;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;

import akka.actor.ActorPath;
import akka.actor.typed.ActorRef;


/**
 * This just makes it easier to group all the reporters together
 * for the Context Engine. 
 * 
 * Since all reporters, at least for the first demo, will need to read from
 * a database, they all share this command. And we want to tell them all at the same
 * time.
 * 
 */
public interface Reporter {

    /**
     * TODO: This may be temporary. As it may not be good practice or necessary
     * to have all reporters use the Reporter.Command as their base behavior.
     */
    public interface Command {}

    /** TODO:
     * This is a potentially temporary message to keep all reporters aligned on the same
     * row in the database. However, later we can likely do away with the
     * `rowNumber` and instead tell them to read periodically?
     */
    public static final class ReadRowOfData implements Command {
        final int rowNumber;
        final ActorRef<StatusOfRead> replyTo;

        public ReadRowOfData(int rowNumber, ActorRef<StatusOfRead> replyTo) {
            this.rowNumber = rowNumber;
            this.replyTo = replyTo;
        }
    }

    public interface Response {}

    /**
     * Holds data that may be useful for debugging / information.
     * 
     * Mainly received by the Context Engine
     */
    public static final class StatusOfRead implements Response {
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
     * @param logger
     * @param replyTo
     * @return
     * @throws ClassNotFoundException
     * @throws SQLException
     */
    static Connection connectToDB(String databaseURI,
                                    Logger logger,
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
     * @param logger
     * @param replyTo
     * @param myPath
     * @return
     * @throws SQLException
     */
    static ResultSet queryDB(String databaseURI,
                            PreparedStatement statement,
                            Logger logger,
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
