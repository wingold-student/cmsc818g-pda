package com.cmsc818g.StressContextEngine.Reporters;

import java.util.Optional;

import com.cmsc818g.StressRecommendationEngine.StressRecommendationEngine;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class SleepReporter extends AbstractBehavior<SleepReporter.Command>{

    public static Behavior<SleepReporter.Command> create(String reporterId, String groupId) {
        return Behaviors.setup(context -> new SleepReporter(context, reporterId, groupId));
    }

    public interface Command {}

    public static final class AskSleepHours implements Command {
        private ActorRef<StressRecommendationEngine.SleepReporterToRecommendation> replyTo;
        

        public AskSleepHours(ActorRef<StressRecommendationEngine.SleepReporterToRecommendation> replyTo) {
            this.replyTo = replyTo;
            
        }
    }

    private final ActorContext<SleepReporter.Command> context;
    private final String reporterId;
    private final String groupId;
     

    private SleepReporter(ActorContext<SleepReporter.Command> context, String reporterId, String groupId) {
        super(context);
        this.reporterId = reporterId;
        this.groupId = groupId;
        this.context = context;

        context.getLog().info("Sleep Reporter with id {}-{} started", reporterId, groupId);
    }

    @Override
    public Receive<SleepReporter.Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(AskSleepHours.class, this::onAskSleepHours)
            .build();
    }

    private Behavior<SleepReporter.Command> onAskSleepHours(AskSleepHours msg) {
        msg.replyTo.tell(new StressRecommendationEngine.SleepReporterToRecommendation(12)); //example sleep hours:12
        return this;
    }


}
