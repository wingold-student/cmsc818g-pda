package com.cmsc818g.StressRecommendationEngine;

import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Receive;

/*
message from controller:
    current stress level
    past stress level
*/

public class StressRecommendationEngine extends AbstractBehavior<StressRecommendationEngine.Command> {

    public interface Command {}

    @Override
    public Receive<Command> createReceive() {
        // TODO Auto-generated method stub
        return null;
    }

    public StressRecommendationEngine(ActorContext<Command> context) {
        super(context);
        //TODO Auto-generated constructor stub
    }
}
