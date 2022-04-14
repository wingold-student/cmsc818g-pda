package com.cmsc818g;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;


public class PdaSupervisor extends AbstractBehavior<Void> {
    public static Behavior<Void> create() {
        return Behaviors.setup(PdaSupervisor::new);
    }

    private PdaSupervisor(ActorContext<Void> context) {
        super(context);
        context.getLog().info("PDA Application started");
    }

    @Override
    public Receive<Void> createReceive() {
        return newReceiveBuilder().onSignal(PostStop.class, signal -> onPostStop()).build();
    }

    private PdaSupervisor onPostStop() {
        getContext().getLog().info("PDA Application stopped");
        return this;
    }

    public static void main( String[] args )
    {
        ActorSystem.create(PdaSupervisor.create(), "pda-system");
    }
}
