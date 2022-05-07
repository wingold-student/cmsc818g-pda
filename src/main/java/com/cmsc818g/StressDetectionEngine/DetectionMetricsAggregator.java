package com.cmsc818g.StressDetectionEngine;

import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class DetectionMetricsAggregator extends AbstractBehavior<DetectionMetricsAggregator.Command> {
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
                context -> new DetectionMetricsAggregator(context)
            )
        ).onFailure(Exception.class, SupervisorStrategy.restart());
    }

    public DetectionMetricsAggregator(ActorContext<Command> context) {
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
