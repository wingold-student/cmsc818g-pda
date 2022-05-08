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

public class SleepReporter extends Reporter {

   /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public interface Command extends Reporter.Command {}

    /** Main request for this actor, asking for the blood pressure reading. */
    public static final class ReadSleepHours implements Command {
        final ActorRef<SleepHoursReading> replyTo;

        public ReadSleepHours(ActorRef<SleepHoursReading> replyTo) {
            this.replyTo = replyTo;
        }
    }
    public static final class Subscribe implements Command {
        final ActorRef<SleepHoursReading> subscriber;

        public Subscribe(ActorRef<SleepHoursReading> subscriber) {
            this.subscriber = subscriber;
        }
    }

    public static final class Unsubscribe implements Command {
        final ActorRef<SleepHoursReading> subscriber;

        public Unsubscribe(ActorRef<SleepHoursReading> subscriber) {
            this.subscriber = subscriber;
        }
    }

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/
    public interface Response extends Reporter.Response {}

    /** Main response for this actor, replying with the blood pressure reading */
    public static final class SleepHoursReading implements Response {

        // Note it's possible there was no reading (NULL), so it is `Optional`
        public final Optional<SleepHours> value;

        public SleepHoursReading(Optional<SleepHours> value) {
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
                                                    int readRate)
    {
        return Behaviors.<Reporter.Command>supervise(
            Behaviors.setup(
                context -> 
                Behaviors.withTimers(
                    timers -> new SleepReporter(context,
                                                timers,
                                                statusListener,
                                                databaseURI,
                                                tableName,
                                                readRate)
                )
            )
        ).onFailure(SQLException.class, SupervisorStrategy.resume());
    }

    private static final String periodicTimerName = "sleep-periodic";
    private Optional<SleepHours> lastReading;
    private final ActorRef<Topic.Command<SleepHoursReading>> sleepTopic;

    /**
     * Creates a BloodPressureReporter.
     * 
     * Note that `lastReading` starts as empty, since it hasn't read yet.
     * @param context Context for this actor (in the system)
     * @param databaseURI URI to the database to read from for blood pressure
     * @param tableName Table in the database to use
     */
    private SleepReporter(ActorContext<Reporter.Command> context,
                        TimerScheduler<Reporter.Command> timers,
                        ActorRef<SQLiteHandler.StatusOfRead> statusListener,
                        String databaseURI,
                        String tableName,
                        int readRate)
    {
        super(context, timers, periodicTimerName, statusListener, databaseURI, tableName, readRate);
        this.lastReading = Optional.empty();
        this.sleepTopic = context.spawn(Topic.create(SleepHoursReading.class, "sleep-topic"), "sleep-topic");
    }

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<Reporter.Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(Reporter.ReadRowOfData.class, this::onReadRowOfData)
            .onMessage(ReadSleepHours.class, this::onReadSleepHours)
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
    protected Behavior<Reporter.Command> onReadRowOfData(Reporter.ReadRowOfData msg) throws ClassNotFoundException, SQLException {
        // Used for messages later
        ActorPath myPath = getContext().getSelf().path();

        List<String> columnHeaders = List.of(
            "id",
            "time",
            "sleep"
        );

        ResultSet results = queryDB(columnHeaders, myPath, msg.rowNumber);

        // Need to call `.next()` as the iterator starts before the data
        // If no data, then it will return null
        if (results.next()) {

            // The cell in the database can be empty/null
            Optional<Integer> reading = Optional.ofNullable(results.getInt("sleep"));

            // Not expecting DateTime to not exist though, so can just get it
            Optional<String> readingTime = Optional.ofNullable(results.getString("time"));

            // Create the latest reading only if all data is there
            if (reading.isPresent()) {

                // Can share because it is immutable
                SleepHours sleepValue = new SleepHours(readingTime, reading.get());

                this.lastReading = Optional.of(sleepValue);
                /*
                this.sleepTopic.tell(Topic.publish(
                    new SleepHoursReading(Optional.of(sleepValue))
                ));
                */
            }

            results.close();

            // Tell the Context Engine we've successfully read
            msg.replyTo.tell(new SQLiteHandler.StatusOfRead(true, "Succesfully read row " + msg.rowNumber, myPath));
            
        } else {

            // Tell the Context Engine we had a problem
            msg.replyTo.tell(new SQLiteHandler.StatusOfRead(false, "No results from row " + msg.rowNumber, myPath));
        }
            

        return this;
    }

