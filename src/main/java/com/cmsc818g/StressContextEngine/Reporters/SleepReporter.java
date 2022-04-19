package com.cmsc818g.StressContextEngine.Reporters;

import java.util.Optional;

import com.cmsc818g.StressRecommendationEngine.StressRecommendationEngine.SleepReporterToRecommendation;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class SleepReporter extends AbstractBehavior<BloodPressureReporter.Command>{

    public interface Command extends BloodPressureReporter.Command {}

    public static final class AskSleepHours implements Command {
        private ActorRef<SleepReporterToRecommendation> replyTo;
        

        public AskSleepHours(ActorRef<SleepReporterToRecommendation> replyTo) {
            this.replyTo = replyTo;
            
        }
    }

    

    public static Behavior<BloodPressureReporter.Command> create(String reporterId, String groupId) {
        return Behaviors.setup(context -> new SleepReporter(context, reporterId, groupId));
    }

    // Just some instance variables
    private final ActorContext<BloodPressureReporter.Command> context;

    private final String reporterId;
    private final String groupId;

    private SleepReporter(ActorContext<BloodPressureReporter.Command> context, String reporterId, String groupId) {
        super(context);
        this.reporterId = reporterId;
        this.groupId = groupId;
        this.context = context;

        context.getLog().info("Sleep Reporter with id {}-{} started", reporterId, groupId);
    }


    @Override
    public Receive<BloodPressureReporter.Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(AskSleepHours.class, this::onAskSleepHours)
            .onMessage(BloodPressureReporter.Passivate.class, m -> Behaviors.stopped())
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    private Behavior<BloodPressureReporter.Command> onAskSleepHours(AskSleepHours msg) {
        //msg.replyTo.tell(new onSleepReporterResponse(msg.requestId, this.IsFreeAt(msg.dateTimeStr)));
        return this;
    }

    private SleepReporter onPostStop() {
        getContext().getLog().info("Scheduler reporter {} stopped", this.reporterId);
        return this;
    }
    
}
