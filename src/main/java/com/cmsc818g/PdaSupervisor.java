package com.cmsc818g;

import com.cmsc818g.StressUIManager.StressUIManager;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;


/** PdaSupervisor is just meant to be the 'root' of the whole ActorSystem.
 * 
 * It could potentially listen in for devices joining or something and distribute
 * messages to multiple 'aspects' of the system.
 * 
 * However, we could also just replace this functionality with
 * the StressManagementController.
 */
public class PdaSupervisor extends AbstractBehavior<Void> {
    public static Behavior<Void> create() {
        return Behaviors.setup(PdaSupervisor::new);
    }
    private ActorRef<StressUIManager.Command> uiManagerActor;

    /**
     * Upon starting, create the StressUIManager
     */
    private PdaSupervisor(ActorContext<Void> context) {
        super(context);
        context.getLog().info("PDA Application started");
        uiManagerActor = context.spawn(StressUIManager.create(), "UIManager");
    }

    @Override
    public Receive<Void> createReceive() {
        return newReceiveBuilder()
            .onSignal(PostStop.class, signal -> onPostStop()).build();
    }

    private PdaSupervisor onPostStop() {
        getContext().getLog().info("PDA Application stopped");
        return this;
    }
}
