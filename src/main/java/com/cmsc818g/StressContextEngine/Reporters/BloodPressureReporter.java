package com.cmsc818g.StressContextEngine.Reporters;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;

import akka.NotUsed;
import akka.actor.ActorPath;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.pubsub.Topic;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.http.javadsl.model.DateTime;
import akka.japi.JavaPartialFunction;
import akka.stream.BoundedSourceQueue;
import akka.stream.CompletionStrategy;
import akka.stream.Graph;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.SinkShape;
import akka.stream.UniformFanOutShape;
import akka.stream.impl.ActorRefSource;
import akka.stream.javadsl.Broadcast;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.SourceQueue;
import akka.stream.javadsl.SourceQueueWithComplete;
import akka.stream.typed.javadsl.ActorSource;

/**
 * Main job of this actor is to get blood pressure readings.
 * 
 * Note that while it can take `Reporter.Command`s this is just for 
 * ease since all reporters will be reading from a database. By engines/others,
 * it should be reached out to via `BloodPressureReporter.Command`s.
 */
public class BloodPressureReporter extends AbstractBehavior<Reporter.Command> {
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

    public static final class CloseSource implements Response {
        public CloseSource() {}
    }

    public static final class SourceError implements Response {
        private final Exception ex;
        public SourceError(Exception ex) {
            this.ex = ex;
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
    public static Behavior<Reporter.Command> create(String databaseURI, String tableName) {
        return Behaviors.<Reporter.Command>supervise(
            Behaviors.setup(
                context -> new BloodPressureReporter(context, databaseURI, tableName)
            )
        ).onFailure(SQLException.class, SupervisorStrategy.resume());
    }

    private final String databaseURI;
    private final String tableName;
    private Optional<BloodPressure> lastReading;
    private final ActorRef<Topic.Command<BloodPressureReading>> bpTopic;
    private final ActorRef<Response> bpSourceActor;

    /**
     * Creates a BloodPressureReporter.
     * 
     * Note that `lastReading` starts as empty, since it hasn't read yet.
     * @param context Context for this actor (in the system)
     * @param databaseURI URI to the database to read from for blood pressure
     * @param tableName Table in the database to use
     */
    private BloodPressureReporter(ActorContext<Reporter.Command> context, String databaseURI, String tableName) {
        super(context);
        this.databaseURI = databaseURI;
        this.tableName = tableName;
        this.lastReading = Optional.empty();
        this.bpTopic = context.spawn(Topic.create(BloodPressureReading.class, "bp-topic"), "bp-topic");

        //BoundedSourceQueue<BloodPressure> bpQueue = Source.<BloodPressure>queue(5).throttle(3, Duration.ofSeconds(3)).run(context.getSystem());
        //Source<BloodPressureReading, ActorRef<BloodPressureReading>> source = ActorRefSource.actorRef
        
        Source<Response, ActorRef<Response>> bpSource = ActorSource.actorRef(
            (m) -> m.getClass().equals(CloseSource.class),
            (m) -> m.getClass().equals(SourceError.class) ? Optional.of(((SourceError) m).ex) : Optional.empty(),
            5,
            OverflowStrategy.dropHead() 
        );

        this.bpSourceActor =
            bpSource
                .collect(
                    new JavaPartialFunction<Response, Optional<BloodPressure>>() {
                        public Optional<BloodPressure> apply(Response r, boolean isCheck) {
                            if (r instanceof BloodPressureReading) {
                                return ((BloodPressureReading) r).value;
                            } else {
                                throw noMatch();
                            }
                        }
                    }
                )
                //.to(Sink.foreach(System.out::println))
                .to(Sink.ignore())
                .run(context.getSystem().classicSystem());
            
        

        ServiceKey<Response> sourceServiceKey = ServiceKey.create(Response.class, "BPSourceService");
        context.getSystem().receptionist().tell(Receptionist.register(sourceServiceKey, bpSourceActor));
        //Source<BloodPressureReading, SourceQueueWithComplete<BloodPressureReading>> queue = Source.<BloodPressureReading>queue(5, OverflowStrategy.dropHead());

        BoundedSourceQueue<BloodPressure> sourceQueue = Source.<BloodPressure>queue(10)
                    .throttle(5, Duration.ofSeconds(3))
                    .to(Sink.ignore())
                    .run(context.getSystem().classicSystem());
        sourceQueue.offer(new BloodPressure(Optional.of(DateTime.now()), "140", "90"));


        Source<BloodPressure, SourceQueueWithComplete<BloodPressure>> tmpSrc = Source.<BloodPressure>queue(10, OverflowStrategy.dropHead());
        Sink<BloodPressure, CompletionStage<BloodPressure>> sink = Sink.head();

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
            String query = "SELECT id, DateTime, Bloodpressure FROM " + this.tableName + " WHERE id = ?";

            // Form a statement and fill in the variables
            PreparedStatement statement;
            statement = conn.prepareStatement(query);
            statement.setInt(1, msg.rowNumber); // `id` is the only variable ( ? )

            ResultSet results = Reporter.queryDB(this.databaseURI, statement, logger, msg.replyTo, myPath);

            // Need to call `.next()` as the iterator starts before the data
            // If no data, then it will return null
            if (results.next()) {

                // The cell in the database can be empty/null
                Optional<String> reading = Optional.ofNullable(results.getString("Bloodpressure"));

                // Not expecting DateTime to not exist though, so can just get it
                String dateTimeStr = results.getString("DateTime");

                // Convert into DateTime, could fail, hence `Optional`
                Optional<DateTime> readingTime = DateTime.fromIsoDateTimeString(dateTimeStr);

                // Create the latest reading only if all data is there
                if (reading.isPresent() && readingTime.isPresent()) {
                    String[] high_low = reading.get().split("/");
                    this.lastReading = Optional.of(new BloodPressure(readingTime, high_low[0], high_low[1]));
                    this.bpTopic.tell(Topic.publish(new BloodPressureReading(this.lastReading)));
                    this.bpSourceActor.tell(new BloodPressureReading(this.lastReading));
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
    private Behavior<Reporter.Command> onReadBloodPressure(ReadBloodPressure msg) {
        msg.replyTo.tell(new BloodPressureReading(this.lastReading));
        return this;
    }

    private Behavior<Reporter.Command> onSubscribe(Subscribe msg) {
        getContext().getLog().info("Got new subscriber");
        this.bpTopic.tell(Topic.subscribe(msg.subscriber));
        return this;
    }

    private Behavior<Reporter.Command> onUnsubscribe(Unsubscribe msg) {
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
    public static class BloodPressure {
        public final Optional<DateTime> readingTime;
        public final int upper;
        public final int lower;

        public BloodPressure(Optional<DateTime> readingTime, String upper, String lower) {
            this.readingTime = readingTime;
            this.upper = Integer.parseInt(upper);
            this.lower = Integer.parseInt(lower);
        }

        @Override
        public String toString() {
            return upper + "/" + lower;
        }
    }
}


