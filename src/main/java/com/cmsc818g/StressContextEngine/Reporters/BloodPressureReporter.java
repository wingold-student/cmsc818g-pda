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

/**
 * Main job of this actor is to get blood pressure readings.
 * 
 * Note that while it can take `Reporter.Command`s this is just for 
 * ease since all reporters will be reading from a database. By engines/others,
 * it should be reached out to via `BloodPressureReporter.Command`s.
 */
public class BloodPressureReporter extends Reporter {
    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public interface Command extends Reporter.Command {}

    /** Main request for this actor, asking for the blood pressure reading. */
    public static final class ReadBloodPressure implements Command {
        final ActorRef<BloodPressureReading> replyTo;

        public ReadBloodPressure(ActorRef<BloodPressureReading> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class Subscribe implements Command {
        final ActorRef<BloodPressureReading> subscriber;

        public Subscribe(ActorRef<BloodPressureReading> subscriber) {
            this.subscriber = subscriber;
        }
    }

    public static final class Unsubscribe implements Command {
        final ActorRef<BloodPressureReading> subscriber;

        public Unsubscribe(ActorRef<BloodPressureReading> subscriber) {
            this.subscriber = subscriber;
        }
    }

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/
    public interface Response extends Reporter.Response {}

    /** Main response for this actor, replying with the blood pressure reading */
    public static final class BloodPressureReading implements Response {

        // Note it's possible there was no reading (NULL), so it is `Optional`
        public final Optional<BloodPressure> value;

        public BloodPressureReading(Optional<BloodPressure> value) {
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
    public static Behavior<Reporter.Command> create(ActorRef<SQLiteHandler.StatusOfRead> statusListener, String databaseURI, String tableName, int readRate) {
        return Behaviors.<Reporter.Command>supervise(
            Behaviors.setup(
                context -> 
                Behaviors.withTimers(
                    timers -> new BloodPressureReporter(context, timers, statusListener, databaseURI, tableName, readRate)
                )
            )
        ).onFailure(SQLException.class, SupervisorStrategy.resume());
    }

    private final static String periodicTimerName = "bp-periodic";

    private Optional<BloodPressure> lastReading;
    private final ActorRef<Topic.Command<BloodPressureReading>> bpTopic;

    /**
     * Creates a BloodPressureReporter.
     * 
     * Note that `lastReading` starts as empty, since it hasn't read yet.
     * @param context Context for this actor (in the system)
     * @param databaseURI URI to the database to read from for blood pressure
     * @param tableName Table in the database to use
     */
    private BloodPressureReporter(ActorContext<Reporter.Command> context,
            TimerScheduler<Reporter.Command> timers,
            ActorRef<SQLiteHandler.StatusOfRead> statusListener,
            String databaseURI,
            String tableName,
            int readRate) {

        super(context, timers, periodicTimerName, statusListener, databaseURI, tableName, readRate);

        this.lastReading = Optional.empty();
        this.bpTopic = context.spawn(Topic.create(BloodPressureReading.class, "bp-topic"), "bp-topic");
    }

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<Reporter.Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(Reporter.ReadRowOfData.class, this::onReadRowOfData)
            .onMessage(ReadBloodPressure.class, this::onReadBloodPressure)
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
     * @throws SQLException
     * @throws ClassNotFoundException
     * @throws Exception
     */
    protected Behavior<Reporter.Command> onReadRowOfData(Reporter.ReadRowOfData msg) throws ClassNotFoundException, SQLException {
        ActorPath myPath = getContext().getSelf().path();

        // Query itself with variables to be filled
        List<String> columnHeaders = List.of(
            "id",
            "time",
            "bp-systolic",
            "bp-diastolic"
        );

        ResultSet results = queryDB(columnHeaders, myPath, msg.rowNumber);

        if (results != null && results.next()) {

            // The cell in the database can be empty/null
            Optional<Integer> systolic = Optional.ofNullable(results.getInt("bp-systolic"));
            Optional<Integer> diastolic = Optional.ofNullable(results.getInt("bp-diastolic"));

            // Get the time (no date atm), could be null
            Optional<String> readingTime = Optional.ofNullable(results.getString("time"));

            // Create the latest reading only if all data is there
            if (systolic.isPresent() && diastolic.isPresent()) {

                // BloodPressure is immutable, so both last reading and the topic can have it
                BloodPressure bp = new BloodPressure(readingTime, systolic.get(), diastolic.get());

                this.lastReading = Optional.of(bp);
                this.bpTopic.tell(Topic.publish(
                    new BloodPressureReading(Optional.of(bp))
                ));

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

        if (results != null)
            results.close();
            
        return this;
    }

    /**
     * When getting a `ReadBloodPressure` message, respond back with last reading.
     * @param msg A request with who to reply back to with the reading
     * @return itself unchanged
     */
    private Behavior<Reporter.Command> onReadBloodPressure(ReadBloodPressure msg) {
        msg.replyTo.tell(new BloodPressureReading(this.lastReading));
        return this;
    }

    private Behavior<Reporter.Command> onSubscribe(Subscribe msg) {
        getContext().getLog().info("New subscriber added");
        this.bpTopic.tell(Topic.subscribe(msg.subscriber));
        return this;
    }

    private Behavior<Reporter.Command> onUnsubscribe(Unsubscribe msg) {
        getContext().getLog().info("Actor has unsubscribed");
        this.bpTopic.tell(Topic.unsubscribe(msg.subscriber));
        return this;
    }



    /**
     * Could do any necessary cleanup here.
     * 
     * This is essentially when the actor is killed by parent/system shutting down.
     * @return itself
     */
    private BloodPressureReporter onPostStop() {
        getContext().getSystem().log().info("BloodPressure Reporter stopped");
        return this;
    }

    /************************************* 
     * HELPER CLASSES
     *************************************/
    public class BloodPressure {
        final Optional<String> readingTime;
        final int systolic;
        final int diastolic;

        public BloodPressure(Optional<String> readingTime, int systolic, int diastolic) {
            this.readingTime = readingTime;
            this.systolic = systolic;
            this.diastolic = diastolic;
        }
        public int getSystolicBP(){
            return systolic;
        }
        public int getDiastolicBP(){
            return diastolic;
        }

        @Override
        public String toString() {
            return systolic + "/" + diastolic;
        }
    }
}


