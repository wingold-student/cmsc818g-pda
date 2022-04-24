package com.cmsc818g.StressContextEngine.Reporters;

import java.util.Optional;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.model.DateTime;
import akka.pattern.StatusReply;

public class BusynessReporter extends AbstractBehavior<Reporter.Command> {
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

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/
    
    public static final class BusynessLevelResponse {
        final Float busynessLevel;

        public BusynessLevelResponse(Float busynessLevel) {
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

    /************************************* 
     * CREATION 
     *************************************/

    public static Behavior<Reporter.Command> create(ActorRef<Reporter.Command> schedulerReporter) {
        return Behaviors.setup(context -> new BusynessReporter(context, schedulerReporter));
    }

    private ActorRef<Reporter.Command> schedulerReporter;
    private ActorRef<SchedulerReporter.Response> schedulerAdapter;

    public BusynessReporter(ActorContext<Reporter.Command> context, ActorRef<Reporter.Command> schedulerReporter) {
        super(context);
        this.schedulerReporter = schedulerReporter;
        this.schedulerAdapter = context.messageAdapter(SchedulerReporter.Response.class, WrappedSchedulerResponse::new);
    }

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<Reporter.Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(Reporter.ReadRowOfData.class, this::onReadRowOfData)
            .onMessage(StartListening.class, this::onStartListening)
            .onMessage(StopListening.class, this::onStopListening)
            .onMessage(GetBusynessLevel.class, this::onGetBusynessLevel)
            .onMessage(WrappedSchedulerResponse.class, this::onWrappedSchedulerResponse)
            .onMessage(GracefulShutdown.class, this::onGracefulShutdown)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    private Behavior<Reporter.Command> onReadRowOfData(Reporter.ReadRowOfData msg) {
        msg.replyTo.tell(new Reporter.StatusOfRead(true, "I did nothing...", getContext().getSelf().path()));
        return this;
    }

    private Behavior<Reporter.Command> onStartListening(StartListening msg) {
        schedulerReporter.tell(new SchedulerReporter.SubscribeForNewEvents(schedulerAdapter));
        return this;
    }

    private Behavior<Reporter.Command> onStopListening(StopListening msg) {
        return this;
    }

    private Behavior<Reporter.Command> onGetBusynessLevel(GetBusynessLevel msg) {
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

    private BusynessReporter onGracefulShutdown(GracefulShutdown msg) {
        return this;
    }

    private BusynessReporter onPostStop() {

        return this;
    }

    private void UnsubscribeFromScheduler() {

    }

}
