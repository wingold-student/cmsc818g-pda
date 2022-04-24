package com.cmsc818g.StressContextEngine.Reporters;

import com.cmsc818g.StressRecommendationEngine.StressRecommendationEngine;
import com.cmsc818g.StressDetectionEngine.StressDetectionEngine;

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

    public interface Command extends Reporter.Command {}
    public static final class AskSleepHoursByRecommendation implements Command {
        private ActorRef<StressRecommendationEngine.SleepReporterToRecommendation> replyTo;
        
        public AskSleepHoursByRecommendation(ActorRef<StressRecommendationEngine.SleepReporterToRecommendation> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class AskSleepHoursByDetection implements Command {
        private ActorRef<StressDetectionEngine.SleepReporterToDetection> replyTo;
        
        public AskSleepHoursByDetection(ActorRef<StressDetectionEngine.SleepReporterToDetection> replyTo) {
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

        context.getLog().info("Sleep Reporter started", reporterId, groupId);
    }

    @Override
    public Receive<SleepReporter.Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(AskSleepHoursByRecommendation.class, this::onAskSleepHoursByRecommend)
            .onMessage(AskSleepHoursByDetection.class, this::onAskSleepHoursByDetection)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    private Behavior<SleepReporter.Command> onAskSleepHoursByRecommend(AskSleepHoursByRecommendation msg) {
        msg.replyTo.tell(new StressRecommendationEngine.SleepReporterToRecommendation(12)); //example sleep hours:12
        return this;
    }

    private Behavior<SleepReporter.Command> onAskSleepHoursByDetection(AskSleepHoursByDetection msg) {
        // same class as created for recommender above
        // Just referencing different message class and replying to detector
        msg.replyTo.tell(new StressDetectionEngine.SleepReporterToDetection(12)); //example sleep hours:12
        return this;
    }

    private SleepReporter onPostStop() {
        getContext().getLog().info("Sleep reporter stopped");
        return this;
    }

}
