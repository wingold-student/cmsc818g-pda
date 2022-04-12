package com.cmsc818g.ContextActors;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

// AbstractBehavior<What type of messages it will receive>
public class SchedulerReporter extends AbstractBehavior<ReporterMessages.Command> {

    /**
     * Since we will want more than a single type of message, we'll create an
     * empty interface that all necessary command messages can implement
     */
    public interface Command extends ReporterMessages.Command {}

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
        final String dateTimeStr;
        final ActorRef<RespondIsFreeAt> replyTo;

        public AskIsFreeAt(long requestId, String dateTimeStr, ActorRef<RespondIsFreeAt> replyTo) {
            this.requestId = requestId;
            this.dateTimeStr = dateTimeStr;
            this.replyTo = replyTo;
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
    public static final class RespondIsFreeAt {
        final long requestId;
        final Optional<Boolean> value;

        public RespondIsFreeAt(long requestId, Optional<Boolean> value) {
            this.requestId = requestId;
            this.value = value;
        }
    }


    /**
     * A request to add an event to the user's schedule.
     */
    public static final class AddToSchedule implements Command {
        final long requestId;
        final String dateTimeStr;
        final String event;
        final ActorRef<ScheduleAddedTo> replyTo;

        public AddToSchedule(long requestId, String dateTimeStr, String event, ActorRef<ScheduleAddedTo> replyTo) {
            this.requestId = requestId;
            this.dateTimeStr = dateTimeStr;
            this.event = event;           
            this.replyTo = replyTo;
        }
    }

    /**
     * Reply back to AddToSchedule. Either it did, true, or it didn't, false.
     */
    public static final class ScheduleAddedTo {
        final long requestId;
        final boolean value;

        public ScheduleAddedTo(long requestId, boolean value) {
            this.requestId = requestId;
            this.value = value;
        }
    }

    /**
     * Actors are essentially created via a Factory.
     * So upon a creation, they are provided any necessary setup data and their context.
     * 
     * Behaviors define well, how an actor behaves. But their behavior can change in the middle
     * of execution. This is why we return it not only here, but after processing messages too.
     *  
     * @param reporterId This context reporter instance's identifier. Could be another other
     *  type of grouping/way of identifying an instance of this actor.
     */
    public static Behavior<ReporterMessages.Command> create(String reporterId, String groupId) {
        return Behaviors.setup(context -> new SchedulerReporter(context, reporterId, groupId));
    }

    // Just some instance variables
    private final ActorContext<ReporterMessages.Command> context;

    private final String reporterId;
    private final String groupId;

    private Optional<String> curEvent;

    /**
     * Constructor for this actor
     * @param context Not super clear what is within the context
     * @param contextRepId Some way of identifying this actor instance
     */
    private SchedulerReporter(ActorContext<ReporterMessages.Command> context, String reporterId, String groupId) {
        super(context);
        this.reporterId = reporterId;
        this.groupId = groupId;
        this.context = context;
        this.curEvent = Optional.empty();

        context.getLog().info("Scheduler Reporter with id {}-{} started", reporterId, groupId);
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
    private Optional<Boolean> IsFreeAt(String dateTimeStr) {
        try {
            this.context.getLog().info("Scheduler Reporter {} got date time str: {}", this.reporterId, dateTimeStr);
            LocalDateTime recvDateTime = LocalDateTime.parse(dateTimeStr);
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss");
            String recvDateTimeStr = recvDateTime.format(dateFormatter);

            this.context.getLog().info("Scheduler Reporter {} created date time: {}", this.reporterId, recvDateTimeStr);

            boolean isFree = this.curEvent.isEmpty();
            return Optional.of(isFree);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

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
    public Receive<ReporterMessages.Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(AskIsFreeAt.class, this::onAskIsFreeAt)
            .onMessage(AddToSchedule.class, this::onAddToSchedule)
            .onMessage(ReporterMessages.Passivate.class, m -> Behaviors.stopped())
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }


    /**
     * Set to be called when we receive a AskIsFreeAt message/command.
     * It will respond back to the actor who asked saying if the user is free or
     * not (or an error occurred).
     * 
     * @param msg The command which is asking if the user is free at a date and time
     * @return the behavior of this actor. It didn't change
     */
    private Behavior<ReporterMessages.Command> onAskIsFreeAt(AskIsFreeAt msg) {
        msg.replyTo.tell(new RespondIsFreeAt(msg.requestId, this.IsFreeAt(msg.dateTimeStr)));
        return this;
    }

    /**
     * Add the event to the user, setting curEvent. But only if the date time string
     * was valid AND the curEvent wasn't previously set.
     * 
     * (Meaning this will fail upon a second AddToSchedule message)
     */
    private Behavior<ReporterMessages.Command> onAddToSchedule(AddToSchedule msg) {
        Optional<Boolean> isFree = this.IsFreeAt(msg.dateTimeStr);
        boolean addedEvent = !isFree.isEmpty() && isFree.get();

        if (addedEvent) {
            this.curEvent = Optional.of(msg.event);
        }

        msg.replyTo.tell(new ScheduleAddedTo(msg.requestId, addedEvent));
        return this;
    }
    
    /**
     * What to do when shut down by a supervisor
     */
    private SchedulerReporter onPostStop() {
        getContext().getLog().info("Scheduler reporter {} stopped", this.reporterId);
        return this;
    }
}
