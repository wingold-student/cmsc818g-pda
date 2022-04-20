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

public class LocationReporter extends AbstractBehavior<LocationReporter.Command>{

    public static Behavior<LocationReporter.Command> create(String reporterId, String groupId) {
        return Behaviors.setup(context -> new LocationReporter(context, reporterId, groupId));
    }

    public interface Command {}

    public static final class AskLocation implements Command {
        private ActorRef<StressRecommendationEngine.LocationReporterToRecommendation> replyTo;
        

        public AskLocation(ActorRef<StressRecommendationEngine.LocationReporterToRecommendation> replyTo) {
            this.replyTo = replyTo;
            
        }
    }

    private final ActorContext<LocationReporter.Command> context;
    private final String reporterId;
    private final String groupId;
     

    private LocationReporter(ActorContext<LocationReporter.Command> context, String reporterId, String groupId) {
        super(context);
        this.reporterId = reporterId;
        this.groupId = groupId;
        this.context = context;

        context.getLog().info("Sleep Reporter with id {}-{} started", reporterId, groupId);
    }


    @Override
    public Receive<LocationReporter.Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(AskLocation.class, this::onAskLocation)
            .build();
    }

    private Behavior<LocationReporter.Command> onAskLocation(AskLocation msg) {
        msg.replyTo.tell(new StressRecommendationEngine.LocationReporterToRecommendation("class room")); //example location: class room
        return this;
    }

    
    
}
