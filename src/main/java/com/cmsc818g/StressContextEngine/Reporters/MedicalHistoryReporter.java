package com.cmsc818g.StressContextEngine.Reporters;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Optional;

import com.cmsc818g.Utilities.SQLiteHandler;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.TimerScheduler;
import akka.http.javadsl.model.DateTime;

public class MedicalHistoryReporter extends Reporter {
    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public interface Command extends Reporter.Command {}

    public static final class SendQuery implements Command {
        final String query;
        final ActorRef<QueryResponse> replyTo;

        public SendQuery (String query, ActorRef<QueryResponse> replyTo) {
            this.query = query;
            this.replyTo = replyTo;
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
        public final Optional<String> results; // TODO: Won't be string

        public QueryResponse (Optional<String> results) {
            this.results = results;
        }
    }

    /************************************* 
     * CREATION 
     *************************************/

    public static Behavior<Reporter.Command> create(ActorRef<SQLiteHandler.StatusOfRead> statusListener,
                                           String databaseURI,
                                           String tableName,
                                           int readRate)
    {
        return Behaviors.<Reporter.Command>supervise(
            Behaviors.setup(context -> 
                Behaviors.withTimers(
                    timers -> new MedicalHistoryReporter(context,
                                                        timers,
                                                        statusListener,
                                                        databaseURI,
                                                        tableName,
                                                        readRate)
                )
            )
        ).onFailure(SQLException.class, SupervisorStrategy.resume());
    }

    private static final String periodicTimerName = "medical-periodic";
    private ArrayList<ActorRef<Response>> eventSubscribers;

    public MedicalHistoryReporter(ActorContext<Reporter.Command> context,
                                  TimerScheduler<Reporter.Command> timers,
                                  ActorRef<SQLiteHandler.StatusOfRead> statusListener,
                                  String databaseURI,
                                  String tableName,
                                  int readRate)
    {
        super(context, timers, periodicTimerName, statusListener, databaseURI, tableName, readRate);
        this.eventSubscribers = new ArrayList<>();
    }

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<Reporter.Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(Reporter.ReadRowOfData.class, this::onReadRowOfData)
            .onMessage(SendQuery.class, this::onSendQuery)
            .onMessage(AddToHistory.class, this::onAddToHistory)
            .onMessage(RemoveFromHistory.class, this::onRemoveFromHistory)
            .onMessage(StartReading.class, this::onStartReading)
            .onMessage(StopReading.class, this::onStopReading)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    private Behavior<Reporter.Command> onSendQuery(SendQuery msg) {
        return this;
    }

    private Behavior<Reporter.Command> onAddToHistory(AddToHistory msg) {
        return this;
    }

    private Behavior<Reporter.Command> onRemoveFromHistory(RemoveFromHistory msg) {
        return this;
    }

    protected Behavior<Reporter.Command> onReadRowOfData(Reporter.ReadRowOfData msg) throws ClassNotFoundException, SQLException {
        // TODO: Doing nothing since no data at the moment
        return this;
    }
    private MedicalHistoryReporter onPostStop() {
        getContext().getLog().info("Medical History Reporter stoppping");
        return this;
    }
}
