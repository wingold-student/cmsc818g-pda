package com.cmsc818g.StressEntityManager;

import java.time.Duration;
import java.util.ArrayList;

import com.cmsc818g.StressManagementController;
import com.cmsc818g.StressEntityManager.Entities.CalendarCommand;
import com.cmsc818g.StressEntityManager.Entities.CalendarEntity;
import com.cmsc818g.StressEntityManager.Entities.PhoneEntity;
import com.cmsc818g.StressEntityManager.Entities.SmartWatchDevice;

import akka.Done;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.TimerScheduler;
import akka.pattern.StatusReply;
import akka.stream.impl.io.InputStreamSinkStage.Data;

public class StressEntityManager extends AbstractBehavior<StressEntityManager.Command> {
    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public interface Command {}
    public static class entityManagerGreet implements Command {
        public final ActorRef<StressManagementController.Command> replyTo;

        public entityManagerGreet(ActorRef<StressManagementController.Command> replyTo) {
          this.replyTo = replyTo;
        }
    }//end of class entityManagerGreet

    public static final class StartPeriodicDatabaseReading implements Command {
      final Duration interval;

      public StartPeriodicDatabaseReading(Duration interval) {
        this.interval = interval;
      }
    }

    public static final class StopPeriodicDatabaseReading implements Command {
      public StopPeriodicDatabaseReading() {}
    }

    public static final class TellEntitiesToRead implements Command {
      final int rowToRead;
      public TellEntitiesToRead(int rowToRead) {
        this.rowToRead = rowToRead;
      }
    }

    public static final class DatabaseReadStatus implements Command {
      final String message;
      final Boolean success;

      public DatabaseReadStatus(String message, Boolean success) {
        this.message = message;
        this.success = success;
      }
    }

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/

    
    /************************************* 
     * CREATION 
     *************************************/
    private ActorRef<PhoneEntity.Command> phoneEntity;
    private ActorRef<CalendarCommand> calendarEntity;
    private ActorRef<SmartWatchDevice.Command> smartWatchEntity;
    private TimerScheduler<Command> timers;

    private final String phoneName = "PhoneEntity";
    private final String calendarName = "CalendarEntity";
    private final String smartWatchName = "SmartWatchEntity";
    private ArrayList<String> entities;

    private int currentRowNumber;

    public static Behavior<Command> create(String databaseURI, String tableName) {
      // return Behaviors.setup(context -> new StressEntityManager(context, databaseURI, tableName));
      return Behaviors.setup(context ->
        Behaviors.withTimers(
          timers ->
            new StressEntityManager(context, databaseURI, tableName, timers)
        )
      );
    }

    public StressEntityManager(ActorContext<Command> context, String databaseURI, String tableName, TimerScheduler<Command> timers) {
        super(context);
        getContext().getLog().info("Entity Manager actor created");
        this.timers = timers;

        this.currentRowNumber = 1;

        phoneEntity = context.spawn(PhoneEntity.create(context.getSelf(), databaseURI, tableName), phoneName);
        calendarEntity = context.spawn(CalendarEntity.create(databaseURI, tableName), calendarName);
        smartWatchEntity = context.spawn(SmartWatchDevice.create(databaseURI, tableName), smartWatchName);

        entities = new ArrayList<String>();

        entities.add(smartWatchName);
        entities.add(phoneName);
        entities.add(calendarName);
    }

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/
  
    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder()
        .onMessage(StartPeriodicDatabaseReading.class, this::onStartPeriodicDatabaseReading)
        .onMessage(StopPeriodicDatabaseReading.class, this::onStopPeriodicDatabaseReading)
        .onMessage(TellEntitiesToRead.class, this::onTellEntitiesToRead)
        .onMessage(DatabaseReadStatus.class, this::onDatabaseReadStatus)
        .onMessage(entityManagerGreet.class, this::onEngineResponse)
        .onSignal(PostStop.class, signal -> onPostStop())
        .build();
    }

    private Behavior<Command> onStartPeriodicDatabaseReading(StartPeriodicDatabaseReading msg) {
      this.timers.startTimerAtFixedRate("readDatabase", new TellEntitiesToRead(this.currentRowNumber), msg.interval);
      return this;
    }

    private Behavior<Command> onStopPeriodicDatabaseReading(StopPeriodicDatabaseReading msg) {
      if (this.timers.isTimerActive("readDatabase"))
        this.timers.cancel("readDatabase");

      return this;
    }

    private Behavior<Command> onTellEntitiesToRead(TellEntitiesToRead msg) {
      ActorContext<Command> context = getContext();
      Duration timeout = Duration.ofSeconds(3);
      int rowNumber = this.currentRowNumber;

      // TODO: Use Group Router instead

      context.askWithStatus(
        Done.class,
        phoneEntity,
        timeout,
        (ActorRef<StatusReply<Done>> ref) -> new PhoneEntity.ReadRowOfData(rowNumber, ref),
        (response, throwable) -> {
          if (response != null) {
            return new DatabaseReadStatus("Phone entity successfully read row: " + rowNumber, true);
          } else {
            getContext().getLog().info("Phone entity failed to read row: " + rowNumber);
            return new StopPeriodicDatabaseReading();
          }
        }
      );

      context.askWithStatus(
        Done.class,
        calendarEntity,
        timeout,
        ((ActorRef<StatusReply<Done>> ref) -> new CalendarEntity.ReadRowOfData(rowNumber, ref)),
        (response, throwable) -> {
          if (response != null) {
            return new DatabaseReadStatus("Calendar entity successfully read row: " + rowNumber, true);
          } else {
            getContext().getLog().info("Calendar entity failed to read row: " + rowNumber, false);
            return new StopPeriodicDatabaseReading();
          }
        }
      );

      context.askWithStatus(
        Done.class,
        smartWatchEntity,
        timeout,
        ((ActorRef<StatusReply<Done>> ref) -> new SmartWatchDevice.ReadRowOfData(rowNumber, ref)),
        (response, throwable) -> {
          if (response != null) {
            return new DatabaseReadStatus("Smart Watch entity successfully read row: " + rowNumber, true);
          } else {
            getContext().getLog().info("Smart Watch entity failed to read row: " + rowNumber, false);
            return new StopPeriodicDatabaseReading();
          }
        }
      );

      this.currentRowNumber++;
      return this;
    }

    private Behavior<Command> onDatabaseReadStatus(DatabaseReadStatus msg) {
      if (msg.success) {
        getContext().getLog().info(msg.message);
      } else {
        getContext().getLog().error(msg.message);
      }

      return this;
    }
  
    private Behavior<Command> onEngineResponse(entityManagerGreet message) { //when receive message
      //get information of connected entities
      getContext().getLog().info("Get entity lists and send msg back");
      message.replyTo.tell(new StressManagementController.EntityManagerToController("entityList", entities)); 
      return this;
    }

    private StressEntityManager onPostStop() {
      getContext().getLog().info("Entity Manager shutting down");
      return this;
  }

}
