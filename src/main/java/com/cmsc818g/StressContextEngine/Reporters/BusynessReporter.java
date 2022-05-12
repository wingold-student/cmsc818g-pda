package com.cmsc818g.StressContextEngine.Reporters;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import com.cmsc818g.Utilities.SQLiteHandler;

import akka.actor.typed.javadsl.TimerScheduler;
import akka.actor.typed.pubsub.Topic;
import akka.actor.ActorPath;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.model.DateTime;
import akka.pattern.StatusReply;

public class BusynessReporter extends Reporter {
    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public interface Command extends Reporter.Command {}

    public static final class StartListening implements Command {
        final ActorRef<StatusReply<String>> replyTo;

        public StartListening(ActorRef<StatusReply<String>> replyTo) {
            this.replyTo = replyTo;
       }
    }

    public static final class StopListening implements Command {
        public StopListening() {}
    }

    public static final class GracefulShutdown implements Command {
        public GracefulShutdown() {}
    }

    public static final class Subscribe implements Command {
        final ActorRef<BusynessLevelResponse> subscriber;

        public Subscribe(ActorRef<BusynessLevelResponse> subscriber) {
            this.subscriber = subscriber;
        }
    }

    public static final class Unsubscribe implements Command {
        final ActorRef<BusynessLevelResponse> subscriber;

        public Unsubscribe(ActorRef<BusynessLevelResponse> subscriber) {
            this.subscriber = subscriber;
        }
    }

    public static final class AdaptedSchedulerUpdateEvent implements Command {
        final SchedulerReporter.UpdateEventResponse event;

        public AdaptedSchedulerUpdateEvent(SchedulerReporter.UpdateEventResponse event) {
            this.event = event;
        }
    }

    public static final class GetBusynessLevel implements Command {
        final DateTime start;
        final Optional<DateTime> end; // If empty, assume up until now
        final ActorRef<BusynessLevelResponse> replyTo;
        final Optional<String> calendarName;

        public GetBusynessLevel(DateTime start, Optional<DateTime> end, Optional<String> calendarName, ActorRef<BusynessLevelResponse> replyTo) {
            this.start = start;
            this.end = end;
            this.calendarName = calendarName;
            this.replyTo = replyTo;
        }
    }
    public static final class GetCurrentBusynessLevel implements Command {
        final ActorRef<BusynessLevelResponse> replyTo;

