package com.cmsc818g.StressEntityManager.Entities;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.model.DateTime;
import akka.japi.Option;
import akka.pattern.StatusReply;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class SmartWatchDevice extends AbstractBehavior<SmartWatchDevice.Command> {
    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public interface Command {}
    public static final class ReadRowOfData implements Command {
        final int rowNumber;
        final ActorRef<StatusReply<Done>> replyTo;

        public ReadRowOfData(int rowNumber,  ActorRef<StatusReply<Done>> replyTo) {
            this.rowNumber = rowNumber;
            this.replyTo = replyTo;
        }
    }

    public static final class ReadHeartrate implements Command {
        final ActorRef<HeartRateReading> replyTo;

        public ReadHeartrate(ActorRef<HeartRateReading> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class ReadBloodPressure implements Command {
        final ActorRef<BloodPressureReading> replyTo;

        public ReadBloodPressure(ActorRef<BloodPressureReading> replyTo) {
            this.replyTo = replyTo;
        }
    }

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/
    public interface Response {}

    public static final class HeartRateReading implements Response {
        final Optional<Integer> value;
        final Optional<DateTime> readingTime;

        public HeartRateReading(Optional<Integer> value, Optional<DateTime> readingTime) {
            this.value = value;
            this.readingTime = readingTime;
        }
    }

    public static final class BloodPressureReading implements Response {
        final Optional<String> value;
        final Optional<DateTime> readingTime;

        public BloodPressureReading(Optional<String> value, Optional<DateTime> readingTime) {
            this.value = value;
            this.readingTime = readingTime;
        }
    }

    /************************************* 
     * CREATION 
     *************************************/

    public static Behavior<Command> create(String databaseURI, String tableName) {
        return Behaviors.setup(context -> new SmartWatchDevice(context, databaseURI, tableName));
    }

    private final String databaseURI;
    private final String tableName;
    private Optional<Integer> lastHeartbeat;
    private Optional<String> lastBloodPressure;
    private Optional<DateTime> lastReadingTime;

    private SmartWatchDevice(ActorContext<Command> context, String databaseURI, String tableName) {
        super(context);
        this.databaseURI = databaseURI;
        this.tableName = tableName;
        this.lastHeartbeat = Optional.empty();
        this.lastBloodPressure = Optional.empty();
        this.lastReadingTime = Optional.empty();

        context.getLog().info("Smartwatch actor starting");
    }

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(ReadRowOfData.class, this::onReadRowOfData)
            .onMessage(ReadHeartrate.class, this::onReadHeartrate)
            .onMessage(ReadBloodPressure.class, this::onReadBloodPressure)
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

        String query = "SELECT id, DateTime, Heartbeat, Bloodpressure FROM " + this.tableName + " WHERE id = ?";
        try {
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setInt(1, msg.rowNumber);

            results = statement.executeQuery();
            results.next();

            this.lastHeartbeat = Optional.of(results.getInt("Heartbeat"));
            this.lastBloodPressure = Optional.of(results.getString("Bloodpressure"));

            String dateTimeStr = results.getString("DateTime");
            this.lastReadingTime = DateTime.fromIsoDateTimeString(dateTimeStr);

            results.close();
            msg.replyTo.tell(StatusReply.Ack());

        } catch (SQLException e) {
            getContext().getLog().error("Failed to query the with error {}", e);
            msg.replyTo.tell(StatusReply.error("Failed query"));
            getContext().stop(getContext().getSelf());
        } 

        return this;
    }

    private Behavior<Command> onReadHeartrate(ReadHeartrate msg) {
        msg.replyTo.tell(new HeartRateReading(this.lastHeartbeat, this.lastReadingTime));
        return this;
    }

    private Behavior<Command> onReadBloodPressure(ReadBloodPressure msg) {
        msg.replyTo.tell(new BloodPressureReading(this.lastBloodPressure, this.lastReadingTime));
        return this;
    }

    private SmartWatchDevice onPostStop() {
        getContext().getLog().info("Smartwatch actor stopped");
        return this;
    }
}