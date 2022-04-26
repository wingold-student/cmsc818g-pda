package com.cmsc818g.StressContextEngine.Reporters;

import java.util.Optional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.slf4j.Logger;

import com.cmsc818g.StressRecommendationEngine.StressRecommendationEngine;

import akka.actor.ActorPath;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.model.DateTime;

public class LocationReporter extends AbstractBehavior<Reporter.Command>{

        /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public interface Command extends Reporter.Command {}

    /** Main request for this actor, asking for the blood pressure reading. */
    public static final class ReadLocation implements Command {
        final ActorRef<LocationReading> replyTo;

        public ReadLocation(ActorRef<LocationReading> replyTo) {
            this.replyTo = replyTo;
        }
    }

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/
    public interface Response extends Reporter.Response {}

    /** Main response for this actor, replying with the blood pressure reading */
    public static final class LocationReading implements Response {

        // Note it's possible there was no reading (NULL), so it is `Optional`
        public final Optional<UserLocation> value;

        public LocationReading(Optional<UserLocation> value) {
            this.value = value;
        }
    }

    /************************************* 
     * CREATION 
     *************************************/

    /**
     * Factory creator of the BloodPressureReporter.
     * 
     * Note that if it encounters a SQLException error, it will be logged,
     * but the actor will simply resume, rather than crash.
     * 
     * @param databaseURI URI to the database for it to read
     * @param tableName Name of the table it should read from in the database
     * @return Behavior that it will take Reporter.Commands (or BloodPressureReporter.Commands)
     */
    public static Behavior<Reporter.Command> create(String databaseURI, String tableName) {
        return Behaviors.<Reporter.Command>supervise(
            Behaviors.setup(
                context -> new LocationReporter(context, databaseURI, tableName)
            )
        ).onFailure(SQLException.class, SupervisorStrategy.resume());
    }

    private final String databaseURI;
    private final String tableName;
    private Optional<UserLocation> lastReading;

    /**
     * Creates a BloodPressureReporter.
     * 
     * Note that `lastReading` starts as empty, since it hasn't read yet.
     * @param context Context for this actor (in the system)
     * @param databaseURI URI to the database to read from for blood pressure
     * @param tableName Table in the database to use
     */
    private LocationReporter(ActorContext<Reporter.Command> context, String databaseURI, String tableName) {
        super(context);
        this.databaseURI = databaseURI;
        this.tableName = tableName;
        this.lastReading = Optional.empty();
    }

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<Reporter.Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(Reporter.ReadRowOfData.class, this::onReadRowOfData)
            .onMessage(ReadLocation.class, this::onReadLocation)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    /**
     * Will query the database for a single row of data at the provided id (row).
     * 
     * @param msg Contains what row to read from the database
     * @return itself as unchanged
     * @throws ClassNotFoundException if the SQL driver cannot be loaded/found
     * @throws SQLException if there is an issue with SQL query/database
     */
    private Behavior<Reporter.Command> onReadRowOfData(Reporter.ReadRowOfData msg) throws ClassNotFoundException, SQLException {
        // Used for messages later
        Logger logger = getContext().getLog();
        ActorPath myPath = getContext().getSelf().path();

        // Forms a connection to the database
        Connection conn = Reporter.connectToDB(this.databaseURI, logger, msg.replyTo, myPath);

        try {
            // Query itself with variables to be filled
            String query = "SELECT id, DateTime, Location FROM " + this.tableName + " WHERE id = ?";

            // Form a statement and fill in the variables
            PreparedStatement statement;
            statement = conn.prepareStatement(query);
            statement.setInt(1, msg.rowNumber); // `id` is the only variable ( ? )

            ResultSet results = Reporter.queryDB(this.databaseURI, statement, logger, msg.replyTo, myPath);

            // Need to call `.next()` as the iterator starts before the data
            // If no data, then it will return null
            if (results.next()) {

                // The cell in the database can be empty/null
                Optional<String> reading = Optional.ofNullable(results.getString("Location"));

                // Not expecting DateTime to not exist though, so can just get it
                String dateTimeStr = results.getString("DateTime");

                // Convert into DateTime, could fail, hence `Optional`
                Optional<DateTime> readingTime = DateTime.fromIsoDateTimeString(dateTimeStr);

                // Create the latest reading only if all data is there
                if (reading.isPresent() && readingTime.isPresent()) {
                    String[] high_low = reading.get().split("/");
                    this.lastReading = Optional.of(new UserLocation(readingTime, high_low[0], high_low[1]));
                }

                results.close();

                // Tell the Context Engine we've successfully read
                msg.replyTo.tell(new Reporter.StatusOfRead(true, "Succesfully read row " + msg.rowNumber, myPath));
                
            } else {

                // Tell the Context Engine we had a problem
                msg.replyTo.tell(new Reporter.StatusOfRead(false, "No results from row " + msg.rowNumber, myPath));
            }
            
        conn.close();

        } catch (SQLException e) {
            // Tell the Context Engine we had a problem
            String errorMsg = "Failed to prepare statement";
            msg.replyTo.tell(new Reporter.StatusOfRead(false, errorMsg, myPath));

            // Throwing it because it'll log this way. And we could handle differently
            // later if we want
            throw e;
        }

        return this;
    }

