package com.cmsc818g.StressContextEngine.Reporters;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.cmsc818g.StressEntityManager.Entities.CalendarCommand;
import com.cmsc818g.Utilities.SQLiteHandler;

import akka.actor.ActorPath;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.TimerScheduler;
import akka.actor.typed.pubsub.Topic;
import akka.http.javadsl.model.DateTime;

// AbstractBehavior<What type of messages it will receive>
public class SchedulerReporter extends Reporter {

    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/

    /**
     * Since we will want more than a single type of message, we'll create an
     * empty interface that all necessary command messages can implement
     */
    public interface Command extends Reporter.Command {}

    // Likely the only one we'll use for the first demo
    public static final class GetCurrentEvent implements Command {
        final ActorRef<CurrentEventResponse> replyTo;

        public GetCurrentEvent(ActorRef<CurrentEventResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    /**
     * This is just one type of command message we may receive. Another actor can
     * ask us if the user "is free at" a certain date and time.
     * 
     * We track a request id so we can keep a conversation if necessary or see if
     * we've received this message before.
     * 
     * dateTimeStr is a string containing a formatted date time.
     * 
     * replyTo is a reference to the actor who sent us this command/message.
     * 
     * NOTE: All variables are final as is the message itself. Messages should be *immutable*
     */
    public static final class AskIsFreeAt implements Command {
        final long requestId;
        final DateTime time;
        final ActorRef<RespondIsFreeAt> replyTo;

        public AskIsFreeAt(long requestId, DateTime time, ActorRef<RespondIsFreeAt> replyTo) {
            this.requestId = requestId;
            this.time = time;
            this.replyTo = replyTo;
        }
    }

    /**
     * A request to add an event to the user's schedule.
     */
    public static final class AddToSchedule implements Command {
        final CalendarEvent event;
        final ActorRef<ScheduleAddedTo> replyTo;

        public AddToSchedule(CalendarEvent event, ActorRef<ScheduleAddedTo> replyTo) {
            this.event = event;           
            this.replyTo = replyTo;
        }
    }

    /**
     * Reply back to AddToSchedule. Either it did, true, or it didn't, false.
     */
    public static final class ScheduleAddedTo {
        final boolean value;

        public ScheduleAddedTo(boolean value) {
            this.value = value;
        }
    }

    public static final class AddCalendar implements Command {
        final ActorRef<CalendarCommand> calendarEntity;
        final String calendarName;

        public AddCalendar(ActorRef<CalendarCommand> calendarEntity, String calendarName) {
            this.calendarEntity = calendarEntity;
            this.calendarName = calendarName;
        }
    }

    public static final class GetEventsInRange implements Command {
        final ActorRef<ResponseEventsInRange> replyTo;
        final Optional<String> calendarName;
        final boolean acrossCalendars;
        final DateTime start;
        final DateTime end;

        public GetEventsInRange(ActorRef<ResponseEventsInRange> replyTo,
                                Optional<String> calendarName,
                                DateTime start,
                                DateTime end,
                                boolean acrossCalendars) {
            this.replyTo = replyTo;
            this.calendarName = calendarName;
            this.acrossCalendars = acrossCalendars;
            this.start = start;
            this.end = end;
        }
    }

    public static final class SubscribeForCurrentEvent implements Command {
        final ActorRef<CurrentEventResponse> subscriber;

        public SubscribeForCurrentEvent(ActorRef<CurrentEventResponse> subscriber) {
            this.subscriber = subscriber;
        }
    }

    public static final class UnsubscribeFromCurrentEvent implements Command {
        final ActorRef<CurrentEventResponse> subscriber;

        public UnsubscribeFromCurrentEvent(ActorRef<CurrentEventResponse> subscriber) {
            this.subscriber = subscriber;
        }
    }
    public static final class SubscribeForUpdates implements Command {
        final ActorRef<UpdateEventResponse> subscriber;

        public SubscribeForUpdates(ActorRef<UpdateEventResponse> subscriber) {
            this.subscriber = subscriber;
        }
    }

    public static final class UnsubscribeFromUpdates implements Command {
        final ActorRef<UpdateEventResponse> subscriber;

        public UnsubscribeFromUpdates(ActorRef<UpdateEventResponse> subscriber) {
            this.subscriber = subscriber;
        }
    }

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/
    public interface Response {}

    // Likely the only response we'll use in the first demo
    public static final class CurrentEventResponse implements Response {
        final public Optional<CalendarEvent> event;

        public CurrentEventResponse(Optional<CalendarEvent> event) {
            this.event = event;
        }
    }

    public static final class UpdateEventResponse implements Response {
        final public UpdateEvent event;

        public UpdateEventResponse(UpdateEvent event) {
            this.event = event;
        }
    }

    /**
     * This is what we will send back to the actor who asked us if the user
     * was free at a certain date and time.
     * 
     * We used the requestId so the actor knows what this is an answer to if multiple
     * queries were sent.
     * 
     * We return an Optional<Boolean> for true, false, empty. Where empty is if we had an
     * issue parsing it. (Though this is just an example of how to handle it)
     * 
     * NOTE: All variables are final. Messages should be *immutable*
     */
    public static final class RespondIsFreeAt implements Response {
        final long requestId;
        final Optional<Boolean> value;

        public RespondIsFreeAt(long requestId, Optional<Boolean> value) {
            this.requestId = requestId;
            this.value = value;
        }
    }

    public static final class ResponseEventsInRange implements Response {
        final HashMap<String, Object> events; // TODO: Update to actual EventObject when created

        public ResponseEventsInRange(HashMap<String, Object> events) {
            this.events = events;
        }
    }

    public static final class NotifyNewEvent implements Response {
        final DateTime time;
        final Duration length;
        final String calendarType;

        public NotifyNewEvent(DateTime time, Duration length, String calendarType) {
            this.time = time;
            this.length = length;
            this.calendarType = calendarType;
        }
    }


    /************************************* 
     * CREATION 
     *************************************/

    /**
     * Actors are essentially created via a Factory.
     * So upon a creation, they are provided any necessary setup data and their context.
     * 
     * Behaviors define well, how an actor behaves. But their behavior can change in the middle
     * of execution. This is why we return it not only here, but after processing messages too.
     * 
     * Here we're watching for if a SQLException occurs, which will log it for us and simply resume.
     * It resumes since it likely isn't a killer exception.
     */
    public static Behavior<Reporter.Command> create(ActorRef<SQLiteHandler.StatusOfRead> statusListener,
                                                    String databaseURI,
                                                    String tableName,
                                                    int readRate,
                                                    String calendarName)
    {
        // return Behaviors.setup(context -> new SchedulerReporter(context, calendarName, calendarDBURI, calendarTableName));
        return Behaviors.<Reporter.Command>supervise(
            Behaviors.setup(
                context -> 
                Behaviors.withTimers(
                    timers -> new SchedulerReporter(context,
                                                    timers,
                                                    statusListener,
                                                    databaseURI,
                                                    tableName,
                                                    readRate,
                                                    calendarName)
                )
            )
        ).onFailure(SQLException.class, SupervisorStrategy.resume());
    }

    // Just some instance variables
    private static final String periodicTimerName = "scheduler-periodic";
    private Optional<CalendarEvent> curEvent;
    private HashMap<String, CalendarData> calendars;
    private final String calendarDemoName;
    private final ActorRef<Topic.Command<CurrentEventResponse>> curEventTopic;

    /**
     * Constructor for this actor
     */
    private SchedulerReporter(ActorContext<Reporter.Command> context,
                              TimerScheduler<Reporter.Command> timers,
                              ActorRef<SQLiteHandler.StatusOfRead> statusListener,
                              String databaseURI,
                              String tableName,
                              int readRate,
                              String calendarName)
    {
        super(context, timers, periodicTimerName, statusListener, databaseURI, tableName, readRate);
        this.curEvent = Optional.empty();
        calendars = new HashMap<>();

        this.calendarDemoName = calendarName;
        CalendarData originalCalendar = new CalendarData(calendarName, databaseURI, tableName);
        calendars.put(calendarName, originalCalendar);

        this.curEventTopic = context.spawn(Topic.create(CurrentEventResponse.class, "curevent-topic"), "curevent-topic");

        context.getLog().info("Scheduler Reporter");
    }

    /**
     * An example method that could determine if the user is free at a given date and time.
     * 
     * Here it will try to parse the date time string. If it parses, and a current event
     * hasn't already been set, then they are free. False if current event is set.
     * Otherwise return an empty value, because an error occurred parsing.
     * 
     * @param dateTimeStr Some formatted date time string, e.g. 2022-03-30T00:00:00
     * @return True if parsed correctly and no current event set, false if
     * curEvent is set, empty otherwise.
     */
    private Optional<Boolean> IsFreeAt(DateTime eventDateTime) {
        try {
            getContext().getLog().info("Scheduler Reporter got date time str: {}", eventDateTime.toIsoDateTimeString());

            boolean isFree = this.curEvent.isEmpty();
            return Optional.of(isFree);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    /**
     * Pattern match on the command message you received. You can decide what
     * function to call depending on what message you received.
     * 
     * So here if we get the 'AskIsFreeAt' message/command, we will go to the
     * onAskIsFreeAt method.
     * 
     * If we receive a stop signal from our supervisor, then we call onPostStop.
     * 
     * You can add abitrary amount of onMessage() for matching. Though I'm sure there
     * are other methods I don't know of yet too.
     */
    @Override
    public Receive<Reporter.Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(Reporter.ReadRowOfData.class, this::onReadRowOfData)
            .onMessage(GetCurrentEvent.class, this::onGetCurrentEvent)
            .onMessage(AskIsFreeAt.class, this::onAskIsFreeAt)
            .onMessage(AddToSchedule.class, this::onAddToSchedule)
            .onMessage(AddCalendar.class, this::onAddCalendar)
            .onMessage(GetEventsInRange.class, this::onGetEventsInRange)
            .onMessage(SubscribeForCurrentEvent.class, this::onSubscribeForCurrentEvent)
            .onMessage(UnsubscribeFromCurrentEvent.class, this::onUnsubscribeFromCurrentEvent)
            .onMessage(StartReading.class, this::onStartReading)
            .onMessage(StopReading.class, this::onStopReading)
            .onMessage(TellSelfToRead.class, this::onTellSelfToRead)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }


    protected Behavior<Reporter.Command> onReadRowOfData(ReadRowOfData msg) throws ClassNotFoundException, SQLException {
        CalendarData calendar = calendars.get(this.calendarDemoName);
        ActorPath myPath = getContext().getSelf().path();

        List<String> columnHeaders = List.of(
            "id",
            "time",
            "date",
            "tag",
            "event"
        );

        ResultSet results = null;
        QueryResponse response = queryDB(columnHeaders, myPath, msg.rowNumber);

        if (response != null)
            results = response.results;

        if (results != null && results.next()) {
            Optional<String> eventName = Optional.ofNullable(results.getString("event"));
            String time = results.getString("time");
            String date = results.getString("date");
            String dateTimeStr = String.format("%sT%s", date, time);
            Optional<DateTime> eventTime = DateTime.fromIsoDateTimeString(dateTimeStr);

            String calendarType = results.getString("tag");

            if (eventName.isPresent() && eventTime.isPresent()) {
                Duration tmpDuration = Duration.ofMinutes(30L); // TODO: TEMPORARY
                Optional.of(Duration.ofDays(1));

                // Can share event because it is immutable
                CalendarEvent event = new CalendarEvent(eventName.get(), eventTime.get(), tmpDuration, calendarType); 

                this.curEvent = Optional.of(event);
                this.curEventTopic.tell(Topic.publish(
                    new CurrentEventResponse(Optional.of(event))
                ));

                msg.replyTo.tell(new SQLiteHandler.StatusOfRead(true, "Succesfully read row " + msg.rowNumber, myPath));
                this.currentRow++;
            } else {
                this.curEvent = Optional.empty();
            }
            
        } else {
            this.curEvent = Optional.empty();
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

    private Behavior<Reporter.Command> onGetCurrentEvent(GetCurrentEvent msg) {
        msg.replyTo.tell(new CurrentEventResponse(this.curEvent));
        return this;
    }

    /**
     * Set to be called when we receive a AskIsFreeAt message/command.
     * It will respond back to the actor who asked saying if the user is free or
     * not (or an error occurred).
     * 
     * @param msg The command which is asking if the user is free at a date and time
     * @return the behavior of this actor. It didn't change
     */
    private Behavior<Reporter.Command> onAskIsFreeAt(AskIsFreeAt msg) {
        msg.replyTo.tell(new RespondIsFreeAt(msg.requestId, this.IsFreeAt(msg.time)));
        return this;
    }

    /**
     * Add the event to the user, setting curEvent. But only if the date time string
     * was valid AND the curEvent wasn't previously set.
     * 
     * (Meaning this will fail upon a second AddToSchedule message)
     */
    private Behavior<Reporter.Command> onAddToSchedule(AddToSchedule msg) {
        Optional<Boolean> isFree = this.IsFreeAt(msg.event.datetime);
        boolean addedEvent = !isFree.isEmpty() && isFree.get();

        if (addedEvent) {
            this.curEvent = Optional.of(msg.event);
        }

        msg.replyTo.tell(new ScheduleAddedTo(addedEvent));
        return this;
    }

    private Behavior<Reporter.Command> onAddCalendar(AddCalendar msg) {
        // calendarEntities.put(msg.calendarName, msg.calendarEntity);
        return this;
    }

    private Behavior<Reporter.Command> onGetEventsInRange(GetEventsInRange msg) {
        msg.replyTo.tell(new ResponseEventsInRange(new HashMap<String, Object>()));
        return this;
    }
    private Behavior<Reporter.Command> onSubscribeForCurrentEvent(SubscribeForCurrentEvent msg) {
        getContext().getLog().info("New current event subscriber added");
        this.curEventTopic.tell(Topic.subscribe(msg.subscriber));
        return this;
    }

    private Behavior<Reporter.Command> onUnsubscribeFromCurrentEvent(UnsubscribeFromCurrentEvent msg) {
        getContext().getLog().info("Actor has unsubscribed from current event");
        this.curEventTopic.tell(Topic.unsubscribe(msg.subscriber));
        return this;
    }
    
    /**
     * What to do when shut down by a supervisor
     */
    private SchedulerReporter onPostStop() {
        getContext().getLog().info("Scheduler reporter stopped");
        return this;
    }

    /************************************* 
     * HELPER CLASSES
     *************************************/
    private class CalendarData {
        final String name;
        final String databaseURI;
        final String tableName;

        public CalendarData(String name, String databaseURI, String tableName) {
            this.name = name;
            this.databaseURI = databaseURI;
            this.tableName = tableName;
        }
    }
    public class CalendarEvent {
        final String eventName;
        final DateTime datetime;
        final Duration length;
        final String calendarType;

        public CalendarEvent(String eventName, DateTime datetime, Duration length, String calendarType) {
            this.eventName = eventName;
            this.datetime = datetime;
            this.length = length;
            this.calendarType = calendarType;
        }
    }

    public class UpdateEvent {
        
    }
}