        public GetCurrentBusynessLevel(ActorRef<BusynessLevelResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/
    
    public static final class BusynessLevelResponse {
        public final Optional<BusynessReading> busynessLevel;

        public BusynessLevelResponse(Optional<BusynessReading> busynessLevel) {
            this.busynessLevel = busynessLevel;
        }
    }

    /************************************* 
     * MESSAGES IT WRAPS
     *************************************/

    public static final class WrappedSchedulerResponse implements Command {
        final SchedulerReporter.Response response;

        public WrappedSchedulerResponse(SchedulerReporter.Response response) {
            this.response = response;
        }
    }

    public static final class WrappedUpdateEventResponse implements Command {
        final SchedulerReporter.UpdateEventResponse response;

        public WrappedUpdateEventResponse(SchedulerReporter.UpdateEventResponse response) {
            this.response = response;
        }
    }

    /************************************* 
     * CREATION 
     *************************************/

    public static Behavior<Reporter.Command> create(
                                                    ActorRef<SQLiteHandler.StatusOfRead> statusListener,
                                                    String databaseURI,
                                                    String tableName,
                                                    int readRate,
                                                    ActorRef<Reporter.Command> schedulerReporter
    ) {
        return Behaviors.<Reporter.Command>supervise(
            Behaviors.setup(
                context -> 
                Behaviors.withTimers(
                    timers -> new BusynessReporter(context, timers, statusListener, databaseURI, tableName, readRate, schedulerReporter)
                )
            )
        ).onFailure(SQLException.class, SupervisorStrategy.resume());
    }

    private static final String periodicTimerName = "busy-periodic";
    private ActorRef<Reporter.Command> schedulerReporter;
    private ActorRef<SchedulerReporter.Response> schedulerAdapter;
    private ActorRef<SchedulerReporter.UpdateEventResponse> updateEventAdapter;
    private Optional<BusynessReading> lastReading;
    private final ActorRef<Topic.Command<BusynessLevelResponse>> busyTopic;
    private int subscriberCount;

    public BusynessReporter(ActorContext<Reporter.Command> context,
                            TimerScheduler<Reporter.Command> timers,
                            ActorRef<SQLiteHandler.StatusOfRead> statusListener,
                            String databaseURI,
                            String tableName,
                            int readRate,
                            ActorRef<Reporter.Command> schedulerReporter
    ) {

        super(context, timers, periodicTimerName, statusListener, databaseURI, tableName, readRate);
        this.schedulerReporter = schedulerReporter;
        this.subscriberCount = 0;

        this.schedulerAdapter = context.messageAdapter(SchedulerReporter.Response.class, WrappedSchedulerResponse::new);
        this.updateEventAdapter = context.messageAdapter(SchedulerReporter.UpdateEventResponse.class, WrappedUpdateEventResponse::new);

        this.schedulerReporter.tell(new SchedulerReporter.SubscribeForUpdates(this.updateEventAdapter));

        this.busyTopic = context.spawn(Topic.create(BusynessLevelResponse.class, "busy-topic"), "busy-topic");
    }

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<Reporter.Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(Reporter.ReadRowOfData.class, this::onReadRowOfData)
            .onMessage(GetBusynessLevel.class, this::onGetBusynessLevel)
            .onMessage(GetCurrentBusynessLevel.class, this::onGetCurrentBusynessLevel)
            .onMessage(WrappedSchedulerResponse.class, this::onWrappedSchedulerResponse)
            .onMessage(WrappedUpdateEventResponse.class, this::onWrappedUpdateEventResponse)
            .onMessage(GracefulShutdown.class, this::onGracefulShutdown)
            .onMessage(StartReading.class, this::onStartReading)
            .onMessage(StopReading.class, this::onStopReading)
            .onMessage(Subscribe.class, this::onSubscribe)
            .onMessage(Unsubscribe.class, this::onUnsubscribe)
            .onMessage(TellSelfToRead.class, this::onTellSelfToRead)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    protected Behavior<Reporter.Command> onReadRowOfData(Reporter.ReadRowOfData msg) throws ClassNotFoundException, SQLException {
        ActorPath myPath = getContext().getSelf().path();

        List<String> columnHeaders = List.of(
            "id",
            "busyness"
        );

        ResultSet results = null;
        QueryResponse response = queryDB(columnHeaders, myPath, msg.rowNumber);

        if (response.results != null)
             results = response.results;

        if (results != null && results.next()) {
            Optional<Integer> busyLevel = Optional.ofNullable(results.getInt("busyness"));

            if (busyLevel.isPresent()) {
                BusynessReading reading = new BusynessReading(busyLevel);
                this.lastReading = Optional.of(reading);
                msg.replyTo.tell(new SQLiteHandler.StatusOfRead(true, "Succesfully read row " + msg.rowNumber, myPath));

                if (subscriberCount > 0) {
                    this.busyTopic.tell(Topic.publish(
                        new BusynessLevelResponse(Optional.of(reading))
                    ));
                }

            } else {
                // this.lastReading = Optional.empty();
            }

        } else {
            // this.lastReading = Optional.empty();
            this.currentRow = 1;
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

    private Behavior<Reporter.Command> onGetBusynessLevel(GetBusynessLevel msg) {
        return this;
    }

    private Behavior<Reporter.Command> onGetCurrentBusynessLevel(GetCurrentBusynessLevel msg) {
        msg.replyTo.tell(new BusynessLevelResponse(this.lastReading));
        return this;
    }

    private Behavior<Reporter.Command> onWrappedSchedulerResponse(WrappedSchedulerResponse wrapped) {
        SchedulerReporter.Response response = wrapped.response;

        if (response instanceof SchedulerReporter.NotifyNewEvent) {
            SchedulerReporter.NotifyNewEvent rsp = (SchedulerReporter.NotifyNewEvent) response;
        } else if (response instanceof SchedulerReporter.ResponseEventsInRange) {
            SchedulerReporter.ResponseEventsInRange rsp = (SchedulerReporter.ResponseEventsInRange) response;
        }
        return this;
    }

    // TODO: Needs to actually do something with the event
    private Behavior<Reporter.Command> onWrappedUpdateEventResponse(WrappedUpdateEventResponse wrapped) {
        SchedulerReporter.UpdateEventResponse response = wrapped.response;
        SchedulerReporter.UpdateEvent event = response.event;

        getContext().getLog().info("Received schedule update");
        return this;
    }

    private BusynessReporter onGracefulShutdown(GracefulShutdown msg) {
        return this;
    }

    private Behavior<Reporter.Command> onSubscribe(Subscribe msg) {
        getContext().getLog().info("New subscriber added");
        this.busyTopic.tell(Topic.subscribe(msg.subscriber));
        this.subscriberCount++;
        return this;
    }

    private Behavior<Reporter.Command> onUnsubscribe(Unsubscribe msg) {
        getContext().getLog().info("Actor has unsubscribed");
        this.busyTopic.tell(Topic.unsubscribe(msg.subscriber));
        this.subscriberCount--;
        return this;
    }

    private BusynessReporter onPostStop() {

        return this;
    }

    private void UnsubscribeFromScheduler() {

    }

    public static class BusynessReading {
        public final Optional<Integer> level;

        public BusynessReading(Optional<Integer> busyLevel) {
            this.level = busyLevel;
        }
    }

}