    /**
     * When getting a `ReadBloodPressure` message, respond back with last reading.
     * @param msg A request with who to reply back to with the reading
     * @return itself unchanged
     */
    private Behavior<Reporter.Command> onReadLocation(ReadLocation msg) {
        msg.replyTo.tell(new LocationReading(this.lastReading));
        return this;
    }

    /**
     * Could do any necessary cleanup here.
     * 
     * This is essentially when the actor is killed by parent/system shutting down.
     * @return itself
     */
    private LocationReporter onPostStop() {
        getContext().getSystem().log().info("BloodPressure Reporter stopped");
        return this;
    }

    /************************************* 
     * HELPER CLASSES
     *************************************/
    public class UserLocation {
        final Optional<DateTime> readingTime;
        final int systolic;
        final int diastolic;

        public UserLocation(Optional<DateTime> readingTime, String upper, String lower) {
            this.readingTime = readingTime;
            this.systolic = Integer.parseInt(upper);
            this.diastolic = Integer.parseInt(lower);
        }
        public int getSystolicBP(){
            return systolic;
        }
        public int getDiastolicBP(){
            return diastolic;
        }
    }
    // public static Behavior<LocationReporter.Command> create(String reporterId, String groupId) {
    //     return Behaviors.setup(context -> new LocationReporter(context, reporterId, groupId));
    // }

    // public interface Command {}

    // public static final class AskLocation implements Command {
    //     private ActorRef<StressRecommendationEngine.LocationReporterToRecommendation> replyTo;
        

    //     public AskLocation(ActorRef<StressRecommendationEngine.LocationReporterToRecommendation> replyTo) {
    //         this.replyTo = replyTo;
            
    //     }
    // }

    // private final ActorContext<LocationReporter.Command> context;
    // private final String reporterId;
    // private final String groupId;
     

    // private LocationReporter(ActorContext<LocationReporter.Command> context, String reporterId, String groupId) {
    //     super(context);
    //     this.reporterId = reporterId;
    //     this.groupId = groupId;
    //     this.context = context;

    //     context.getLog().info("Sleep Reporter with id {}-{} started", reporterId, groupId);
    // }


    // @Override
    // public Receive<LocationReporter.Command> createReceive() {
    //     return newReceiveBuilder()
    //         .onMessage(AskLocation.class, this::onAskLocation)
    //         .build();
    // }

    // private Behavior<LocationReporter.Command> onAskLocation(AskLocation msg) {
    //     msg.replyTo.tell(new StressRecommendationEngine.LocationReporterToRecommendation("class room")); //example location: class room
    //     return this;
    // }

    
    
}
