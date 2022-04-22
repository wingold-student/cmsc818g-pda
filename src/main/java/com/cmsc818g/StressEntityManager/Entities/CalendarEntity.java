package com.cmsc818g.StressEntityManager.Entities;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.model.DateTime;
import akka.pattern.StatusReply;

public class CalendarEntity extends AbstractBehavior<CalendarCommand> {
    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public static final class ReadRowOfData implements CalendarCommand {
        final int rowNumber;
        final ActorRef<StatusReply<Done>> replyTo;

        public ReadRowOfData(int rowNumber,  ActorRef<StatusReply<Done>> replyTo) {
            this.rowNumber = rowNumber;
            this.replyTo = replyTo;
        }
    }

    public static final class GetCurrentEvent implements CalendarCommand {
        final ActorRef<CalendarResponse> replyTo;

        public GetCurrentEvent(ActorRef<CalendarResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }
    public static final class GetEventsInRange implements CalendarCommand {
        final DateTime start;
        final DateTime end;
        final ActorRef<CalendarResponse> replyTo;

        public GetEventsInRange(DateTime start, DateTime end, ActorRef<CalendarResponse> replyTo) {
            this.start = start;
            this.end = end;
            this.replyTo = replyTo;
        }
    }

    // TODO: Combine add, remove, move, update etc. ?
    public static final class AddEvent implements CalendarCommand {
        final Event event;
        final ActorRef<StatusReply<Boolean>> replyTo;

        public AddEvent(Event event, ActorRef<StatusReply<Boolean>> replyTo) {
            this.event = event;
            this.replyTo = replyTo;
        }
    }

    public static final class RemoveEvent implements CalendarCommand {
        final Event event;
        final ActorRef<StatusReply<Boolean>> replyTo;

        public RemoveEvent(Event event, ActorRef<StatusReply<Boolean>> replyTo) {
            this.event = event;
            this.replyTo = replyTo;
        }
    }

    public static final class MoveEvent implements CalendarCommand {
        final Event event;
        final DateTime newTime;
        final ActorRef<StatusReply<Boolean>> replyTo;
        
        public MoveEvent(Event event, DateTime newTime, ActorRef<StatusReply<Boolean>> replyTo) {
            this.event = event;
            this.newTime = newTime;
            this.replyTo = replyTo;
        }
    }

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/
    public static final class GetCurrentEventResponse implements CalendarResponse {
        final Optional<Event> event;

        public GetCurrentEventResponse(Optional<Event> event) {
            this.event = event;
        }
    }
    public static final class GetEventsInRangeResponse implements CalendarResponse {
        final Event[] events;

        public GetEventsInRangeResponse(Event[] events) {
            this.events = events;
        }
    }

    /************************************* 
     * CREATION 
     *************************************/

     public static Behavior<CalendarCommand> create(String databaseURI, String tableName) {
         return Behaviors.setup(context -> new CalendarEntity(context, databaseURI, tableName));
     }

    private String databaseURI; // TODO: Temporary?
    private String tableName;
    private Optional<Event> currentEvent;

    public CalendarEntity(ActorContext<CalendarCommand> context, String databaseURI, String tableName) {
        super(context);
        this.databaseURI = databaseURI;
        this.tableName = tableName;
        this.currentEvent = Optional.empty();
    }

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<CalendarCommand> createReceive() {
        return newReceiveBuilder()
            .onMessage(ReadRowOfData.class, this::onReadRowOfData)
            .onMessage(GetCurrentEvent.class, this::onGetCurrentEvent)
            .onMessage(GetEventsInRange.class, this::onGetEventsInRange)
            .onMessage(AddEvent.class, this::onAddEvent)
            .onMessage(RemoveEvent.class, this::onRemoveEvent)
            .onMessage(MoveEvent.class, this::onMoveEvent)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    private Connection connectToDB() {
        Connection conn = null;

        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(this.databaseURI);
        } catch (SQLException e) {
            // entityManager.tell(I've failed!)
            getContext().getLog().error("Failed to connect to the database {} with error {}", databaseURI, e);
            getContext().stop(getContext().getSelf());
        } catch (ClassNotFoundException e) {
            getContext().getLog().error("Failed to load JDBC driver", e);
            getContext().stop(getContext().getSelf());
        }

        return conn;
    }

    private Behavior<CalendarCommand> onReadRowOfData(ReadRowOfData msg) {
        Connection conn = this.connectToDB();
        ResultSet results = null;

        String query = "SELECT id, DateTime, Schedule FROM " + this.tableName + " WHERE id = ?";
        try {
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setInt(1, msg.rowNumber);

            results = statement.executeQuery();

            if (results.next()) {
                Optional<String> eventName = Optional.ofNullable(results.getString("Schedule"));
                String dateTimeStr = results.getString("DateTime");
                Optional<DateTime> eventTime = DateTime.fromIsoDateTimeString(dateTimeStr);

                if (eventName.isPresent() && eventTime.isPresent()) {
                    Duration tmpDuration = Duration.ofMinutes(30L); // TODO: TEMPORARY
                    Optional.of(Duration.ofDays(1));
                    Event event = new Event(eventName.get(), eventTime.get(), tmpDuration, "");
                    this.currentEvent = Optional.of(event);
                }

                results.close();
                msg.replyTo.tell(StatusReply.Ack());
            } else {
                msg.replyTo.tell(StatusReply.error("No results"));
            }

        } catch (SQLException e) {
            getContext().getLog().error("Failed to query the with error: ", e);
            msg.replyTo.tell(StatusReply.error("Failed query"));
        } 

        return this;
    }

    private Behavior<CalendarCommand> onGetCurrentEvent(GetCurrentEvent msg) {
        msg.replyTo.tell(new GetCurrentEventResponse(this.currentEvent));
        return this;
    }

    private Behavior<CalendarCommand> onGetEventsInRange(GetEventsInRange msg) {
        return this;
    }

    private Behavior<CalendarCommand> onAddEvent(AddEvent msg) {
        return this;
    }

    private Behavior<CalendarCommand> onRemoveEvent(RemoveEvent msg) {
        return this;
    }

    private Behavior<CalendarCommand> onMoveEvent(MoveEvent msg) {
        return this;
    }

    private CalendarEntity onPostStop() {
        return this;
    }

    /************************************* 
     * HELPER CLASSES
     *************************************/
    public class Event {
        String eventName;
        DateTime time;
        Duration length;
        String calendarType;

        public Event(String eventName, DateTime time, Duration length, String calendarType) {
            this.eventName = eventName;
            this.time = time;
            this.length = length;
            this.calendarType = calendarType;
        }
    }
}
