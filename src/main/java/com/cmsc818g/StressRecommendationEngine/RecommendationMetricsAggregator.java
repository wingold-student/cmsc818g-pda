package com.cmsc818g.StressRecommendationEngine;

import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class RecommendationMetricsAggregator extends AbstractBehavior<RecommendationMetricsAggregator.Command> {
    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public interface Command {}

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/
    public interface Response {}

    /************************************* 
     * CREATION 
     *************************************/
    public static Behavior<Command> create() {
        return Behaviors.<Command>supervise(
            Behaviors.setup(
                context -> new RecommendationMetricsAggregator(context)
            )
        ).onFailure(Exception.class, SupervisorStrategy.restart());
    }

    public RecommendationMetricsAggregator(ActorContext<Command> context) {
        super(context);
        //TODO Auto-generated constructor stub
    }


    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<Command> createReceive() {
        // TODO Auto-generated method stub
        return null;
    }
    
}