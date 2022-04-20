package com.cmsc818g.StressContextEngine.Reporters;

import java.util.ArrayList;
import java.util.Optional;

import com.cmsc818g.StressContextEngine.StressContextEngine;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.model.DateTime;

public class MedicalHistoryReporter extends AbstractBehavior<MedicalHistoryReporter.Command> {
    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public interface Command {}

    public static final class SendQuery implements Command {
        final String query;

        public SendQuery (String query) {
            this.query = query;
        }
    }


    // TODO: Requires a response?
    public static final class AddToHistory implements Command {
        final String diagnosis;
        final String[] medication;
        final Optional<DateTime> timeDiagnosed; // Will be 'now' if not provided

        public AddToHistory (String diagnosis, String[] medication, Optional<DateTime> timeDiangosed) {
            this.diagnosis = diagnosis;
            this.medication = medication;
            this.timeDiagnosed = timeDiangosed;
        }
    }

    // TODO: Requires a response?
    public static final class RemoveFromHistory implements Command {
        final String diagnosis;
        final String[] medication;

        public RemoveFromHistory(String diagnosis, String[] medication) {
            this.diagnosis = diagnosis;
            this.medication = medication;
        }
    }

    public static final class SubscribeForEventUpdates implements Command {
        final ActorRef<Response> subscriberActor;

        public SubscribeForEventUpdates(ActorRef<Response> subscriberActor, String[] calendarTypes) {
            this.subscriberActor = subscriberActor;
        }
    }

    public static final class UnsubscribeForEventUpdates implements Command {
        final ActorRef<Response> subscriberActor;

        public UnsubscribeForEventUpdates(ActorRef<Response> subscriberActor, String[] calendarTypes) {
            this.subscriberActor = subscriberActor;
        }
    }

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/
    public interface Response {}

    public static final class QueryResponse implements Response {
        final String results; // TODO: Won't be string

        public QueryResponse (String results) {
            this.results = results;
        }
    }

    /************************************* 
     * CREATION 
     *************************************/

    public static Behavior<Command> create(String databaseURI) {
        return Behaviors.setup(context -> new MedicalHistoryReporter(context, databaseURI));
    }

    private String databaseURI;
    private ActorRef<StressContextEngine.Command> contextEngine;
    private ArrayList<ActorRef<Response>> eventSubscribers;

    public MedicalHistoryReporter(ActorContext<Command> context, String databaseURI) {
        super(context);
        this.databaseURI = databaseURI;
        this.eventSubscribers = new ArrayList<>();

        boolean connected = true;

        if (!connected) {
            // TODO: tell contextEngine
            context.stop(context.getSelf());
        }
    }

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(SendQuery.class, this::onSendQuery)
            .onMessage(AddToHistory.class, this::onAddToHistory)
            .onMessage(RemoveFromHistory.class, this::onRemoveFromHistory)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    private Behavior<Command> onSendQuery(SendQuery msg) {
        return this;
    }

    private Behavior<Command> onAddToHistory(AddToHistory msg) {
        return this;
    }

    private Behavior<Command> onRemoveFromHistory(RemoveFromHistory msg) {
        return this;
    }

    private MedicalHistoryReporter onPostStop() {
        getContext().getLog().info("Medical History Reporter stoppping");
        return this;
    }
}
