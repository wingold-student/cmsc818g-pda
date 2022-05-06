#!/usr/bin/python

import numpy as np
import matplotlib.pyplot as plt
import pandas as pd
import sklearn
import seaborn as sns
import pandas as pd
from matplotlib import pyplot as plt
from sklearn import preprocessing
from sklearn.naive_bayes import GaussianNB
from sklearn.neighbors import KNeighborsClassifier, KNeighborsRegressor
from sklearn import metrics
from sklearn.multioutput import ClassifierChain
from sklearn.neighbors import KNeighborsClassifier 
from sklearn.model_selection import cross_val_predict, train_test_split  
from sklearn.preprocessing import StandardScaler    
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, mean_squared_error  

# ################################################################
#       csv file read and write 
#       create data file with target (stress level)
#       for knn training
# # ###############################################################
class File_setting:
    def __init__(self):
        print("this is file data setting code")
        
    def defineStress(sleep, busy, systolic, diastolic, hr):
        total = 0 # level is 0 to 5
        
        if(sleep <= 4):
            total += 40
        elif(sleep <= 5):
            total += 30
        elif(sleep <= 6):
            total += 20
        elif(sleep <= 8):
            total += 10
            
        if(busy == 5):
            total += 40
        elif(busy == 4):
            total += 30
        elif(busy == 3):
            total += 20
        elif(busy == 2):
            total += 10

        if(systolic >140):
            total += 50
        elif(systolic >130):
            total += 30
        elif( systolic >125):
            total += 20
        elif(systolic >100):
            total += 10
    
        if(diastolic >90):
            total += 50
        elif(diastolic >85):
            total += 30
        elif(diastolic >77):
            total += 20
        elif(diastolic >73):
            total += 10
            
        if(hr > 130):
            total += 50
        elif(hr > 110):
            total += 30
        elif(hr > 130):
            total += 20
        elif(hr > 130):
            total += 10          

        if(total >= 180):
            level = 5
        elif(total >= 120):
            level = 4
        elif(total >= 80):
            level = 3  
        elif(total >= 50):
            level = 2
        else:
            level = 1
        return level
          
    import csv
    filepath = 'src/main/resources/dailyData.csv'
    header = []
    target = []
    heartrate = []
    bp_systolic = []
    bp_diastolic = []
    sleep=[]
    id=[]
    busy = []
    
    try: 
        with open(filepath, newline='') as csvfile:
            manual_reader =  csv.reader(csvfile, delimiter=',')
            header = next(manual_reader)
            print(header)
            for row in manual_reader:
                # print(', '.join(row)) 
                # id, time, sleep-hour, bp-systolic, bp-diastolic, heart-rate, variance, target
                row[0] = int(row[0])
                id.append(row[0])
                row[1]= int(row[1])# sleep-hour
                sleep.append(row[1]) 
                row[2] = int(row[2]) #busyness
                busy.append(row[2]) 
                row[3] = int(row[3]) # bp-systolic
                bp_systolic.append(row[3])
                row[4] = int(row[4]) # bp-diastolic
                bp_diastolic.append(row[3])
                row[5] = int(row[5]) # heart-rate
                heartrate.append(row[5])
                level = defineStress(row[1], row[2], row[3], row[4], row[5])
                target.append(level)
            
    finally:
        csvfile.close()    
        
    try:
        with open('src/main/resources/knn_data.csv', 'w', newline='') as csvfile:       
            manual_writer = csv.writer(csvfile, delimiter=',')
            manual_writer.writerow(["id","sleep-hours","busyness","bp-systolic","bp-diastolic", "heart-rate",  "target"])
            zip_all = zip(id, sleep, busy, bp_systolic, bp_diastolic,heartrate, target)
            for l in zip_all :
                manual_writer.writerow(l)
    finally:
        csvfile.close()    


# ######################################################
#       knn training with hr_data.csv
# #######################################################
class KNN:
    def __init__(self):
        pass

    dataset = pd.read_csv('src/main/resources/knn_data.csv', index_col="id")
    datatop = dataset.head()
    print(datatop)
  
    X = dataset.iloc[:, [1,2,3,4,5]].values # splits the data and make separate array X to hold attributes.
    y = dataset.iloc[:, -1].values  # splits the data and makes a separate array y to hold corresponding labels.
    # print(X.shape)
    # print(y.shape)
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.02, random_state=0) # training 80%, testing 20%
    # k_split = 220   
    # X_train = X[:k_split]
    # y_train = y[:k_split]
    # X_test = X[k_split:]
    # y_test = y[k_split:]

    ##### KNN regression #####
    reg = KNeighborsRegressor(n_neighbors = 3)
    reg.fit(X_train, y_train)
    y_pred = reg.predict(X_test)
    # y_pred = cross_val_predict(reg, X, y, cv=5)
    #print(y_pred.round(0))

    score = reg.score(X_test, y_test)
    print("score", score)
    
    ##### KNN classification #####
    # knn = KNeighborsClassifier(n_neighbors = 3, p = 2) 
    # # p = 1 , Manhattan Distance
    # # p = 2 , Euclidean Distance
    # # p = infinity , Cheybchev Distance
    
    # knn.fit(X_train, y_train)
    # y_pred = knn.predict(X_test)
  
    # print("accuracy score: " , accuracy_score(y_test, y_pred)) 
    # print(classification_report(y_test, y_pred))
    # print(confusion_matrix(y_test, y_pred))
    
if __name__ == '__main__':
    File_setting()
    KNN()