    /**
     * When getting a `ReadBloodPressure` message, respond back with last reading.
     * @param msg A request with who to reply back to with the reading
     * @return itself unchanged
     */
    private Behavior<Reporter.Command> onReadSleepHours(ReadSleepHours msg) {
        msg.replyTo.tell(new SleepHoursReading(this.lastReading));
        return this;
    }

    private Behavior<Reporter.Command> onSubscribe(Subscribe msg) {
        getContext().getLog().info("New subscriber added");
        this.sleepTopic.tell(Topic.subscribe(msg.subscriber));
        return this;
    }

    private Behavior<Reporter.Command> onUnsubscribe(Unsubscribe msg) {
        getContext().getLog().info("Actor has unsubscribed");
        this.sleepTopic.tell(Topic.unsubscribe(msg.subscriber));
        return this;
    }


    /**
     * Could do any necessary cleanup here.
     * 
     * This is essentially when the actor is killed by parent/system shutting down.
     * @return itself
     */
    private SleepReporter onPostStop() {
        getContext().getSystem().log().info("BloodPressure Reporter stopped");
        return this;
    }

    /************************************* 
     * HELPER CLASSES
     *************************************/
    public class SleepHours {
        final Optional<String> readingTime;
        final int sleep;

        public SleepHours(Optional<String> readingTime, int sleep) {
            this.readingTime = readingTime;
            this.sleep = sleep;
        }
    }






    // public static final class AskSleepHoursByDetection implements Command {
    //     private ActorRef<StressDetectionEngine.SleepReporterToDetection> replyTo;
        
    //     public AskSleepHoursByDetection(ActorRef<StressDetectionEngine.SleepReporterToDetection> replyTo) {
    //         this.replyTo = replyTo;
            
    //     }
    // }

    // private final ActorContext<SleepReporter.Command> context;
    // private final String reporterId;
    // private final String groupId;
     

    // private SleepReporter(ActorContext<SleepReporter.Command> context, String reporterId, String groupId) {
    //     super(context);
    //     this.reporterId = reporterId;
    //     this.groupId = groupId;
    //     this.context = context;

    //     context.getLog().info("Sleep Reporter started", reporterId, groupId);
    // }

    // @Override
    // public Receive<SleepReporter.Command> createReceive() {
    //     return newReceiveBuilder()
    //         .onMessage(AskSleepHoursByRecommendation.class, this::onAskSleepHoursByRecommend)
    //         .onMessage(AskSleepHoursByDetection.class, this::onAskSleepHoursByDetection)
    //         .onSignal(PostStop.class, signal -> onPostStop())
    //         .build();
    // }

    // private Behavior<SleepReporter.Command> onAskSleepHoursByRecommend(AskSleepHoursByRecommendation msg) {
    //     msg.replyTo.tell(new StressRecommendationEngine.SleepReporterToRecommendation(12)); //example sleep hours:12
    //     return this;
    // }

    // private Behavior<SleepReporter.Command> onAskSleepHoursByDetection(AskSleepHoursByDetection msg) {
    //     // same class as created for recommender above
    //     // Just referencing different message class and replying to detector
    //     msg.replyTo.tell(new StressDetectionEngine.SleepReporterToDetection(12)); //example sleep hours:12
    //     return this;
    // }

    // private SleepReporter onPostStop() {
    //     getContext().getLog().info("Sleep reporter stopped");
    //     return this;
    // }

}
