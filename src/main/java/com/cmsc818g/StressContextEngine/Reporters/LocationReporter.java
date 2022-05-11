package com.cmsc818g.StressContextEngine.Reporters;

import java.util.List;
import java.util.Optional;

import java.sql.ResultSet;
import java.sql.SQLException;


import com.cmsc818g.Utilities.SQLiteHandler;

import akka.actor.ActorPath;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.TimerScheduler;
import akka.actor.typed.pubsub.Topic;

public class LocationReporter extends Reporter {

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
    public static final class Subscribe implements Command {
        final ActorRef<LocationReading> subscriber;

        public Subscribe(ActorRef<LocationReading> subscriber) {
            this.subscriber = subscriber;
        }
    }

    public static final class Unsubscribe implements Command {
        final ActorRef<LocationReading> subscriber;

        public Unsubscribe(ActorRef<LocationReading> subscriber) {
            this.subscriber = subscriber;
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
    public static Behavior<Reporter.Command> create(ActorRef<SQLiteHandler.StatusOfRead> statusListener,
                                                    String databaseURI,
                                                    String tableName,
                                                    int readRate
    ) {
        return Behaviors.<Reporter.Command>supervise(
            Behaviors.setup(
                context -> 
                Behaviors.withTimers(
                    timers -> new LocationReporter(context,
                                                   timers,
                                                   statusListener,
                                                   databaseURI,
                                                   tableName,
                                                   readRate)
                )
            )       
        ).onFailure(SQLException.class, SupervisorStrategy.resume());
    }

    private static final String periodicTimerName = "loc-periodic";
    private Optional<UserLocation> lastReading;
    private final ActorRef<Topic.Command<LocationReading>> locTopic;
    private int subscriberCount = 0;

    /**
     * Creates a BloodPressureReporter.
     * 
     * Note that `lastReading` starts as empty, since it hasn't read yet.
     * @param context Context for this actor (in the system)
     * @param databaseURI URI to the database to read from for blood pressure
     * @param tableName Table in the database to use
     */
    private LocationReporter(ActorContext<Reporter.Command> context,
                            TimerScheduler<Reporter.Command> timers,
                            ActorRef<SQLiteHandler.StatusOfRead> statusListener,
                            String databaseURI,
                            String tableName,
                            int readRate
    ) {
        super(context, timers, periodicTimerName, statusListener, databaseURI, tableName, readRate);

        this.lastReading = Optional.empty();
        this.locTopic = context.spawn(Topic.create(LocationReading.class, "loc-topic"), "loc-topic");
    }

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<Reporter.Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(Reporter.ReadRowOfData.class, this::onReadRowOfData)
            .onMessage(ReadLocation.class, this::onReadLocation)
            .onMessage(Subscribe.class, this::onSubscribe)
            .onMessage(Unsubscribe.class, this::onUnsubscribe)
            .onMessage(StartReading.class, this::onStartReading)
            .onMessage(StopReading.class, this::onStopReading)
            .onMessage(TellSelfToRead.class, this::onTellSelfToRead)
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
    protected Behavior<Reporter.Command> onReadRowOfData(Reporter.ReadRowOfData msg) throws ClassNotFoundException, SQLException {
        // Used for messages later
        ActorPath myPath = getContext().getSelf().path();

        List<String> columnHeaders = List.of(
            "id",
            "time",
            "location"
        );

        ResultSet results = null;
        QueryResponse response = queryDB(columnHeaders, myPath, msg.rowNumber);
        if (response != null)
            results = response.results;

        // Need to call `.next()` as the iterator starts before the data
        // If no data, then it will return null
        if (results != null && results.next()) {

            // The cell in the database can be empty/null
            Optional<String> reading = Optional.ofNullable(results.getString("location"));

            // Not expecting DateTime to not exist though, so can just get it
            Optional<String> readingTime = Optional.ofNullable(results.getString("time"));

            // Create the latest reading only if all data is there
            if (reading.isPresent()) {
                String locationString = reading.get();

                // UserLocation is immutable, so both can share this object
                UserLocation locValue = new UserLocation(readingTime, locationString);

                this.lastReading = Optional.of(locValue);
                if (subscriberCount > 0) {
                    this.locTopic.tell(Topic.publish(
                        new LocationReading(Optional.of(locValue))
                    ));
                }

                // Tell the Context Engine we've successfully read
                msg.replyTo.tell(new SQLiteHandler.StatusOfRead(true, "Succesfully read row " + msg.rowNumber, myPath));
            } else {
                this.lastReading = Optional.empty();
            }
            
        } else {
            this.lastReading = Optional.empty();
            // Tell the Context Engine we had a problem
            msg.replyTo.tell(new SQLiteHandler.StatusOfRead(false, "No results from row " + msg.rowNumber, myPath));
        }

        if (response != null) {
            if (response.conn != null)
                response.conn.close();
            
            if (response.statement != null)
                response.statement.close();

            if (results != null)
                results.close();
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

    private Behavior<Reporter.Command> onSubscribe(Subscribe msg) {
        getContext().getLog().info("New subscriber added");
        this.locTopic.tell(Topic.subscribe(msg.subscriber));
        subscriberCount++;
        return this;
    }

    private Behavior<Reporter.Command> onUnsubscribe(Unsubscribe msg) {
        getContext().getLog().info("Actor has unsubscribed");
        this.locTopic.tell(Topic.unsubscribe(msg.subscriber));
        subscriberCount--;
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
        final public Optional<String> readingTime;
        final public String location;

        public UserLocation(Optional<String> readingTime, String location) {
            this.readingTime = readingTime;
            this.location = location;
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
