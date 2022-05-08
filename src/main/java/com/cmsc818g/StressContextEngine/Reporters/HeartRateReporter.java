package com.cmsc818g.StressContextEngine.Reporters;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

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


// Following BloodPressureReporter as a template

/**
 * Main job of this actor is to get hear rate readings.
 * 
 * Note that while it can take `Reporter.Command`s this is just for 
 * ease since all reporters will be reading from a database. By engines/others,
 * it should be reached out to via `HeartRateReporter.Command`s.
 */
public class HeartRateReporter extends Reporter {
    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public interface Command extends Reporter.Command {}

    /** Main request for this actor, asking for the heart rate reading. */
    public static final class ReadHeartRate implements Command {
        final ActorRef<HeartRateReading> replyTo;

        public ReadHeartRate(ActorRef<HeartRateReading> replyTo) {
            this.replyTo = replyTo;
        }
    }
    public static final class Subscribe implements Command {
        final ActorRef<HeartRateReading> subscriber;

        public Subscribe(ActorRef<HeartRateReading> subscriber) {
            this.subscriber = subscriber;
        }
    }

    public static final class Unsubscribe implements Command {
        final ActorRef<HeartRateReading> subscriber;

        public Unsubscribe(ActorRef<HeartRateReading> subscriber) {
            this.subscriber = subscriber;
        }
    }

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/
    public interface Response extends Reporter.Response {}

    /** Main response for this actor, replying with the heart rate reading */
    public static final class HeartRateReading implements Response {

        // Note it's possible there was no reading (NULL), so it is `Optional`
        public final Optional<HeartRate> value;

        public HeartRateReading(Optional<HeartRate> value) {
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
    public static Behavior<Reporter.Command> create(
                                                ActorRef<SQLiteHandler.StatusOfRead> statusListener,
                                                String databaseURI,
                                                String tableName,
                                                int readRate)
    {
        return Behaviors.<Reporter.Command>supervise(
            Behaviors.setup(
                context -> 
                Behaviors.withTimers(
                    timers -> new HeartRateReporter(context, timers, statusListener, databaseURI, tableName, readRate)
                )
            )
        ).onFailure(SQLException.class, SupervisorStrategy.resume());
    }

    private static final String periodicTimerName = "hr-periodic";
    private Optional<HeartRate> lastReading;
    private final ActorRef<Topic.Command<HeartRateReading>> hrTopic;

    /**
     * Creates a HeartRateReporter.
     * 
     * Note that `lastReading` starts as empty, since it hasn't read yet.
     * @param context Context for this actor (in the system)
     * @param databaseURI URI to the database to read from for blood pressure
     * @param tableName Table in the database to use
     */
    private HeartRateReporter(ActorContext<Reporter.Command> context,
                            TimerScheduler<Reporter.Command> timers,
                            ActorRef<SQLiteHandler.StatusOfRead> statusListener,
                            String databaseURI,
                            String tableName,
                            int readRate)
    {
        super(context, timers, periodicTimerName, statusListener, databaseURI, tableName, readRate);

        this.lastReading = Optional.empty();
        this.hrTopic = context.spawn(Topic.create(HeartRateReading.class, "hr-topic"), "hr-topic");
    }

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<Reporter.Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(Reporter.ReadRowOfData.class, this::onReadRowOfData)
            .onMessage(ReadHeartRate.class, this::onReadHeartRate)
            .onMessage(Subscribe.class, this::onSubscribe)
            .onMessage(Unsubscribe.class, this::onUnsubscribe)
            .onMessage(StartReading.class, this::onStartReading)
            .onMessage(StopReading.class, this::onStopReading)
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
        ActorPath myPath = getContext().getSelf().path();

        // Query itself with variables to be filled
        List<String> columnHeaders = List.of(
            "id",
            "time",
            "heart-rate"
        );

        ResultSet results = queryDB(columnHeaders, myPath, msg.rowNumber);

        // Need to call `.next()` as the iterator starts before the data
        // If no data, then it will return null
        if (results.next()) {

            // The cell in the database can be empty/null
            Optional<Integer> reading = Optional.ofNullable(results.getInt("heart-beat"));

            // Not expecting DateTime to not exist though, so can just get it
            Optional<String> readingTime = Optional.ofNullable(results.getString("time"));

            // Convert into DateTime, could fail, hence `Optional`
            // Optional<DateTime> readingTime = DateTime.fromIsoDateTimeString(dateTimeStr);

            // Create the latest reading only if all data is there
            if (reading.isPresent()) {
                Integer hr_value = reading.get();
                
                // Is immutable, can both lastReading and the topic can have this
                HeartRate hr = new HeartRate(readingTime, hr_value);
                this.lastReading = Optional.of(hr);

                // Publish the heartrate out
                this.hrTopic.tell(
                    Topic.publish(
                        new HeartRateReading(
                            Optional.of(hr)
                        )
                    )
                );
            }

            // Tell the Context Engine we've successfully read
            msg.replyTo.tell(new SQLiteHandler.StatusOfRead(true, "Succesfully read row " + msg.rowNumber, myPath));
            
        } else {

            // Tell the Context Engine we had a problem
            msg.replyTo.tell(new SQLiteHandler.StatusOfRead(false, "No results from row " + msg.rowNumber, myPath));
        }

        if (results != null)
            results.close();

        return this;
    }

    /**
     * When getting a `ReadHeartRate` message, respond back with last reading.
     * @param msg A request with who to reply back to with the reading
     * @return itself unchanged
     */
    private Behavior<Reporter.Command> onReadHeartRate(ReadHeartRate msg) {
        msg.replyTo.tell(new HeartRateReading(this.lastReading));
        return this;
    }

    private Behavior<Reporter.Command> onSubscribe(Subscribe msg) {
        getContext().getLog().info("New subscriber added");
        this.hrTopic.tell(Topic.subscribe(msg.subscriber));
        return this;
    }

    private Behavior<Reporter.Command> onUnsubscribe(Unsubscribe msg) {
        getContext().getLog().info("Actor has unsubscribed");
        this.hrTopic.tell(Topic.unsubscribe(msg.subscriber));
        return this;
    }

    /**
     * Could do any necessary cleanup here.
     * 
     * This is essentially when the actor is killed by parent/system shutting down.
     * @return itself
     */
    private HeartRateReporter onPostStop() {
        getContext().getSystem().log().info("HeartRate Reporter stopped");
        return this;
    }

    /************************************* 
     * HELPER CLASSES
     *************************************/
    public class HeartRate {
        final Optional<String> readingTime;
        final int heartrate;

        public HeartRate(Optional<String> readingTime, int hr_value) {
            this.readingTime = readingTime;
            this.heartrate = hr_value;
        }

        public int getheartrate(){
            return heartrate;
        }
    }
}


