package com.cmsc818g.StressContextEngine.Reporters;

import java.sql.Connection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

import com.cmsc818g.Utilities.SQLiteHandler;

import akka.actor.ActorPath;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.TimerScheduler;


/**
 * This just makes it easier to group all the reporters together
 * for the Context Engine. 
 * 
 * Since all reporters, at least for the first demo, will need to read from
 * a database, they all share this command. And we want to tell them all at the same
 * time.
 * 
 */
public abstract class Reporter extends AbstractBehavior<Reporter.Command> {


    /**
     * TODO: This may be temporary. As it may not be good practice or necessary
     * to have all reporters use the Reporter.Command as their base behavior.
     */
    public interface Command {}

    public static enum StartReading implements Command {
        INSTANCE
    }

    public static enum StopReading implements Command {
        INSTANCE
    }
    /** TODO:
     * This is a potentially temporary message to keep all reporters aligned on the same
     * row in the database. However, later we can likely do away with the
     * `rowNumber` and instead tell them to read periodically?
     */
    public static final class ReadRowOfData implements Command {
        final int rowNumber;
        final ActorRef<SQLiteHandler.StatusOfRead> replyTo;

        public ReadRowOfData(int rowNumber, ActorRef<SQLiteHandler.StatusOfRead> replyTo) {
            this.rowNumber = rowNumber;
            this.replyTo = replyTo;
        }
    }

    protected static enum TellSelfToRead implements Command {
        INSTANCE
    };

    public interface Response {}

    
    private final String databaseURI;
    private final String tableName;
    private final int readRate;
    private final String timerName;
    private final TimerScheduler<Command> timers;
    protected int currentRow;
    private final ActorRef<SQLiteHandler.StatusOfRead> statusListener;

    public Reporter(
            ActorContext<Command> context,
            TimerScheduler<Command> timers,
            String timerName,
            ActorRef<SQLiteHandler.StatusOfRead> statusListener,
            String databaseURI,
            String tableName,
            int readRate
    ) {
        super(context);

        this.timers = timers;
        this.timerName = timerName;

        this.statusListener = statusListener;
        this.databaseURI = databaseURI;
        this.tableName = tableName;
        this.readRate = readRate;

        this.currentRow = 1;
    }
    
    protected Behavior<Reporter.Command> onStartReading(StartReading msg) {
        this.currentRow = 1;

        getContext().getLog().info("Starting periodic reads of data");
        timers.startTimerAtFixedRate(this.timerName,
                                    TellSelfToRead.INSTANCE,
                                    Duration.ofSeconds(readRate));
        return this;
    }

    protected Behavior<Reporter.Command> onTellSelfToRead(TellSelfToRead msg) {
        getContext().getSelf().tell(new Reporter.ReadRowOfData(this.currentRow, this.statusListener));
        return this;
    }

    protected Behavior<Reporter.Command> onStopReading(StopReading msg) {
        getContext().getLog().info("Stopped reading from database");
        timers.cancel(this.timerName);
        return this;
    }

    protected QueryResponse queryDB(List<String> columnHeaders, ActorPath actorPath, int rowNumber) throws ClassNotFoundException, SQLException {
        String headers = String.join(", ", columnHeaders);
        String sql = String.format("SELECT %s FROM %s WHERE id = ?", headers, tableName);

        QueryResponse response = new QueryResponse();

        try {
            response.conn = SQLiteHandler.connectToDB(databaseURI, statusListener, actorPath);
            response.statement = response.conn.prepareStatement(sql);
            response.statement.setInt(1, rowNumber);
            response.results = SQLiteHandler.queryDB(databaseURI, response.statement, statusListener, actorPath);
        } catch (ClassNotFoundException e) {
            String errorStr = "Failed find the SQLite drivers";
            getContext().getLog().error(errorStr, e);
            getContext().getSelf().tell(StopReading.INSTANCE);
            throw e;
        } catch(SQLException e) {
            String errorStr = String.format("Failed to execute SQL query %s on row %d from actor %s", sql, rowNumber, actorPath);
            getContext().getLog().error(errorStr, e);
            getContext().getSelf().tell();
            throw e;
        }

        return response;
    }

    protected abstract Behavior<Reporter.Command> onReadRowOfData(ReadRowOfData msg)  throws ClassNotFoundException, SQLException;

    protected class QueryResponse {
        public Connection conn;
        public PreparedStatement statement;
        public ResultSet results;
    }
}
