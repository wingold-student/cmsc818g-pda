package com.cmsc818g.StressEntityManager.Entities;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;

import com.cmsc818g.StressEntityManager.StressEntityManager;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.model.DateTime;
import akka.pattern.StatusReply;

/** TODO:
 * Don't explicitly handle SQLExceptions with try/catch?
 * Instead let it die and have a supervisor restart?
 */

public class PhoneEntity extends AbstractBehavior<PhoneEntity.Command> {
    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public interface Command {}

    // TODO: For demo/testing purposes only
    public static final class ReadRowOfData implements Command {
        final int rowNumber;
        final ActorRef<StatusReply<Done>> replyTo;

        public ReadRowOfData(int rowNumber, ActorRef<StatusReply<Done>> replyTo) {
            this.rowNumber = rowNumber;
            this.replyTo = replyTo;
        }
    }

    public static final class AskingForSleepData implements Command {
        final ActorRef<Response> replyTo;

        public AskingForSleepData(ActorRef<Response> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class AskingForLocation implements Command {
        final ActorRef<Response> replyTo;

        public AskingForLocation(ActorRef<Response> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class SubscribeForMediaEvents implements Command {
        final ActorRef<Response> subscriber;

        public SubscribeForMediaEvents(ActorRef<Response> subscriber) {
            this.subscriber = subscriber;
        }
    }

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/
    public interface Response {}

    public static final class SleepDataResponse implements Response {
        final Optional<Integer> amountSlept; // TODO: Duration a better fit later?
        final Optional<Float> sleepQuality;
        final Optional<DateTime> onNightOf;

        public SleepDataResponse(Optional<Integer> amountSlept, Optional<Float> sleepQuality, Optional<DateTime> onNightOf) {
            this.amountSlept = amountSlept;
            this.sleepQuality = sleepQuality;
            this.onNightOf = onNightOf;
        }
    }

    public static final class LocationResponse implements Response {
        final Optional<String> location;

        public LocationResponse(Optional<String> location) {
            this.location = location;
        }
    }

    public static final class MediaEvent implements Response {
        final Media mediaEvent;

        public MediaEvent(Media mediaEvent) {
            this.mediaEvent = mediaEvent;
        }
    }


    /************************************* 
     * CREATION 
     *************************************/
    public static Behavior<Command> create(ActorRef<StressEntityManager.Command> entityManager, String databaseURI, String tableName) {
        return Behaviors.setup(context -> new PhoneEntity(context, entityManager, databaseURI, tableName));
    }

    private ArrayList<ActorRef<MediaEvent>> mediaSubscribers;
    private ActorRef<StressEntityManager.Command> entityManager;
    private String databaseURI;
    private String tableName;

    // Reading data
    private Optional<Integer> lastSleepHours;
    private Optional<String> lastLocation;
    private Optional<DateTime> lastTimeRead;

    public PhoneEntity(ActorContext<Command> context, ActorRef<StressEntityManager.Command> entityManager, String databaseURI, String tableName) {
        super(context);
        this.mediaSubscribers = new ArrayList<>();
        this.entityManager = entityManager;
        this.databaseURI = databaseURI;
        this.tableName = tableName;

        this.lastLocation = Optional.empty();
        this.lastSleepHours = Optional.empty();
        this.lastTimeRead = Optional.empty();
    }


    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(ReadRowOfData.class, this::onReadRowOfData)
            .onMessage(AskingForSleepData.class, this::onAskingForSleepData)
            .onMessage(AskingForLocation.class, this::onAskingForLocation)
            .onMessage(SubscribeForMediaEvents.class, this::onSubscribeForMediaEvents)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    private Connection connectToDB() {
        Connection conn = null;

        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(this.databaseURI);
        } catch (SQLException e) {
            // entityManager.tell(I've failed!)
            getContext().getLog().error("Failed to connect to the database {} with error {}", databaseURI, e);
            getContext().stop(getContext().getSelf());
        } catch (ClassNotFoundException e) {
            getContext().getLog().error("Failed to load JDBC driver", e);
            getContext().stop(getContext().getSelf());
        }

        return conn;
    }

    private Behavior<Command> onReadRowOfData(ReadRowOfData msg) {
        Connection conn = this.connectToDB();
        ResultSet results = null;

        String query = "SELECT id, DateTime, Location, SleepHours FROM " + this.tableName + " WHERE id = ?";
        try {
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setInt(1, msg.rowNumber);

            results = statement.executeQuery();
            results.next();
            this.lastLocation = Optional.of(results.getString("Location"));
            this.lastSleepHours = Optional.of(results.getInt("SleepHours"));
            String dateTimeStr = results.getString("DateTime");
            this.lastTimeRead = DateTime.fromIsoDateTimeString(dateTimeStr);

            results.close();
            msg.replyTo.tell(StatusReply.Ack());

        } catch (SQLException e) {
            getContext().getLog().error("Failed to query the with error {}", e);
            getContext().stop(getContext().getSelf());
        } 

        return this;
    }

    private Behavior<Command> onAskingForSleepData(AskingForSleepData msg) {
        msg.replyTo.tell(new SleepDataResponse(this.lastSleepHours, Optional.of(0f), this.lastTimeRead));
        return this;
    }

    private Behavior<Command> onAskingForLocation(AskingForLocation msg) {
        msg.replyTo.tell(new LocationResponse(this.lastLocation));
        return this;
    }

    // TODO
    private Behavior<Command> onSubscribeForMediaEvents(SubscribeForMediaEvents msg) {
        return this;
    }

    private PhoneEntity onPostStop() {
        return this;
    }


    /************************************* 
     * HELPER CLASSES
     *************************************/
    // TODO: Part of Media Reporter or per phone??
    public class Media {
        private String name;
        private String genre;
        private DateTime playedAt;

        public Media(String name, String genre) {
            this.name = name;
            this.genre = genre;
        }
    }
    
}
