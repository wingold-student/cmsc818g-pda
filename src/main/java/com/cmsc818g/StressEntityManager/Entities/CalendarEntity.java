package com.cmsc818g.StressEntityManager.Entities;

import java.time.Duration;

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
    public static final class GetEventsInRangeResponse implements CalendarResponse {
        final Event[] events;
        final ActorRef<CalendarResponse> replyTo;

        public GetEventsInRangeResponse(Event[] events, ActorRef<CalendarResponse> replyTo) {
            this.events = events;
            this.replyTo = replyTo;
        }
    }

    /************************************* 
     * CREATION 
     *************************************/

     public static Behavior<CalendarCommand> create(String databaseURI) {
         return Behaviors.setup(context -> new CalendarEntity(context, databaseURI));
     }

    private String databaseURI; // TODO: Temporary?

    public CalendarEntity(ActorContext<CalendarCommand> context, String databaseURI) {
        super(context);
        this.databaseURI = databaseURI;
    }

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<CalendarCommand> createReceive() {
        return newReceiveBuilder()
            .onMessage(GetEventsInRange.class, this::onGetEventsInRange)
            .onMessage(AddEvent.class, this::onAddEvent)
            .onMessage(RemoveEvent.class, this::onRemoveEvent)
            .onMessage(MoveEvent.class, this::onMoveEvent)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
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
