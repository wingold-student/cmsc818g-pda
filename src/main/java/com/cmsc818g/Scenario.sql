-- MySQL dump 10.13  Distrib 8.0.28, for macos12.2 (arm64)
--
-- Host: localhost    Database: HealthData
-- ------------------------------------------------------
-- Server version	8.0.28

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `MedicalHistory`
--

DROP TABLE IF EXISTS `MedicalHistory`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `MedicalHistory` (
  `id` int NOT NULL,
  `Medication` varchar(45) NOT NULL,
  `Diagnosis` varchar(45) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `MedicalHistory`
--

LOCK TABLES `MedicalHistory` WRITE;
/*!40000 ALTER TABLE `MedicalHistory` DISABLE KEYS */;
INSERT INTO `MedicalHistory` VALUES (1,'aspirin','headache'),(2,'ibufropen','cold'),(3,'antacid','stomacheache'),(4,'xanax','anxiety'),(5,'prozac','depression'),(6,'zolpidem','insomnia');
/*!40000 ALTER TABLE `MedicalHistory` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `Scenario`
--

DROP TABLE IF EXISTS `Scenario`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `Scenario` (
  `id` int NOT NULL,
  `DateTime` datetime DEFAULT NULL,
  `Event` varchar(45) DEFAULT NULL,
  `Location` varchar(45) DEFAULT NULL,
  `Schedule` varchar(45) DEFAULT NULL,
  `Hearbeat` int DEFAULT NULL,
  `BloodPressure` int DEFAULT NULL,
  `SleepHours` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `Scenario`
--

LOCK TABLES `Scenario` WRITE;
/*!40000 ALTER TABLE `Scenario` DISABLE KEYS */;
INSERT INTO `Scenario` VALUES (1,'2022-04-19 08:00:00','wakeup','home',NULL,60,84,3),(2,'2022-04-19 08:30:00','breakfast','home',NULL,72,85,3),(3,'2022-04-19 09:00:00','work','office','work',87,87,3),(4,'2022-04-19 09:30:00','work','office','work',83,98,3),(5,'2022-04-19 10:00:00','meeting','office','meeting1',88,95,3),(6,'2022-04-19 10:30:00','meeting','office','meeting1',97,97,3),(7,'2022-04-19 11:00:00','class','classroom','class',98,102,3),(8,'2022-04-19 11:30:00','class','classroom','class',84,98,3),(9,'2022-04-19 12:00:00','class','classroom','class',76,99,3),(10,'2022-04-19 12:30:00','lunch','restaurant',NULL,78,99,3),(11,'2022-04-19 13:00:00','lunch','restaurant',NULL,70,100,3),(12,'2022-04-19 13:30:00','work','office','work',80,102,3),(13,'2022-04-19 14:00:00','meeting','office','meeting2',103,105,3),(14,'2022-04-19 14:30:00','meeting','office','meeting2',107,102,3),(15,'2022-04-19 15:00:00','pda-treatment','home','meeting3',90,98,3),(16,'2022-04-19 15:30:00','pda-treatment','home','meeting3',83,95,3),(17,'2022-04-19 16:00:00','pda-treatment','home','work',60,90,3),(18,'2022-04-19 16:30:00','pda-treatment','home','work',58,84,3);
/*!40000 ALTER TABLE `Scenario` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `WorkCalender`
--

DROP TABLE IF EXISTS `WorkCalender`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `WorkCalender` (
  `id` int NOT NULL,
  `datetime` datetime NOT NULL,
  `event` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `WorkCalender`
--

LOCK TABLES `WorkCalender` WRITE;
/*!40000 ALTER TABLE `WorkCalender` DISABLE KEYS */;
/*!40000 ALTER TABLE `WorkCalender` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2022-04-20 18:53:29
